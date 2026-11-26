package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

/** Every device-only case in design §7.1, with the exact words each surface shows. */
class SaveRouteTest {
    private val gbaEmu = PlayerChoice("gba-emu", label = "GBA.emu")
    private val folderGame = SaveStatus.DeviceOnly(NoDestinationWhy.NotOnRomm, playedHere = true, emulator = "GBA.emu")
    private val launchOnly = SaveStatus.DeviceOnly(NoDestinationWhy.PlayerCantSync, playedHere = true, emulator = "My Boy!")

    private fun facts(status: SaveStatus, onRomm: Boolean = false, player: String? = "GBA.emu", syncs: Boolean = true,
        supported: PlayerChoice? = null, installable: String? = null, connected: Boolean = true) = SaveRouteFacts(
        status, onRomm, connected, player, syncs, supported, installable, fileName = "Puzzle.gba", system = "Game Boy Advance",
    )

    @Test fun aRommGameWithAnEmulatorThatSyncsNeedsNoRoute() {
        for (status in listOf(SaveStatus.Protected(1, 1), SaveStatus.NothingYet, SaveStatus.Unconfirmed(UnconfirmedWhy.FolderNotChecked),
            SaveStatus.NotSyncing(SaveIssue.NoFolder), SaveStatus.Waiting(0))) {
            assertNull(status.toString(), saveRoute(facts(status, onRomm = true)))
        }
    }

    @Test fun aProblemKeepsItsOwnFixRoute() {
        val incident = SaveIncident("WrongFolder:gba", IncidentKind.WrongFolder, "gba", "GBA.emu", emptyList(), null, null,
            Evidence(EvidenceKind.SessionWrite, ""), 0)
        assertNull(saveRoute(facts(SaveStatus.Problem(incident))))
        assertNull(saveRoute(facts(SaveStatus.Problem(incident), onRomm = true, syncs = false)))
    }

    @Test fun aFolderGameWithAnEmulatorThatSyncsAddsItToRommUnderTheSameName() {
        val route = saveRoute(facts(folderGame))!!
        assertEquals("RomM doesn’t have this game, so its saves stay on this device.", route.summary)
        assertEquals(listOf(
            SaveRouteStep("Play with an emulator Foldcade syncs", "Foldcade syncs GBA.emu’s saves.", SaveRouteMark.Done),
            SaveRouteStep("Add this game to RomM", "Upload Puzzle.gba for Game Boy Advance, with the same file name.", SaveRouteMark.Now),
        ), route.steps)
        assertEquals("The next time you play, Foldcade puts this save on RomM. If RomM already has a different one, Foldcade keeps both and asks which to play first.", route.note)
        // Foldcade can't upload a game, so A has nothing to press.
        assertNull(route.action)
    }

    @Test fun signedOutTheRommStepSaysWhereToSignInFirst() {
        val route = saveRoute(facts(folderGame, connected = false))!!
        assertEquals(SaveRouteStep("Sign in to RomM, then add this game",
            "Settings › Library › Add › RomM server. Then upload Puzzle.gba for Game Boy Advance, with the same file name.", SaveRouteMark.Now), route.steps.last())
    }

    @Test fun withoutAFileNameTheRommStepStillNamesTheRule() {
        assertEquals("Upload it with the same file name and system.", SaveRouteCopy.rommDetail(null, null, true))
        assertEquals("Upload Puzzle.gba, with the same file name.", SaveRouteCopy.rommDetail("Puzzle.gba", null, true))
    }

    @Test fun aFolderGameWhoseEmulatorIsNotSetUpSetsItUpBeforeRomm() {
        val route = saveRoute(facts(SaveStatus.NotSyncing(SaveIssue.NoFolder)))!!
        assertEquals("RomM doesn’t have this game, so its saves stay on this device.", route.summary)
        assertEquals(listOf(SaveRouteMark.Done, SaveRouteMark.Now, SaveRouteMark.Next), route.steps.map { it.mark })
        assertEquals(SaveRouteStep("Set up GBA.emu’s saves", null, SaveRouteMark.Now, SaveRouteAction.SetUp), route.steps[1])
        assertEquals(SaveRouteAction.SetUp, route.action)
        assertTrue(savesOnlyHere(emulator = true, syncs = true, onRomm = false))
    }

    @Test fun aGameFileThatCantSyncSaysWhyWithNothingToPress() {
        val step = saveRoute(facts(SaveStatus.NotSyncing(SaveIssue.NoSlot, SpotIssue.NoTitleId)).copy(playerId = "azahar", player = "Azahar"))!!.steps[1]
        assertEquals(SaveRouteStep("Use a game file that syncs", "This game file doesn’t say which 3DS game it is. A .cci syncs; a .zcci doesn’t.", SaveRouteMark.Now), step)
    }

    @Test fun aLaunchOnlyEmulatorOnARommGameOffersOneThatSyncs() {
        val route = saveRoute(facts(launchOnly, onRomm = true, player = "My Boy!", syncs = false, supported = gbaEmu))!!
        assertEquals("Foldcade can’t sync My Boy!, so its saves stay on this device.", route.summary)
        assertEquals(listOf(SaveRouteStep("Play with GBA.emu", "Foldcade syncs GBA.emu’s saves.", SaveRouteMark.Now, SaveRouteAction.UsePlayer(gbaEmu))), route.steps)
        assertEquals("My Boy! keeps the saves made in it. Foldcade can’t move them.", route.note)
        assertEquals(SaveRouteAction.UsePlayer(gbaEmu), route.action)
        assertEquals("Foldcade can’t sync My Boy!, so play with GBA.emu to keep these saves on RomM.", SaveRouteCopy.banner("My Boy!", "GBA.emu"))
        assertEquals("My Boy! can’t load RomM saves. Play with GBA.emu first.", SaveRouteCopy.cantLoad("My Boy!", "GBA.emu"))
    }

    @Test fun aLaunchOnlyEmulatorOnAFolderGameSwitchesThenAddsItToRomm() {
        val route = saveRoute(facts(launchOnly, player = "My Boy!", syncs = false, supported = gbaEmu))!!
        assertEquals(listOf("Play with GBA.emu", "Add this game to RomM"), route.steps.map { it.title })
        assertEquals(listOf(SaveRouteMark.Now, SaveRouteMark.Next), route.steps.map { it.mark })
        // The switch is the thing to say about saves already made.
        assertEquals("My Boy! keeps the saves made in it. Foldcade can’t move them.", route.note)
    }

    @Test fun anEmulatorThatSyncsButIsNotInstalledIsNamedWithoutAButton() {
        val route = saveRoute(facts(launchOnly, onRomm = true, player = "My Boy!", syncs = false, installable = "GBA.emu"))!!
        assertEquals(listOf(SaveRouteStep("Install GBA.emu", "Foldcade syncs GBA.emu’s saves for Game Boy Advance.", SaveRouteMark.Now)), route.steps)
        assertNull(route.action)
        assertEquals("Foldcade can’t sync My Boy!, so play with GBA.emu to keep these saves on RomM.", SaveRouteCopy.banner("My Boy!", "GBA.emu"))
    }

    @Test fun withNoEmulatorThatSyncsForTheSystemThePageOnlySaysSo() {
        val flycast = SaveStatus.DeviceOnly(NoDestinationWhy.PlayerCantSync, emulator = "Flycast")
        val onRomm = saveRoute(facts(flycast, onRomm = true, player = "Flycast", syncs = false))!!
        assertEquals(SaveRoute("Foldcade can’t sync Flycast yet, so its saves stay on this device.", emptyList(),
            banner = "Foldcade can’t sync Flycast yet, so its saves stay on this device."), onRomm)
        assertEquals("Foldcade can’t sync Flycast yet, so its saves stay on this device.", SaveRouteCopy.banner("Flycast", null))
        val folder = saveRoute(facts(flycast, player = "Flycast", syncs = false))!!
        assertEquals("RomM doesn’t have this game either.", folder.note)
        assertTrue(folder.steps.isEmpty())
    }

    @Test fun aFolderGameWithNoEmulatorInstalledNamesOneToInstall() {
        val route = saveRoute(facts(SaveStatus.Unconfirmed(UnconfirmedWhy.NoPlayer), player = null, syncs = false, installable = "GBA.emu"))!!
        assertEquals("No emulator Foldcade syncs is installed for this game.", route.summary)
        assertEquals(listOf("Install GBA.emu", "Add this game to RomM"), route.steps.map { it.title })
        assertNull(saveRoute(facts(SaveStatus.Unconfirmed(UnconfirmedWhy.NoPlayer), onRomm = true, player = null, syncs = false)))
    }

    @Test fun partnersAreOnlyEverUnconfirmed() {
        fun partner(name: String, cloud: String?) = saveRoute(facts(SaveStatus.Unconfirmed(UnconfirmedWhy.PartnerCloud,
            keeper = SaveKeeper.Partner(name, cloud)), player = name, syncs = false))!!
        val steam = partner("DroidDeck", "Steam Cloud")
        assertEquals("Steam Cloud keeps these saves when it’s on for the game. Foldcade can’t check it yet.", steam.summary)
        assertTrue(steam.steps.isEmpty())
        assertNull(steam.note)
        assertEquals("GameNative keeps these saves. Foldcade can’t check its cloud yet.", partner("GameNative", null).summary)
        // No sentence claims a partner's cloud holds a save: there is no receipt (§8.3).
        for (route in listOf(steam, partner("GameNative", null))) assertTrue(route.summary.contains("can’t check"))
        assertEquals("These saves stay on your computer, or with the game’s own cloud.",
            saveRoute(facts(SaveStatus.Unconfirmed(UnconfirmedWhy.HostComputer), player = "Moonlight", syncs = false))!!.summary)
        assertEquals("This app keeps its own saves. Foldcade can’t see them.",
            saveRoute(facts(SaveStatus.Unconfirmed(UnconfirmedWhy.AppKeepsSaves), player = null, syncs = false))!!.summary)
    }

    @Test fun theSwitchIsAskedFirstAndSaysWhatStaysBehind() {
        assertEquals("Play with GBA.emu?", SaveRouteCopy.switchTitle("GBA.emu"))
        assertEquals("Foldcade syncs GBA.emu’s saves. My Boy! keeps the saves made in it. Foldcade can’t move them. Change it any time in Y › Open with.",
            SaveRouteCopy.switchBody("GBA.emu", "My Boy!", playedHere = true))
        assertEquals("Foldcade syncs GBA.emu’s saves. Change it any time in Y › Open with.", SaveRouteCopy.switchBody("GBA.emu", "My Boy!", playedHere = false))
        assertEquals("GBA.emu plays this game now.", SaveRouteCopy.switched("GBA.emu"))
    }

    @Test fun theCountIsWhereTheNextSaveGoesNotWhatIsQueued() {
        // A folder game, set up or not, and a launch-only emulator on a RomM game.
        assertTrue(savesOnlyHere(emulator = true, syncs = true, onRomm = false))
        assertTrue(savesOnlyHere(emulator = true, syncs = false, onRomm = true))
        // A RomM game with an emulator that syncs, and partners, streams and apps, are not counted.
        assertFalse(savesOnlyHere(emulator = true, syncs = true, onRomm = true))
        assertFalse(savesOnlyHere(emulator = false, syncs = false, onRomm = false))
    }

    @Test fun theBannerOverARommListSaysTheWayOn() {
        fun banner(supported: PlayerChoice? = null, installable: String? = null, pending: Boolean = false) =
            saveRoute(facts(launchOnly, onRomm = true, player = "My Boy!", syncs = false, supported = supported, installable = installable)
                .copy(switchPending = pending))!!.banner
        assertEquals("Foldcade can’t sync My Boy!, so play with GBA.emu to keep these saves on RomM.", banner(supported = gbaEmu))
        assertEquals("Foldcade can’t sync My Boy!, so install GBA.emu to keep these saves on RomM.", banner(installable = "GBA.emu"))
        assertEquals("Foldcade can’t sync My Boy! yet, so its saves stay on this device.", banner())
        assertEquals("Once My Boy! closes, GBA.emu plays this game and keeps its saves on RomM.", banner(supported = gbaEmu, pending = true))
        assertNull(saveRoute(facts(folderGame))!!.banner)
    }

    @Test fun aSwitchMadeWhileTheOldEmulatorIsOpenOffersNothingAgain() {
        val route = saveRoute(facts(launchOnly, onRomm = true, player = "My Boy!", syncs = false, supported = gbaEmu).copy(switchPending = true))!!
        assertEquals(listOf(SaveRouteStep("Play with GBA.emu", "Once My Boy! closes, GBA.emu plays this game.", SaveRouteMark.Now)), route.steps)
        assertNull(route.action)
    }

    @Test fun aFinishedUploadFromAnEarlierEmulatorNeverMarksADeviceOnlyGameSynced() {
        val synced = TileActivity.Synced(SyncMark.Synced, 10_000)
        assertNull(shownActivity(launchOnly, synced))
        assertEquals(TileActivity.Syncing, shownActivity(launchOnly, TileActivity.Syncing))
        assertEquals(synced, shownActivity(SaveStatus.Protected(1, 1), synced))
        val hero = heroFactText(HeroSubject.Item(HeroItem("g", "Puzzle", "")), launchOnly, synced)
        // The device-only line leads, and no "synced" from the earlier emulator sits beside it.
        assertEquals("My Boy! saves only on this device", hero.save)
        assertNull(hero.live)
    }

    @Test fun aNeverPlayedFolderGameHasAnAmberLineButNoBadge() {
        val unplayed = SaveStatus.DeviceOnly(NoDestinationWhy.NotOnRomm, playedHere = false)
        assertFalse(unplayed.needsAttention)
        assertTrue(unplayed.attentionLine)
        assertEquals("Saves only on this device", saveStatusLine(unplayed))
        assertEquals("My Boy! saves only on this device", saveStatusLine(launchOnly))
    }

    @Test fun setUpSavesNeverSaysAllSetWhilePlayedGamesStayHere() {
        assertEquals("2 games only on this device", SaveSetupCopy.head(0, 0, onlyHere = 2))
        assertEquals("1 game only on this device", SaveSetupCopy.onlyOnThisDevice(1))
        assertEquals("3 to go", SaveSetupCopy.head(3, 0, onlyHere = 2))
        assertEquals(SaveSetupCopy.allSet, SaveSetupCopy.head(0, 0))
        assertEquals(SaveSetupCopy.doneForNow, SaveSetupCopy.endTitle(0, 0, onlyHere = 1))
        assertEquals(SaveSetupCopy.allSet, SaveSetupCopy.endTitle(0, 0))
        assertEquals("1 game only on this device. Y › Saves on each game shows how to keep it on RomM.", SaveSetupCopy.endBody(0, 0, onlyHere = 1))
        assertEquals("1 game only on this device. Y › Saves on each game shows how to keep it on RomM.", SaveSetupCopy.endBody(0, 4, onlyHere = 1))
        assertNull(SaveSetupCopy.endBody(0, 0))
    }

    @Test fun aFirstLinkStopSaysWhatEachCopyIsAndThatNothingIsPlacedFirst() {
        val incident = SaveIncident("MaybeNewSave:melonds:romm:12", IncidentKind.MaybeNewSave, "melonds", "melonDS", listOf("12"), null, null,
            Evidence(EvidenceKind.SessionWrite, SaveProtectionCopy.firstLinkDetail), 0, repair = RepairState.Keeping,
            subject = SaveIncidentGame("romm", "12", "nintendo-ds", "Puzzle", "melonds", "content://rf4", "https://romm.test", null, null), firstLink = true)
        assertEquals("Two saves for Puzzle", SaveProtectionCopy.title(incident))
        assertEquals("This Thor has a save RomM hasn’t seen, and RomM has a different one. Foldcade keeps both on RomM, then you choose which to play. " +
            "Choose first, or this session’s progress stays on this device.", SaveProtectionCopy.body(incident, hasVersions = true, stop = true))
        assertEquals("This Thor has a save RomM hasn’t seen, and RomM has a different one. Foldcade keeps both on RomM, then you choose which to play.",
            SaveProtectionCopy.body(incident, hasVersions = true))
        val dialog = saveProtectionDialog(incident, HostScreen.Bottom)
        assertEquals(listOf(DialogButton.ChooseSaves, DialogButton.PlayWithoutSyncing), dialog.buttons)
        assertEquals("Choose save", dialogButtonLabel(DialogButton.ChooseSaves))
        assertEquals("Two saves. Choose which to play.", SaveProtectionCopy.noticeBody(incident))
        // It doesn't stop by kind: the launch hook stops it, so other games and Maybe* rules are unchanged.
        assertFalse(incident.blocksLaunch)
    }
}
