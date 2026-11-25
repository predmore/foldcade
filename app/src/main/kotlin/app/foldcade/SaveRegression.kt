package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.ObservedSlot
import app.foldcade.api.plugin.Player
import app.foldcade.language.*
import app.foldcade.romm.rommSaveHash
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException

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

/** Context comes from the same bound root before launch and after return. */
internal fun returnRegressionContext(
    before: ProbeSnapshot?,
    after: ProbeSnapshot?,
    hosted: Boolean,
    previousProfile: String?,
    currentProfile: String?,
    reinstalled: Boolean,
    openIncident: Boolean,
): Set<RegressionContext> = buildSet {
    val first = before?.roots?.singleOrNull { it.bound }
    val last = after?.roots?.singleOrNull { it.bound }
    if (!hosted && first?.markersPresent == false && last?.markersPresent == true && first.root.identity == last.root.identity)
        add(RegressionContext.FreshRoot)
    if (previousProfile != null && currentProfile != null && previousProfile != currentProfile) add(RegressionContext.NewProfile)
    if (reinstalled) add(RegressionContext.Reinstalled)
    if (openIncident) add(RegressionContext.OpenIncident)
}

internal data class ReturnedSave(val spot: SaveSpot, val slot: ObservedSlot, val bytes: ByteArray)
internal data class SaveInspection(val hash: String?, val shape: SaveShape?)

internal fun inspectSave(bytes: ByteArray, folder: Boolean): SaveInspection =
    SaveInspection(rommSaveHash(bytes), saveShape(bytes, folder))

/** Compare with the verified local RomM copy, including when the server is offline. */
internal class SaveRegression(
    private val saves: PlayerSaves,
    private val store: SaveProtectionStore,
    private val baseline: suspend (Game, String, Boolean) -> SaveInspection?,
    private val inspect: (ByteArray, Boolean) -> SaveInspection = ::inspectSave,
) {
    fun capture(libraryId: String, game: Game, player: Player, spots: List<SaveSpot>): Pair<ObservedSaves, ReturnedSave?> {
        var returned: ReturnedSave? = null
        val observed = saves.changedSince(libraryId, game, player, spots) { spot, slot, bytes ->
            if (spots.size == 1) returned = ReturnedSave(spot, slot, bytes)
        }
        return observed to returned
    }

    suspend fun quarantine(subject: SaveIncidentGame, player: Player, returned: ReturnedSave, root: FolderRef?,
        context: Set<RegressionContext>): Boolean {
        val origin = subject.origin ?: return false
        val game = subject.game()
        val id = regressionIncidentId(game, player)
        val previous = store.incident(id)
        val folder = returned.spot.isFolderSave()
        val prior = try { baseline(game, returned.slot.slot, folder) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { SaveInspection(null, null).takeIf { context.isNotEmpty() } }
        if (prior == null && previous == null) return false
        val current = try { inspect(returned.bytes, folder) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }

        val same = current?.hash?.let { it == prior?.hash } == true
        val reason = when {
            same -> null
            context.isNotEmpty() -> SaveProtectionCopy.mayBeNewGame
            else -> current?.takeIf { it.hash != null }?.shape?.let { suspectRegression(prior?.shape, it) }
        }
        if (previous == null && reason == null) return false

        // The first read supplied both the observed hash and bytes. A failed inspection
        // cannot force another read of a mutable emulator file or prevent ordinary sync.
        val uri = saves.stageCopy(game, player, returned.spot, returned.bytes, origin)
        val incident = previous ?: SaveIncident(id, IncidentKind.MaybeNewSave, player.saveFolderId(), player.displayName,
            listOf(game.remoteKey), root, null, Evidence(EvidenceKind.SessionWrite, SaveProtectionCopy.mayBeNewGame),
            System.currentTimeMillis(), repair = RepairState.Keeping, subject = subject,
            quarantinedHash = current?.hash, quarantineSourceUri = uri)
        store.put(incident.copy(staged = (incident.staged + uri).distinct()))
        if (reason == null) return false // Preserve later progress as a Backup and hand it to ordinary sync.
        saves.noteHandedOver(subject.libraryId, game, player, listOf(returned.spot), ObservedSaves(listOf(returned.slot)))
        return true
    }
}
