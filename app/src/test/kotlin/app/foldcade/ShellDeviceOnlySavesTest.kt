package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.plugins.emulators.EmulatorsEntry
import app.foldcade.plugins.explus.ExPlusEntry
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/** Y › Saves for games whose saves stay on this device, end to end through the shell (design §7.1). */
class ShellDeviceOnlySavesTest {
    private val played = mutableSetOf<String>()
    private val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
        register(LocalFolderEntry()); register(ExPlusEntry()); register(EmulatorsEntry())
    }
    private val store = SessionStore(MemoryPrefs())
    private val shell = ShellController(store, plugins, lastPlayedMillis = { if (it in played) 1L else null }, playedTiles = { played.toList() }).apply {
        setInstalledApps(emptyList(), plugins.playerPackageNames(), plugins.playerPackageNames())
    }
    private val myBoy = PlayerChoice("my-boy.game-boy-advance", label = "My Boy!")
    private val gbaEmu = "gba-emu.game-boy-advance"
    // A provider may name the file by an opaque document id: the name must come from the catalog, never the URI.
    private val local = Game("local-folder", "content://tree/document/42", "game-boy-advance", Availability.LocalOnly, "Puzzle")
    private val server = Game("romm", "7", "game-boy-advance", Availability.Cached, "Tetris")
    private fun entry(game: Game, file: String) = GridEntry(game.remoteKey, game.label, "", game.platformId, null, true, game, file)
    private val localId = homeGameId(local.backendId, local.remoteKey)
    private val serverId = homeGameId(server.backendId, server.remoteKey)
    private fun target(id: String, game: Game) = SavePickerTarget(id, game.backendId, game.remoteKey, shell.savePlayer(shell.launchRoute(id)!!)?.id)

    @Test fun everyLibraryGameOffersSavesIncludingOnesRommDoesNotHave() {
        shell.ingestLibrary("romm", listOf(entry(server, "Tetris.gba")), emptyList())
        shell.ingestLibrary("local-folder", listOf(entry(local, "Puzzle.gba")), emptyList())
        shell.openHomeAll()
        for (title in listOf("Tetris", "Puzzle")) {
            val index = (0 until shell.model.count).single { shell.homeFace(it)?.title == title }
            shell.touchCell(index, HostScreen.Bottom)
            assertTrue(title, shell.model.tileActions?.saves == true)
        }
    }

    @Test fun aLaunchOnlyFolderGameOffersTheEmulatorThatSyncsThenItsSetupAndTheSameNameRoute() {
        shell.ingestLibrary("local-folder", listOf(entry(local, "Puzzle.gba")), emptyList())
        shell.useSavePlayer(localId, myBoy)
        assertEquals(SaveStatus.DeviceOnly(NoDestinationWhy.PlayerCantSync, emulator = "My Boy!"), shell.saveHealthOf(localId))
        val route = shell.savePickerRoute(target(localId, local))!!
        assertEquals("Foldcade can’t sync My Boy!, so its saves stay on this device.", route.summary)
        assertEquals("Play with GBA.emu", route.steps[0].title)
        assertEquals(gbaEmu, (route.action as SaveRouteAction.UsePlayer).choice.playerId)
        // No RomM server is signed in here, so the step says to sign in first.
        assertEquals("Sign in to RomM, then add this game", route.steps[1].title)
        assertTrue(route.steps[1].detail!!.contains("upload Puzzle.gba"))
        assertEquals("Your progress in My Boy! stays there. GBA.emu starts without it.", route.note)

        // Switching is Y › Open with's choice, and the page then shows the new emulator's way on.
        shell.useSavePlayer(localId, (route.action as SaveRouteAction.UsePlayer).choice)
        assertEquals(gbaEmu, store.gamePlayerChoice(shell.tileOf(localId))?.playerId)
        // Never played here: the line is amber, the tile stays plain (§10.1).
        assertEquals(SaveStatus.NotSyncing(SaveIssue.NoFolder, badge = false), shell.saveHealthOf(localId))
        val next = shell.savePickerRoute(target(localId, local))!!
        assertEquals(listOf(SaveRouteMark.Done, SaveRouteMark.Now, SaveRouteMark.Next), next.steps.map { it.mark })
        assertEquals(SaveRouteAction.SetUp, next.action)
    }

    @Test fun aSyncingFolderGameStaysDeviceOnlyEvenWithAReceiptFromItsOwnLibrary() {
        shell.ingestLibrary("local-folder", listOf(entry(local, "Puzzle.gba")), emptyList())
        shell.useSavePlayer(localId, PlayerChoice(gbaEmu, label = "GBA.emu"))
        (plugins.player(gbaEmu) as SaveFolderHolder).bindSaveFolder("content://saves")
        shell.refreshPlayerSaves()
        // The host reads receipts for folder games too; "nothing yet" there must not hide that RomM can't hold it.
        shell.setSaveDestination(local, OffDeviceFact.NothingYet)
        assertTrue(shell.saveHealthOf(localId) is SaveStatus.DeviceOnly)
        shell.setSaveDestination(local, OffDeviceFact.Unknown)
        assertTrue(shell.saveHealthOf(localId) is SaveStatus.DeviceOnly)
        val route = shell.savePickerRoute(target(localId, local))!!
        // No RomM server is signed in here, so the page doesn't claim RomM lacks the game.
        assertEquals("Foldcade isn’t signed in to RomM, so this game’s saves stay on this device.", route.summary)
        assertEquals(listOf(SaveRouteMark.Done, SaveRouteMark.Now), route.steps.map { it.mark })
    }

    @Test fun aRommGameWithAnEmulatorThatSyncsHasNoRoute() {
        shell.ingestLibrary("romm", listOf(entry(server, "Tetris.gba")), emptyList())
        shell.useSavePlayer(serverId, PlayerChoice(gbaEmu, label = "GBA.emu"))
        assertNull(shell.savePickerRoute(target(serverId, server)))
        shell.useSavePlayer(serverId, myBoy)
        val route = shell.savePickerRoute(target(serverId, server))!!
        assertEquals(1, route.steps.size)
        assertEquals("Play with GBA.emu", route.steps.single().title)
    }

    @Test fun settingsCountsOnlyPlayedGamesWhoseSavesStayHere() {
        shell.ingestLibrary("romm", listOf(entry(server, "Tetris.gba")), emptyList())
        shell.ingestLibrary("local-folder", listOf(entry(local, "Puzzle.gba")), emptyList())
        shell.useSavePlayer(localId, PlayerChoice(gbaEmu, label = "GBA.emu"))
        shell.useSavePlayer(serverId, PlayerChoice(gbaEmu, label = "GBA.emu"))
        (plugins.player(gbaEmu) as SaveFolderHolder).bindSaveFolder("content://saves")
        shell.refreshPlayerSaves()
        // §10.1: a folder game nobody has played here has nothing at stake.
        assertEquals(0, shell.model.savesOnlyHere)
        played += shell.playId(localId)
        shell.notePlayChanged()
        assertEquals(1, shell.model.savesOnlyHere)
        assertEquals(SaveSetupCopy.onlyOnThisDevice(1), rowText(Row.SaveSetup, shell.model).value)
        // A played RomM game with an emulator that syncs is not counted.
        played += shell.playId(serverId)
        shell.notePlayChanged()
        assertEquals(1, shell.model.savesOnlyHere)
        // Played with an emulator Foldcade can't sync, the RomM game's saves stay here too.
        shell.useSavePlayer(serverId, myBoy)
        assertEquals(2, shell.model.savesOnlyHere)
    }

    @Test fun aQueuedUploadFromTheEarlierEmulatorNeverHidesTheCount() {
        shell.ingestLibrary("romm", listOf(entry(server, "Tetris.gba")), emptyList())
        played += shell.playId(serverId)
        shell.useSavePlayer(serverId, myBoy)
        assertEquals(1, shell.model.savesOnlyHere)
        // GBA.emu's last upload is still waiting: the next save still goes nowhere but this device.
        shell.setUnsyncedSaves("romm", setOf(server.remoteKey))
        assertTrue(shell.saveHealthOf(serverId) is SaveStatus.Waiting)
        shell.notePlayChanged()
        assertEquals(1, shell.model.savesOnlyHere)
        // GBA.emu's upload finishing now can't put "Saves synced" on a game My Boy! plays.
        assertTrue(shell.savesStayHere(server.backendId, server.remoteKey))
        shell.useSavePlayer(serverId, PlayerChoice(gbaEmu, label = "GBA.emu"))
        assertFalse(shell.savesStayHere(server.backendId, server.remoteKey))
    }

    @Test fun aSwitchWhileTheOldEmulatorIsOpenWaitsForItToCloseThenTheSavesPageFollows() {
        shell.ingestLibrary("romm", listOf(entry(server, "Tetris.gba")), emptyList())
        shell.useSavePlayer(serverId, myBoy)
        val myBoyPlayer = plugins.player(myBoy.playerId)!!
        var open: Player? = myBoyPlayer
        shell.runningPlayer = { id -> open.takeIf { id == shell.tileOf(serverId) } }
        val reopened = mutableListOf<SavePickerTarget>()
        shell.onSavePickerPlayerChanged = { reopened += it }
        shell.useSavePlayer(serverId, PlayerChoice(gbaEmu, label = "GBA.emu"))
        val pageTarget = SavePickerTarget(shell.tileOf(serverId), server.backendId, server.remoteKey, myBoy.playerId)
        val route = shell.savePickerRoute(pageTarget)!!
        // Nothing to press again, and the line says when the switch happens.
        assertNull(route.action)
        assertEquals("Once My Boy! closes, GBA.emu plays this game.", route.steps.single().detail)
        assertEquals("Once My Boy! closes, GBA.emu plays this game and keeps its saves on RomM.", route.banner)
        shell.presentSavePicker(SavePickerState(pageTarget, "Tetris", "My Boy!", shell.savePickerHealth(pageTarget), loading = false, route = route))
        assertTrue(reopened.isEmpty())
        open = null
        shell.noteRunningChanged()
        // The whole target, so the reopened page keeps the RomM copy it named.
        assertEquals(listOf(pageTarget), reopened)
    }

    @Test fun onlyLaunchOnlyEmulatorsStillShowTheSettingsCount() {
        val launchOnlyPlugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply { register(LocalFolderEntry()); register(EmulatorsEntry()) }
        val only = ShellController(SessionStore(MemoryPrefs()), launchOnlyPlugins, lastPlayedMillis = { 1L }, playedTiles = { played.toList() }).apply {
            setInstalledApps(emptyList(), launchOnlyPlugins.playerPackageNames(), launchOnlyPlugins.playerPackageNames())
        }
        val dreamcast = Game("local-folder", "content://tree/document/7", "dreamcast", Availability.LocalOnly, "Racer")
        only.ingestLibrary("local-folder", listOf(entry(dreamcast, "Racer.gdi")), emptyList())
        played += only.tileOf(homeGameId(dreamcast.backendId, dreamcast.remoteKey))
        only.notePlayChanged()
        assertEquals(1, only.model.savesOnlyHere)
        assertTrue(emulatorShelf(only.model).isEmpty())
        assertTrue(Row.SaveSetup in settingsRows(SettingsCategory.Players, only.model))
        assertEquals(SaveSetupCopy.onlyOnThisDevice(1), rowText(Row.SaveSetup, only.model).value)
        // The page it opens has no emulator tiles, and ends on what stays here instead of going blank.
        assertTrue(saveSetupShowsEnd(saveSetupTiles(only.model, SaveSetupPage()), SaveSetupPage(), only.model.savesOnlyHere))
        assertEquals(SaveSetupCopy.doneForNow, SaveSetupCopy.endTitle(0, 0, only.model.savesOnlyHere))
        // Its end has a Done that A presses, which closes the page.
        val page = SaveSetupPage()
        val opened = only.model.copy(saveSetup = page)
        assertEquals(listOf(HintKey(PromptKey.FaceA, SaveSetupCopy.done)), saveSetupActions(opened, page))
        assertNull(applySaveSetup(opened, page, Meaning.Activate).first.saveSetup)
    }
}
