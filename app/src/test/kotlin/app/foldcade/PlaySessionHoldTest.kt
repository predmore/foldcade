package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/** Removing a session from Play activity is held, as deleting a download is. */
class PlaySessionHoldTest {
    private var time = 0L
    private var days: (java.time.LocalDate, String?) -> List<String> = { _, _ -> listOf("s1", "s2") }
    private val today = java.time.LocalDate.of(2026, 11, 9)

    @Test
    fun aOnADayListsItsSessionsAndAnEmptyDayStaysOnTheWeek() {
        val shell = page()
        days = { _, _ -> emptyList() }
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        assertNull(shell.model.playActivity?.sessions)
        val asked = mutableListOf<Pair<java.time.LocalDate, String?>>()
        days = { date, game -> asked += date to game; listOf("s1", "s2") }
        // A week back, then a day up: the shell is asked for that date, of everything played.
        shell.onMeaning(Meaning.MoveLeft, HostScreen.Top)
        shell.onMeaning(Meaning.MoveUp, HostScreen.Top)
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        assertEquals(listOf(today.minusDays(8) to null), asked)
        assertEquals(listOf("s1", "s2"), shell.model.playActivity?.sessions)
    }

    @Test
    fun holdingXRemovesTheFocusedSession() {
        val shell = listed()
        shell.onMeaning(Meaning.MoveDown, HostScreen.Top)
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Top)
        val hold = shell.model.deleteHold
        assertEquals(Row.RemoveSession, hold?.row)
        assertEquals("s2", hold?.gameId)
        // Nothing is removed until the hold is done, and the list waits as it was.
        assertEquals(listOf("s1", "s2"), shell.model.playActivity?.sessions)
        time += DELETE_HOLD_MILLIS / 2
        assertNull(shell.tickRecovery())
        assertNotNull(shell.model.deleteHold)
        assertTrue(shell.model.deleteProgress > 0f)
        time += DELETE_HOLD_MILLIS
        assertEquals(Effect.RemovePlaySession("s2"), shell.tickRecovery())
        assertNull(shell.model.deleteHold)
        assertEquals(listOf("s1"), shell.model.playActivity?.sessions)
        assertEquals(0, shell.model.playActivity?.session)
    }

    @Test
    fun lettingGoEarlyKeepsTheSession() {
        val shell = listed()
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Top)
        time += DELETE_HOLD_MILLIS / 2
        assertNull(shell.releaseMeaning(Meaning.ActivateBottom, HostScreen.Top.name))
        assertNull(shell.model.deleteHold)
        time += DELETE_HOLD_MILLIS
        assertNull(shell.tickRecovery())
        assertEquals(listOf("s1", "s2"), shell.model.playActivity?.sessions)
    }

    @Test
    fun anotherKeyOrLosingTheWindowCancels() {
        val shell = listed()
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Top)
        shell.onMeaning(Meaning.Back, HostScreen.Top)
        assertNull(shell.model.deleteHold)
        // The key that canceled the hold does not also close the list.
        assertEquals(listOf("s1", "s2"), shell.model.playActivity?.sessions)
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Top)
        shell.inputFocus(HostScreen.Top.name, false)
        time += DELETE_HOLD_MILLIS
        assertNull(shell.tickRecovery())
        assertEquals(listOf("s1", "s2"), shell.model.playActivity?.sessions)
    }

    @Test
    fun aNewDayWhileThePageIsOpenMovesItsBoundsWithTheClock() {
        val shell = page()
        assertEquals(today, shell.model.playActivity?.calendar?.today)
        // Midnight passes with the page open: the clock tick refreshes today, and the cursor stays put.
        shell.playCalendar = { PlayCalendar(today.plusDays(1), listOf(today.minusDays(40))) }
        shell.notePlayClock()
        assertEquals(today.plusDays(1), shell.model.playActivity?.calendar?.today)
        assertEquals(today, shell.model.playActivity?.cursor)
        // A time zone west of here makes today yesterday: the cursor comes back inside.
        shell.playCalendar = { PlayCalendar(today.minusDays(1), listOf(today.minusDays(40))) }
        shell.notePlayClock()
        assertEquals(today.minusDays(1), shell.model.playActivity?.cursor)
    }

    @Test
    fun aHeldBClosesThePageAndItsRepeatsNeverReachTheGrid() {
        val shell = page()
        val grid = shell.model.playActivity!!.grid
        shell.onMeaning(Meaning.Back, HostScreen.Top)
        assertNull(shell.model.playActivity)
        assertEquals(grid, shell.model.focus)
        val closed = shell.model
        // The key is still down: its repeats belong to the press that closed the page.
        repeat(3) { shell.onMeaning(Meaning.Back, HostScreen.Top, repeatCount = it + 1) }
        assertEquals(closed, shell.model)
        // A fresh press is the grid's again.
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Top)
        assertNotNull(shell.model.panel)
    }

    private fun page(): ShellController {
        val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { time })
        shell.playDaySessions = { date, game -> days(date, game) }
        shell.playCalendar = { PlayCalendar(today, listOf(today.minusDays(40))) }
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Top)
        val rows = panelRows(shell.model.panel!!, shell.model)
        repeat(rows.size) { shell.onMeaning(Meaning.MoveUp, HostScreen.Top) }
        assertEquals(Row.PlayActivity, rows[shell.model.panel!!.index])
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        assertNotNull(shell.model.playActivity)
        return shell
    }

    private fun listed(): ShellController {
        val shell = page()
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        assertEquals(listOf("s1", "s2"), shell.model.playActivity?.sessions)
        return shell
    }
}
