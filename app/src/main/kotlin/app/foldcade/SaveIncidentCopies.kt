package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.host.PluginHost
import app.foldcade.language.SaveIncident
import app.foldcade.language.gatesLaunch
import kotlin.coroutines.cancellation.CancellationException

/** Suspect bytes never enter the library's held slot. Caller holds saveFlow. */
internal class SaveIncidentCopies(
    private val saves: PlayerSaves,
    private val plugins: PluginHost,
    private val store: SaveProtectionStore,
) {
    fun stage(game: Game, player: Player, spots: List<SaveSpot>, incident: SaveIncident, mark: Boolean): ObservedSaves {
        val observed = saves.stageCopies(game, player, spots)
        val uris = observed.slots.mapNotNull { it.contentUri }
        store.incidents().firstOrNull { it.id == incident.id }?.let { current ->
            store.put(current.copy(staged = (current.staged + uris).distinct()))
        }
        // Mark only after the durable copy and its incident reference exist. Placement
        // still backs up an overwrite, but changedSince cannot promote these bytes.
        if (mark) saves.noteHandedOver(game.backendId, game, player, spots, observed)
        return observed
    }

    fun beginUnprotected(player: Player) {
        store.incidents().filter { it.playerKey == player.saveFolderId() && it.kind.gatesLaunch }
            .forEach { store.put(it.copy(recoveryRequired = true)) }
    }

    /** Returns true when ordinary reconciliation must be skipped for a bypassed return. */
    suspend fun preserveReturn(game: Game, player: Player, spots: List<SaveSpot>, unprotected: Boolean): Boolean {
        val incident = store.incidents().firstOrNull { it.playerKey == player.saveFolderId() && it.kind.gatesLaunch }
        if (!unprotected || incident == null) return false
        // Persist the latch before reading bytes: a partial or unreadable return must not clear the alarm.
        val held = incident.copy(recoveryRequired = true)
        store.put(held)
        stage(game, player, spots, held, mark = true)
        keep(game, player)
        return true
    }

    suspend fun keep(game: Game, player: Player) {
        for (incident in store.incidents().filter { it.playerKey == player.saveFolderId() && it.staged.isNotEmpty() }) {
            try {
                val observed = saves.stagedCopies(game, player, incident.staged)
                if (observed.slots.isEmpty()) continue
                val kept = plugins.library(game.backendId)?.keepAsBackups(game, player, observed)
                    ?: throw PluginException.Unavailable("No save backend")
                if (kept.size != observed.slots.size) throw PluginException.Unavailable("Backups did not verify")
                store.incidents().firstOrNull { it.id == incident.id }?.let { current ->
                    val uploaded = observed.slots.mapNotNull { it.contentUri }.toSet()
                    store.put(current.copy(kept = (current.kept + kept).distinct(), staged = current.staged.filterNot { it in uploaded }))
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                // Offline, unsupported backends and uncertain uploads leave the durable copy queued.
            }
        }
    }
}
