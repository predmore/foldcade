package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class HomeSelectionAdmissionTest {
    @Test fun addToHomeWinsOverADeferredReport() = admit(BulkAction.Collection, home = true)
    @Test fun addToWinsOverADeferredReport() = admit(BulkAction.Collection)
    @Test fun newCollectionWinsOverADeferredReport() = admit(BulkAction.NewCollection)

    private fun admit(action: BulkAction, home: Boolean = false) {
        val id = androidHomeId("alpha")
        val other = androidHomeId("beta")
        val folder = HomeFolder(HOME_ANDROID_GAMES, "Games", bucket = HomeKind.AndroidGame, slots = listOf(id, other))
        val collection = HomeFolder("home.user.1", "Quests", userMade = true)
        val before = hideGame(HomeBoard(slots = listOf(HOME_ALL, null, folder.id, collection.id),
            folders = mapOf(folder.id to folder, collection.id to collection)), id)
        val store = SessionStore(MemoryPrefs()).also { it.saveHomeBoard(encodeHome(before)) }
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        val apps = listOf(LaunchableApp("alpha", "Alpha", true), LaunchableApp("beta", "Beta", true))
        shell.setInstalledApps(apps, emptySet())
        shell.openHomeAll()
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.SelectTile)
        shell.setInstalledApps(apps + LaunchableApp("notes", "Notes", false), emptySet())
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        when (action) {
            BulkAction.NewCollection -> {
                choose(shell, Row.AddToCollection)
                choose(shell, Row.NewCollection)
            }
            BulkAction.Collection -> {
                choose(shell, Row.AddToCollection)
                choose(shell, if (home) Row.Collection(HOME_ROOT, Copy.home) else Row.Collection(collection.id, collection.name))
            }
            else -> error("Unsupported admission")
        }
        val after = decodeHome(store.homeBoardRaw())
        assertNull(shell.homeSelection())
        assertFalse("$action must admit the game", id in after.hidden)
        assertTrue(HOME_ANDROID_APPS in after.slots)
        when (action) {
            BulkAction.Collection -> if (home) assertTrue(id in after.slots) else assertTrue(id in after.folders.getValue(collection.id).slots)
            BulkAction.NewCollection -> assertTrue(after.folders.values.any { it.collection && it.id != collection.id && id in it.slots })
            else -> Unit
        }
    }

    private fun choose(shell: ShellController, row: Row) {
        val target = panelRows(shell.model.panel!!, shell.model).indexOf(row)
        assertTrue("Missing $row", target >= 0)
        walkPanelTo(shell, target, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }
}
