package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class KeptGameMenuTest {
    private val app = LaunchableApp("notes", "Notes", false)
    private val other = LaunchableApp("browser", "Browser", false)
    private val store = SessionStore(MemoryPrefs())
    private fun shell() = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { 100L })

    @Test fun aKeptGameMenuDropsSlotActionsAndNoOpsKeepItsUndo() {
        val collection = HomeFolder("home.user.1", "Tools", userMade = true, slots = listOf(androidHomeId("notes")))
        store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL, collection.id),
            folders = mapOf(collection.id to collection), emulatorsFiled = true)))
        val shell = shell()
        shell.setInstalledApps(listOf(app, other), emptySet())
        focus(shell, 1)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, panelRows(shell.model.panel!!, shell.model).filterIsInstance<Row.Collection>().single { it.folderId == collection.id })
        assertNotNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        val rows = panelRows(shell.model.panel!!, shell.model)
        assertFalse(Row.MoveTile in rows)
        assertFalse(Row.RemoveTile in rows)
        assertEquals(androidHomeId("notes"), shell.model.tileActions!!.game)
        assertEquals(Row.AddToCollection, rows[shell.model.panel!!.index])
        val saved = store.homeBoardRaw()
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom)
        shell.pickUpHome(0)
        assertEquals(saved, store.homeBoardRaw())
        assertNotNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.applyHomeAddNew(shell.model.addNewToHome)
        assertNotNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Row.Undo, panelRows(shell.model.panel!!, shell.model).first())
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNotNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(androidHomeId("notes") in decodeHome(store.homeBoardRaw()).folders.getValue(collection.id).slots)
    }

    @Test fun keepingTheRemainingAppOnTopRemovesUndoWithoutActivatingAnotherRow() {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { 100L })
        shell.setInstalledApps(listOf(other, app), emptySet())
        shell.showHomeGrid(HomeGrid.Apps)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.RemoveTile)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Row.Undo, panelRows(shell.model.panel!!, shell.model).first())
        choose(shell, Row.PinApp)
        assertNull(shell.model.undoVerb)
        assertNotNull(shell.model.panel)
        val rows = panelRows(shell.model.panel!!, shell.model)
        assertFalse(Row.Undo in rows)
        assertEquals(Row.PinApp, rows[shell.model.panel!!.index])
        assertTrue(store.appShelfState().record("notes").favorite)
        assertTrue(store.appShelfState().record("browser").hidden)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertFalse(Row.Undo in panelRows(shell.model.panel!!, shell.model))
    }

    private fun focus(shell: ShellController, target: Int) {
        val columns = GridStrip.column(target) - GridStrip.column(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(columns)) { shell.onMeaning(if (columns > 0) Meaning.MoveRight else Meaning.MoveLeft, HostScreen.Bottom) }
        val rows = GridStrip.row(target) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(rows)) { shell.onMeaning(if (rows > 0) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom) }
        assertEquals(target, shell.model.focus.cellIndex)
    }

    private fun choose(shell: ShellController, row: Row) {
        val rows = panelRows(shell.model.panel!!, shell.model)
        val target = rows.indexOf(row)
        assertTrue("Missing $row in $rows", target >= 0)
        repeat(kotlin.math.abs(target - shell.model.panel!!.index)) {
            shell.onMeaning(if (target > shell.model.panel!!.index) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom)
        }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }
}
