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

    @Test fun aGroupResultOwnsItsMenuOverAnEmptyHidden() {
        for (collection in listOf(false, true)) {
            val store = SessionStore(MemoryPrefs())
            val id = if (collection) "home.user.1" else HOME_ANDROID_APPS
            val group = HomeFolder(id, "Tools", userMade = collection,
                bucket = if (collection) null else HomeKind.AndroidApp, slots = listOf(androidHomeId("notes")))
            store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL, id),
                folders = mapOf(id to group))))
            val shell = shell(store)
            shell.showHomeGrid(HomeGrid.Hidden)
            assertEquals(0, shell.model.count)
            shell.openFind()
            focusResult(shell, id)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            assertNotNull(shell.model.panel)
            assertEquals(id, shell.model.tileActions!!.showOnHome)
            assertNull(shell.model.tileActions!!.game)
            // Foldcade's folder also has Folders' switch, after the way to its tile.
            assertEquals(if (collection) listOf(Row.ShowOnHome) else listOf(Row.ShowOnHome, Row.FolderOnHome), panelRows(shell.model.panel!!, shell.model))
            shell.onMeaning(Meaning.Activate, HostScreen.Top)
            assertNull(shell.find)
            assertEquals(id, shell.homeFace(shell.model.focus.cellIndex)!!.id)
        }
    }

    @Test fun findsHiddenFilterAndShowUseEveryKindOfUnifiedHiddenItem() {
        // An emulator a player covers is filed in Emulators, not hidden, so Hidden leaves it out.
        // So is a folder off Home: Folders lists it, switched off.
        val ids = setOf(game, androidHomeId("notes"))
        for (target in ids) {
            val store = SessionStore(MemoryPrefs())
            store.saveHomeBoard(encodeHome(HomeBoard(hidden = setOf(game, androidHomeId("notes")), retired = setOf(HOME_MOONLIGHT))))
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
            assertFalse(Row.Hide in rows)
            shell.onMeaning(Meaning.Options, HostScreen.Top)
            assertNotNull(shell.find)
            assertFalse(shell.find!!.results.any { it.id == target })
            val board = decodeHome(store.homeBoardRaw())
            assertFalse(target in board.hidden || target in board.retired)
            if (target == androidHomeId("notes")) assertTrue(board.folders.getValue(HOME_ANDROID_APPS).slots.contains(target))
        }
    }

    @Test fun hideFromFindTargetsTheResultAndUndoPausesAndRestoresIt() {
        for ((overHidden, target) in listOf(false, true).flatMap { hidden -> listOf(game, androidHomeId("notes")).map { hidden to it } }) {
            val store = SessionStore(MemoryPrefs())
            val shell = shell(store)
            if (overHidden) shell.showHomeGrid(HomeGrid.Hidden)
            val before = decodeHome(store.homeBoardRaw())
            shell.openFind()
            focusResult(shell, target)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            // Find has no spot to take a game off, so Remove is not offered; Hide is.
            assertFalse(Row.RemoveTile in panelRows(shell.model.panel!!, shell.model))
            choose(shell, Row.Hide)
            assertTrue(target in decodeHome(store.homeBoardRaw()).hidden)
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
        // Notes stays hidden, so the Hidden filter stays when Drift leaves it.
        store.saveHomeBoard(encodeHome(HomeBoard(hidden = setOf(game, androidHomeId("notes")))))
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
        // Add to's first choice is Home: the game was pinned, then unpinned, which Undo offers back.
        assertEquals(HOME_ROOT, (panelRows(shell.model.panel!!, shell.model).first() as Row.Collection).folderId)
        assertEquals(Copy.unpinned, shell.model.undoVerb)
        choose(shell, panelRows(shell.model.panel!!, shell.model).filterIsInstance<Row.Collection>().first())
        assertTrue(game in decodeHome(store.homeBoardRaw()).slots)
        assertTrue(androidHomeId("notes") in decodeHome(store.homeBoardRaw()).hidden)
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
        walkPanelTo(shell, target, HostScreen.Top)
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
    }
}
