package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.language.SaveHealth
import app.foldcade.language.SaveIssue
import app.foldcade.plugins.armsx2.ArmsX2Player
import app.foldcade.plugins.aps3e.Aps3ePlayer
import app.foldcade.plugins.azahar.AzaharPlayer
import app.foldcade.plugins.duckstation.DuckStationPlayer
import app.foldcade.plugins.emulators.EmulatorsEntry
import app.foldcade.plugins.melonds.MelonDsPlayer
import app.foldcade.plugins.retroarch.RetroArchEntry
import org.junit.Assert.*
import org.junit.Test

class GameSaveHealthTest {
    private fun players(): List<Player> = listOf(
        MelonDsPlayer(), AzaharPlayer(), ArmsX2Player(), Aps3ePlayer(), DuckStationPlayer(),
        RetroArchEntry().players.first(),
    )
    private fun game(player: Player) = Game("romm", "42", player.platformId, Availability.Cached, "Game")
    private fun health(player: Player, server: Boolean = true, waiting: Boolean = false,
        sync: SaveSyncState? = null, setup: String? = null, reachable: Boolean? = null) =
        gameSaveHealth(game(player), player, server, waiting, sync, setup, reachable)

    @Test fun everySyncingPlayerWithoutAFolderNeedsAttention() {
        for (player in players()) {
            assertEquals(player.id, SaveHealth.NotSyncing(SaveIssue.NoFolder), health(player))
            // A held save reaching the server would still leave this player's own save unprotected.
            assertEquals(player.id, SaveHealth.NotSyncing(SaveIssue.NoFolder), health(player, waiting = true))
            assertEquals(player.id, SaveHealth.DeviceOnly, health(player, server = false))
        }
    }

    @Test fun configuredPlayersAreFineUntilTheLedgerHasProgressWaiting() {
        for (player in players()) {
            (player as SaveFolderHolder).bindSaveFolder("content://saves")
            (player as? HostedSaveFolder)?.bindHostedFolder("content://hosted", true)
            assertEquals(player.id, SaveHealth.Fine, health(player))
            assertEquals(player.id, SaveHealth.Waiting, health(player, waiting = true))
            assertEquals(player.id, SaveHealth.Fine, health(player, waiting = false))
            assertEquals(player.id, SaveHealth.NotSyncing(SaveIssue.NoSlot), health(player, reachable = false))
            assertEquals(player.id, SaveHealth.Fine, health(player, reachable = true))
        }
    }

    @Test fun hostedGrantAndPlayerSetupMustBothBeReady() {
        val duck = DuckStationPlayer().apply { bindSaveFolder("content://saves") }
        assertEquals(SaveHealth.NotSyncing(SaveIssue.HostedFolder), health(duck))
        duck.bindHostedFolder("content://hosted", false)
        assertEquals(SaveHealth.NotSyncing(SaveIssue.HostedFolder), health(duck))
        duck.bindHostedFolder("content://hosted", true)
        assertEquals(SaveHealth.Fine, health(duck))
        val arms = ArmsX2Player().apply { bindSaveFolder("content://saves") }
        assertEquals(SaveHealth.NotSyncing(SaveIssue.NeedsSetup), health(arms, sync = SaveSyncState.NeedsSetup("Keep the file card")))
        assertEquals(SaveHealth.Fine, health(arms, sync = SaveSyncState.Syncing))
        val ps3 = Aps3ePlayer().apply { bindSaveFolder("content://saves") }
        assertEquals(SaveHealth.NotSyncing(SaveIssue.NeedsSetup), health(ps3, setup = "Install firmware"))
    }

    @Test fun standaloneEmulatorsIncludingEdenAreDeviceOnlyButExternalGamesAreQuiet() {
        val emulators = EmulatorsEntry().players
        assertTrue(emulators.any { it.id.startsWith("eden.") })
        for (player in emulators) {
            assertEquals(player.id, SaveHealth.DeviceOnly, health(player))
            assertEquals(player.id, SaveHealth.DeviceOnly, health(player, server = false))
        }
        for (id in listOf("android", "moonlight", "gamenative", "droiddeck")) {
            val app = object : Player by emulators.first() {
                override val id = id
                override val needsLocalFile = false
            }
            assertEquals(id, SaveHealth.Fine, health(app, waiting = true))
        }
        assertEquals(SaveHealth.Fine, gameSaveHealth(game(emulators.first()), null, true, false))
    }

    @Test fun emptyOrNullDeclarationsFromASyncingPlayerNeverLookFine() {
        val base = ArmsX2Player().apply { bindSaveFolder("content://saves") }
        for (declarations in listOf(emptyList(), listOf(SaveDeclaration("card1", null)))) {
            val player = object : Player by base, SaveFolderHolder by base {
                override fun saveDeclarations(game: Game) = declarations
            }
            assertEquals(SaveHealth.NotSyncing(SaveIssue.NoSlot), health(player))
        }
    }
}
