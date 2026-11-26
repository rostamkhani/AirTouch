package com.airtouch.app.gestures

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Gesture decisions from timed hand samples. Callers must use one clock for
 * [HandSample.timestampMs] and the `nowMs` arguments. Media and camera code stay outside.
 */
class GestureEngine(
    config: GestureConfig = GestureConfig(),
    private val onEvent: (GestureEvent) -> Unit = {},
) {
    var config: GestureConfig = config
        private set

    var generation: Int = 0
        private set

    var snapshot: EngineSnapshot = EngineSnapshot.EMPTY
        private set

    private var state = EngineState.WAITING_FOR_HAND
    private var stabilizeStart = 0L
    private var stabilizeCount = 0
    private var lastAcceptedSampleTs = Long.MIN_VALUE
    private var lastSampleNow = 0L
    private var handPresent = false
    private var smoothedPalm: Point2? = null
    private val history = ArrayDeque<TimedPoint>()
    private var cooldownUntil = 0L
    private var neutralPoint: Point2? = null
    private var stillSince: Long? = null
    private var fistSince: Long? = null
    private var openSince: Long? = null
    private var latched = false
    private var requireOpenBeforePinch = true
    private var pinchCandidate = PinchKind.NONE
    private var pinchCandidateSince = 0L
    private var pinchCandidateSamples = 0
    private var releaseSamples = 0
    private var lastCommand: GestureCommand? = null
    private var lastLatencyMs = 0L
    private var lastFeatures: HandFeatures? = null
    private var lastPoints: List<Point2> = emptyList()
    private var lastHandedness = Handedness.UNKNOWN
    private var lastWristZ: Float? = null
    private var lastCandidate = GestureCandidate.NONE

    fun updateConfig(newConfig: GestureConfig) {
        config = newConfig
        clearMotion()
        latched = false
        requireOpenBeforePinch = true
        if (state != EngineState.SUSPENDED) {
            state = EngineState.WAITING_FOR_HAND
            handPresent = false
        }
        publish(lastSampleNow)
    }

    fun setGeneration(value: Int) {
        if (value == generation) return
        generation = value
        clearMotion()
        latched = false
        requireOpenBeforePinch = true
        if (state != EngineState.SUSPENDED) {
            state = EngineState.WAITING_FOR_HAND
        }
        handPresent = false
        publish(lastSampleNow)
    }

    fun suspendFromUser(nowMs: Long) {
        if (state == EngineState.SUSPENDED) return
        state = EngineState.SUSPENDED
        handPresent = snapshot.handPresent
        fire(GestureCommand.SUSPEND, nowMs)
        clearMotion()
        latched = false
        requireOpenBeforePinch = true
        publish(nowMs)
    }

    fun resumeFromUser(nowMs: Long) {
        if (state != EngineState.SUSPENDED) return
        state = EngineState.READY
        fire(GestureCommand.RESUME, nowMs)
        clearMotion()
        requireOpenBeforePinch = true
        publish(nowMs)
    }

    fun heartbeat(nowMs: Long) {
        if (!handPresent || lastSampleNow == 0L) return
        if (nowMs - lastSampleNow <= config.handLossMs) return
        handPresent = false
        loseHand()
        publish(nowMs)
    }

    fun onSample(sample: HandSample, nowMs: Long) {
        if (sample.generation != generation) return
        if (nowMs - sample.timestampMs > config.maxResultAgeMs) return
        if (sample.timestampMs <= lastAcceptedSampleTs && lastAcceptedSampleTs != Long.MIN_VALUE) return
        val features = HandGeometry.features(sample) ?: return
        lastAcceptedSampleTs = sample.timestampMs
        lastSampleNow = nowMs
        handPresent = true
        lastFeatures = features
        lastPoints = sample.landmarks.map { Point2(it.x, it.y) }
        lastHandedness = sample.handedness
        lastWristZ = sample.worldLandmarks.firstOrNull()?.z
        lastLatencyMs = (nowMs - sample.timestampMs).coerceAtLeast(0)
        val timestamp = sample.timestampMs

        if (state == EngineState.SUSPENDED) {
            handleSuspended(features, timestamp)
            publish(nowMs)
            return
        }

        if (state == EngineState.WAITING_FOR_HAND) {
            state = EngineState.STABILIZING
            stabilizeStart = timestamp
            stabilizeCount = 0
            clearMotion()
            requireOpenBeforePinch = true
        }

        if (state == EngineState.STABILIZING) {
            stabilizeCount += 1
            val ready = timestamp - stabilizeStart >= config.stabilizeMs &&
                stabilizeCount >= config.stabilizeSamples
            if (!ready) {
                lastCandidate = GestureCandidate.NONE
                publish(nowMs)
                return
            }
            state = EngineState.READY
            requireOpenBeforePinch = true
            history.clear()
            smoothedPalm = null
        }

        if (isFist(features)) {
            lastCandidate = GestureCandidate.FIST
            if (fistSince == null) fistSince = timestamp
            if (timestamp - fistSince!! >= config.holdMs) {
                state = EngineState.SUSPENDED
                fire(GestureCommand.SUSPEND, timestamp)
                clearMotion()
                latched = false
                requireOpenBeforePinch = true
            }
            publish(nowMs)
            return
        }
        fistSince = null

        if (requireOpenBeforePinch && isContact(features)) {
            latched = true
            state = EngineState.PINCH_LATCHED
            lastCandidate = contactCandidate(features)
            publish(nowMs)
            return
        }
        if (requireOpenBeforePinch && !isContact(features)) {
            requireOpenBeforePinch = false
        }

        if (state == EngineState.WAITING_FOR_NEUTRAL) {
            updateNeutral(features, timestamp)
            publish(nowMs)
            return
        }

        if (handlePinch(features, timestamp)) {
            publish(nowMs)
            return
        }

        if (state == EngineState.READY) {
            considerSwipe(features, timestamp)
        }
        publish(nowMs)
    }

    private fun handleSuspended(features: HandFeatures, timestamp: Long) {
        if (!isOpenPalm(features)) {
            openSince = null
            lastCandidate = GestureCandidate.NONE
            return
        }
        lastCandidate = GestureCandidate.OPEN_PALM
        if (openSince == null) openSince = timestamp
        if (timestamp - openSince!! >= config.holdMs) {
            state = EngineState.READY
            openSince = null
            clearMotion()
            requireOpenBeforePinch = true
            fire(GestureCommand.RESUME, timestamp)
        }
    }

    private fun handlePinch(features: HandFeatures, timestamp: Long): Boolean {
        val kind = allowedPinch(features)
        if (latched || state == EngineState.PINCH_LATCHED) {
            val released = features.pinchIndexRatio > config.pinchExit &&
                features.pinchMiddleRatio > config.pinchExit
            if (released) {
                releaseSamples += 1
                if (releaseSamples >= 2) {
                    latched = false
                    state = EngineState.READY
                    releaseSamples = 0
                    pinchCandidate = PinchKind.NONE
                    pinchCandidateSamples = 0
                    requireOpenBeforePinch = false
                }
            } else {
                releaseSamples = 0
            }
            lastCandidate = contactCandidate(features)
            return true
        }

        if (kind == PinchKind.NONE) {
            pinchCandidate = PinchKind.NONE
            pinchCandidateSamples = 0
            lastCandidate = GestureCandidate.NONE
            return false
        }

        lastCandidate = when (kind) {
            PinchKind.INDEX -> GestureCandidate.PINCH_INDEX
            PinchKind.MIDDLE -> GestureCandidate.PINCH_MIDDLE
            PinchKind.AMBIGUOUS -> GestureCandidate.AMBIGUOUS_PINCH
            PinchKind.NONE -> GestureCandidate.NONE
        }
        if (kind != pinchCandidate) {
            pinchCandidate = kind
            pinchCandidateSince = timestamp
            pinchCandidateSamples = 1
            return true
        }
        pinchCandidateSamples += 1
        val confirmed = pinchCandidateSamples >= config.pinchConfirmSamples &&
            timestamp - pinchCandidateSince >= config.pinchConfirmMs
        if (!confirmed || kind == PinchKind.AMBIGUOUS) return true

        latched = true
        state = EngineState.PINCH_LATCHED
        val startedDuringCooldown = pinchCandidateSince < cooldownUntil
        val command = if (kind == PinchKind.INDEX) {
            GestureCommand.PLAY_PAUSE
        } else {
            GestureCommand.MUTE_TOGGLE
        }
        if (!startedDuringCooldown && timestamp >= cooldownUntil && command in config.enabled) {
            fire(command, timestamp)
            cooldownUntil = timestamp + config.cooldownMs
        }
        return true
    }

    private fun considerSwipe(features: HandFeatures, timestamp: Long) {
        val palm = smooth(features.palmCenterPx)
        history.addLast(TimedPoint(timestamp, palm))
        while (history.size > 1 && timestamp - history.first().t > config.swipeWindowMaxMs) {
            history.removeFirst()
        }
        if (!isPalmOpen(features)) {
            lastCandidate = if (lastCandidate == GestureCandidate.NONE) GestureCandidate.NONE else lastCandidate
            return
        }
        if (history.size < config.swipeMinSamples) return
        val first = history.first()
        val last = history.last()
        val elapsed = last.t - first.t
        if (elapsed < config.swipeWindowMinMs) return
        val dx = last.p.x - first.p.x
        val dy = last.p.y - first.p.y
        val adx = abs(dx)
        val ady = abs(dy)
        val along = max(adx, ady)
        val across = min(adx, ady)
        if (across > 1f && along < config.axisDominance * across) {
            lastCandidate = GestureCandidate.DIAGONAL
            return
        }
        val horizontal = adx >= ady
        val fraction = if (horizontal) adx / features.imageWidth else ady / features.imageHeight
        val palms = along / features.palmWidthPx
        val speed = palms / (elapsed / 1000f)
        if (fraction < config.swipeMinFraction || palms < config.swipeMinPalmWidths || speed < config.swipeMinSpeed) {
            return
        }
        val command = if (horizontal) {
            if (dx > 0f) GestureCommand.NEXT else GestureCommand.PREVIOUS
        } else {
            if (dy < 0f) GestureCommand.VOLUME_UP else GestureCommand.VOLUME_DOWN
        }
        lastCandidate = when (command) {
            GestureCommand.NEXT -> GestureCandidate.SWIPE_RIGHT
            GestureCommand.PREVIOUS -> GestureCandidate.SWIPE_LEFT
            GestureCommand.VOLUME_UP -> GestureCandidate.SWIPE_UP
            else -> GestureCandidate.SWIPE_DOWN
        }
        val startedDuringCooldown = first.t < cooldownUntil
        val allowed = command in config.enabled && !startedDuringCooldown && timestamp >= cooldownUntil
        neutralPoint = first.p
        state = EngineState.WAITING_FOR_NEUTRAL
        stillSince = null
        history.clear()
        if (allowed) {
            fire(command, timestamp)
            cooldownUntil = timestamp + config.cooldownMs
        }
    }

    private fun updateNeutral(features: HandFeatures, timestamp: Long) {
        val target = neutralPoint
        if (target == null) {
            state = EngineState.READY
            requireOpenBeforePinch = true
            return
        }
        val palm = smooth(features.palmCenterPx)
        history.addLast(TimedPoint(timestamp, palm))
        while (history.size > 1 && timestamp - history.first().t > config.swipeWindowMaxMs) {
            history.removeFirst()
        }
        val speed = recentSpeed(timestamp, features.palmWidthPx)
        val away = HandGeometry.distance(palm, target)
        lastCandidate = GestureCandidate.NONE
        if (away <= config.neutralRadiusPalm * features.palmWidthPx && speed <= config.neutralMaxSpeed) {
            if (stillSince == null) stillSince = timestamp
            if (timestamp - stillSince!! >= config.neutralStillMs && timestamp >= cooldownUntil) {
                state = EngineState.READY
                neutralPoint = null
                stillSince = null
                history.clear()
                smoothedPalm = palm
                requireOpenBeforePinch = true
            }
        } else {
            stillSince = null
        }
    }

    private fun recentSpeed(timestamp: Long, palmWidth: Float): Float {
        val recent = history.filter { timestamp - it.t <= config.neutralStillMs }
        if (recent.size < 2 || palmWidth <= 0f) return 0f
        val elapsed = recent.last().t - recent.first().t
        if (elapsed <= 0L) return 0f
        val travelled = HandGeometry.distance(recent.first().p, recent.last().p) / palmWidth
        return travelled / (elapsed / 1000f)
    }

    private fun smooth(raw: Point2): Point2 {
        val alpha = config.smoothingAlpha.coerceIn(0f, 1f)
        val previous = smoothedPalm
        val next = if (previous == null || alpha >= 0.999f) {
            raw
        } else {
            Point2(
                previous.x + (raw.x - previous.x) * alpha,
                previous.y + (raw.y - previous.y) * alpha,
            )
        }
        smoothedPalm = next
        return next
    }

    private fun fire(command: GestureCommand, timestamp: Long) {
        lastCommand = command
        onEvent(GestureEvent(command, timestamp))
    }

    private fun loseHand() {
        clearMotion()
        latched = false
        requireOpenBeforePinch = true
        lastFeatures = null
        lastPoints = emptyList()
        lastCandidate = GestureCandidate.NONE
        if (state != EngineState.SUSPENDED) {
            state = EngineState.WAITING_FOR_HAND
        }
    }

    private fun clearMotion() {
        history.clear()
        smoothedPalm = null
        neutralPoint = null
        stillSince = null
        fistSince = null
        openSince = null
        pinchCandidate = PinchKind.NONE
        pinchCandidateSamples = 0
        releaseSamples = 0
        stabilizeCount = 0
    }

    private fun isContact(features: HandFeatures): Boolean {
        return features.pinchIndexRatio < config.pinchEnter ||
            features.pinchMiddleRatio < config.pinchEnter
    }

    private fun allowedPinch(features: HandFeatures): PinchKind {
        val index = features.pinchIndexRatio < config.pinchEnter
        val middle = features.pinchMiddleRatio < config.pinchEnter
        val kind = when {
            index && middle -> PinchKind.AMBIGUOUS
            index -> PinchKind.INDEX
            middle -> PinchKind.MIDDLE
            else -> PinchKind.NONE
        }
        return when (kind) {
            PinchKind.INDEX ->
                if (isOpen(features, Finger.MIDDLE, Finger.RING, Finger.PINKY)) kind else PinchKind.AMBIGUOUS
            PinchKind.MIDDLE ->
                if (isOpen(features, Finger.INDEX, Finger.RING, Finger.PINKY)) kind else PinchKind.AMBIGUOUS
            else -> kind
        }
    }

    private fun contactCandidate(features: HandFeatures): GestureCandidate {
        return when (allowedPinch(features)) {
            PinchKind.INDEX -> GestureCandidate.PINCH_INDEX
            PinchKind.MIDDLE -> GestureCandidate.PINCH_MIDDLE
            PinchKind.AMBIGUOUS -> GestureCandidate.AMBIGUOUS_PINCH
            PinchKind.NONE -> GestureCandidate.NONE
        }
    }

    private fun isFist(features: HandFeatures): Boolean {
        return isClosed(features, Finger.INDEX, Finger.MIDDLE, Finger.RING, Finger.PINKY) &&
            features.fingers[Finger.THUMB] != FingerPose.OPEN
    }

    private fun isPalmOpen(features: HandFeatures): Boolean {
        return isOpen(features, Finger.INDEX, Finger.MIDDLE, Finger.RING, Finger.PINKY) &&
            features.fingers[Finger.THUMB] != FingerPose.CLOSED &&
            features.pinchIndexRatio > config.pinchExit &&
            features.pinchMiddleRatio > config.pinchExit
    }

    private fun isOpenPalm(features: HandFeatures): Boolean {
        return Finger.entries.all { features.fingers[it] == FingerPose.OPEN } &&
            features.pinchIndexRatio > config.pinchExit &&
            features.pinchMiddleRatio > config.pinchExit
    }

    private fun isOpen(features: HandFeatures, vararg fingers: Finger): Boolean {
        return fingers.all { features.fingers[it] == FingerPose.OPEN }
    }

    private fun isClosed(features: HandFeatures, vararg fingers: Finger): Boolean {
        return fingers.all { features.fingers[it] == FingerPose.CLOSED }
    }

    private fun publish(nowMs: Long) {
        val features = lastFeatures
        snapshot = EngineSnapshot(
            state = state,
            handPresent = handPresent,
            handedness = if (handPresent) lastHandedness else Handedness.UNKNOWN,
            fingers = if (handPresent) features?.fingers.orEmpty() else emptyMap(),
            points = if (handPresent) lastPoints else emptyList(),
            pinchIndex = if (handPresent) features?.pinchIndexRatio else null,
            pinchMiddle = if (handPresent) features?.pinchMiddleRatio else null,
            candidate = if (handPresent) lastCandidate else GestureCandidate.NONE,
            lastCommand = lastCommand,
            lastLatencyMs = if (handPresent) lastLatencyMs else 0,
            imageWidth = features?.imageWidth ?: 0,
            imageHeight = features?.imageHeight ?: 0,
            wristWorldZ = if (handPresent) lastWristZ else null,
            neutralPending = state == EngineState.WAITING_FOR_NEUTRAL,
        )
        if (nowMs < 0) return
    }

    private data class TimedPoint(val t: Long, val p: Point2)
}
