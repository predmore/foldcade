package app.foldcade

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTest {
    @Test
    fun ticksOnTheMinuteBoundary() {
        assertEquals(60_000L, millisUntilNextMinute(0L))
        assertEquals(1L, millisUntilNextMinute(59_999L))
        assertEquals(30_000L, millisUntilNextMinute(90_000L))
    }

    @Test
    fun wifiBarsFollowAndroidsScaleForTheDevice() {
        assertEquals(0, scaledBars(0, 4))
        assertEquals(4, scaledBars(4, 4))
        assertEquals(2, scaledBars(2, 4))
        assertEquals(3, scaledBars(2, 3))
        assertEquals(4, scaledBars(5, 4))
        assertEquals(4, scaledBars(0, 0))
    }
}
