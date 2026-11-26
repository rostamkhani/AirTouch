package com.airtouch.app.settings

import com.airtouch.app.media.TargetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class SettingsTest {
    @Test
    fun corruptValuesFallBackToASafeRange() {
        val settings = sanitize(
            RawSettings(
                targetFps = 90,
                sensitivity = 9f,
                cooldownMs = 10,
                holdMs = 9_000,
                stabilizeMs = 1,
                targetMode = "nope",
                manualPackage = "  ",
            ),
        )
        assertEquals(15, settings.targetFps)
        assertEquals(1.4f, settings.sensitivity)
        assertEquals(400L, settings.cooldownMs)
        assertEquals(2_000L, settings.holdMs)
        assertEquals(200L, settings.stabilizeMs)
        assertEquals(TargetMode.AUTO, settings.targetMode)
        assertEquals(null, settings.manualPackage)
    }

    @Test
    fun sensitivityKeepsPinchExitAboveEnter() {
        listOf(0.7f, 1f, 1.4f).forEach { sensitivity ->
            val config = defaultSettings().copy(sensitivity = sensitivity).toGestureConfig()
            assertTrue(config.pinchExit > config.pinchEnter)
        }
    }
}

class ModelAssetTest {
    @Test
    fun packagedHandLandmarkerMatchesThePinnedChecksum() {
        val file = listOf(
            File("src/main/assets/hand_landmarker.task"),
            File("app/src/main/assets/hand_landmarker.task"),
        ).firstOrNull { it.isFile } ?: error("hand landmarker asset is missing from ${File(".").absolutePath}")
        assertEquals(7_819_105L, file.length())
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        val hex = digest.joinToString("") { "%02x".format(it) }
        assertEquals("fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1", hex)
    }
}
