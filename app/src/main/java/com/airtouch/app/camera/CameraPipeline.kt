package com.airtouch.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class CameraPipeline(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrame: (bitmap: Bitmap, timestampMs: Long, frontCamera: Boolean) -> Unit,
    private val onBusyCleared: () -> Unit,
    private val onError: (String) -> Unit,
    private val onBound: (front: Boolean, message: String?) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null
    private var wantFront = true
    private var boundFront = true
    private var targetFps = 15
    private var thermalCap: Int? = null
    private var noHandCap = false
    private var rotation = Surface.ROTATION_0
    private var running = false
    private var accepting = false
    private val busy = AtomicBoolean(false)
    private var lastAcceptedAt = 0L
    private var generation = 0
    private var lastHandAt = 0L
    var onGeneration: (Int) -> Unit = {}

    fun start(front: Boolean, fps: Int) {
        wantFront = front
        targetFps = fps
        lastHandAt = android.os.SystemClock.uptimeMillis()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    provider = future.get()
                    running = true
                    rebind()
                } catch (error: Exception) {
                    onError(cameraMessage(error))
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun apply(front: Boolean, fps: Int) {
        val cameraChanged = front != wantFront
        wantFront = front
        targetFps = fps
        if (running && cameraChanged) {
            mainHandler.post { rebind() }
        }
    }

    fun setPreview(provider: Preview.SurfaceProvider?) {
        mainHandler.post {
            surfaceProvider = provider
            if (running) rebind()
        }
    }

    fun setTargetRotation(newRotation: Int) {
        mainHandler.post {
            if (rotation == newRotation) return@post
            rotation = newRotation
            if (running) rebind()
        }
    }

    fun setThermalCap(cap: Int?) {
        thermalCap = cap
    }

    fun noteHand(present: Boolean, nowMs: Long) {
        if (present) {
            lastHandAt = nowMs
            noHandCap = false
        } else if (lastHandAt != 0L && nowMs - lastHandAt > 3_000) {
            noHandCap = true
        }
    }

    fun markIdle() {
        busy.set(false)
        onBusyCleared()
    }

    fun shutdown() {
        running = false
        accepting = false
        mainHandler.post {
            provider?.unbindAll()
            analysis = null
            preview = null
        }
        cameraExecutor.shutdown()
    }

    private fun rebind() {
        val cameraProvider = provider ?: return
        generation += 1
        onGeneration(generation)
        accepting = false
        cameraProvider.unbindAll()
        val frontAvailable = runCatching { cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
        val backAvailable = runCatching { cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
        val useFront = when {
            wantFront && frontAvailable -> true
            !wantFront && backAvailable -> false
            frontAvailable -> true
            backAvailable -> false
            else -> {
                onError("این گوشی دوربین قابل استفاده ندارد")
                return
            }
        }
        val switched = useFront != wantFront
        val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .setTargetRotation(rotation)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(cameraExecutor, ::analyze)
            }
        val boundPreview = surfaceProvider?.let { surface ->
            Preview.Builder()
                .setTargetRotation(rotation)
                .build()
                .also { it.surfaceProvider = surface }
        }
        preview = boundPreview
        try {
            if (boundPreview != null) {
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, boundPreview, analysis)
            } else {
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, analysis)
            }
            accepting = true
            boundFront = useFront
            val message = if (switched) "دوربین انتخاب‌شده موجود نیست؛ دوربین دیگر استفاده می‌شود" else null
            onBound(useFront, message)
        } catch (error: Exception) {
            onError(cameraMessage(error))
        }
    }

    private fun analyze(proxy: androidx.camera.core.ImageProxy) {
        try {
            if (!accepting) return
            if (!busy.compareAndSet(false, true)) return
            val now = android.os.SystemClock.uptimeMillis()
            val interval = 1_000L / effectiveFps().coerceAtLeast(1)
            if (lastAcceptedAt != 0L && now - lastAcceptedAt < interval) {
                busy.set(false)
                return
            }
            lastAcceptedAt = now
            val bitmap = proxy.toBitmap()
            onFrame(bitmap, now, boundFront)
        } catch (error: Exception) {
            busy.set(false)
            onError(cameraMessage(error))
        } finally {
            proxy.close()
        }
    }

    private fun effectiveFps(): Int {
        var fps = targetFps
        thermalCap?.let { fps = min(fps, it) }
        if (noHandCap) fps = min(fps, 5)
        return max(fps, 1)
    }

    private fun cameraMessage(error: Throwable): String {
        val name = error.javaClass.simpleName
        return when {
            name.contains("Security", ignoreCase = true) -> "مجوز دوربین یا سرویس پیش‌زمینه رد شد"
            name.contains("Camera", ignoreCase = true) -> "دوربین در دسترس نیست یا توسط سیستم بسته شده"
            else -> "بازکردن دوربین ممکن نشد"
        }
    }
}
