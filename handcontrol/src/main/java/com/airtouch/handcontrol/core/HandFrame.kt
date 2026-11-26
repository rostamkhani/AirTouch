package com.airtouch.handcontrol.core

/** MediaPipe hand landmark indices. */
object HandLandmark {
    const val WRIST = 0
    const val THUMB_CMC = 1
    const val THUMB_MCP = 2
    const val THUMB_IP = 3
    const val THUMB_TIP = 4
    const val INDEX_MCP = 5
    const val INDEX_PIP = 6
    const val INDEX_DIP = 7
    const val INDEX_TIP = 8
    const val MIDDLE_MCP = 9
    const val MIDDLE_PIP = 10
    const val MIDDLE_DIP = 11
    const val MIDDLE_TIP = 12
    const val RING_MCP = 13
    const val RING_PIP = 14
    const val RING_DIP = 15
    const val RING_TIP = 16
    const val PINKY_MCP = 17
    const val PINKY_PIP = 18
    const val PINKY_DIP = 19
    const val PINKY_TIP = 20
    const val COUNT = 21

    val CONNECTIONS: List<Pair<Int, Int>> = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        5 to 9, 9 to 10, 10 to 11, 11 to 12,
        9 to 13, 13 to 14, 14 to 15, 15 to 16,
        13 to 17, 17 to 18, 18 to 19, 19 to 20,
        0 to 17,
    )
}

enum class Handedness { LEFT, RIGHT, UNKNOWN }

enum class Finger(val base: Int, val middle: Int, val distal: Int, val tip: Int) {
    THUMB(HandLandmark.THUMB_CMC, HandLandmark.THUMB_MCP, HandLandmark.THUMB_IP, HandLandmark.THUMB_TIP),
    INDEX(HandLandmark.INDEX_MCP, HandLandmark.INDEX_PIP, HandLandmark.INDEX_DIP, HandLandmark.INDEX_TIP),
    MIDDLE(HandLandmark.MIDDLE_MCP, HandLandmark.MIDDLE_PIP, HandLandmark.MIDDLE_DIP, HandLandmark.MIDDLE_TIP),
    RING(HandLandmark.RING_MCP, HandLandmark.RING_PIP, HandLandmark.RING_DIP, HandLandmark.RING_TIP),
    PINKY(HandLandmark.PINKY_MCP, HandLandmark.PINKY_PIP, HandLandmark.PINKY_DIP, HandLandmark.PINKY_TIP);

    companion object {
        /** Fingers whose tip can be touched by the thumb tip, in hand order. */
        val TOUCHABLE: List<Finger> = listOf(INDEX, MIDDLE, RING, PINKY)
    }
}

enum class FingerState { OPEN, CLOSED }

/**
 * One detected hand.
 *
 * [image] holds 21 points normalized to the analyzed frame, already upright and mirrored the way
 * the user sees the preview: x grows to the user's right and y grows downward. z is MediaPipe's
 * relative depth. [world] holds MediaPipe's metric 3D landmarks centered on the hand; it may be
 * empty when only 2D points are available.
 */
data class HandFrame(
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val image: List<Vec3>,
    val world: List<Vec3> = emptyList(),
    val handedness: Handedness = Handedness.UNKNOWN,
    val score: Float = 1f,
) {
    init {
        require(image.size == HandLandmark.COUNT) { "expected ${HandLandmark.COUNT} image landmarks" }
        require(world.isEmpty() || world.size == HandLandmark.COUNT) { "expected ${HandLandmark.COUNT} world landmarks" }
        require(imageWidth > 0 && imageHeight > 0) { "image size must be positive" }
    }

    fun pixel(index: Int): Vec3 {
        val point = image[index]
        return Vec3(point.x * imageWidth, point.y * imageHeight, point.z * imageWidth)
    }
}
