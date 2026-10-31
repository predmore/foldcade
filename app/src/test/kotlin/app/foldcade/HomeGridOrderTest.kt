package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import app.foldcade.language.LibraryCopy
import app.foldcade.language.activityLine
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
import app.foldcade.language.HeroSubject
import app.foldcade.language.HomeKeys
import app.foldcade.language.KeyCap
import app.foldcade.language.KeyboardCopy
import app.foldcade.language.KeyboardLayout
import app.foldcade.language.CollectionLook
import app.foldcade.language.CollectionTint
import app.foldcade.language.MARK_LIBRARY
import app.foldcade.language.HOME_ALL
import app.foldcade.language.HOME_ROOT
import app.foldcade.language.HomeBoard
import app.foldcade.language.HomeFolder
import app.foldcade.language.androidHomeId
import app.foldcade.language.encodeHome
import app.foldcade.language.PanelLevel
import app.foldcade.language.editHomeRows
import app.foldcade.language.leftRows
import app.foldcade.language.RemoveVerb
import app.foldcade.language.Row
import app.foldcade.language.actionRows
import app.foldcade.language.folderCountLine
import app.foldcade.language.panelRows
import app.foldcade.language.rowText
import app.foldcade.language.homeGameId
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.plugins.melonds.MelonDsEntry
import app.foldcade.plugins.melonds.MelonDsPlayer
import app.foldcade.plugins.retroarch.RetroArchEntry
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        // No games, no folder: not even for GameNative, Moonlight, DroidDeck, or Android. No Favorites either.
        assertEquals(listOf(LibraryCopy.title), titles(shell))
        assertTrue(shell.homeFace(0)?.allTile == true)
        assertEquals("library", shell.homeFace(0)?.mark)

        // Each folder comes with its first game.
        shell.setInstalledApps(
            listOf(LaunchableApp("com.example.game", "Game", systemGame = true), LaunchableApp("com.example.notes", "Notes", systemGame = false)),
            emptySet(),
        )
        shell.ingestLibrary("local-folder", listOf(entry("puzzle", "Puzzle", "nintendo-3ds")), emptyList())
        assertEquals(listOf(LibraryCopy.title, "Android Games", "Android Apps", "Nintendo 3DS"), titles(shell))
        assertTrue((1..3).all { shell.homeFace(it)?.folder == true })
        // All counts its games as every folder does; Notes is an app, so it is not one of them.
        assertEquals("2 games", shell.homeFace(0)?.shortText)
        assertEquals("1 app", shell.homeFace(2)?.shortText)
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
        // Firmware for a game already here reads as firmware, not as the game downloading again.
        shell.noteActivity("romm", "drift", GameActivity.GettingFirmware(100L, 400L), nowMillis = 0L)
        assertEquals(TileActivity.Downloading(0.25f, firmware = true), shell.focusedActivity())
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
    fun aFirmwareDownloadKeepsTheFractionAndChangesTheWords() {
        val shell = shell()
        val id = homeGameId("romm", "drift")
        shell.noteActivity("romm", "drift", GameActivity.Downloading(50L, 100L), nowMillis = 0L, firmware = true)
        val firmware = shell.activity.getValue(id)
        assertEquals(TileActivity.Downloading(0.5f, firmware = true), firmware)
        assertEquals("Getting firmware 50%", activityLine(firmware))
        shell.noteActivity("romm", "drift", GameActivity.Downloading(50L, 100L), nowMillis = 0L)
        assertEquals(TileActivity.Downloading(0.5f), shell.activity.getValue(id))
        assertEquals("Downloading 50%", activityLine(shell.activity.getValue(id)))
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

        choose(shell, Row.Hide)
        // No games, no folder: the folder closed under the focus, and Back has nothing left to close.
        assertTrue(titles(shell).none { it == "Nintendo DS" })
        assertTrue(shell.model.atLibraryRoot)
        assertEquals(listOf(LibraryCopy.title), titles(shell))
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
        choose(shell, Row.Hide)
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
    fun pxPlayReadsAsBothScreensAndOtherAppsTakeOne() {
        // PXPlay draws the DualSense controls on the bottom screen while it streams.
        val shell = shell()
        shell.setInstalledApps(
            listOf(LaunchableApp("psplay.grill.com", "PXPlay", false), LaunchableApp("com.example.notes", "Notes", false)),
            emptySet(),
        )
        val home = titles(shell)
        // A streaming app, as Moonlight is, so it files with the emulators.
        focus(shell, home.indexOf("Emulators"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf("PXPlay"), titles(shell).filter(String::isNotEmpty))

        focus(shell, titles(shell).indexOf("PXPlay"))
        assertEquals("psplay.grill.com", shell.focusedGame()?.androidPackage)
        assertTrue(shell.focusedGame()!!.occupiesBothDisplays)
        assertFalse(shell.focusedLaunchesOnOneScreen())

        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Android Apps"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Notes"))
        assertFalse(shell.focusedGame()!!.occupiesBothDisplays)
        assertTrue(shell.focusedLaunchesOnOneScreen())
    }

    @Test
    fun aDsGameReadsAsBothScreensBesideRetroArchsDsCoresUntilOnlyRetroArchIsInstalled() {
        // RetroArch also runs DS games now, on one screen, ranked behind melonDS. The hero and
        // the launch keys follow the emulator the game opens in, not every one that could.
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        plugins.register(LocalFolderEntry())
        plugins.register(MelonDsEntry())
        plugins.register(RetroArchEntry())
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins)
        shell.ingestLibrary("local-folder", listOf(entry("tetris", "Tetris DS", "nintendo-ds")), emptyList())
        focus(shell, titles(shell).indexOf("Nintendo DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(shell.focusedGame()!!.occupiesBothDisplays)
        assertFalse(shell.focusedLaunchesOnOneScreen())

        shell.setInstalledApps(listOf(LaunchableApp(MelonDsPlayer.STABLE_PACKAGE, "melonDS", false), LaunchableApp(RETROARCH, "RetroArch", false)), emptySet())
        assertTrue(shell.focusedGame()!!.occupiesBothDisplays)

        shell.setInstalledApps(listOf(LaunchableApp(RETROARCH, "RetroArch", false)), emptySet())
        assertFalse(shell.focusedGame()!!.occupiesBothDisplays)
        assertTrue(shell.focusedLaunchesOnOneScreen())

        // An emulator with no launcher entry still counts, as it does at launch.
        val apps = listOf(LaunchableApp(RETROARCH, "RetroArch", false))
        shell.setInstalledApps(apps, emptySet(), installedPlayers = setOf(RETROARCH, MelonDsPlayer.STABLE_PACKAGE))
        assertTrue(shell.focusedGame()!!.occupiesBothDisplays)

        // A hero already drawn reads state that an install changes, so it draws again.
        val reads = mutableSetOf<Any>()
        Snapshot.observe(readObserver = { reads += it }) { shell.focusedGame() }
        val changed = mutableSetOf<Any>()
        val handle = Snapshot.registerApplyObserver { objects, _ -> changed.addAll(objects) }
        try {
            shell.setInstalledApps(apps, emptySet(), installedPlayers = setOf(RETROARCH))
            Snapshot.sendApplyNotifications()
        } finally {
            handle.dispose()
        }
        assertTrue(reads.any { it in changed })
        assertFalse(shell.focusedGame()!!.occupiesBothDisplays)
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
    fun yMakesANamedCollectionThatWearsTheIconYouPick() {
        val shell = shell()
        // On the All tile there is no game to hold, so the collection takes the first free slot.
        choose(shell, Row.NewCollection)
        // A new collection is a gold folder card showing the stack of games.
        val plain = CollectionLook(MARK_LIBRARY, CollectionTint.Gold).mark
        val folder = List(shell.model.count) { shell.homeFace(it) }.indexOfFirst { it?.folder == true && it.mark == plain }
        assertTrue(folder >= 0)
        // The new collection takes the focus and opens its name field.
        assertEquals(folder, shell.model.focus.cellIndex)
        assertEquals(HomeKeys.Typing, shell.homeKeys())
        // A grid opened from a command center stays while a name is typed.
        assertTrue(shell.holdsCommandCenter())
        assertEquals("${Copy.collection} 1", shell.renameDraft())
        // A new collection's keyboard starts on the quick names; A picks one and Start saves it.
        assertTrue(shell.typing()!!.typing.focus.onChips)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(KeyboardCopy.quickNames.first(), shell.renameDraft())
        shell.typing()?.setText("Handhelds")
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        assertNull(shell.renameDraft())
        assertEquals(HomeKeys.Idle, shell.homeKeys())
        assertFalse(shell.holdsCommandCenter())
        assertEquals("Handhelds", shell.homeFace(folder)?.title)
        assertEquals(folder, shell.model.focus.cellIndex)
        // Renaming starts with the old name selected: X clears it, a tapped key types, B keeps the old name.
        choose(shell, Row.RenameFolder)
        assertFalse(shell.typing()!!.typing.focus.onChips)
        shell.onMeaning(Meaning.Erase, HostScreen.Bottom)
        shell.tapTyping(KeyboardLayout.find(KeyCap.Glyph('n')))
        assertEquals("N", shell.renameDraft())
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        assertNull(shell.model.panel)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.renameDraft())
        assertEquals("Handhelds", shell.homeFace(folder)?.title)
        // Done on an empty name keeps the keyboard open with the old name back.
        choose(shell, Row.RenameFolder)
        shell.onMeaning(Meaning.Erase, HostScreen.Bottom)
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        assertEquals("Handhelds", shell.renameDraft())
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)

        choose(shell, Row.FolderIcon)
        assertEquals(HomeKeys.PickingIcon, shell.homeKeys())
        assertEquals(FOLDER_ICONS.indexOf(MARK_LIBRARY), shell.folderIconPick()?.first?.glyph)
        // The tile wears each choice as the cursor reaches it.
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        val glyph = FOLDER_ICONS[FOLDER_ICONS.indexOf(MARK_LIBRARY) + 1]
        assertEquals(CollectionLook(glyph, CollectionTint.Gold).mark, shell.homeFace(folder)?.mark)
        // R1 steps the colour without moving off the glyph.
        shell.onMeaning(Meaning.RightPanel, HostScreen.Bottom)
        val chosen = CollectionLook(glyph, CollectionTint.Coral)
        assertEquals(chosen.mark, shell.homeFace(folder)?.mark)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertNull(shell.folderIconPick())
        assertEquals(chosen.mark, shell.homeFace(folder)?.mark)
        assertTrue(shell.settledAt(shell.homeFace(folder)?.id) > 0L)
        assertEquals(folder, shell.model.focus.cellIndex)
        // B puts the old look back, though the tile wore the new one while it was focused.
        choose(shell, Row.FolderIcon)
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertFalse(chosen.mark == shell.homeFace(folder)?.mark)
        shell.onMeaning(Meaning.CancelHold, HostScreen.Bottom)
        assertEquals(chosen.mark, shell.homeFace(folder)?.mark)
        // An open editor holds a command center's grid; going home closes it with the old look.
        assertFalse(shell.holdsCommandCenter())
        choose(shell, Row.FolderIcon)
        assertTrue(shell.holdsCommandCenter())
        shell.onMeaning(Meaning.RightPanel, HostScreen.Bottom)
        shell.goHome()
        assertNull(shell.folderIconPick())
        assertFalse(shell.holdsCommandCenter())
        assertEquals(HomeKeys.Idle, shell.homeKeys())
        assertEquals(chosen.mark, List(shell.model.count) { shell.homeFace(it) }.firstOrNull { it?.title == "Handhelds" }?.mark)
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
    fun aGameCarriedOutOfItsFolderWalksOnAlongHomeAndBPutsItAllBack() {
        val shell = shell()
        shell.ingestLibrary(
            "local-folder",
            listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-3ds")),
            emptyList(),
        )
        val root = titles(shell)
        val folder = root.indexOf("Nintendo 3DS")
        focus(shell, folder)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        val inside = titles(shell)
        choose(shell, Row.MoveTile)
        // Up from the top row rises to the Home target; Y takes the game out, still held.
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertEquals(app.foldcade.language.HoldDrop.TakeOut, shell.homeHoldDrop())
        assertEquals(true, shell.homeCarry()?.overHome)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(HomeKeys.Moving, shell.homeKeys())
        assertEquals(folder, shell.model.focus.cellIndex)
        assertEquals(inside[0], titles(shell)[folder])
        assertTrue("Nintendo 3DS" in titles(shell))
        // The top screen shows the game in the hand.
        assertEquals(inside[0], (shell.focusedHero() as? app.foldcade.language.HeroSubject.Item)?.item?.title)
        // B puts it all back: the folder is open again, with the game where it was.
        shell.onMeaning(Meaning.CancelHold, HostScreen.Bottom)
        assertEquals(HomeKeys.Idle, shell.homeKeys())
        assertEquals(inside, titles(shell))
        assertEquals(0, shell.model.focus.cellIndex)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertEquals(root, titles(shell))

        // Carried out again and put down, it is a pin, and its folder still has it.
        focus(shell, folder)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        choose(shell, Row.MoveTile)
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(HomeKeys.Idle, shell.homeKeys())
        assertEquals(inside[0], titles(shell)[folder])
        val moved = titles(shell).indexOf("Nintendo 3DS")
        focus(shell, moved)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(inside, titles(shell))
    }

    @Test
    fun theTopScreenReadsTheTileInTheHandNotTheSlotUnderTheCursor() {
        val shell = shell()
        shell.ingestLibrary(
            "local-folder",
            listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-3ds")),
            emptyList(),
        )
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Drift"))
        choose(shell, Row.MoveTile)
        // Up onto the top row, then Up to the Home target: the slots go back and Puzzle is under the cursor.
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertEquals(true, shell.homeCarry()?.overHome)
        assertEquals("Puzzle", titles(shell)[shell.model.focus.cellIndex])
        val hero = (shell.focusedHero() as? app.foldcade.language.HeroSubject.Item)?.item
        assertEquals("Drift", hero?.title)
        assertEquals(hero?.key, shell.focusedGame()?.id)
        assertEquals("drift", shell.focusedRecord()?.remoteKey)
    }

    @Test
    fun aHeldDirectionStopsAtTheLastColumnAndAHeldADoesNotLaunch() {
        val shell = shell()
        shell.ingestLibrary(
            "local-folder",
            listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-ds")),
            emptyList(),
        )
        val last = GridStrip.column(shell.model.count - 1)
        focus(shell, 0)
        choose(shell, Row.MoveTile)
        // Held, it steps on every other repeat.
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom, repeatCount = 1)
        assertEquals(0, shell.model.focus.cellIndex)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom, repeatCount = 2)
        assertEquals(1, shell.model.focus.cellIndex)
        repeat(GridStrip.trailing + 3) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom, repeatCount = it + 1) }
        assertEquals(last, GridStrip.column(shell.model.focus.cellIndex))
        // A fresh press goes on into the empty strip.
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertEquals(last + 1, GridStrip.column(shell.model.focus.cellIndex))
        shell.onMeaning(Meaning.CancelHold, HostScreen.Bottom)
        val folder = titles(shell).indexOf("Nintendo 3DS")
        focus(shell, folder)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        // Still down after it opened the folder, A launches nothing.
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom, repeatCount = 1))
        assertTrue(shell.onMeaning(Meaning.Activate, HostScreen.Bottom) is Effect.Launch)
    }

    @Test
    fun tidyInEditHomeClosesTheGaps() {
        val shell = shell()
        shell.ingestLibrary(
            "local-folder",
            listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-ds")),
            emptyList(),
        )
        val before = titles(shell)
        // Carry the first folder past the end, which leaves a gap where it was.
        val first = before.indexOf("Nintendo 3DS")
        focus(shell, first)
        choose(shell, Row.MoveTile)
        repeat(GridStrip.trailing) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, first)
        // Tidy closes every gap on the board, so it is L1's, never the empty slot's.
        assertFalse(Row.Tidy in optionRows(shell))
        openEditHome(shell)
        val rows = panelRows(shell.model.panel!!, shell.model)
        walkPanelTo(shell, rows.indexOf(Row.Tidy), HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(before.filter { it != "Nintendo 3DS" } + "Nintendo 3DS", titles(shell).filter { it.isNotEmpty() })
        assertTrue(titles(shell).dropLastWhile { it.isEmpty() }.none { it.isEmpty() })
        assertFalse(Row.Tidy in editHomeRows(shell.model))
    }

    @Test
    fun theLastTileTakenOffHomeTakesItsSlotAndTheStripsReachWithIt() {
        val shell = shell()
        shell.ingestLibrary(
            "local-folder",
            listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-ds")),
            emptyList(),
        )
        val count = shell.model.count
        val lastTitle = titles(shell).last()
        focus(shell, count - 1)
        choose(shell, Row.RemoveTile)
        assertFalse(lastTitle in titles(shell))
        // No empty slot is left at the end, so the reach is the remaining tiles' own.
        assertEquals(count - 1, shell.model.count)
        assertFalse(shell.homeFace(shell.model.count - 1)?.empty == true)
        repeat(GridStrip.columns(count) + GridStrip.trailing + 2) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        assertEquals(GridStrip.columns(count - 1) - 1 + GridStrip.trailing, GridStrip.column(shell.model.focus.cellIndex))
    }

    @Test
    fun aHomeSavedWithEmptySlotsAtItsEndReachesOnlyAsFarAsItsTiles() {
        // Boards saved before this kept the slot a removed last tile left behind.
        val store = SessionStore(MemoryPrefs())
        val folder = HomeFolder("mine", "Mine", userMade = true, slots = listOf(androidHomeId("app")))
        store.saveHomeBoard(encodeHome(HomeBoard(
            slots = listOf(HOME_ALL, folder.id, null, null, null, null),
            folders = mapOf(folder.id to folder), addNewToHome = false,
        )))
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(LaunchableApp("app", "App", false)), emptySet())
        // All and Mine fill one column.
        assertEquals(1, GridStrip.columns(shell.model.count))
        assertFalse(shell.homeFace(shell.model.count - 1)?.empty == true)
        repeat(Metrics.columns * 3) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        assertEquals(GridStrip.trailing, GridStrip.column(shell.model.focus.cellIndex))
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
        // An empty slot shows no game and offers a move and a new collection, as an empty slot between tiles does.
        assertNull(shell.focusedGame())
        assertEquals(listOf(Row.MoveTile, Row.NewCollection), optionRows(shell))
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
    fun aGameInAFolderPinsJoinsCollectionsAndStaysInItsFolder() {
        val shell = shell()
        shell.ingestLibrary(
            "local-folder",
            listOf(entry("puzzle", "Puzzle", "nintendo-3ds"), entry("drift", "Drift", "nintendo-3ds")),
            emptyList(),
        )
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Puzzle"))
        // A folder holds every game of its kind, so nothing is removed from it; Hide is its own row.
        // The folder's Sort orders the whole folder, so it is Edit home's, not the game's.
        assertEquals(listOf(Row.MoveTile, Row.AddToCollection, Row.SelectTile, Row.Hide), optionRows(shell))
        assertNull(shell.model.tileActions?.remove)
        // Add to › Home pins it, leaving the game in its folder, and the focus on it.
        choose(shell, Row.AddToCollection)
        assertEquals(PanelLevel.Collections, shell.model.panel?.level)
        assertEquals(listOf(Row.Collection(HOME_ROOT, Copy.home), Row.NewCollection), panelRows(shell.model.panel!!, shell.model))
        assertFalse(shell.model.tileActions!!.collections!![0].member)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(shell.model.tileActions!!.collections!![0].member)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        val inside = titles(shell)
        assertEquals(listOf("Puzzle", "Drift"), inside)
        assertEquals("Puzzle", shell.focusedGame()?.title)

        // Add to: the first collection is made from the menu, holding the game, which stays where it is.
        focus(shell, inside.indexOf("Drift"))
        choose(shell, Row.AddToCollection)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(HomeKeys.Typing, shell.homeKeys())
        // The game's platform leads the quick names.
        assertEquals("Nintendo 3DS", shell.typing()?.typing?.chips?.first())
        shell.typing()?.setText("Quests")
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        assertEquals(inside, titles(shell))
        assertEquals("Drift", shell.focusedGame()?.title)

        // A toggles a collection and the menu stays open with its check.
        choose(shell, Row.AddToCollection)
        val quests = panelRows(shell.model.panel!!, shell.model).indexOfFirst { it is Row.Collection && it.label == "Quests" }
        assertEquals(1, quests)
        assertTrue(shell.model.tileActions!!.collections!![quests].member)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(PanelLevel.Collections, shell.model.panel?.level)
        assertFalse(shell.model.tileActions!!.collections!![quests].member)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(shell.model.tileActions!!.collections!![quests].member)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)

        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        val root = titles(shell)
        assertTrue("Puzzle" in root)
        assertTrue("Quests" in root)
        assertFalse("Drift" in root)
        assertEquals(folderCountLine(1), shell.homeFace(root.indexOf("Quests"))?.shortText)
        // Remove on a pin unpins it: only the root tile goes.
        focus(shell, root.indexOf("Puzzle"))
        assertEquals(listOf(Row.MoveTile, Row.AddToCollection, Row.SelectTile, Row.RemoveTile, Row.Hide), optionRows(shell))
        assertEquals(Copy.unpin, rowText(Row.RemoveTile, shell.model).label)
        choose(shell, Row.RemoveTile)
        assertFalse("Puzzle" in titles(shell))
        assertEquals(Copy.unpinned, shell.model.undoVerb)
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(inside, titles(shell))

        // Out of the collection on screen, Add to stays open on the same game, and can put it back.
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Quests"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf("Drift"), titles(shell))
        assertEquals(listOf(Row.MoveTile, Row.AddToCollection, Row.SelectTile, Row.RemoveTile, Row.Hide), optionRows(shell))
        assertEquals(Copy.remove, rowText(Row.RemoveTile, shell.model).label)
        choose(shell, Row.AddToCollection)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(PanelLevel.Collections, shell.model.panel?.level)
        assertEquals(1, shell.model.panel?.index)
        assertEquals(listOf(""), titles(shell))
        assertFalse(shell.model.tileActions!!.collections!![1].member)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf("Drift"), titles(shell))
        assertTrue(shell.model.tileActions!!.collections!![1].member)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf(""), titles(shell))
        // Home, first in the list, pins the game the menu kept.
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(shell.model.tileActions!!.collections!![0].member)
        // Back in its menu, the rest acts on that game too: Hide, and the menu closes.
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        val held = panelRows(shell.model.panel!!, shell.model).filterNot { it == Row.Undo }
        assertEquals(listOf(Row.AddToCollection, Row.Hide), held)
        pickRowFocus(shell, Row.Hide)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertNull(shell.model.panel)
        assertEquals(Copy.hidden, shell.model.undoVerb)
        // The focus rests on the empty slot it left, and Y there is the slot's own menu.
        assertEquals(0, shell.model.focus.cellIndex)
        assertEquals(listOf(Row.MoveTile), optionRows(shell))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertFalse("Drift" in titles(shell))
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertFalse("Drift" in titles(shell))
        assertTrue("Puzzle" in titles(shell))
    }

    @Test
    fun removingAFolderFromHomeSpillsNothingOntoTheRoot() {
        val shell = shell()
        shell.ingestLibrary("local-folder", listOf(entry("puzzle", "Puzzle", "nintendo-3ds")), emptyList())
        val folder = titles(shell).indexOf("Nintendo 3DS")
        focus(shell, folder)
        assertEquals(listOf(Row.MoveTile, Row.RenameFolder, Row.RemoveTile, Row.SortFolder), optionRows(shell))
        choose(shell, Row.RemoveTile)
        // The folder was the last tile, so its empty slot goes with it.
        assertEquals(listOf(LibraryCopy.title), titles(shell))
        // A rescan leaves it off Home. Puzzle is still in All, and not on Home.
        shell.ingestLibrary("local-folder", listOf(entry("puzzle", "Puzzle", "nintendo-3ds")), emptyList())
        assertFalse("Nintendo 3DS" in titles(shell))
        shell.openHomeAll()
        val puzzle = titles(shell).indexOf("Puzzle")
        assertFalse(shell.homeFace(puzzle)!!.onGrid)
        focus(shell, puzzle)
        // All offers what a game has, and nothing that brings a folder back. All has no spots, so no Remove.
        assertEquals(listOf(Row.AddToCollection, Row.SelectTile, Row.Hide), optionRows(shell))
        // With no Move in All, Y then Y does nothing.
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val (open, effect) = shell.model to shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertNull(effect)
        assertEquals(open.panel, shell.model.panel)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        // Add to › Home pins it. The folder the user took off Home stays off.
        addToHome(shell)
        assertTrue(shell.homeFace(puzzle)!!.onGrid)
        shell.goHome()
        assertTrue("Puzzle" in titles(shell))
        assertFalse("Nintendo 3DS" in titles(shell))
    }

    @Test
    fun aPlayBringsRecentlyPlayedAndItOpensReadOnly() {
        val played = HashMap<String, Long>()
        val shell = ShellController(
            SessionStore(MemoryPrefs()),
            PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()),
            lastPlayedMillis = { played[it] },
        )
        shell.ingestLibrary("local-folder", listOf(entry("puzzle", "Puzzle", "nintendo-3ds")), emptyList())
        assertFalse(Copy.recentlyPlayed in titles(shell))
        played[homeGameId("local-folder", "puzzle")] = System.currentTimeMillis() + 60_000L
        shell.notePlayChanged()
        val recent = titles(shell).indexOf(Copy.recentlyPlayed)
        assertTrue(recent >= 0)
        focus(shell, recent)
        assertEquals(listOf(Row.MoveTile, Row.RenameFolder, Row.RemoveTile), optionRows(shell))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf("Puzzle"), titles(shell))
        // Nothing moves or comes out of it, and the game can still be pinned and filed.
        assertEquals(listOf(Row.AddToCollection, Row.SelectTile, Row.Hide), optionRows(shell))
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
        choose(shell, Row.Hide)
        shell.openHomeAll()
        val puzzle = titles(shell).indexOf("Puzzle")
        focus(shell, puzzle)
        assertFalse(shell.homeFace(puzzle)!!.onGrid)
        // A hidden game has nothing to hide; Show puts it back, as Find and Hidden offer.
        assertEquals(listOf(Row.AddToCollection, Row.ShowHidden, Row.SelectTile), optionRows(shell))
        // Add to › Home brings a hidden game back: a pin, and back in its folder, which comes back with it.
        addToHome(shell)
        assertTrue(shell.homeFace(puzzle)?.onGrid == true)
        // Back on Home, it can be shown there.
        assertEquals(listOf(Row.AddToCollection, Row.ShowOnHome, Row.SelectTile, Row.Hide), optionRows(shell))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertTrue("Puzzle" in titles(shell))
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf("Puzzle"), titles(shell))
    }

    @Test
    fun undoExpiryCannotMoveDeleteUnderTheFocusedAddToRow() {
        val shell = shell()
        shell.ingestLibrary("romm", listOf(entry("drift", "Drift", "nintendo-3ds", backendId = "romm", availability = Availability.Cached)), emptyList())
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        choose(shell, Row.Hide)
        shell.openHomeAll()
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        pickRowFocus(shell, Row.AddToCollection)
        val rows = panelRows(shell.model.panel!!, shell.model)
        recoveryTime += 8001L
        shell.tickRecovery()
        assertEquals(rows, panelRows(shell.model.panel!!, shell.model))
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertNull(shell.model.deleteHold)
        // A opened Add to, not Delete download; its first choice is Home.
        assertEquals(PanelLevel.Collections, shell.model.panel?.level)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.goHome()
        assertTrue("Drift" in titles(shell))
    }

    private fun pickRowFocus(shell: ShellController, row: Row) {
        val target = panelRows(shell.model.panel!!, shell.model).indexOf(row)
        assertTrue(target >= 0)
        walkPanelTo(shell, target, HostScreen.Bottom)
    }

    @Test
    fun deleteDownloadSuppressesTheConfirmRepeatUntilKeyUp() {
        val shell = shell()
        shell.ingestLibrary("romm", listOf(entry("drift", "Drift", "nintendo-3ds", backendId = "romm", availability = Availability.Cached)), emptyList())
        shell.openHomeAll()
        choose(shell, Row.RemoveDownload)
        recoveryTime += 800L
        assertEquals(Effect.RemoveDownload(homeGameId("romm", "drift")), shell.tickRecovery())
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom, repeatCount = 1))
        shell.releaseMeaning(Meaning.Activate, canceled = true)
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Top, repeatCount = 2))
        shell.releaseMeaning(Meaning.Activate)
        assertNotNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
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
            listOf(Row.MoveTile, Row.AddToCollection, Row.Saves, Row.SelectTile, Row.Hide, Row.RemoveDownload),
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
        walkPanelTo(shell, rows.indexOf(Row.RemoveDownload), HostScreen.Bottom)
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        recoveryTime += 800L
        assertEquals(Effect.RemoveDownload(homeGameId("romm", "drift")), shell.tickRecovery())
        assertNull(shell.model.panel)
        assertEquals("drift", shell.focusedDownload()?.remoteKey)

        shell.noteOffDevice("romm", "drift")
        assertEquals(true, shell.homeFace(titles(shell).indexOf("Drift"))?.onServer)
        assertNull(shell.focusedDownload())
        assertFalse(Row.RemoveDownload in optionRows(shell))
    }

    @Test
    fun aMenuThatKeptItsGameDeletesThatGamesDownload() {
        // Review r2 #2: the game left the collection on screen, and Delete download read the empty slot.
        val shell = shell()
        shell.setArtKeySaved(true)
        val drift = homeGameId("romm", "drift")
        shell.ingestLibrary(
            "romm",
            listOf(entry("drift", "Drift", "nintendo-3ds", backendId = "romm", availability = Availability.Cached)),
            emptyList(),
        )
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.typing()?.setText("Racers")
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Racers"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf("Drift"), titles(shell))
        // In the slot, Art names the game and opens on it.
        assertTrue(Row.ChooseArt in optionRows(shell))
        assertEquals(drift, shell.model.tileActions?.game)
        choose(shell, Row.ChooseArt)
        assertEquals(drift, shell.artPicker?.gameId)
        shell.artInput(Meaning.Back)
        assertNull(shell.artPicker)

        choose(shell, Row.AddToCollection)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf(""), titles(shell))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        val rows = panelRows(shell.model.panel!!, shell.model)
        assertEquals(drift, shell.model.tileActions?.game)
        // Returning to the parent keeps the rows it showed before the collection toggle.
        assertTrue(Row.ChooseArt in rows)
        assertTrue(Row.RemoveDownload in rows)
        walkPanelTo(shell, rows.indexOf(Row.RemoveDownload), HostScreen.Bottom)
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        recoveryTime += 800L
        val effect = shell.tickRecovery()
        assertEquals(Effect.RemoveDownload(drift), effect)
        assertNull(shell.model.panel)
        // The slot is empty, so only the named game finds the copy to delete.
        assertNull(shell.focusedDownload())
        assertEquals("drift", shell.downloadFor((effect as Effect.RemoveDownload).gameId)?.remoteKey)
        // While it is being deleted, it is not offered again.
        assertTrue(shell.beginRemove("romm", "drift"))
        assertNull(shell.downloadFor(drift))
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
    fun changingTheServerHoldsDeleteDownloadUntilItListsItsGames() {
        val shell = shell()
        val cached = listOf(entry("42", "Drift", "nintendo-3ds", backendId = "romm", availability = Availability.Cached))
        shell.ingestLibrary("romm", cached, emptyList())
        shell.openHomeAll()
        focus(shell, titles(shell).indexOf("Drift"))
        assertEquals("42", shell.focusedDownload()?.remoteKey)
        val before = shell.deviceMark()
        // ROM 42 on the new server is another game, so the old tile's copy is not the one it would delete.
        shell.noteServerChanged("romm")
        assertNull(shell.focusedDownload())
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertFalse(Row.RemoveDownload in panelRows(shell.model.panel!!, shell.model))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        // Refreshes that started before the change, or while it was saving, list the old server.
        shell.ingestLibrary("romm", cached, emptyList(), readAt = before)
        shell.ingestLibrary("romm", cached, emptyList(), readAt = shell.deviceMark())
        assertNull(shell.focusedDownload())
        shell.settleServerChange("romm")
        val published = shell.deviceMark()
        shell.settleServerChange("romm")
        assertEquals(published, shell.deviceMark())
        shell.ingestLibrary("romm", cached, emptyList(), readAt = before)
        assertNull(shell.focusedDownload())
        shell.ingestLibrary("romm", cached, emptyList(), readAt = published)
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
        assertEquals(listOf(Row.AddToCollection, Row.Saves, Row.ShowOnHome, Row.SelectTile, Row.Hide, Row.RemoveDownload), optionRows(shell))
        shell.noteOffDevice("romm", "drift")
        assertFalse(Row.RemoveDownload in optionRows(shell))
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
    @Test
    fun namingShowsWhatIsBeingNamedNotWhatTheGridHasFocused() {
        // Review r4: inside a collection, Add to unchecks it, which empties the focused slot,
        // and New collection then had no hero, so the name and caret were nowhere to be seen.
        val shell = shell()
        shell.ingestLibrary("local-folder", listOf(entry("drift", "Drift", "nintendo-3ds")), emptyList())
        focus(shell, titles(shell).indexOf("Nintendo 3DS"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Drift"))
        choose(shell, Row.AddToCollection)
        pickRow(shell) { it == Row.NewCollection }
        shell.typing()?.setText("Racers")
        shell.onMeaning(Meaning.Done, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Racers"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf("Drift"), titles(shell).filter { it.isNotEmpty() })

        choose(shell, Row.AddToCollection)
        pickRow(shell) { it is Row.Collection && it.label == "Racers" }
        pickRow(shell) { it == Row.NewCollection }
        assertEquals(HomeKeys.Typing, shell.homeKeys())
        // The slot the grid has focused is empty now, so it has no hero of its own.
        assertNull(shell.focusedHero())
        // The keyboard's hero is the collection being named.
        val named = shell.typingHero()
        assertEquals("${Copy.collection} 2", (named as HeroSubject.Folder).folder.name)
        // It wears its folder card, as its tile will.
        assertEquals(CollectionLook(MARK_LIBRARY, CollectionTint.Gold).mark, named.folder.mark)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.typingHero())
    }

    /** Down the open menu to the first row that matches, then A. */
    private fun pickRow(shell: ShellController, match: (Row) -> Boolean) {
        val panel = shell.model.panel ?: error("no menu open")
        val rows = panelRows(panel, shell.model)
        val to = rows.indexOfFirst(match)
        assertTrue("no such row in $rows", to >= 0)
        walkPanelTo(shell, to, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    /** Y › Add to › Home, then out of the menu. */
    private fun addToHome(shell: ShellController) {
        choose(shell, Row.AddToCollection)
        assertEquals(Row.Collection(HOME_ROOT, Copy.home), panelRows(shell.model.panel!!, shell.model)[shell.model.panel!!.index])
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
    }

    /** L1, down to Edit home, then A: the board's settings, never the tile's. */
    private fun openEditHome(shell: ShellController) {
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        walkPanelTo(shell, leftRows().indexOf(Row.EditHome), HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(PanelLevel.EditHome, shell.model.panel?.level)
    }

    private fun optionRows(shell: ShellController): List<Row> =
        actionRows(shell.model.tileActions).filterNot { it == Row.Undo }

    /** Y, down the menu to [row], then A. */
    private fun choose(shell: ShellController, row: Row) {
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val panel = shell.model.panel ?: error("Y opened no menu")
        val rows = panelRows(panel, shell.model)
        assertTrue("$row in $rows", row in rows)
        walkPanelTo(shell, rows.indexOf(row), HostScreen.Bottom)
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

    private var recoveryTime = 0L

    private fun shell(): ShellController =
        ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { recoveryTime })
}

private const val RETROARCH = "com.retroarch.aarch64"
