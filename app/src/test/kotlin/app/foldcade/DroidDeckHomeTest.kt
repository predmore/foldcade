package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import app.foldcade.language.HOME_ANDROID_APPS
import app.foldcade.language.HOME_ANDROID_GAMES
import app.foldcade.language.HOME_DROIDDECK
import app.foldcade.language.HOME_EMULATORS
import app.foldcade.language.HomeBoard
import app.foldcade.language.HostScreen
import app.foldcade.language.LaunchableApp
import app.foldcade.language.androidHomeId
import app.foldcade.language.Meaning
import app.foldcade.language.Metrics
import app.foldcade.language.Row
import app.foldcade.language.decodeHome
import app.foldcade.language.encodeHome
import app.foldcade.language.onHome
import app.foldcade.language.panelRows
import app.foldcade.localfolder.SteamShortcut
import app.foldcade.plugins.droiddeck.DroidDeckGame
import app.foldcade.plugins.gamenative.CatalogGame
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DroidDeck games sit in their own Home folder, and Home waits for both sources before dropping one. */
class DroidDeckHomeTest {
    private val hades = Shelf.DROIDDECK_PREFIX + "1145350"
    private val celeste = Shelf.DROIDDECK_PREFIX + "18446744069448138752"

    @After
    fun clearShelf() {
        Shelf.droidDeckGames = emptyList()
        Shelf.droidDeckInstalled = false
    }

    @Test
    fun droidDeckTilesWaitUntilSettled() {
        val store = SessionStore(MemoryPrefs())
        Shelf.droidDeckGames = listOf(tile(hades, "Hades II"), tile(celeste, "Celeste"))
        ShellController(store, plugins()).apply {
            settleDroidDeck()
            noteShelfChanged()
        }
        assertTrue(hades in droidDeckSlots(board(store)))
        assertTrue(celeste in droidDeckSlots(board(store)))
        val slot = droidDeckSlots(board(store)).indexOf(hades)

        // A cold start: nothing has answered yet, so the tiles stay where they were.
        Shelf.droidDeckGames = emptyList()
        val shell = ShellController(store, plugins())
        shell.noteShelfChanged()
        assertEquals(slot, droidDeckSlots(board(store)).indexOf(hades))

        // Both sources answered, and Celeste is gone.
        Shelf.droidDeckGames = listOf(tile(hades, "Hades II"))
        shell.settleDroidDeck()
        shell.noteShelfChanged()
        assertEquals(slot, droidDeckSlots(board(store)).indexOf(hades))
        assertFalse(celeste in droidDeckSlots(board(store)))
    }

    @Test
    fun anAcceptedPinGoesHomeEvenWithAddNewGamesOff() {
        val store = SessionStore(MemoryPrefs())
        store.saveHomeBoard(encodeHome(HomeBoard(addNewToHome = false)))
        Shelf.droidDeckGames = listOf(tile(hades, "Hades II"))
        val shell = ShellController(store, plugins())
        shell.settleDroidDeck()
        shell.noteShelfChanged()
        assertFalse(onHome(board(store), hades))

        shell.placeDroidDeckPin(hades)
        assertTrue(hades in droidDeckSlots(board(store)))
    }

    @Test
    fun removingADroidDeckGameFromHomeUnpinsOnlyThatGame() {
        val removed = mutableListOf<String>()
        Shelf.droidDeckGames = listOf(tile(hades, "Hades II"))
        val shell = ShellController(
            SessionStore(MemoryPrefs()),
            plugins(),
            onDroidDeckRemoved = { removed += it },
        )
        shell.settleDroidDeck()
        shell.noteShelfChanged()
        focus(shell, titles(shell).indexOf("DroidDeck"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)

        focus(shell, titles(shell).indexOf("Hades II"))
        choose(shell, Row.RemoveTile)
        assertEquals(listOf("1145350"), removed)
    }

    @Test
    fun droidDeckItselfIsInEmulatorsAndItsFolderHoldsOnlyGames() {
        val store = SessionStore(MemoryPrefs())
        Shelf.droidDeckGames = listOf(tile(hades, "Hades II"))
        val shell = ShellController(store, plugins())
        shell.settleDroidDeck()
        shell.setInstalledApps(
            listOf(
                LaunchableApp("com.droiddeck.launcher", "DroidDeck", systemGame = true),
                LaunchableApp("com.example.notes", "Notes", systemGame = false),
            ),
            setOf("com.droiddeck.launcher"),
        )
        val placed = board(store)
        assertEquals(listOf(hades), droidDeckSlots(placed))
        assertEquals(listOf(androidHomeId("com.droiddeck.launcher")), placed.folders[HOME_EMULATORS]?.slots)
        assertFalse(placed.folders.containsKey(HOME_ANDROID_GAMES))
        assertEquals(listOf(androidHomeId("com.example.notes")), placed.folders[HOME_ANDROID_APPS]?.slots)
    }

    @Test
    fun theDroidDeckTileSaysHowToAddAGameOnlyWhenItHasNone() {
        val tile = { Shelf.games.single { it.id == Shelf.DROIDDECK_TILE } }
        assertEquals(Copy.steamInDroidDeck, tile().shortText)
        Shelf.droidDeckInstalled = true
        assertEquals(Copy.addToHomeInDroidDeck, tile().shortText)
        assertFalse(tile().emptyShelfHint)
        Shelf.droidDeckGames = listOf(tile(hades, "Hades II"))
        assertEquals(Copy.steamInDroidDeck, tile().shortText)
    }

    @Test
    fun steamArtIsLookedUpOnlyForSteamIds() {
        Shelf.droidDeckGames = listOf(tile(hades, "Hades II"), tile(celeste, "Celeste"))
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins())
        assertEquals(1145350, shell.artQuery(Shelf.droidDeckGames[0])?.steamAppId)
        assertEquals(null, shell.artQuery(Shelf.droidDeckGames[1])?.steamAppId)
        assertEquals("Celeste", shell.artQuery(Shelf.droidDeckGames[1])?.title)
    }

    @Test
    fun theLastReadIsKept() {
        val prefs = MemoryPrefs()
        SessionStore(prefs).setDroidDeckPins(listOf(DroidDeckGame("1145350", "Hades II"), DroidDeckGame("620", "Portal\u001f2")))
        assertEquals(
            listOf(DroidDeckGame("1145350", "Hades II"), DroidDeckGame("620", "Portal 2")),
            SessionStore(prefs).droidDeckPins(),
        )
    }

    @Test
    fun steamFilesGoWhereTheyCanBePlayed() {
        val synced = SteamShortcut("content://deck/hades", "Hades II", "1145350", fromDroidDeck = true)
        val added = SteamShortcut("content://gn/celeste", "Celeste", "18446744069448138752")
        val plain = SteamShortcut("content://gn/portal", "Portal 2", "620")

        val both = routeSteamFiles(listOf(synced, added, plain), gameNativeInstalled = true, droidDeckInstalled = true)
        assertEquals(listOf(CatalogGame("Portal 2", 620, "STEAM")), both.gameNative)
        assertEquals(listOf(DroidDeckGame("1145350", "Hades II"), DroidDeckGame("18446744069448138752", "Celeste")), both.droidDeck)

        val deckOnly = routeSteamFiles(listOf(plain), gameNativeInstalled = false, droidDeckInstalled = true)
        assertEquals(emptyList<CatalogGame>(), deckOnly.gameNative)
        assertEquals(listOf(DroidDeckGame("620", "Portal 2")), deckOnly.droidDeck)

        // With neither installed, a plain file stays where it always went.
        val neither = routeSteamFiles(listOf(plain), gameNativeInstalled = false, droidDeckInstalled = false)
        assertEquals(listOf(CatalogGame("Portal 2", 620, "STEAM")), neither.gameNative)
    }

    private fun tile(id: String, title: String) = ShelfGame(
        id = id,
        title = title,
        shortText = Copy.progressInDroidDeck,
        platformId = "droiddeck",
        mark = "droiddeck",
        libraryId = "droiddeck",
        remoteKey = id.removePrefix(Shelf.DROIDDECK_PREFIX),
    )

    private fun board(store: SessionStore): HomeBoard = decodeHome(store.homeBoardRaw())

    private fun droidDeckSlots(board: HomeBoard): List<String?> = board.folders[HOME_DROIDDECK]?.slots.orEmpty()

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
