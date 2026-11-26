package com.airtouch.app.handtracking

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.airtouch.app.gestures.HandSample
import com.airtouch.app.gestures.Handedness
import com.airtouch.app.gestures.Landmark2
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.ConcurrentHashMap

class HandTracker(
    private val context: Context,
    private val onSample: (HandSample?) -> Unit,
    private val onIdle: () -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private var landmarker: HandLandmarker? = null
    private val inflight = ConcurrentHashMap<Long, PendingFrame>()
    private var closed = false

    fun open(confidence: Float) {
        synchronized(this) {
            if (closed) return
            landmarker?.close()
            val base = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(confidence)
                .setMinHandPresenceConfidence(confidence)
                .setMinTrackingConfidence(confidence)
                .setResultListener { result, _ -> deliver(result) }
                .setErrorListener { error ->
                    Log.w(TAG, "hand landmarker error: ${error.javaClass.simpleName}")
                    clearInflight()
                    onIdle()
                    onFailure("مدل تشخیص دست خطا داد")
                }
                .build()
            landmarker = HandLandmarker.createFromOptions(context, options)
        }
    }

    fun submit(bitmap: Bitmap, timestampMs: Long, frontCamera: Boolean, sampleGeneration: Int) {
        if (closed) {
            bitmap.recycle()
            onIdle()
            return
        }
        val safeTimestamp = timestampMs.coerceAtLeast(1L)
        inflight[safeTimestamp] = PendingFrame(
            bitmap = bitmap,
            width = bitmap.width,
            height = bitmap.height,
            mirror = frontCamera,
            generation = sampleGeneration,
        )
        try {
            val image = BitmapImageBuilder(bitmap).build()
            synchronized(this) {
                val active = landmarker
                if (active == null) {
                    recycle(safeTimestamp)
                    onSample(null)
                    onIdle()
                    return
                }
                active.detectAsync(image, safeTimestamp)
            }
        } catch (error: Exception) {
            Log.w(TAG, "detect failed: ${error.javaClass.simpleName}")
            recycle(safeTimestamp)
            onIdle()
            onFailure("پردازش تصویر ممکن نشد")
        }
    }

    fun close() {
        closed = true
        synchronized(this) {
            landmarker?.close()
            landmarker = null
        }
        clearInflight()
    }

    private fun deliver(result: com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult) {
        val timestamp = result.timestampMs()
        val frame = inflight[timestamp]
        recycle(timestamp)
        try {
            val hands = result.landmarks()
            if (hands.isEmpty() || frame == null) {
                onSample(null)
                return
            }
            val points = hands.first().map { landmark ->
                val x = if (frame.mirror) 1f - landmark.x() else landmark.x()
                Landmark2(x, landmark.y(), landmark.z())
            }
            val world = result.worldLandmarks().firstOrNull().orEmpty().map { landmark ->
                Landmark2(landmark.x(), landmark.y(), landmark.z())
            }
            val name = result.handedness().firstOrNull()?.firstOrNull()?.categoryName().orEmpty()
            val handedness = when {
                name.equals("Left", ignoreCase = true) -> Handedness.LEFT
                name.equals("Right", ignoreCase = true) -> Handedness.RIGHT
                else -> Handedness.UNKNOWN
            }
            onSample(
                HandSample(
                    timestampMs = timestamp,
                    landmarks = points,
                    worldLandmarks = world,
                    handedness = handedness,
                    imageWidth = frame.width.coerceAtLeast(1),
                    imageHeight = frame.height.coerceAtLeast(1),
                    generation = frame.generation,
                ),
            )
        } catch (error: Exception) {
            Log.w(TAG, "result parse failed: ${error.javaClass.simpleName}")
            onSample(null)
        } finally {
            onIdle()
        }
    }

    private fun recycle(timestamp: Long) {
        inflight.remove(timestamp)?.let { frame ->
            if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
        }
    }

    private data class PendingFrame(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val mirror: Boolean,
        val generation: Int,
    )

    private fun clearInflight() {
        inflight.keys.toList().forEach(::recycle)
    }

    companion object {
        private const val TAG = "AirTouchHand"
        const val MODEL_ASSET = "hand_landmarker.task"
    }
}
