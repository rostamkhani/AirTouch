package com.airtouch.handcontrol.core

enum class TouchKind { TAP, DOUBLE_TAP, LONG_PRESS }

/** Something an app can bind an action to. [id] is stable and safe to persist. */
sealed interface GestureTrigger {
    val id: String

    /** A static pose held still for the configured hold time. */
    data class Hold(val pose: HandPose) : GestureTrigger {
        override val id: String get() = "hold:${pose.name}"
    }

    /** Thumb tip touching one fingertip. */
    data class Touch(val finger: Finger, val kind: TouchKind) : GestureTrigger {
        override val id: String get() = "touch:${finger.name}:${kind.name}"
    }

    /** Index finger, pointing alone, bent once and straightened again quickly. */
    data object IndexClick : GestureTrigger {
        override val id: String get() = "click:INDEX"
    }

    companion object {
        val ALL: List<GestureTrigger> by lazy {
            HandPose.entries.map { Hold(it) } +
                Finger.TOUCHABLE.flatMap { finger -> TouchKind.entries.map { Touch(finger, it) } } +
                IndexClick
        }

        fun parse(id: String): GestureTrigger? = ALL.firstOrNull { it.id == id }
    }
}
