package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.Effect
import app.foldcade.language.FindFilterKind
import app.foldcade.language.GridStrip
import app.foldcade.language.HomeGrid
import app.foldcade.language.HomeKeys
import app.foldcade.language.HostScreen
import app.foldcade.language.KeyCap
import app.foldcade.language.KeyboardLayout
import app.foldcade.language.LaunchableApp
import app.foldcade.language.Meaning
import app.foldcade.language.PanelLevel
import app.foldcade.language.Row
import app.foldcade.language.panelRows
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Find over the grid: Select opens it, a few letters find a game, and Show on Home goes to it. */
class FindFlowTest {
    @Test
    fun selectOpensFindAndClosesItWithTheGridAsItWas() {
        val shell = shell()
        val before = shell.model.focus
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        assertNotNull(shell.find)
        assertEquals(HomeKeys.Finding, shell.homeKeys())
        assertTrue(shell.holdsCommandCenter())
        assertFalse(shell.atRest())
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        assertNull(shell.find)
        assertEquals(before, shell.model.focus)
        assertEquals(HomeKeys.Idle, shell.homeKeys())
    }

    @Test
    fun aFewLettersFindAGameAndAOpensIt() {
        val shell = shell()
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        type(shell, "puz")
        val find = shell.find!!
        assertEquals("puz", find.query.lowercase())
        assertEquals("Puzzle", find.results.first().title)
        // The top screen shows the best result while the keys have the D-pad.
        assertEquals("Puzzle", shell.focusedGame()?.title)
        // Start goes to the results, on the best one, where A, X and Y mean what they do on a tile.
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        assertTrue(find.onResults)
        assertEquals(HomeKeys.Idle, shell.homeKeys())
        val launch = shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom)
        assertEquals(Effect.Launch(shell.model.focus.cellIndex, onBottom = true), launch)
        assertEquals("Puzzle", shell.focusedGame()?.title)
        shell.closeFindAfterLaunch()
        assertNull(shell.find)
    }

    @Test
    fun upFromTheTopRowReachesTheResultsAndDownComesBack() {
        val shell = shell()
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        val find = shell.find!!
        // From G, up to the number row, then past it.
        repeat(3) { shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom) }
        assertTrue(find.onResults)
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertEquals(1, find.index)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        assertFalse(find.onResults)
        assertEquals(HomeKeys.Finding, shell.homeKeys())
    }

    @Test
    fun bClearsTheQueryThenCloses() {
        val shell = shell()
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        type(shell, "dr")
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertEquals("", shell.find?.query)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.find)
    }

    @Test
    fun theTriggersStepTheFiltersRoundTheEnds() {
        val shell = shell()
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        val find = shell.find!!
        val kinds = find.filters.map { it.kind }
        assertEquals(FindFilterKind.All, kinds.first())
        assertTrue(FindFilterKind.Apps in kinds && FindFilterKind.Platform in kinds)
        shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        assertEquals(FindFilterKind.Games, find.filters[find.filter].kind)
        assertTrue(find.results.none { it.title == "Notes" })
        shell.onMeaning(Meaning.LetterBackward, HostScreen.Bottom)
        shell.onMeaning(Meaning.LetterBackward, HostScreen.Bottom)
        assertEquals(find.filters.lastIndex, find.filter)
    }

    @Test
    fun yOnAResultOffersShowOnHomeFirstAndItOpensTheFolderOnTheGame() {
        val shell = shell()
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        type(shell, "drift")
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val panel = shell.model.panel!!
        assertEquals(PanelLevel.Actions, panel.level)
        val rows = panelRows(panel, shell.model)
        assertEquals(Row.ShowOnHome, rows.first())
        assertTrue(Row.MoveTile !in rows && Row.RemoveTile !in rows)
        // Y then Select: Show on Home.
        shell.onMeaning(Meaning.View, HostScreen.Top)
        assertNull(shell.find)
        assertNull(shell.model.panel)
        assertEquals("Drift", shell.focusedGame()?.title)
        assertEquals(1, shell.revealPulse)
        // Inside the 3DS folder; B goes back to the folder's tile on the root.
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertEquals("Nintendo 3DS", shell.focusedGame()?.title)
    }

    @Test
    fun aHiddenGameIsFoundAndPinningItBringsItBack() {
        val shell = shell()
        // Hide Puzzle from inside its folder.
        val folder = titles(shell).indexOf("Nintendo 3DS")
        focus(shell, folder)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Puzzle"))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Top)
        assertFalse("Puzzle" in titles(shell))
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        type(shell, "puzzle")
        val found = shell.find!!.results.first()
        assertEquals("Puzzle", found.title)
        assertTrue(found.hidden)
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val rows = panelRows(shell.model.panel!!, shell.model)
        assertTrue(Row.ShowOnHome !in rows)
        assertTrue(Row.PinTile in rows)
        val pin = rows.indexOf(Row.PinTile)
        repeat(pin) { shell.onMeaning(Meaning.MoveDown, HostScreen.Top) }
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        // Find stays open, now on a game that is back on Home.
        assertFalse(shell.find!!.results.first().hidden)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Row.ShowOnHome, panelRows(shell.model.panel!!, shell.model).first())
    }

    @Test
    fun anOpenMenuKeepsItsGameWhenTheResultsReorder() {
        // Review r1: unhiding a game moved it up the list, and the menu went on to the game now at its old place.
        val shell = shell()
        hide(shell, "Drift")
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        val find = shell.find!!
        val drift = find.results.indexOfFirst { it.title == "Drift" }
        assertEquals(find.results.lastIndex, drift)
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        repeat(drift) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val game = shell.model.tileActions?.game
        assertEquals("Drift", find.subject?.title)
        // Add to, then Favorites: that brings Drift back, which sorts it among the games on Home.
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        assertEquals(PanelLevel.Collections, shell.model.panel?.level)
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        assertFalse(find.results.first { it.title == "Drift" }.hidden)
        assertTrue(find.results.indexOfFirst { it.title == "Drift" } < drift)
        assertEquals(game, shell.model.tileActions?.game)
        assertTrue(shell.model.tileActions?.collections?.first()?.member == true)
        // The focus went with it, so the menu closing leaves the ring on Drift.
        shell.onMeaning(Meaning.Back, HostScreen.Top)
        shell.onMeaning(Meaning.Back, HostScreen.Top)
        assertEquals("Drift", find.subject?.title)
    }

    @Test
    fun anOpenMenuKeepsItsGameWhenItsFilterGoes() {
        val shell = shell()
        hide(shell, "Drift")
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        val find = shell.find!!
        while (find.filters[find.filter].kind != FindFilterKind.Hidden) shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val game = shell.model.tileActions?.game
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        // Back on Home, Drift is no longer hidden, so the Hidden filter has nothing left and goes,
        // and Find falls back to All. The menu still names Drift.
        assertEquals(FindFilterKind.All, find.filters[find.filter].kind)
        assertTrue(find.filters.none { it.kind == FindFilterKind.Hidden })
        assertEquals(game, shell.model.tileActions?.game)
        assertTrue(shell.model.tileActions?.collections?.first()?.member == true)
    }

    /** Hides [title] from inside its folder, with Y then X. */
    private fun hide(shell: ShellController, title: String) {
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf(title))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Top)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
    }

    @Test
    fun aOnAFolderResultOpensIt() {
        val shell = shell()
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        type(shell, "nintendo")
        val find = shell.find!!
        assertEquals("Nintendo 3DS", find.results.first().title)
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertNull(shell.find)
        assertTrue(titles(shell).containsAll(listOf("Drift", "Puzzle")))
    }

    @Test
    fun allOffersShowOnHomeForAGameOnHome() {
        val shell = shell()
        shell.openHomeAll()
        val drift = titles(shell).indexOf("Drift")
        focus(shell, drift)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Row.ShowOnHome, panelRows(shell.model.panel!!, shell.model).first())
        shell.onMeaning(Meaning.View, HostScreen.Top)
        assertFalse(shell.homeAllOpen())
        assertEquals("Drift", shell.focusedGame()?.title)
    }

    @Test
    fun findOpensOverAShelfToo() {
        val shell = shell()
        shell.showHomeGrid(HomeGrid.Apps)
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        assertEquals(HomeKeys.Finding, shell.homeKeys())
        type(shell, "puz")
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.View, HostScreen.Top)
        // Show on Home leaves the shelf for Home.
        assertEquals(HomeGrid.StandIns, shell.model.homeGrid)
        assertEquals("Puzzle", shell.focusedGame()?.title)
    }

    @Test
    fun aDrawerOpenedOverFindsMenuLeavesFind() {
        val shell = shell()
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        type(shell, "drift")
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        // B closes the menu and Find is still there, on its results.
        shell.onMeaning(Meaning.Back, HostScreen.Top)
        assertTrue(shell.find!!.onResults)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.RightPanel, HostScreen.Top)
        assertNull(shell.find)
    }

    @Test
    fun nothingElseOpensFindWhileATileIsLifted() {
        val shell = shell()
        shell.pickUpHome(1)
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        assertNull(shell.find)
    }

    /** Types [text] by tapping each key, as a finger on the keyboard would. */
    private fun type(shell: ShellController, text: String) {
        for (char in text) shell.tapFind(KeyboardLayout.find(KeyCap.Glyph(char.lowercaseChar())))
    }

    private fun focus(shell: ShellController, index: Int) {
        val at = shell.model.focus.cellIndex
        val columns = GridStrip.column(index) - GridStrip.column(at)
        repeat(maxOf(columns, 0)) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        repeat(maxOf(-columns, 0)) { shell.onMeaning(Meaning.MoveLeft, HostScreen.Bottom) }
        val rows = GridStrip.row(index) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(maxOf(rows, 0)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        repeat(maxOf(-rows, 0)) { shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom) }
        assertEquals(index, shell.model.focus.cellIndex)
    }

    private fun titles(shell: ShellController): List<String> =
        List(shell.model.count) { index -> shell.homeFace(index)?.title.orEmpty() }

    private fun entry(key: String, title: String, platformId: String): GridEntry = GridEntry(
        id = key,
        title = title,
        shortText = platformId,
        platformId = platformId,
        availabilityLabel = null,
        occupiesBothDisplays = false,
        game = Game(backendId = "local-folder", remoteKey = key, platformId = platformId, availability = Availability.LocalOnly, label = title),
    )

    private fun shell(): ShellController {
        val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(LaunchableApp("com.example.notes", "Notes", systemGame = false)), emptySet())
        shell.ingestLibrary("local-folder", listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-3ds")), emptyList())
        return shell
    }
}
