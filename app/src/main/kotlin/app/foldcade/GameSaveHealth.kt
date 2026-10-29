package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.HostedSaveFolder
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveSyncState
import app.foldcade.language.SaveHealth
import app.foldcade.language.SaveIssue
import app.foldcade.language.deriveSaveHealth
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
): SaveHealth {
    if (player == null) return SaveHealth.Fine
    val (declarations, supportsSync) = saves
    // File-opening players are emulators in the bundled plugins. App/streaming/PC
    // players do not open ROM files. EmulatorPlayer also covers import-only emulators.
    val emulator = player.needsLocalFile || player is EmulatorPlayer || supportsSync
    val issue = playerSaveIssue(player, sync, setup) ?: SaveIssue.NoSlot.takeIf {
        supportsSync && (declarations.isEmpty() || declarations.any { it.locationUri == null } || reachable == false || (player is app.foldcade.plugins.eden.EdenPlayer && reachable != true))
    }
    return deriveSaveHealth(emulator, onServer, supportsSync, issue, waiting)
}

/** Read the ledger after work settles, rather than competing with an active sync's ROM lock. */
internal fun GameActivity.refreshesSaveHealth(): Boolean = this is GameActivity.Synced || this == GameActivity.Idle
