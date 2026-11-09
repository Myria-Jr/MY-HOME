package de.myria.island

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Gedämpfte Feder (wie SwiftUI .spring). Physikalisch integriert,
 * damit Unterbrechungen mitten in der Animation natürlich weiterlaufen.
 */
class Spring(
    var value: Float,
    var stiffness: Float = 380f,
    var dampingRatio: Float = 0.75f,
    private val precision: Float = 0.5f,
) {
    var target: Float = value
    var velocity: Float = 0f

    fun snap(v: Float) {
        value = v; target = v; velocity = 0f
    }

    /** @return true solange die Feder noch in Bewegung ist */
    fun step(dt: Float): Boolean {
        if (value == target && velocity == 0f) return false
        val k = stiffness
        val c = 2f * dampingRatio * sqrt(k)
        var t = dt
        while (t > 0f) {
            val h = min(t, 1f / 240f)
            val force = -k * (value - target) - c * velocity
            velocity += force * h
            value += velocity * h
            t -= h
        }
        if (abs(velocity) < precision * 4f && abs(value - target) < precision) {
            value = target
            velocity = 0f
            return false
        }
        return true
    }
}
