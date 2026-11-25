package app.foldcade

import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderSearch
import app.foldcade.language.ConfigSavePath
import app.foldcade.language.FolderRef
import app.foldcade.language.RootReading
import app.foldcade.language.ProbeSnapshot
import app.foldcade.language.SaveIncident
import app.foldcade.language.spotChanged
import app.foldcade.plugins.armsx2.ArmsX2CardConfig
import app.foldcade.plugins.armsx2.ArmsX2Player

/** A card path is an observation target. Only a change to this game's files is evidence of a write. */
internal class ArmsX2SaveProbe(
    private val player: ArmsX2Player,
    private val saves: PlayerSaves,
    reader: GameFileReader?,
) {
    private val serial by lazy { reader?.let(player::saveSerial) }
    private val zipName = reader?.fileName.orEmpty().substringBeforeLast('.') + ".zip"

    fun config(access: SaveFolderAccess): ArmsX2CardConfig = player.cardConfig(access, serial)

    fun spot(uri: String, directory: String, card: String?): SaveSpot? {
        val game = serial ?: return null
        val name = card ?: return null
        val parent = if (directory.isEmpty()) name else "$directory/$name"
        return SaveSpot(ArmsX2Player.SAVE_SLOT, uri, zipName, search = SaveFolderSearch(parent, game))
    }

    fun outsideSpot(uri: String, card: String?): SaveSpot? =
        card?.takeIf { savePathParts(it)?.size == 1 && '\\' !in it }?.let { spot(uri, "", it) }

    fun outsideReading(config: ConfigSavePath, uri: String): RootReading? {
        val live = config.live ?: return null
        val spot = outsideSpot(uri, config.effective) ?: return null
        return try {
            RootReading(live, false, emptyList(), hash(spot), true, null, null, spotFile = spot.search?.parent)
        } catch (_: SaveNotPlaced) {
            RootReading(live, false, emptyList(), null, false, null, null, spotFile = spot.search?.parent)
        }
    }

    /** Outside cards and a card selection changed during play are kept apart from the working slot. */
    fun preserveReturn(
        pending: LibraryReturn,
        spots: List<SaveSpot>,
        after: ProbeSnapshot?,
        incident: SaveIncident?,
        store: SaveProtectionStore,
        copies: SaveIncidentCopies,
        packageName: String,
        readable: (FolderRef) -> String?,
    ): Boolean {
        val game = pending.game
        val before = pending.baseline
        val selections = listOfNotNull(before?.configSavePath, after?.configSavePath)
        fun spotId(spot: SaveSpot) = storageIdentity(spot.folderUri, packageName) to spot.search
        val placed = pending.spots.singleOrNull()
        val selected = after?.roots?.singleOrNull { it.bound }
        val cardChanged = placed != null && (spots.singleOrNull()?.let(::spotId) != spotId(placed) ||
            selected != null && selected.spotFile != placed.search?.parent)
        if (selections.isEmpty() && incident == null && !cardChanged) return false

        val working = (pending.spots + spots).distinctBy(::spotId)
        for (spot in working) stage(spot, pending, incident, copies)
        // A subsequent placement must check the working bytes even if the card changes again.
        if (working.isNotEmpty()) store.requireBackup(player.saveFolderId(), game)

        val candidates = selections.mapNotNull { selection -> selection.live?.let { it to selection.effective } } +
            listOfNotNull(incident?.live?.takeIf { incident.alternateSavePath != null }?.let { it to incident.alternateSavePath })
        val changedSelection = before != null && after != null && before.configSavePath != after.configSavePath
        for ((root, card) in candidates.distinct()) {
            val uri = readable(root) ?: continue
            val spot = outsideSpot(uri, card) ?: continue
            val reading = after?.roots?.firstOrNull { !it.bound && it.root == root && it.spotFile == card }
            if (incident == null && !pending.unprotectedSession && !pending.preserveAsBackup && !changedSelection &&
                (before == null || reading == null || !spotChanged(before, reading))) continue
            // A raw card can contain several games. Its bytes are never attributed to this one.
            if (saves.docs.children(uri, "")?.any { it.name == card && !it.isFolder } == true) continue
            stage(spot, pending, incident, copies)
        }
        if (incident != null && before != null && after != null) {
            for (reading in after.roots.filterNot { it.bound }) {
                val path = reading.spotFile ?: continue
                if (candidates.any { (root, card) -> reading.root == root && path == card }) continue
                if (!reading.spotReadable || !(reading.root == incident.live || spotChanged(before, reading))) continue
                val uri = readable(reading.root) ?: continue
                val spot = spot(uri, path.substringBeforeLast('/', ""), path.substringAfterLast('/')) ?: continue
                stage(spot, pending, incident, copies)
            }
        }
        return true
    }

    private fun stage(spot: SaveSpot, pending: LibraryReturn, incident: SaveIncident?, copies: SaveIncidentCopies) {
        val bytes = saves.readSpot(spot, strict = true, limit = SAVE_ZIP_LIMIT)?.first ?: return
        copies.stageBackup(pending.game, player, spot, bytes, incident)
    }

    fun hash(spot: SaveSpot): String? = saves.readSpot(spot, strict = true, limit = SAVE_ZIP_LIMIT)?.first?.let(::md5Hex)
}

internal fun String.toDocumentId(): String = if (startsWith("primary:")) this
    else substringBefore(':').uppercase(java.util.Locale.ROOT) + ":" + substringAfter(':')

/** Config names the candidate folder and card. The session judge still needs a stable before/after read. */
internal fun ArmsX2CardConfig.outsideCard(): ConfigSavePath? {
    if (directory != null) return null
    val ref = outsideDirectoryId?.let { FolderRef(it, it.removePrefix("primary:")) }
    return ConfigSavePath(expected = null, effective = cardName, live = ref, proves = false)
}
