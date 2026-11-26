package com.airtouch.app.media

import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import com.airtouch.app.control.ControlAction
import com.airtouch.app.player.MusicLibrary
import com.airtouch.app.player.PlayerConnection
import com.airtouch.app.service.CommandRecord
import com.airtouch.app.service.RuntimeState
import com.airtouch.app.settings.AppSettings
import com.airtouch.app.settings.PlaybackSource
import com.airtouch.app.settings.defaultSettings
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Executes [ControlAction]s on the main thread against the selected playback source. */
class CommandRouter(
    context: Context,
    private val media: MediaSessionRepository,
    private val volume: VolumeController,
    private val player: PlayerConnection,
    private val library: MusicLibrary,
    private val runtime: RuntimeState,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settings: AppSettings = defaultSettings()
    private var job: Job? = null

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        media.updateSettings(newSettings)
    }

    fun perform(action: ControlAction, gesture: String) {
        val name = action.label
        when (action) {
            ControlAction.NONE -> Unit
            ControlAction.LOCK_TOGGLE -> record(
                name,
                gesture,
                sent = true,
                confirmed = true,
                detail = if (runtime.locked.value) "کنترل حرکتی قفل شد" else "کنترل حرکتی باز شد",
            )
            ControlAction.VOLUME_UP -> adjust(up = true, name = name, gesture = gesture)
            ControlAction.VOLUME_DOWN -> adjust(up = false, name = name, gesture = gesture)
            ControlAction.MUTE_TOGGLE -> mute(name, gesture)
            else -> if (settings.playbackSource == PlaybackSource.BUILT_IN) builtIn(action, name, gesture) else system(action, name, gesture)
        }
    }

    private fun builtIn(action: ControlAction, name: String, gesture: String) {
        val tracks = library.tracks.value
        player.use { controller ->
            if (controller.mediaItemCount == 0) {
                if (tracks.isEmpty()) {
                    record(name, gesture, false, false, "فهرست پخش داخلی خالی است؛ از صفحه پخش‌کننده آهنگ اضافه کنید")
                    return@use
                }
                player.loadQueue(tracks, 0, play = action == ControlAction.PLAY_PAUSE)
                record(name, gesture, true, true, "فهرست پخش داخلی آماده شد")
                return@use
            }
            when (action) {
                ControlAction.PLAY_PAUSE -> {
                    if (controller.isPlaying) {
                        controller.pause()
                    } else {
                        if (controller.playbackState == androidx.media3.common.Player.STATE_ENDED) controller.seekTo(0, 0L)
                        controller.prepare()
                        controller.play()
                    }
                    record(name, gesture, true, true, if (controller.playWhenReady) "پخش شد" else "متوقف شد")
                }
                ControlAction.NEXT -> {
                    if (controller.hasNextMediaItem()) {
                        controller.seekToNextMediaItem()
                        record(name, gesture, true, true, "آهنگ بعدی")
                    } else {
                        record(name, gesture, false, false, "آهنگ بعدی در فهرست نیست")
                    }
                }
                ControlAction.PREVIOUS -> {
                    controller.seekToPrevious()
                    record(name, gesture, true, true, "آهنگ قبلی یا شروع همین آهنگ")
                }
                ControlAction.SEEK_FORWARD, ControlAction.SEEK_BACKWARD -> {
                    val delta = if (action == ControlAction.SEEK_FORWARD) SEEK_MS else -SEEK_MS
                    val duration = controller.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    controller.seekTo((controller.currentPosition + delta).coerceIn(0L, duration))
                    record(name, gesture, true, true, "جابه‌جا شد")
                }
                else -> Unit
            }
        }
    }

    private fun system(action: ControlAction, name: String, gesture: String) {
        val controller = media.activeController()
        if (controller == null) {
            val key = when (action) {
                ControlAction.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                ControlAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                ControlAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> null
            }
            if (key != null && settings.mediaKeyFallback && settings.targetMode == TargetMode.AUTO) {
                dispatchKey(key)
                record(name, gesture, true, null, "کلید رسانه‌ای سراسری ارسال شد و مقصد قطعی نیست")
            } else {
                record(name, gesture, false, false, "به پخش‌کننده‌ای وصل نیست؛ دسترسی اعلان را روشن کنید یا پخش‌کننده داخلی را انتخاب کنید")
            }
            return
        }
        if (action == ControlAction.SEEK_FORWARD || action == ControlAction.SEEK_BACKWARD) {
            seekSystem(controller, forward = action == ControlAction.SEEK_FORWARD, name = name, gesture = gesture)
            return
        }
        val capabilities = media.capabilities(controller)
        val choice = when (action) {
            ControlAction.PLAY_PAUSE -> choosePlayPause(capabilities)
            ControlAction.NEXT -> chooseSkip(true, capabilities)
            ControlAction.PREVIOUS -> chooseSkip(false, capabilities)
            else -> return
        }
        if (choice is TransportChoice.Reject) {
            record(name, gesture, false, false, choice.reason)
            return
        }
        val transport = (choice as TransportChoice.Send).action
        job?.cancel()
        job = scope.launch {
            val confirmed = if (action == ControlAction.PLAY_PAUSE) {
                val before = controller.playbackState?.state
                observePlayback(controller, before) { sendTransport(controller, transport) }
            } else {
                val before = metadataKey(controller.metadata)
                observeMetadata(controller, before) { sendTransport(controller, transport) }
            }
            record(name, gesture, true, confirmed, if (confirmed) "تغییر وضعیت دیده شد" else "نتیجه تأیید نشد")
        }
    }

    private fun seekSystem(controller: MediaController, forward: Boolean, name: String, gesture: String) {
        val state = controller.playbackState
        val actions = state?.actions ?: 0L
        val controls = controller.transportControls
        when {
            actions and PlaybackState.ACTION_SEEK_TO != 0L && state != null -> {
                val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
                val position = state.position + if (state.state == PlaybackState.STATE_PLAYING) (elapsed * state.playbackSpeed).toLong() else 0L
                val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: Long.MAX_VALUE
                controls.seekTo((position + if (forward) SEEK_MS else -SEEK_MS).coerceIn(0L, duration))
                record(name, gesture, true, null, "درخواست جابه‌جایی ارسال شد")
            }
            forward && actions and PlaybackState.ACTION_FAST_FORWARD != 0L -> {
                controls.fastForward()
                record(name, gesture, true, null, "جلو بردن ارسال شد")
            }
            !forward && actions and PlaybackState.ACTION_REWIND != 0L -> {
                controls.rewind()
                record(name, gesture, true, null, "عقب بردن ارسال شد")
            }
            else -> record(name, gesture, false, false, "این پخش‌کننده جابه‌جایی را اعلام نکرده")
        }
    }

    private fun adjust(up: Boolean, name: String, gesture: String) {
        val controller = if (settings.playbackSource == PlaybackSource.SYSTEM) media.activeController() else null
        if (controller != null && volume.isRemote(controller)) {
            when (val decision = adjustVolume(volume.remoteSnapshot(controller), up)) {
                is VolumeDecision.Reject -> record(name, gesture, false, false, decision.detail)
                is VolumeDecision.SetLevel -> {
                    volume.applyRemote(controller, decision.level, decision.savedLevel)
                    record(name, gesture, true, null, "فرمان صدای خروجی راه‌دور ارسال شد")
                }
            }
            return
        }
        when (val decision = adjustVolume(volume.localSnapshot(), up)) {
            is VolumeDecision.Reject -> record(name, gesture, false, false, decision.detail)
            is VolumeDecision.SetLevel -> {
                val confirmed = volume.applyLocal(decision.level, decision.savedLevel)
                record(name, gesture, true, confirmed, if (confirmed) decision.detail else "تغییر صدا تأیید نشد")
            }
        }
    }

    private fun mute(name: String, gesture: String) {
        val controller = if (settings.playbackSource == PlaybackSource.SYSTEM) media.activeController() else null
        if (controller != null && volume.isRemote(controller)) {
            when (val decision = toggleMute(volume.remoteSnapshot(controller))) {
                is VolumeDecision.Reject -> record(name, gesture, false, false, decision.detail)
                is VolumeDecision.SetLevel -> {
                    volume.applyRemote(controller, decision.level, decision.savedLevel)
                    record(name, gesture, true, null, "فرمان صدای خروجی راه‌دور ارسال شد")
                }
            }
            return
        }
        when (val decision = toggleMute(volume.localSnapshot())) {
            is VolumeDecision.Reject -> record(name, gesture, false, false, decision.detail)
            is VolumeDecision.SetLevel -> {
                val confirmed = volume.applyLocal(decision.level, decision.savedLevel)
                record(name, gesture, true, confirmed, if (confirmed) decision.detail else "تغییر صدا تأیید نشد")
            }
        }
    }

    private fun sendTransport(controller: MediaController, action: TransportAction) {
        val controls = controller.transportControls
        when (action) {
            TransportAction.PLAY -> controls.play()
            TransportAction.PAUSE -> controls.pause()
            TransportAction.TOGGLE -> {
                val time = SystemClock.uptimeMillis()
                controller.dispatchMediaButtonEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
                controller.dispatchMediaButtonEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
            }
            TransportAction.NEXT -> controls.skipToNext()
            TransportAction.PREVIOUS -> controls.skipToPrevious()
        }
    }

    private suspend fun observePlayback(controller: MediaController, before: Int?, send: () -> Unit): Boolean {
        return withTimeoutOrNull(CONFIRM_MS) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        val now = state?.state
                        if (now != null && now != before) {
                            runCatching { controller.unregisterCallback(this) }
                            if (continuation.isActive) continuation.resume(true)
                        }
                    }
                }
                controller.registerCallback(callback)
                continuation.invokeOnCancellation { runCatching { controller.unregisterCallback(callback) } }
                send()
            }
        } ?: false
    }

    private suspend fun observeMetadata(controller: MediaController, before: String?, send: () -> Unit): Boolean {
        return withTimeoutOrNull(CONFIRM_MS) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        val now = metadataKey(metadata)
                        if (now != null && now != before) {
                            runCatching { controller.unregisterCallback(this) }
                            if (continuation.isActive) continuation.resume(true)
                        }
                    }
                }
                controller.registerCallback(callback)
                continuation.invokeOnCancellation { runCatching { controller.unregisterCallback(callback) } }
                send()
            }
        } ?: false
    }

    private fun dispatchKey(keyCode: Int) {
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun metadataKey(metadata: MediaMetadata?): String? {
        if (metadata == null) return null
        val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        return listOfNotNull(id, title).joinToString("|").ifBlank { null }
    }

    private fun record(name: String, gesture: String, sent: Boolean, confirmed: Boolean?, detail: String) {
        runtime.lastCommand.value = CommandRecord(name, gesture, sent, confirmed, detail)
    }

    private companion object {
        const val SEEK_MS = 10_000L
        const val CONFIRM_MS = 2_000L
    }
}
