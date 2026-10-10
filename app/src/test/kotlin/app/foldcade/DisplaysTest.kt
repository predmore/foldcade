package app.foldcade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplaysTest {
    @Test
    fun thorDefaultPutsTheDefaultDisplayOnTop() {
        val assignment = assignDisplays(
            defaultDisplayId = 0,
            otherDisplayIds = listOf(5),
            defaultDisplayIsTop = true,
        )
        assertEquals(0, assignment.topDisplayId)
        assertEquals(5, assignment.bottomDisplayId)
        assertEquals(Panel.Top, assignment.panelFor(0))
        assertEquals(Panel.Bottom, assignment.panelFor(5))
    }

    @Test
    fun swappedRolesPutTheOtherDisplayOnTop() {
        val assignment = assignDisplays(
            defaultDisplayId = 4,
            otherDisplayIds = listOf(9),
            defaultDisplayIsTop = false,
        )
        assertEquals(9, assignment.topDisplayId)
        assertEquals(4, assignment.bottomDisplayId)
    }

    @Test
    fun aSingleDisplayDoesNotInventASecondId() {
        val assignment = assignDisplays(
            defaultDisplayId = 3,
            otherDisplayIds = emptyList(),
            defaultDisplayIsTop = true,
        )
        assertEquals(3, assignment.topDisplayId)
        assertNull(assignment.bottomDisplayId)
        assertNull(assignment.panelFor(0))
        assertNull(assignment.panelFor(1))
    }

    @Test
    fun swappedSingleDisplayStaysOnTheDefaultDisplay() {
        val assignment = assignDisplays(
            defaultDisplayId = 3,
            otherDisplayIds = listOf(3),
            defaultDisplayIsTop = false,
        )
        assertEquals(3, assignment.topDisplayId)
        assertNull(assignment.bottomDisplayId)
    }

    @Test
    fun choosesTheLowestOtherIdAndSkipsTheDefault() {
        val assignment = assignDisplays(
            defaultDisplayId = 2,
            otherDisplayIds = listOf(2, 8, 6),
            defaultDisplayIsTop = true,
        )
        assertEquals(6, assignment.bottomDisplayId)
    }
}
