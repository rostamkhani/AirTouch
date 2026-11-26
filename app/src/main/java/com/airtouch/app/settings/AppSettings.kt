package com.airtouch.app.settings

import com.airtouch.app.gestures.GestureCommand
import com.airtouch.app.gestures.GestureConfig
import com.airtouch.app.media.TargetMode

data class AppSettings(
    val useFrontCamera: Boolean,
    val showPreview: Boolean,
    val targetFps: Int,
    val sensitivity: Float,
    val cooldownMs: Long,
    val holdMs: Long,
    val stabilizeMs: Long,
    val enabledCommands: Set<GestureCommand>,
    val targetMode: TargetMode,
    val manualPackage: String?,
    val mediaKeyFallback: Boolean,
    val feedbackSound: Boolean,
    val onboardingDone: Boolean,
)

data class RawSettings(
    val useFrontCamera: Boolean? = null,
    val showPreview: Boolean? = null,
    val targetFps: Int? = null,
    val sensitivity: Float? = null,
    val cooldownMs: Long? = null,
    val holdMs: Long? = null,
    val stabilizeMs: Long? = null,
    val enabledCommands: Set<GestureCommand>? = null,
    val targetMode: String? = null,
    val manualPackage: String? = null,
    val mediaKeyFallback: Boolean? = null,
    val feedbackSound: Boolean? = null,
    val onboardingDone: Boolean? = null,
)

fun defaultSettings(): AppSettings = AppSettings(
    useFrontCamera = true,
    showPreview = true,
    targetFps = 15,
    sensitivity = 1f,
    cooldownMs = 700,
    holdMs = 1_000,
    stabilizeMs = 300,
    enabledCommands = GestureConfig.DEFAULT_ENABLED,
    targetMode = TargetMode.AUTO,
    manualPackage = null,
    mediaKeyFallback = false,
    feedbackSound = false,
    onboardingDone = false,
)

fun sanitize(raw: RawSettings): AppSettings {
    val defaults = defaultSettings()
    val fps = raw.targetFps?.takeIf { it == 10 || it == 15 || it == 20 } ?: defaults.targetFps
    val sensitivity = raw.sensitivity?.takeIf { it.isFinite() }?.coerceIn(0.7f, 1.4f) ?: defaults.sensitivity
    val mode = when (raw.targetMode) {
        "MANUAL" -> TargetMode.MANUAL
        "AUTO" -> TargetMode.AUTO
        null -> defaults.targetMode
        else -> defaults.targetMode
    }
    val manual = raw.manualPackage?.takeIf { it.isNotBlank() }
    return AppSettings(
        useFrontCamera = raw.useFrontCamera ?: defaults.useFrontCamera,
        showPreview = raw.showPreview ?: defaults.showPreview,
        targetFps = fps,
        sensitivity = sensitivity,
        cooldownMs = raw.cooldownMs?.coerceIn(400L, 1_500L) ?: defaults.cooldownMs,
        holdMs = raw.holdMs?.coerceIn(600L, 2_000L) ?: defaults.holdMs,
        stabilizeMs = raw.stabilizeMs?.coerceIn(200L, 800L) ?: defaults.stabilizeMs,
        enabledCommands = raw.enabledCommands ?: defaults.enabledCommands,
        targetMode = mode,
        manualPackage = manual,
        mediaKeyFallback = raw.mediaKeyFallback ?: defaults.mediaKeyFallback,
        feedbackSound = raw.feedbackSound ?: defaults.feedbackSound,
        onboardingDone = raw.onboardingDone ?: defaults.onboardingDone,
    )
}

fun AppSettings.toGestureConfig(): GestureConfig {
    val scale = sensitivity.coerceIn(0.7f, 1.4f)
    val enter = (0.25f * scale).coerceIn(0.12f, 0.45f)
    val exitFloor = enter + 0.08f
    val exit = (0.40f * scale).coerceAtLeast(exitFloor).coerceAtMost(0.75f)
    return GestureConfig(
        stabilizeMs = stabilizeMs,
        swipeMinFraction = (0.12f / scale).coerceIn(0.08f, 0.25f),
        swipeMinPalmWidths = (1f / scale).coerceIn(0.7f, 1.6f),
        swipeMinSpeed = (2.5f / scale).coerceIn(1.4f, 4f),
        pinchEnter = enter,
        pinchExit = if (exit > enter) exit else exitFloor,
        cooldownMs = cooldownMs,
        holdMs = holdMs,
        enabled = enabledCommands,
    )
}

fun AppSettings.modelConfidence(): Float = (0.6f / sensitivity).coerceIn(0.45f, 0.75f)
