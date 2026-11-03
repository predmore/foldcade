package app.foldcade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrontStackTest {
    private var clock = 0L
    private fun shown(pkg: String, cls: String = "$pkg.Main") = FrontMove(pkg, cls, shown = true, atMillis = ++clock)
    private fun left(pkg: String, cls: String = "$pkg.Main") = FrontMove(pkg, cls, shown = false, atMillis = ++clock)

    @Test
    fun anAppOpenedBesideAndLeftGivesWayToTheOneStillInFront() {
        // NetherSX2 on top; a game opened from the bottom command center, then Home on the bottom.
        val stack = FrontStack()
        stack.apply(listOf(shown("nethersx2"), shown("game")))
        assertEquals("game", stack.front())
        stack.apply(listOf(left("game")))
        assertEquals("nethersx2", stack.front())
    }

    @Test
    fun anAppMovingBetweenItsOwnActivitiesStays() {
        // A new activity resumes before the old one stops.
        val stack = FrontStack()
        stack.apply(listOf(shown("files", "files.Picker"), left("files", "files.Picker"), shown("files", "files.Browse"), left("files", "files.Picker")))
        assertEquals("files", stack.front())
    }

    @Test
    fun readingTheLatestMovesAgainChangesNothing() {
        val moves = listOf(shown("a"), shown("b"), left("b"), shown("c"), left("a"))
        val once = FrontStack().apply { apply(moves) }
        val again = FrontStack().apply { apply(moves); apply(moves.takeLast(3)) }
        assertEquals(once.front(), again.front())
        assertEquals("c", again.front())
    }

    @Test
    fun nothingInFrontIsNull() {
        val stack = FrontStack()
        stack.apply(listOf(shown("a"), left("a")))
        assertNull(stack.front())
    }
}
