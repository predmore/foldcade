package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import app.foldcade.language.Effect
import app.foldcade.language.HomeGrid
import app.foldcade.language.HostScreen
import app.foldcade.language.LaunchableApp
import app.foldcade.language.GridStrip
import app.foldcade.language.Metrics
import app.foldcade.language.Meaning
import app.foldcade.language.Motion
import app.foldcade.language.SyncMark
import app.foldcade.language.TileActivity
import app.foldcade.language.FOLDER_ICONS
import app.foldcade.language.HomeKeys
import app.foldcade.language.MARK_FOLDER
import app.foldcade.language.Row
import app.foldcade.language.actionRows
import app.foldcade.language.panelRows
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
    fun freshBoardPinsAllAndMakesAFolderOnlyForGames() {
        val shell = shell()
        assertEquals(HomeGrid.StandIns, shell.model.homeGrid)
        assertFalse(shell.model.libraryGrid)
        assertTrue(shell.model.addNewToHome)
        // No games, no folder: not even for GameNative, Moonlight, DroidDeck, or Android.
        assertEquals(listOf(Copy.allLibrary), titles(shell))
        assertTrue(shell.homeFace(0)?.pinned == true)
        assertEquals("library", shell.homeFace(0)?.mark)

        // Each folder comes with its first game.
        shell.setInstalledApps(
            listOf(LaunchableApp("com.example.game", "Game", systemGame = true), LaunchableApp("com.example.notes", "Notes", systemGame = false)),
            emptySet(),
        )
        shell.ingestLibrary("local-folder", listOf(entry("puzzle", "Puzzle", "nintendo-3ds")), emptyList())
        assertEquals(listOf(Copy.allLibrary, "Android Games", "Android Apps", "Nintendo 3DS"), titles(shell))
        assertTrue((1..3).all { shell.homeFace(it)?.folder == true })
        assertEquals("android-games", shell.homeFace(1)?.mark)
        assertEquals("android-apps", shell.homeFace(2)?.mark)
        assertTrue(titles(shell).none { it == "GameNative" || it == "Moonlight" || it == "DroidDeck" || it == "Nintendo DS" })
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
    fun aDownloadThenASyncShowOnTheTileAndTheMarkFadesAway() {
        val shell = shell()
        shell.ingestLibrary(
            "romm",
            listOf(entry("drift", "Drift", "nintendo-3ds", backendId = "romm", availability = Availability.RemoteOnly)),
            emptyList(),
        )
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        val drift = titles(shell).indexOf("Drift")
        focus(shell, drift)

        assertNull(shell.noteActivity("romm", "drift", GameActivity.Downloading(0L, null), nowMillis = 0L))
        assertEquals(TileActivity.Downloading(null), shell.activityAt(drift))
        shell.noteActivity("romm", "drift", GameActivity.Downloading(50L, 200L), nowMillis = 0L)
        assertEquals(TileActivity.Downloading(0.25f), shell.focusedActivity())
        shell.noteActivity("romm", "drift", GameActivity.Checking, nowMillis = 0L)
        assertEquals(TileActivity.Checking, shell.activityAt(drift))
        shell.noteActivity("romm", "drift", GameActivity.SyncingSaves, nowMillis = 0L)
        assertEquals(TileActivity.Syncing, shell.activityAt(drift))

        val until = shell.noteActivity("romm", "drift", GameActivity.Synced(SyncResult(SyncOutcome.Uploaded)), 1_000L)
        assertEquals(1_000L + Motion.durationSyncMark, until)
        assertEquals(TileActivity.Synced(SyncMark.Synced, until!!), shell.activityAt(drift))
        // The launch ending does not take the mark early. Only its own timer does.
        shell.settleActivity("romm", "drift")
        shell.expireActivity("romm", "drift", until - 1)
        assertEquals(TileActivity.Synced(SyncMark.Synced, until), shell.activityAt(drift))
        shell.expireActivity("romm", "drift", until)
        assertNull(shell.activityAt(drift))
    }

    @Test
    fun aLaunchThatEndsMidWayLeavesTheTileQuiet() {
        val shell = shell()
        shell.noteActivity("romm", "drift", GameActivity.Downloading(10L, 100L), nowMillis = 0L)
        shell.settleActivity("romm", "drift")
        assertTrue(shell.activity.isEmpty())
        shell.noteActivity("romm", "drift", GameActivity.SyncingSaves, nowMillis = 0L)
        shell.noteActivity("romm", "drift", GameActivity.Idle, nowMillis = 0L)
        assertTrue(shell.activity.isEmpty())
    }

    @Test
    fun aSyncReadsAsWorkedOnlyWhenItRan() {
        assertEquals(SyncMark.Synced, syncMark(SyncResult(SyncOutcome.Downloaded)))
        assertEquals(SyncMark.Synced, syncMark(SyncResult(SyncOutcome.Unchanged)))
        assertEquals(SyncMark.KeptBoth, syncMark(SyncResult(SyncOutcome.KeptBoth, "Kept both copies")))
        assertEquals(SyncMark.Later, syncMark(SyncResult(SyncOutcome.Queued, "Saves will sync when RomM is reachable")))
        assertEquals(SyncMark.NotSynced, syncMark(SyncResult(SyncOutcome.Unchanged, "RomM server too old for save sync")))
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
    fun removingTheLastGameInAnOpenFolderLandsBackOnTheRoot() {
        val shell = shell()
        shell.ingestLibrary("local-folder", listOf(entry("drift", "Drift", "nintendo-ds")), emptyList())
        focus(shell, titles(shell).indexOf("Nintendo DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertFalse(shell.model.atLibraryRoot)
        assertEquals("Drift", shell.focusedGame()?.title)

        choose(shell, Row.RemoveTile)
        // No games, no folder: the folder closed under the focus, and Back has nothing left to close.
        assertTrue(titles(shell).none { it == "Nintendo DS" })
        assertTrue(shell.model.atLibraryRoot)
        assertEquals(listOf(Copy.allLibrary), titles(shell))
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
        assertEquals(2, shell.model.count)

        focus(shell, 1)
        assertEquals("Game Boy Advance", shell.focusedGame()?.title)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals("Cart", shell.focusedGame()?.title)
        choose(shell, Row.RemoveTile)
        // Its only game hidden, the folder goes and the grid is back at the root.
        assertTrue(shell.model.atLibraryRoot)
        assertTrue(titles(shell).none { it == "Game Boy Advance" })

        shell.openHomeAll()
        val cart = List(shell.model.count) { shell.homeFace(it) }.first { it?.title == "Cart" }
        assertTrue(cart != null)
        assertFalse(cart!!.onGrid)
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

    @Test
    fun yMakesANamedFolderThatWearsTheIconYouPick() {
        val shell = shell()
        // On the All tile there is no game to wrap, so the folder takes the first free slot.
        choose(shell, Row.NewFolder)
        val folder = List(shell.model.count) { shell.homeFace(it) }.indexOfFirst { it?.folder == true && it.mark == MARK_FOLDER }
        assertTrue(folder >= 0)
        // The new folder takes the focus and opens its name field.
        assertEquals(folder, shell.model.focus.cellIndex)
        assertEquals(HomeKeys.Renaming, shell.homeKeys())
        assertEquals("${Copy.newFolder} 1", shell.renameDraft())
        shell.editRename("Handhelds")
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertNull(shell.renameDraft())
        assertEquals("Handhelds", shell.homeFace(folder)?.title)
        // B on the name field keeps the name it had.
        choose(shell, Row.RenameFolder)
        shell.editRename("Nope")
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertEquals("Handhelds", shell.homeFace(folder)?.title)

        choose(shell, Row.FolderIcon)
        assertEquals(HomeKeys.PickingIcon, shell.homeKeys())
        assertEquals(0, shell.folderIconPick()?.first?.index)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        val chosen = FOLDER_ICONS[FolderIconPick.COLUMNS + 1]
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertNull(shell.folderIconPick())
        assertEquals(chosen, shell.homeFace(folder)?.mark)
        assertEquals(folder, shell.model.focus.cellIndex)
        // B leaves the icon as it was.
        choose(shell, Row.FolderIcon)
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        shell.onMeaning(Meaning.CancelHold, HostScreen.Bottom)
        assertEquals(chosen, shell.homeFace(folder)?.mark)
        // A system folder can be renamed but keeps its own icon.
        shell.ingestLibrary("local-folder", listOf(entry("drift", "Drift", "nintendo-ds")), emptyList())
        focus(shell, titles(shell).indexOf("Nintendo DS"))
        val rows = optionRows(shell)
        assertTrue(Row.RenameFolder in rows)
        assertFalse(Row.FolderIcon in rows)
    }

    @Test
    fun moveWalksATileAndAOrBEndsIt() {
        val shell = shell()
        shell.ingestLibrary(
            "local-folder",
            listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-ds")),
            emptyList(),
        )
        val before = titles(shell)
        focus(shell, 1)
        choose(shell, Row.MoveTile)
        assertEquals(HomeKeys.Moving, shell.homeKeys())
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        assertEquals(before[1], titles(shell)[2])
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(HomeKeys.Idle, shell.homeKeys())
        assertEquals(2, shell.model.focus.cellIndex)

        val placed = titles(shell)
        choose(shell, Row.MoveTile)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.CancelHold, HostScreen.Bottom)
        assertEquals(HomeKeys.Idle, shell.homeKeys())
        assertEquals(placed, titles(shell))
        assertEquals(2, shell.model.focus.cellIndex)
    }

    @Test
    fun theCursorWalksPastTheLastTileAndALiftedTileGoesAsFar() {
        val shell = shell()
        shell.ingestLibrary(
            "local-folder",
            listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-ds")),
            emptyList(),
        )
        val count = shell.model.count
        val lastColumn = GridStrip.columns(count) - 1
        repeat(lastColumn + GridStrip.trailing + 2) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        assertEquals(GridStrip.index(lastColumn + GridStrip.trailing, 0), shell.model.focus.cellIndex)
        // An empty slot shows no game and offers a move and a new folder, as an empty slot between tiles does.
        assertNull(shell.focusedGame())
        assertEquals(listOf(Row.MoveTile, Row.NewFolder), optionRows(shell))
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        assertEquals(GridStrip.index(lastColumn + GridStrip.trailing, 1), shell.model.focus.cellIndex)

        focus(shell, count - 1)
        val lifted = titles(shell)[count - 1]
        choose(shell, Row.MoveTile)
        repeat(GridStrip.trailing + 2) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        val at = shell.model.focus.cellIndex
        assertEquals(lastColumn + GridStrip.trailing, GridStrip.column(at))
        assertEquals(lifted, titles(shell)[at])

        // The slots it walked past are empty now. The top screen shows nothing for them.
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        val hole = (0 until shell.model.count).first { shell.homeFace(it)?.empty == true }
        focus(shell, hole)
        assertNull(shell.focusedHero())
        focus(shell, at)
        assertEquals(lifted, (shell.focusedHero() as? app.foldcade.language.HeroSubject.Item)?.item?.title)
    }

    @Test
    fun aLiftedGapBringsTheTileItLandsOnBackToWhereItWas() {
        val shell = shell()
        shell.ingestLibrary(
            "local-folder",
            listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-ds")),
            emptyList(),
        )
        val count = shell.model.count
        val before = titles(shell)
        // The gap just past the last tile, carried onto a tile, sends that tile to where the gap was.
        val (step, onto) = if (GridStrip.row(count) > 0) Meaning.MoveUp to count - 1 else Meaning.MoveLeft to count - Metrics.rows
        focus(shell, count)
        choose(shell, Row.MoveTile)
        assertEquals(HomeKeys.Moving, shell.homeKeys())
        shell.onMeaning(step, HostScreen.Bottom)
        assertEquals(onto, shell.model.focus.cellIndex)
        assertEquals(before[onto], titles(shell)[count])
        shell.onMeaning(Meaning.CancelHold, HostScreen.Bottom)
        assertEquals(before, titles(shell))

        focus(shell, count)
        choose(shell, Row.MoveTile)
        shell.onMeaning(step, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(HomeKeys.Idle, shell.homeKeys())
        assertEquals(before[onto], titles(shell)[count])
        assertEquals("", titles(shell)[onto])
    }

    @Test
    fun moveToTakesAGameOutOfItsFolderAndNewFolderWrapsIt() {
        val shell = shell()
        shell.ingestLibrary(
            "local-folder",
            listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-3ds")),
            emptyList(),
        )
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Puzzle"))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val rows = panelRows(shell.model.panel!!, shell.model)
        assertEquals(listOf(Row.MoveTile, Row.MoveTileTo, Row.NewFolder, Row.RemoveTile), rows)
        repeat(rows.indexOf(Row.MoveTileTo)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        // Home first, then every folder but the one it is in.
        val destinations = panelRows(shell.model.panel!!, shell.model)
        assertEquals(Row.Destination(null, Copy.homeSlot), destinations.first())
        assertFalse(destinations.any { it is Row.Destination && it.label == "Nintendo 3DS" })
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertFalse("Puzzle" in titles(shell))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertTrue("Puzzle" in titles(shell))

        // Inside a folder, New folder lands on the root with the game in it.
        assertEquals("Nintendo 3DS", shell.focusedGame()?.title)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Drift"))
        choose(shell, Row.NewFolder)
        assertEquals(HomeKeys.Renaming, shell.homeKeys())
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        val made = shell.model.focus.cellIndex
        assertEquals("${Copy.newFolder} 1", shell.homeFace(made)?.title)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf("Drift"), titles(shell).filter { it.isNotEmpty() })
    }

    @Test
    fun goHomeLeavesAllAndAnOpenFolderForHomesFolders() {
        // The command center's Open an app button shows the grid this way.
        val shell = shell()
        shell.ingestLibrary("local-folder", listOf(entry("puzzle", "Puzzle", "nintendo-3ds")), emptyList())
        val root = titles(shell)
        shell.openHomeAll()
        shell.goHome()
        assertEquals(root, titles(shell))
        assertTrue(shell.model.atLibraryRoot)

        focus(shell, root.indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals("Puzzle", shell.focusedGame()?.title)
        shell.goHome()
        assertEquals(root, titles(shell))
        assertEquals("Nintendo 3DS", shell.focusedGame()?.title)
    }

    @Test
    fun yInAllAddsAnItemThatIsNotOnHomeYet() {
        val shell = shell()
        shell.ingestLibrary("local-folder", listOf(entry("puzzle", "Puzzle", "nintendo-3ds")), emptyList())
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        choose(shell, Row.RemoveTile)
        shell.openHomeAll()
        val puzzle = titles(shell).indexOf("Puzzle")
        focus(shell, puzzle)
        assertEquals(listOf(Row.AddToHome), optionRows(shell))
        // It goes back where it came from, and the row says where.
        assertEquals("${Copy.addTo} Nintendo 3DS", shell.model.tileActions?.addToHome)
        choose(shell, Row.AddToHome)
        assertTrue(shell.homeFace(puzzle)?.onGrid == true)
        // Placed now, so Y has nothing to offer and stays shut.
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertNull(shell.model.panel)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertFalse("Puzzle" in titles(shell))
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue("Puzzle" in titles(shell))
    }

    @Test
    fun yDeletesADownloadedServerGameAndItsTileShowsTheCloudAgain() {
        val shell = shell()
        shell.ingestLibrary(
            "romm",
            listOf(entry("drift", "Drift", "nintendo-3ds", backendId = "romm", availability = Availability.Cached)),
            emptyList(),
        )
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Drift"))
        assertEquals(
            listOf(Row.MoveTile, Row.MoveTileTo, Row.NewFolder, Row.RemoveTile, Row.RemoveDownload),
            optionRows(shell),
        )
        // A copy still arriving is not offered.
        shell.noteActivity("romm", "drift", GameActivity.Downloading(1, 2), nowMillis = 0)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertFalse(Row.RemoveDownload in panelRows(shell.model.panel!!, shell.model))
        assertNull(shell.focusedDownload())
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.settleActivity("romm", "drift")

        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val rows = panelRows(shell.model.panel!!, shell.model)
        repeat(rows.indexOf(Row.RemoveDownload)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        assertEquals(Effect.RemoveDownload, shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertNull(shell.model.panel)
        assertEquals("drift", shell.focusedDownload()?.remoteKey)

        shell.noteOffDevice("romm", "drift")
        assertEquals(true, shell.homeFace(titles(shell).indexOf("Drift"))?.onServer)
        assertNull(shell.focusedDownload())
        assertFalse(Row.RemoveDownload in optionRows(shell))
    }

    @Test
    fun aDeleteUnderWayIsNotOfferedTwice() {
        val shell = shell()
        shell.ingestLibrary(
            "romm",
            listOf(entry("drift", "Drift", "nintendo-3ds", backendId = "romm", availability = Availability.Cached)),
            emptyList(),
        )
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Drift"))
        assertTrue(shell.beginRemove("romm", "drift"))
        assertFalse(shell.beginRemove("romm", "drift"))
        assertEquals(setOf(homeGameId("romm", "drift")), shell.removing)
        assertNull(shell.focusedDownload())
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertFalse(Row.RemoveDownload in panelRows(shell.model.panel!!, shell.model))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.endRemove("romm", "drift")
        assertEquals("drift", shell.focusedDownload()?.remoteKey)
    }

    @Test
    fun aScanThatReadTheDiskBeforeADeleteDoesNotBringTheCopyBack() {
        val shell = shell()
        val cached = listOf(entry("drift", "Drift", "nintendo-3ds", backendId = "romm", availability = Availability.Cached))
        shell.ingestLibrary("romm", cached, emptyList())
        val onServer = { shell.homeFace(titles(shell).indexOf("Drift"))?.onServer }
        val before = shell.deviceMark()
        shell.noteOffDevice("romm", "drift")
        // The scan read the disk before the delete and lands after it.
        shell.ingestLibrary("romm", cached, emptyList(), readAt = before)
        shell.openHomeAll()
        assertEquals(true, onServer())
        // A scan that started after the delete is the truth again.
        shell.ingestLibrary("romm", cached, emptyList(), readAt = shell.deviceMark())
        assertEquals(false, onServer())
    }

    @Test
    fun anotherServerHoldsDeleteDownloadUntilItListsItsGames() {
        val shell = shell()
        val cached = listOf(entry("42", "Drift", "nintendo-3ds", backendId = "romm#2", availability = Availability.Cached))
        shell.ingestLibrary("romm#2", cached, emptyList())
        shell.openHomeAll()
        focus(shell, titles(shell).indexOf("Drift"))
        assertEquals("42", shell.focusedDownload()?.remoteKey)
        val before = shell.deviceMark()
        // ROM 42 on the new server is another game, so the old tile's copy is not the one it would delete.
        shell.noteServerChanged("romm#2")
        assertNull(shell.focusedDownload())
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertNull(shell.model.panel)
        // Refreshes that started before the change, or while it was saving, list the old server.
        shell.ingestLibrary("romm#2", cached, emptyList(), readAt = before)
        shell.ingestLibrary("romm#2", cached, emptyList(), readAt = shell.deviceMark())
        assertNull(shell.focusedDownload())
        shell.settleServerChange("romm#2")
        val published = shell.deviceMark()
        shell.settleServerChange("romm#2")
        assertEquals(published, shell.deviceMark())
        shell.ingestLibrary("romm#2", cached, emptyList(), readAt = before)
        assertNull(shell.focusedDownload())
        shell.ingestLibrary("romm#2", cached, emptyList(), readAt = published)
        assertEquals("42", shell.focusedDownload()?.remoteKey)
    }

    @Test
    fun yInAllDeletesTheDownloadOfAGameAlreadyOnHome() {
        val shell = shell()
        shell.ingestLibrary(
            "romm",
            listOf(entry("drift", "Drift", "nintendo-3ds", backendId = "romm", availability = Availability.Cached)),
            emptyList(),
        )
        shell.openHomeAll()
        focus(shell, titles(shell).indexOf("Drift"))
        assertEquals(listOf(Row.RemoveDownload), optionRows(shell))
        shell.noteOffDevice("romm", "drift")
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertNull(shell.model.panel)
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

    /** Y's rows for the focused tile, with the menu left closed. */
    private fun optionRows(shell: ShellController): List<Row> =
        actionRows(shell.model.tileActions, shell.model.appActions)

    /** Y, down the menu to [row], then A. */
    private fun choose(shell: ShellController, row: Row) {
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val panel = shell.model.panel ?: error("Y opened no menu")
        val rows = panelRows(panel, shell.model)
        assertTrue("$row in $rows", row in rows)
        repeat(rows.indexOf(row)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    /** Moves focus from the focused cell to [index]. */
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

    private fun shell(): ShellController =
        ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
}
