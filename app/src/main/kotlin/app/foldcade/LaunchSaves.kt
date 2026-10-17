package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Player
import app.foldcade.host.PluginHost
import kotlin.coroutines.cancellation.CancellationException

/**
 * A launch's saves, before the player starts. The copy of the game that keeps them,
 * [GameRoute.saves], may be another library's than the copy that plays: a game in a folder
 * that a server also has keeps one save, on the server, whichever copy opens.
 */
internal class LaunchSaves(
    private val saves: PlayerSaves,
    private val plugins: PluginHost,
    /** A save was written for the player with this save folder key, which must load it. */
    private val awaitLoad: (String) -> Unit,
) {
    /**
     * Hands the library any save the player changed outside Foldcade, lets the
     * library sync, then writes its saves into the player's folder.
     * Throws [SaveNotPlaced] when the player has a save folder and any step
     * fails: the game must not start from a save the library does not know.
     * The caller runs one save flow at a time.
     */
    suspend fun prepare(route: GameRoute, player: Player, local: LaunchTarget): PreparedLaunch {
        val candidates = candidates(route)
        var chosen: Negotiated? = null
        for (owner in candidates) {
            val negotiated = try {
                negotiate(route, owner, player, local)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Only the first copy's failure stops the launch. A later one was a second look.
                if (chosen == null) throw failure else continue
            }
            if (chosen == null) chosen = negotiated
            // A device with no save of its own takes the first server copy that has one.
            if (negotiated.placement.savesToPlace.isNotEmpty() || negotiated.spots.isEmpty()) {
                chosen = negotiated
                break
            }
        }
        val (owner, spots, placement) = chosen ?: negotiate(route, route.saves, player, local)
        try {
            val written = mutableListOf<String>()
            val placed = saves.place(owner.backendId, owner, player, spots, placement.savesToPlace, written)
            // Kept until the player starts, so a launch cancelled now still starts it fresh next time.
            if (written.isNotEmpty()) awaitLoad(player.saveFolderId())
            if (!placed) throw SaveNotPlaced()
            return PreparedLaunch(placement.target, spots, owner)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (spots.isNotEmpty() && failure !is SaveNotPlaced) throw SaveNotPlaced() else throw failure
        }
    }

    /** [owner]'s saves for this launch: any outside change handed to it first, then its sync. */
    private suspend fun negotiate(route: GameRoute, owner: Game, player: Player, local: LaunchTarget): Negotiated {
        val libraryId = owner.backendId
        // Saves are named after the file that opens, which a folded copy shares by definition.
        val spots = saves.spots(player, owner, local)
        try {
            val outside = saves.changedSince(libraryId, owner, player, spots)
            if (outside.slots.isNotEmpty()) {
                plugins.reconcile(libraryId, owner, player, outside)
                saves.noteHandedOver(libraryId, owner, player, spots, outside)
            }
            val placement = if (owner == route.play) {
                plugins.prepareLaunch(libraryId, owner, player)
            } else {
                plugins.prepareSaves(libraryId, owner, player, local)
            }
            return Negotiated(owner, spots, placement)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // With a save folder in play, any failure here would start the game from a save the library does not know.
            if (spots.isNotEmpty()) throw SaveNotPlaced() else throw failure
        }
    }

    /**
     * The copies that may keep the saves, [GameRoute.saves] first. With several servers, one
     * that already keeps a save here goes first, and each after it is asked in turn when the one
     * before had nothing, so a device with no save loads the progress any server has.
     */
    private suspend fun candidates(route: GameRoute): List<Game> {
        if (route.servers.size < 2) return listOf(route.saves)
        val all = (listOf(route.saves) + route.servers).distinct()
        val held = all.filter { holdsSaves(it) }
        return held + (all - held.toSet())
    }

    private suspend fun holdsSaves(game: Game): Boolean = try {
        plugins.saves(game.backendId, game).slots.isNotEmpty()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private data class Negotiated(val owner: Game, val spots: List<SaveSpot>, val placement: Placement)
}

/** [owner] is the copy whose library kept the saves, which the read-back returns them to. */
internal class PreparedLaunch(val target: LaunchTarget, val spots: List<SaveSpot>, val owner: Game? = null)

/** A library save could not be written into the player's folder and checked. */
internal class SaveNotPlaced : Exception("save could not be placed")
