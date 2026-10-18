package app.foldcade.language

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiltTest {
    /** Held upright, screen toward you: the device's z axis points at you, level with the floor. */
    private val upright = about(1f, 0f, 0f, 90f)

    @Test
    fun theFirstReadingIsRest() {
        val tilt = TiltFollower(fullDegrees = 6f, recenterSeconds = 2.4f)
        tilt.follow(upright, 0.02f)
        assertEquals(0f, tilt.x, 0f)
        assertEquals(0f, tilt.y, 0f)
    }

    @Test
    fun turningLeftSlidesTheViewRightAndTippingBackSlidesItDown() {
        val left = TiltFollower(6f, 1000f)
        settle(left, upright, times(about(0f, 0f, 1f, 4f), upright))
        assertTrue(left.x > 0.4f)
        assertEquals(0f, left.y, 0.02f)

        // The hinge is level and runs across the screen, so tipping the top edge
        // away from you turns the device the negative way about it.
        val back = TiltFollower(6f, 1000f)
        settle(back, upright, times(about(1f, 0f, 0f, -4f), upright))
        assertTrue(back.y > 0.4f)
        assertEquals(0f, back.x, 0.02f)
    }

    @Test
    fun aBigTurnStopsShortOfAFullSlide() {
        val tilt = TiltFollower(6f, 1000f)
        settle(tilt, upright, times(about(0f, 0f, 1f, 60f), upright))
        assertTrue(tilt.x > 0.95f)
        assertTrue(tilt.x <= 1f)
    }

    @Test
    fun turningInThePlaneOfTheScreenSlidesNothing() {
        val tilt = TiltFollower(6f, 1000f)
        // Upright, the screen's own axis is level, so a steering-wheel turn has no part about world up.
        settle(tilt, upright, times(about(0f, -1f, 0f, 10f), upright))
        assertEquals(0f, tilt.x, 0.02f)
        assertEquals(0f, tilt.y, 0.02f)
    }

    @Test
    fun aHeldLeanDriftsHome() {
        val tilt = TiltFollower(6f, recenterSeconds = 2.4f)
        val leaned = times(about(0f, 0f, 1f, 4f), upright)
        tilt.follow(upright, 0.02f)
        repeat(15) { tilt.follow(leaned, 0.02f) }
        val moved = tilt.x
        assertTrue(moved > 0.2f)
        repeat(1000) { tilt.follow(leaned, 0.02f) }
        assertTrue(abs(tilt.x) < 0.01f)
    }

    @Test
    fun resetPutsTheLayersHome() {
        val tilt = TiltFollower(6f, 1000f)
        settle(tilt, upright, times(about(0f, 0f, 1f, 4f), upright))
        tilt.reset()
        assertEquals(0f, tilt.x, 0f)
        tilt.follow(times(about(0f, 0f, 1f, 4f), upright), 0.02f)
        assertEquals(0f, tilt.x, 0f)
    }

    @Test
    fun tiltCoverHidesTheEdgeOfAFullScreenLayer() {
        val side = 1080f
        val cover = Motion.tiltCover(side)
        assertTrue(side * (cover - 1f) / 2f >= Motion.tiltPx - 0.001f)
        assertEquals(1f, Motion.tiltCover(0f), 0f)
        assertTrue(Motion.tiltArtShare < 1f)
    }

    private fun settle(tilt: TiltFollower, rest: FloatArray, now: FloatArray) {
        tilt.follow(rest, 0.02f)
        repeat(100) { tilt.follow(now, 0.02f) }
    }

    private fun TiltFollower.follow(q: FloatArray, seconds: Float) = follow(q[0], q[1], q[2], q[3], seconds)

    /** A turn of [degrees] about the axis (x, y, z), as w, x, y, z. */
    private fun about(x: Float, y: Float, z: Float, degrees: Float): FloatArray {
        val half = Math.toRadians(degrees.toDouble()) / 2
        val s = sin(half).toFloat()
        return floatArrayOf(cos(half).toFloat(), x * s, y * s, z * s)
    }

    /** [a] after [b]. */
    private fun times(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
        a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
        a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
        a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
        a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0],
    )
}
