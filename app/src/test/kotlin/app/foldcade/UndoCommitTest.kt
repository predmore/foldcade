package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class UndoCommitTest {
    @Test fun touchKeyboardSaveInvalidatesAnOlderRemoval() {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(LaunchableApp("notes", "Notes", false), LaunchableApp("browser", "Browser", false)), emptySet())
        shell.showHomeGrid(HomeGrid.Apps)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.RemoveTile)
        assertNotNull(shell.model.undoVerb)
        shell.goHome()
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.RenameFolder)
        shell.typing()!!.setText("Renamed apps")
        shell.tapTyping(KeyboardLayout.find(KeyCap.Done))
        assertEquals("Renamed apps", decodeHome(store.homeBoardRaw()).folders.getValue(HOME_ANDROID_APPS).name)
        assertNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertFalse(Row.Undo in panelRows(shell.model.panel!!, shell.model))
    }

    @Test fun catalogUpdatesKeepUndoAndSurviveRestoration() {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        val old = LaunchableApp("notes", "Notes", false)
        val kept = LaunchableApp("kept", "Zoo", false)
        shell.setInstalledApps(listOf(old, kept), emptySet())
        shell.showHomeGrid(HomeGrid.Apps)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.RemoveTile)
        val newApp = LaunchableApp("browser", "Browser", false)
        shell.setInstalledApps(listOf(old, kept, newApp), emptySet())
        assertNotNull(shell.model.undoVerb)
        val updated = decodeHome(store.homeBoardRaw())
        val newFolderSlot = updated.slots.indexOf(HOME_ANDROID_APPS)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Undo)
        val restored = decodeHome(store.homeBoardRaw())
        assertTrue(onHome(restored, androidHomeId("notes")))
        assertTrue(onHome(restored, androidHomeId("browser")))
        assertEquals(newFolderSlot, restored.slots.indexOf(HOME_ANDROID_APPS))
    }

    private fun choose(shell: ShellController, row: Row) {
        val target = panelRows(shell.model.panel!!, shell.model).indexOf(row)
        assertTrue(target >= 0)
        while (shell.model.panel!!.index != target) shell.onMeaning(if (shell.model.panel!!.index < target) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }
}
