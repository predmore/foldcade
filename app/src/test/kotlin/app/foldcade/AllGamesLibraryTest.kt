package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import app.foldcade.language.FindFilterKind
import app.foldcade.language.GridStrip
import app.foldcade.language.HOME_ROOT
import app.foldcade.language.HomeKeys
import app.foldcade.language.HostScreen
import app.foldcade.language.KeyCap
import app.foldcade.language.KeyboardLayout
import app.foldcade.language.LaunchableApp
import app.foldcade.language.LibraryCopy
import app.foldcade.language.LibraryTabKind
import app.foldcade.language.Meaning
import app.foldcade.language.Row
import app.foldcade.language.decodeHome
import app.foldcade.language.panelRows
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** L1's Games: every game on every system, its tabs, Search, and adding a game to Home from it. */
class AllGamesLibraryTest {
    @Test
    fun lOneGamesOpensEveryGameOnEverySystemAndNoApps() {
        val (shell, _) = shell()
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.AllLibrary)
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
        assertEquals(listOf("Asphalt", "Mario Kart 7", "Mario Kart DS", "Mario Kart Super Circuit", "Zelda"), titles(shell))
        val bar = shell.libraryBar()!!
        assertEquals(LibraryTabKind.All, bar.tab.kind)
        // A system tab for each platform, and Android Games for the installed game.
        assertEquals(4, bar.tabs.count { it.kind == LibraryTabKind.System })
        // B goes back to Home, and the bar goes with All games.
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertEquals(HomeKeys.Idle, shell.homeKeys())
        assertNull(shell.libraryBar())
    }

    @Test
    fun theHomeTileIsAllGamesAndOpensTheSameLibrary() {
        val (shell, _) = shell()
        assertEquals(LibraryCopy.title, shell.homeFace(0)?.title)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
        assertEquals(5, shell.model.count)
    }

    @Test
    fun triggersAndTapsStepTheTabsAndAllGamesOpensOnAllAgain() {
        val (shell, _) = shell()
        shell.openHomeAll()
        focus(shell, 4)
        val tabs = shell.libraryBar()!!.tabs
        shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        assertEquals(tabs[1], shell.libraryBar()!!.tab)
        assertEquals(0, shell.model.focus.cellIndex)
        // Round the end, back to the last tab.
        shell.onMeaning(Meaning.LetterBackward, HostScreen.Bottom)
        shell.onMeaning(Meaning.LetterBackward, HostScreen.Bottom)
        assertEquals(tabs.last(), shell.libraryBar()!!.tab)
        // A tap on a system's tab lists only that system's games.
        val gba = tabs.indexOfFirst { it.label == "GBA" }
        shell.tapLibraryTab(gba)
        assertEquals(listOf("Mario Kart Super Circuit", "Zelda"), titles(shell))
        // The tab holds through a refresh of the library.
        shell.ingestLibrary("local-folder", entries + entry("fzero", "F-Zero", "gba"), emptyList())
        assertEquals(listOf("F-Zero", "Mario Kart Super Circuit", "Zelda"), titles(shell))
        // Opened again, All games starts on All.
        shell.goHome()
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.AllLibrary)
        assertEquals(LibraryTabKind.All, shell.libraryBar()!!.tab.kind)
    }

    @Test
    fun showPutsAHiddenGameBackFromNotOnHome() {
        val (shell, store) = shell()
        shell.openHomeAll()
        focus(shell, titles(shell).indexOf("Zelda"))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertFalse(Row.ShowHidden in panelRows(shell.model.panel!!, shell.model))
        choose(shell, Row.Hide)
        shell.tapLibraryTab(shell.libraryBar()!!.tabs.indexOfFirst { it.kind == LibraryTabKind.NotOnHome })
        focus(shell, titles(shell).indexOf("Zelda"))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.ShowHidden)
        val board = decodeHome(store.homeBoardRaw())
        assertTrue(board.hidden.none { it.endsWith("zelda") })
        // Back in its folder, not pinned.
        assertFalse(board.slots.any { it?.endsWith("zelda") == true })
    }

    @Test
    fun aFolderSwitchedOffInFoldersPutsItsGamesUnderNotOnHome() {
        val (shell, store) = shell()
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.EditHome)
        choose(shell, Row.Folders)
        assertTrue(shell.homeFoldersOpen())
        // Folders runs A to Z, so its triggers jump letters, as Hidden's do.
        assertTrue(shell.model.gridLabels.isNotEmpty())
        val gba = (0 until shell.model.count).first { shell.homeFace(it)?.title == "Game Boy Advance" }
        focus(shell, gba)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.FolderOnHome)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertFalse(decodeHome(store.homeBoardRaw()).slots.any { it?.contains("game-boy-advance") == true })
        shell.openHomeAll()
        // All games still lists them, its triggers step tabs, and Not on Home holds the folder's games.
        assertTrue(shell.model.gridLabels.isEmpty())
        assertTrue("Zelda" in titles(shell))
        shell.tapLibraryTab(shell.libraryBar()!!.tabs.indexOfFirst { it.kind == LibraryTabKind.NotOnHome })
        assertTrue(titles(shell).containsAll(listOf("Mario Kart Super Circuit", "Zelda")))
        assertFalse("Mario Kart DS" in titles(shell))
    }

    @Test
    fun aHeldTriggerStepsOneTab() {
        val (shell, _) = shell()
        shell.openHomeAll()
        shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        val first = shell.libraryBar()!!.tab
        repeat(5) { shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom, repeatCount = it + 1) }
        assertEquals(first, shell.libraryBar()!!.tab)
    }

    @Test
    fun aTabTapIsIgnoredUnderAMenu() {
        val (shell, _) = shell()
        shell.openHomeAll()
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertNotNull(shell.model.panel)
        shell.tapLibraryTab(2)
        assertEquals(LibraryTabKind.All, shell.libraryBar()!!.tab.kind)
        shell.tapLibrarySearch()
        assertNull(shell.find)
    }

    @Test
    fun searchStartsOnGamesAndFindsAGameOnEverySystem() {
        val (shell, _) = shell()
        shell.openHomeAll()
        shell.tapLibrarySearch()
        val find = shell.find!!
        assertEquals(FindFilterKind.Games, find.filters[find.filter].kind)
        for (char in "mario kart") shell.tapFind(KeyboardLayout.find(if (char == ' ') KeyCap.Space else KeyCap.Glyph(char)))
        assertEquals(setOf("Mario Kart 7", "Mario Kart DS", "Mario Kart Super Circuit"), find.results.map { it.title }.toSet())
        // B clears the query, then closes Find back onto All games as it was.
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.find)
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
    }

    @Test
    fun searchFromASystemsTabStartsOnThatSystem() {
        val (shell, _) = shell()
        shell.openHomeAll()
        shell.tapLibraryTab(shell.libraryBar()!!.tabs.indexOfFirst { it.label == "DS" })
        // Select, as the bar's Search pill says.
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        val find = shell.find!!
        assertEquals("nintendo-ds", find.filters[find.filter].platformId)
        assertEquals(listOf("Mario Kart DS"), find.results.map { it.title })
    }

    @Test
    fun addToHomePinsAGameAndBringsAHiddenOneBackFromNotOnHome() {
        val (shell, store) = shell()
        shell.openHomeAll()
        // Hide Mario Kart 7, from All games itself.
        focus(shell, titles(shell).indexOf("Mario Kart 7"))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Hide)
        val kart7 = decodeHome(store.homeBoardRaw()).hidden.single()
        assertTrue(kart7.endsWith("kart7"))
        // Still in All games, and now under Not on Home too.
        assertTrue("Mario Kart 7" in titles(shell))
        val notOnHome = shell.libraryBar()!!.tabs.indexOfFirst { it.kind == LibraryTabKind.NotOnHome }
        assertTrue(notOnHome > 0)
        shell.tapLibraryTab(notOnHome)
        assertTrue("Mario Kart 7" in titles(shell))
        focus(shell, titles(shell).indexOf("Mario Kart 7"))
        assertFalse(shell.homeFace(shell.model.focus.cellIndex)!!.onGrid)
        // Y › Add to › Home: shown again and pinned on the root.
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, Row.Collection(HOME_ROOT, Copy.home))
        val board = decodeHome(store.homeBoardRaw())
        assertFalse(kart7 in board.hidden)
        assertTrue(kart7 in board.slots)
        // The menu stays on its game though Not on Home, now empty, gave way to All.
        assertNotNull(shell.model.panel)
        assertEquals(LibraryTabKind.All, shell.libraryBar()!!.tab.kind)
        assertFalse(shell.libraryBar()!!.tabs.any { it.kind == LibraryTabKind.NotOnHome })
        assertEquals(Row.Collection(HOME_ROOT, Copy.home), panelRows(shell.model.panel!!, shell.model).first { it is Row.Collection })
        assertEquals("Mario Kart 7", shell.homeFaceOf(kart7).title)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.model.panel)
        assertTrue(shell.homeFace(titles(shell).indexOf("Mario Kart 7"))!!.onGrid)
        // Hiding another game brings Not on Home back as a tab, but All stays open.
        focus(shell, titles(shell).indexOf("Zelda"))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Hide)
        assertTrue(shell.libraryBar()!!.tabs.any { it.kind == LibraryTabKind.NotOnHome })
        assertEquals(LibraryTabKind.All, shell.libraryBar()!!.tab.kind)
    }

    private val entries = listOf(
        entry("kartsc", "Mario Kart Super Circuit", "gba"),
        entry("zelda", "Zelda", "gba"),
        entry("kartds", "Mario Kart DS", "nds"),
        entry("kart7", "Mario Kart 7", "3ds"),
    )

    private fun shell(): Pair<ShellController, SessionStore> {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(
            listOf(LaunchableApp("com.example.notes", "Notes", systemGame = false), LaunchableApp("com.example.asphalt", "Asphalt", systemGame = true)),
            emptySet(),
        )
        shell.ingestLibrary("local-folder", entries, emptyList())
        return shell to store
    }

    private fun entry(key: String, title: String, platformId: String): GridEntry = GridEntry(
        id = key,
        title = title,
        shortText = platformId,
        platformId = platformId,
        availabilityLabel = null,
        occupiesBothDisplays = false,
        game = Game(backendId = "local-folder", remoteKey = key, platformId = platformId, availability = Availability.LocalOnly, label = title),
    )

    private fun titles(shell: ShellController): List<String> =
        List(shell.model.count) { index -> shell.homeFace(index)?.title.orEmpty() }

    private fun choose(shell: ShellController, row: Row) {
        val at = panelRows(shell.model.panel!!, shell.model).indexOf(row)
        assertTrue("Missing $row", at >= 0)
        walkPanelTo(shell, at, HostScreen.Top)
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
    }

    private fun focus(shell: ShellController, index: Int) {
        val columns = GridStrip.column(index) - GridStrip.column(shell.model.focus.cellIndex)
        repeat(maxOf(columns, 0)) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        repeat(maxOf(-columns, 0)) { shell.onMeaning(Meaning.MoveLeft, HostScreen.Bottom) }
        val rows = GridStrip.row(index) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(maxOf(rows, 0)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        repeat(maxOf(-rows, 0)) { shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom) }
        assertEquals(index, shell.model.focus.cellIndex)
    }
}
