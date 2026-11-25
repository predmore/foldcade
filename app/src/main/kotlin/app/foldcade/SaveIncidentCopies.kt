package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.host.PluginHost
import app.foldcade.language.SaveIncident
import app.foldcade.language.Evidence
import app.foldcade.language.EvidenceKind
import app.foldcade.language.IncidentKind
import app.foldcade.language.SaveFixCopyDestination
import app.foldcade.language.blocksLaunch
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.plugins.romm.ROMM_LIBRARY_ID
import app.foldcade.plugins.romm.keepRommRepairBackup
import app.foldcade.language.hostedCardMismatch
import app.foldcade.plugins.duckstation.DuckStationPlayer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Suspect bytes never enter the library's held slot. Caller holds saveFlow. */
internal class SaveIncidentCopies(
    private val saves: PlayerSaves,
    private val plugins: PluginHost,
    private val store: SaveProtectionStore,
    private val rommOrigin: () -> String? = { null },
) {
    fun stage(game: Game, player: Player, spots: List<SaveSpot>, incident: SaveIncident? = null, mark: Boolean): ObservedSaves {
        val observed = saves.stageCopies(game, player, spots)
        incident?.let { record(it.id, observed.slots.mapNotNull { slot -> slot.contentUri }) }
        // Sidecars are durable before marks advance. Their queue survives every incident close path.
        if (mark) saves.noteHandedOver(game.backendId, game, player, spots, observed)
        return observed
    }

    fun record(id: String, uris: List<String>) {
        val current = store.incident(id) ?: return
        val staged = (current.staged + uris).distinct()
        if (current.staged != staged) store.put(current.copy(staged = staged))
    }

    fun destinations(staged: List<String>): Map<String, SaveFixCopyDestination> {
        if (staged.isEmpty()) return emptyMap()
        val queued = try { saves.queuedCopies(staged.toSet()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return emptyMap() } // Fix can still proceed if its checklist cannot read the queue.
        return queued.mapNotNull { copy ->
            val destination = when {
                copy.origin != null || copy.game.backendId == ROMM_LIBRARY_ID -> SaveFixCopyDestination.Romm
                copy.game.backendId == LocalFolderBackend.ID -> SaveFixCopyDestination.Device
                else -> return@mapNotNull null
            }
            copy.slot.contentUri?.let { it to destination }
        }.toMap()
    }

    /** A pre-update session can still carry bytes from the slot it launched with. */
    fun preserveUnsupportedRetroArchReturn(pending: LibraryReturn): Boolean {
        if (!unsupportedRetroArchReturn(saves, pending)) return false
        stage(pending.game, pending.player, pending.spots, mark = false)
        return true
    }

    fun stageCandidate(game: Game, player: Player, candidate: RetroArchCandidate, incident: SaveIncident?) {
        val bytes = (candidate.read as? FileReading.Bytes)?.bytes ?: return
        stageBackup(game, player, candidate.spot, bytes, incident)
    }

    fun stageCandidate(game: Game, player: Player, spot: SaveSpot, bytes: ByteArray, incident: SaveIncident?) =
        stageBackup(game, player, spot, bytes, incident)

    fun stageBackup(game: Game, player: Player, spot: SaveSpot, bytes: ByteArray, incident: SaveIncident?) {
        val origin = rommOrigin().takeIf { app.foldcade.plugins.romm.supportsRommRepair(plugins.library(game.backendId)) }
        if (incident != null && incident.gameIds.isEmpty()) {
            // Identical bytes in two games sharing an emulator still belong in separate RomM histories.
            record(incident.id, listOf(saves.stageCopy(game, player, spot, bytes, origin)))
            return
        }
        stageAlternate(game, player, spot, bytes, incident, origin)
    }

    fun stageAlternate(game: Game, player: Player, spot: SaveSpot, bytes: ByteArray, incident: SaveIncident?, origin: String? = null) {
        val hash = md5Hex(bytes)
        val current = incident?.let { store.incident(it.id) }
        if (current != null && (hash in current.capturedAlternateHashes || current.staged.any { it.endsWith("-$hash.save") })) return
        val uri = saves.stageCopy(game, player, spot, bytes, origin, hash)
        if (current != null) store.put(current.copy(staged = (current.staged + uri).distinct(),
            capturedAlternateHashes = current.capturedAlternateHashes + hash))
    }

    /** Called only after a successful external start, before its return record is saved. */
    suspend fun beginUnprotected(game: Game, player: Player): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        val key = player.saveFolderId()
        val incidents = store.incidents().filter { it.playerKey == key && it.blocksLaunch && it.appliesTo(game) }
        if (incidents.isEmpty() && !store.needsBackup(key, game)) return@withContext false
        store.requireBackup(key, game)
        incidents.forEach { store.put(it.copy(recoveryRequired = true)) }
        true
    }

    /** Bring an existing run forward without save preparation, then latch any open incident. */
    suspend fun resume(running: RunningGame, sessionStartedAtMillis: Long? = null, start: () -> Boolean): RunningGame? {
        if (!start()) return null
        val saves = running.saves?.let { pending ->
            pending.copy(preserveAsBackup = beginUnprotected(pending.game, pending.player) || pending.preserveAsBackup,
                startedByFoldcade = false,
                sessionStartedAtMillis = sessionStartedAtMillis ?: pending.sessionStartedAtMillis,
                sessionEndedAtMillis = if (sessionStartedAtMillis == null) pending.sessionEndedAtMillis else null)
        }
        return running.copy(saves = saves)
    }

    /** A checked head in the working folder ends the rebound fence, even without an outside copy. */
    suspend fun headPlaced(game: Game, player: Player) = withContext(Dispatchers.IO) {
        val key = player.saveFolderId()
        if (store.needsBackup(key, game)) store.handled(key, game)
    }

    /** The launch's persisted decision wins even if a guided fix closed the incident meanwhile. */
    suspend fun preserveReturn(game: Game, player: Player, spots: List<SaveSpot>, preserveAsBackup: Boolean): Boolean {
        val key = player.saveFolderId()
        val incident = backupIncident(game, player)
        if (!preserveAsBackup && !store.pendingBackup(key, game)) {
            if (player !is DuckStationPlayer || incident == null) return false
            if (spots.isEmpty()) return true
            store.requireBackup(key, game)
            // A stale card must not announce a sync. An unchanged card needs no new archive.
            val outside = saves.changedSince(game.backendId, game, player, spots, strict = true)
            if (outside.slots.isEmpty()) store.handled(key, game) else preserveChanged(game, player, spots, outside, incident)
            return true
        }
        // Retained through strict-read failures and later launches until durable staging succeeds.
        store.requireBackup(key, game)
        if (spots.isEmpty()) throw SaveNotPlaced()
        stage(game, player, spots, incident, mark = true)
        store.handled(key, game)
        keep(game, player)
        return true
    }

    /** Read only the frozen account when its identity can no longer be attributed to this session. */
    suspend fun preserveEdenReturn(pending: LibraryReturn, plan: EdenReturnPlan) {
        val player = pending.player
        val key = player.saveFolderId()
        val game = pending.game
        val incident = plan.change?.let { change ->
            val id = "${IncidentKind.MaybeNewSave.name}:$key"
            val old = store.incidents().firstOrNull { it.id == id }
            SaveIncident(id, IncidentKind.MaybeNewSave, key, player.displayName, listOf(game.remoteKey), null, null,
                Evidence(EvidenceKind.Config, change), old?.openedAtMillis ?: System.currentTimeMillis(), old?.kept.orEmpty(), old?.staged.orEmpty())
                .also(store::put)
        } ?: store.incidents().firstOrNull { it.playerKey == key && it.blocksLaunch && it.appliesTo(game) }
        if (plan.change != null || pending.edenSession != null && !pending.unprotectedSession && plan.spots.isNotEmpty()) store.rebound(key)
        // The active account's outside bytes will still be fenced at the next launch or save selection.
        stage(game, player, plan.spots, incident, mark = false)
        keep(game, player)
    }

    /** First outside bytes after a rebind (or an unfinished bypass) never reach ordinary reconcile. */
    suspend fun preserveBeforeLaunch(game: Game, player: Player, spots: List<SaveSpot>, outside: ObservedSaves): Boolean {
        val key = player.saveFolderId()
        val quarantine = store.incident(regressionIncidentId(game, player))
        val blocked = store.incidents().firstOrNull { it.playerKey == key && it.blocksLaunch && it.appliesTo(game) }
        val incident = blocked ?: if (player is DuckStationPlayer) backupIncident(game, player) else null
        if (!store.needsBackup(key, game) && quarantine == null && incident == null) return false
        if (spots.isEmpty()) return false // No bytes can be reconciled or overwritten; retain the latch.
        if (outside.slots.isEmpty() && !store.pendingBackup(key, game)) return false
        preserveChanged(game, player, spots, outside, incident, completeHandover = blocked == null)
        return true
    }

    private suspend fun preserveChanged(game: Game, player: Player, spots: List<SaveSpot>, outside: ObservedSaves,
        incident: SaveIncident?, completeHandover: Boolean = true) {
        val observed = stage(game, player, spots, incident, mark = false)
        if (outside.slots.any { slot -> observed.slots.none { it.slot == slot.slot } }) throw SaveNotPlaced()
        saves.noteHandedOver(game.backendId, game, player, spots, observed)
        if (completeHandover) store.handled(player.saveFolderId(), game)
        keep(game, player)
    }

    private fun backupIncident(game: Game, player: Player): SaveIncident? =
        store.incidents().firstOrNull {
            it.playerKey == player.saveFolderId() && it.appliesTo(game) && (it.blocksLaunch || it.hostedCardMismatch)
        }

    suspend fun keep(game: Game, player: Player) {
        for (copy in queued(game, player)) keepCopy(copy, player)
    }

    fun hasQueued(game: Game, player: Player): Boolean = queued(game, player).isNotEmpty()

    private fun queued(game: Game, player: Player) = saves.queuedCopies().filter {
        it.game.backendId == game.backendId && it.game.remoteKey == game.remoteKey && it.playerKey == player.saveFolderId() && belongsToCurrentServer(it)
    }

    /** A successful connection retries every queued game, including incidents closed in an older process. */
    suspend fun keepAll(libraryId: String) {
        for (copy in saves.queuedCopies().filter { it.game.backendId == libraryId && belongsToCurrentServer(it) }) {
            val player = plugins.player(copy.playerId) ?: plugins.playerIds().mapNotNull(plugins::player).firstOrNull { it.saveFolderId() == copy.playerKey }
            if (player != null) keepCopy(copy, player)
        }
    }

    private fun belongsToCurrentServer(copy: StagedSaveCopy) = copy.origin == null || copy.origin == rommOrigin()

    private suspend fun keepCopy(copy: StagedSaveCopy, player: Player) {
        try {
            val library = plugins.library(copy.game.backendId) ?: throw PluginException.Unavailable("No save backend")
            val observed = ObservedSaves(listOf(copy.slot))
            val kept = if (copy.origin != null) keepRommRepairBackup(library, copy.game, player, observed, copy.origin)
                else library.keepAsBackups(copy.game, player, observed)
            if (kept.size != 1 || kept.single().isBlank()) throw PluginException.Unavailable("Backup did not verify")
            store.incidents().filter { it.playerKey == copy.playerKey && copy.slot.contentUri in it.staged }.forEach { current ->
                val candidate = if (current.quarantineSourceUri == copy.slot.contentUri) kept.single() else current.quarantineBackupId
                store.put(current.copy(kept = (current.kept + kept).distinct(),
                    staged = current.staged.filterNot { it == copy.slot.contentUri }, quarantineBackupId = candidate))
            }
            saves.removeQueuedCopy(copy)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            // Offline, unsupported backends and uncertain uploads leave the sidecar and bytes queued.
        }
    }
}
