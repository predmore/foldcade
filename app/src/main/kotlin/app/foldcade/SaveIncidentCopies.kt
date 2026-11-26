package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.host.PluginHost
import app.foldcade.language.SaveIncident
import app.foldcade.language.FolderRef
import app.foldcade.language.RepairState
import app.foldcade.language.SaveProtectionCopy
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.romm.rommSaveHash
import app.foldcade.api.plugin.HostedSaveFolder
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.plugins.eden.EdenPlayer
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
    /** RomM's versions of a game, as repair lists them. Tests supply their own. */
    private val versions: (suspend (Game, String) -> List<app.foldcade.plugins.romm.RommRepairSave>)? = null,
) {
    /**
     * First link (design §7.1): a folder copy's save that RomM has never seen, meeting a RomM copy
     * whose slot holds something else. The folder's bytes are staged for a verified Backup, and the
     * launch stops on the two-saves choice, so neither copy is placed or handed over until the player
     * picks one. Until a choice succeeds (This Thor, RomM, or a save played from Y › Saves) every
     * launch stops again, whatever a Play anyway return has kept meanwhile.
     *
     * "Never seen" is decided by content, not by this device's history, so a reinstall that wiped
     * marks and caches still recognises an old synced save ([firstLinkDiffers]). Null lets the
     * ordinary sync run: the slot was linked here before, RomM knows these bytes or has no
     * verifiable head, RomM can't be read now (the ordinary negotiation then fails before writing
     * anything too), or the emulator can't finish a choice yet (Eden, hosted cards).
     */
    suspend fun holdFirstLink(route: GameRoute, owner: Game, player: Player, local: LaunchTarget, spots: List<SaveSpot>,
        outside: ObservedSaves, packageName: String? = null, installFirstMillis: Long? = null): SaveIncident? {
        val origin = rommOrigin() ?: return null
        // Any open stop for any copy of this game, on this server, gates every launch of it, whichever copy
        // or route starts it, before any filter for raising one. Another server's stop is not this one's.
        openFirstLinkFor(route, origin)?.let { return it }
        if (route.play.backendId != LocalFolderBackend.ID || owner.backendId == LocalFolderBackend.ID) return null
        // One RomM owner only: a game folded from several RomM ids keeps the ordinary sync (§7.1 scope).
        if (route.servers.distinctBy { it.backendId to it.remoteKey }.size != 1) return null
        // Repair can't place a chosen copy for these yet; they keep the ordinary sync (follow-up in §7.1).
        if (player !is SaveFolderHolder || player is HostedSaveFolder || player is EdenPlayer) return null
        val spot = spots.singleOrNull() ?: return null
        if (player.saveDeclarations(owner).singleOrNull()?.slot != spot.slot) return null
        val id = firstLinkIncidentId(owner, player, origin)
        // Cheap first: a slot this device has placed, read or held is an ordinary RomM game here.
        val linked = try {
            saves.everHandled(owner.backendId, owner, player, spot) || plugins.saves(owner.backendId, owner).slots.any { it.slot == spot.slot }
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { true }
        if (linked || outside.slots.none { it.slot == spot.slot }) return null
        val target = (local as? LaunchTarget.ContentUri)?.uri ?: return null
        val bytes = withContext(Dispatchers.IO) { saves.readSpot(spot, strict = true)?.first } ?: return null
        val hash = rommSaveHash(bytes) ?: return null
        val listed = try { (versions ?: ::rommVersions)(owner, origin) }
            catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { return null }
        if (!firstLinkDiffers(hash, spot.slot, listed)) return null
        val uri = withContext(Dispatchers.IO) { saves.stageCopy(owner, player, spot, bytes, origin) }
        val folder = FolderRef(storageIdentity(spot.folderUri), runCatching { folderPathLabel(spot.folderUri) }.getOrDefault(spot.folderUri))
        store.put(SaveIncident(id, IncidentKind.MaybeNewSave, player.saveFolderId(), player.displayName, listOf(owner.remoteKey), folder, null,
            Evidence(EvidenceKind.SessionWrite, SaveProtectionCopy.firstLinkDetail), System.currentTimeMillis(),
            staged = listOf(uri), repair = RepairState.Keeping,
            // The build that launched, so repair finds it when two builds of the emulator are installed.
            subject = incidentGame(owner, player, target, origin, null).copy(packageName = packageName, installFirstMillis = installFirstMillis),
            quarantinedHash = hash, quarantineSourceUri = uri, firstLink = true))
        // Offline, the copy stays queued, and the choice waits for it to verify.
        keep(owner, player)
        return store.incident(id)
    }

    /**
     * A Play anyway session past a first-link stop made newer progress here. The stop stays open, and
     * "This Thor" in its choice becomes those bytes, kept and verified again before it is offered.
     */
    private fun followFirstLink(open: SaveIncident, observed: ObservedSaves) {
        val slot = observed.slots.singleOrNull() ?: return
        val uri = slot.contentUri ?: return
        val bytes = runCatching { java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(java.net.URI(uri))) }.getOrNull() ?: return
        val hash = rommSaveHash(bytes) ?: return
        if (hash == open.quarantinedHash) return
        store.put(open.copy(quarantinedHash = hash, quarantineSourceUri = uri, quarantineBackupId = null,
            staged = (open.staged + uri).distinct(), repair = RepairState.Keeping))
    }

    /** An explicit choice placed a save for [game] (This Thor, RomM, or Y › Saves): its first-link stop has done its job. */
    fun firstLinkChosen(game: Game, player: Player) {
        // Only a choice made on the stop's own server answers it.
        val origin = rommOrigin() ?: return
        openFirstLink(game, player, origin)?.let { store.close(it.id) }
    }

    /** The open first-link stop a session of [game] on the current server would answer to: its id and origin, for the return record. */
    fun firstLinkSession(game: Game, player: Player): FirstLinkSession? {
        val origin = rommOrigin() ?: return null
        return openFirstLink(game, player, origin)?.let { FirstLinkSession(it.id, origin) }
    }

    /** [game]'s open first-link stop on this player, raised for [origin]. */
    private fun openFirstLink(game: Game, player: Player, origin: String): SaveIncident? =
        store.incident(firstLinkIncidentId(game, player, origin))?.takeIf { it.isOpenFirstLink(origin) }

    /** Any open stop raised on [origin] for a copy of [route]'s game, by any emulator. */
    private fun openFirstLinkFor(route: GameRoute, origin: String): SaveIncident? {
        val copies = (listOf(route.play, route.saves) + route.servers).map { it.backendId to it.remoteKey }.toSet()
        return store.incidents().firstOrNull { incident ->
            incident.isOpenFirstLink(origin) && incident.subject?.let { (it.libraryId to it.gameId) in copies } == true
        }
    }

    private fun SaveIncident.isOpenFirstLink(origin: String) = firstLink && repair != RepairState.Done && subject?.origin == origin

    private suspend fun rommVersions(game: Game, origin: String): List<app.foldcade.plugins.romm.RommRepairSave> {
        val library = plugins.library(game.backendId)
        if (!app.foldcade.plugins.romm.supportsRommRepair(library)) throw PluginException.Unavailable("No RomM saves")
        return app.foldcade.plugins.romm.rommRepairSaves(library, game, origin)
    }

    fun stage(game: Game, player: Player, spots: List<SaveSpot>, incident: SaveIncident? = null, mark: Boolean, origin: String? = null): ObservedSaves {
        val observed = saves.stageCopies(game, player, spots, origin)
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
        val incidents = store.incidents().filter { it.gatesLaunch(player, game) }
        // Play anyway past a first-link stop on this server plays this device's save: a Backup, never a slot head.
        // Another server's stop doesn't latch a session here.
        val firstLink = firstLinkSession(game, player) != null
        if (incidents.isEmpty() && !firstLink && !store.needsBackup(key, game)) return@withContext false
        store.requireBackup(key, game)
        incidents.filter { it.appliesTo(game) }.forEach { store.put(it.copy(recoveryRequired = true)) }
        true
    }

    /** Bring an existing run forward without save preparation, then latch any open incident. */
    suspend fun resume(running: RunningGame, sessionStartedAtMillis: Long? = null, start: () -> Boolean): RunningGame? {
        if (!start()) return null
        val saves = running.saves?.let { pending ->
            pending.copy(preserveAsBackup = beginUnprotected(pending.game, pending.player) || pending.preserveAsBackup,
                firstLink = firstLinkSession(pending.game, pending.player) ?: pending.firstLink,
                startedByFoldcade = false,
                sessionStartedAtMillis = sessionStartedAtMillis ?: pending.sessionStartedAtMillis,
                sessionEndedAtMillis = if (sessionStartedAtMillis == null) pending.sessionEndedAtMillis else null)
        }
        return running.copy(saves = saves)
    }

    /**
     * A checked head in the working folder ends the rebound fence, even without an outside copy.
     * [chosen] when the player picked that save, as from Y › Saves: the explicit choice a first-link stop waits for.
     */
    suspend fun headPlaced(game: Game, player: Player, chosen: Boolean = false) = withContext(Dispatchers.IO) {
        val key = player.saveFolderId()
        if (store.needsBackup(key, game)) store.handled(key, game)
        if (chosen) firstLinkChosen(game, player)
    }

    /** The launch's persisted decision wins even if a guided fix closed the incident meanwhile. */
    suspend fun preserveReturn(game: Game, player: Player, spots: List<SaveSpot>, preserveAsBackup: Boolean,
        firstLink: FirstLinkSession? = null): Boolean {
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
        // A session past a first-link stop is that server's progress, as its return record says: its Backup
        // goes to that server alone, even if another is connected by the time it uploads, and only that
        // stop follows it. Nothing else decides which stop a session belongs to.
        val observed = stage(game, player, spots, incident, mark = true, origin = firstLink?.origin)
        firstLink?.let { session ->
            store.incident(session.incidentId)?.takeIf { it.isOpenFirstLink(session.origin) }?.let { followFirstLink(it, observed) }
        }
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
        // A first-link stop on this server keeps outside bytes out of reconcile too, as when a save is played from Y › Saves.
        val quarantine = store.incident(regressionIncidentId(game, player)) ?: rommOrigin()?.let { openFirstLink(game, player, it) }
        val blocked = store.incidents().firstOrNull { it.gatesLaunch(player, game) }
        // Another game's melonDS beside incident can gate this launch; copies are recorded on this game's own incident.
        val incident = store.incidents().firstOrNull { it.gatesLaunch(player, game) && it.appliesTo(game) }
            ?: if (player is DuckStationPlayer) backupIncident(game, player) else null
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
            // A first-link stop takes only copies kept on its own server as its candidate.
            store.incidents().filter { it.playerKey == copy.playerKey && copy.slot.contentUri in it.staged &&
                (!it.firstLink || it.subject?.origin == copy.origin) }.forEach { current ->
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

/**
 * Whether the device's [hash] is progress RomM has never seen, against a slot head it can verify.
 * Every version RomM lists for the game counts as seen: the slot's head and older versions, other
 * slots, and unslotted Backups, from any device. A head without a hash can't be compared, so it
 * never raises the stop.
 */
internal fun firstLinkDiffers(hash: String, slot: String, versions: List<app.foldcade.plugins.romm.RommRepairSave>): Boolean {
    val head = versions.firstOrNull { it.slot == slot } ?: return false
    if (head.hash.isBlank()) return false
    return versions.none { it.hash.isNotBlank() && it.hash.equals(hash, ignoreCase = true) }
}

/** The first-link stop a Play anyway session answers to, persisted in its return record. */
internal data class FirstLinkSession(val incidentId: String, val origin: String)

/** First-link stops are keyed by server, so one server's stop never overwrites another's. */
internal fun firstLinkIncidentId(game: Game, player: Player, origin: String) =
    "${regressionIncidentId(game, player)}:firstLink:${md5Hex(origin.toByteArray())}"
