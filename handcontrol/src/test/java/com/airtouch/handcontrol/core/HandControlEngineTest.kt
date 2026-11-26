package com.airtouch.handcontrol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandControlEngineTest {
    private fun hold(pose: HandPose) = GestureTrigger.Hold(pose).id
    private fun touch(finger: Finger, kind: TouchKind) = GestureTrigger.Touch(finger, kind).id

    @Test
    fun heldPoseFiresOnceAfterHoldTime() {
        val timeline = Timeline()
        timeline.hold(Frames.fist, 500)
        assertTrue(timeline.triggers().isEmpty())
        timeline.hold(Frames.fist, 1_500)
        assertEquals(listOf(hold(HandPose.FIST)), timeline.triggerIds())
        val fired = timeline.triggers().single()
        assertTrue("fired at ${fired.timestampMs}", fired.timestampMs in 600L..760L)
    }

    @Test
    fun repeatingPoseRepeatsOnlyWhenConfigured() {
        val plain = Timeline()
        plain.hold(Frames.like, 2_500)
        assertEquals(listOf(hold(HandPose.THUMB_UP)), plain.triggerIds())

        val repeating = Timeline(HandControlConfig(repeatPoses = setOf(HandPose.THUMB_UP)))
        repeating.hold(Frames.like, 2_500)
        val repeats = repeating.triggers().map { it.repeat }
        assertEquals(0, repeats.first())
        assertTrue("repeats $repeats", repeats.size in 4..5)
        assertEquals(repeats.indices.toList(), repeats)
    }

    @Test
    fun indexTouchTapFiresOnceOnRelease() {
        val timeline = Timeline()
        timeline.hold(Frames.palm, 300)
        timeline.hold(Frames.ok, 200)
        assertTrue(timeline.triggers().none { it.trigger is GestureTrigger.Touch })
        timeline.hold(Frames.palm, 700)
        assertEquals(listOf(touch(Finger.INDEX, TouchKind.TAP)), timeline.triggerIds())
    }

    @Test
    fun longTouchIsLongPressNotTap() {
        val timeline = Timeline()
        timeline.hold(Frames.palm, 300)
        timeline.hold(Frames.ok, 1_300)
        timeline.hold(Frames.palm, 700)
        val ids = timeline.triggerIds()
        assertEquals(1, ids.count { it == touch(Finger.INDEX, TouchKind.LONG_PRESS) })
        assertTrue(ids.none { it == touch(Finger.INDEX, TouchKind.TAP) })
    }

    @Test
    fun doubleTapReplacesTwoTapsWhenEnabled() {
        val timeline = Timeline(HandControlConfig(doubleTapFingers = setOf(Finger.INDEX)))
        timeline.hold(Frames.palm, 300)
        timeline.hold(Frames.ok, 150)
        timeline.hold(Frames.palm, 150)
        timeline.hold(Frames.ok, 150)
        timeline.hold(Frames.palm, 800)
        assertEquals(listOf(touch(Finger.INDEX, TouchKind.DOUBLE_TAP)), timeline.triggerIds().filter { it.startsWith("touch:") })
    }

    @Test
    fun singleTapWaitsForDoubleTapWindowWhenEnabled() {
        val timeline = Timeline(HandControlConfig(doubleTapFingers = setOf(Finger.INDEX)))
        timeline.hold(Frames.palm, 300)
        timeline.hold(Frames.ok, 150)
        timeline.hold(Frames.palm, 200)
        assertTrue(timeline.triggers().none { it.trigger is GestureTrigger.Touch })
        timeline.hold(Frames.palm, 600)
        assertEquals(listOf(touch(Finger.INDEX, TouchKind.TAP)), timeline.triggerIds().filter { it.startsWith("touch:") })
    }

    @Test
    fun eachFingertipTouchIsSeparate() {
        for (finger in Finger.TOUCHABLE) {
            val timeline = Timeline()
            timeline.hold(Frames.palm, 300)
            val state = timeline.hold(Frames.touching(Frames.palm, finger), 200)
            assertEquals("touch set for $finger", setOf(finger), state.touches)
            timeline.hold(Frames.palm, 600)
            assertEquals("$finger", listOf(touch(finger, TouchKind.TAP)), timeline.triggerIds().filter { it.startsWith("touch:") })
        }
    }

    @Test
    fun flickeringShapesNeverFire() {
        val timeline = Timeline()
        repeat(30) {
            timeline.hold(Frames.fist, 66)
            timeline.hold(Frames.palm, 66)
        }
        assertTrue(timeline.triggerIds().toString(), timeline.triggers().none { it.trigger is GestureTrigger.Hold })
    }

    @Test
    fun movingHandWaitsUntilStill() {
        val timeline = Timeline()
        var x = -0.15f
        repeat(15) {
            timeline.hold(Frames.translated(Frames.fist, x, 0f), 66)
            x += if (it % 2 == 0) 0.12f else -0.10f
        }
        assertTrue(timeline.triggers().isEmpty())
        val stillFrom = timeline.now
        timeline.hold(Frames.fist, 1_200)
        val fired = timeline.triggers().single()
        assertEquals(hold(HandPose.FIST), fired.trigger.id)
        assertTrue("fired ${fired.timestampMs - stillFrom} ms after stopping", fired.timestampMs - stillFrom >= 500)
    }

    @Test
    fun losingTheHandEndsTouchWithoutTap() {
        val timeline = Timeline()
        timeline.hold(Frames.palm, 300)
        timeline.hold(Frames.ok, 200)
        val state = timeline.hold(null, 800)
        assertTrue(timeline.triggers().none { it.trigger is GestureTrigger.Touch })
        assertTrue(timeline.events.any { it is TouchEnded })
        assertTrue(timeline.events.any { it is HandLost })
        assertTrue(!state.handPresent)
    }

    @Test
    fun shortDropoutKeepsThePose() {
        val timeline = Timeline()
        timeline.hold(Frames.fist, 300)
        timeline.hold(null, 200)
        timeline.hold(Frames.fist, 500)
        assertEquals(listOf(hold(HandPose.FIST)), timeline.triggerIds())
        assertTrue(timeline.events.none { it is HandLost })
    }

    @Test
    fun tinyHandIsIgnored() {
        val timeline = Timeline()
        val state = timeline.hold(Frames.scaled(Frames.fist, 0.15f), 1_500)
        assertEquals(HandQuality.TOO_SMALL, state.quality)
        assertNull(state.pose)
        assertTrue(timeline.triggers().isEmpty())
    }

    @Test
    fun indexClickFiresOnceAndDoesNotTriggerFist() {
        val timeline = Timeline()
        timeline.hold(Frames.one, 500)
        timeline.hold(Frames.fist, 200)
        timeline.hold(Frames.one, 300)
        assertEquals(listOf(GestureTrigger.IndexClick.id), timeline.triggerIds())
    }

    @Test
    fun slowCloseIsNotAClick() {
        val timeline = Timeline()
        timeline.hold(Frames.one, 500)
        timeline.hold(Frames.fist, 900)
        timeline.hold(Frames.one, 300)
        assertTrue(timeline.triggerIds().none { it == GestureTrigger.IndexClick.id })
    }

    @Test
    fun posesDoNotDependOnScreenRotation() {
        for (degrees in listOf(90f, 180f, 270f)) {
            assertEquals(HandPose.TWO, RealHands.settle(Frames.rotated(Frames.peace, degrees)).pose)
            assertEquals(HandPose.FIST, RealHands.settle(Frames.rotated(Frames.fist, degrees)).pose)
        }
        assertEquals(HandPose.THUMB_DOWN, RealHands.settle(Frames.rotated(Frames.like, 180f)).pose)
        assertEquals(HandPose.THUMB_SIDE, RealHands.settle(Frames.rotated(Frames.like, 90f)).pose)
    }

    @Test
    fun poseTilesFollowTheHand() {
        val timeline = Timeline()
        assertEquals(HandPose.TWO, timeline.hold(Frames.peace, 400).pose)
        assertEquals(HandPose.FIST, timeline.hold(Frames.fist, 400).pose)
        assertEquals(setOf(Finger.INDEX), timeline.hold(Frames.ok, 400).touches)
        val after = timeline.hold(Frames.palm, 400)
        assertEquals(HandPose.OPEN_PALM, after.pose)
        assertTrue(after.touches.isEmpty())
    }
}
