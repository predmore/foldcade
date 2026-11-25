package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.HostedSaveFolder
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveSyncState
import app.foldcade.language.SaveStatus
import app.foldcade.language.SaveIssue
import app.foldcade.language.*
import app.foldcade.plugins.emulators.EmulatorPlayer
import kotlin.coroutines.cancellation.CancellationException

/** Player-level setup, independent of the file format or a particular game's slots. */
internal fun playerSaveIssue(player: Player?, sync: SaveSyncState?, setup: String?): SaveIssue? = when {
    player is HostedSaveFolder && !player.hostedFolderInUse() -> SaveIssue.HostedFolder
    player is SaveFolderHolder && player !is HostedSaveFolder && !player.hasSaveFolder() -> SaveIssue.NoFolder
    sync is SaveSyncState.NeedsSetup || setup != null -> SaveIssue.NeedsSetup
    else -> null
}

internal data class GameSaveSupport(val declarations: List<SaveDeclaration>, val supportsSync: Boolean)

/** Pure plugin metadata, cached by the shell per player and game. */
internal fun readGameSaveSupport(game: Game, player: Player?): GameSaveSupport = try {
    GameSaveSupport(player?.saveDeclarations(game).orEmpty(), player?.supportsSaveSync(game) == true)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    GameSaveSupport(emptyList(), player is SaveFolderHolder || player is HostedSaveFolder)
}

/** Reads only player declarations and the host's cached checks; never reads a card on a frame. */
internal fun gameSaveHealth(
    game: Game,
    player: Player?,
    onServer: Boolean,
    waiting: Boolean,
    sync: SaveSyncState? = null,
    setup: String? = null,
    reachable: Boolean? = null,
    saves: GameSaveSupport = readGameSaveSupport(game, player),
    location: LocationFact? = null,
    offDevice: OffDeviceFact? = null,
    incidents: List<SaveIncident> = emptyList(),
    playedHere: Boolean = false,
    waitingSinceMillis: Long? = null,
    spotIssue: SpotIssue? = null,
    spotDetail: String? = null,
): SaveStatus {
    val (declarations, supportsSync) = saves
    val emulator = player != null && (player.needsLocalFile || player is EmulatorPlayer || supportsSync)
    val keeper = when {
        player == null -> SaveKeeper.NoPlayer
        emulator && supportsSync -> SaveKeeper.Emulator(player.displayName)
        emulator -> SaveKeeper.LaunchOnly(player.displayName)
        player.id.startsWith("droiddeck") -> SaveKeeper.Partner("DroidDeck", "Steam Cloud")
        player.id.startsWith("gamenative") -> SaveKeeper.Partner("GameNative", null)
        player.id.contains("moonlight") -> SaveKeeper.HostComputer
        else -> SaveKeeper.App
    }
    val issue = playerSaveIssue(player, sync, setup) ?: SaveIssue.NoSlot.takeIf {
        supportsSync && (declarations.isEmpty() || declarations.any { it.locationUri == null } || reachable == false)
    }
    val bound = declarations.firstOrNull()?.locationUri?.let { FolderRef(storageIdentity(it), runCatching { folderPathLabel(it) }.getOrDefault(it)) }
    val location = if (issue != null) LocationFact.Missing(issue) else location ?: when {
        bound != null -> LocationFact.Bound(bound)
        else -> LocationFact.NotApplicable
    }
    val spot = if (reachable == false) SpotFact.Unresolved(spotIssue ?: SpotIssue.NoTitleId, spotDetail) else if (reachable == true) SpotFact.Resolved else SpotFact.Unchecked
    val destination = offDevice ?: when {
        !supportsSync && emulator -> OffDeviceFact.NoDestination(NoDestinationWhy.PlayerCantSync)
        !onServer && emulator -> OffDeviceFact.NoDestination(NoDestinationWhy.NotOnRomm)
        else -> OffDeviceFact.Unknown
    }
    return deriveSaveStatus(SaveFacts(keeper, location, spot, destination,
        if (waiting) waitingSinceMillis ?: 0 else null, incidents, playedHere))
}

/** Read the ledger after work settles, rather than competing with an active sync's ROM lock. */
internal fun GameActivity.refreshesSaveHealth(): Boolean = this is GameActivity.Synced || this == GameActivity.Idle
