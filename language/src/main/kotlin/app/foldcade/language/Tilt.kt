package app.foldcade.language

import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * How far the Thor has turned from the way it was lately held, for the top screen's
 * layers to slide by. Fed the game rotation vector as a unit quaternion, device to
 * world with world z up. Turning left or right is read about world up, and tipping
 * about the hinge, so it does not matter which half holds the sensor or how far the
 * lid is open. [x] and [y] are shares of the full slide, in screen directions, each
 * between -1 and 1. A held lean drifts back to rest over [recenterSeconds].
 */
class TiltFollower(
    private val fullDegrees: Float,
    private val recenterSeconds: Float,
    private val smoothSeconds: Float = SMOOTH_SECONDS,
) {
    private var rest: FloatArray? = null

    var x = 0f
        private set
    var y = 0f
        private set

    /** Takes one reading, [seconds] after the last. */
    fun follow(w: Float, qx: Float, qy: Float, qz: Float, seconds: Float) {
        val now = normalized(w, qx, qy, qz) ?: return
        val base = rest
        if (base == null) {
            rest = now
            return
        }
        val dt = seconds.coerceIn(0f, MAX_STEP_SECONDS)
        // The turn from rest to now, in world axes: now times rest's inverse.
        val bw = base[0]
        val bx = base[1]
        val by = base[2]
        val bz = base[3]
        val nw = now[0]
        val nx = now[1]
        val ny = now[2]
        val nz = now[3]
        var dw = nw * bw + nx * bx + ny * by + nz * bz
        var dx = bw * nx - nw * bx - (ny * bz - nz * by)
        var dy = bw * ny - nw * by - (nz * bx - nx * bz)
        var dz = bw * nz - nw * bz - (nx * by - ny * bx)
        if (dw < 0f) {
            dw = -dw; dx = -dx; dy = -dy; dz = -dz
        }
        // Small turns: twice the vector part is the turn in radians about each world axis.
        val turnX = 2f * dx
        val turnY = 2f * dy
        val turnZ = 2f * dz
        // The hinge is the device's x axis, wherever it points in the world now.
        val hingeX = 1f - 2f * (ny * ny + nz * nz)
        val hingeY = 2f * (nx * ny + nw * nz)
        val hingeZ = 2f * (nx * nz - nw * ny)
        val full = Math.toRadians(fullDegrees.toDouble()).toFloat()
        // The view stays put in the world, so it slides the opposite way to the screen:
        // turning left moves it right, and tipping the top edge back moves it down.
        val across = soft(turnZ / full)
        val down = soft(-(turnX * hingeX + turnY * hingeY + turnZ * hingeZ) / full)
        val follow = 1f - exp(-dt / smoothSeconds)
        x += (across - x) * follow
        y += (down - y) * follow
        rest = toward(base, now, 1f - exp(-dt / recenterSeconds.coerceAtLeast(MIN_RECENTER_SECONDS)))
    }

    /** Forgets the rest, so the next reading is the new one and the layers are home. */
    fun reset() {
        rest = null
        x = 0f
        y = 0f
    }

    private fun soft(share: Float): Float = tanh(share.toDouble()).toFloat()

    private companion object {
        /** Long enough to hide the sensor's jitter, short enough to feel attached. */
        const val SMOOTH_SECONDS = .12f
        const val MAX_STEP_SECONDS = .25f
        const val MIN_RECENTER_SECONDS = .05f

        fun normalized(w: Float, x: Float, y: Float, z: Float): FloatArray? {
            val length = sqrt(w * w + x * x + y * y + z * z)
            if (length < 1e-6f || length.isNaN()) return null
            return floatArrayOf(w / length, x / length, y / length, z / length)
        }

        /** [from] a share of the way to [to], on the short way round. */
        fun toward(from: FloatArray, to: FloatArray, share: Float): FloatArray {
            val sign = if (from[0] * to[0] + from[1] * to[1] + from[2] * to[2] + from[3] * to[3] < 0f) -1f else 1f
            return normalized(
                from[0] + (to[0] * sign - from[0]) * share,
                from[1] + (to[1] * sign - from[1]) * share,
                from[2] + (to[2] * sign - from[2]) * share,
                from[3] + (to[3] * sign - from[3]) * share,
            ) ?: to
        }
    }
}
