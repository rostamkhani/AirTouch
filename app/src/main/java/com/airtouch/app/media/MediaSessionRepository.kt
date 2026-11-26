package com.airtouch.app.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import com.airtouch.app.service.MediaUiState
import com.airtouch.app.service.RuntimeState
import com.airtouch.app.settings.AppSettings

class MediaSessionRepository(
    private val context: Context,
    private val runtime: RuntimeState,
) {
    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var registered = false
    private var controllers: List<MediaController> = emptyList()
    private var active: MediaController? = null
    private var callback: MediaController.Callback? = null
    private var lastPackage: String? = null
    private var settings: AppSettings? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        replace(list.orEmpty())
    }

    fun onConnected(service: NotificationListenerService, listenerComponent: ComponentName) {
        component = listenerComponent
        manager = service.getSystemService(MediaSessionManager::class.java)
        runtime.media.value = runtime.media.value.copy(listenerConnected = true)
        if (!registered) {
            val name = component
            if (name != null) {
                manager?.addOnActiveSessionsChangedListener(sessionListener, name)
                registered = true
            }
        }
        refresh()
    }

    fun onDisconnected() {
        if (registered) {
            runCatching { manager?.removeOnActiveSessionsChangedListener(sessionListener) }
            registered = false
        }
        runtime.media.value = MediaUiState(listenerConnected = false)
        replace(emptyList())
    }

    fun updateSettings(newSettings: AppSettings) {
        val changed = settings?.targetMode != newSettings.targetMode ||
            settings?.manualPackage != newSettings.manualPackage
        settings = newSettings
        if (changed) selectActive()
    }

    fun refresh() {
        val sessionManager = manager ?: return
        val name = component ?: return
        try {
            replace(sessionManager.getActiveSessions(name))
        } catch (_: SecurityException) {
            onDisconnected()
        }
    }

    fun activeController(): MediaController? = active

    private fun replace(list: List<MediaController>) {
        controllers = list
        selectActive()
    }

    private fun selectActive() {
        val currentSettings = settings
        val snapshots = controllers.map { it.toSnapshot() }
        val chosen = if (currentSettings == null) {
            snapshots.firstOrNull { it.playing } ?: snapshots.firstOrNull()
        } else {
            selectSession(snapshots, currentSettings.targetMode, currentSettings.manualPackage, lastPackage)
        }
        val next = chosen?.let { snapshot -> controllers.firstOrNull { it.key() == snapshot.key } }
        val previousKey = active?.key()
        if (next?.key() != previousKey) {
            callback?.let { old -> active?.unregisterCallback(old) }
            active = next
            if (next != null) {
                val created = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                    override fun onSessionDestroyed() = refresh()
                }
                callback = created
                next.registerCallback(created)
            } else {
                callback = null
            }
            if (next != null) lastPackage = next.packageName
        }
        publish(snapshots)
    }

    private fun publish(snapshots: List<SessionSnapshot> = controllers.map { it.toSnapshot() }) {
        val controller = active
        val state = controller?.playbackState
        val metadata = controller?.metadata
        val remote = controller?.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
        runtime.media.value = runtime.media.value.copy(
            listenerConnected = manager != null && registered,
            targetLabel = controller?.let { labelOf(it.packageName) },
            targetPackage = controller?.packageName,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            playbackLabel = phaseLabel(state?.state),
            outputLabel = if (controller == null) "نشستی انتخاب نشده" else if (remote) "خروجی راه‌دور" else "خروجی همین گوشی",
            supportedActions = actionLabels(state),
            sessions = snapshots,
        )
    }

    fun capabilities(controller: MediaController): TransportCapabilities {
        val state = controller.playbackState
        val actions = state?.actions ?: 0L
        return TransportCapabilities(
            phase = when (state?.state) {
                PlaybackState.STATE_PLAYING -> PlaybackPhase.PLAYING
                PlaybackState.STATE_PAUSED -> PlaybackPhase.PAUSED
                PlaybackState.STATE_BUFFERING -> PlaybackPhase.BUFFERING
                PlaybackState.STATE_CONNECTING -> PlaybackPhase.CONNECTING
                PlaybackState.STATE_STOPPED -> PlaybackPhase.STOPPED
                else -> PlaybackPhase.UNKNOWN
            },
            canPlay = actions and PlaybackState.ACTION_PLAY != 0L,
            canPause = actions and PlaybackState.ACTION_PAUSE != 0L,
            canPlayPause = actions and PlaybackState.ACTION_PLAY_PAUSE != 0L,
            canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
        )
    }

    private fun MediaController.toSnapshot(): SessionSnapshot {
        return SessionSnapshot(
            key = key(),
            packageName = packageName,
            label = labelOf(packageName),
            playing = playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    private fun MediaController.key(): String = packageName + "#" + sessionToken.hashCode()

    private fun labelOf(packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun phaseLabel(state: Int?): String = when (state) {
        PlaybackState.STATE_PLAYING -> "در حال پخش"
        PlaybackState.STATE_PAUSED -> "متوقف موقت"
        PlaybackState.STATE_BUFFERING -> "در حال آماده‌سازی پخش"
        PlaybackState.STATE_CONNECTING -> "در حال اتصال"
        PlaybackState.STATE_STOPPED -> "متوقف"
        else -> "نامشخص"
    }

    private fun actionLabels(state: PlaybackState?): List<String> {
        val actions = state?.actions ?: return emptyList()
        val labels = mutableListOf<String>()
        if (actions and PlaybackState.ACTION_PLAY != 0L) labels += "پخش"
        if (actions and PlaybackState.ACTION_PAUSE != 0L) labels += "توقف"
        if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) labels += "بعدی"
        if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) labels += "قبلی"
        return labels
    }
}
