package com.airtouch.handcontrol.core

/**
 * Maps [GestureTriggered] events to app actions of type [A].
 *
 * - A short cooldown stops two different triggers from firing back to back.
 * - Repeats of a held pose only pass for actions that [isRepeatable] accepts.
 * - The trigger bound to [lockAction] toggles a lock; while locked, nothing else is dispatched.
 */
class GestureDispatcher<A : Any>(
    private val isRepeatable: (A) -> Boolean = { false },
    private val onAction: (action: A, trigger: GestureTrigger, repeat: Int) -> Unit,
) {
    var bindings: Map<GestureTrigger, A> = emptyMap()
    var cooldownMs: Long = 350L
    var lockAction: A? = null

    var locked: Boolean = false
        private set

    private var lastFiredAt = Long.MIN_VALUE / 2
    private var lastTrigger: GestureTrigger? = null

    /** Returns the dispatched action, or null when the event was ignored. */
    fun onEvent(event: HandEvent): A? {
        if (event !is GestureTriggered) return null
        val action = bindings[event.trigger] ?: return null
        if (action == lockAction) {
            if (event.repeat > 0) return null
            locked = !locked
            return fire(action, event)
        }
        if (locked) return null
        if (event.repeat > 0) {
            if (!isRepeatable(action) || event.trigger != lastTrigger) return null
        } else if (event.timestampMs - lastFiredAt < cooldownMs) {
            return null
        }
        return fire(action, event)
    }

    fun setLocked(value: Boolean) {
        locked = value
    }

    /** Engine settings implied by the bindings: which poses repeat and which fingers wait for a double tap. */
    fun engineHints(): EngineHints {
        val repeatPoses = bindings.entries
            .filter { (trigger, action) -> trigger is GestureTrigger.Hold && isRepeatable(action) }
            .map { (trigger, _) -> (trigger as GestureTrigger.Hold).pose }
            .toSet()
        val doubleTapFingers = bindings.keys
            .filterIsInstance<GestureTrigger.Touch>()
            .filter { it.kind == TouchKind.DOUBLE_TAP }
            .map { it.finger }
            .toSet()
        return EngineHints(repeatPoses, doubleTapFingers)
    }

    private fun fire(action: A, event: GestureTriggered): A {
        lastFiredAt = event.timestampMs
        lastTrigger = event.trigger
        onAction(action, event.trigger, event.repeat)
        return action
    }
}

data class EngineHints(val repeatPoses: Set<HandPose>, val doubleTapFingers: Set<Finger>)

fun HandControlConfig.withHints(hints: EngineHints): HandControlConfig =
    copy(repeatPoses = hints.repeatPoses, doubleTapFingers = hints.doubleTapFingers)
