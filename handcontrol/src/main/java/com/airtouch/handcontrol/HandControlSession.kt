package com.airtouch.handcontrol

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.airtouch.handcontrol.camera.HandCameraSource
import com.airtouch.handcontrol.core.HandControlConfig
import com.airtouch.handcontrol.core.HandControlEngine
import com.airtouch.handcontrol.core.HandControlState
import com.airtouch.handcontrol.core.HandEvent
import com.airtouch.handcontrol.mediapipe.HandLandmarkerRunner
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Camera + MediaPipe + [HandControlEngine] in one object.
 *
 * Create it with the lifecycle that should own the camera (an Activity, or a LifecycleService for
 * background use), call [start], and observe [state] for live tiles/overlays and [events] (or
 * [addListener]) for triggers. Call public methods on the main thread; listeners are called on the
 * main thread too. Call [release] when done.
 */
class HandControlSession(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    config: HandControlConfig = HandControlConfig(),
    trackerOptions: HandLandmarkerRunner.Options = HandLandmarkerRunner.Options(),
) {
    enum class Problem { MODEL_MISSING, MODEL_FAILED, NO_CAMERA, CAMERA_FAILED }

    data class Status(
        val running: Boolean = false,
        val starting: Boolean = false,
        val frontCamera: Boolean = true,
        val usedFallbackCamera: Boolean = false,
        val problem: Problem? = null,
        val analysisFps: Int = 0,
        val inferenceMs: Long = 0L,
        val frameWidth: Int = 0,
        val frameHeight: Int = 0,
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HandControl")
    }
    private val listeners = CopyOnWriteArrayList<(HandEvent) -> Unit>()
    private val pending = ArrayList<HandEvent>()
    private val engine = HandControlEngine(config) { pending += it }

    private val mutableState = MutableStateFlow(HandControlState.EMPTY)
    val state: StateFlow<HandControlState> = mutableState.asStateFlow()

    private val mutableStatus = MutableStateFlow(Status())
    val status: StateFlow<Status> = mutableStatus.asStateFlow()

    private val mutableEvents = MutableSharedFlow<HandEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<HandEvent> = mutableEvents.asSharedFlow()

    private var options = trackerOptions
    private var runner: HandLandmarkerRunner? = null
    @Volatile private var camera: HandCameraSource? = null
    private var previewSurface: Preview.SurfaceProvider? = null
    private var frontCamera = true
    private var maxFps = 15
    private var fpsCap: Int? = null
    private var released = false

    @Volatile private var active = false
    private var lastHandAt = 0L
    private val frameTimes = ArrayDeque<Long>()
    private var lastStatusAt = 0L

    fun addListener(listener: (HandEvent) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (HandEvent) -> Unit) {
        listeners -= listener
    }

    fun start(frontCamera: Boolean = this.frontCamera, fps: Int = maxFps) {
        check(!released) { "session released" }
        if (active) return
        active = true
        this.frontCamera = frontCamera
        maxFps = fps
        mutableStatus.value = Status(starting = true, frontCamera = frontCamera)
        if (!modelPresent()) {
            fail(Problem.MODEL_MISSING)
            return
        }
        executor.execute {
            if (runner == null) {
                runner = try {
                    HandLandmarkerRunner(appContext, options)
                } catch (error: Throwable) {
                    Log.w(TAG, "model open failed: ${error.javaClass.simpleName}")
                    null
                }
            }
            val ready = runner != null
            mainHandler.post {
                if (!active) return@post
                if (ready) startCamera() else fail(Problem.MODEL_FAILED)
            }
        }
    }

    fun stop() {
        if (!active) return
        active = false
        camera?.stop()
        camera = null
        executor.execute {
            engine.reset()
            runner?.close()
            runner = null
            pending.clear()
            mutableState.value = HandControlState.EMPTY
        }
        val problem = mutableStatus.value.problem
        mutableStatus.value = Status(frontCamera = frontCamera, problem = problem)
    }

    fun release() {
        if (released) return
        stop()
        released = true
        listeners.clear()
        executor.shutdown()
    }

    fun setFrontCamera(front: Boolean) {
        frontCamera = front
        camera?.setFrontCamera(front)
    }

    fun setMaxFps(fps: Int) {
        maxFps = fps
        camera?.setMaxFps(fps)
    }

    fun setFpsCap(cap: Int?) {
        fpsCap = cap
        camera?.setFpsCap(cap)
    }

    /** Attach a PreviewView's surface provider, or null to stop showing the preview. */
    fun setPreviewSurface(provider: Preview.SurfaceProvider?) {
        previewSurface = provider
        camera?.setPreviewSurface(provider)
    }

    fun updateConfig(config: HandControlConfig) {
        if (released) return
        executor.execute { engine.updateConfig(config) }
    }

    /** Reopens the model with new confidence settings if they changed. */
    fun updateTrackerOptions(newOptions: HandLandmarkerRunner.Options) {
        if (released || newOptions == options) return
        options = newOptions
        executor.execute {
            val current = runner ?: return@execute
            current.close()
            runner = runCatching { HandLandmarkerRunner(appContext, newOptions) }.getOrNull()
            if (runner == null) mainHandler.post { fail(Problem.MODEL_FAILED) }
        }
    }

    private fun startCamera() {
        val source = HandCameraSource(
            context = appContext,
            lifecycleOwner = lifecycleOwner,
            analysisExecutor = executor,
            onFrame = ::onFrame,
            listener = object : HandCameraSource.Listener {
                override fun onBound(frontCamera: Boolean, usedFallback: Boolean) {
                    mutableStatus.value = mutableStatus.value.copy(
                        running = true,
                        starting = false,
                        frontCamera = frontCamera,
                        usedFallbackCamera = usedFallback,
                        problem = null,
                    )
                }

                override fun onError(problem: HandCameraSource.Problem) {
                    fail(if (problem == HandCameraSource.Problem.NO_CAMERA) Problem.NO_CAMERA else Problem.CAMERA_FAILED)
                }
            },
        )
        camera = source
        source.setFpsCap(fpsCap)
        source.setPreviewSurface(previewSurface)
        source.start(frontCamera, maxFps)
    }

    private fun onFrame(bitmap: android.graphics.Bitmap, timestampMs: Long, mirrored: Boolean, uprightTurns: Int) {
        if (!active) return
        val current = runner ?: return
        val started = SystemClock.uptimeMillis()
        val frame = try {
            current.detect(bitmap, timestampMs, mirrored)
                ?.copy(uprightRotation = uprightTurns)
                ?.rotatedForScreen(screenRotation())
        } catch (error: RuntimeException) {
            Log.w(TAG, "detect failed: ${error.javaClass.simpleName}")
            null
        }
        val inference = SystemClock.uptimeMillis() - started
        pending.clear()
        mutableState.value = engine.process(frame, timestampMs)
        if (pending.isNotEmpty()) {
            val batch = pending.toList()
            mainHandler.post { deliver(batch) }
        }
        if (frame != null) lastHandAt = timestampMs
        camera?.setIdle(timestampMs - lastHandAt > IDLE_AFTER_MS)
        frameTimes.addLast(timestampMs)
        while (frameTimes.isNotEmpty() && timestampMs - frameTimes.first() > 1_000L) frameTimes.removeFirst()
        if (timestampMs - lastStatusAt >= 500L) {
            lastStatusAt = timestampMs
            mutableStatus.value = mutableStatus.value.copy(
                analysisFps = frameTimes.size,
                inferenceMs = inference,
                frameWidth = bitmap.width,
                frameHeight = bitmap.height,
            )
        }
    }

    private fun deliver(batch: List<HandEvent>) {
        if (!active) return
        for (event in batch) {
            mutableEvents.tryEmit(event)
            for (listener in listeners) listener(event)
        }
    }

    private fun fail(problem: Problem) {
        val wasActive = active
        stop()
        if (wasActive || problem == Problem.MODEL_MISSING) {
            mutableStatus.value = Status(frontCamera = frontCamera, problem = problem)
        }
    }

    private fun modelPresent(): Boolean =
        runCatching { appContext.assets.open(options.modelAssetPath).close() }.isSuccess

    private fun screenRotation(): Int =
        appContext.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0

    private companion object {
        const val TAG = "HandControl"
        const val IDLE_AFTER_MS = 3_000L
    }
}
