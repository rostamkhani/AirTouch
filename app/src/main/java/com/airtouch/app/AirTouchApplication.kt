package com.airtouch.app

import android.app.Application
import android.content.Context

class AirTouchApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
