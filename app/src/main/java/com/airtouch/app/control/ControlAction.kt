package com.airtouch.app.control

import com.airtouch.handcontrol.core.Finger
import com.airtouch.handcontrol.core.GestureTrigger
import com.airtouch.handcontrol.core.HandPose
import com.airtouch.handcontrol.core.TouchKind

enum class ControlAction {
    NONE,
    PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE_TOGGLE,
    SEEK_FORWARD,
    SEEK_BACKWARD,
    LOCK_TOGGLE;

    val repeatable: Boolean
        get() = this == VOLUME_UP || this == VOLUME_DOWN || this == SEEK_FORWARD || this == SEEK_BACKWARD

    val label: String
        get() = when (this) {
            NONE -> "بدون کار"
            PLAY_PAUSE -> "پخش یا توقف"
            NEXT -> "آهنگ بعدی"
            PREVIOUS -> "آهنگ قبلی"
            VOLUME_UP -> "زیاد کردن صدا"
            VOLUME_DOWN -> "کم کردن صدا"
            MUTE_TOGGLE -> "بی‌صدا یا بازگشت صدا"
            SEEK_FORWARD -> "۱۰ ثانیه جلو"
            SEEK_BACKWARD -> "۱۰ ثانیه عقب"
            LOCK_TOGGLE -> "قفل یا باز کردن کنترل حرکتی"
        }
}

object DefaultBindings {
    val map: Map<GestureTrigger, ControlAction> = mapOf(
        GestureTrigger.Hold(HandPose.FIST) to ControlAction.PLAY_PAUSE,
        GestureTrigger.Hold(HandPose.TWO) to ControlAction.NEXT,
        GestureTrigger.Hold(HandPose.ONE) to ControlAction.PREVIOUS,
        GestureTrigger.Hold(HandPose.THUMB_UP) to ControlAction.VOLUME_UP,
        GestureTrigger.Hold(HandPose.THUMB_DOWN) to ControlAction.VOLUME_DOWN,
        GestureTrigger.Hold(HandPose.PINKY) to ControlAction.MUTE_TOGGLE,
        GestureTrigger.Touch(Finger.INDEX, TouchKind.TAP) to ControlAction.PLAY_PAUSE,
        GestureTrigger.Touch(Finger.MIDDLE, TouchKind.TAP) to ControlAction.NEXT,
        GestureTrigger.Touch(Finger.RING, TouchKind.TAP) to ControlAction.PREVIOUS,
        GestureTrigger.Touch(Finger.PINKY, TouchKind.TAP) to ControlAction.MUTE_TOGGLE,
        GestureTrigger.Touch(Finger.INDEX, TouchKind.LONG_PRESS) to ControlAction.LOCK_TOGGLE,
    )

    fun encode(bindings: Map<GestureTrigger, ControlAction>): String =
        bindings.entries
            .filter { it.value != ControlAction.NONE }
            .sortedBy { it.key.id }
            .joinToString(";") { "${it.key.id}=${it.value.name}" }
            .ifEmpty { EMPTY }

    fun decode(text: String?): Map<GestureTrigger, ControlAction>? {
        if (text == null) return null
        if (text == EMPTY) return emptyMap()
        return text.split(';').mapNotNull { entry ->
            val parts = entry.split('=')
            if (parts.size != 2) return@mapNotNull null
            val trigger = GestureTrigger.parse(parts[0]) ?: return@mapNotNull null
            val action = runCatching { ControlAction.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
            if (action == ControlAction.NONE) null else trigger to action
        }.toMap()
    }

    private const val EMPTY = "-"
}
