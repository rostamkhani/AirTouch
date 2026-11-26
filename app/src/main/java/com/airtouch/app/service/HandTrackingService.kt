package com.airtouch.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.airtouch.app.AirTouchApplication
import com.airtouch.app.R
import com.airtouch.app.camera.CameraPipeline
import com.airtouch.app.gestures.EngineState
import com.airtouch.app.gestures.GestureEngine
import com.airtouch.app.gestures.HandSample
import com.airtouch.app.handtracking.HandTracker
import com.airtouch.app.settings.modelConfidence
import com.airtouch.app.settings.toGestureConfig
import com.airtouch.app.ui.MainActivity
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HandTrackingService : LifecycleService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stopped = AtomicBoolean(false)
    private val appContainer by lazy { (application as AirTouchApplication).container }
    private val runtime by lazy { appContainer.runtime }
    private lateinit var engine: GestureEngine
    private lateinit var tracker: HandTracker
    private var pipeline: CameraPipeline? = null
    private var settingsJob: Job? = null
    private var watchJob: Job? = null
    private var started = false
    private var foregroundStarted = false
    private var allowBackground = false
    @Volatile private var generation = 0
    private var pendingConfidence: Float? = null
    private var appliedConfidence = -1f
    private var lastNotification = ""
    private val cameraRate = RateCounter()
    private val analysisRate = RateCounter()
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var screenRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) shutdown("screen")
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = GestureEngine { event ->
            mainHandler.post { appContainer.commandRouter.onGesture(event) }
        }
        tracker = HandTracker(
            context = this,
            onSample = { sample -> mainHandler.post { onHand(sample) } },
            onIdle = { mainHandler.post { onTrackerIdle() } },
            onFailure = { message -> mainHandler.post { fail(message) } },
        )
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
        screenRegistered = true
        watchThermal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> shutdown("user")
            ACTION_SUSPEND -> engine.suspendFromUser(SystemClock.uptimeMillis())
            ACTION_RESUME -> engine.resumeFromUser(SystemClock.uptimeMillis())
            else -> {
                allowBackground = intent?.getBooleanExtra(EXTRA_ALLOW_BACKGROUND, false) ?: false
                runtime.allowBackground.value = allowBackground
                if (!foregroundStarted) promote("در حال آماده‌سازی")
                mainHandler.post { ensureStarted() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        shutdown("destroy")
        super.onDestroy()
    }

    private fun ensureStarted() {
        if (started || stopped.get()) return
        started = true
        runtime.phase.value = ServicePhase.STARTING
        runtime.errorText.value = null
        appContainer.mediaRepository.onTargetChanged = {
            generation += 1
            engine.setGeneration(generation)
        }
        if (!modelPresent()) {
            fail("مدل تشخیص دست داخل برنامه پیدا نشد")
            return
        }
        pipeline = CameraPipeline(
            context = this,
            lifecycleOwner = this,
            onFrame = { bitmap, timestamp, front ->
                runtime.resolutionText.value = "${bitmap.width}×${bitmap.height}"
                runtime.cameraFps.value = cameraRate.mark(timestamp)
                tracker.submit(bitmap, timestamp, front, generation)
            },
            onBusyCleared = {},
            onError = { message -> mainHandler.post { fail(message) } },
            onBound = { front, message ->
                mainHandler.post {
                    runtime.phase.value = ServicePhase.RUNNING
                    runtime.usingFrontCamera.value = front
                    runtime.cameraMessage.value = message
                    refreshStatus()
                }
            },
        ).also { created ->
            created.onGeneration = { value ->
                generation = value
                engine.setGeneration(value)
            }
            appContainer.cameraPipeline = created
        }
        settingsJob = scope.launch {
            appContainer.settingsRepository.settings.collect { settings ->
                engine.updateConfig(settings.toGestureConfig())
                appContainer.commandRouter.updateSettings(settings)
                pipeline?.apply(settings.useFrontCamera, settings.targetFps)
                if (appliedConfidence < 0f) {
                    appliedConfidence = settings.modelConfidence()
                    val opened = withContext(Dispatchers.Default) {
                        runCatching { tracker.open(appliedConfidence) }.isSuccess
                    }
                    if (!opened) {
                        fail("مدل تشخیص دست باز نشد")
                        return@collect
                    }
                    pipeline?.start(settings.useFrontCamera, settings.targetFps)
                } else if (settings.modelConfidence() != appliedConfidence) {
                    pendingConfidence = settings.modelConfidence()
                }
            }
        }
        watchJob = scope.launch {
            while (isActive) {
                delay(100)
                val now = SystemClock.uptimeMillis()
                engine.heartbeat(now)
                pipeline?.noteHand(engine.snapshot.handPresent, now)
                runtime.hand.value = engine.snapshot
                if (runtime.phase.value == ServicePhase.RUNNING) refreshStatus()
            }
        }
    }

    private fun onHand(sample: HandSample?) {
        val now = SystemClock.uptimeMillis()
        if (sample != null) {
            engine.onSample(sample, now)
            runtime.analysisFps.value = analysisRate.mark(now)
        }
        runtime.hand.value = engine.snapshot
        refreshStatus()
    }

    private fun onTrackerIdle() {
        pipeline?.markIdle()
        val confidence = pendingConfidence ?: return
        pendingConfidence = null
        appliedConfidence = confidence
        scope.launch(Dispatchers.Default) {
            runCatching { tracker.open(confidence) }
            engine.setGeneration(++generation)
        }
    }

    private fun refreshStatus() {
        val hand = engine.snapshot
        val listening = runtime.media.value.listenerConnected
        val text = when {
            hand.state == EngineState.SUSPENDED -> "کنترل معلق است"
            !hand.handPresent -> "فعال؛ دستی دیده نمی‌شود"
            !listening -> "دست دیده شد؛ دسترسی رسانه قطع است"
            else -> "دست شناسایی شد"
        }
        runtime.statusText.value = text
        if (text != lastNotification && foregroundStarted) {
            lastNotification = text
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification(text, hand.state == EngineState.SUSPENDED))
        }
    }

    private fun promote(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            val channel = NotificationChannel(CHANNEL, "کنترل حرکتی AirTouch", NotificationManager.IMPORTANCE_LOW)
            channel.description = "روشن بودن دوربین و کنترل حرکتی را نشان می‌دهد"
            manager.createNotificationChannel(channel)
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(text, suspended = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
        )
        foregroundStarted = true
        lastNotification = text
    }

    private fun buildNotification(text: String, suspended: Boolean): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, HandTrackingService::class.java).setAction(ACTION_STOP),
            flags,
        )
        val toggle = PendingIntent.getService(
            this,
            3,
            Intent(this, HandTrackingService::class.java).setAction(if (suspended) ACTION_RESUME else ACTION_SUSPEND),
            flags,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_air)
            .setContentTitle("AirTouch")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stat_air, if (suspended) "ادامه" else "تعلیق", toggle)
            .addAction(R.drawable.ic_stat_air, "توقف دوربین", stop)
            .build()
    }

    private fun watchThermal() {
        if (Build.VERSION.SDK_INT < 29) {
            runtime.thermalText.value = "در این نسخه اندروید گزارش دما نیست"
            return
        }
        val power = getSystemService(PowerManager::class.java)
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            runtime.thermalText.value = thermalLabel(status)
            when {
                status >= PowerManager.THERMAL_STATUS_SEVERE -> shutdown("thermal")
                status >= PowerManager.THERMAL_STATUS_MODERATE -> pipeline?.setThermalCap(5)
                else -> pipeline?.setThermalCap(null)
            }
        }
        thermalListener = listener
        power.addThermalStatusListener(ContextCompat.getMainExecutor(this), listener)
        runtime.thermalText.value = thermalLabel(power.currentThermalStatus)
    }

    private fun thermalLabel(status: Int): String = when {
        Build.VERSION.SDK_INT < 29 -> "در این نسخه اندروید گزارش دما نیست"
        status >= PowerManager.THERMAL_STATUS_SEVERE -> "دمای گوشی بالا است"
        status >= PowerManager.THERMAL_STATUS_MODERATE -> "گوشی گرم شده و نرخ پردازش کم شده"
        else -> "دمای گوشی عادی است"
    }

    private fun modelPresent(): Boolean {
        return runCatching { assets.open(HandTracker.MODEL_ASSET).close() }.isSuccess
    }

    private fun fail(message: String) {
        runtime.phase.value = ServicePhase.ERROR
        runtime.errorText.value = message
        runtime.statusText.value = message
        shutdown("error")
    }

    private fun shutdown(reason: String) {
        if (!stopped.compareAndSet(false, true)) return
        runtime.phase.value = ServicePhase.STOPPING
        settingsJob?.cancel()
        watchJob?.cancel()
        pipeline?.shutdown()
        if (appContainer.cameraPipeline === pipeline) appContainer.cameraPipeline = null
        tracker.close()
        if (::engine.isInitialized) engine.setGeneration(generation + 1)
        if (screenRegistered) runCatching { unregisterReceiver(screenReceiver) }
        if (Build.VERSION.SDK_INT >= 29) {
            thermalListener?.let { listener ->
                runCatching {
                    getSystemService(PowerManager::class.java).removeThermalStatusListener(listener)
                }
            }
        }
        appContainer.commandRouter.close()
        scope.cancel()
        if (reason == "thermal") {
            runtime.errorText.value = "دما بالا رفت و دوربین برای محافظت از گوشی خاموش شد"
        }
        runtime.resetAfterStop()
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    companion object {
        const val ACTION_STOP = "com.airtouch.app.STOP"
        const val ACTION_SUSPEND = "com.airtouch.app.SUSPEND"
        const val ACTION_RESUME = "com.airtouch.app.RESUME"
        const val EXTRA_ALLOW_BACKGROUND = "allow_background"
        private const val CHANNEL = "airtouch_control"
        private const val NOTIFICATION_ID = 42
    }
}

private class RateCounter {
    private val stamps = ArrayDeque<Long>()

    fun mark(now: Long): Float {
        stamps.addLast(now)
        while (stamps.isNotEmpty() && now - stamps.first() > 1_000) stamps.removeFirst()
        return stamps.size.toFloat()
    }
}
