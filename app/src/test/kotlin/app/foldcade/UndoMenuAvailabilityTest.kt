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

    @Test fun hidingTheLastAppInEitherAndroidFolderStillOffersUndo() {
        for (folder in listOf(HOME_ANDROID_APPS, HOME_ANDROID_GAMES)) {
            val store = SessionStore(MemoryPrefs())
            val shell = shell(store)
            shell.setInstalledApps(listOf(notes.copy(systemGame = folder == HOME_ANDROID_GAMES)), emptySet())
            shell.openHomeFolder(folder)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            choose(shell, Row.Hide)
            assertEquals(Copy.hidden, shell.model.undoVerb)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            assertNotNull(shell.model.panel)
            assertEquals(Row.Undo, panelRows(shell.model.panel!!, shell.model).first())
            assertEquals(0, shell.model.panel!!.index)
            shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
            val board = decodeHome(store.homeBoardRaw())
            assertFalse(androidHomeId("notes") in board.hidden)
            assertTrue(androidHomeId("notes") in board.folders.getValue(folder).slots)
        }
    }

    @Test fun emptyHiddenCanUndoAnUnpinAndShowingItsLastItemClearsUndo() {
        val store = SessionStore(MemoryPrefs())
        val shell = shell(store)
        shell.setInstalledApps(listOf(notes), emptySet())
        shell.openHomeFolder(HOME_ANDROID_APPS)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, Row.Collection(HOME_ROOT, Copy.home))
        shell.goHome()
        val pin = decodeHome(store.homeBoardRaw()).slots.indexOf(androidHomeId("notes"))
        focus(shell, pin)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.RemoveTile)
        assertEquals(Copy.unpinned, shell.model.undoVerb)
        shell.showHomeGrid(HomeGrid.Hidden)
        assertEquals(0, shell.model.count)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertNotNull(shell.model.panel)
        assertEquals(listOf(Row.Undo), panelRows(shell.model.panel!!, shell.model))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(androidHomeId("notes") in decodeHome(store.homeBoardRaw()).slots)
        shell.openHomeFolder(HOME_ANDROID_APPS)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Hide)
        shell.showHomeGrid(HomeGrid.Hidden)
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
                folders = mapOf(folder.id to folder))))
            val shell = shell(store)
            shell.setInstalledApps(listOf(notes), emptySet())
            focus(shell, 1)
            shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            // A collection's game comes out of it; a folder keeps every app of its kind, so it is hidden.
            choose(shell, if (collection) Row.RemoveTile else Row.Hide)
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
        walkPanelTo(shell, target, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    private fun focus(shell: ShellController, target: Int) {
        while (GridStrip.column(shell.model.focus.cellIndex) != GridStrip.column(target)) shell.onMeaning(
            if (shell.model.focus.cellIndex < target) Meaning.MoveRight else Meaning.MoveLeft, HostScreen.Bottom)
        while (shell.model.focus.cellIndex != target) shell.onMeaning(
            if (shell.model.focus.cellIndex < target) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom)
    }
}
