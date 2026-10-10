package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import app.foldcade.language.HomeGrid
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.Metrics
import app.foldcade.language.homeGameId
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.plugins.melonds.MelonDsEntry
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
                "GameNative",
                "Moonlight",
                "Android Games",
                "Android Apps",
            ),
            titles(shell),
        )
        assertTrue(titles(shell).none { it == "Clamshell" || it == "Slim Dual" || it == "Handheld" })
        // No ROM, no platform folder.
        assertTrue(titles(shell).none { it == "Nintendo 3DS" || it == "Nintendo DS" })
        assertTrue(shell.homeFace(0)?.pinned == true)
        assertTrue(shell.homeFace(1)?.folder == true)
        assertEquals("library", shell.homeFace(0)?.mark)
        assertEquals("pc", shell.homeFace(1)?.mark)
        assertEquals("moonlight", shell.homeFace(2)?.mark)
        assertEquals("android-games", shell.homeFace(3)?.mark)
        assertEquals("android-apps", shell.homeFace(4)?.mark)
    }

    @Test
    fun aScanned3dsGameMakesItsFolderAndConfirmLaunchesIt() {
        val shell = shell()
        shell.ingestLibrary("local-folder", listOf(entry("puzzle", "Puzzle", "nintendo-3ds")), emptyList())
        val folder = titles(shell).indexOf("Nintendo 3DS")
        assertTrue(folder >= 0)
        focus(shell, folder)
        assertEquals("Nintendo 3DS", shell.focusedGame()?.title)
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertEquals("Puzzle", shell.focusedGame()?.title)
        assertTrue(shell.onMeaning(Meaning.Activate, HostScreen.Bottom) is app.foldcade.language.Effect.Launch)
    }

    @Test
    fun rommGamesShareTheFolderBoard() {
        val shell = shell()
        shell.ingestLibrary("local-folder", listOf(entry("puzzle", "Puzzle", "nintendo-3ds")), emptyList())
        shell.ingestLibrary(
            "romm",
            listOf(entry("drift", "Drift", "nintendo-3ds", backendId = "romm")),
            emptyList(),
        )
        assertFalse(shell.model.libraryGrid)
        assertEquals(HomeGrid.StandIns, shell.model.homeGrid)
        assertEquals(1, titles(shell).count { it == "Nintendo 3DS" })
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(titles(shell).containsAll(listOf("Drift", "Puzzle")))
    }

    @Test
    fun aServerGameShowsACloudUntilItIsOnTheDevice() {
        val shell = shell()
        shell.ingestLibrary("local-folder", listOf(entry("puzzle", "Puzzle", "nintendo-3ds")), emptyList())
        shell.ingestLibrary(
            "romm",
            listOf(entry("drift", "Drift", "nintendo-3ds", backendId = "romm", availability = Availability.RemoteOnly)),
            emptyList(),
        )
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        val onServer = { title: String -> shell.homeFace(titles(shell).indexOf(title))?.onServer }
        assertEquals(true, onServer("Drift"))
        assertEquals(false, onServer("Puzzle"))
        shell.noteOnDevice("romm", "drift")
        assertEquals(false, onServer("Drift"))
    }

    @Test
    fun aSecondPressWhileDownloadingIsIgnored() {
        val shell = shell()
        assertTrue(shell.beginDownload("romm", "drift"))
        assertFalse(shell.beginDownload("romm", "drift"))
        assertEquals(setOf(homeGameId("romm", "drift")), shell.downloading)
        shell.endDownload("romm", "drift")
        assertTrue(shell.downloading.isEmpty())
        assertTrue(shell.beginDownload("romm", "drift"))
    }

    @Test
    fun aBackgroundLibraryLeavesAnOpenPanelUp() {
        val shell = shell()
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        val panel = shell.model.panel
        assertTrue(panel != null)
        shell.ingestLibrary("romm", listOf(entry("drift", "Drift", "nintendo-ds", backendId = "romm")), emptyList())
        assertEquals(panel, shell.model.panel)
        assertTrue(titles(shell).contains("Nintendo DS"))
    }

    @Test
    fun droppingRommTakesOnlyItsGamesOff() {
        val shell = shell()
        shell.ingestLibrary("local-folder", listOf(entry("puzzle", "Puzzle", "nintendo-3ds")), emptyList())
        shell.ingestLibrary("romm", listOf(entry("drift", "Drift", "nintendo-ds", backendId = "romm")), emptyList())
        assertTrue(shell.hasLibraryGames())
        shell.dropLibrary("romm")
        assertFalse(titles(shell).contains("Nintendo DS"))
        assertTrue(titles(shell).contains("Nintendo 3DS"))
        assertTrue(shell.hasLibraryGames())
    }

    @Test
    fun aPlatformFolderLeavesWhenItsLastRomIsGone() {
        val shell = shell()
        shell.ingestLibrary(
            "local-folder",
            listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-ds")),
            emptyList(),
        )
        val before = titles(shell)
        assertTrue("Nintendo 3DS" in before && "Nintendo DS" in before)

        shell.ingestLibrary("local-folder", listOf(entry("drift", "Drift", "nintendo-ds")), emptyList())
        val after = titles(shell)
        assertFalse("Nintendo 3DS" in after)
        // The DS folder keeps its place.
        assertEquals(before.indexOf("Nintendo DS"), after.indexOf("Nintendo DS"))
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
        assertEquals(6, shell.model.count)

        focus(shell, 5)
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
    fun aHomeLibraryGameKeepsPlayUnderItsHomeIdAndItsPlayersScreens() {
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        plugins.register(LocalFolderEntry())
        plugins.register(MelonDsEntry())
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins)
        shell.ingestLibrary("local-folder", listOf(entry("tetris", "Tetris DS", "nintendo-ds")), emptyList())
        focus(shell, titles(shell).indexOf("Nintendo DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)

        val game = shell.focusedGame()
        assertEquals(homeGameId("local-folder", "tetris"), game?.id)
        // Off the library grid the hero reads play history under the id it launched with.
        assertEquals(game?.id, shell.playId(game!!.id))
        assertTrue(game.occupiesBothDisplays)
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

    private fun entry(
        key: String,
        title: String,
        platformId: String,
        backendId: String = "local-folder",
        availability: Availability = Availability.LocalOnly,
    ): GridEntry = GridEntry(
        id = key,
        title = title,
        shortText = platformId,
        platformId = platformId,
        availabilityLabel = null,
        occupiesBothDisplays = false,
        game = Game(
            backendId = backendId,
            remoteKey = key,
            platformId = platformId,
            availability = availability,
            label = title,
        ),
    )

    /** Moves focus from the first cell to [index] on the root grid. */
    private fun focus(shell: ShellController, index: Int) {
        repeat(index / Metrics.columns) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        repeat(index % Metrics.columns) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
    }

    private fun titles(shell: ShellController): List<String> =
        List(shell.model.count) { index -> shell.homeFace(index)?.title.orEmpty() }

    private fun shell(): ShellController =
        ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
}
