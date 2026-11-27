package com.airtouch.handcontrol.core

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/** One real hand from the HaGRID photo sample, run through the same MediaPipe model the app ships. */
data class RealHand(val label: String, val frame: HandFrame)

object RealHands {
    fun load(resource: String): List<RealHand> {
        val stream = RealHands::class.java.classLoader!!.getResourceAsStream(resource)
            ?: error("missing test resource $resource")
        BufferedReader(InputStreamReader(GZIPInputStream(stream))).use { reader ->
            return reader.lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map(::parse)
                .toList()
        }
    }

    private fun parse(line: String): RealHand {
        val parts = line.split(',')
        val label = parts[0]
        val width = parts[1].toInt()
        val height = parts[2].toInt()
        val handedness = when (parts[3]) {
            "Left" -> Handedness.LEFT
            "Right" -> Handedness.RIGHT
            else -> Handedness.UNKNOWN
        }
        val values = parts.drop(4).map { it.toFloat() }
        val image = List(21) { Vec3(values[it * 3], values[it * 3 + 1], values[it * 3 + 2]) }
        val world = List(21) { Vec3(values[63 + it * 3], values[63 + it * 3 + 1], values[63 + it * 3 + 2]) }
        return RealHand(label, HandFrame(0L, width, height, image, world, handedness))
    }

    /** Feeds the same still frame for about [durationMs] at 15 fps and returns the settled state. */
    fun settle(
        frame: HandFrame,
        config: HandControlConfig = HandControlConfig(),
        durationMs: Long = 800L,
        events: MutableList<HandEvent>? = null,
    ): HandControlState {
        val engine = HandControlEngine(config) { events?.add(it) }
        var state = HandControlState.EMPTY
        var now = 0L
        while (now <= durationMs) {
            state = engine.process(frame.copy(timestampMs = now), now)
            now += 66L
        }
        return state
    }
}
