package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class HomeSelectionFolderUndoTest {
    @Test fun undoRestoresTheEmptiedSystemFolderInItsRootSlot() = hideAndUndo(arrival = false)
    @Test fun undoRestoresTheEmptiedFolderAndKeepsADeferredArrival() = hideAndUndo(arrival = true)

    private fun hideAndUndo(arrival: Boolean) {
        val a = androidHomeId("alpha")
        val b = androidHomeId("beta")
        val folder = HomeFolder(HOME_ANDROID_GAMES, "Games", bucket = HomeKind.AndroidGame, slots = listOf(a, b))
        val store = SessionStore(MemoryPrefs()).also { it.saveHomeBoard(encodeHome(HomeBoard(
            slots = listOf(HOME_ALL, null, folder.id), folders = mapOf(folder.id to folder)))) }
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        val apps = listOf(LaunchableApp("alpha", "Alpha", true), LaunchableApp("beta", "Beta", true))
        shell.setInstalledApps(apps, emptySet())
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.SelectTile)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        if (arrival) shell.setInstalledApps(apps + LaunchableApp("notes", "Notes", false), emptySet())
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom)
        val hidden = decodeHome(store.homeBoardRaw())
        assertFalse(folder.id in hidden.slots)
        assertEquals(Copy.hidden, shell.model.undoVerb)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Undo)
        val restored = decodeHome(store.homeBoardRaw())
        assertEquals(folder.id, restored.slots[2])
        assertEquals(listOf(a, b), restored.folders.getValue(folder.id).slots)
        if (arrival) {
            assertEquals(HOME_ANDROID_APPS, restored.slots[1])
            assertEquals(listOf(androidHomeId("notes")), restored.folders.getValue(HOME_ANDROID_APPS).slots)
        } else assertEquals(listOf(HOME_ALL, null, folder.id), restored.slots)
    }

    private fun choose(shell: ShellController, row: Row) {
        val target = panelRows(shell.model.panel!!, shell.model).indexOf(row)
        assertTrue("Missing $row", target >= 0)
        repeat(kotlin.math.abs(target - shell.model.panel!!.index)) {
            shell.onMeaning(if (target > shell.model.panel!!.index) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom)
        }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }
}
