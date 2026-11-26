package com.airtouch.handcontrol.core

/**
 * Recognition tuning. Defaults come from measurements on real hand photos (HaGRID) run through
 * the same MediaPipe model; see the library README for the numbers.
 */
data class HandControlConfig(
    val fingerOpenAbove: Float = 0.55f,
    val fingerClosedBelow: Float = 0.40f,
    val pinkyOpenAbove: Float = 0.45f,
    val pinkyClosedBelow: Float = 0.30f,
    val fingerRaisedAbove: Float = 0.45f,
    val thumbOpenAbove: Float = 0.55f,
    val thumbClosedBelow: Float = 0.35f,
    val touchEnter: Float = 0.35f,
    val touchExit: Float = 0.50f,
    val touchTipRatioEnter: Float = 0.80f,
    val touchTipRatioExit: Float = 1.00f,
    val touchAmbiguityRatio: Float = 1.30f,
    val touchEnterFrames: Int = 2,
    val touchExitFrames: Int = 2,
    val poseEnterMs: Long = 120L,
    val poseEnterFrames: Int = 2,
    val poseExitMs: Long = 150L,
    val holdMs: Long = 600L,
    val repeatIntervalMs: Long = 450L,
    val tapMinMs: Long = 40L,
    val tapMaxMs: Long = 450L,
    val longPressMs: Long = 800L,
    val doubleTapWindowMs: Long = 400L,
    val clickMinMs: Long = 60L,
    val clickMaxMs: Long = 500L,
    val clickArmMs: Long = 250L,
    val clickArmAbove: Float = 0.75f,
    val clickDownBelow: Float = 0.45f,
    val clickUpAbove: Float = 0.70f,
    val handLossMs: Long = 300L,
    val maxStableSpeed: Float = 1.6f,
    val minHandSize: Float = 0.06f,
    val repeatPoses: Set<HandPose> = emptySet(),
    val doubleTapFingers: Set<Finger> = emptySet(),
) {
    init {
        require(fingerOpenAbove > fingerClosedBelow) { "finger thresholds must leave a gap" }
        require(pinkyOpenAbove > pinkyClosedBelow) { "pinky thresholds must leave a gap" }
        require(thumbOpenAbove > thumbClosedBelow) { "thumb thresholds must leave a gap" }
        require(touchExit > touchEnter) { "touch exit must be above touch enter" }
        require(touchTipRatioExit >= touchTipRatioEnter) { "touch ratio exit must not be below enter" }
        require(tapMaxMs < longPressMs) { "a tap must be shorter than a long press" }
    }

    /**
     * [sensitivity] 1 keeps the defaults. Higher values accept looser touches and more hand
     * motion during a hold; lower values ask for clearer, stiller gestures.
     */
    fun withSensitivity(sensitivity: Float): HandControlConfig {
        val s = sensitivity.coerceIn(0.7f, 1.4f)
        val enter = (0.35f * s).coerceIn(0.22f, 0.50f)
        return copy(
            touchEnter = enter,
            touchExit = (enter + 0.15f).coerceAtMost(0.70f),
            touchTipRatioEnter = (0.80f * s).coerceIn(0.65f, 0.95f),
            touchTipRatioExit = (1.00f * s).coerceIn(0.85f, 1.15f).coerceAtLeast((0.80f * s).coerceIn(0.65f, 0.95f)),
            maxStableSpeed = (1.6f * s).coerceIn(1.1f, 2.4f),
        )
    }
}
