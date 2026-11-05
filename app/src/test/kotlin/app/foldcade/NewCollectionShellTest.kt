package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/** A collection saved on the keyboard's Done is filed as one a Y action makes (Sol review of #388). */
class NewCollectionShellTest {
    @Test fun aGameACollectionAdmitsJoinsItsFolderWhenTheNameIsSaved() {
        val store = SessionStore(MemoryPrefs())
        // Android Games holds Beta. New games stay off Home, so Alpha arrives in All but not in the folder.
        val beta = androidHomeId("beta")
        val games = HomeFolder(HOME_ANDROID_GAMES, "Android Games", bucket = HomeKind.AndroidGame, slots = listOf(beta))
        store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL, games.id), folders = mapOf(games.id to games), addNewToHome = false)))
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        val alpha = androidHomeId("alpha")
        shell.setInstalledApps(listOf(LaunchableApp("alpha", "Alpha", true), LaunchableApp("beta", "Beta", true)), emptySet())
        assertFalse(decodeHome(store.homeBoardRaw()).folders[HOME_ANDROID_GAMES]?.slots.orEmpty().contains(alpha))
        shell.openHomeAll()
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, Row.NewCollection)
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        val board = decodeHome(store.homeBoardRaw())
        assertTrue(board.folders.values.any { it.collection && alpha in it.slots })
        assertTrue(alpha in board.folders[HOME_ANDROID_GAMES]?.slots.orEmpty())
    }

    private fun choose(shell: ShellController, row: Row) {
        val target = panelRows(shell.model.panel!!, shell.model).indexOf(row)
        assertTrue("Missing $row", target >= 0)
        walkPanelTo(shell, target, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }
}
