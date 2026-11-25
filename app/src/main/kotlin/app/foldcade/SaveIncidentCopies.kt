package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.host.PluginHost
import app.foldcade.language.SaveIncident
import app.foldcade.language.blocksLaunch
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Suspect bytes never enter the library's held slot. Caller holds saveFlow. */
internal class SaveIncidentCopies(
    private val saves: PlayerSaves,
    private val plugins: PluginHost,
    private val store: SaveProtectionStore,
) {
    fun stage(game: Game, player: Player, spots: List<SaveSpot>, incident: SaveIncident? = null, mark: Boolean): ObservedSaves {
        val observed = saves.stageCopies(game, player, spots)
        val uris = observed.slots.mapNotNull { it.contentUri }
        store.incidents().firstOrNull { it.id == incident?.id }?.let { current ->
            store.put(current.copy(staged = (current.staged + uris).distinct()))
        }
        // Sidecars are durable before marks advance. Their queue survives every incident close path.
        if (mark) saves.noteHandedOver(game.backendId, game, player, spots, observed)
        return observed
    }

    /** Called only after a successful external start, before its return record is saved. */
    suspend fun beginUnprotected(game: Game, player: Player): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        val key = player.saveFolderId()
        val incidents = store.incidents().filter { it.playerKey == key && it.blocksLaunch }
        if (incidents.isEmpty() && !store.needsBackup(key, game)) return@withContext false
        store.requireBackup(key, game)
        incidents.forEach { store.put(it.copy(recoveryRequired = true)) }
        true
    }

    /** A checked head in the working folder ends the rebound fence, even without an outside copy. */
    suspend fun headPlaced(game: Game, player: Player) = withContext(Dispatchers.IO) {
        val key = player.saveFolderId()
        if (store.needsBackup(key, game)) store.handled(key, game)
    }

    /** The launch's persisted decision wins even if a guided fix closed the incident meanwhile. */
    suspend fun preserveReturn(game: Game, player: Player, spots: List<SaveSpot>, preserveAsBackup: Boolean): Boolean {
        if (!preserveAsBackup && !store.pendingBackup(player.saveFolderId(), game)) return false
        // Retained through strict-read failures and later launches until durable staging succeeds.
        store.requireBackup(player.saveFolderId(), game)
        if (spots.isEmpty()) throw SaveNotPlaced()
        val incident = store.incidents().firstOrNull { it.playerKey == player.saveFolderId() && it.blocksLaunch }
        stage(game, player, spots, incident, mark = true)
        store.handled(player.saveFolderId(), game)
        keep(game, player)
        return true
    }

    /** First outside bytes after a rebind (or an unfinished bypass) never reach ordinary reconcile. */
    suspend fun preserveBeforeLaunch(game: Game, player: Player, spots: List<SaveSpot>, outside: ObservedSaves): Boolean {
        val key = player.saveFolderId()
        if (!store.needsBackup(key, game)) return false
        if (spots.isEmpty()) return false // No bytes can be reconciled or overwritten; retain the latch.
        if (outside.slots.isEmpty() && !store.pendingBackup(key, game)) return false
        val observed = stage(game, player, spots, mark = false)
        if (outside.slots.any { slot -> observed.slots.none { it.slot == slot.slot } }) throw SaveNotPlaced()
        saves.noteHandedOver(game.backendId, game, player, spots, observed)
        store.handled(key, game)
        keep(game, player)
        return true
    }

    suspend fun keep(game: Game, player: Player) {
        for (copy in saves.queuedCopies().filter { it.game.backendId == game.backendId && it.game.remoteKey == game.remoteKey && it.playerKey == player.saveFolderId() })
            keepCopy(copy, player)
    }

    /** A successful connection retries every queued game, including incidents closed in an older process. */
    suspend fun keepAll(libraryId: String) {
        for (copy in saves.queuedCopies().filter { it.game.backendId == libraryId }) {
            val player = plugins.player(copy.playerId) ?: plugins.playerIds().mapNotNull(plugins::player).firstOrNull { it.saveFolderId() == copy.playerKey }
            if (player != null) keepCopy(copy, player)
        }
    }

    private suspend fun keepCopy(copy: StagedSaveCopy, player: Player) {
        try {
            val kept = plugins.library(copy.game.backendId)?.keepAsBackups(copy.game, player, ObservedSaves(listOf(copy.slot)))
                ?: throw PluginException.Unavailable("No save backend")
            if (kept.size != 1 || kept.single().isBlank()) throw PluginException.Unavailable("Backup did not verify")
            store.incidents().filter { it.playerKey == copy.playerKey && copy.slot.contentUri in it.staged }.forEach { current ->
                store.put(current.copy(kept = (current.kept + kept).distinct(), staged = current.staged.filterNot { it == copy.slot.contentUri }))
            }
            saves.removeQueuedCopy(copy)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            // Offline, unsupported backends and uncertain uploads leave the sidecar and bytes queued.
        }
    }
}
