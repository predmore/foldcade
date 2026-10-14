package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class UndoMenuAvailabilityTest {
    private val notes = LaunchableApp("notes", "Notes", false)
    private fun shell(store: SessionStore) = ShellController(store,
        PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { 100L })

    @Test fun hidingTheLastAppOnEitherShelfStillOpensUndo() {
        for (grid in listOf(HomeGrid.Apps, HomeGrid.AndroidGames)) {
            val store = SessionStore(MemoryPrefs())
            val shell = shell(store)
            shell.setInstalledApps(listOf(notes.copy(systemGame = grid == HomeGrid.AndroidGames)), emptySet())
            shell.showHomeGrid(grid)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            choose(shell, Row.RemoveTile)
            assertEquals(0, shell.model.count)
            assertEquals(Copy.hidden, shell.model.undoVerb)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            assertNotNull(shell.model.panel)
            assertEquals(listOf(Row.Undo), panelRows(shell.model.panel!!, shell.model))
            assertEquals(0, shell.model.panel!!.index)
            shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
            assertEquals(1, shell.model.count)
            assertFalse(store.appShelfState().record("notes").hidden)
        }
    }

    @Test fun emptyHiddenCanUndoAnUnpinAndShowingItsLastItemClearsUndo() {
        val store = SessionStore(MemoryPrefs())
        val shell = shell(store)
        shell.setInstalledApps(listOf(notes), emptySet())
        shell.showHomeGrid(HomeGrid.Apps)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.PinTile)
        shell.goHome()
        val pin = decodeHome(store.homeBoardRaw()).slots.indexOf(androidHomeId("notes"))
        focus(shell, pin)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.RemoveTile)
        shell.showHomeGrid(HomeGrid.HiddenApps)
        assertEquals(0, shell.model.count)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertNotNull(shell.model.panel)
        assertEquals(listOf(Row.Undo), panelRows(shell.model.panel!!, shell.model))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(androidHomeId("notes") in decodeHome(store.homeBoardRaw()).slots)
        shell.showHomeGrid(HomeGrid.Apps)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.RemoveTile)
        shell.showHomeGrid(HomeGrid.HiddenApps)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.ShowHidden)
        assertEquals(0, shell.model.count)
        assertNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertNull(shell.model.panel)
    }

    @Test fun hidingOrRemovingTheLastGroupMemberStillOffersUndo() {
        for (collection in listOf(false, true)) {
            val store = SessionStore(MemoryPrefs())
            val folder = HomeFolder(if (collection) "home.user.1" else HOME_ANDROID_APPS,
                "Tools", userMade = collection, bucket = if (collection) null else HomeKind.AndroidApp,
                slots = listOf(androidHomeId("notes")))
            store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL, folder.id),
                folders = mapOf(folder.id to folder), emulatorsFiled = true)))
            val shell = shell(store)
            shell.setInstalledApps(listOf(notes), emptySet())
            focus(shell, 1)
            shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            choose(shell, Row.RemoveTile)
            assertFalse(androidHomeId("notes") in decodeHome(store.homeBoardRaw()).folders.getValue(folder.id).slots)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            assertNotNull(shell.model.panel)
            assertEquals(Row.Undo, panelRows(shell.model.panel!!, shell.model).first())
            shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
            assertTrue(androidHomeId("notes") in decodeHome(store.homeBoardRaw()).folders.getValue(folder.id).slots)
        }
    }

    private fun choose(shell: ShellController, row: Row) {
        val target = panelRows(shell.model.panel!!, shell.model).indexOf(row)
        assertTrue(target >= 0)
        while (shell.model.panel!!.index != target) shell.onMeaning(
            if (shell.model.panel!!.index < target) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    private fun focus(shell: ShellController, target: Int) {
        while (GridStrip.column(shell.model.focus.cellIndex) != GridStrip.column(target)) shell.onMeaning(
            if (shell.model.focus.cellIndex < target) Meaning.MoveRight else Meaning.MoveLeft, HostScreen.Bottom)
        while (shell.model.focus.cellIndex != target) shell.onMeaning(
            if (shell.model.focus.cellIndex < target) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom)
    }
}
