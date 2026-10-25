package app.foldcade.language

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * How far the Thor has turned from the way it was lately held, for the top screen's
 * layers to slide by. Fed the game rotation vector as a unit quaternion, device to
 * world with world z up. The sensor's axes follow the device's natural orientation, and
 * the Thor's panels are portrait-native shown sideways, so [screenTurns] is the screen's
 * rotation from it, as the display reports it. Tipping is read about the hinge, and turning left or right
 * about world up or the level line toward the player, so it does not matter which
 * half holds the sensor or how far the lid is open. [x] and [y] are shares of the full slide, in screen directions, each
 * between -1 and 1. A held lean drifts back to rest over [recenterSeconds].
 */
class TiltFollower(
    private val fullDegrees: Float,
    private val recenterSeconds: Float,
    private val smoothSeconds: Float = SMOOTH_SECONDS,
    screenTurns: Int = 0,
) {
    private var rest: FloatArray? = null

    // The screen's axes in the device's: a turn about the device's z axis, the opposite
    // way to the picture's quarter turns.
    private val screenW: Float
    private val screenZ: Float

    init {
        val half = -Math.floorMod(screenTurns, 4) * Math.PI / 4
        screenW = cos(half).toFloat()
        screenZ = sin(half).toFloat()
    }

    var x = 0f
        private set
    var y = 0f
        private set

    /** Takes one reading, [seconds] after the last. */
    fun follow(w: Float, qx: Float, qy: Float, qz: Float, seconds: Float) {
        // Screen to world: device to world after screen to device.
        val now = normalized(
            w * screenW - qz * screenZ,
            qx * screenW + qy * screenZ,
            qy * screenW - qx * screenZ,
            qz * screenW + w * screenZ,
        ) ?: return
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
        // The hinge is the screen's x axis, wherever it points in the world now.
        val hingeX = 1f - 2f * (ny * ny + nz * nz)
        val hingeY = 2f * (nx * ny + nw * nz)
        val hingeZ = 2f * (nx * nz - nw * ny)
        val tip = turnX * hingeX + turnY * hingeY + turnZ * hingeZ
        // What is left once the tip is taken out turns the Thor left or right. Hands do
        // that as often by rolling it like a wheel, about the level line toward the player,
        // as by swinging it about world up, so both count, the way gyro games read them.
        val sideX = turnX - tip * hingeX
        val sideY = turnY - tip * hingeY
        val sideZ = turnZ - tip * hingeZ
        // That line lies across the hinge's level part, so it fades out rather than
        // flipping round as a Thor on its side brings the hinge upright.
        val roll = sideX * hingeY - sideY * hingeX
        val swing = sideZ + roll
        val side = sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ)
        // A turn about an axis between the two is not both turns at once.
        val turn = swing.coerceIn(-side, side)
        val full = Math.toRadians(fullDegrees.toDouble()).toFloat()
        // The view stays put in the world, so it slides the opposite way to the screen:
        // turning left moves it right, and tipping the top edge back moves it down.
        val across = soft(turn / full)
        val down = soft(-tip / full)
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
