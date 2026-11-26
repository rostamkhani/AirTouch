package com.airtouch.handcontrol.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Accuracy on real photos. Landmarks were produced by running the bundled MediaPipe model on
 * cropped hands from the HaGRID sample set (CC BY-SA 4.0). Each photo is fed as a still stream
 * so the full engine (hysteresis, debounce, touches) runs exactly as on the phone.
 */
class RealHandAccuracyTest {
    private val expected: Map<String, Set<HandPose>> = mapOf(
        "call" to setOf(HandPose.CALL),
        "dislike" to setOf(HandPose.THUMB_DOWN),
        "like" to setOf(HandPose.THUMB_UP),
        "fist" to setOf(HandPose.FIST),
        "four" to setOf(HandPose.FOUR),
        "ok" to setOf(HandPose.OK),
        "one" to setOf(HandPose.ONE),
        "palm" to setOf(HandPose.OPEN_PALM),
        "stop" to setOf(HandPose.OPEN_PALM, HandPose.FOUR),
        "stop_inverted" to setOf(HandPose.OPEN_PALM, HandPose.FOUR),
        "peace" to setOf(HandPose.TWO),
        "peace_inverted" to setOf(HandPose.TWO),
        "rock" to setOf(HandPose.ROCK, HandPose.LOVE),
        "three" to setOf(HandPose.THREE),
        "three2" to setOf(HandPose.THREE_THUMB),
    )

    /** In HaGRID "three" the thumb usually rests on the pinky tip, which is the thumb–pinky touch here. */
    private fun accepted(label: String, state: HandControlState): Boolean {
        val poses = expected[label] ?: return true
        if (state.pose in poses) return true
        return label == "three" && state.touches == setOf(Finger.PINKY)
    }

    private fun evaluate(resource: String): Map<String, Float> {
        val hands = RealHands.load(resource)
        val results = LinkedHashMap<String, Float>()
        val report = StringBuilder("\n$resource\n")
        for ((label, group) in hands.groupBy { it.label }.toSortedMap()) {
            val states = group.map { RealHands.settle(it.frame) }
            val correct = group.indices.count { accepted(label, states[it]) }
            val accuracy = correct.toFloat() / group.size
            results[label] = accuracy
            val confusions = states.groupingBy { s -> s.pose?.name ?: s.touches.joinToString("+", prefix = "touch:").ifEmpty { "none" } }
                .eachCount().entries.sortedByDescending { it.value }.take(4)
                .joinToString { "${it.key}=${it.value}" }
            report.append(String.format("  %-15s %5.1f%%  (%d)  %s\n", label, accuracy * 100, group.size, confusions))
        }
        println(report)
        return results
    }

    @Test
    fun heldOutPhotosAreRecognized() {
        val results = evaluate("hagrid_holdout.csv.gz")
        assertMinimums(results)
    }

    @Test
    fun tuningPhotosAreRecognized() {
        val results = evaluate("hagrid_landmarks.csv.gz")
        assertMinimums(results)
    }

    @Test
    fun touchesOnlyFireForTheOkPhotosAmongRealPoses() {
        val hands = RealHands.load("hagrid_holdout.csv.gz")
        for ((label, group) in hands.groupBy { it.label }) {
            if (label == "ok" || label == "three" || label == "mute") continue
            val falseTouches = group.count { RealHands.settle(it.frame).touches.isNotEmpty() }
            val rate = falseTouches.toFloat() / group.size
            assertTrue("$label photos produced touches in ${"%.1f".format(rate * 100)}%", rate <= 0.05f)
        }
    }

    /** HaGRID "three" often has the pinky held half-bent under the thumb, so it splits with FOUR. */
    private val minimum = mapOf("three" to 0.70f).withDefault { 0.80f }

    private fun assertMinimums(results: Map<String, Float>) {
        for ((label, accuracy) in results) {
            if (label !in expected) continue
            val floor = minimum.getValue(label)
            assertTrue("$label accuracy ${"%.1f".format(accuracy * 100)}% is below ${(floor * 100).toInt()}%", accuracy >= floor)
        }
        val mean = results.filterKeys { it in expected }.values.average()
        assertTrue("mean accuracy ${"%.1f".format(mean * 100)}% is below 90%", mean >= 0.90)
    }
}
