package com.airtouch.handcontrol.core

import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Per-frame, stateless measurements of one hand.
 *
 * [openness] is 0 for a fully curled finger and 1 for a straight one. [touchDistances] are 2D
 * thumb-tip to fingertip distances in palm sizes, and [touchTipRatios] compare that distance with
 * the distance to the same finger's two middle joints; a real tip-to-tip touch has a ratio well
 * below 1, while a thumb resting on a curled finger does not.
 */
data class HandMeasurements(
    val flexionDegrees: List<Float>,
    val openness: List<Float>,
    val touchDistances: List<Float>,
    val touchTipRatios: List<Float>,
    val thumbAngleDegrees: Float,
    val palmCenter: Vec3,
    val palmSizePx: Float,
    val handSizeFraction: Float,
    val partlyOutside: Boolean,
) {
    fun openness(finger: Finger): Float = openness[finger.ordinal]
    fun touchDistance(finger: Finger): Float = touchDistances[Finger.TOUCHABLE.indexOf(finger)]
    fun touchTipRatio(finger: Finger): Float = touchTipRatios[Finger.TOUCHABLE.indexOf(finger)]
}

object HandMeasurer {
    /** Finger flexion (MCP + PIP + DIP, degrees) mapped to openness 1 at [STRAIGHT_FLEX] and 0 at [CURLED_FLEX]. */
    const val STRAIGHT_FLEX = 60f
    const val CURLED_FLEX = 150f

    fun measure(frame: HandFrame): HandMeasurements? {
        val points3d = if (frame.world.size == HandLandmark.COUNT) frame.world else List(HandLandmark.COUNT) { frame.pixel(it) }
        val palmWidth3d = points3d[HandLandmark.INDEX_MCP].distanceTo(points3d[HandLandmark.PINKY_MCP])
        val palmLength3d = points3d[HandLandmark.WRIST].distanceTo(points3d[HandLandmark.MIDDLE_MCP])
        val palm3d = max(palmWidth3d, palmLength3d * 0.75f)
        if (palm3d <= 1e-6f) return null

        val pixels = List(HandLandmark.COUNT) { frame.pixel(it).let { p -> Vec3(p.x, p.y, 0f) } }
        val palmWidthPx = pixels[HandLandmark.INDEX_MCP].distanceTo(pixels[HandLandmark.PINKY_MCP])
        val palmLengthPx = pixels[HandLandmark.WRIST].distanceTo(pixels[HandLandmark.MIDDLE_MCP])
        val palmPx = max(palmWidthPx, palmLengthPx * 0.75f)
        if (palmPx < 4f) return null

        val flexion = ArrayList<Float>(5)
        val openness = ArrayList<Float>(5)
        for (finger in Finger.entries) {
            if (finger == Finger.THUMB) {
                val bend = flexionDegrees(points3d[finger.base], points3d[finger.middle], points3d[finger.distal]) +
                    flexionDegrees(points3d[finger.middle], points3d[finger.distal], points3d[finger.tip])
                val reach = points3d[HandLandmark.THUMB_TIP].distanceTo(points3d[HandLandmark.PINKY_MCP]) / palmWidth3d.coerceAtLeast(1e-6f)
                flexion += bend
                openness += thumbOpenness(reach, bend)
            } else {
                val bend = flexionDegrees(points3d[HandLandmark.WRIST], points3d[finger.base], points3d[finger.middle]) +
                    flexionDegrees(points3d[finger.base], points3d[finger.middle], points3d[finger.distal]) +
                    flexionDegrees(points3d[finger.middle], points3d[finger.distal], points3d[finger.tip])
                flexion += bend
                openness += ((CURLED_FLEX - bend) / (CURLED_FLEX - STRAIGHT_FLEX)).coerceIn(0f, 1f)
            }
        }

        val thumbTip = pixels[HandLandmark.THUMB_TIP]
        val distances = ArrayList<Float>(4)
        val ratios = ArrayList<Float>(4)
        for (finger in Finger.TOUCHABLE) {
            val toTip = thumbTip.distanceTo(pixels[finger.tip])
            val toJoint = min(thumbTip.distanceTo(pixels[finger.distal]), thumbTip.distanceTo(pixels[finger.middle]))
            distances += toTip / palmPx
            ratios += toTip / toJoint.coerceAtLeast(1e-3f)
        }

        val thumbVector = pixels[HandLandmark.THUMB_TIP] - pixels[HandLandmark.THUMB_MCP]
        val thumbAngle = Math.toDegrees(atan2(thumbVector.x.toDouble(), (-thumbVector.y).toDouble())).toFloat()

        val palmIndices = intArrayOf(HandLandmark.WRIST, HandLandmark.INDEX_MCP, HandLandmark.MIDDLE_MCP, HandLandmark.RING_MCP, HandLandmark.PINKY_MCP)
        var cx = 0f
        var cy = 0f
        for (index in palmIndices) {
            cx += frame.image[index].x
            cy += frame.image[index].y
        }
        val outside = frame.image.any { it.x < -0.02f || it.x > 1.02f || it.y < -0.02f || it.y > 1.02f }

        return HandMeasurements(
            flexionDegrees = flexion,
            openness = openness,
            touchDistances = distances,
            touchTipRatios = ratios,
            thumbAngleDegrees = thumbAngle,
            palmCenter = Vec3(cx / palmIndices.size, cy / palmIndices.size),
            palmSizePx = palmPx,
            handSizeFraction = palmPx / min(frame.imageWidth, frame.imageHeight).toFloat(),
            partlyOutside = outside,
        )
    }

    /**
     * Thumb openness from two cues measured on real photos: how far the tip reaches from the
     * pinky knuckle (in palm widths) and how much the thumb is bent.
     */
    fun thumbOpenness(reachPalmWidths: Float, bendDegrees: Float): Float {
        val score = (reachPalmWidths - 1.0f) / 0.8f - (bendDegrees - 30f) / 90f
        return score.coerceIn(0f, 1f)
    }
}
