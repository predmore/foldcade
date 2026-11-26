package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.language.*
import app.foldcade.language.SaveIssue
import app.foldcade.plugins.armsx2.ArmsX2Player
import app.foldcade.plugins.aps3e.Aps3ePlayer
import app.foldcade.plugins.azahar.AzaharPlayer
import app.foldcade.plugins.duckstation.DuckStationPlayer
import app.foldcade.plugins.eden.EdenPlayer
import app.foldcade.plugins.emulators.EmulatorsEntry
import app.foldcade.plugins.melonds.MelonDsPlayer
import app.foldcade.plugins.retroarch.RetroArchEntry
import org.junit.Assert.*
import org.junit.Test

class GameSaveHealthTest {
    private fun players(): List<Player> = listOf(
        MelonDsPlayer(), AzaharPlayer(), ArmsX2Player(), Aps3ePlayer(), DuckStationPlayer(), EdenPlayer(),
        RetroArchEntry().players.first(),
        app.foldcade.plugins.ppsspp.PpssppPlayer(), app.foldcade.plugins.dolphin.DolphinPlayer(),
        *app.foldcade.plugins.explus.ExPlusEntry().players.toTypedArray(),
    )
    private fun game(player: Player) = Game("romm", "42", player.platformId, Availability.Cached, "Game")
    private fun health(player: Player, server: Boolean = true, waiting: Boolean = false,
        sync: SaveSyncState? = null, setup: String? = null, reachable: Boolean? = null) =
        gameSaveHealth(game(player), player, server, waiting, sync, setup, reachable)

    @Test fun everySyncingPlayerWithoutAFolderNeedsAttention() {
        for (player in players()) {
            val issue = if (player is HostedSaveFolder) SaveIssue.HostedFolder else SaveIssue.NoFolder
            assertEquals(player.id, SaveStatus.NotSyncing(issue), health(player))
            // A held save reaching the server would still leave this player's own save unprotected.
            assertEquals(player.id, SaveStatus.NotSyncing(issue), health(player, waiting = true))
            // A folder game nobody has played here: the line is amber, the tile stays plain (§10.1).
            assertEquals(player.id, SaveStatus.NotSyncing(issue, badge = false), health(player, server = false))
            assertFalse(health(player, server = false).needsAttention)
            assertTrue(health(player, server = false).attentionLine)
            assertEquals(player.id, SaveStatus.NotSyncing(issue), gameSaveHealth(game(player), player, false, false, playedHere = true))
        }
    }

    @Test fun aFileTypeThatCanNeverSyncIsKnownBeforeTheFirstLaunch() {
        val azahar = AzaharPlayer().apply { bindSaveFolder("content://saves") }
        // A valid .zcci names no title: its save has no place, whatever a launch reads later.
        assertEquals(SaveStatus.NotSyncing(spot = SpotIssue.NoTitleId, detail = SaveRouteCopy.zcci),
            gameSaveHealth(game(azahar), azahar, true, false, fileName = "Picross.zcci"))
        // After a launch reads it, the same words, not the generic header sentence.
        assertEquals(SaveStatus.NotSyncing(SaveIssue.NoSlot, SpotIssue.NoTitleId, detail = SaveRouteCopy.zcci),
            gameSaveHealth(game(azahar), azahar, true, false, reachable = false, fileName = "Picross.zcci"))
        // A .cci waits for its header as before.
        assertEquals(SaveStatus.Unconfirmed(UnconfirmedWhy.FolderNotChecked), gameSaveHealth(game(azahar), azahar, true, false, fileName = "Picross.cci"))
        val dolphin = app.foldcade.plugins.dolphin.DolphinPlayer().apply { bindSaveFolder("content://saves") }
        assertEquals(SpotIssue.UnsupportedImage, (gameSaveHealth(game(dolphin), dolphin, true, false, fileName = "Wind.rvz") as SaveStatus.NotSyncing).spot)
        assertEquals(SaveStatus.Unconfirmed(UnconfirmedWhy.FolderNotChecked), gameSaveHealth(game(dolphin), dolphin, true, false, fileName = "Wind.iso"))
        // A folder game nobody has played: no badge, and its Saves page says why before any launch.
        val local = gameSaveHealth(game(azahar).copy(backendId = "local-folder", availability = Availability.LocalOnly), azahar, false, false, fileName = "Picross.zcci")
        assertFalse(local.needsAttention)
        val route = saveRoute(SaveRouteFacts(local, onRomm = false, rommConnected = true, player = "Azahar", playerSyncs = true, playerId = "azahar"))!!
        assertEquals(SaveRouteStep(SaveRouteCopy.stepSpot, "Use a .cci copy of this game instead. Foldcade can’t sync .zcci files yet.", SaveRouteMark.Now),
            route.steps[1])
        // No promise that the next play puts an unsyncable file's save on RomM.
        assertNull(route.note)
    }

    @Test fun configuredPlayersAreFineUntilTheLedgerHasProgressWaiting() {
        for (player in players()) {
            (player as SaveFolderHolder).bindSaveFolder("content://saves")
            (player as? HostedSaveFolder)?.bindHostedFolder("content://hosted", true)
            assertEquals(player.id, SaveStatus.Unconfirmed(app.foldcade.language.UnconfirmedWhy.FolderNotChecked), health(player))
            assertEquals(player.id, SaveStatus.Waiting(0), health(player, waiting = true))
            assertEquals(player.id, SaveStatus.Unconfirmed(app.foldcade.language.UnconfirmedWhy.FolderNotChecked), health(player, waiting = false))
            assertEquals(player.id, SaveStatus.NotSyncing(SaveIssue.NoSlot, SpotIssue.NoTitleId), health(player, reachable = false))
            assertEquals(player.id, SaveStatus.Unconfirmed(app.foldcade.language.UnconfirmedWhy.FolderNotChecked), health(player, reachable = true))
        }
    }

    @Test fun edenWithAGrantWaitsForAnActualUnreachableCheckBeforeShowingNoSlot() {
        val player = EdenPlayer()
        assertEquals(SaveStatus.NotSyncing(SaveIssue.NoFolder), health(player))
        player.bindSaveFolder("content://eden/tree/root")
        assertEquals(SaveStatus.Unconfirmed(app.foldcade.language.UnconfirmedWhy.FolderNotChecked), health(player))
        assertEquals(SaveStatus.Waiting(0), health(player, waiting = true))
        assertEquals(SaveStatus.NotSyncing(SaveIssue.NoSlot, SpotIssue.NoTitleId), health(player, reachable = false))
        assertEquals(SaveStatus.Unconfirmed(app.foldcade.language.UnconfirmedWhy.FolderNotChecked), health(player, reachable = true))
        assertEquals(SaveStatus.Waiting(0), health(player, reachable = true, waiting = true))
        player.bindSaveFolder("")
        assertEquals(SaveStatus.NotSyncing(SaveIssue.NoFolder), health(player))
    }

    @Test fun hostedGrantAndPlayerSetupMustBothBeReady() {
        val duck = DuckStationPlayer().apply { bindSaveFolder("content://saves") }
        assertEquals(SaveStatus.NotSyncing(SaveIssue.HostedFolder), health(duck))
        duck.bindHostedFolder("content://hosted", false)
        assertEquals(SaveStatus.NotSyncing(SaveIssue.HostedFolder), health(duck))
        duck.bindHostedFolder("content://hosted", true)
        assertEquals(SaveStatus.Unconfirmed(app.foldcade.language.UnconfirmedWhy.FolderNotChecked), health(duck))
        val arms = ArmsX2Player().apply { bindSaveFolder("content://saves") }
        assertEquals(SaveStatus.NotSyncing(SaveIssue.NeedsSetup), health(arms, sync = SaveSyncState.NeedsSetup("Keep the file card")))
        assertEquals(SaveStatus.Unconfirmed(app.foldcade.language.UnconfirmedWhy.FolderNotChecked), health(arms, sync = SaveSyncState.Syncing))
        val ps3 = Aps3ePlayer().apply { bindSaveFolder("content://saves") }
        assertEquals(SaveStatus.NotSyncing(SaveIssue.NeedsSetup), health(ps3, setup = "Install firmware"))
    }

    @Test fun hostedCardsSyncWithoutADataFolder() {
        val duck = DuckStationPlayer().apply { bindHostedFolder("content://hosted", true) }
        assertFalse(duck.hasSaveFolder())
        assertEquals(SaveStatus.Unconfirmed(app.foldcade.language.UnconfirmedWhy.FolderNotChecked), health(duck))
        assertEquals(SaveStatus.Waiting(0), health(duck, waiting = true))
        duck.bindHostedFolder("content://hosted", false)
        assertEquals(SaveStatus.NotSyncing(SaveIssue.HostedFolder), health(duck))
    }

    @Test fun aPastLocationCheckCannotHideNewSetupTrouble() {
        val player = ArmsX2Player().apply { bindSaveFolder("content://saves") }
        val verified = LocationFact.Verified(FolderRef("primary:saves", "saves"), Evidence(EvidenceKind.SessionWrite, "Game saved"), 1)
        val status = gameSaveHealth(game(player), player, true, false, sync = SaveSyncState.NeedsSetup("Use a folder card"), location = verified)
        assertEquals(SaveStatus.NotSyncing(SaveIssue.NeedsSetup), status)
    }

    @Test fun onlySettledActivityRefreshesTheLedger() {
        for (outcome in SyncOutcome.entries) assertTrue(GameActivity.Synced(SyncResult(outcome)).refreshesSaveHealth())
        assertTrue(GameActivity.Idle.refreshesSaveHealth())
        for (activity in listOf(GameActivity.SyncingSaves, GameActivity.Checking,
            GameActivity.Downloading(1, 2), GameActivity.GettingFirmware(1, 2))) {
            assertFalse(activity.refreshesSaveHealth())
        }
    }

    @Test fun standaloneEmulatorsAreDeviceOnlyButExternalGamesAreQuiet() {
        val emulators = EmulatorsEntry().players
        for (player in emulators) {
            assertEquals(player.id, SaveStatus.DeviceOnly(NoDestinationWhy.PlayerCantSync, emulator = player.displayName), health(player))
            assertEquals(player.id, SaveStatus.DeviceOnly(NoDestinationWhy.PlayerCantSync, emulator = player.displayName), health(player, server = false))
        }
        for (id in listOf("android", "moonlight", "gamenative", "droiddeck")) {
            val app = object : Player by emulators.first() {
                override val id = id
                override val needsLocalFile = false
            }
            assertEquals(id, SaveStatus.Waiting(0), health(app, waiting = true))
        }
        assertEquals(SaveStatus.Unconfirmed(UnconfirmedWhy.NoPlayer), gameSaveHealth(game(emulators.first()), null, true, false))
    }

    @Test fun anApkStreamingAdapterIsTheHostComputerByItsGamesBackend() {
        val apk = object : Player by EmulatorsEntry().players.first() {
            override val id = "apk:com.example.stream"
            override val needsLocalFile = false
        }
        val stream = Game("moonlight", "host:app", "moonlight", Availability.Cached, "Desktop")
        assertEquals(SaveStatus.Unconfirmed(UnconfirmedWhy.HostComputer), gameSaveHealth(stream, apk, true, false))
    }

    @Test fun whereASaveCanGoOutranksAnyReceipt() {
        val melon = MelonDsPlayer().apply { bindSaveFolder("content://saves") }
        val verified = LocationFact.Verified(FolderRef("primary:saves", "saves"), Evidence(EvidenceKind.SessionWrite, "Game saved"), 1)
        // A folder game's own library can report "nothing yet" or a matching hash: neither is RomM.
        for (receipt in listOf(OffDeviceFact.NothingYet, OffDeviceFact.OnRomm(1), OffDeviceFact.Unknown)) {
            assertEquals(receipt.toString(), SaveStatus.DeviceOnly(NoDestinationWhy.NotOnRomm, emulator = "melonDS"),
                gameSaveHealth(game(melon), melon, false, false, reachable = true, location = verified, offDevice = receipt))
        }
        // A launch-only emulator on a signed-out server still can't sync: it says so, not "folder not checked".
        val flycast = EmulatorsEntry().players.first { it.displayName == "Flycast" }
        assertEquals(SaveStatus.DeviceOnly(NoDestinationWhy.PlayerCantSync, emulator = "Flycast"),
            gameSaveHealth(game(flycast), flycast, true, false, offDevice = OffDeviceFact.NoDestination(NoDestinationWhy.SignedOut)))
        // On a RomM game the receipt still decides.
        assertEquals(SaveStatus.Protected(1, 1), gameSaveHealth(game(melon), melon, true, false, reachable = true, location = verified, offDevice = OffDeviceFact.OnRomm(1)))
    }

    @Test fun emptyOrNullDeclarationsFromASyncingPlayerNeverLookFine() {
        val base = ArmsX2Player().apply { bindSaveFolder("content://saves") }
        for (declarations in listOf(emptyList(), listOf(SaveDeclaration("card1", null)))) {
            val player = object : Player by base, SaveFolderHolder by base {
                override fun saveDeclarations(game: Game) = declarations
            }
            assertEquals(SaveStatus.NotSyncing(SaveIssue.NoSlot), health(player))
        }
    }

    @Test fun saveCapabilitySurvivesTheReachableSlotWrapper() {
        for (player in players()) {
            val game = game(player)
            val wrapped = playerForSaveSpots(player, game, emptyList())
            assertTrue(player.id, player.supportsSaveSync(game))
            assertTrue(player.id, wrapped.supportsSaveSync(game))
            assertFalse(wrapped is SaveFolderHolder)
            val issue = if (player is HostedSaveFolder) SaveIssue.HostedFolder else SaveIssue.NoFolder
            assertEquals(SaveStatus.NotSyncing(issue), health(player))
        }
        for (player in EmulatorsEntry().players) {
            val game = game(player)
            assertFalse(player.id, playerForSaveSpots(player, game, emptyList()).supportsSaveSync(game))
        }
        val explicitSupport = object : Player by EmulatorsEntry().players.first() {
            override fun supportsSaveSync(game: Game) = true
        }
        assertEquals(SaveStatus.NotSyncing(SaveIssue.NoSlot), health(explicitSupport))
    }
}
