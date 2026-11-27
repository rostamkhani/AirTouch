package com.airtouch.handcontrol.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor
import kotlin.math.min

/**
 * Binds CameraX analysis (and an optional preview) to [lifecycleOwner] and hands each analyzed
 * frame to [onFrame] as a bitmap that is upright for [uprightTurns] (quarter turns, same values as
 * `Surface.ROTATION_*`). Analysis and preview follow the screen rotation, so the picture and the
 * detected hand stay aligned when the phone turns.
 *
 * All public methods must be called on the main thread. [onFrame] runs on [analysisExecutor] and
 * must not keep the bitmap after it returns.
 */
class HandCameraSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val analysisExecutor: Executor,
    private val onFrame: (bitmap: Bitmap, timestampMs: Long, mirrored: Boolean, uprightTurns: Int) -> Unit,
    private val listener: Listener,
) {
    interface Listener {
        fun onBound(frontCamera: Boolean, usedFallback: Boolean) {}
        fun onError(problem: Problem) {}
    }

    enum class Problem { NO_CAMERA, BIND_FAILED, CAMERA_CLOSED }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val converter = UprightFrameConverter()
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var analysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null
    private var wantFront = true
    private var running = false
    private var rotation = Surface.ROTATION_0

    @Volatile private var boundFront = true
    @Volatile private var accepting = false
    @Volatile private var maxFps = 15
    @Volatile private var fpsCap: Int? = null
    @Volatile private var idle = false
    private var lastAcceptedAt = 0L

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val current = currentRotation()
            if (current == rotation) return
            rotation = current
            analysis?.targetRotation = current
            preview?.targetRotation = current
        }
    }

    fun start(frontCamera: Boolean, fps: Int) {
        if (running) return
        running = true
        wantFront = frontCamera
        maxFps = fps
        rotation = currentRotation()
        displayManager?.registerDisplayListener(displayListener, mainHandler)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (!running) return@addListener
                provider = runCatching { future.get() }.getOrNull()
                if (provider == null) listener.onError(Problem.BIND_FAILED) else bind()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun stop() {
        if (!running) return
        running = false
        accepting = false
        displayManager?.unregisterDisplayListener(displayListener)
        camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner)
        runCatching { provider?.unbindAll() }
        analysis?.clearAnalyzer()
        analysis = null
        preview = null
        camera = null
    }

    fun setFrontCamera(front: Boolean) {
        if (wantFront == front) return
        wantFront = front
        if (running) bind()
    }

    fun setPreviewSurface(provider: Preview.SurfaceProvider?) {
        if (surfaceProvider === provider) return
        surfaceProvider = provider
        if (running) bind()
    }

    fun setMaxFps(fps: Int) {
        maxFps = fps.coerceIn(1, 30)
    }

    /** Lower ceiling, for example while the phone is hot. Null removes it. */
    fun setFpsCap(cap: Int?) {
        fpsCap = cap
    }

    /** While idle (no hand for a while) analysis drops to a few frames per second. */
    fun setIdle(value: Boolean) {
        idle = value
    }

    private fun bind() {
        val cameraProvider = provider ?: return
        accepting = false
        camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner)
        runCatching { cameraProvider.unbindAll() }
        val hasFront = runCatching { cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
        val hasBack = runCatching { cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
        val useFront = when {
            wantFront && hasFront -> true
            !wantFront && hasBack -> false
            hasFront -> true
            hasBack -> false
            else -> {
                listener.onError(Problem.NO_CAMERA)
                return
            }
        }
        val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        val newAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(resolution)
            .setTargetRotation(rotation)
            .build()
        newAnalysis.setAnalyzer(analysisExecutor, ::analyze)
        val newPreview = surfaceProvider?.let { surface ->
            Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build(),
                )
                .setTargetRotation(rotation)
                .build()
                .also { it.surfaceProvider = surface }
        }
        analysis = newAnalysis
        preview = newPreview
        try {
            val bound = if (newPreview != null) {
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, newPreview, newAnalysis)
            } else {
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, newAnalysis)
            }
            camera = bound
            boundFront = useFront
            accepting = true
            bound.cameraInfo.cameraState.observe(lifecycleOwner) { state ->
                if (state.error != null && running) listener.onError(Problem.CAMERA_CLOSED)
            }
            listener.onBound(useFront, useFront != wantFront)
        } catch (error: Exception) {
            listener.onError(Problem.BIND_FAILED)
        }
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (!accepting) return
            val now = SystemClock.uptimeMillis()
            var fps = maxFps
            fpsCap?.let { fps = min(fps, it) }
            if (idle) fps = min(fps, IDLE_FPS)
            if (lastAcceptedAt != 0L && now - lastAcceptedAt < 1_000L / fps.coerceAtLeast(1)) return
            lastAcceptedAt = now
            val mirrored = boundFront
            val bitmap = converter.convert(image, mirrored)
            onFrame(bitmap, now, mirrored, rotation)
        } finally {
            image.close()
        }
    }

    private fun currentRotation(): Int =
        displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0

    private companion object {
        const val IDLE_FPS = 5
    }
}
