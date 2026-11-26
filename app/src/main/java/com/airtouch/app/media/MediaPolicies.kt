package com.airtouch.app.media

enum class TargetMode {
    AUTO,
    MANUAL,
}

enum class PlaybackPhase {
    PLAYING,
    PAUSED,
    BUFFERING,
    CONNECTING,
    STOPPED,
    UNKNOWN,
}

enum class TransportAction {
    PLAY,
    PAUSE,
    TOGGLE,
    NEXT,
    PREVIOUS,
}

data class SessionSnapshot(
    val key: String,
    val packageName: String,
    val label: String,
    val playing: Boolean,
)

data class TransportCapabilities(
    val phase: PlaybackPhase,
    val canPlay: Boolean,
    val canPause: Boolean,
    val canPlayPause: Boolean,
    val canNext: Boolean,
    val canPrevious: Boolean,
)

sealed class TransportChoice {
    data class Send(val action: TransportAction) : TransportChoice()
    data class Reject(val reason: String) : TransportChoice()
}

data class VolumeSnapshot(
    val current: Int,
    val min: Int,
    val max: Int,
    val savedLevel: Int?,
    val fixed: Boolean,
    val inCall: Boolean,
    val remote: Boolean,
    val remoteSupportsAbsolute: Boolean,
)

sealed class VolumeDecision {
    data class SetLevel(val level: Int, val savedLevel: Int?, val detail: String) : VolumeDecision()
    data class Reject(val detail: String) : VolumeDecision()
}

fun selectSession(
    sessions: List<SessionSnapshot>,
    mode: TargetMode,
    manualPackage: String?,
    lastPackage: String?,
): SessionSnapshot? {
    if (sessions.isEmpty()) return null
    if (mode == TargetMode.MANUAL) {
        if (manualPackage.isNullOrBlank()) return null
        val matches = sessions.filter { it.packageName == manualPackage }
        return matches.firstOrNull { it.playing } ?: matches.firstOrNull()
    }
    sessions.firstOrNull { it.playing }?.let { return it }
    if (!lastPackage.isNullOrBlank()) {
        sessions.firstOrNull { it.packageName == lastPackage }?.let { return it }
    }
    return sessions.first()
}

fun choosePlayPause(capabilities: TransportCapabilities): TransportChoice {
    return when (capabilities.phase) {
        PlaybackPhase.PLAYING, PlaybackPhase.BUFFERING, PlaybackPhase.CONNECTING ->
            when {
                capabilities.canPause -> TransportChoice.Send(TransportAction.PAUSE)
                capabilities.canPlayPause -> TransportChoice.Send(TransportAction.TOGGLE)
                else -> TransportChoice.Reject("پشتیبانی اعلام نشده")
            }
        PlaybackPhase.PAUSED, PlaybackPhase.STOPPED ->
            when {
                capabilities.canPlay -> TransportChoice.Send(TransportAction.PLAY)
                capabilities.canPlayPause -> TransportChoice.Send(TransportAction.TOGGLE)
                else -> TransportChoice.Reject("پشتیبانی اعلام نشده")
            }
        PlaybackPhase.UNKNOWN ->
            if (capabilities.canPlayPause) {
                TransportChoice.Send(TransportAction.TOGGLE)
            } else {
                TransportChoice.Reject("وضعیت پخش نامعلوم است")
            }
    }
}

fun chooseSkip(next: Boolean, capabilities: TransportCapabilities): TransportChoice {
    return if (next) {
        if (capabilities.canNext) {
            TransportChoice.Send(TransportAction.NEXT)
        } else {
            TransportChoice.Reject("پشتیبانی اعلام نشده")
        }
    } else if (capabilities.canPrevious) {
        TransportChoice.Send(TransportAction.PREVIOUS)
    } else {
        TransportChoice.Reject("پشتیبانی اعلام نشده")
    }
}

fun adjustVolume(state: VolumeSnapshot, up: Boolean): VolumeDecision {
    rejectVolume(state)?.let { return it }
    if (state.max < state.min) return VolumeDecision.Reject("محدوده صدا نامعتبر است")
    val next = if (up) {
        (state.current + 1).coerceAtMost(state.max)
    } else {
        (state.current - 1).coerceAtLeast(state.min)
    }
    if (next == state.current) {
        return VolumeDecision.Reject(if (up) "صدا در بیشترین مقدار است" else "صدا در کمترین مقدار است")
    }
    val saved = if (next > 0) null else state.savedLevel
    return VolumeDecision.SetLevel(next, saved, if (up) "صدا زیاد شد" else "صدا کم شد")
}

fun toggleMute(state: VolumeSnapshot): VolumeDecision {
    rejectVolume(state)?.let { return it }
    if (state.current > state.min) {
        return VolumeDecision.SetLevel(state.min, state.current, "بی‌صدا شد")
    }
    val restore = state.savedLevel
    if (restore == null || restore <= 0) {
        return VolumeDecision.Reject("سطح قبلی صدا موجود نیست؛ صدا را دستی تنظیم کنید")
    }
    val level = restore.coerceIn(state.min, state.max)
    if (level <= 0) {
        return VolumeDecision.Reject("سطح قبلی صدا موجود نیست؛ صدا را دستی تنظیم کنید")
    }
    return VolumeDecision.SetLevel(level, null, "صدا برگشت")
}

private fun rejectVolume(state: VolumeSnapshot): VolumeDecision.Reject? {
    if (state.inCall) return VolumeDecision.Reject("در حالت تماس، صدای رسانه تغییر نمی‌کند")
    if (state.fixed) return VolumeDecision.Reject("حجم صدا روی این خروجی ثابت است")
    if (state.remote && !state.remoteSupportsAbsolute) {
        return VolumeDecision.Reject("این خروجی تنظیم صدا را اعلام نکرده است")
    }
    return null
}
