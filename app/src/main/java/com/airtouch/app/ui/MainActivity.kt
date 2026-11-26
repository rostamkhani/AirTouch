package com.airtouch.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airtouch.app.AirTouchApplication
import com.airtouch.app.LocaleHelper
import com.airtouch.app.service.HandTrackingService
import com.airtouch.app.ui.theme.AirTouchTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AirTouchApplication).container
        setContent {
            val viewModel = viewModel<AirTouchViewModel>(factory = AirTouchViewModel.factory(container))
            AirTouchTheme {
                AirTouchApp(
                    viewModel = viewModel,
                    onStartService = ::startTracking,
                    onStopService = ::stopTracking,
                )
            }
        }
    }

    private fun startTracking(allowBackground: Boolean) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val intent = Intent(this, HandTrackingService::class.java)
            .putExtra(HandTrackingService.EXTRA_ALLOW_BACKGROUND, allowBackground)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopTracking() {
        startService(
            Intent(this, HandTrackingService::class.java).setAction(HandTrackingService.ACTION_STOP),
        )
    }
}
