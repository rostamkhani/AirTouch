package com.airtouch.handcontrol.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HandFrameRotationTest {
    @Test
    fun fourQuarterTurnsReturnToTheSameHand() {
        val frame = Frames.peace.copy(uprightRotation = 0)
        var turned = frame
        repeat(4) {
            turned = turned.copy(uprightRotation = 1).rotatedForScreen(0)
        }
        assertEquals(frame.imageWidth, turned.imageWidth)
        assertEquals(frame.imageHeight, turned.imageHeight)
        frame.image.indices.forEach { index ->
            assertEquals(frame.image[index].x, turned.image[index].x, 0.001f)
            assertEquals(frame.image[index].y, turned.image[index].y, 0.001f)
        }
    }

    @Test
    fun oddTurnSwapsTheFrameSize() {
        val frame = Frames.fist.copy(uprightRotation = 0, imageWidth = 480, imageHeight = 640)
        val turned = frame.copy(uprightRotation = 1).rotatedForScreen(0)
        assertEquals(640, turned.imageWidth)
        assertEquals(480, turned.imageHeight)
        assertEquals(0, turned.uprightRotation)
    }

    @Test
    fun sameRotationLeavesTheFrameUntouched() {
        val frame = Frames.ok.copy(uprightRotation = 1)
        assertEquals(frame, frame.rotatedForScreen(1))
    }
}
