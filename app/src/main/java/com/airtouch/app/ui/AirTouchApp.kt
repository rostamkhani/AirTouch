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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airtouch.app.service.MediaNotificationListener
import com.airtouch.app.service.ServicePhase
import com.airtouch.app.ui.camera.CameraScreen
import com.airtouch.app.ui.gestures.GesturesScreen
import com.airtouch.app.ui.home.HomeScreen
import com.airtouch.app.ui.lab.LabScreen
import com.airtouch.app.ui.media.MediaScreen
import com.airtouch.app.ui.player.PlayerScreen
import com.airtouch.handcontrol.core.HandControlState
import kotlinx.coroutines.flow.MutableStateFlow

private enum class Page { ONBOARDING, HOME, LAB, GESTURES, PLAYER, MEDIA, CAMERA }

private val emptyState = MutableStateFlow(HandControlState.EMPTY)

@Composable
fun AirTouchApp(
    viewModel: AirTouchViewModel,
    onStartService: (Boolean) -> Unit,
    onStopService: () -> Unit,
    onUnlock: () -> Unit,
) {
    val context = LocalContext.current
    val container = viewModel.container
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val phase by viewModel.runtime.phase.collectAsStateWithLifecycle()
    val status by viewModel.runtime.statusText.collectAsStateWithLifecycle()
    val error by viewModel.runtime.errorText.collectAsStateWithLifecycle()
    val session by viewModel.runtime.session.collectAsStateWithLifecycle()
    val media by viewModel.runtime.media.collectAsStateWithLifecycle()
    val lastCommand by viewModel.runtime.lastCommand.collectAsStateWithLifecycle()
    val allowBackground by viewModel.runtime.allowBackground.collectAsStateWithLifecycle()
    val thermal by viewModel.runtime.thermalText.collectAsStateWithLifecycle()
    val cameraMessage by viewModel.runtime.cameraMessage.collectAsStateWithLifecycle()
    val locked by viewModel.runtime.locked.collectAsStateWithLifecycle()
    val recentTriggers by viewModel.runtime.recentTriggers.collectAsStateWithLifecycle()
    val player by container.player.state.collectAsStateWithLifecycle()
    val tracks by container.library.tracks.collectAsStateWithLifecycle()
    val hand by (session?.state ?: emptyState).collectAsState()
    val sessionStatus by (session?.status ?: MutableStateFlow(null)).collectAsState()

    var page by rememberSaveable(settings.onboardingDone) {
        mutableStateOf(if (settings.onboardingDone) Page.HOME else Page.ONBOARDING)
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onStartService(granted)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startAfterCamera(context, notificationLauncher, onStartService)
    }
    val start = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        } else {
            startAfterCamera(context, notificationLauncher, onStartService)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(allowBackground, phase) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && phase == ServicePhase.RUNNING && !allowBackground) onStopService()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        container.player.connect()
        onDispose { }
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
                    hand = hand,
                    locked = locked,
                    source = settings.playbackSource,
                    player = player,
                    media = media,
                    lastCommand = lastCommand,
                    onSource = viewModel::setSource,
                    onStart = start,
                    onStop = onStopService,
                    onUnlock = onUnlock,
                    onOpenLab = { page = Page.LAB },
                    onOpenGestures = { page = Page.GESTURES },
                    onOpenPlayer = { page = Page.PLAYER },
                    onOpenMedia = { page = Page.MEDIA },
                    onOpenCamera = { page = Page.CAMERA },
                )
                Page.LAB -> LabScreen(
                    session = session,
                    state = hand,
                    status = sessionStatus,
                    showPreview = settings.showPreview,
                    recentTriggers = recentTriggers,
                    holdMs = settings.holdMs,
                    onLabVisible = viewModel::setLabActive,
                    onStart = start,
                    onBack = { page = Page.HOME },
                )
                Page.GESTURES -> GesturesScreen(
                    settings = settings,
                    onBack = { page = Page.HOME },
                    onBind = viewModel::bind,
                    onHold = viewModel::setHold,
                    onCooldown = viewModel::setCooldown,
                    onSensitivity = viewModel::setSensitivity,
                    onReset = viewModel::resetGestures,
                )
                Page.PLAYER -> PlayerScreen(
                    connection = container.player,
                    library = container.library,
                    state = player,
                    tracks = tracks,
                    source = settings.playbackSource,
                    onSource = viewModel::setSource,
                    onBack = { page = Page.HOME },
                )
                Page.MEDIA -> MediaScreen(
                    settings = settings,
                    media = media,
                    listenerEnabled = listenerEnabled(context),
                    onBack = { page = Page.HOME },
                    onSource = viewModel::setSource,
                    onMode = viewModel::setTargetMode,
                    onPackage = viewModel::setManualPackage,
                    onFallback = viewModel::setFallback,
                )
                Page.CAMERA -> CameraScreen(
                    settings = settings,
                    session = session,
                    state = hand,
                    status = sessionStatus,
                    thermal = thermal,
                    message = cameraMessage,
                    onBack = { page = Page.HOME },
                    onFront = viewModel::setFrontCamera,
                    onPreview = viewModel::setPreview,
                    onFps = viewModel::setFps,
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
            "AirTouch تصویر دست را فقط روی همین گوشی پردازش می‌کند. تصویر ذخیره یا ارسال نمی‌شود. دوربین فقط با دکمه شروع روشن می‌شود.",
        )
        InfoCard(
            "حرکات",
            "همه فرمان‌ها با دست ثابت است: حالت‌هایی مثل مشت، عدد یک و دو، شست بالا، و لمس نوک شست با هر انگشت. در صفحه «آزمایش حرکات» می‌بینید برنامه هر حالت را درست می‌شناسد یا نه.",
        )
        InfoCard(
            "اعلان",
            "برای ماندن کنترل وقتی از برنامه بیرون می‌روید، یک اعلان دائمی نشان داده می‌شود. مجوز نمایش اعلان با دسترسی خواندن اعلان‌ها فرق دارد.",
        )
        InfoCard(
            "موسیقی",
            "AirTouch پخش‌کننده خودش را دارد. برای کنترل برنامه موسیقی دیگر، باید دسترسی اعلان AirTouch را در تنظیمات سیستم روشن کنید.",
        )
        WideButton("متوجه شدم", onClick = onDone)
    }
}

private fun startAfterCamera(
    context: Context,
    notificationLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onStartService: (Boolean) -> Unit,
) {
    val needsNotification = Build.VERSION.SDK_INT >= 33
    val granted = !needsNotification || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    if (needsNotification && !granted) {
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        onStartService(granted)
    }
}

private fun listenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val mine = ComponentName(context, MediaNotificationListener::class.java).flattenToString()
    return flat.split(":").any { it.equals(mine, ignoreCase = true) }
}
