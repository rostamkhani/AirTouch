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
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.airtouch.app.AirTouchApplication
import com.airtouch.app.R
import com.airtouch.app.control.ControlAction
import com.airtouch.app.settings.toHandConfig
import com.airtouch.app.settings.trackerOptions
import com.airtouch.app.ui.MainActivity
import com.airtouch.handcontrol.HandControlSession
import com.airtouch.handcontrol.PersianLabels
import com.airtouch.handcontrol.core.Finger
import com.airtouch.handcontrol.core.GestureDispatcher
import com.airtouch.handcontrol.core.GestureTriggered
import com.airtouch.handcontrol.core.HandEvent
import com.airtouch.handcontrol.core.withHints
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Owns the camera while gesture control is on. Runs as a camera foreground service so control
 * keeps working while another app is open; stops when the screen turns off or the user stops it.
 */
class HandTrackingService : LifecycleService() {
    private val stopped = AtomicBoolean(false)
    private val container by lazy { (application as AirTouchApplication).container }
    private val runtime by lazy { container.runtime }
    private lateinit var session: HandControlSession
    private lateinit var dispatcher: GestureDispatcher<ControlAction>
    private var started = false
    private var foregroundStarted = false
    private var jobs = mutableListOf<Job>()
    private var lastNotification = ""
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var screenRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) shutdown(Reason.SCREEN)
        }
    }

    private val eventListener: (HandEvent) -> Unit = { event ->
        if (event is GestureTriggered) {
            runtime.noteTrigger(event.trigger.id, SystemClock.uptimeMillis())
            if (!runtime.labActive.value) {
                dispatcher.onEvent(event)
                runtime.locked.value = dispatcher.locked
                refreshNotification()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        session = HandControlSession(this, this)
        session.addListener(eventListener)
        dispatcher = GestureDispatcher(isRepeatable = { it.repeatable }) { action, trigger, _ ->
            if (action == ControlAction.LOCK_TOGGLE) runtime.locked.value = dispatcher.locked
            container.commandRouter.perform(action, PersianLabels.trigger(trigger))
        }
        dispatcher.lockAction = ControlAction.LOCK_TOGGLE
        dispatcher.setLocked(runtime.locked.value)
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
            ACTION_STOP, ACTION_LOCK, ACTION_UNLOCK -> {
                if (intent.action != ACTION_STOP) setLocked(intent.action == ACTION_LOCK)
                if (intent.action == ACTION_STOP || !started) shutdown(Reason.USER)
            }
            else -> {
                if (stopped.get()) return START_NOT_STICKY
                runtime.allowBackground.value = intent?.getBooleanExtra(EXTRA_ALLOW_BACKGROUND, false) ?: false
                if (!foregroundStarted) promote()
                if (!started) {
                    started = true
                    lifecycleScope.launch { begin() }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        shutdown(Reason.DESTROY)
        super.onDestroy()
    }

    private suspend fun begin() {
        if (stopped.get()) return
        runtime.phase.value = ServicePhase.STARTING
        runtime.errorText.value = null
        runtime.session.value = session
        val initial = container.settingsRepository.settings.first()
        if (stopped.get()) return
        jobs += lifecycleScope.launch {
            combine(container.settingsRepository.settings, runtime.labActive) { settings, lab -> settings to lab }
                .collect { (settings, lab) ->
                    dispatcher.bindings = settings.bindings
                    dispatcher.cooldownMs = settings.cooldownMs
                    val hints = dispatcher.engineHints()
                    // The test page shows double taps for every finger, so it always waits for them.
                    val config = settings.toHandConfig().withHints(
                        if (lab) hints.copy(doubleTapFingers = Finger.TOUCHABLE.toSet()) else hints,
                    )
                    session.updateConfig(config)
                    session.updateTrackerOptions(settings.trackerOptions())
                    session.setFrontCamera(settings.useFrontCamera)
                    session.setMaxFps(settings.targetFps)
                    container.commandRouter.updateSettings(settings)
                }
        }
        jobs += lifecycleScope.launch {
            session.status.collect { status ->
                val problem = status.problem
                when {
                    problem != null -> fail(problemText(problem))
                    status.running -> {
                        runtime.phase.value = ServicePhase.RUNNING
                        runtime.cameraMessage.value = if (status.usedFallbackCamera) {
                            "دوربین انتخاب‌شده نیست؛ دوربین دیگر استفاده شد"
                        } else {
                            null
                        }
                    }
                }
            }
        }
        jobs += lifecycleScope.launch {
            combine(
                session.state.map { it.handPresent to it.pose }.distinctUntilChanged(),
                runtime.locked,
                runtime.labActive,
            ) { hand, locked, lab -> Triple(hand, locked, lab) }
                .collect { refreshNotification() }
        }
        session.start(initial.useFrontCamera, initial.targetFps)
    }

    private fun setLocked(value: Boolean) {
        runtime.locked.value = value
        if (::dispatcher.isInitialized) dispatcher.setLocked(value)
        refreshNotification()
    }

    private fun statusText(): String {
        val state = session.state.value
        return when {
            runtime.labActive.value -> "صفحه آزمایش باز است؛ فرمانی اجرا نمی‌شود"
            runtime.locked.value -> "کنترل حرکتی قفل است"
            !state.handPresent -> "فعال؛ دستی دیده نمی‌شود"
            state.pose != null -> "دست: ${PersianLabels.pose(state.pose!!)}"
            else -> "دست دیده می‌شود"
        }
    }

    private fun refreshNotification() {
        if (!foregroundStarted || stopped.get()) return
        val text = statusText()
        runtime.statusText.value = text
        if (text == lastNotification) return
        lastNotification = text
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun promote() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            val channel = NotificationChannel(CHANNEL, "کنترل حرکتی AirTouch", NotificationManager.IMPORTANCE_LOW)
            channel.description = "روشن بودن دوربین و کنترل حرکتی را نشان می‌دهد"
            manager.createNotificationChannel(channel)
        }
        val text = "در حال آماده‌سازی"
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        foregroundStarted = true
        lastNotification = text
    }

    private fun buildNotification(text: String): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags,
        )
        val stop = PendingIntent.getService(this, 2, Intent(this, HandTrackingService::class.java).setAction(ACTION_STOP), flags)
        val locked = runtime.locked.value
        val lock = PendingIntent.getService(
            this,
            3,
            Intent(this, HandTrackingService::class.java).setAction(if (locked) ACTION_UNLOCK else ACTION_LOCK),
            flags,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_air)
            .setContentTitle("AirTouch")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stat_air, if (locked) "باز کردن قفل" else "قفل حرکات", lock)
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
                status >= PowerManager.THERMAL_STATUS_SEVERE -> shutdown(Reason.THERMAL)
                status >= PowerManager.THERMAL_STATUS_MODERATE -> session.setFpsCap(8)
                else -> session.setFpsCap(null)
            }
        }
        thermalListener = listener
        power.addThermalStatusListener(ContextCompat.getMainExecutor(this), listener)
        runtime.thermalText.value = thermalLabel(power.currentThermalStatus)
    }

    private fun thermalLabel(status: Int): String = when {
        status >= PowerManager.THERMAL_STATUS_SEVERE -> "دمای گوشی بالا است"
        status >= PowerManager.THERMAL_STATUS_MODERATE -> "گوشی گرم شده و نرخ پردازش کم شده"
        else -> "دمای گوشی عادی است"
    }

    private fun problemText(problem: HandControlSession.Problem): String = when (problem) {
        HandControlSession.Problem.MODEL_MISSING -> "مدل تشخیص دست داخل برنامه پیدا نشد"
        HandControlSession.Problem.MODEL_FAILED -> "مدل تشخیص دست باز نشد"
        HandControlSession.Problem.NO_CAMERA -> "این گوشی دوربین قابل استفاده ندارد"
        HandControlSession.Problem.CAMERA_FAILED -> "دوربین در دسترس نیست یا برنامه دیگری آن را گرفته"
    }

    private fun fail(message: String) {
        runtime.errorText.value = message
        shutdown(Reason.ERROR)
        runtime.phase.value = ServicePhase.ERROR
        runtime.statusText.value = message
    }

    private fun shutdown(reason: Reason) {
        if (!stopped.compareAndSet(false, true)) return
        runtime.phase.value = ServicePhase.STOPPING
        jobs.forEach { it.cancel() }
        jobs.clear()
        if (::session.isInitialized) {
            session.removeListener(eventListener)
            session.release()
        }
        if (screenRegistered) runCatching { unregisterReceiver(screenReceiver) }
        screenRegistered = false
        if (Build.VERSION.SDK_INT >= 29) {
            thermalListener?.let { listener ->
                runCatching { getSystemService(PowerManager::class.java).removeThermalStatusListener(listener) }
            }
        }
        runtime.resetAfterStop()
        if (reason == Reason.THERMAL) runtime.errorText.value = "دما بالا رفت و دوربین برای محافظت از گوشی خاموش شد"
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private enum class Reason { USER, SCREEN, THERMAL, ERROR, DESTROY }

    companion object {
        const val ACTION_STOP = "com.airtouch.app.STOP"
        const val ACTION_LOCK = "com.airtouch.app.LOCK"
        const val ACTION_UNLOCK = "com.airtouch.app.UNLOCK"
        const val EXTRA_ALLOW_BACKGROUND = "allow_background"
        private const val CHANNEL = "airtouch_control"
        private const val NOTIFICATION_ID = 42
    }
}
