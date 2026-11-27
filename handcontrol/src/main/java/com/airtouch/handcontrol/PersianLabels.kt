package com.airtouch.handcontrol

import com.airtouch.handcontrol.core.Finger
import com.airtouch.handcontrol.core.GestureTrigger
import com.airtouch.handcontrol.core.HandPose
import com.airtouch.handcontrol.core.HandQuality
import com.airtouch.handcontrol.core.TouchKind

/** Persian names and short instructions for every pose, touch and trigger. */
object PersianLabels {
    fun pose(pose: HandPose): String = when (pose) {
        HandPose.OPEN_PALM -> "کف دست باز"
        HandPose.FIST -> "مشت"
        HandPose.ONE -> "عدد یک (اشاره)"
        HandPose.TWO -> "عدد دو (پیروزی)"
        HandPose.THREE -> "عدد سه"
        HandPose.FOUR -> "عدد چهار"
        HandPose.THUMB_UP -> "شست بالا"
        HandPose.THUMB_DOWN -> "شست پایین"
        HandPose.THUMB_SIDE -> "شست به پهلو"
        HandPose.PINKY -> "فقط انگشت کوچک"
        HandPose.ROCK -> "شاخ (اشاره و کوچک)"
        HandPose.LOVE -> "شست، اشاره و کوچک"
        HandPose.CALL -> "تلفن (شست و کوچک)"
        HandPose.L_SHAPE -> "حرف L (شست و اشاره)"
        HandPose.THREE_THUMB -> "سه با شست"
        HandPose.OK -> "علامت اوکی"
    }

    fun symbol(pose: HandPose): String = when (pose) {
        HandPose.OPEN_PALM -> "🖐"
        HandPose.FIST -> "✊"
        HandPose.ONE -> "☝"
        HandPose.TWO -> "✌"
        HandPose.THREE -> "3"
        HandPose.FOUR -> "4"
        HandPose.THUMB_UP -> "👍"
        HandPose.THUMB_DOWN -> "👎"
        HandPose.THUMB_SIDE -> "👍↔"
        HandPose.PINKY -> "ı"
        HandPose.ROCK -> "🤘"
        HandPose.LOVE -> "🤟"
        HandPose.CALL -> "🤙"
        HandPose.L_SHAPE -> "L"
        HandPose.THREE_THUMB -> "3👍"
        HandPose.OK -> "👌"
    }

    fun howTo(pose: HandPose): String = when (pose) {
        HandPose.OPEN_PALM -> "همه انگشت‌ها باز"
        HandPose.FIST -> "همه انگشت‌ها بسته"
        HandPose.ONE -> "فقط اشاره باز، بقیه بسته"
        HandPose.TWO -> "اشاره و میانی باز، بقیه بسته"
        HandPose.THREE -> "اشاره، میانی و انگشتری باز"
        HandPose.FOUR -> "چهار انگشت باز، شست بسته"
        HandPose.THUMB_UP -> "همه بسته، شست رو به بالا"
        HandPose.THUMB_DOWN -> "همه بسته، شست رو به پایین"
        HandPose.THUMB_SIDE -> "همه بسته، شست به چپ یا راست"
        HandPose.PINKY -> "همه بسته، فقط کوچک باز"
        HandPose.ROCK -> "اشاره و کوچک باز، شست بسته"
        HandPose.LOVE -> "شست، اشاره و کوچک باز"
        HandPose.CALL -> "شست و کوچک باز، بقیه بسته"
        HandPose.L_SHAPE -> "شست و اشاره باز، بقیه بسته"
        HandPose.THREE_THUMB -> "شست، اشاره و میانی باز"
        HandPose.OK -> "نوک شست و اشاره به هم، بقیه باز"
    }

    fun finger(finger: Finger): String = when (finger) {
        Finger.THUMB -> "شست"
        Finger.INDEX -> "اشاره"
        Finger.MIDDLE -> "میانی"
        Finger.RING -> "انگشتری"
        Finger.PINKY -> "کوچک"
    }

    fun touch(finger: Finger): String = "لمس شست و ${finger(finger)}"

    fun touchKind(kind: TouchKind): String = when (kind) {
        TouchKind.TAP -> "یک ضربه"
        TouchKind.DOUBLE_TAP -> "دو ضربه"
        TouchKind.LONG_PRESS -> "نگه‌داشتن"
    }

    fun trigger(trigger: GestureTrigger): String = when (trigger) {
        is GestureTrigger.Hold -> pose(trigger.pose)
        is GestureTrigger.Touch -> "${touch(trigger.finger)}: ${touchKind(trigger.kind)}"
        GestureTrigger.IndexClick -> "کلیک با اشاره"
    }

    fun quality(quality: HandQuality): String = when (quality) {
        HandQuality.NO_HAND -> "دستی دیده نمی‌شود"
        HandQuality.GOOD -> "دست واضح است"
        HandQuality.MOVING -> "دست در حال حرکت است؛ ثابت نگه دارید"
        HandQuality.TOO_SMALL -> "دست دور است؛ نزدیک‌تر بیاورید"
        HandQuality.PARTLY_OUTSIDE -> "بخشی از دست بیرون کادر است"
    }
}
