package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class CloseGameHostTest {
    private val id = homeGameId("local-folder", "sonic")
    private fun shell() = ShellController(SessionStore(MemoryPrefs()),
        PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())).also {
        it.ingestLibrary("local-folder", listOf(GridEntry("sonic", "Sonic", "GBA", "gba", null, false,
            Game("local-folder", "sonic", "gba", Availability.LocalOnly, "Sonic"))), emptyList())
        it.openHomeAll()
        it.resumable = true
        it.running = setOf(id)
        it.canCloseGame = { true }
        it.moveGameScreen = { HostScreen.Bottom }
    }

    @Test fun xClosesAPausedTileButRepeatedXDoesNothing() {
        val shell = shell()
        assertEquals(id, shell.focusedGame()?.id)
        assertNull(shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom, repeatCount = 1))
        assertEquals(Effect.CloseGame(id), shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom))
        assertNull(shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom, repeatCount = 1))
        shell.releaseMeaning(Meaning.ActivateBottom)
        shell.running = emptySet()
        assertTrue(shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom) is Effect.Launch)
    }

    @Test fun aPageOverAPausedTileKeepsItsOwnX() {
        val shell = shell()
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        shell.onMeaning(Meaning.Menu, HostScreen.Bottom)
        assertNotNull(shell.model.settings)
        assertFalse(shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom) is Effect.CloseGame)
    }

    @Test fun findsXAndMenuActOnThePausedResult() {
        val shell = shell()
        shell.openFind()
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        assertEquals(id, shell.find!!.subject?.id)
        assertEquals(Effect.CloseGame(id), shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom))
        shell.releaseMeaning(Meaning.ActivateBottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val rows = panelRows(shell.model.panel!!, shell.model)
        assertTrue(Row.RestartGame in rows && Row.CloseGame in rows && Row.MoveGame in rows)
        assertEquals(Effect.CloseGame(id), shell.onMeaning(Meaning.ActivateBottom, HostScreen.Top))
    }
}
