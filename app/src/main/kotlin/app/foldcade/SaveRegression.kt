package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.Player
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import app.foldcade.plugins.romm.rommSyncedSave
import app.foldcade.plugins.romm.supportsRommRepair
import app.foldcade.romm.rommSaveHash
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

internal fun regressionIncidentId(game: Game, player: Player) =
    "MaybeNewSave:${player.saveFolderId()}:${game.backendId}:${game.remoteKey}"

internal fun SaveIncidentGame.game() = Game(libraryId, gameId, platformId, Availability.Cached, label)

internal fun SaveIncident.appliesTo(game: Game) = gameIds.isEmpty() || game.remoteKey in gameIds &&
    (subject?.libraryId?.let { it == game.backendId } != false)

internal fun incidentGame(game: Game, player: Player, targetUri: String?, origin: String?, reading: ProbeSnapshot?) =
    SaveIncidentGame(game.backendId, game.remoteKey, game.platformId, game.label, player.id, targetUri, origin,
        reading?.packageName, reading?.installFirstMillis)

internal fun saveShape(bytes: ByteArray, folder: Boolean): SaveShape? {
    if (!folder) return SaveShape(bytes.size.toLong(), 1, folder = false)
    val listed = zipEntryCount(bytes) ?: return null
    return try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entries = 0
            var files = 0
            var total = 0L
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                if (!entry.isDirectory) files++
                total += zip.skip(SAVE_ZIP_LIMIT - total + 1)
                if (total > SAVE_ZIP_LIMIT) return null
            }
            if (entries == listed) SaveShape(total, files, folder = true) else null
        }
    } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
}

internal fun SaveSpot.isFolderSave() = folders.isNotEmpty() || search != null

/** Inspect the verified local RomM receipt, so the same quarantine works on an offline return. */
internal class SaveRegression(
    private val saves: PlayerSaves,
    private val plugins: PluginHost,
    private val store: SaveProtectionStore,
    private val copies: SaveIncidentCopies,
) {
    suspend fun quarantine(
        subject: SaveIncidentGame,
        player: Player,
        spots: List<SaveSpot>,
        observed: ObservedSaves,
        root: FolderRef?,
        context: Set<RegressionContext>,
    ): Boolean {
        val game = subject.game()
        val library = plugins.library(subject.libraryId)
        if (subject.origin == null || !supportsRommRepair(library)) return false
        // The existing choice route can place exactly one slot.
        val slot = observed.slots.singleOrNull() ?: return false
        val spot = spots.singleOrNull { it.slot == slot.slot } ?: return false
        val id = regressionIncidentId(game, player)
        val previous = store.incident(id)
        val folderSave = spot.isFolderSave()
        val synced = rommSyncedSave(library, game, slot.slot, SAVE_ZIP_LIMIT)?.let {
            rommSaveHash(it) to saveShape(it, folderSave)
        }
        if (previous == null && synced?.second == null) return false
        val bytes = saves.readSpot(spot, strict = true, limit = SAVE_ZIP_LIMIT)?.first ?: throw SaveNotPlaced()
        if (md5Hex(bytes) != slot.contentHash) throw SaveNotPlaced()
        val hash = rommSaveHash(bytes) ?: throw SaveNotPlaced()
        if (synced?.first == hash) return false
        val shape = saveShape(bytes, folderSave)
        val reason = shape?.let { suspectRegression(synced?.second, it, context) }
        if (previous == null && reason == null) return false

        val uri = saves.stageCopy(game, player, spot, bytes, subject.origin)
        // Preserve the first unresolved reset choice. Subsequent progress is kept too.
        val incident = previous ?: SaveIncident(id, IncidentKind.MaybeNewSave, player.saveFolderId(), player.displayName,
            listOf(game.remoteKey), root, null, Evidence(EvidenceKind.SessionWrite, reason ?: SaveProtectionCopy.mayBeNewGame),
            System.currentTimeMillis(), repair = RepairState.Keeping, subject = subject, quarantinedHash = hash)
        store.put(incident.copy(staged = (incident.staged + uri).distinct()))
        saves.noteHandedOver(game.backendId, game, player, spots, observed)
        copies.keep(game, player)
        return true
    }
}
