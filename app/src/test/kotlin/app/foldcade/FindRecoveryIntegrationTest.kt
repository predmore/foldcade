package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class FindRecoveryIntegrationTest {
    private var time = 100L
    private val notes = LaunchableApp("notes", "Notes", false)
    private val emulator = LaunchableApp("emu", "Emulator", false)
    private val game = homeGameId("local-folder", "drift")
    private fun shell(store: SessionStore) = ShellController(store,
        PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { time }).also {
        it.setInstalledApps(listOf(notes, emulator), setOf("emu"))
        it.ingestLibrary("local-folder", listOf(GridEntry("drift", "Drift", "GBA", "gba", null, false,
            Game("local-folder", "drift", "gba", Availability.LocalOnly, "Drift"))), emptyList())
    }

    @Test fun findsHiddenFilterAndShowUseEveryKindOfUnifiedHiddenItem() {
        val ids = setOf(game, androidHomeId("notes"), androidHomeId("emu"), HOME_MOONLIGHT)
        for (target in ids) {
            val store = SessionStore(MemoryPrefs())
            store.saveHomeBoard(encodeHome(HomeBoard(hidden = setOf(game), retired = setOf(HOME_MOONLIGHT))))
            store.saveAppShelfState(AppShelfState().hide("notes"))
            val shell = shell(store)
            shell.openFind()
            val find = shell.find!!
            while (find.filters[find.filter].kind != FindFilterKind.Hidden) shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
            assertEquals(ids, find.results.map { it.id }.toSet())
            assertTrue(find.results.all { it.hidden && FindCopy.hidden in shell.findFacts(it) })
            focusResult(shell, target)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            val rows = panelRows(shell.model.panel!!, shell.model)
            assertTrue(Row.ShowHidden in rows)
            assertEquals(rows.indexOf(Row.ShowHidden), chordIndex(rows, Meaning.Options))
            assertFalse(Row.PinTile in rows)
            shell.onMeaning(Meaning.Options, HostScreen.Top)
            assertNotNull(shell.find)
            assertFalse(shell.find!!.results.any { it.id == target })
            val board = decodeHome(store.homeBoardRaw())
            assertFalse(target in board.hidden || target in board.retired)
            if (target == HOME_MOONLIGHT) assertTrue(target in board.slots)
            if (target == androidHomeId("notes")) assertFalse(store.appShelfState().record("notes").hidden)
            if (target == androidHomeId("emu")) assertTrue(store.appShelfState().record("emu").showDespitePlayer)
        }
    }

    @Test fun hideFromFindTargetsTheResultAndUndoPausesAndRestoresIt() {
        for ((overShelf, target) in listOf(false, true).flatMap { shelf -> listOf(game, androidHomeId("notes")).map { shelf to it } }) {
            val store = SessionStore(MemoryPrefs())
            val shell = shell(store)
            if (overShelf) shell.showHomeGrid(HomeGrid.AndroidGames)
            val before = decodeHome(store.homeBoardRaw())
            shell.openFind()
            focusResult(shell, target)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            choose(shell, Row.RemoveTile)
            assertTrue(target in decodeHome(store.homeBoardRaw()).hidden)
            if (target == androidHomeId("notes")) assertTrue(store.appShelfState().record("notes").hidden)
            assertEquals(Copy.hidden, shell.model.undoVerb)
            assertNotNull(shell.find)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            assertEquals(Row.Undo, panelRows(shell.model.panel!!, shell.model).first())
            time += 12_000L
            shell.tickRecovery()
            shell.onMeaning(Meaning.Activate, HostScreen.Top)
            assertEquals(before, decodeHome(store.homeBoardRaw()))
            assertFalse(shell.find!!.results.first { it.id == target }.hidden)
            assertNull(shell.model.undoVerb)
        }
    }

    @Test fun findEditsInvalidateUndoAndAMenuKeepsTheResultAfterTheFilterChanges() {
        val store = SessionStore(MemoryPrefs())
        store.saveHomeBoard(encodeHome(HomeBoard(hidden = setOf(game))))
        val shell = shell(store)
        shell.openFind()
        val find = shell.find!!
        while (find.filters[find.filter].kind != FindFilterKind.Hidden) shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        focusResult(shell, game)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, panelRows(shell.model.panel!!, shell.model).filterIsInstance<Row.Collection>().first())
        assertEquals(game, shell.model.tileActions!!.game)
        assertFalse(find.results.any { it.id == game })
        choose(shell, panelRows(shell.model.panel!!, shell.model).filterIsInstance<Row.Collection>().first())
        assertNotNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Back, HostScreen.Top)
        choose(shell, Row.Favorite)
        assertTrue(game in decodeHome(store.homeBoardRaw()).folders.getValue(HOME_FAVORITES).slots)
        assertFalse(androidHomeId("notes") in decodeHome(store.homeBoardRaw()).hidden)
        assertNull(shell.model.undoVerb)
    }

    private fun focusResult(shell: ShellController, id: String) {
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        val find = shell.find!!
        val target = find.results.indexOfFirst { it.id == id }
        assertTrue(target >= 0)
        while (find.index != target) shell.onMeaning(if (find.index < target) Meaning.MoveRight else Meaning.MoveLeft, HostScreen.Bottom)
    }

    private fun choose(shell: ShellController, row: Row) {
        val target = panelRows(shell.model.panel!!, shell.model).indexOf(row)
        assertTrue("Missing $row", target >= 0)
        while (shell.model.panel!!.index != target) shell.onMeaning(if (shell.model.panel!!.index < target) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Top)
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
    }
}
