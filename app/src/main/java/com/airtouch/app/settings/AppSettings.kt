package com.airtouch.app.settings

import com.airtouch.app.control.ControlAction
import com.airtouch.app.control.DefaultBindings
import com.airtouch.app.media.TargetMode
import com.airtouch.handcontrol.core.GestureTrigger
import com.airtouch.handcontrol.core.HandControlConfig
import com.airtouch.handcontrol.mediapipe.HandLandmarkerRunner

/** Where gesture commands go: AirTouch's own player, or the music app already playing. */
enum class PlaybackSource {
    BUILT_IN,
    SYSTEM,
}

data class AppSettings(
    val useFrontCamera: Boolean,
    val showPreview: Boolean,
    val targetFps: Int,
    val sensitivity: Float,
    val holdMs: Long,
    val cooldownMs: Long,
    val bindings: Map<GestureTrigger, ControlAction>,
    val playbackSource: PlaybackSource,
    val targetMode: TargetMode,
    val manualPackage: String?,
    val mediaKeyFallback: Boolean,
    val onboardingDone: Boolean,
)

data class RawSettings(
    val useFrontCamera: Boolean? = null,
    val showPreview: Boolean? = null,
    val targetFps: Int? = null,
    val sensitivity: Float? = null,
    val holdMs: Long? = null,
    val cooldownMs: Long? = null,
    val bindings: String? = null,
    val playbackSource: String? = null,
    val targetMode: String? = null,
    val manualPackage: String? = null,
    val mediaKeyFallback: Boolean? = null,
    val onboardingDone: Boolean? = null,
)

fun defaultSettings(): AppSettings = AppSettings(
    useFrontCamera = true,
    showPreview = true,
    targetFps = 15,
    sensitivity = 1f,
    holdMs = 600,
    cooldownMs = 400,
    bindings = DefaultBindings.map,
    playbackSource = PlaybackSource.BUILT_IN,
    targetMode = TargetMode.AUTO,
    manualPackage = null,
    mediaKeyFallback = false,
    onboardingDone = false,
)

fun sanitize(raw: RawSettings): AppSettings {
    val defaults = defaultSettings()
    return AppSettings(
        useFrontCamera = raw.useFrontCamera ?: defaults.useFrontCamera,
        showPreview = raw.showPreview ?: defaults.showPreview,
        targetFps = raw.targetFps?.takeIf { it == 10 || it == 15 || it == 20 || it == 30 } ?: defaults.targetFps,
        sensitivity = raw.sensitivity?.takeIf { it.isFinite() }?.coerceIn(0.7f, 1.4f) ?: defaults.sensitivity,
        holdMs = raw.holdMs?.coerceIn(300L, 1_500L) ?: defaults.holdMs,
        cooldownMs = raw.cooldownMs?.coerceIn(200L, 1_500L) ?: defaults.cooldownMs,
        bindings = DefaultBindings.decode(raw.bindings) ?: defaults.bindings,
        playbackSource = PlaybackSource.entries.firstOrNull { it.name == raw.playbackSource } ?: defaults.playbackSource,
        targetMode = TargetMode.entries.firstOrNull { it.name == raw.targetMode } ?: defaults.targetMode,
        manualPackage = raw.manualPackage?.takeIf { it.isNotBlank() },
        mediaKeyFallback = raw.mediaKeyFallback ?: defaults.mediaKeyFallback,
        onboardingDone = raw.onboardingDone ?: defaults.onboardingDone,
    )
}

fun AppSettings.toHandConfig(): HandControlConfig =
    HandControlConfig(holdMs = holdMs).withSensitivity(sensitivity)

fun AppSettings.trackerOptions(): HandLandmarkerRunner.Options {
    val confidence = (0.5f / sensitivity).coerceIn(0.4f, 0.7f)
    return HandLandmarkerRunner.Options(
        minDetectionConfidence = confidence,
        minPresenceConfidence = confidence,
        minTrackingConfidence = confidence,
    )
}
