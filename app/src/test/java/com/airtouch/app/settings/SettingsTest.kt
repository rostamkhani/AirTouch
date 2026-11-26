package com.airtouch.app.settings

import com.airtouch.app.control.ControlAction
import com.airtouch.app.control.DefaultBindings
import com.airtouch.app.media.TargetMode
import com.airtouch.handcontrol.core.Finger
import com.airtouch.handcontrol.core.GestureTrigger
import com.airtouch.handcontrol.core.HandPose
import com.airtouch.handcontrol.core.TouchKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun corruptValuesFallBackToASafeRange() {
        val settings = sanitize(
            RawSettings(
                targetFps = 90,
                sensitivity = 9f,
                cooldownMs = 10,
                holdMs = 9_000,
                bindings = "garbage;hold:NOPE=PLAY_PAUSE;hold:FIST=NOT_AN_ACTION",
                playbackSource = "nope",
                targetMode = "nope",
                manualPackage = "  ",
            ),
        )
        assertEquals(15, settings.targetFps)
        assertEquals(1.4f, settings.sensitivity)
        assertEquals(200L, settings.cooldownMs)
        assertEquals(1_500L, settings.holdMs)
        assertTrue(settings.bindings.isEmpty())
        assertEquals(PlaybackSource.BUILT_IN, settings.playbackSource)
        assertEquals(TargetMode.AUTO, settings.targetMode)
        assertEquals(null, settings.manualPackage)
    }

    @Test
    fun missingBindingsUseDefaults() {
        assertEquals(DefaultBindings.map, sanitize(RawSettings()).bindings)
    }

    @Test
    fun bindingsRoundTripIncludingAnEmptyMap() {
        val bindings = mapOf(
            GestureTrigger.Hold(HandPose.TWO) to ControlAction.NEXT,
            GestureTrigger.Touch(Finger.RING, TouchKind.DOUBLE_TAP) to ControlAction.SEEK_FORWARD,
            GestureTrigger.IndexClick to ControlAction.PLAY_PAUSE,
        )
        assertEquals(bindings, DefaultBindings.decode(DefaultBindings.encode(bindings)))
        assertEquals(emptyMap<GestureTrigger, ControlAction>(), DefaultBindings.decode(DefaultBindings.encode(emptyMap())))
    }

    @Test
    fun defaultsCoverTheRequestedGestures() {
        val bound = DefaultBindings.map.keys
        for (pose in listOf(HandPose.FIST, HandPose.TWO, HandPose.ONE, HandPose.PINKY, HandPose.THUMB_UP)) {
            assertTrue("$pose is bound", GestureTrigger.Hold(pose) in bound)
        }
        for (finger in Finger.TOUCHABLE) {
            assertTrue("$finger tap is bound", GestureTrigger.Touch(finger, TouchKind.TAP) in bound)
        }
    }

    @Test
    fun sensitivityKeepsTouchExitAboveEnter() {
        listOf(0.7f, 1f, 1.4f).forEach { sensitivity ->
            val config = defaultSettings().copy(sensitivity = sensitivity).toHandConfig()
            assertTrue(config.touchExit > config.touchEnter)
            assertTrue(config.touchTipRatioExit >= config.touchTipRatioEnter)
        }
    }
}
