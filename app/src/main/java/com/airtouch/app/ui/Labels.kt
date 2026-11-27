package com.airtouch.app.ui

import com.airtouch.app.service.ServicePhase

fun ServicePhase.labelFa(): String = when (this) {
    ServicePhase.STOPPED -> "دوربین خاموش"
    ServicePhase.STARTING -> "در حال آماده‌سازی"
    ServicePhase.RUNNING -> "کنترل حرکتی روشن"
    ServicePhase.STOPPING -> "در حال توقف"
    ServicePhase.ERROR -> "خطا"
}
