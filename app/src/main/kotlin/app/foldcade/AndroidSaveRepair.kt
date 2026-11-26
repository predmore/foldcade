package app.foldcade

import android.content.pm.PackageManager
import app.foldcade.api.plugin.HostedSaveFolder
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SaveLocationProbe
import app.foldcade.language.FolderRef
import app.foldcade.language.BesideSaveSource
import app.foldcade.language.IncidentKind
import app.foldcade.language.RepairState
import app.foldcade.language.SaveIncident
import app.foldcade.language.SaveIncidentGame
import app.foldcade.language.SaveConflictFocus
import app.foldcade.language.SaveConflictPrompt
import app.foldcade.language.SaveConflictSide
import app.foldcade.language.SaveProtectionCopy
import app.foldcade.language.blocksLaunch
import app.foldcade.language.suspectRegression
import app.foldcade.plugins.eden.EdenPlayer
import app.foldcade.plugins.retroarch.RetroArchPlayer
import app.foldcade.plugins.romm.RommRepairNeedsSync
import app.foldcade.plugins.romm.rommRepairSaves
import app.foldcade.plugins.romm.rommSyncedSave
import app.foldcade.plugins.romm.supportsRommRepair
import app.foldcade.romm.rommSaveHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun FoldcadeApp.repairWork(incident: SaveIncident): SaveRepairWork? {
    val subject = incident.subject ?: return null
    val game = subject.game()
    val origin = subject.origin?.takeIf { it == store.rommOrigin() } ?: return null
    val library = plugins.library(game.backendId)
    if (!supportsRommRepair(library)) return null
    val player = plugins.player(subject.playerId) ?: return null
    if (player.saveFolderId() != incident.playerKey) return null
    val holder = player as? SaveFolderHolder ?: return null
    val isQuarantine = incident.kind == IncidentKind.MaybeNewSave
    val besideGame = incident.besideGame
    // Hosted cards belong to the provider. A separate data/BIOS grant is never a repair destination.
    if (player is HostedSaveFolder) return null
    if (!isQuarantine && (incident.kind != IncidentKind.WrongFolder || player is EdenPlayer)) return null
    // A write beside the ROM can only be repaired after the in-app instructions and bound-folder pick.
    if (besideGame && incident.repair == RepairState.NotStarted) return null
    val target = subject.targetUri?.let { LaunchTarget.ContentUri(it) } ?: return null
    val slot = player.saveDeclarations(game).singleOrNull()?.slot ?: return null
    val eden = if (player is EdenPlayer) {
        playerSaves.edenRepairTarget(player, game, target, subject.edenContext)?.takeIf { it.spot.slot == slot } ?: return null
    } else null
    val current = eden?.spot?.folderUri ?: playerSaveFolder(player) ?: return null
    val currentId = storageIdentity(current, packageName)
    val live = when {
        isQuarantine -> FolderRef(currentId, folderPathLabel(current))
        besideGame -> incident.bound ?: return null
        else -> incident.live ?: return null
    }
    val bound = if (isQuarantine) live else incident.bound ?: return null
    if (currentId !in setOf(bound.identity, live.identity)) return null

    val packageInfo = (subject.packageName?.takeIf { it in player.packageNames }?.let(::listOf) ?: player.packageNames)
        .mapNotNull { name -> try { packageManager.getPackageInfo(name, 0) } catch (_: PackageManager.NameNotFoundException) { null } }
        .singleOrNull() ?: return null
    if (!isQuarantine && subject.installFirstMillis != null && subject.installFirstMillis != packageInfo.firstInstallTime) return null

    val android = AndroidSaveFolderFinder(this)
    val grants = android.grants(listOf(holder))
    val finder = android.finder(player, grants)
    fun reachable(ref: FolderRef): String? {
        return listOfNotNull(current.takeIf { currentId == ref.identity }, finder.reachable(ref.identity)).distinct()
            .firstOrNull { storageIdentity(it, packageName) == ref.identity && android.canReach(it, grants) }
    }
    val boundUri = reachable(bound) ?: return null
    val liveUri = reachable(live) ?: return null

    return object : SaveRepairWork {
        override val slot = slot

        override suspend fun running(): Boolean = withContext(Dispatchers.Main.immediate) {
            launchWork.active || launchWork.waitingToLight ||
                (isQuarantine && saveProtection.incidents().any { it.playerKey == incident.playerKey && it.blocksLaunch && it.appliesTo(game) }) ||
                player.packageNames.any { name ->
                    val fallback = incident.openedAtMillis.takeIf { (player is RetroArchPlayer || besideGame) && name == packageInfo.packageName }
                    name in closingPlayers || repairPlayerHolds.mayHold(name, fallback, repairHasRunningTask(name))
                }
        }

        override suspend fun versions() = rommRepairSaves(library, game, origin)

        private fun spot(folder: String): SaveSpot {
            if (player is EdenPlayer) {
                val current = playerSaves.edenRepairTarget(player, game, target, subject.edenContext)
                if (current == null || current != eden) throw RommRepairNeedsSync()
                return current.spot
            }
            val frozenName = subject.romFileName.takeIf { besideGame }
            val spots = if (frozenName != null) playerSaves.spotsForFileName(player, game, frozenName, root = folder)
                else playerSaves.probeSpots(player, game, target, folder)
            return spots.singleOrNull()?.takeIf { it.slot == slot } ?: throw RepairFoldersUnavailable()
        }

        private fun beside(): SaveSpot? {
            if (player.probesContentSaves) return null
            val probe = player as? SaveLocationProbe ?: return null
            val uri = subject.targetUri ?: return null
            val document = runCatching { folderDocumentId(uri) }.getOrNull() ?: return null
            val parent = document.substringBeforeLast('/', "")
            val name = probe.saveBesideGame(slot, document.substringAfterLast('/')) ?: return null
            if (parent.isEmpty()) return null
            val folder = folderDocumentUri(uri, parent)
            return if (android.canReach(folder, grants)) SaveSpot(slot, folder, name) else null
        }

        override suspend fun readCopies(keptHashes: Set<String>, stage: Boolean): RepairCopies {
            val liveSpot = spot(liveUri)
            val previous = if (isQuarantine || besideGame || !stage) null else rommSyncedSave(library, game, slot, SAVE_ZIP_LIMIT)
                ?.let { rommSaveHash(it) to saveShape(it, liveSpot.isFolderSave()) }
            val seen = keptHashes.toMutableSet()
            var liveRawHash: String? = null
            var liveSuspect = false
            fun read(spot: SaveSpot, live: Boolean = false): String? {
                val bytes = playerSaves.readSpot(spot, strict = true, limit = SAVE_ZIP_LIMIT)?.first ?: return null
                val hash = rommSaveHash(bytes) ?: throw RepairFoldersUnavailable()
                if (stage && seen.add(hash)) {
                    saveIncidentCopies.record(incident.id, listOf(playerSaves.stageCopy(game, player, spot, bytes, origin)))
                }
                if (live) {
                    liveRawHash = md5Hex(bytes)
                    liveSuspect = previous?.second?.let { prior ->
                        saveShape(bytes, spot.isFolderSave())?.let { suspectRegression(prior, it) != null }
                    } == true
                }
                return hash
            }
            val besideCopy = if (besideGame) {
                val candidate = saveProbes.besideCandidate(player, game, target, bound)
                val current = saveProtection.incident(incident.id) ?: throw RepairFoldersUnavailable()
                val bytes = besideBytesForRepair(candidate, current)
                val hash = bytes?.let { rommSaveHash(it) ?: throw RepairFoldersUnavailable() }
                if (stage && bytes != null && candidate != null && hash != null && seen.add(hash)) {
                    saveIncidentCopies.stageBackup(game, player, candidate.spot, bytes, current)
                }
                if (stage) candidate?.source?.takeUnless { it == BesideSaveSource.Missing }
                    ?.let { saveProtection.noteBesideSaveSource(current.id, it) }
                hash
            } else null
            // Give a shared Backup the live folder's label when both roots hold the same bytes.
            val liveCopy = read(liveSpot, live = true)
            val boundCopy = if (bound.identity == live.identity) liveCopy else read(spot(boundUri))
            return RepairCopies(boundCopy, liveCopy, if (besideGame) besideCopy else beside()?.let { read(it) }, liveSuspect,
                liveMatchesSynced = liveCopy != null && liveCopy == previous?.first, liveRawHash = liveRawHash)
        }

        override suspend fun uploadBackups(): Boolean {
            saveIncidentCopies.keep(game, player)
            return !saveIncidentCopies.hasQueued(game, player)
        }

        override suspend fun bind() {
            if (running()) throw RepairGameRunning()
            val before = playerSaveFolder(player) ?: throw RepairFoldersUnavailable()
            when (storageIdentity(before, packageName)) {
                live.identity -> Unit
                bound.identity -> saveProtection.rebound(player.saveFolderId())
                else -> throw RepairFoldersUnavailable()
            }
            if (before != liveUri) store.setPlayerSaveFolder(player.saveFolderId(), liveUri, durable = true)
            saveProtection.retargetBesideRepairs(incident.playerKey, live)
            // A crash after the preference write is also safe: startup binds from that preference.
            plugins.playerIds().mapNotNull(plugins::player).forEach { sharing ->
                if (sharing is SaveFolderHolder && sharing.saveFolderId() == player.saveFolderId()) sharing.bindSaveFolder(liveUri)
            }
        }

        override suspend fun place(selection: RepairState.Placing, copies: RepairCopies, backedUpLive: Boolean) {
            if (running()) throw RepairGameRunning()
            val spots = listOf(spot(liveUri))
            if (eden != null) saveProtection.observeEdenContext(incident.playerKey, eden.session, System.currentTimeMillis())
            ServerSaveFlow(playerSaves, plugins, store::saveAwaitsLoad, onHeadPlaced = saveIncidentCopies::headPlaced,
                awaitLoad = store::setSaveAwaitsLoad).repair(game, player, spots, origin, selection, copies.liveRawHash.takeIf { backedUpLive }) {
                if (running() || !readCopies(emptySet(), stage = false).sameBytes(copies)) throw RepairGameRunning()
                if (player !is EdenPlayer && playerSaveFolder(player)?.let { storageIdentity(it, packageName) } != live.identity)
                    throw RepairFoldersUnavailable()
            }
        }

        override suspend fun verify() {
            if (!isQuarantine && !besideGame) {
                val markers = holder.saveFolderMarkers
                val present = playerSaves.folderAccess(player, liveUri, allowWrite = false).list("")
                    ?.any { entry -> markers.any { it.equals(entry.name, ignoreCase = true) } }
                saveProtection.verified(incident.playerKey, live, incident.evidence, System.currentTimeMillis(),
                    packageInfo.packageName, packageInfo.firstInstallTime, present)
            }
        }
    }
}

internal suspend fun FoldcadeApp.saveRepairPrompt(incident: SaveIncident, subject: SaveIncidentGame,
    choice: RepairState.Choosing): SaveConflictPrompt? {
    val origin = subject.origin?.takeIf { it == store.rommOrigin() } ?: return null
    val listed = rommRepairSaves(plugins.library(subject.libraryId), subject.game(), origin)
    val head = listed.firstOrNull { it.id == choice.headId && it.slot != null && it.hash == choice.headHash } ?: return null
    if (listed.firstOrNull { it.slot == head.slot }?.id != head.id) return null
    val backup = listed.firstOrNull { it.id == choice.backupId && it.slot == null } ?: return null
    val folder = incident.live?.label ?: incident.bound?.label
    return SaveConflictPrompt(incident.id, subject.libraryId, subject.label,
        SaveConflictSide(SaveProtectionCopy.thisThor, backup.time, backup.size, folder?.let(SaveProtectionCopy::fromFolder),
            // A first link's copy is this device's own progress, not a suspected reset (§7.1).
            SaveProtectionCopy.mayBeNewGame.takeIf { choice.suspect && !incident.firstLink }),
        SaveConflictSide(SaveProtectionCopy.romm, head.time, head.size),
        focus = SaveConflictFocus.Remote, repair = choice,
        // Leaving both would place RomM's at the next launch, which is a choice the player didn't make.
        canKeepBoth = incident.kind == IncidentKind.MaybeNewSave && !incident.firstLink)
}
