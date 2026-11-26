package com.airtouch.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airtouch.app.service.ExecutionMode
import com.airtouch.app.service.MediaNotificationListener
import com.airtouch.app.service.ServicePhase
import com.airtouch.app.ui.camera.CameraScreen
import com.airtouch.app.ui.diagnostics.DiagnosticsScreen
import com.airtouch.app.ui.gestures.GesturesScreen
import com.airtouch.app.ui.home.HomeScreen
import com.airtouch.app.ui.media.MediaScreen

private enum class Page {
    ONBOARDING,
    HOME,
    CAMERA,
    GESTURES,
    LAB,
    MEDIA,
}

@Composable
fun AirTouchApp(
    viewModel: AirTouchViewModel,
    onStartService: (Boolean) -> Unit,
    onStopService: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val phase by viewModel.runtime.phase.collectAsStateWithLifecycle()
    val status by viewModel.runtime.statusText.collectAsStateWithLifecycle()
    val error by viewModel.runtime.errorText.collectAsStateWithLifecycle()
    val hand by viewModel.runtime.hand.collectAsStateWithLifecycle()
    val media by viewModel.runtime.media.collectAsStateWithLifecycle()
    val mode by viewModel.runtime.executionMode.collectAsStateWithLifecycle()
    val allowBackground by viewModel.runtime.allowBackground.collectAsStateWithLifecycle()
    val thermal by viewModel.runtime.thermalText.collectAsStateWithLifecycle()
    val resolution by viewModel.runtime.resolutionText.collectAsStateWithLifecycle()
    val cameraFps by viewModel.runtime.cameraFps.collectAsStateWithLifecycle()
    val analysisFps by viewModel.runtime.analysisFps.collectAsStateWithLifecycle()
    val cameraMessage by viewModel.runtime.cameraMessage.collectAsStateWithLifecycle()
    var page by remember(settings.onboardingDone) {
        mutableStateOf(if (settings.onboardingDone) Page.HOME else Page.ONBOARDING)
    }
    var pendingBackground by remember { mutableStateOf(true) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onStartService(granted)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) return@rememberLauncherForActivityResult
        startAfterCamera(context, pendingBackground, notificationLauncher, onStartService)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(allowBackground, phase) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && phase == ServicePhase.RUNNING && !allowBackground) {
                onStopService()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(page) {
        if (page == Page.LAB) viewModel.setExecutionMode(ExecutionMode.DRY_RUN)
    }
    if (page != Page.HOME && page != Page.ONBOARDING) {
        BackHandler { page = Page.HOME }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(Modifier.fillMaxSize()) {
            when (page) {
                Page.ONBOARDING -> OnboardingScreen {
                    viewModel.finishOnboarding()
                    page = Page.HOME
                }
                Page.HOME -> HomeScreen(
                    phase = phase,
                    status = status,
                    error = error,
                    cameraMessage = cameraMessage,
                    media = media,
                    executionMode = mode,
                    handSeen = hand.handPresent,
                    onStart = {
                        pendingBackground = true
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            startAfterCamera(context, true, notificationLauncher, onStartService)
                        }
                    },
                    onStop = onStopService,
                    onEnableControl = { viewModel.setExecutionMode(ExecutionMode.CONTROL) },
                    onOpenCamera = { page = Page.CAMERA },
                    onOpenGestures = { page = Page.GESTURES },
                    onOpenLab = { page = Page.LAB },
                    onOpenMedia = { page = Page.MEDIA },
                )
                Page.CAMERA -> CameraScreen(
                    settings = settings,
                    pipeline = viewModel.runtime.let { (context.applicationContext as com.airtouch.app.AirTouchApplication).container.cameraPipeline },
                    resolution = resolution,
                    cameraFps = cameraFps,
                    analysisFps = analysisFps,
                    thermal = thermal,
                    message = cameraMessage,
                    onBack = { page = Page.HOME },
                    onFront = viewModel::setFrontCamera,
                    onPreview = viewModel::setPreview,
                    onFps = viewModel::setFps,
                    onSensitivity = viewModel::setSensitivity,
                )
                Page.GESTURES -> GesturesScreen(
                    settings = settings,
                    onBack = { page = Page.HOME },
                    onToggle = viewModel::setCommand,
                    onCooldown = viewModel::setCooldown,
                    onFeedback = viewModel::setFeedback,
                    onReset = viewModel::resetSettings,
                )
                Page.LAB -> DiagnosticsScreen(
                    settings = settings,
                    pipeline = (context.applicationContext as com.airtouch.app.AirTouchApplication).container.cameraPipeline,
                    hand = hand,
                    cameraFps = cameraFps,
                    analysisFps = analysisFps,
                    onBack = { page = Page.HOME },
                )
                Page.MEDIA -> MediaScreen(
                    settings = settings,
                    media = media,
                    listenerEnabled = listenerEnabled(context),
                    onBack = { page = Page.HOME },
                    onMode = viewModel::setTargetMode,
                    onPackage = viewModel::setManualPackage,
                    onFallback = viewModel::setFallback,
                )
            }
        }
    }
}

@Composable
private fun OnboardingScreen(onDone: () -> Unit) {
    ScreenColumn {
        PageTitle("قبل از شروع")
        InfoCard(
            "دوربین",
            "AirTouch تصویر دست را فقط روی گوشی پردازش می‌کند. تصویر ذخیره یا ارسال نمی‌شود. دوربین فقط با دکمه شروع روشن می‌شود.",
        )
        InfoCard(
            "اعلان",
            "برای ماندن کنترل وقتی برنامه موسیقی را باز می‌کنید، یک اعلان دائمی نشان داده می‌شود. مجوز نمایش اعلان با دسترسی خواندن اعلان‌ها فرق دارد.",
        )
        InfoCard(
            "موسیقی",
            "کنترل پخش‌کننده‌های دیگر به روشن‌کردن دسترسی اعلان AirTouch در تنظیمات سیستم نیاز دارد. اگر آن را ندهید، آزمایش دست و صدای خود گوشی همچنان ممکن است.",
        )
        WideButton("متوجه شدم", onClick = onDone)
    }
}

private fun startAfterCamera(
    context: Context,
    wantsBackground: Boolean,
    notificationLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onStartService: (Boolean) -> Unit,
) {
    val needsNotification = Build.VERSION.SDK_INT >= 33
    val granted = !needsNotification || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    if (wantsBackground && needsNotification && !granted) {
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        onStartService(wantsBackground && granted)
    }
}

private fun listenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val mine = ComponentName(context, MediaNotificationListener::class.java).flattenToString()
    return flat.split(":").any { it.equals(mine, ignoreCase = true) }
}
