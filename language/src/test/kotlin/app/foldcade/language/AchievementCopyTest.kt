package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AchievementCopyTest {
    @Test
    fun theLineSaysProgressOnlyWhenItIsKnown() {
        assertEquals("12 of 40 achievements", achievementLine(earned = 12, total = 40))
        assertEquals("0 of 40 achievements", achievementLine(earned = 0, total = 40))
        assertEquals("40 achievements", achievementLine(earned = null, total = 40))
        assertEquals("1 achievement", achievementLine(earned = null, total = 1))
        assertEquals("40 of 40 achievements", achievementLine(earned = 41, total = 40))
        assertNull(achievementLine(earned = 3, total = 0))
    }

    @Test
    fun pointsAreLeftOutWhenThereAreNone() {
        assertEquals("10 points", achievementPoints(10))
        assertEquals("1 point", achievementPoints(1))
        assertNull(achievementPoints(0))
        assertNull(achievementPoints(null))
    }
}
