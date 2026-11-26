package com.airtouch.handcontrol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureDispatcherTest {
    private enum class Action { PLAY, NEXT, VOLUME_UP, LOCK }

    private val fired = mutableListOf<Action>()
    private val dispatcher = GestureDispatcher<Action>(isRepeatable = { it == Action.VOLUME_UP }) { action, _, _ ->
        fired += action
    }

    private val fist = GestureTrigger.Hold(HandPose.FIST)
    private val two = GestureTrigger.Hold(HandPose.TWO)
    private val thumbUp = GestureTrigger.Hold(HandPose.THUMB_UP)
    private val okLong = GestureTrigger.Touch(Finger.INDEX, TouchKind.LONG_PRESS)
    private val middleDouble = GestureTrigger.Touch(Finger.MIDDLE, TouchKind.DOUBLE_TAP)

    init {
        dispatcher.bindings = mapOf(
            fist to Action.PLAY,
            two to Action.NEXT,
            thumbUp to Action.VOLUME_UP,
            okLong to Action.LOCK,
            middleDouble to Action.NEXT,
        )
        dispatcher.lockAction = Action.LOCK
        dispatcher.cooldownMs = 400
    }

    @Test
    fun cooldownDropsAQuickSecondTrigger() {
        dispatcher.onEvent(GestureTriggered(fist, 1_000))
        assertNull(dispatcher.onEvent(GestureTriggered(two, 1_200)))
        assertEquals(Action.NEXT, dispatcher.onEvent(GestureTriggered(two, 1_500)))
        assertEquals(listOf(Action.PLAY, Action.NEXT), fired)
    }

    @Test
    fun repeatsOnlyPassForRepeatableActions() {
        dispatcher.onEvent(GestureTriggered(thumbUp, 1_000))
        dispatcher.onEvent(GestureTriggered(thumbUp, 1_450, repeat = 1))
        dispatcher.onEvent(GestureTriggered(fist, 3_000))
        dispatcher.onEvent(GestureTriggered(fist, 3_450, repeat = 1))
        assertEquals(listOf(Action.VOLUME_UP, Action.VOLUME_UP, Action.PLAY), fired)
    }

    @Test
    fun lockBlocksEverythingButTheLockGesture() {
        dispatcher.onEvent(GestureTriggered(okLong, 1_000))
        assertTrue(dispatcher.locked)
        assertNull(dispatcher.onEvent(GestureTriggered(fist, 3_000)))
        dispatcher.onEvent(GestureTriggered(okLong, 5_000))
        assertTrue(!dispatcher.locked)
        assertEquals(Action.PLAY, dispatcher.onEvent(GestureTriggered(fist, 7_000)))
    }

    @Test
    fun unboundAndNonTriggerEventsAreIgnored() {
        assertNull(dispatcher.onEvent(PoseStarted(HandPose.FIST, 1_000)))
        assertNull(dispatcher.onEvent(GestureTriggered(GestureTrigger.IndexClick, 1_000)))
        assertTrue(fired.isEmpty())
    }

    @Test
    fun hintsFollowBindings() {
        val hints = dispatcher.engineHints()
        assertEquals(setOf(HandPose.THUMB_UP), hints.repeatPoses)
        assertEquals(setOf(Finger.MIDDLE), hints.doubleTapFingers)
    }

    @Test
    fun triggerIdsRoundTrip() {
        for (trigger in GestureTrigger.ALL) {
            assertEquals(trigger, GestureTrigger.parse(trigger.id))
        }
        assertEquals(HandPose.entries.size + 12 + 1, GestureTrigger.ALL.size)
    }
}
