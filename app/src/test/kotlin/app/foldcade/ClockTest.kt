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
}
