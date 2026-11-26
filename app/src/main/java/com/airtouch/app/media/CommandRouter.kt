package com.airtouch.app.media

import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import com.airtouch.app.gestures.GestureCommand
import com.airtouch.app.gestures.GestureEvent
import com.airtouch.app.service.CommandRecord
import com.airtouch.app.service.ExecutionMode
import com.airtouch.app.service.RuntimeState
import com.airtouch.app.settings.AppSettings
import com.airtouch.app.settings.defaultSettings
import com.airtouch.app.ui.labelFa
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class CommandRouter(
    context: Context,
    private val media: MediaSessionRepository,
    private val volume: VolumeController,
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

    fun close() {
        job?.cancel()
    }

    fun onGesture(event: GestureEvent) {
        val name = event.command.labelFa()
        if (event.command == GestureCommand.SUSPEND || event.command == GestureCommand.RESUME) {
            record(name, recognized = true, sent = false, confirmed = null, detail = name)
            return
        }
        if (runtime.executionMode.value != ExecutionMode.CONTROL) {
            record(name, recognized = true, sent = false, confirmed = false, detail = "حالت آزمایش؛ فرمانی ارسال نشد")
            return
        }
        when (event.command) {
            GestureCommand.VOLUME_UP -> adjust(up = true, name = name)
            GestureCommand.VOLUME_DOWN -> adjust(up = false, name = name)
            GestureCommand.MUTE_TOGGLE -> mute(name)
            GestureCommand.NEXT -> transport(next = true, playPause = false, name = name)
            GestureCommand.PREVIOUS -> transport(next = false, playPause = false, name = name)
            GestureCommand.PLAY_PAUSE -> transport(next = false, playPause = true, name = name)
            else -> Unit
        }
    }

    private fun adjust(up: Boolean, name: String) {
        val controller = media.activeController()
        val remote = volume.isRemote(controller)
        if (remote && controller != null) {
            when (val decision = adjustVolume(volume.remoteSnapshot(controller), up)) {
                is VolumeDecision.Reject -> record(name, true, false, false, decision.detail)
                is VolumeDecision.SetLevel -> {
                    volume.applyRemote(controller, decision.level, decision.savedLevel)
                    record(name, true, true, null, "فرمان صدای خروجی راه‌دور ارسال شد")
                }
            }
            return
        }
        when (val decision = adjustVolume(volume.localSnapshot(), up)) {
            is VolumeDecision.Reject -> record(name, true, false, false, decision.detail)
            is VolumeDecision.SetLevel -> {
                val confirmed = volume.applyLocal(decision.level, decision.savedLevel)
                record(name, true, true, confirmed, if (confirmed) decision.detail else "تغییر صدا تأیید نشد")
            }
        }
    }

    private fun mute(name: String) {
        val controller = media.activeController()
        if (volume.isRemote(controller) && controller != null) {
            when (val decision = toggleMute(volume.remoteSnapshot(controller))) {
                is VolumeDecision.Reject -> record(name, true, false, false, decision.detail)
                is VolumeDecision.SetLevel -> {
                    volume.applyRemote(controller, decision.level, decision.savedLevel)
                    record(name, true, true, null, "فرمان صدای خروجی راه‌دور ارسال شد")
                }
            }
            return
        }
        when (val decision = toggleMute(volume.localSnapshot())) {
            is VolumeDecision.Reject -> record(name, true, false, false, decision.detail)
            is VolumeDecision.SetLevel -> {
                val confirmed = volume.applyLocal(decision.level, decision.savedLevel)
                record(name, true, true, confirmed, if (confirmed) decision.detail else "تغییر صدا تأیید نشد")
            }
        }
    }

    private fun transport(next: Boolean, playPause: Boolean, name: String) {
        val controller = media.activeController()
        if (controller == null) {
            if (settings.mediaKeyFallback && settings.targetMode == TargetMode.AUTO) {
                dispatchKey(if (playPause) KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE else if (next) KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                record(name, true, true, null, "کلید رسانه‌ای سراسری ارسال شد و مقصد قطعی نیست")
            } else {
                record(name, true, false, false, "دسترسی به نشست رسانه برقرار نیست")
            }
            return
        }
        val choice = if (playPause) {
            choosePlayPause(media.capabilities(controller))
        } else {
            chooseSkip(next, media.capabilities(controller))
        }
        if (choice is TransportChoice.Reject) {
            record(name, true, false, false, choice.reason)
            return
        }
        val action = (choice as TransportChoice.Send).action
        job?.cancel()
        job = scope.launch {
            val confirmed = if (playPause) {
                val before = controller.playbackState?.state
                observePlayback(controller, before) { sendTransport(controller, action) }
            } else {
                val before = metadataKey(controller.metadata)
                observeMetadata(controller, before) { sendTransport(controller, action) }
            }
            record(
                name,
                recognized = true,
                sent = true,
                confirmed = confirmed,
                detail = if (confirmed) "تغییر وضعیت مشاهده شد" else "نتیجه تأیید نشد",
            )
        }
    }

    private fun sendTransport(controller: MediaController, action: TransportAction) {
        val controls = controller.transportControls
        when (action) {
            TransportAction.PLAY -> controls.play()
            TransportAction.PAUSE -> controls.pause()
            TransportAction.TOGGLE -> dispatchToController(controller)
            TransportAction.NEXT -> controls.skipToNext()
            TransportAction.PREVIOUS -> controls.skipToPrevious()
        }
    }

    private suspend fun observePlayback(
        controller: MediaController,
        before: Int?,
        send: () -> Unit,
    ): Boolean {
        return withTimeoutOrNull(2_000) {
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

    private suspend fun observeMetadata(
        controller: MediaController,
        before: String?,
        send: () -> Unit,
    ): Boolean {
        return withTimeoutOrNull(2_000) {
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

    private fun dispatchToController(controller: MediaController) {
        val time = SystemClock.uptimeMillis()
        controller.dispatchMediaButtonEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
        controller.dispatchMediaButtonEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
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

    private fun record(
        name: String,
        recognized: Boolean,
        sent: Boolean,
        confirmed: Boolean?,
        detail: String,
    ) {
        runtime.media.value = runtime.media.value.copy(
            lastCommand = CommandRecord(name, recognized, sent, confirmed, detail),
        )
    }
}
