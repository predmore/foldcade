package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
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
        val owner = owner(route)
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
            val written = mutableListOf<String>()
            val placed = saves.place(libraryId, owner, player, spots, placement.savesToPlace, written)
            // Kept until the player starts, so a launch cancelled now still starts it fresh next time.
            if (written.isNotEmpty()) awaitLoad(player.saveFolderId())
            if (!placed) throw SaveNotPlaced()
            return PreparedLaunch(placement.target, spots, owner)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // With a save folder in play, any failure here would start the game from a save the library does not know.
            if (spots.isNotEmpty() && failure !is SaveNotPlaced) throw SaveNotPlaced() else throw failure
        }
    }

    /**
     * [GameRoute.saves], unless it holds no save and another server's copy does. Then that copy
     * keeps them, so a device with no save of its own loads the progress a server already has.
     */
    private suspend fun owner(route: GameRoute): Game {
        if (route.servers.size < 2) return route.saves
        return (listOf(route.saves) + route.servers).distinct().firstOrNull { holdsSaves(it) } ?: route.saves
    }

    private suspend fun holdsSaves(game: Game): Boolean = try {
        plugins.saves(game.backendId, game).slots.isNotEmpty()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}

/** [owner] is the copy whose library kept the saves, which the read-back returns them to. */
internal class PreparedLaunch(val target: LaunchTarget, val spots: List<SaveSpot>, val owner: Game? = null)

/** A library save could not be written into the player's folder and checked. */
internal class SaveNotPlaced : Exception("save could not be placed")
