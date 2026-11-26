package com.airtouch.app.gestures

enum class Finger {
    THUMB,
    INDEX,
    MIDDLE,
    RING,
    PINKY,
}

enum class FingerPose {
    OPEN,
    CLOSED,
    UNKNOWN,
}

enum class Handedness {
    LEFT,
    RIGHT,
    UNKNOWN,
}

enum class GestureCommand {
    NEXT,
    PREVIOUS,
    PLAY_PAUSE,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE_TOGGLE,
    SUSPEND,
    RESUME,
}

enum class EngineState {
    WAITING_FOR_HAND,
    STABILIZING,
    READY,
    PINCH_LATCHED,
    WAITING_FOR_NEUTRAL,
    SUSPENDED,
}

enum class GestureCandidate {
    NONE,
    SWIPE_RIGHT,
    SWIPE_LEFT,
    SWIPE_UP,
    SWIPE_DOWN,
    PINCH_INDEX,
    PINCH_MIDDLE,
    AMBIGUOUS_PINCH,
    FIST,
    OPEN_PALM,
    DIAGONAL,
}

enum class PinchKind {
    NONE,
    INDEX,
    MIDDLE,
    AMBIGUOUS,
}

data class Point2(val x: Float, val y: Float)

data class Landmark2(val x: Float, val y: Float, val z: Float = 0f)

data class HandSample(
    val timestampMs: Long,
    val landmarks: List<Landmark2>,
    val worldLandmarks: List<Landmark2> = emptyList(),
    val handedness: Handedness,
    val imageWidth: Int,
    val imageHeight: Int,
    val generation: Int = 0,
)

data class GestureEvent(
    val command: GestureCommand,
    val timestampMs: Long,
)

data class HandFeatures(
    val palmCenterPx: Point2,
    val palmWidthPx: Float,
    val fingers: Map<Finger, FingerPose>,
    val pinchIndexRatio: Float,
    val pinchMiddleRatio: Float,
    val imageWidth: Int,
    val imageHeight: Int,
)

data class EngineSnapshot(
    val state: EngineState,
    val handPresent: Boolean,
    val handedness: Handedness,
    val fingers: Map<Finger, FingerPose>,
    val points: List<Point2>,
    val pinchIndex: Float?,
    val pinchMiddle: Float?,
    val candidate: GestureCandidate,
    val lastCommand: GestureCommand?,
    val lastLatencyMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val wristWorldZ: Float?,
    val neutralPending: Boolean,
) {
    companion object {
        val EMPTY = EngineSnapshot(
            state = EngineState.WAITING_FOR_HAND,
            handPresent = false,
            handedness = Handedness.UNKNOWN,
            fingers = emptyMap(),
            points = emptyList(),
            pinchIndex = null,
            pinchMiddle = null,
            candidate = GestureCandidate.NONE,
            lastCommand = null,
            lastLatencyMs = 0,
            imageWidth = 0,
            imageHeight = 0,
            wristWorldZ = null,
            neutralPending = false,
        )
    }
}

data class GestureConfig(
    val stabilizeMs: Long = 300,
    val stabilizeSamples: Int = 3,
    val swipeWindowMinMs: Long = 150,
    val swipeWindowMaxMs: Long = 500,
    val swipeMinSamples: Int = 3,
    val swipeMinFraction: Float = 0.12f,
    val swipeMinPalmWidths: Float = 1f,
    val swipeMinSpeed: Float = 2.5f,
    val axisDominance: Float = 1.8f,
    val pinchEnter: Float = 0.25f,
    val pinchExit: Float = 0.40f,
    val pinchConfirmMs: Long = 80,
    val pinchConfirmSamples: Int = 2,
    val cooldownMs: Long = 700,
    val holdMs: Long = 1_000,
    val neutralRadiusPalm: Float = 0.5f,
    val neutralStillMs: Long = 200,
    val neutralMaxSpeed: Float = 0.8f,
    val handLossMs: Long = 250,
    val maxResultAgeMs: Long = 250,
    val smoothingAlpha: Float = 0.65f,
    val enabled: Set<GestureCommand> = DEFAULT_ENABLED,
) {
    init {
        require(pinchExit > pinchEnter) { "pinch exit must stay above pinch enter" }
        require(stabilizeMs > 0 && holdMs > 0 && cooldownMs >= 0)
    }

    companion object {
        val DEFAULT_ENABLED = setOf(
            GestureCommand.NEXT,
            GestureCommand.PREVIOUS,
            GestureCommand.PLAY_PAUSE,
            GestureCommand.VOLUME_UP,
            GestureCommand.VOLUME_DOWN,
            GestureCommand.MUTE_TOGGLE,
        )
    }
}
