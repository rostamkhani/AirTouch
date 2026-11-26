package com.airtouch.app.gestures

import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.roundToInt

object HandGeometry {
    fun features(sample: HandSample): HandFeatures? {
        if (sample.landmarks.size != 21) return null
        if (sample.imageWidth <= 0 || sample.imageHeight <= 0) return null
        if (sample.landmarks.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val width = sample.imageWidth.toFloat()
        val height = sample.imageHeight.toFloat()
        val px = sample.landmarks.map { Point2(it.x * width, it.y * height) }
        val palmIds = intArrayOf(0, 5, 9, 13, 17)
        val center = Point2(
            palmIds.map { px[it].x }.average().toFloat(),
            palmIds.map { px[it].y }.average().toFloat(),
        )
        val palmWidth = distance(px[5], px[17])
        if (palmWidth < 8f) return null
        return HandFeatures(
            palmCenterPx = center,
            palmWidthPx = palmWidth,
            fingers = mapOf(
                Finger.THUMB to thumbPose(px, sample.handedness, palmWidth, center),
                Finger.INDEX to fingerPose(px, 5),
                Finger.MIDDLE to fingerPose(px, 9),
                Finger.RING to fingerPose(px, 13),
                Finger.PINKY to fingerPose(px, 17),
            ),
            pinchIndexRatio = distance(px[4], px[8]) / palmWidth,
            pinchMiddleRatio = distance(px[4], px[12]) / palmWidth,
            imageWidth = sample.imageWidth,
            imageHeight = sample.imageHeight,
        )
    }

    fun fingerPose(points: List<Point2>, mcpIndex: Int): FingerPose {
        val pipAngle = jointAngle(points[mcpIndex], points[mcpIndex + 1], points[mcpIndex + 2])
        val dipAngle = jointAngle(points[mcpIndex + 1], points[mcpIndex + 2], points[mcpIndex + 3])
        if (pipAngle.isNaN() || dipAngle.isNaN()) return FingerPose.UNKNOWN
        if (pipAngle >= 150f && dipAngle >= 140f) return FingerPose.OPEN
        if (pipAngle <= 110f) return FingerPose.CLOSED
        return FingerPose.UNKNOWN
    }

    fun thumbPose(
        points: List<Point2>,
        handedness: Handedness,
        palmWidth: Float,
        palmCenter: Point2,
    ): FingerPose {
        val mcp = points[2]
        val ip = points[3]
        val tip = points[4]
        val indexMcp = points[5]
        val angle = jointAngle(mcp, ip, tip)
        if (angle.isNaN()) return FingerPose.UNKNOWN
        val tipRatio = distance(tip, palmCenter) / palmWidth
        val lateral = when (handedness) {
            Handedness.RIGHT -> tip.x < indexMcp.x - palmWidth * 0.15f
            Handedness.LEFT -> tip.x > indexMcp.x + palmWidth * 0.15f
            Handedness.UNKNOWN -> tipRatio > 1f
        }
        if (tipRatio < 0.75f || angle < 120f) return FingerPose.CLOSED
        if (angle > 140f && tipRatio > 0.9f && (lateral || tipRatio > 1.1f)) return FingerPose.OPEN
        return FingerPose.UNKNOWN
    }

    fun jointAngle(a: Point2, b: Point2, c: Point2): Float {
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v2x = c.x - b.x
        val v2y = c.y - b.y
        val magnitude = hypot(v1x.toDouble(), v1y.toDouble()) * hypot(v2x.toDouble(), v2y.toDouble())
        if (magnitude < 1e-3) return Float.NaN
        val cosine = ((v1x * v2x + v1y * v2y) / magnitude).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosine)).toFloat()
    }

    fun distance(a: Point2, b: Point2): Float = hypot(a.x - b.x, a.y - b.y)

    fun percent(value: Float): Int = (value * 100f).roundToInt()
}
