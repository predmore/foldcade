package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/** Y › Sort in a collection: the grid reorders behind the open menu, which stays on its game. */
class FolderSortHostTest {
    private val zeta = LaunchableApp("zeta", "Zeta", false)
    private val notes = LaunchableApp("notes", "Notes", false)
    private val browser = LaunchableApp("browser", "browser", false)
    private val z = androidHomeId("zeta")
    private val n = androidHomeId("notes")
    private val b = androidHomeId("browser")
    private val last = mapOf(z to 50L, b to 90L)
    private val played = mapOf(z to 7_000L, n to 9_000L)
    private val arranged = listOf(z, null, n, b)
    private val collection = HomeFolder("home.user.1", "Tools", userMade = true, slots = arranged)
    private val store = SessionStore(MemoryPrefs())

    private fun shell(): ShellController {
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { 100L },
            lastPlayedMillis = { last[it] }, playMillis = { played[it] })
        shell.setInstalledApps(listOf(zeta, notes, browser), emptySet())
        return shell
    }

    @Test fun sortingFromAGamesMenuReordersTheGridAndKeepsTheMenuOnThatGame() {
        store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL, collection.id), folders = mapOf(collection.id to collection))))
        val shell = shell()
        focus(shell, 1)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(arranged, shell.homeSlots())
        focus(shell, 2)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Row.SortFolder, rows(shell).last())
        assertTrue(Row.MoveTile in rows(shell))

        choose(shell, Row.SortFolder)
        assertEquals(listOf(b, z, n), shell.homeSlots())
        assertMenuOn(shell, n, Copy.sortRecent)
        assertFalse(Row.MoveTile in rows(shell))
        assertEquals(FolderSort.Recent, saved().folders.getValue(collection.id).sort)
        assertEquals(arranged, saved().folders.getValue(collection.id).slots)

        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertEquals(listOf(b, n, z), shell.homeSlots())
        assertMenuOn(shell, n, Copy.sortListed)

        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertEquals(listOf(n, z, b), shell.homeSlots())
        assertMenuOn(shell, n, Copy.sortMostPlayed)

        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertEquals(arranged, shell.homeSlots())
        assertMenuOn(shell, n, Copy.sortCustom)
        assertTrue(Row.MoveTile in rows(shell))
        assertEquals(arranged, saved().folders.getValue(collection.id).slots)

        // Left steps back, and Back leaves the focus on the game the menu was on.
        shell.onMeaning(Meaning.MoveLeft, HostScreen.Bottom)
        assertMenuOn(shell, n, Copy.sortMostPlayed)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.model.panel)
        assertEquals(0, shell.model.focus.cellIndex)
        assertEquals(n, shell.homeFace(shell.model.focus.cellIndex)?.id)
    }

    @Test fun aSortedFolderLiftsNothingAndKeepsItsSortAcrossARestart() {
        val sorted = collection.copy(sort = FolderSort.Alphabetical)
        store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL, sorted.id), folders = mapOf(sorted.id to sorted))))
        val shell = shell()
        focus(shell, 1)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf(b, n, z), shell.homeSlots())
        shell.pickUpHome(0)
        assertNull(shell.homeLiftedIndex())
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertFalse(Row.MoveTile in rows(shell))
        // Y then Y is Move's chord, which a sorted folder does not have.
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertNull(shell.homeLiftedIndex())
        assertEquals(FolderSort.Alphabetical, saved().folders.getValue(sorted.id).sort)
    }

    @Test fun aFolderTilesMenuOnTheRootSortsThatFolder() {
        val recentlyPlayed = HomeFolder(HOME_RECENT, Copy.recentlyPlayed, mark = MARK_RECENT)
        store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL, collection.id, HOME_RECENT),
            folders = mapOf(collection.id to collection, HOME_RECENT to recentlyPlayed))))
        val shell = shell()
        focus(shell, 1)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Row.SortFolder, rows(shell).last())
        choose(shell, Row.SortFolder)
        assertEquals(1, shell.model.focus.cellIndex)
        assertEquals(collection.id, shell.homeFace(1)?.id)
        assertEquals(FolderSort.Recent, saved().folders.getValue(collection.id).sort)
        assertEquals(Row.SortFolder, rows(shell)[shell.model.panel!!.index])
        // Recently played orders itself: its tile has no Sort.
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        val recent = shell.homeSlots().indexOf(HOME_RECENT)
        assertTrue(recent >= 0)
        focus(shell, recent)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertFalse(Row.SortFolder in rows(shell))
    }

    private fun saved(): HomeBoard = decodeHome(store.homeBoardRaw())

    private fun rows(shell: ShellController): List<Row> = panelRows(shell.model.panel!!, shell.model)

    private fun assertMenuOn(shell: ShellController, game: String, sort: String) {
        val panel = shell.model.panel!!
        assertEquals(Row.SortFolder, rows(shell)[panel.index])
        assertEquals(sort, rowText(Row.SortFolder, shell.model).value)
        assertEquals(game, shell.model.tileActions?.game)
        assertFalse(shell.model.tileActions!!.kept)
        assertEquals(game, shell.homeFace(shell.model.focus.cellIndex)?.id)
        assertEquals(shell.model.focus.cellIndex, panel.grid.cellIndex)
    }

    private fun focus(shell: ShellController, target: Int) {
        val columns = GridStrip.column(target) - GridStrip.column(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(columns)) { shell.onMeaning(if (columns > 0) Meaning.MoveRight else Meaning.MoveLeft, HostScreen.Bottom) }
        val rows = GridStrip.row(target) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(rows)) { shell.onMeaning(if (rows > 0) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom) }
        assertEquals(target, shell.model.focus.cellIndex)
    }

    private fun choose(shell: ShellController, row: Row) {
        val rows = rows(shell)
        val target = rows.indexOf(row)
        assertTrue("Missing $row in $rows", target >= 0)
        repeat(kotlin.math.abs(target - shell.model.panel!!.index)) {
            shell.onMeaning(if (target > shell.model.panel!!.index) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom)
        }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }
}
