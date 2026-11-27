package com.airtouch.handcontrol.core

import kotlin.math.abs

/**
 * Static hand shapes. Each is defined by which fingers are open; thumb poses also use the
 * on-screen direction of the thumb.
 */
enum class HandPose {
    OPEN_PALM,
    FIST,
    ONE,
    TWO,
    THREE,
    FOUR,
    THUMB_UP,
    THUMB_DOWN,
    THUMB_SIDE,
    PINKY,
    ROCK,
    LOVE,
    CALL,
    L_SHAPE,
    THREE_THUMB,
    OK,
}

object PoseClassifier {
    private const val UP_ENTER = 55f
    private const val UP_KEEP = 70f
    private const val DOWN_ENTER = 125f
    private const val DOWN_KEEP = 110f

    /**
     * [states] are in [Finger] order. [touches] are the fingers currently touched by the thumb tip.
     * Returns null when the shape is not one of [HandPose].
     */
    fun classify(
        states: List<FingerState>,
        touches: Set<Finger>,
        thumbAngleDegrees: Float,
        previous: HandPose? = null,
    ): HandPose? {
        val thumb = states[Finger.THUMB.ordinal] == FingerState.OPEN
        val index = states[Finger.INDEX.ordinal] == FingerState.OPEN
        val middle = states[Finger.MIDDLE.ordinal] == FingerState.OPEN
        val ring = states[Finger.RING.ordinal] == FingerState.OPEN
        val pinky = states[Finger.PINKY.ordinal] == FingerState.OPEN

        if (Finger.INDEX in touches) {
            return if (middle && ring && pinky) HandPose.OK else null
        }
        if (touches.isNotEmpty()) return null

        return when {
            thumb && index && middle && ring && pinky -> HandPose.OPEN_PALM
            !thumb && index && middle && ring && pinky -> HandPose.FOUR
            !thumb && index && middle && ring && !pinky -> HandPose.THREE
            thumb && index && middle && !ring && !pinky -> HandPose.THREE_THUMB
            !thumb && index && middle && !ring && !pinky -> HandPose.TWO
            !thumb && index && !middle && !ring && !pinky -> HandPose.ONE
            thumb && index && !middle && !ring && !pinky -> HandPose.L_SHAPE
            !thumb && !index && !middle && !ring && !pinky -> HandPose.FIST
            thumb && !index && !middle && !ring && !pinky -> thumbDirection(thumbAngleDegrees, previous)
            !thumb && !index && !middle && !ring && pinky -> HandPose.PINKY
            thumb && !index && !middle && !ring && pinky -> HandPose.CALL
            !thumb && index && !middle && !ring && pinky -> HandPose.ROCK
            thumb && index && !middle && !ring && pinky -> HandPose.LOVE
            else -> null
        }
    }

    /** 0 degrees is straight up on screen, 180 is straight down. */
    fun thumbDirection(angleDegrees: Float, previous: HandPose?): HandPose {
        val fromUp = abs(angleDegrees)
        return when {
            previous == HandPose.THUMB_UP && fromUp <= UP_KEEP -> HandPose.THUMB_UP
            previous == HandPose.THUMB_DOWN && fromUp >= DOWN_KEEP -> HandPose.THUMB_DOWN
            fromUp <= UP_ENTER -> HandPose.THUMB_UP
            fromUp >= DOWN_ENTER -> HandPose.THUMB_DOWN
            else -> HandPose.THUMB_SIDE
        }
    }
}
