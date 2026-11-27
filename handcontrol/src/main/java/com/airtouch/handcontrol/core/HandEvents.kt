package com.airtouch.handcontrol.core

sealed interface HandEvent {
    val timestampMs: Long
}

data class HandAppeared(override val timestampMs: Long) : HandEvent

data class HandLost(override val timestampMs: Long) : HandEvent

data class PoseStarted(val pose: HandPose, override val timestampMs: Long) : HandEvent

data class PoseEnded(val pose: HandPose, val durationMs: Long, override val timestampMs: Long) : HandEvent

data class TouchStarted(val finger: Finger, override val timestampMs: Long) : HandEvent

data class TouchEnded(val finger: Finger, val durationMs: Long, override val timestampMs: Long) : HandEvent

/** [repeat] is 0 for the first firing and counts up while a repeating pose stays held. */
data class GestureTriggered(
    val trigger: GestureTrigger,
    override val timestampMs: Long,
    val repeat: Int = 0,
) : HandEvent

enum class HandQuality {
    NO_HAND,
    GOOD,
    MOVING,
    TOO_SMALL,
    PARTLY_OUTSIDE,
}

data class HandControlState(
    val timestampMs: Long = 0L,
    val handPresent: Boolean = false,
    val quality: HandQuality = HandQuality.NO_HAND,
    val pose: HandPose? = null,
    val poseCandidate: HandPose? = null,
    val poseHeldMs: Long = 0L,
    val holdProgress: Float = 0f,
    val touches: Set<Finger> = emptySet(),
    val touchCandidates: Set<Finger> = emptySet(),
    val fingerStates: List<FingerState?> = List(5) { null },
    val fingerOpenness: List<Float> = List(5) { 0f },
    val touchDistances: List<Float> = List(4) { Float.NaN },
    val thumbAngleDegrees: Float? = null,
    val speedPalmsPerSecond: Float = 0f,
    val frame: HandFrame? = null,
) {
    companion object {
        val EMPTY = HandControlState()
    }
}
