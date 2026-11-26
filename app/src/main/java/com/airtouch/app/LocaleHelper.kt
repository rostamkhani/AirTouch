package com.airtouch.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag("fa-IR")
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
