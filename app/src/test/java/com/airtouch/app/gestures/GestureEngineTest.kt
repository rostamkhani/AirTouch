package com.airtouch.app.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HandGeometryTest {
    @Test
    fun openPalmHasExtendedFingersOnBothHands() {
        val right = features(HandFixtures.openRight(), Handedness.RIGHT)
        val left = features(HandFixtures.mirror(HandFixtures.openRight()), Handedness.LEFT)
        listOf(right, left).forEach { hand ->
            Finger.entries.forEach { finger ->
                assertEquals(hand.fingers.toString(), FingerPose.OPEN, hand.fingers[finger])
            }
        }
    }

    @Test
    fun fistIsClosedAndPinchRatiosStayApart() {
        val fist = features(HandFixtures.fistRight(), Handedness.RIGHT)
        listOf(Finger.INDEX, Finger.MIDDLE, Finger.RING, Finger.PINKY).forEach { finger ->
            assertEquals(FingerPose.CLOSED, fist.fingers[finger])
        }
        assertTrue(fist.fingers[Finger.THUMB] != FingerPose.OPEN)
        val pinch = features(HandFixtures.pinchIndex(), Handedness.RIGHT)
        assertTrue(pinch.pinchIndexRatio < 0.25f)
        assertTrue(pinch.pinchMiddleRatio > 0.25f)
        assertEquals(FingerPose.OPEN, pinch.fingers[Finger.MIDDLE])
    }

    @Test
    fun wideImageKeepsASmallPinchSmall() {
        val pinch = HandGeometry.features(
            HandFixtures.sample(
                timestampMs = 0,
                landmarks = HandFixtures.pinchIndex(),
                width = 1920,
                height = 1080,
            ),
        )!!
        assertTrue(pinch.pinchIndexRatio < 0.25f)
    }

    private fun features(landmarks: List<Landmark2>, handedness: Handedness): HandFeatures {
        return HandGeometry.features(HandFixtures.sample(0, landmarks, handedness))!!
    }
}

class GestureEngineTest {
    private val events = mutableListOf<GestureEvent>()
    private val engine = GestureEngine(testConfig(), events::add)

    @Test
    fun swipeRightThenReturnDoesNotEmitTheOpposite() {
        feed(still(0, HandFixtures.openRight()))
        feed(move(400, 0f, 0.36f, 0f))
        assertEquals(listOf(GestureCommand.NEXT), commands())
        feed(move(1_000, 0.36f, 0f, 0f))
        assertEquals(listOf(GestureCommand.NEXT), commands())
        settle(1_700, HandFixtures.openRight())
        assertEquals(EngineState.READY, engine.snapshot.state)
    }

    @Test
    fun swipeLeftUpAndDownEachFireOnce() {
        assertSwipe(0L, 0f, -0.36f, 0f, GestureCommand.PREVIOUS)
        events.clear()
        engine.updateConfig(testConfig())
        assertSwipe(8_000L, 0f, 0f, -0.36f, GestureCommand.VOLUME_UP)
        events.clear()
        engine.updateConfig(testConfig())
        assertSwipe(16_000L, 0f, 0f, 0.36f, GestureCommand.VOLUME_DOWN)
    }

    @Test
    fun pinchFiresOnceUntilReleaseAndPressAgain() {
        feed(still(0, HandFixtures.pinchIndex()))
        assertTrue(commands().isEmpty())
        assertEquals(EngineState.PINCH_LATCHED, engine.snapshot.state)
        feed(still(700, HandFixtures.openRight(), step = 80, count = 4))
        feed(still(1_200, HandFixtures.pinchIndex(), step = 50, count = 4))
        assertEquals(listOf(GestureCommand.PLAY_PAUSE), commands())
        feed(still(1_500, HandFixtures.pinchIndex(), step = 100, count = 8))
        assertEquals(listOf(GestureCommand.PLAY_PAUSE), commands())
    }

    @Test
    fun middlePinchMutesAndAmbiguousContactDoesNothing() {
        arm(HandFixtures.openRight())
        feed(still(700, HandFixtures.pinchMiddle(), step = 50, count = 4))
        assertEquals(listOf(GestureCommand.MUTE_TOGGLE), commands())
        events.clear()
        engine.updateConfig(testConfig())
        arm(HandFixtures.openRight(), start = 3_000)
        feed(still(3_700, HandFixtures.ambiguousPinch(), step = 40, count = 6))
        assertTrue(commands().isEmpty())
    }

    @Test
    fun fistSuspendsAndOpenPalmResumesAfterHandLoss() {
        feed(still(0, HandFixtures.fistRight(), step = 100, count = 16))
        assertEquals(GestureCommand.SUSPEND, commands().single())
        engine.heartbeat(2_200)
        assertEquals(EngineState.SUSPENDED, engine.snapshot.state)
        feed(still(2_400, HandFixtures.openRight(), step = 100, count = 12))
        assertEquals(listOf(GestureCommand.SUSPEND, GestureCommand.RESUME), commands())
        assertEquals(EngineState.READY, engine.snapshot.state)
    }

    @Test
    fun cooldownDoesNotStoreAGestureForLater() {
        arm(HandFixtures.openRight())
        feed(move(400, 0f, 0.36f, 0f))
        feed(still(800, HandFixtures.pinchIndex(), step = 40, count = 5))
        feed(still(1_100, HandFixtures.openRight(), step = 50, count = 6))
        engine.heartbeat(2_600)
        assertEquals(listOf(GestureCommand.NEXT), commands())
    }

    @Test
    fun diagonalJitterAndDisabledCommandStayQuiet() {
        arm(HandFixtures.openRight())
        feed(move(400, 0f, 0.30f, 0.28f))
        assertTrue(commands().isEmpty())
        val random = Random(7)
        val jitter = (0 until 3_000).map { index ->
            val dx = (random.nextFloat() - 0.5f) * 0.016f
            val dy = (random.nextFloat() - 0.5f) * 0.016f
            HandFixtures.sample(5_000L + index * 100L, HandFixtures.openRight(dx, dy))
        }
        jitter.forEach { engine.onSample(it, it.timestampMs) }
        assertTrue(commands().isEmpty())
        engine.updateConfig(testConfig().copy(enabled = emptySet()))
        arm(HandFixtures.openRight(), start = 400_000)
        feed(move(400_400, 0f, 0.36f, 0f))
        assertTrue(commands().isEmpty())
    }

    @Test
    fun staleSampleOldGenerationAndLostHandDoNotCommand() {
        val fresh = HandFixtures.sample(1_000, HandFixtures.openRight())
        engine.onSample(fresh, 1_400)
        assertEquals(EngineState.WAITING_FOR_HAND, engine.snapshot.state)
        engine.setGeneration(3)
        engine.onSample(fresh.copy(timestampMs = 2_000, generation = 2), 2_000)
        assertTrue(commands().isEmpty())
        arm(HandFixtures.openRight(), start = 3_000)
        engine.heartbeat(3_700)
        assertEquals(EngineState.WAITING_FOR_HAND, engine.snapshot.state)
        assertNull(engine.snapshot.pinchIndex)
    }

    @Test
    fun leftHandSwipeUsesMirroredImageCoordinates() {
        val pose = HandFixtures.mirror(HandFixtures.openRight())
        feed(still(0, pose, handedness = Handedness.LEFT))
        val moved = (0 until 5).map { step ->
            val progress = step / 4f
            HandFixtures.sample(
                timestampMs = 400L + step * 80L,
                landmarks = HandFixtures.mirror(HandFixtures.openRight(dx = progress * 0.36f)),
                handedness = Handedness.LEFT,
            )
        }
        moved.forEach { engine.onSample(it, it.timestampMs) }
        assertEquals(listOf(GestureCommand.PREVIOUS), commands())
    }

    private fun assertSwipe(start: Long, from: Float, dx: Float, dy: Float, command: GestureCommand) {
        arm(HandFixtures.openRight(from, 0f), start)
        feed(move(start + 400, from, dx, dy))
        assertEquals(listOf(command), commands())
    }

    private fun arm(pose: List<Landmark2>, start: Long = 0, handedness: Handedness = Handedness.RIGHT) {
        feed(still(start, pose, handedness = handedness).map { it.copy(generation = engine.generation) })
        assertEquals(EngineState.READY, engine.snapshot.state)
    }

    private fun settle(start: Long, pose: List<Landmark2>) {
        feed(still(start, pose, step = 50, count = 8))
    }

    private fun feed(samples: List<HandSample>) {
        samples.forEach { engine.onSample(it, it.timestampMs) }
    }

    private fun still(
        start: Long,
        pose: List<Landmark2>,
        step: Long = 150,
        count: Int = 3,
        handedness: Handedness = Handedness.RIGHT,
    ): List<HandSample> {
        return List(count) { index ->
            HandFixtures.sample(start + index * step, pose, handedness)
        }
    }

    private fun move(start: Long, fromX: Float, dx: Float, dy: Float): List<HandSample> {
        return List(5) { step ->
            val progress = step / 4f
            HandFixtures.sample(
                timestampMs = start + step * 80L,
                landmarks = HandFixtures.openRight(fromX + dx * progress, dy * progress),
            )
        }
    }

    private fun commands(): List<GestureCommand> = events.map { it.command }

    private fun testConfig(): GestureConfig = GestureConfig(smoothingAlpha = 1f)
}
