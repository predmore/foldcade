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
    fun freshBoardPinsAllThenSystemFolders() {
        val shell = shell()
        assertEquals(HomeGrid.StandIns, shell.model.homeGrid)
        assertFalse(shell.model.libraryGrid)
        assertTrue(shell.model.addNewToHome)
        assertEquals(
            listOf(
                Copy.allLibrary,
                "Nintendo 3DS",
                "Nintendo DS",
                "GameNative",
                "Moonlight",
                "Android Games",
                "Android Apps",
            ),
            titles(shell),
        )
        assertTrue(titles(shell).none { it == "Clamshell" || it == "Slim Dual" || it == "Handheld" })
        assertTrue(shell.homeFace(0)?.pinned == true)
        assertTrue(shell.homeFace(1)?.folder == true)
        assertEquals("dual", shell.homeFace(1)?.mark)
        assertEquals("beam", shell.homeFace(4)?.mark)
    }

    @Test
    fun openingThe3dsFolderThenConfirmLaunchesTheTileInside() {
        val shell = shell()
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
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
        assertEquals(8, shell.model.count)

        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        repeat(3) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
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

    @Test
    fun aPlatformOnlyTheLibraryNamesUsesThatName() {
        val shell = shell()
        val game = Game(
            backendId = "romm",
            remoteKey = "42",
            platformId = "segacd",
            availability = Availability.RemoteOnly,
            label = "Sonic CD",
        )
        shell.ingestLibrary(
            "romm",
            listOf(
                GridEntry(
                    id = "42",
                    title = "Sonic CD",
                    shortText = "",
                    platformId = "segacd",
                    availabilityLabel = null,
                    occupiesBothDisplays = false,
                    game = game,
                ),
            ),
            emptyList(),
            listedNames = mapOf("segacd" to "Sega CD"),
        )
        assertTrue("Sega CD" in titles(shell))
        assertFalse("segacd" in titles(shell))
    }

    private fun titles(shell: ShellController): List<String> =
        List(shell.model.count) { index -> shell.homeFace(index)?.title.orEmpty() }

    private fun shell(): ShellController =
        ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
}
