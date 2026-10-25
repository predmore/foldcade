package app.foldcade

import app.foldcade.api.plugin.HostedSaveFolder
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SaveSyncState

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
    player is SaveFolderHolder && !player.hasSaveFolder() -> SaveLaunchOffer.Folder
    player is HostedSaveFolder && !player.hostedFolderInUse() -> SaveLaunchOffer.Setup(player.hostedFolderSteps)
    sync is SaveSyncState.NeedsSetup -> SaveLaunchOffer.Setup(sync.steps)
    else -> null
}
