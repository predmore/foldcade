package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class HomeSelectionViewChangeTest {
    @Test fun aShelfOpenedThroughTheMenusEndsSelectionAndFlushesTheRefresh() = changeView(Row.Apps)
    @Test fun theGamesShelfAlsoEndsSelectionAndFlushesTheRefresh() = changeView(Row.AndroidGames)
    @Test fun settingsOpenedThroughTheMenusEndsSelectionAndFlushesTheRefresh() = changeView(Row.Settings)
    @Test fun anExternalLibraryViewEndsSelectionAndFlushesTheRefresh() = changeView(null)

    private fun changeView(destination: Row?) {
        val id = androidHomeId("alpha")
        val store = SessionStore(MemoryPrefs()).also { it.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL, id)))) }
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        val game = LaunchableApp("alpha", "Alpha", true)
        shell.setInstalledApps(listOf(game), emptySet())
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.SelectTile)
        shell.setInstalledApps(listOf(game, LaunchableApp("notes", "Notes", false)), emptySet())
        assertFalse(HOME_ANDROID_APPS in decodeHome(store.homeBoardRaw()).slots)
        if (destination == null) shell.showNoLibrary() else {
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            shell.onMeaning(Meaning.RightPanel, HostScreen.Bottom)
            shell.completeIslandRetire()
            shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
            shell.completeIslandRetire()
            choose(shell, destination)
        }
        when (destination) {
            Row.Apps -> assertEquals(HomeGrid.Apps, shell.model.homeGrid)
            Row.AndroidGames -> assertEquals(HomeGrid.AndroidGames, shell.model.homeGrid)
            Row.Settings -> assertNotNull(shell.model.settings)
            null -> assertTrue(shell.model.libraryGrid)
            else -> Unit
        }
        if (destination == Row.Apps || destination == Row.AndroidGames) assertTrue("Shelf actions must remain available", shell.model.appActions != null)
        assertTrue("The pending arrival must have merged", HOME_ANDROID_APPS in decodeHome(store.homeBoardRaw()).slots)
        shell.goHome()
        assertNull("Selection must not return on Home", shell.homeSelection())
        assertEquals(HomeKeys.Idle, shell.homeKeys())
    }

    private fun choose(shell: ShellController, row: Row) {
        val target = panelRows(shell.model.panel!!, shell.model).indexOf(row)
        assertTrue("Missing $row", target >= 0)
        if (shell.model.panel!!.index != target) shell.touchPanel(target, HostScreen.Top)
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
    }
}
