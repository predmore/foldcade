package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/** Up from the top row lights L1's or R1's pill. A there opens that drawer, and Up stays put while the pills are out of reach. */
class PillFocusHostTest {
    @Test fun aOnTheLitPillOpensItsDrawerInsteadOfLaunchingTheTileBelow() {
        val shell = shell()
        shell.openHomeAll()
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
        assertTrue(shell.focusedGame() != null)
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertEquals(Chrome.LeftIsland, shell.model.focus.chrome)

        val effect = shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertFalse("A on the pill launched $effect", effect is Effect.Launch)
        assertEquals(Side.Left, shell.model.panel?.side)
        // Closing the drawer finds All as it was.
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.model.panel)
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
    }

    @Test fun xOnTheLitPillOnHomeOpensTheDrawerAndLeavesTheFolderShut() {
        val shell = shell()
        val folder = titles(shell).indexOf("Nintendo 3DS")
        focus(shell, folder)
        // Over to the right half, so Up lands on R1.
        repeat(Metrics.columns) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        repeat(GridStrip.row(shell.model.focus.cellIndex)) { shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom) }
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertNotNull(shell.model.focus.chrome)

        val effect = shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom)
        assertFalse(effect is Effect.Launch)
        assertNotNull(shell.model.panel)
        assertEquals(HomeKeys.Idle, shell.homeKeys())
        assertTrue(titles(shell).contains("Nintendo 3DS"))
    }

    @Test fun besideAnAppOnTopUpFromTheTopRowStaysOnTheGrid() {
        val store = SessionStore(MemoryPrefs())
        val shell = shell(store)
        store.place(Panel.Top, ExternalApp("game"))
        shell.openHomeAll()
        val before = shell.model.focus.cellIndex
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertNull(shell.model.focus.chrome)
        assertEquals(before, shell.model.focus.cellIndex)
        assertTrue(shell.onMeaning(Meaning.Activate, HostScreen.Bottom) is Effect.Launch)
    }

    @Test fun aPillLitBeforeAnAppCoveredItGivesTheCursorBack() {
        val store = SessionStore(MemoryPrefs())
        val shell = shell(store)
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertNotNull(shell.model.focus.chrome)
        store.place(Panel.Top, ExternalApp("game"))
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertNull(shell.model.focus.chrome)
        assertEquals(GridStrip.index(1, 0), shell.model.focus.cellIndex)
    }

    @Test fun anEmptyGridBesideAnAppHasNoPillToRestOn() {
        val store = SessionStore(MemoryPrefs())
        val shell = shell(store)
        store.place(Panel.Top, ExternalApp("game"))
        shell.showGames(emptyList())
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertNull(shell.model.focus.chrome)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertNull(shell.model.panel)
        // With the top screen free again, R1 is what an empty grid rests on.
        store.update { it.home(Panel.Top) }
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertEquals(Chrome.StatusCluster, shell.model.focus.chrome)
    }

    @Test fun aSelectionKeepsThePillsOutOfReach() {
        val shell = shell()
        shell.openHomeAll()
        choose(shell, Row.SelectTile)
        assertEquals(HomeKeys.Selecting, shell.homeKeys())
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertNull(shell.model.focus.chrome)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertEquals(Chrome.LeftIsland, shell.model.focus.chrome)
    }

    private fun shell(store: SessionStore = SessionStore(MemoryPrefs())): ShellController {
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.ingestLibrary(
            "local-folder",
            listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-ds")),
            emptyList(),
        )
        return shell
    }

    private fun entry(key: String, title: String, platformId: String): GridEntry = GridEntry(
        id = key,
        title = title,
        shortText = platformId,
        platformId = platformId,
        availabilityLabel = null,
        occupiesBothDisplays = false,
        game = Game(
            backendId = "local-folder",
            remoteKey = key,
            platformId = platformId,
            availability = Availability.LocalOnly,
            label = title,
        ),
    )

    /** Y, down the menu to [row], then A. */
    private fun choose(shell: ShellController, row: Row) {
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val panel = shell.model.panel ?: error("Y opened no menu")
        val rows = panelRows(panel, shell.model)
        assertTrue("$row in $rows", row in rows)
        repeat(rows.indexOf(row)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
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
}
