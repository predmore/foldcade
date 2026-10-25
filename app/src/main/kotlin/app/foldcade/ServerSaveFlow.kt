package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.host.PluginHost
import app.foldcade.language.SaveDestination
import app.foldcade.language.SavePickerTarget

/** Captured when Y opens Saves, retained across activity recreation. */
internal data class SavePickerRequest(val target: SavePickerTarget, val route: GameRoute, val owner: Game, val player: Player?)

/** The caller owns saveFlow, so a return, launch or another choice cannot race capture/placement. */
internal class ServerSaveFlow(
    private val saves: PlayerSaves,
    private val plugins: PluginHost,
    private val awaitLoad: (String) -> Unit,
) {
    suspend fun perform(request: SavePickerRequest, saveId: String?, destination: SaveDestination? = null) {
        val player = request.player
        val owner = request.owner
        val library = owner.backendId
        val local = player?.takeIf { it.supportsSaveSync(owner) }?.let { plugins.ensureLocal(request.route.play.backendId, request.route.play) }
        val spots = if (local != null) saves.spots(player, owner, local) else emptyList()
        val declarations = player?.saveDeclarations(owner).orEmpty()
        val ownsDestination = destination == null || declarations.any { it.slot == destination.slot }
        if (saveId != null && ownsDestination && (player == null || !player.supportsSaveSync(owner) || declarations.isEmpty() || spots.size != declarations.size)) throw SaveNotPlaced()
        // Even a copy to a foreign slot first keeps every readable change from the player.
        if (player != null && spots.isNotEmpty()) {
            val observed = saves.changedSince(library, owner, player, spots, strict = true)
            if (observed.slots.isNotEmpty()) {
                plugins.reconcile(library, owner, playerForSaveSpots(player, owner, spots), observed)
                saves.noteHandedOver(library, owner, player, spots, observed)
            }
        }
        if (saveId == null) {
            plugins.syncServerSaves(library, owner, player)
            return
        }
        // Persist before the backend can commit a choice or a player file can be replaced.
        if (ownsDestination && player != null) awaitLoad(player.saveFolderId())
        val placed = if (destination == null) {
            plugins.playServerSave(library, owner, player ?: throw SaveNotPlaced(), saveId)
        } else plugins.copyServerSave(library, owner, saveId, destination.slot, destination.emulator, player)
        if (placed.isNotEmpty()) {
            if (player == null || !saves.place(library, owner, player, spots, placed)) throw SaveNotPlaced()
        }
    }
}
