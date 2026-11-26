package com.airtouch.app.gestures

object HandFixtures {
    const val WIDTH = 1000
    const val HEIGHT = 1000

    fun openRight(dx: Float = 0f, dy: Float = 0f): List<Landmark2> {
        val thumb = listOf(
            point(0.40f, 0.70f),
            point(0.30f, 0.66f),
            point(0.22f, 0.60f),
            point(0.14f, 0.52f),
        )
        return hand(
            wrist = point(0.50f, 0.80f),
            thumb = thumb,
            index = straight(point(0.40f, 0.56f), point(0.40f, 0.28f)),
            middle = straight(point(0.50f, 0.54f), point(0.50f, 0.22f)),
            ring = straight(point(0.60f, 0.56f), point(0.60f, 0.30f)),
            pinky = straight(point(0.68f, 0.60f), point(0.68f, 0.40f)),
        ).move(dx, dy)
    }

    fun fistRight(): List<Landmark2> {
        val indexMcp = point(0.40f, 0.56f)
        val middleMcp = point(0.50f, 0.54f)
        val ringMcp = point(0.60f, 0.56f)
        val pinkyMcp = point(0.68f, 0.60f)
        return hand(
            wrist = point(0.50f, 0.80f),
            thumb = listOf(
                point(0.46f, 0.70f),
                point(0.50f, 0.66f),
                point(0.54f, 0.64f),
                point(0.53f, 0.62f),
            ),
            index = curled(indexMcp),
            middle = curled(middleMcp),
            ring = curled(ringMcp),
            pinky = curled(pinkyMcp),
        )
    }

    fun pinchIndex(): List<Landmark2> {
        val open = openRight().toMutableList()
        val indexTip = open[8]
        open[4] = Landmark2(indexTip.x + 0.008f, indexTip.y + 0.008f, 0f)
        return open
    }

    fun pinchMiddle(): List<Landmark2> {
        val open = openRight().toMutableList()
        val middleTip = open[12]
        open[4] = Landmark2(middleTip.x + 0.008f, middleTip.y + 0.008f, 0f)
        return open
    }

    fun ambiguousPinch(): List<Landmark2> {
        val open = openRight().toMutableList()
        val indexTip = open[8]
        val middleTip = open[12]
        val sharedX = (indexTip.x + middleTip.x) / 2f
        val sharedY = (indexTip.y + middleTip.y) / 2f
        open[8] = Landmark2(sharedX - 0.01f, sharedY, 0f)
        open[12] = Landmark2(sharedX + 0.01f, sharedY, 0f)
        open[4] = Landmark2(sharedX, sharedY + 0.005f, 0f)
        return open
    }

    fun mirror(landmarks: List<Landmark2>): List<Landmark2> {
        return landmarks.map { Landmark2(1f - it.x, it.y, it.z) }
    }

    fun sample(
        timestampMs: Long,
        landmarks: List<Landmark2>,
        handedness: Handedness = Handedness.RIGHT,
        generation: Int = 0,
        width: Int = WIDTH,
        height: Int = HEIGHT,
        worldZ: Float? = null,
    ): HandSample {
        return HandSample(
            timestampMs = timestampMs,
            landmarks = landmarks,
            worldLandmarks = if (worldZ == null) emptyList() else listOf(Landmark2(0f, 0f, worldZ)),
            handedness = handedness,
            imageWidth = width,
            imageHeight = height,
            generation = generation,
        )
    }

    private fun point(x: Float, y: Float) = Landmark2(x, y, 0f)

    private fun straight(mcp: Landmark2, tip: Landmark2): List<Landmark2> {
        fun at(t: Float) = Landmark2(
            mcp.x + (tip.x - mcp.x) * t,
            mcp.y + (tip.y - mcp.y) * t,
            0f,
        )
        return listOf(at(0f), at(0.35f), at(0.68f), at(1f))
    }

    private fun curled(mcp: Landmark2): List<Landmark2> {
        return listOf(
            mcp,
            Landmark2(mcp.x + 0.045f, mcp.y + 0.03f, 0f),
            Landmark2(mcp.x + 0.02f, mcp.y - 0.01f, 0f),
            Landmark2(mcp.x + 0.005f, mcp.y + 0.01f, 0f),
        )
    }

    private fun hand(
        wrist: Landmark2,
        thumb: List<Landmark2>,
        index: List<Landmark2>,
        middle: List<Landmark2>,
        ring: List<Landmark2>,
        pinky: List<Landmark2>,
    ): List<Landmark2> {
        return listOf(wrist) + thumb + index + middle + ring + pinky
    }

    private fun List<Landmark2>.move(dx: Float, dy: Float): List<Landmark2> {
        if (dx == 0f && dy == 0f) return this
        return map { Landmark2(it.x + dx, it.y + dy, it.z) }
    }
}
