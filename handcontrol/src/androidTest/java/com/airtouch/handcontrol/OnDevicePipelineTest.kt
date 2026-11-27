package com.airtouch.handcontrol

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airtouch.handcontrol.camera.UprightFrameConverter
import com.airtouch.handcontrol.core.HandControlEngine
import com.airtouch.handcontrol.core.HandControlState
import com.airtouch.handcontrol.core.HandFrame
import com.airtouch.handcontrol.core.HandPose
import com.airtouch.handcontrol.mediapipe.HandLandmarkerRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Runs the real Android MediaPipe model, the upright/mirror converter and the engine on real hand
 * photos (HaGRID crops, CC BY-SA 4.0) on a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class OnDevicePipelineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private val expected = mapOf(
        "palm" to setOf(HandPose.OPEN_PALM),
        "fist" to setOf(HandPose.FIST),
        "peace" to setOf(HandPose.TWO),
        "like" to setOf(HandPose.THUMB_UP),
        "dislike" to setOf(HandPose.THUMB_DOWN),
        "ok" to setOf(HandPose.OK),
        "one" to setOf(HandPose.ONE),
        "call" to setOf(HandPose.CALL),
        "rock" to setOf(HandPose.ROCK, HandPose.LOVE),
        "four" to setOf(HandPose.FOUR, HandPose.OPEN_PALM),
    )

    private fun photos(): List<Pair<String, Bitmap>> {
        val assets = instrumentation.context.assets
        return assets.list("hands").orEmpty().filter { it.endsWith(".jpg") }.sorted().map { name ->
            val bitmap = assets.open("hands/$name").use { BitmapFactory.decodeStream(it) }
            name to bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    private fun settle(bitmap: Bitmap, mirrored: Boolean = false): Pair<HandControlState, HandFrame?> {
        HandLandmarkerRunner(context).use { runner ->
            val engine = HandControlEngine()
            var state = HandControlState.EMPTY
            var last: HandFrame? = null
            var now = 0L
            repeat(12) {
                val frame = runner.detect(bitmap, now, mirrored)
                if (frame != null) last = frame
                state = engine.process(frame, now)
                now += 66L
            }
            return state to last
        }
    }

    @Test
    fun recognizesRealPhotosOnDevice() {
        val results = photos().map { (name, bitmap) ->
            val label = name.substringBeforeLast('_')
            val state = settle(bitmap).first
            Log.i(TAG, "$name -> ${state.pose} touches=${state.touches} quality=${state.quality}")
            state.pose in expected.getValue(label)
        }
        val accuracy = results.count { it }.toFloat() / results.size
        Log.i(TAG, "on-device accuracy ${(accuracy * 100).toInt()}% of ${results.size}")
        assertTrue("on-device accuracy $accuracy", accuracy >= 0.8f)
    }

    @Test
    fun rotatedSensorFramesComeOutUpright() {
        val converter = UprightFrameConverter()
        for ((name, upright) in photos().filterIndexed { index, _ -> index % 3 == 0 }) {
            val reference = settle(upright).second
            assertNotNull("$name has a hand", reference)
            for (sensorRotation in listOf(90, 180, 270)) {
                // A sensor frame that needs [sensorRotation] degrees clockwise to look upright.
                val sensor = rotate(upright, -sensorRotation)
                val fixed = converter.transform(sensor, sensorRotation, mirror = false).copy(Bitmap.Config.ARGB_8888, false)
                assertEquals(upright.width, fixed.width)
                assertEquals(upright.height, fixed.height)
                val frame = settle(fixed).second
                assertNotNull("$name at $sensorRotation", frame)
                val error = meanDistance(reference!!, frame!!) { it }
                Log.i(TAG, "$name rotation $sensorRotation mean landmark error $error")
                assertTrue("$name at $sensorRotation is off by $error", error < 0.03f)
            }
        }
    }

    @Test
    fun mirroredFrontCameraMatchesThePreview() {
        val converter = UprightFrameConverter()
        val cases = photos().filterIndexed { index, _ -> index % 3 == 1 }
        var compared = 0
        for ((name, upright) in cases) {
            val (plainState, plain) = settle(upright, mirrored = false)
            val mirroredBitmap = converter.transform(upright, 0, mirror = true).copy(Bitmap.Config.ARGB_8888, false)
            val (mirroredState, mirrored) = settle(mirroredBitmap, mirrored = true)
            if (plain == null || mirrored == null) {
                Log.i(TAG, "$name: model found no hand (plain=${plain != null}, mirrored=${mirrored != null})")
                continue
            }
            compared += 1
            val error = meanDistance(plain, mirrored) { x -> 1f - x }
            Log.i(TAG, "$name mirror error $error, ${plainState.pose} vs ${mirroredState.pose}")
            assertTrue("$name mirrored landmarks are off by $error", error < 0.03f)
            assertEquals("$name pose", plainState.pose, mirroredState.pose)
            assertEquals("$name handedness", plain.handedness, mirrored.handedness)
        }
        assertTrue("only $compared of ${cases.size} mirrored photos were detected", compared >= cases.size * 0.8f)
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun meanDistance(a: HandFrame, b: HandFrame, mapX: (Float) -> Float): Float {
        var total = 0f
        for (i in a.image.indices) {
            total += abs(a.image[i].x - mapX(b.image[i].x)) + abs(a.image[i].y - b.image[i].y)
        }
        return total / (a.image.size * 2)
    }

    private companion object {
        const val TAG = "AirTouchDeviceTest"
    }
}
