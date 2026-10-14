package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class HomeSelectionAdmissionTest {
    @Test fun pinWinsOverADeferredHiddenAppReport() = admit(BulkAction.Pin)
    @Test fun favoriteWinsOverADeferredHiddenAppReport() = admit(BulkAction.Favorite)
    @Test fun addToWinsOverADeferredHiddenAppReport() = admit(BulkAction.Collection)
    @Test fun newCollectionWinsOverADeferredHiddenAppReport() = admit(BulkAction.NewCollection)

    private fun admit(action: BulkAction) {
        val id = androidHomeId("alpha")
        val other = androidHomeId("beta")
        val folder = HomeFolder(HOME_ANDROID_GAMES, "Games", bucket = HomeKind.AndroidGame, slots = listOf(id, other))
        val collection = HomeFolder("home.user.1", "Quests", userMade = true)
        val before = hideGame(HomeBoard(slots = listOf(HOME_ALL, null, folder.id, collection.id),
            folders = mapOf(folder.id to folder, collection.id to collection)), id)
        val store = SessionStore(MemoryPrefs()).also {
            it.saveHomeBoard(encodeHome(before))
            it.saveAppShelfState(AppShelfState().hide("alpha"))
        }
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        val apps = listOf(LaunchableApp("alpha", "Alpha", true), LaunchableApp("beta", "Beta", true))
        shell.setInstalledApps(apps, emptySet())
        shell.openHomeAll()
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.SelectTile)
        shell.setInstalledApps(apps + LaunchableApp("notes", "Notes", false), emptySet())
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        when (action) {
            BulkAction.Pin -> choose(shell, Row.PinTile)
            BulkAction.Favorite -> choose(shell, Row.Favorite)
            BulkAction.NewCollection -> choose(shell, Row.NewCollection)
            BulkAction.Collection -> {
                choose(shell, Row.AddToCollection)
                choose(shell, Row.Collection(collection.id, collection.name))
            }
            else -> error("Unsupported admission")
        }
        val after = decodeHome(store.homeBoardRaw())
        assertNull(shell.homeSelection())
        assertFalse("$action must admit the game", id in after.hidden)
        assertFalse("$action must show the app shelf", store.appShelfState().record("alpha").hidden)
        assertTrue(HOME_ANDROID_APPS in after.slots)
        when (action) {
            BulkAction.Pin -> assertTrue(id in after.slots)
            BulkAction.Favorite -> assertTrue(favorite(after, id))
            BulkAction.Collection -> assertTrue(id in after.folders.getValue(collection.id).slots)
            BulkAction.NewCollection -> assertTrue(after.folders.values.any { it.collection && it.id != collection.id && id in it.slots })
            else -> Unit
        }
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
