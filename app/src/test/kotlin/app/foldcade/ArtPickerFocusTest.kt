package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class ArtPickerFocusTest {
    private val games = listOf(LaunchableApp("beta", "Beta", true), LaunchableApp("charlie", "Charlie", true))

    @Test fun androidHomeFromAllOrAFolderClosesArtAndRejectsLateResultsAndSaves() {
        for (fromFolder in listOf(false, true)) {
            val applied = mutableListOf<String>()
            val shell = shell { id, _, _ -> applied += id }
            if (fromFolder) {
                val folder = (0 until shell.model.count).first { shell.homeFace(it)?.id == HOME_ANDROID_GAMES }
                shell.touchCell(folder, HostScreen.Bottom)
                shell.touchCell(folder, HostScreen.Bottom)
            } else shell.openHomeAll()
            val opened = openArt(shell)
            shell.goHome()
            assertNull(shell.artPicker)
            assertNotEquals(opened.gameId, shell.focusedGame()?.id)
            shell.artCandidates(opened)
            shell.artMatches(opened.gameId, listOf(ArtMatch(2, "Late match")))
            shell.artInput(Meaning.Activate)
            shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
            assertNull(shell.artPicker)
            assertTrue("Home must never save art for the previous game", applied.isEmpty())
        }
    }

    @Test fun explicitPlaceAndViewChangesCloseArtEvenWhenTheSameGameRemainsSelected() {
        val changes = listOf<Pair<String, (ShellController) -> Unit>>(
            "All" to { it.openHomeAll() },
            "Games" to { it.showHomeGrid(HomeGrid.AndroidGames) },
            "Apps" to { it.showHomeGrid(HomeGrid.Apps) },
            "Curated Home" to { it.showCuratedHome() },
            "Library" to { it.showNoLibrary() },
            "Chrome focus" to { it.touchChrome(Chrome.StatusCluster, HostScreen.Top) },
        )
        for ((name, change) in changes) {
            val shell = shell()
            shell.openHomeAll()
            openArt(shell)
            change(shell)
            assertNull(name, shell.artPicker)
            assertNotEquals(HomeKeys.ChoosingArt, shell.homeKeys())
        }
    }

    @Test fun aCatalogUpdateClosesArtIfTheItemAtTheSameCellChanges() {
        val shell = shell()
        shell.openHomeAll()
        val opened = openArt(shell)
        shell.setInstalledApps(games, emptySet())
        shell.setLibrarySyncing(true)
        assertEquals("Updates that keep the same focused game should preserve Art", opened, shell.artPicker)
        shell.setInstalledApps(listOf(LaunchableApp("alpha", "Alpha", true)) + games, emptySet())
        assertEquals("Alpha", shell.focusedGame()?.title)
        assertNull(shell.artPicker)
    }

    private fun shell(onApply: (String, ArtKind, String?) -> Unit = { _, _, _ -> }): ShellController =
        ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()),
            onArtApply = onApply).also {
            it.setInstalledApps(games, emptySet())
            it.setArtKeySaved(true)
        }

    private fun openArt(shell: ShellController): ArtPickerState {
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val index = panelRows(shell.model.panel!!, shell.model).indexOf(Row.ChooseArt)
        assertTrue(index >= 0)
        shell.touchPanel(index, HostScreen.Top)
        shell.touchPanel(index, HostScreen.Top)
        val gameId = shell.artPicker!!.gameId
        shell.artMatches(gameId, listOf(ArtMatch(1, "Beta")))
        val loaded = shell.artPicker!!.copy(loading = false,
            candidates = listOf(ArtCandidate("1", "https://cdn/art", "https://cdn/thumb")))
        shell.artCandidates(loaded)
        return shell.artPicker!!
    }
}
