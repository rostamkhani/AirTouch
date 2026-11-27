package com.airtouch.handcontrol.core

import kotlin.math.acos
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float = 0f) {
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)
    infix fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z
    fun length(): Float = sqrt(this dot this)
    fun distanceTo(other: Vec3): Float = (this - other).length()

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}

/** Bend at [joint] in degrees: 0 when a, joint and c are in a straight line. */
internal fun flexionDegrees(a: Vec3, joint: Vec3, c: Vec3): Float {
    val first = a - joint
    val second = c - joint
    val denominator = first.length() * second.length()
    if (denominator <= 1e-9f) return 0f
    val cosine = ((first dot second) / denominator).coerceIn(-1f, 1f)
    return 180f - Math.toDegrees(acos(cosine).toDouble()).toFloat()
}
