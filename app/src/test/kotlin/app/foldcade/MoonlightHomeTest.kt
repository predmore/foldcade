package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.HOME_MOONLIGHT
import app.foldcade.language.HomeBoard
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.Metrics
import app.foldcade.language.Row
import app.foldcade.language.decodeHome
import app.foldcade.language.encodeHome
import app.foldcade.language.onHome
import app.foldcade.language.panelRows
import app.foldcade.plugins.moonlight.moonlightApp
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every pinned Moonlight game is on Home, and Home waits for Android before dropping one. */
class MoonlightHomeTest {
    private val host = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
    private val hades = "$host|7"
    private val celeste = "$host|8"

    @After
    fun clearShelf() {
        Shelf.moonlightGames = emptyList()
    }

    @Test
    fun moonlightTilesWaitForTheFirstShortcutRead() {
        val store = SessionStore(MemoryPrefs())
        Shelf.moonlightGames = listOf(tile(hades, "Hades"), tile(celeste, "Celeste"))
        ShellController(store, plugins()).apply {
            settleMoonlight()
            noteShelfChanged()
        }
        val placed = board(store)
        assertTrue(hades in moonlightSlots(placed))
        store.saveHomeBoard(
            encodeHome(
                placed.copy(
                    hidden = placed.hidden + celeste,
                    folders = placed.folders.mapValues { (_, folder) ->
                        folder.copy(slots = folder.slots.map { if (it == celeste) null else it })
                    },
                ),
            ),
        )
        val slot = moonlightSlots(board(store)).indexOf(hades)

        // A cold start: the shelf is empty until Android answers.
        Shelf.moonlightGames = emptyList()
        val shell = ShellController(store, plugins())
        shell.noteShelfChanged()
        assertEquals(slot, moonlightSlots(board(store)).indexOf(hades))
        assertTrue(celeste in board(store).hidden)

        Shelf.moonlightGames = listOf(tile(hades, "Hades"), tile(celeste, "Celeste"))
        shell.settleMoonlight()
        shell.noteShelfChanged()
        assertEquals(slot, moonlightSlots(board(store)).indexOf(hades))
        assertFalse(onHome(board(store), celeste))

        // Android said both pins are gone.
        Shelf.moonlightGames = emptyList()
        shell.noteShelfChanged()
        assertFalse(hades in moonlightSlots(board(store)))
        assertFalse(celeste in board(store).hidden)
    }

    @Test
    fun anAcceptedPinGoesHomeEvenWithAddNewGamesOff() {
        val store = SessionStore(MemoryPrefs())
        store.saveHomeBoard(encodeHome(HomeBoard(addNewToHome = false)))
        Shelf.moonlightGames = listOf(tile(hades, "Hades"))
        val shell = ShellController(store, plugins())
        shell.settleMoonlight()
        shell.noteShelfChanged()
        assertFalse(onHome(board(store), hades))

        shell.placeMoonlightPin(hades)
        assertTrue(hades in moonlightSlots(board(store)))
    }

    @Test
    fun removingAMoonlightGameFromHomeUnpinsOnlyThatGame() {
        val removed = mutableListOf<String>()
        Shelf.moonlightGames = listOf(tile(hades, "Hades"))
        val shell = ShellController(
            SessionStore(MemoryPrefs()),
            plugins(),
            onMoonlightRemoved = { removed += it },
        )
        shell.settleMoonlight()
        shell.noteShelfChanged()
        focus(shell, titles(shell).indexOf("Moonlight"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)

        focus(shell, titles(shell).indexOf("Hades"))
        choose(shell, Row.RemoveTile)
        assertEquals(listOf(hades), removed)

        // The built-in Moonlight tile is not a pinned game.
        focus(shell, titles(shell).indexOf("Moonlight"))
        choose(shell, Row.RemoveTile)
        assertEquals(listOf(hades), removed)
    }

    @Test
    fun theLastReadIsKeptAndTheImportSheetsKeysAreGone() {
        val prefs = MemoryPrefs()
        prefs.edit()
            .putString("moonlight_source", "ImportedList")
            .putBoolean("moonlight_import_settled", true)
            .putStringSet("moonlight_placements", setOf(hades))
            .apply()
        val store = SessionStore(prefs)
        SessionStore.RETIRED_MOONLIGHT_KEYS.forEach { assertFalse(it, prefs.contains(it)) }

        store.setMoonlightPins(listOfNotNull(moonlightApp(host, "7", "Hades"), moonlightApp(host, "8", "Celeste")))
        assertEquals(listOf("Hades", "Celeste"), SessionStore(prefs).moonlightPins().map { it.label })
    }

    private fun tile(key: String, title: String) = ShelfGame(
        id = key,
        title = title,
        shortText = "Moonlight",
        platformId = "moonlight",
        mark = "moonlight",
        libraryId = "moonlight",
        remoteKey = key,
    )

    private fun board(store: SessionStore): HomeBoard = decodeHome(store.homeBoardRaw())

    private fun moonlightSlots(board: HomeBoard): List<String?> = board.folders[HOME_MOONLIGHT]?.slots.orEmpty()

    private fun plugins() = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())

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
        assertTrue("no such tile", index >= 0)
        val at = shell.model.focus.cellIndex
        val rows = index / Metrics.columns - at / Metrics.columns
        val columns = index % Metrics.columns - at % Metrics.columns
        repeat(maxOf(rows, 0)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        repeat(maxOf(-rows, 0)) { shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom) }
        repeat(maxOf(columns, 0)) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        repeat(maxOf(-columns, 0)) { shell.onMeaning(Meaning.MoveLeft, HostScreen.Bottom) }
        assertEquals(index, shell.model.focus.cellIndex)
    }

    private fun titles(shell: ShellController): List<String> =
        List(shell.model.count) { index -> shell.homeFace(index)?.title.orEmpty() }
}
