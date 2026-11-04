package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Player
import app.foldcade.host.PluginHost
import app.foldcade.plugins.eden.EdenPlayer
import app.foldcade.plugins.dolphin.DolphinPlayer
import app.foldcade.plugins.ppsspp.PpssppPlayer
import app.foldcade.plugins.explus.ExPlusPlayer
import kotlin.coroutines.cancellation.CancellationException

/**
 * A launch's saves, before the player starts. The copy of the game that keeps them,
 * [GameRoute.saves], may be another library's than the copy that plays: a game in a folder
 * that a server also has keeps one save, on the server, whichever copy opens.
 */
internal class LaunchSaves(
    private val saves: PlayerSaves,
    private val plugins: PluginHost,
    private val firmwareAvailable: (Player, Game, LaunchTarget) -> Boolean = { player, game, _ -> firmwareRequired(player, game) && canPrepareFirmware(player) },
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
        if (!syncing) {
            val firmware = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                player.firmwareFileName("bios.bin") != null || player.firmwareFileName("PS3UPDAT.PUP") != null
            }
            val available = firmware && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { firmwareAvailable(player, route.saves, local) }
            if (!available) {
                return PreparedLaunch(local, emptyList(), route.saves)
            }
            // Firmware is independent of save sync. Ask the library with no save slots.
            val firmwareOnly = object : Player by player {
                override fun supportsSaveSync(game: Game) = false
                override fun saveDeclarations(game: Game) = emptyList<app.foldcade.api.plugin.SaveDeclaration>()
            }
            val placement = plugins.prepareSaves(route.saves.backendId, route.saves, firmwareOnly, local)
            return PreparedLaunch(placement.target, emptyList(), route.saves)
        }
        val candidates = candidates(route)
        val guarded = player is EdenPlayer || player is PpssppPlayer || player is DolphinPlayer || player is ExPlusPlayer
        val resolvedSpots = if (guarded) saves.spots(player, route.saves, local, forLaunch = true) else null
        // A profile or a content-derived game id can be unresolved. Do not let
        // negotiation acknowledge a save this device cannot place.
        if (guarded && resolvedSpots?.isEmpty() == true) {
            onSpots(route.saves, player, emptyList())
            if (player is app.foldcade.api.plugin.SaveFolderHolder && player.hasSaveFolder()) {
                try {
                    val slots = player.saveDeclarations(route.saves).map { it.slot }.toSet()
                    for (owner in candidates) {
                        val held = plugins.saves(owner.backendId, owner).slots.any { it.slot in slots }
                        val incoming = held || try {
                            plugins.serverSaves(owner.backendId, owner, player).any { it.slot in slots }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            if (player is EdenPlayer) throw failure
                            // Unsupported PSP/GC images still play offline. No destination means
                            // no negotiation, writes or acknowledgement; the unsynced signal stays.
                            false
                        }
                        if (incoming) {
                            if (player is EdenPlayer) throw UnresolvedSaveNotPlaced(needsEdenUser = !saves.edenHasUser(player, owner))
                            throw UnresolvedSaveNotPlaced()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: SaveNotPlaced) {
                    throw failure
                } catch (_: Exception) {
                    // Failed local reads, and an unresolved Eden profile's remote check,
                    // cannot prove there is no incoming save.
                    throw UnresolvedSaveNotPlaced()
                }
            }
            // No known incoming save: allow first-run setup or unsupported-image offline play.
            // Return discovery can hand over a resolved save without a baseline from this launch.
            return PreparedLaunch(local, emptyList(), route.saves)
        }
        val setup = mutableListOf<String>()
        var chosen: Negotiated? = null
        for (owner in candidates) {
            val negotiated = try {
                negotiate(route, owner, player, local, setup, resolvedSpots)
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
        val (owner, spots, placement) = chosen ?: negotiate(route, route.saves, player, local, setup, resolvedSpots)
        try {
            // EX names batteries from the ROM filename. A rename can leave an
            // older bundle with no compatible destination; preserve it and ask.
            if (player is ExPlusPlayer && saves.hasIncompatibleSave(spots, placement.savesToPlace)) {
                onSpots(owner, player, emptyList())
                throw UnresolvedSaveNotPlaced(incompatible = true)
            }
            val written = mutableListOf<String>()
            val placed = saves.place(owner.backendId, owner, player, spots, placement.savesToPlace, written)
            // Kept until the player starts, so a launch cancelled now still starts it fresh next time.
            // Card setup counts as a change too: a running player would still be holding the old card.
            if (written.isNotEmpty() || setup.isNotEmpty()) awaitLoad(player.saveFolderId())
            if (!placed) throw SaveNotPlaced()
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
        val available = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { firmwareAvailable(player, owner, local) }
        val syncingPlayer = playerForSaveSpots(player, owner, spots, allowFirmware = available)
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

/** No compatible destination, before any write; an explicit per-launch opt-out is safe. */
internal class UnresolvedSaveNotPlaced(val needsEdenUser: Boolean = false, val incompatible: Boolean = false) : SaveNotPlaced()

/** A library sees only slots the shell can reach; all other player behavior delegates unchanged. */
internal fun playerForSaveSpots(player: Player, game: Game, spots: List<SaveSpot>, allowFirmware: Boolean = true): Player {
    val supportsSync = player.supportsSaveSync(game)
    val reachable = spots.map { it.slot }.toSet()
    val declarations = player.saveDeclarations(game).map { slot ->
        if (slot.slot in reachable) slot else slot.copy(locationUri = null)
    }
    return object : Player by player {
        override fun supportsSaveSync(game: Game) = supportsSync
        override fun firmwareFileName(name: String): String? =
            if (allowFirmware && canPrepareFirmware(player)) player.firmwareFileName(name) else null
        override fun saveDeclarations(game: Game) = declarations
    }
}

/** Do not fetch large BIOS archives until a standalone player's data folder is reachable. */
internal fun canPrepareFirmware(player: Player): Boolean = player !is app.foldcade.api.plugin.SaveFolderHolder ||
    player.hasSaveFolder() || player is app.foldcade.plugins.retroarch.RetroArchPlayer
