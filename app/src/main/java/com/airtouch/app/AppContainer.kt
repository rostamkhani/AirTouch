package com.airtouch.app

import android.content.Context
import com.airtouch.app.camera.CameraPipeline
import com.airtouch.app.media.CommandRouter
import com.airtouch.app.media.MediaSessionRepository
import com.airtouch.app.media.VolumeController
import com.airtouch.app.service.RuntimeState
import com.airtouch.app.settings.SettingsRepository

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settingsRepository = SettingsRepository(appContext)
    val runtime = RuntimeState()
    val mediaRepository = MediaSessionRepository(appContext, runtime)
    val volumeController = VolumeController(appContext)
    val commandRouter = CommandRouter(appContext, mediaRepository, volumeController, runtime)

    @Volatile
    var cameraPipeline: CameraPipeline? = null
}
