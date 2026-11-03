package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class HomeReturnFocusTest {
    private val keys = listOf("alpha", "beta", "gamma", "delta")
    private val ids = keys.map(::id)
    private val target = ids.last()
    private val collection = "home.user.1"
    private var now = 1_000L

    private fun id(key: String) = homeGameId("local-folder", key)

    private fun entry(key: String) = GridEntry(
        key, key.replaceFirstChar { it.uppercase() }, "", "nintendo-gba", null, false,
        Game("local-folder", key, "nintendo-gba", Availability.LocalOnly, label = key.replaceFirstChar { it.uppercase() }),
    )

    private fun store(sort: FolderSort = FolderSort.Custom, rootPin: Boolean = false): SessionStore =
        SessionStore(MemoryPrefs()).also { store ->
            val folder = HomeFolder(collection, "Collection", userMade = true, slots = ids, sort = sort)
            store.saveHomeBoard(encodeHome(HomeBoard(
                slots = listOf(HOME_ALL, collection) + if (rootPin) listOf(null, null, target) else emptyList(),
                folders = mapOf(collection to folder), addNewToHome = false,
            )))
        }

    private fun shell(store: SessionStore = store(), played: Map<String, Long> = emptyMap(), time: Map<String, Long> = emptyMap()) =
        ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()),
            lastPlayedMillis = { played[it] }, playMillis = { time[it] }, nowMillis = { now })

    private fun load(shell: ShellController, keys: List<String> = this.keys) =
        shell.ingestLibrary("local-folder", keys.map(::entry), emptyList())

    private fun openCollection(shell: ShellController) {
        assertTrue(shell.showOnHome(collection))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertFalse(shell.model.atLibraryRoot)
    }

    private fun focus(shell: ShellController, id: String) {
        val at = (0 until shell.model.count).first { shell.homeFace(it)?.id == id }
        shell.touchCell(at, HostScreen.Bottom)
        assertEquals(id, shell.homeFace(shell.model.focus.cellIndex)?.id)
    }

    private fun assertFocused(shell: ShellController, id: String = target) {
        assertEquals(id, shell.homeFace(shell.model.focus.cellIndex)?.id)
        assertNull(shell.model.focus.chrome)
        assertEquals(GridStrip.column(shell.model.focus.cellIndex), shell.model.focus.lastColumn)
    }

    @Test fun recentSortedFolderFindsTheLaunchedGameAfterHistoryReordersIt() {
        val played = ids.mapIndexed { i, id -> id to (400L - i * 100L) }.toMap().toMutableMap()
        val store = store(FolderSort.Recent)
        val shell = shell(store, played)
        load(shell)
        openCollection(shell)
        focus(shell, target)
        assertEquals(3, shell.model.focus.cellIndex)
        assertTrue(shell.onMeaning(Meaning.Activate, HostScreen.Bottom) is Effect.Launch)
        shell.noteGameLaunched(target)
        played[target] = 500L
        shell.notePlayChanged()
        shell.noteHomeReturned()
        assertEquals(0, shell.model.focus.cellIndex)
        assertFocused(shell)
        assertFalse(shell.model.atLibraryRoot)
        assertNull(store.pendingHomeGame())
    }

    @Test fun recentlyPlayedKeepsItsViewWhenTheLaunchedGameMovesToTheFront() {
        val played = ids.mapIndexed { i, id -> id to (400L - i * 100L) }.toMap().toMutableMap()
        val shell = shell(played = played)
        load(shell)
        assertTrue(shell.showOnHome(HOME_RECENT))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, target)
        shell.noteGameLaunched(target)
        played[target] = 500L
        shell.notePlayChanged()
        shell.noteHomeReturned()
        assertEquals(0, shell.model.focus.cellIndex)
        assertFocused(shell)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertFocused(shell, HOME_RECENT)
    }

    @Test fun allRecentAndMostPlayedKeepTheirChosenTab() {
        for (kind in listOf(LibraryTabKind.Recent, LibraryTabKind.MostPlayed)) {
            val played = ids.mapIndexed { i, id -> id to (400L - i * 100L) }.toMap().toMutableMap()
            val time = played.toMutableMap()
            val shell = shell(played = played, time = time)
            load(shell)
            shell.openHomeAll()
            shell.tapLibraryTab(shell.libraryBar()!!.tabs.indexOfFirst { it.kind == kind })
            focus(shell, target)
            assertEquals(3, shell.model.focus.cellIndex)
            shell.noteGameLaunched(target)
            played[target] = 500L
            time[target] = 500L
            shell.notePlayChanged()
            shell.noteHomeReturned()
            assertFocused(shell)
            assertEquals(0, shell.model.focus.cellIndex)
            assertEquals(kind, shell.libraryBar()!!.tab.kind)
        }
    }

    @Test fun mostPlayedFolderResolvesTheGameAfterTheSessionEnds() {
        val time = ids.mapIndexed { i, id -> id to (400L - i * 100L) }.toMap().toMutableMap()
        val shell = shell(store(FolderSort.MostPlayed), time = time)
        load(shell)
        openCollection(shell)
        focus(shell, target)
        shell.noteGameLaunched(target)
        time[target] = 500L
        shell.notePlayChanged()
        shell.noteHomeReturned()
        assertFocused(shell)
        assertEquals(0, shell.model.focus.cellIndex)
    }

    @Test fun findLaunchLandsOnTheGameInsteadOfTheGridUnderFind() {
        val shell = shell()
        load(shell)
        shell.onMeaning(Meaning.View, HostScreen.Bottom)
        for (char in "delta") shell.tapFind(KeyboardLayout.find(KeyCap.Glyph(char)))
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        assertTrue(shell.onMeaning(Meaning.Activate, HostScreen.Bottom) is Effect.Launch)
        shell.noteGameLaunched(target)
        shell.closeFindAfterLaunch()
        assertNull(shell.find)
        assertTrue(shell.model.atLibraryRoot)
        shell.noteHomeReturned()
        assertFocused(shell)
        assertFalse(shell.model.atLibraryRoot)
        assertEquals(0, shell.revealPulse)
    }

    @Test fun commandCenterBrowsingDoesNotReplaceTheGameToReturnTo() {
        val store = store()
        val shell = shell(store)
        load(shell)
        openCollection(shell)
        focus(shell, target)
        shell.noteGameLaunched(target)
        store.place(Panel.Top, ExternalApp(target))
        // CommandCenter.browse and its idle settle use these same session operations.
        shell.goHome()
        store.update { it.browse(Panel.Bottom) }
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertEquals(target, store.pendingHomeGame())
        store.update { it.stopBrowsing() }
        store.update { it.home(Panel.Top) }
        val session = store.session
        shell.noteHomeReturned()
        assertFocused(shell)
        assertEquals(session, store.session)
        val landed = shell.model.focus
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        val moved = shell.model.focus
        assertNotEquals(landed, moved)
        shell.noteHomeReturned()
        store.update { it.stopBrowsing() }
        shell.noteHomeReturned()
        assertEquals(moved, shell.model.focus)
    }

    @Test fun aRootLaunchReturnsToTheRootPin() {
        val shell = shell(store(rootPin = true))
        load(shell)
        focus(shell, target)
        shell.noteGameLaunched(target)
        shell.touchCell(0, HostScreen.Bottom)
        shell.noteHomeReturned()
        assertFocused(shell)
        assertTrue(shell.model.atLibraryRoot)
        assertEquals(4, shell.model.focus.cellIndex)
    }

    @Test fun homeRecallsFromBothWindowsKeepTheOpenFolderAndTheLanding() {
        val shell = shell()
        load(shell)
        openCollection(shell)
        focus(shell, target)
        shell.noteGameLaunched(target)
        shell.goHome(fromRecall = true)
        shell.noteHomeReturned()
        assertFocused(shell)
        val landed = shell.model.focus
        shell.goHome(fromRecall = true)
        shell.noteHomeReturned()
        assertEquals(landed, shell.model.focus)
        assertFalse(shell.model.atLibraryRoot)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.goHome(fromRecall = true)
        assertTrue(shell.model.atLibraryRoot)
    }

    @Test fun homeOnTheFreePanelLeavesTheCursorWhileTheGameIsStillOpen() {
        val store = store()
        val shell = shell(store)
        load(shell)
        val before = shell.model.focus
        shell.noteGameLaunched(target)
        store.place(Panel.Top, ExternalApp(target))
        // Home on the free screen, or another app leaving it: the game is still open on top.
        shell.goHome(fromRecall = true)
        shell.noteHomeReturned()
        shell.noteHomeReturned(OTHER_APP_ID)
        assertEquals(before, shell.model.focus)
        assertTrue(shell.model.atLibraryRoot)
        assertEquals(target, store.session.topApp?.id)
        assertEquals(target, store.pendingHomeGame())
        // The game leaving its own screen is the return.
        store.update { it.home(Panel.Top) }
        shell.noteHomeReturned(target)
        assertFocused(shell)
        assertNull(store.pendingHomeGame())
        shell.onMeaning(Meaning.MoveLeft, HostScreen.Bottom)
        val moved = shell.model.focus
        shell.noteHomeReturned()
        assertEquals(moved, shell.model.focus)
    }

    @Test fun homeStillGoesToRootAfterALanding() {
        val store = store()
        val shell = shell(store)
        load(shell)
        shell.noteGameLaunched(target)
        shell.noteHomeReturned(target)
        assertFocused(shell)
        assertFalse(shell.model.atLibraryRoot)
        // The same recall reaching the other screen at once leaves the landing alone.
        shell.goHome(fromRecall = true)
        assertFocused(shell)
        // A later Home press, with no key or touch between, goes to the top level as always.
        now += 5_000L
        shell.goHome(fromRecall = true)
        assertTrue(shell.model.atLibraryRoot)
    }

    @Test fun homeGoesToRootBeforeAnyGameReturned() {
        val shell = shell()
        load(shell)
        openCollection(shell)
        shell.goHome(fromRecall = true)
        assertTrue(shell.model.atLibraryRoot)
    }

    @Test fun theOlderOfTwoOpenGamesLandsWhenItLeaves() {
        val store = store()
        val shell = shell(store)
        load(shell)
        val older = ids.first()
        shell.noteGameLaunched(older)
        store.place(Panel.Top, ExternalApp(older))
        shell.noteGameLaunched(target)
        store.place(Panel.Bottom, ExternalApp(target))
        // The older game leaves the top while the newer one stays open below.
        store.update { it.home(Panel.Top) }
        shell.noteHomeReturned(older)
        assertFocused(shell, older)
    }

    @Test fun aStartThatFailedLeavesNothingToComeBackTo() {
        val store = store()
        val shell = shell(store)
        load(shell)
        val before = shell.model.focus
        shell.noteGameLaunched(target)
        shell.forgetGameLaunch()
        assertNull(store.pendingHomeGame())
        shell.noteHomeReturned()
        assertEquals(before, shell.model.focus)
    }

    @Test fun aGameStillOnTheOtherPanelIsRememberedIfHomeDiesAfterAFreePanelRecall() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        val shell = shell(store)
        load(shell)
        shell.noteGameLaunched(target)
        store.place(Panel.Top, ExternalApp(target))
        shell.noteHomeReturned()
        assertEquals(target, SessionStore(prefs).pendingHomeGame())
        val restored = shell(SessionStore(prefs))
        load(restored)
        assertFocused(restored)
    }

    @Test fun processDeathWaitsForTheRightLibraryAndLandsOnlyOnce() {
        val prefs = MemoryPrefs()
        val first = shell(SessionStore(prefs))
        load(first)
        first.noteGameLaunched(target)
        val store = SessionStore(prefs)
        val restored = shell(store)
        restored.setInstalledApps(emptyList(), emptySet())
        restored.ingestLibrary("other", emptyList(), emptyList())
        assertEquals(target, store.pendingHomeGame())
        assertTrue(restored.model.atLibraryRoot)
        load(restored)
        restored.showCuratedHome()
        assertFocused(restored)
        assertNull(store.pendingHomeGame())
        restored.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        val moved = restored.model.focus
        restored.noteHomeReturned()
        restored.noteShelfChanged()
        assertEquals(moved, restored.model.focus)
        assertNull(SessionStore(prefs).pendingHomeGame())
    }

    @Test fun aUserKeyCancelsColdRestorationBeforeTheLibraryArrives() {
        val store = store()
        shell(store).noteGameLaunched(target)
        val restored = shell(store)
        restored.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        val moved = restored.model.focus
        assertNull(store.pendingHomeGame())
        load(restored)
        assertEquals(moved, restored.model.focus)
        restored.noteHomeReturned()
        assertNotEquals(target, restored.homeFace(restored.model.focus.cellIndex)?.id)
    }

    @Test fun repeatsWhileAColdHomeLoadsDoNotMoveOrCancelItsPendingGame() {
        val store = store()
        shell(store).noteGameLaunched(target)
        val restored = shell(store)
        val before = restored.model.focus
        restored.onMeaning(Meaning.MoveRight, HostScreen.Bottom, repeatCount = 3)
        assertEquals(before, restored.model.focus)
        assertEquals(target, store.pendingHomeGame())
        load(restored)
        assertFocused(restored)
    }

    @Test fun aTouchCancelsColdRestorationBeforeTheLibraryArrives() {
        val store = store()
        shell(store).noteGameLaunched(target)
        val restored = shell(store)
        restored.touchCell(1, HostScreen.Bottom)
        assertNull(store.pendingHomeGame())
        load(restored)
        assertTrue(restored.model.atLibraryRoot)
        assertNotEquals(target, restored.homeFace(restored.model.focus.cellIndex)?.id)
    }

    @Test fun openingAndClosingFindCancelsAColdPendingGame() {
        val store = store()
        shell(store).noteGameLaunched(target)
        val restored = shell(store)
        restored.openFind()
        assertNotNull(restored.find)
        restored.closeFind()
        assertNull(store.pendingHomeGame())
        load(restored)
        assertTrue(restored.model.atLibraryRoot)
    }

    @Test fun aNewLaunchReplacesAColdPendingGame() {
        val store = store()
        shell(store).noteGameLaunched(target)
        val restored = shell(store)
        restored.noteGameLaunched(ids.first())
        load(restored)
        assertTrue(restored.model.atLibraryRoot)
        assertEquals(ids.first(), store.pendingHomeGame())
        restored.noteHomeReturned()
        assertFocused(restored, ids.first())
    }

    @Test fun aColdPendingGameExpiresRatherThanPullingFocusAfterASlowRead() {
        val store = store()
        shell(store).noteGameLaunched(target)
        val restored = shell(store)
        now += 30_001L
        load(restored)
        assertNull(store.pendingHomeGame())
        assertTrue(restored.model.atLibraryRoot)
    }

    @Test fun anOpenMenuDiscardsTheReturnInsteadOfLandingAfterItCloses() {
        val store = store()
        val shell = shell(store)
        load(shell)
        shell.noteGameLaunched(target)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val before = shell.model.focus
        val menu = shell.model.panel
        assertNotNull(menu)
        shell.noteHomeReturned()
        assertEquals(menu, shell.model.panel)
        assertEquals(before, shell.model.focus)
        assertNull(store.pendingHomeGame())
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.noteShelfChanged()
        shell.noteHomeReturned()
        assertTrue(shell.model.atLibraryRoot)
    }

    @Test fun settingsKeepsItsOwnFocusAndDiscardsTheReturn() {
        val store = store()
        val shell = shell(store)
        load(shell)
        shell.noteGameLaunched(target)
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        val settings = panelRows(shell.model.panel!!, shell.model).indexOf(Row.Settings)
        shell.touchPanel(settings, HostScreen.Top)
        shell.touchPanel(settings, HostScreen.Top)
        assertNotNull(shell.model.settings)
        val before = shell.model.settings
        shell.noteHomeReturned()
        assertEquals(before, shell.model.settings)
        assertNull(store.pendingHomeGame())
        shell.closeSettings()
        shell.noteHomeReturned()
        assertTrue(shell.model.atLibraryRoot)
    }

    @Test fun anOpenDialogKeepsItsOwnFocusAndDiscardsTheReturn() {
        val store = store()
        val shell = shell(store)
        load(shell)
        shell.noteGameLaunched(target)
        val dialog = DialogState(DialogKind.Ok, "Notice", "Body", listOf(DialogButton.Ok), 0, 0, HostScreen.Bottom)
        shell.showDialog(dialog)
        shell.noteHomeReturned()
        assertEquals(dialog, shell.model.dialog)
        assertNull(store.pendingHomeGame())
        shell.dismissDialog()
        shell.noteHomeReturned()
        assertTrue(shell.model.atLibraryRoot)
    }

    @Test fun dismissingADialogWhileTheLibraryLoadsCancelsThePendingGame() {
        val store = store()
        shell(store).noteGameLaunched(target)
        val restored = shell(store)
        val dialog = DialogState(DialogKind.Ok, "Notice", "Body", listOf(DialogButton.Ok), 0, 0, HostScreen.Bottom)
        restored.showDialog(dialog)
        restored.touchDialog(0, HostScreen.Bottom)
        assertNull(store.pendingHomeGame())
        load(restored)
        assertTrue(restored.model.atLibraryRoot)
    }

    @Test fun repeatsFromTheGameAreIgnoredAndTheFirstFreshPressUsesTheLandedTile() {
        val shell = shell()
        load(shell)
        shell.noteGameLaunched(target)
        shell.noteHomeReturned()
        assertFocused(shell)
        val landed = shell.model.focus
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom, repeatCount = 4)
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom, repeatCount = 5))
        assertEquals(landed, shell.model.focus)
        assertEquals(Effect.Launch(landed.cellIndex), shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertFocused(shell)
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertNotEquals(landed, shell.model.focus)
    }

    @Test fun pausedAndResumedGamesReturnToTheirTileWithoutChangingDisplayAssignment() {
        val store = store()
        val shell = shell(store)
        load(shell)
        shell.running = setOf(target)
        shell.resumable = true
        repeat(2) {
            shell.noteGameLaunched(target)
            store.place(Panel.Bottom, ExternalApp(target))
            shell.goHome()
            store.update { it.home(Panel.Bottom) }
            val session = store.session
            shell.noteHomeReturned()
            assertFocused(shell)
            assertEquals(TileActivity.Paused, shell.focusedActivity())
            assertEquals(session, store.session)
        }
    }

    @Test fun aResyncAfterLandingPreservesTheGameAcrossInsertedRows() {
        val store = store(FolderSort.Alphabetical)
        val shell = shell(store)
        load(shell)
        shell.openHomeAll()
        focus(shell, target)
        shell.noteGameLaunched(target)
        shell.noteHomeReturned()
        assertEquals(2, shell.model.focus.cellIndex)
        load(shell, keys + "aardvark")
        assertFocused(shell)
        assertEquals(3, shell.model.focus.cellIndex)
        assertEquals(LibraryTabKind.All, shell.libraryBar()!!.tab.kind)
    }

    @Test fun theLibraryGridResolvesItsTileIdAndKeepsFocusAcrossResync() {
        val shell = shell()
        load(shell)
        shell.showPlatforms("local-folder", emptyList())
        shell.showGames(keys.map(::entry))
        shell.touchCell(3, HostScreen.Bottom)
        shell.noteGameLaunched(target)
        shell.touchCell(0, HostScreen.Bottom)
        shell.noteHomeReturned()
        assertTrue(shell.model.libraryGrid)
        assertEquals("delta", shell.entryAt(shell.model.focus.cellIndex)?.id)
        shell.showGames((listOf("aardvark") + keys).map(::entry))
        assertEquals(4, shell.model.focus.cellIndex)
        assertEquals("delta", shell.entryAt(shell.model.focus.cellIndex)?.id)
    }

    @Test fun landingScrollsTheStripToTheGame() {
        val shell = shell(SessionStore(MemoryPrefs()))
        val many = (0..39).map { "game${it.toString().padStart(2, '0')}" }
        load(shell, many)
        shell.openHomeAll()
        val last = id(many.last())
        focus(shell, last)
        shell.noteGameLaunched(last)
        shell.touchCell(0, HostScreen.Bottom)
        assertEquals(0, shell.model.firstColumn)
        shell.noteHomeReturned()
        assertFocused(shell, last)
        assertEquals(39, shell.model.focus.cellIndex)
        assertTrue(shell.model.firstColumn > 0)
        assertTrue(GridStrip.column(39) in shell.model.firstColumn until shell.model.firstColumn + shell.model.columnsShown)
    }

    @Test fun aBackgroundCatalogRefreshKeepsTheVisibleViewport() {
        val shell = shell(SessionStore(MemoryPrefs()))
        val many = (0..39).map { "game${it.toString().padStart(2, '0')}" }
        load(shell, many)
        shell.openHomeAll()
        focus(shell, id(many.last()))
        shell.touchScroll(13)
        assertEquals(13, shell.model.firstColumn)
        shell.noteShelfChanged()
        assertEquals(13, shell.model.firstColumn)
        shell.notePlayChanged()
        assertEquals(13, shell.model.firstColumn)
        assertFocused(shell, id(many.last()))
    }

    @Test fun returningByIdUsesTheLeftmostScrollThatShowsTheGame() {
        val shell = shell(SessionStore(MemoryPrefs()))
        val many = (0..39).map { "game${it.toString().padStart(2, '0')}" }
        load(shell, many)
        shell.openHomeAll()
        val last = id(many.last())
        focus(shell, last)
        shell.noteGameLaunched(last)
        shell.touchScroll(13)
        assertEquals(13, shell.model.firstColumn)
        shell.noteHomeReturned()
        assertFocused(shell, last)
        assertEquals(10, shell.model.firstColumn)
    }

    @Test fun aGameFoldedWhileAwayReturnsToItsSharedTileAndSurvivesAColdHome() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        val shell = shell(store)
        val local = entry("delta").copy(fileName = "Delta.gba")
        shell.ingestLibrary("local-folder", listOf(local), emptyList())
        shell.noteGameLaunched(target)
        val mirror = local.copy(game = local.game!!.copy(backendId = "mirror"))
        shell.ingestLibrary("mirror", listOf(mirror), emptyList())
        val tile = shell.tileOf(target)
        assertTrue(tile.startsWith(SAME_PREFIX))
        shell.noteHomeReturned()
        assertFocused(shell, tile)
        shell.noteGameLaunched(tile)
        val restored = shell(SessionStore(prefs))
        restored.ingestLibrary("local-folder", listOf(local), emptyList())
        assertFocused(restored, tile)
        assertNull(SessionStore(prefs).pendingHomeGame())
    }

    @Test fun missingOrHiddenGamesLeaveTheClampedCursorAlone() {
        for (hidden in listOf(false, true)) {
            val store = store()
            if (hidden) store.saveHomeBoard(encodeHome(decodeHome(store.homeBoardRaw()).copy(hidden = setOf(target))))
            val shell = shell(store)
            load(shell)
            shell.noteGameLaunched(target)
            if (!hidden) load(shell, keys.dropLast(1))
            val before = shell.model.focus
            shell.noteHomeReturned()
            assertEquals(before, shell.model.focus)
            assertNull(store.pendingHomeGame())
        }
    }
}
