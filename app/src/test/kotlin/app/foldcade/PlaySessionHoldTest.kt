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
    private var days: (Int, Int) -> List<String> = { _, _ -> listOf("s1", "s2") }

    @Test
    fun aOnADayListsItsSessionsAndAnEmptyDayStaysOnTheWeek() {
        val shell = page()
        days = { _, _ -> emptyList() }
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        assertNull(shell.model.playActivity?.sessions)
        val asked = mutableListOf<Pair<Int, Int>>()
        days = { weeksBack, day -> asked += weeksBack to day; listOf("s1", "s2") }
        shell.onMeaning(Meaning.MoveLeft, HostScreen.Top)
        shell.onMeaning(Meaning.MoveUp, HostScreen.Top)
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        assertEquals(listOf(1 to PLAY_WEEK_DAYS - 2), asked)
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

    private fun page(): ShellController {
        val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { time })
        shell.playDaySessions = { weeksBack, day -> days(weeksBack, day) }
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
