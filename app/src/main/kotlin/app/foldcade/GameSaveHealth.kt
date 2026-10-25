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

/** Reads only player declarations and the host's cached checks; never reads a card on a frame. */
internal fun gameSaveHealth(
    game: Game,
    player: Player?,
    onServer: Boolean,
    waiting: Boolean,
    sync: SaveSyncState? = null,
    setup: String? = null,
    reachable: Boolean? = null,
): SaveHealth {
    if (player == null) return SaveHealth.Fine
    val holder = player as? SaveFolderHolder
    val hosted = player as? HostedSaveFolder
    val (declarations, supportsSync) = try {
        player.saveDeclarations(game) to player.supportsSaveSync(game)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList<SaveDeclaration>() to (holder != null || hosted != null)
    }
    // File-opening players are emulators in the bundled plugins. App/streaming/PC
    // players do not open ROM files. EmulatorPlayer also covers import-only emulators.
    val emulator = player.needsLocalFile || player is EmulatorPlayer || supportsSync
    val issue = when {
        holder != null && !holder.hasSaveFolder() && hosted?.hostedFolderInUse() != true -> SaveIssue.NoFolder
        hosted != null && !hosted.hostedFolderInUse() -> SaveIssue.HostedFolder
        sync is SaveSyncState.NeedsSetup || setup != null -> SaveIssue.NeedsSetup
        supportsSync && (declarations.isEmpty() || declarations.any { it.locationUri == null } || reachable == false) -> SaveIssue.NoSlot
        else -> null
    }
    return deriveSaveHealth(emulator, onServer, supportsSync, issue, waiting)
}

/** Read the ledger after work settles, rather than competing with an active sync's ROM lock. */
internal fun GameActivity.refreshesSaveHealth(): Boolean = this is GameActivity.Synced || this == GameActivity.Idle
