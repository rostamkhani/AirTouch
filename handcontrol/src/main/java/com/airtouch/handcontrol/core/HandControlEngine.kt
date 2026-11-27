package com.airtouch.handcontrol.core

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Turns a stream of [HandFrame]s into stable poses, touches and triggers.
 *
 * Not thread-safe: call [process] from one thread. Events go to [listener] synchronously, in
 * order, from inside [process]. Time only moves forward through [process], so feed it every
 * analyzed camera frame, passing null when no hand was found.
 */
class HandControlEngine(
    config: HandControlConfig = HandControlConfig(),
    private val listener: (HandEvent) -> Unit = {},
) {
    var config: HandControlConfig = config
        private set

    var state: HandControlState = HandControlState.EMPTY
        private set

    private var handPresent = false
    private var lastSeenMs = 0L

    private val fingerStates = arrayOfNulls<FingerState>(5)

    private var lastCenter: Vec3? = null
    private var lastCenterMs = 0L
    private var speed = 0f

    private val touchActive = BooleanArray(4)
    private val rawStreak = IntArray(4)
    private val missStreak = IntArray(4)
    private val rawSince = LongArray(4)
    private val lastRawTrue = LongArray(4)
    private val touchStart = LongArray(4)
    private val longFired = BooleanArray(4)
    private val pendingTap = LongArray(4) { NONE }

    private var pose: HandPose? = null
    private var poseStart = 0L
    private var holdAnchor = 0L
    private var holdFired = false
    private var repeatCount = 0
    private var nextRepeatAt = 0L
    private var candidate: HandPose? = null
    private var candidateSince = 0L
    private var candidateFrames = 0
    private var mismatchSince = NONE

    private var clickArmedSince = NONE
    private var clickDownAt = NONE

    fun updateConfig(newConfig: HandControlConfig) {
        config = newConfig
    }

    fun reset() {
        handPresent = false
        lastSeenMs = 0L
        fingerStates.fill(null)
        lastCenter = null
        speed = 0f
        touchActive.fill(false)
        rawStreak.fill(0)
        missStreak.fill(0)
        longFired.fill(false)
        pendingTap.fill(NONE)
        pose = null
        holdFired = false
        candidate = null
        candidateFrames = 0
        mismatchSince = NONE
        clickArmedSince = NONE
        clickDownAt = NONE
        state = HandControlState.EMPTY
    }

    fun process(frame: HandFrame?, nowMs: Long): HandControlState {
        val measurements = frame?.let(HandMeasurer::measure)
        if (frame == null || measurements == null) {
            if (handPresent && nowMs - lastSeenMs > config.handLossMs) loseHand(nowMs)
            flushPendingTaps(nowMs)
            state = if (handPresent) state.copy(timestampMs = nowMs) else HandControlState(timestampMs = nowMs)
            return state
        }

        if (!handPresent) {
            handPresent = true
            emit(HandAppeared(nowMs))
        }
        lastSeenMs = nowMs

        updateSpeed(frame, measurements, nowMs)
        val quality = when {
            measurements.partlyOutside -> HandQuality.PARTLY_OUTSIDE
            measurements.handSizeFraction < config.minHandSize -> HandQuality.TOO_SMALL
            speed > config.maxStableSpeed -> HandQuality.MOVING
            else -> HandQuality.GOOD
        }
        val trusted = quality == HandQuality.GOOD || quality == HandQuality.MOVING

        updateFingerStates(measurements)
        val chosenTouch = chooseTouch(measurements, trusted)
        updateTouches(chosenTouch, nowMs)
        flushPendingTaps(nowMs)

        val states = fingerStates.map { it ?: FingerState.CLOSED }
        val activeTouches = activeTouchSet()
        val rawPose = if (trusted) {
            PoseClassifier.classify(states, activeTouches, measurements.thumbAngleDegrees, pose ?: candidate)
        } else {
            null
        }
        updatePose(rawPose, nowMs)
        updateHold(quality, nowMs)
        if (trusted) updateClick(measurements, nowMs) else clearClick()

        state = HandControlState(
            timestampMs = nowMs,
            handPresent = true,
            quality = quality,
            pose = pose,
            poseCandidate = rawPose,
            poseHeldMs = if (pose != null) nowMs - poseStart else 0L,
            holdProgress = holdProgress(nowMs),
            touches = activeTouches,
            touchCandidates = chosenTouch?.let { setOf(Finger.TOUCHABLE[it]) } ?: emptySet(),
            fingerStates = fingerStates.toList(),
            fingerOpenness = measurements.openness,
            touchDistances = measurements.touchDistances,
            thumbAngleDegrees = measurements.thumbAngleDegrees,
            speedPalmsPerSecond = speed,
            frame = frame,
        )
        return state
    }

    private fun updateSpeed(frame: HandFrame, measurements: HandMeasurements, nowMs: Long) {
        val center = measurements.palmCenter
        val previous = lastCenter
        val elapsed = (nowMs - lastCenterMs) / 1000f
        if (previous != null && elapsed > 0f && elapsed <= 0.5f) {
            val dx = (center.x - previous.x) * frame.imageWidth
            val dy = (center.y - previous.y) * frame.imageHeight
            val instant = sqrt(dx * dx + dy * dy) / measurements.palmSizePx / elapsed
            speed = 0.5f * speed + 0.5f * instant
        } else {
            speed = 0f
        }
        lastCenter = center
        lastCenterMs = nowMs
    }

    private fun updateFingerStates(measurements: HandMeasurements) {
        for (finger in Finger.entries) {
            val openness = measurements.openness(finger)
            val openAbove = when (finger) {
                Finger.THUMB -> config.thumbOpenAbove
                Finger.PINKY -> config.pinkyOpenAbove
                else -> config.fingerOpenAbove
            }
            val closedBelow = when (finger) {
                Finger.THUMB -> config.thumbClosedBelow
                Finger.PINKY -> config.pinkyClosedBelow
                else -> config.fingerClosedBelow
            }
            val i = finger.ordinal
            fingerStates[i] = when (fingerStates[i]) {
                null -> if (openness >= (openAbove + closedBelow) / 2f) FingerState.OPEN else FingerState.CLOSED
                FingerState.OPEN -> if (openness <= closedBelow) FingerState.CLOSED else FingerState.OPEN
                FingerState.CLOSED -> if (openness >= openAbove) FingerState.OPEN else FingerState.CLOSED
            }
        }
    }

    /** Index into [Finger.TOUCHABLE] of the single fingertip the thumb is touching this frame. */
    private fun chooseTouch(measurements: HandMeasurements, trusted: Boolean): Int? {
        if (!trusted) return null
        val candidates = ArrayList<Int>(4)
        for (k in Finger.TOUCHABLE.indices) {
            val finger = Finger.TOUCHABLE[k]
            val distance = measurements.touchDistances[k]
            val ratio = measurements.touchTipRatios[k]
            val near = if (touchActive[k]) {
                distance < config.touchExit && ratio < config.touchTipRatioExit
            } else {
                distance < config.touchEnter && ratio < config.touchTipRatioEnter
            }
            val othersRaised = Finger.TOUCHABLE.all { other ->
                other == finger || measurements.openness(other) >= config.fingerRaisedAbove
            }
            if (near && othersRaised) candidates += k
        }
        if (candidates.isEmpty()) return null
        val active = touchActive.indexOfFirst { it }
        if (active in candidates) return active
        val sorted = candidates.sortedBy { measurements.touchDistances[it] }
        if (sorted.size >= 2) {
            val best = measurements.touchDistances[sorted[0]]
            val second = measurements.touchDistances[sorted[1]]
            if (second < best * config.touchAmbiguityRatio) return null
        }
        return sorted[0]
    }

    private fun updateTouches(chosen: Int?, nowMs: Long) {
        for (k in Finger.TOUCHABLE.indices) {
            val raw = k == chosen
            if (raw) lastRawTrue[k] = nowMs
            if (!touchActive[k]) {
                if (raw) {
                    if (rawStreak[k] == 0) rawSince[k] = nowMs
                    rawStreak[k] += 1
                    if (rawStreak[k] >= config.touchEnterFrames) startTouch(k, rawSince[k], nowMs)
                } else {
                    rawStreak[k] = 0
                }
            } else {
                if (raw) {
                    missStreak[k] = 0
                } else {
                    missStreak[k] += 1
                    if (missStreak[k] >= config.touchExitFrames) endTouch(k, nowMs, countAsTap = true)
                }
            }
            if (touchActive[k] && !longFired[k] && nowMs - touchStart[k] >= config.longPressMs) {
                longFired[k] = true
                emit(GestureTriggered(GestureTrigger.Touch(Finger.TOUCHABLE[k], TouchKind.LONG_PRESS), nowMs))
            }
        }
    }

    private fun startTouch(k: Int, startedAt: Long, nowMs: Long) {
        touchActive[k] = true
        touchStart[k] = startedAt
        longFired[k] = false
        missStreak[k] = 0
        rawStreak[k] = 0
        emit(TouchStarted(Finger.TOUCHABLE[k], nowMs))
    }

    private fun endTouch(k: Int, nowMs: Long, countAsTap: Boolean) {
        val finger = Finger.TOUCHABLE[k]
        val duration = (lastRawTrue[k] - touchStart[k]).coerceAtLeast(0L)
        touchActive[k] = false
        missStreak[k] = 0
        rawStreak[k] = 0
        emit(TouchEnded(finger, duration, nowMs))
        if (!countAsTap || longFired[k]) return
        if (duration < config.tapMinMs || duration > config.tapMaxMs) return
        if (finger in config.doubleTapFingers) {
            val previous = pendingTap[k]
            if (previous != NONE && touchStart[k] - previous <= config.doubleTapWindowMs) {
                pendingTap[k] = NONE
                emit(GestureTriggered(GestureTrigger.Touch(finger, TouchKind.DOUBLE_TAP), nowMs))
            } else {
                if (previous != NONE) emit(GestureTriggered(GestureTrigger.Touch(finger, TouchKind.TAP), nowMs))
                pendingTap[k] = lastRawTrue[k]
            }
        } else {
            emit(GestureTriggered(GestureTrigger.Touch(finger, TouchKind.TAP), nowMs))
        }
    }

    private fun flushPendingTaps(nowMs: Long) {
        for (k in Finger.TOUCHABLE.indices) {
            val pending = pendingTap[k]
            if (pending == NONE) continue
            val expired = if (touchActive[k]) {
                nowMs - touchStart[k] > config.tapMaxMs
            } else {
                nowMs - pending > config.doubleTapWindowMs
            }
            if (expired) {
                pendingTap[k] = NONE
                emit(GestureTriggered(GestureTrigger.Touch(Finger.TOUCHABLE[k], TouchKind.TAP), nowMs))
            }
        }
    }

    private fun activeTouchSet(): Set<Finger> {
        var result: Set<Finger> = emptySet()
        for (k in Finger.TOUCHABLE.indices) if (touchActive[k]) result = result + Finger.TOUCHABLE[k]
        return result
    }

    private fun updatePose(rawPose: HandPose?, nowMs: Long) {
        if (rawPose == candidate) {
            candidateFrames += 1
        } else {
            candidate = rawPose
            candidateSince = nowMs
            candidateFrames = 1
        }
        if (rawPose == pose) {
            mismatchSince = NONE
        } else if (mismatchSince == NONE) {
            mismatchSince = nowMs
        }
        if (candidate == pose) return
        val next = candidate
        if (next != null && candidateFrames >= config.poseEnterFrames && nowMs - candidateSince >= config.poseEnterMs) {
            switchPose(next, candidateSince, nowMs)
        } else if (pose != null && mismatchSince != NONE && nowMs - mismatchSince >= config.poseExitMs) {
            switchPose(null, nowMs, nowMs)
        }
    }

    private fun switchPose(next: HandPose?, startedAt: Long, nowMs: Long) {
        pose?.let { emit(PoseEnded(it, nowMs - poseStart, nowMs)) }
        pose = next
        holdFired = false
        repeatCount = 0
        mismatchSince = NONE
        if (next != null) {
            poseStart = startedAt
            holdAnchor = startedAt
            emit(PoseStarted(next, nowMs))
        }
    }

    private fun updateHold(quality: HandQuality, nowMs: Long) {
        val current = pose ?: return
        if (mismatchSince != NONE) return
        when (quality) {
            HandQuality.GOOD -> Unit
            HandQuality.MOVING -> {
                if (!holdFired) holdAnchor = nowMs
                return
            }
            else -> return
        }
        if (!holdFired) {
            if (nowMs - holdAnchor >= config.holdMs) {
                holdFired = true
                repeatCount = 0
                nextRepeatAt = nowMs + config.repeatIntervalMs
                emit(GestureTriggered(GestureTrigger.Hold(current), nowMs))
            }
        } else if (current in config.repeatPoses && nowMs >= nextRepeatAt) {
            repeatCount += 1
            nextRepeatAt = nowMs + config.repeatIntervalMs
            emit(GestureTriggered(GestureTrigger.Hold(current), nowMs, repeatCount))
        }
    }

    private fun holdProgress(nowMs: Long): Float {
        if (pose == null) return 0f
        if (holdFired) return 1f
        return ((nowMs - holdAnchor).toFloat() / max(1L, config.holdMs)).coerceIn(0f, 1f)
    }

    private fun updateClick(measurements: HandMeasurements, nowMs: Long) {
        val othersCurled = listOf(Finger.MIDDLE, Finger.RING, Finger.PINKY).all { fingerStates[it.ordinal] == FingerState.CLOSED }
        if (!othersCurled) {
            clearClick()
            return
        }
        val index = measurements.openness(Finger.INDEX)
        if (clickDownAt != NONE) {
            when {
                nowMs - clickDownAt > config.clickMaxMs -> clearClick()
                index >= config.clickUpAbove -> {
                    val elapsed = nowMs - clickDownAt
                    clickDownAt = NONE
                    clickArmedSince = nowMs
                    if (elapsed >= config.clickMinMs) emit(GestureTriggered(GestureTrigger.IndexClick, nowMs))
                }
            }
            return
        }
        if (index >= config.clickArmAbove) {
            if (clickArmedSince == NONE) clickArmedSince = nowMs
        } else if (index <= config.clickDownBelow) {
            if (clickArmedSince != NONE && nowMs - clickArmedSince >= config.clickArmMs) clickDownAt = nowMs
            clickArmedSince = NONE
        }
    }

    private fun clearClick() {
        clickArmedSince = NONE
        clickDownAt = NONE
    }

    private fun loseHand(nowMs: Long) {
        for (k in Finger.TOUCHABLE.indices) {
            if (touchActive[k]) endTouch(k, nowMs, countAsTap = false)
            rawStreak[k] = 0
            missStreak[k] = 0
        }
        switchPose(null, nowMs, nowMs)
        candidate = null
        candidateFrames = 0
        fingerStates.fill(null)
        lastCenter = null
        speed = 0f
        clearClick()
        handPresent = false
        emit(HandLost(nowMs))
    }

    private fun emit(event: HandEvent) = listener(event)

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}
