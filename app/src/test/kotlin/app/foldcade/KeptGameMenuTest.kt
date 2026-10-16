package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class KeptGameMenuTest {
    private val app = LaunchableApp("notes", "Notes", false)
    private val other = LaunchableApp("browser", "Browser", false)
    private val store = SessionStore(MemoryPrefs())
    private fun shell() = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { 100L })

    @Test fun aKeptGameMenuDropsSlotActionsAndNoOpsKeepItsUndo() {
        val collection = HomeFolder("home.user.1", "Tools", userMade = true, slots = listOf(androidHomeId("notes")))
        store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL, collection.id),
            folders = mapOf(collection.id to collection))))
        val shell = shell()
        shell.setInstalledApps(listOf(app, other), emptySet())
        focus(shell, 1)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, panelRows(shell.model.panel!!, shell.model).filterIsInstance<Row.Collection>().single { it.folderId == collection.id })
        assertNotNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        val rows = panelRows(shell.model.panel!!, shell.model)
        assertFalse(Row.MoveTile in rows)
        assertFalse(Row.RemoveTile in rows)
        assertEquals(androidHomeId("notes"), shell.model.tileActions!!.game)
        assertEquals(Row.AddToCollection, rows[shell.model.panel!!.index])
        val saved = store.homeBoardRaw()
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom)
        shell.pickUpHome(0)
        assertEquals(saved, store.homeBoardRaw())
        assertNotNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.applyHomeAddNew(shell.model.addNewToHome)
        assertNotNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Row.Undo, panelRows(shell.model.panel!!, shell.model).first())
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNotNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(androidHomeId("notes") in decodeHome(store.homeBoardRaw()).folders.getValue(collection.id).slots)
    }

    @Test fun keepingTheRemainingAppOnTopRemovesUndoWithoutActivatingAnotherRow() {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { 100L })
        shell.setInstalledApps(listOf(other, app), emptySet())
        shell.showHomeGrid(HomeGrid.Apps)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.RemoveTile)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Row.Undo, panelRows(shell.model.panel!!, shell.model).first())
        choose(shell, Row.PinApp)
        assertNull(shell.model.undoVerb)
        assertNotNull(shell.model.panel)
        val rows = panelRows(shell.model.panel!!, shell.model)
        assertFalse(Row.Undo in rows)
        assertEquals(Row.PinApp, rows[shell.model.panel!!.index])
        assertTrue(store.appShelfState().record("notes").favorite)
        assertTrue(store.appShelfState().record("browser").hidden)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertFalse(Row.Undo in panelRows(shell.model.panel!!, shell.model))
    }

    @Test fun aKeptAndroidMenuClassifiesItsNamedAppInEitherDirection() {
        for (wasGame in listOf(false, true)) {
            val store = SessionStore(MemoryPrefs())
            val shell = keptApp(store, wasGame)
            choose(shell, Row.MoveApp)
            val destination = if (wasGame) AndroidShelf.Apps else AndroidShelf.Games
            assertEquals(destination, store.appShelfState().record("notes").shelf)
            val board = decodeHome(store.homeBoardRaw())
            val folder = if (wasGame) HOME_ANDROID_APPS else HOME_ANDROID_GAMES
            assertTrue(androidHomeId("notes") in board.folders.getValue(folder).slots)
            assertFalse(androidHomeId("notes") in board.folders.getValue("home.user.1").slots)
            assertNull(store.appShelfState().record("browser").shelf)
        }
    }

    @Test fun keptAndroidActionsCarryTheirGameThroughFavoritesCollectionsAndArtResults() {
        for (row in listOf(Row.Favorite, Row.NewCollection, Row.ChooseArt)) {
            val store = SessionStore(MemoryPrefs())
            val applied = mutableListOf<String>()
            val shell = keptApp(store, true) { id, _, _ -> applied += id }
            if (row == Row.NewCollection) choose(shell, Row.AddToCollection)
            choose(shell, row)
            val gameId = androidHomeId("notes")
            when (row) {
                Row.Favorite -> assertTrue(gameId in decodeHome(store.homeBoardRaw()).folders.getValue(HOME_FAVORITES).slots)
                Row.NewCollection -> {
                    shell.typing()!!.setText("New tools")
                    shell.onMeaning(Meaning.Done, HostScreen.Bottom)
                    assertTrue(decodeHome(store.homeBoardRaw()).folders.values.any { it.name == "New tools" && gameId in it.slots })
                }
                Row.ChooseArt -> {
                    val opened = shell.artPicker!!
                    assertEquals(gameId, opened.gameId)
                    shell.artCandidates(opened.copy(loading = false,
                        candidates = listOf(ArtCandidate("one", "https://cdn/art", "https://cdn/thumb"))))
                    assertNotNull(shell.artPicker)
                    shell.artInput(Meaning.Activate)
                    assertEquals(listOf(gameId), applied)
                    val loaded = shell.artPicker!!
                    shell.goHome()
                    shell.artCandidates(loaded)
                    shell.artInput(Meaning.Activate)
                    assertNull(shell.artPicker)
                    assertEquals(listOf(gameId), applied)
                }
                else -> error("Unexpected test row")
            }
            assertFalse(androidHomeId("browser") in decodeHome(store.homeBoardRaw()).folders.getValue("home.user.1").slots)
        }
    }

    private fun keptApp(store: SessionStore, wasGame: Boolean,
        onArtApply: (String, ArtKind, String?) -> Unit = { _, _, _ -> }): ShellController {
        val collection = HomeFolder("home.user.1", "Tools", userMade = true, slots = listOf(androidHomeId("notes")))
        store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL, collection.id),
            folders = mapOf(collection.id to collection))))
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()),
            nowMillis = { 100L }, onArtApply = onArtApply)
        shell.setInstalledApps(listOf(app.copy(systemGame = wasGame), other), emptySet())
        shell.setArtKeySaved(true)
        focus(shell, 1)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, panelRows(shell.model.panel!!, shell.model).filterIsInstance<Row.Collection>().single { it.folderId == collection.id })
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertTrue(shell.model.tileActions!!.kept)
        assertNull(shell.focusedGame()?.androidPackage)
        return shell
    }

    private fun focus(shell: ShellController, target: Int) {
        val columns = GridStrip.column(target) - GridStrip.column(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(columns)) { shell.onMeaning(if (columns > 0) Meaning.MoveRight else Meaning.MoveLeft, HostScreen.Bottom) }
        val rows = GridStrip.row(target) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(rows)) { shell.onMeaning(if (rows > 0) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom) }
        assertEquals(target, shell.model.focus.cellIndex)
    }

    private fun choose(shell: ShellController, row: Row) {
        val rows = panelRows(shell.model.panel!!, shell.model)
        val target = rows.indexOf(row)
        assertTrue("Missing $row in $rows", target >= 0)
        repeat(kotlin.math.abs(target - shell.model.panel!!.index)) {
            shell.onMeaning(if (target > shell.model.panel!!.index) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom)
        }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }
}
