package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Player
import app.foldcade.host.PluginHost
import app.foldcade.plugins.eden.EdenPlayer
import kotlin.coroutines.cancellation.CancellationException

/**
 * A launch's saves, before the player starts. The copy of the game that keeps them,
 * [GameRoute.saves], may be another library's than the copy that plays: a game in a folder
 * that a server also has keeps one save, on the server, whichever copy opens.
 */
internal class LaunchSaves(
    private val saves: PlayerSaves,
    private val plugins: PluginHost,
    /** The folder the user picked for a player, where its firmware goes, or null. */
    private val pickedFolder: (Player) -> String? = { null },
    private val onSpots: suspend (Game, Player, List<SaveSpot>) -> Unit = { _, _, _ -> },
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
    suspend fun prepare(route: GameRoute, player: Player, local: LaunchTarget, syncing: Boolean = true): PreparedLaunch {
        if (!syncing) return PreparedLaunch(local, emptyList(), route.saves)
        val candidates = candidates(route)
        val edenSpots = if (player is EdenPlayer) saves.spots(player, route.saves, local, forLaunch = true) else null
        // Eden may not have made its user yet. Do not let negotiation acknowledge
        // a server save this device cannot place, even if the player starts anyway.
        if (player is EdenPlayer && edenSpots?.isEmpty() == true) {
            onSpots(route.saves, player, emptyList())
            if (player.hasSaveFolder()) {
                try {
                    for (owner in candidates) {
                        val held = plugins.saves(owner.backendId, owner).slots.any { it.slot == EdenPlayer.SLOT }
                        val incoming = held || plugins.serverSaves(owner.backendId, owner, player).any { it.slot == EdenPlayer.SLOT }
                        if (incoming) throw EdenSaveNotPlaced(needsUser = !saves.edenHasUser(player, owner))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: SaveNotPlaced) {
                    throw failure
                } catch (_: Exception) {
                    // An unavailable listing cannot prove there is no incoming save.
                    throw SaveNotPlaced()
                }
            }
            // With no incoming save, first boot can make a user and save. Return
            // discovery hands it over without a server baseline from this launch.
            return PreparedLaunch(local, emptyList(), route.saves)
        }
        val setup = mutableListOf<String>()
        var chosen: Negotiated? = null
        for (owner in candidates) {
            val negotiated = try {
                negotiate(route, owner, player, local, setup, edenSpots)
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
        val (owner, spots, placement) = chosen ?: negotiate(route, route.saves, player, local, setup, edenSpots)
        try {
            val written = mutableListOf<String>()
            val placed = saves.place(owner.backendId, owner, player, spots, placement.savesToPlace, written)
            // Kept until the player starts, so a launch cancelled now still starts it fresh next time.
            // Card setup counts as a change too: a running player would still be holding the old card.
            if (written.isNotEmpty() || setup.isNotEmpty()) awaitLoad(player.saveFolderId())
            if (!placed) throw SaveNotPlaced()
            // A BIOS the library has goes beside the saves. A file already there stays.
            saves.placeFirmware(player, owner, placement.target, pickedFolder(player))
            return PreparedLaunch(placement.target, spots, owner)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (spots.isNotEmpty() && failure !is SaveNotPlaced) throw SaveNotPlaced() else throw failure
        }
    }

    /** [owner]'s saves for this launch: any outside change handed to it first, then its sync. */
    private suspend fun negotiate(
        route: GameRoute,
        owner: Game,
        player: Player,
        local: LaunchTarget,
        setup: MutableList<String>,
        resolvedSpots: List<SaveSpot>?,
    ): Negotiated {
        val libraryId = owner.backendId
        // Saves are named after the file that opens, which a folded copy shares by definition.
        // forLaunch: the card may be created here, and must not be on the way back.
        val spots = resolvedSpots ?: saves.spots(player, owner, local, forLaunch = true, changed = setup)
        onSpots(owner, player, spots)
        // A declared folder is not necessarily a reachable slot for this game
        // (for example an ARMSX2 file card). Tell the library what the shell can
        // actually place, so its activity cannot claim an unreachable save synced.
        val syncingPlayer = playerForSaveSpots(player, owner, spots)
        try {
            val outside = saves.changedSince(libraryId, owner, player, spots)
            if (outside.slots.isNotEmpty()) {
                plugins.reconcile(libraryId, owner, syncingPlayer, outside)
                saves.noteHandedOver(libraryId, owner, player, spots, outside)
            }
            val placement = if (owner == route.play) {
                plugins.prepareLaunch(libraryId, owner, syncingPlayer)
            } else {
                plugins.prepareSaves(libraryId, owner, syncingPlayer, local)
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
     * The copies that may keep the saves, [GameRoute.saves] first. RomM can list one file under
     * two platforms that fold into one tile. A copy that already keeps a save here goes first,
     * and each after it is asked in turn when the one before had nothing, so a device with no
     * save loads the progress either copy has.
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
internal open class SaveNotPlaced : Exception("save could not be placed")

internal class EdenSaveNotPlaced(val needsUser: Boolean) : SaveNotPlaced()

/** A library sees only slots the shell can reach; all other player behavior delegates unchanged. */
internal fun playerForSaveSpots(player: Player, game: Game, spots: List<SaveSpot>): Player {
    val supportsSync = player.supportsSaveSync(game)
    val reachable = spots.map { it.slot }.toSet()
    val declarations = player.saveDeclarations(game).map { slot ->
        if (slot.slot in reachable) slot else slot.copy(locationUri = null)
    }
    return object : Player by player {
        override fun supportsSaveSync(game: Game) = supportsSync
        override fun saveDeclarations(game: Game) = declarations
    }
}
