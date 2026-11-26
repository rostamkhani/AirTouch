package com.airtouch.handcontrol.mediapipe

import android.content.Context
import android.graphics.Bitmap
import com.airtouch.handcontrol.core.HandFrame
import com.airtouch.handcontrol.core.Handedness
import com.airtouch.handcontrol.core.Vec3
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.Closeable

/**
 * Synchronous MediaPipe Hand Landmarker in VIDEO mode. Call [detect] from one background thread;
 * it blocks until the frame is processed, so the camera naturally drops frames while busy.
 */
class HandLandmarkerRunner(
    context: Context,
    val options: Options = Options(),
) : Closeable {
    data class Options(
        val modelAssetPath: String = DEFAULT_MODEL_ASSET,
        val minDetectionConfidence: Float = 0.5f,
        val minPresenceConfidence: Float = 0.5f,
        val minTrackingConfidence: Float = 0.5f,
    )

    private val landmarker: HandLandmarker
    private var lastTimestamp = 0L

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath(options.modelAssetPath)
            .setDelegate(Delegate.CPU)
            .build()
        val taskOptions = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            .setMinHandDetectionConfidence(options.minDetectionConfidence)
            .setMinHandPresenceConfidence(options.minPresenceConfidence)
            .setMinTrackingConfidence(options.minTrackingConfidence)
            .build()
        landmarker = HandLandmarker.createFromOptions(context, taskOptions)
    }

    /**
     * [bitmap] must already be upright and mirrored as shown to the user. [mirrored] tells whether
     * it was mirrored, which MediaPipe needs for its left/right label.
     */
    fun detect(bitmap: Bitmap, timestampMs: Long, mirrored: Boolean): HandFrame? {
        val timestamp = maxOf(timestampMs, lastTimestamp + 1)
        lastTimestamp = timestamp
        val result = landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), timestamp)
        return toFrame(result, bitmap.width, bitmap.height, mirrored, timestampMs)
    }

    override fun close() {
        landmarker.close()
    }

    companion object {
        const val DEFAULT_MODEL_ASSET = "hand_landmarker.task"

        fun toFrame(
            result: HandLandmarkerResult,
            width: Int,
            height: Int,
            mirrored: Boolean,
            timestampMs: Long,
        ): HandFrame? {
            val hand = result.landmarks().firstOrNull() ?: return null
            if (hand.size != 21) return null
            val image = hand.map { Vec3(it.x(), it.y(), it.z()) }
            val world = result.worldLandmarks().firstOrNull()
                ?.takeIf { it.size == 21 }
                ?.map { Vec3(it.x(), it.y(), it.z()) }
                .orEmpty()
            val category = result.handedness().firstOrNull()?.firstOrNull()
            val label = when (category?.categoryName()?.lowercase()) {
                "left" -> Handedness.LEFT
                "right" -> Handedness.RIGHT
                else -> Handedness.UNKNOWN
            }
            // MediaPipe labels hands as if the image were a mirrored selfie.
            val handedness = when {
                mirrored || label == Handedness.UNKNOWN -> label
                label == Handedness.LEFT -> Handedness.RIGHT
                else -> Handedness.LEFT
            }
            return HandFrame(
                timestampMs = timestampMs,
                imageWidth = width,
                imageHeight = height,
                image = image,
                world = world,
                handedness = handedness,
                score = category?.score() ?: 1f,
            )
        }
    }
}
