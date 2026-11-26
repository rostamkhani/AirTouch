package com.airtouch.handcontrol.core

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelAssetTest {
    @Test
    fun packagedHandLandmarkerMatchesThePinnedChecksum() {
        val file = listOf(
            File("src/main/assets/hand_landmarker.task"),
            File("handcontrol/src/main/assets/hand_landmarker.task"),
        ).firstOrNull { it.isFile } ?: error("hand landmarker asset is missing from ${File(".").absolutePath}")
        assertEquals(7_819_105L, file.length())
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        val hex = digest.joinToString("") { "%02x".format(it) }
        assertEquals("fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1", hex)
    }
}
