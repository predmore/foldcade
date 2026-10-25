package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginException
import app.foldcade.host.PluginHost
import app.foldcade.language.SaveDestination
import app.foldcade.language.SavePickerTarget

/** Captured when Y opens Saves, retained across activity recreation. */
internal data class SavePickerRequest(val target: SavePickerTarget, val route: GameRoute, val owner: Game, val player: Player?)

internal class SaveNeedsDownload : Exception()

/** The caller owns saveFlow, so a return, launch or another choice cannot race capture/placement. */
internal class ServerSaveFlow(
    private val saves: PlayerSaves,
    private val plugins: PluginHost,
    private val awaitsLoad: (String) -> Boolean,
    private val awaitLoad: (String, Boolean) -> Unit,
) {
    suspend fun perform(request: SavePickerRequest, saveId: String?, destination: SaveDestination? = null) {
        val player = request.player
        val owner = request.owner
        val library = owner.backendId
        val remote = request.route.play.availability == Availability.RemoteOnly
        val declarations = player?.saveDeclarations(owner).orEmpty()
        val ownsDestination = destination == null || declarations.any { it.slot == destination.slot }
        val wantsPlacement = saveId != null && ownsDestination
        val spots = if (remote) {
            if (wantsPlacement && player != null) {
                val name = plugins.fileName(request.route.play.backendId, request.route.play) ?: throw SaveNeedsDownload()
                saves.spotsForFileName(player, owner, name).also {
                    if (it.size != declarations.size || it.isEmpty()) throw SaveNeedsDownload()
                }
            } else emptyList()
        } else {
            val local = player?.takeIf { it.supportsSaveSync(owner) }?.let { plugins.ensureLocal(request.route.play.backendId, request.route.play) }
            if (local != null) saves.spots(player, owner, local) else emptyList()
        }
        if (wantsPlacement && (player == null || !player.supportsSaveSync(owner) || declarations.isEmpty() || spots.size != declarations.size)) throw SaveNotPlaced()
        // A server-only ROM has no new emulator progress to capture. Reading its file is unnecessary.
        if (!remote && player != null && spots.isNotEmpty()) {
            val observed = saves.changedSince(library, owner, player, spots, strict = true)
            if (observed.slots.isNotEmpty()) {
                plugins.reconcile(library, owner, playerForSaveSpots(player, owner, spots), observed)
                saves.noteHandedOver(library, owner, player, spots, observed)
            }
        }
        plugins.syncServerSaves(library, owner, player)
        if (saveId == null) return
        val key = player?.saveFolderId()
        val wasAwaiting = key?.let(awaitsLoad) == true
        var armed = false
        val beforeCommit: () -> Unit = {
            if (ownsDestination && key != null) {
                armed = true
                awaitLoad(key, true)
            }
        }
        val placed = try {
            if (destination == null) plugins.playServerSave(library, owner, player ?: throw SaveNotPlaced(), saveId, beforeCommit)
            else plugins.copyServerSave(library, owner, saveId, destination.slot, destination.emulator, player, beforeCommit)
        } catch (refused: PluginException.SaveChoiceRefused) {
            if (armed && key != null) awaitLoad(key, wasAwaiting)
            throw refused
        }
        // A failure after promotion keeps the marker: the new bytes must load on the next launch.
        if (placed.isNotEmpty() && (player == null || !saves.place(library, owner, player, spots, placed))) throw SaveNotPlaced()
    }
}
