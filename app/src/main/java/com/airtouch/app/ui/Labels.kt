package com.airtouch.app.ui

import com.airtouch.app.gestures.EngineState
import com.airtouch.app.gestures.Finger
import com.airtouch.app.gestures.FingerPose
import com.airtouch.app.gestures.GestureCandidate
import com.airtouch.app.gestures.GestureCommand
import com.airtouch.app.gestures.Handedness
import com.airtouch.app.service.ServicePhase

fun GestureCommand.labelFa(): String = when (this) {
    GestureCommand.NEXT -> "آهنگ بعدی"
    GestureCommand.PREVIOUS -> "آهنگ قبلی"
    GestureCommand.PLAY_PAUSE -> "پخش یا توقف"
    GestureCommand.VOLUME_UP -> "زیاد کردن صدا"
    GestureCommand.VOLUME_DOWN -> "کم کردن صدا"
    GestureCommand.MUTE_TOGGLE -> "بی‌صدا یا بازگشت صدا"
    GestureCommand.SUSPEND -> "تعلیق کنترل"
    GestureCommand.RESUME -> "ادامه کنترل"
}

fun GestureCommand.hintFa(): String = when (this) {
    GestureCommand.NEXT -> "کف دست باز را یک‌بار به راست ببرید و برگردانید"
    GestureCommand.PREVIOUS -> "کف دست باز را یک‌بار به چپ ببرید و برگردانید"
    GestureCommand.PLAY_PAUSE -> "نوک شست را به نوک اشاره بزنید و جدا کنید"
    GestureCommand.VOLUME_UP -> "کف دست باز را یک‌بار بالا ببرید"
    GestureCommand.VOLUME_DOWN -> "کف دست باز را یک‌بار پایین بیاورید"
    GestureCommand.MUTE_TOGGLE -> "نوک شست را به نوک انگشت میانی بزنید"
    GestureCommand.SUSPEND -> "مشت را حدود یک ثانیه نگه دارید. موسیقی قطع نمی‌شود"
    GestureCommand.RESUME -> "در حالت تعلیق، کف دست را حدود یک ثانیه باز نگه دارید"
}

fun EngineState.labelFa(): String = when (this) {
    EngineState.WAITING_FOR_HAND -> "در انتظار دست"
    EngineState.STABILIZING -> "در حال پایدار شدن"
    EngineState.READY -> "آماده فرمان"
    EngineState.PINCH_LATCHED -> "تماس انگشت ثبت شد"
    EngineState.WAITING_FOR_NEUTRAL -> "در انتظار بازگشت به نقطه شروع"
    EngineState.SUSPENDED -> "کنترل معلق است"
}

fun GestureCandidate.labelFa(): String = when (this) {
    GestureCandidate.NONE -> "حرکتی در جریان نیست"
    GestureCandidate.SWIPE_RIGHT -> "حرکت به راست"
    GestureCandidate.SWIPE_LEFT -> "حرکت به چپ"
    GestureCandidate.SWIPE_UP -> "حرکت به بالا"
    GestureCandidate.SWIPE_DOWN -> "حرکت به پایین"
    GestureCandidate.PINCH_INDEX -> "تماس شست و اشاره"
    GestureCandidate.PINCH_MIDDLE -> "تماس شست و میانی"
    GestureCandidate.AMBIGUOUS_PINCH -> "تماس مبهم"
    GestureCandidate.FIST -> "مشت"
    GestureCandidate.OPEN_PALM -> "کف دست باز"
    GestureCandidate.DIAGONAL -> "حرکت مورب رد شد"
}

fun Finger.labelFa(): String = when (this) {
    Finger.THUMB -> "شست"
    Finger.INDEX -> "اشاره"
    Finger.MIDDLE -> "میانی"
    Finger.RING -> "انگشتر"
    Finger.PINKY -> "کوچک"
}

fun FingerPose.labelFa(): String = when (this) {
    FingerPose.OPEN -> "باز"
    FingerPose.CLOSED -> "بسته"
    FingerPose.UNKNOWN -> "نامشخص"
}

fun Handedness.labelFa(): String = when (this) {
    Handedness.LEFT -> "چپ"
    Handedness.RIGHT -> "راست"
    Handedness.UNKNOWN -> "نامشخص"
}

fun ServicePhase.labelFa(): String = when (this) {
    ServicePhase.STOPPED -> "دوربین خاموش"
    ServicePhase.STARTING -> "در حال آماده‌سازی"
    ServicePhase.RUNNING -> "دوربین فعال"
    ServicePhase.STOPPING -> "در حال توقف"
    ServicePhase.ERROR -> "خطا"
}
