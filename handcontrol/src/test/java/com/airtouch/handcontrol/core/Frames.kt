package com.airtouch.handcontrol.core

import kotlin.math.cos
import kotlin.math.sin

/** Real frames picked from the fixture plus small geometric edits for sequence tests. */
object Frames {
    private val hands by lazy { RealHands.load("hagrid_landmarks.csv.gz") }

    /** First real frame of [label] that settles to [pose] (and, if given, exactly [touches]). */
    fun real(label: String, pose: HandPose?, touches: Set<Finger> = emptySet()): HandFrame {
        return hands.asSequence()
            .filter { it.label == label }
            .map { it.frame }
            .first { frame ->
                val state = RealHands.settle(frame)
                state.pose == pose && state.touches == touches && state.quality == HandQuality.GOOD
            }
    }

    val palm by lazy { real("palm", HandPose.OPEN_PALM) }
    val fist by lazy { real("fist", HandPose.FIST) }
    val ok by lazy { real("ok", HandPose.OK, setOf(Finger.INDEX)) }
    val one by lazy { real("one", HandPose.ONE) }
    val like by lazy { real("like", HandPose.THUMB_UP) }
    val peace by lazy { real("peace", HandPose.TWO) }

    /** Moves the thumb tip onto [finger]'s tip in both 2D and 3D, keeping the rest of the hand. */
    fun touching(base: HandFrame, finger: Finger): HandFrame {
        val image = base.image.toMutableList()
        val world = base.world.toMutableList()
        val tip = image[finger.tip]
        val distal = image[finger.distal]
        image[HandLandmark.THUMB_TIP] = Vec3(tip.x + (tip.x - distal.x) * 0.1f, tip.y + (tip.y - distal.y) * 0.1f, tip.z)
        image[HandLandmark.THUMB_IP] = lerp(image[HandLandmark.THUMB_MCP], image[HandLandmark.THUMB_TIP], 0.55f)
        world[HandLandmark.THUMB_TIP] = world[finger.tip]
        world[HandLandmark.THUMB_IP] = lerp(world[HandLandmark.THUMB_MCP], world[HandLandmark.THUMB_TIP], 0.55f)
        return base.copy(image = image, world = world)
    }

    fun translated(base: HandFrame, dx: Float, dy: Float): HandFrame =
        base.copy(image = base.image.map { Vec3(it.x + dx, it.y + dy, it.z) })

    fun scaled(base: HandFrame, factor: Float): HandFrame {
        val cx = base.image.map { it.x }.average().toFloat()
        val cy = base.image.map { it.y }.average().toFloat()
        return base.copy(image = base.image.map { Vec3(0.5f + (it.x - cx) * factor, 0.5f + (it.y - cy) * factor, it.z) })
    }

    /** Rotates the 2D points about the image center by [degrees] (clockwise on screen) in a square frame. */
    fun rotated(base: HandFrame, degrees: Float): HandFrame {
        val radians = Math.toRadians(degrees.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val size = maxOf(base.imageWidth, base.imageHeight)
        val image = base.image.map { point ->
            val px = point.x * base.imageWidth - base.imageWidth / 2f
            val py = point.y * base.imageHeight - base.imageHeight / 2f
            Vec3((px * c - py * s + size / 2f) / size, (px * s + py * c + size / 2f) / size, point.z)
        }
        return base.copy(imageWidth = size, imageHeight = size, image = image)
    }

    private fun lerp(a: Vec3, b: Vec3, t: Float) = Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
}

/** Drives an engine through timed segments of frames at a fixed frame rate. */
class Timeline(config: HandControlConfig = HandControlConfig()) {
    val events = mutableListOf<HandEvent>()
    val engine = HandControlEngine(config) { events += it }
    var now = 0L
        private set
    var frameMs = 66L

    fun hold(frame: HandFrame?, durationMs: Long): HandControlState {
        val end = now + durationMs
        var state = engine.state
        while (now < end) {
            state = engine.process(frame?.copy(timestampMs = now), now)
            now += frameMs
        }
        return state
    }

    fun triggers(): List<GestureTriggered> = events.filterIsInstance<GestureTriggered>()
    fun triggerIds(): List<String> = triggers().map { it.trigger.id }
}
