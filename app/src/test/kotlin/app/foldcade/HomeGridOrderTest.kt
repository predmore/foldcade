package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import app.foldcade.language.HomeGrid
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bottom-grid order the emulator walks. Placement stays put across a rescan. */
class HomeGridOrderTest {
    @Test
    fun freshBoardPinsAllThenLooseMarksThenSystemFolders() {
        val shell = shell()
        assertEquals(HomeGrid.StandIns, shell.model.homeGrid)
        assertFalse(shell.model.libraryGrid)
        assertTrue(shell.model.addNewToHome)
        assertEquals(
            listOf(
                Copy.allLibrary,
                "Clamshell",
                "Slim Dual",
                "Handheld",
                "Cartridge",
                "Disc",
                "Cloud",
                "Nintendo 3DS",
                "Nintendo DS",
                "GameNative",
                "Moonlight",
                "Android Games",
                "Android Apps",
            ),
            titles(shell),
        )
        assertTrue(shell.homeFace(0)?.pinned == true)
        assertTrue(shell.homeFace(7)?.folder == true)
        assertEquals("dual", shell.homeFace(7)?.mark)
        assertEquals("beam", shell.homeFace(10)?.mark)
    }

    @Test
    fun openingThe3dsFolderThenConfirmLaunchesTheTileInside() {
        val shell = shell()
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        repeat(3) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        assertEquals("Nintendo 3DS", shell.focusedGame()?.title)
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertEquals("3DS", shell.focusedGame()?.title)
        assertTrue(shell.onMeaning(Meaning.Activate, HostScreen.Bottom) is app.foldcade.language.Effect.Launch)
    }

    @Test
    fun rescanAppendsASystemFolderAndKeepsHiddenGamesOffTheGrid() {
        val shell = shell()
        val game = Game(
            backendId = "local-folder",
            remoteKey = "cart",
            platformId = "gba",
            availability = Availability.LocalOnly,
            label = "Cart",
        )
        shell.ingestLibrary(
            "local-folder",
            listOf(
                GridEntry(
                    id = "cart",
                    title = "Cart",
                    shortText = "gba",
                    platformId = "game-boy-advance",
                    availabilityLabel = null,
                    occupiesBothDisplays = false,
                    game = game,
                ),
            ),
            emptyList(),
        )
        assertEquals("Game Boy Advance", titles(shell).last())
        assertEquals(14, shell.model.count)

        repeat(3) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertEquals("Game Boy Advance", shell.focusedGame()?.title)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals("Cart", shell.focusedGame()?.title)
        shell.enterHomeEdit()
        shell.onMeaning(Meaning.RemoveFromHome, HostScreen.Bottom)
        assertTrue(shell.homeFace(0)?.empty == true)

        shell.openHomeAll()
        val cart = List(shell.model.count) { shell.homeFace(it) }.first { it?.title == "Cart" }
        assertTrue(cart != null)
        assertFalse(cart!!.onGrid)
        assertEquals(Copy.allGames, shell.homeChrome()?.tab)
    }

    private fun titles(shell: ShellController): List<String> =
        List(shell.model.count) { index -> shell.homeFace(index)?.title.orEmpty() }

    private fun shell(): ShellController =
        ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
}
