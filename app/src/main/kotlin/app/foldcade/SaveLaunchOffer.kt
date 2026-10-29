package app.foldcade

import app.foldcade.api.plugin.HostedSaveFolder
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SaveSyncState
import app.foldcade.plugins.eden.EdenPlayer

/** Eden's per-launch opt-out skips negotiation and read-back, even with a held grant. */
internal fun saveSyncForLaunch(player: Player, declinedFor: String?): Boolean = player !is EdenPlayer || declinedFor != player.id

/** A choice for this launch only. The caller passes the declining player's id on its retry. */
internal sealed interface SaveLaunchOffer {
    data object Folder : SaveLaunchOffer
    data class Setup(val steps: String) : SaveLaunchOffer
}

internal enum class SaveFolderAnswer { Chosen, PlayWithoutSyncing, PickerCancelled }

internal sealed interface SaveLaunchContinuation {
    data class Launch(val declinedFor: String?) : SaveLaunchContinuation
    data object Cancel : SaveLaunchContinuation
}

/** A picked folder still lets the hosted/card setup ask run; both decline paths bypass only this player. */
internal fun saveLaunchContinuation(playerId: String, answer: SaveFolderAnswer, needsFolderToStart: Boolean): SaveLaunchContinuation = when {
    answer == SaveFolderAnswer.Chosen -> SaveLaunchContinuation.Launch(null)
    needsFolderToStart -> SaveLaunchContinuation.Cancel
    else -> SaveLaunchContinuation.Launch(playerId)
}

internal fun saveLaunchOffer(
    player: Player,
    onServer: Boolean,
    declinedFor: String? = null,
    sync: SaveSyncState? = null,
): SaveLaunchOffer? = when {
    !onServer || declinedFor == player.id -> null
    player is HostedSaveFolder && !player.hostedFolderInUse() -> SaveLaunchOffer.Setup(player.hostedFolderSteps)
    player is SaveFolderHolder && player !is HostedSaveFolder && !player.hasSaveFolder() -> SaveLaunchOffer.Folder
    sync is SaveSyncState.NeedsSetup -> SaveLaunchOffer.Setup(sync.steps)
    else -> null
}
