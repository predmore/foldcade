package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveLocationProbe
import app.foldcade.language.BesideSaveSource
import app.foldcade.language.FolderRef
import app.foldcade.language.RootReading
import app.foldcade.language.SaveIncident
import app.foldcade.language.SaveIncidentGame
import app.foldcade.language.RepairState
import app.foldcade.language.blocksLaunch
import app.foldcade.plugins.explus.ExPlusPlayer
import app.foldcade.plugins.melonds.MelonDsPlayer
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.cancellation.CancellationException

internal val Player.probesContentSaves: Boolean get() = this is MelonDsPlayer || this is ExPlusPlayer

/** A melonDS beside-ROM write exposes its global save setting. The copy still belongs to the original game. */
internal fun SaveIncident.affectsSaveUse(player: Player, game: Game? = null): Boolean =
    playerKey == player.saveFolderId() && (gameIds.isEmpty() || besideGame && player is MelonDsPlayer || game?.let(::appliesTo) == true)

internal fun SaveIncident.gatesLaunch(player: Player, game: Game): Boolean = blocksLaunch && affectsSaveUse(player, game)

/** Probe the ROM's actual parent through a held storage tree or our own RomM media. */
internal class BesideGameSaves(
    private val docs: SaveDocuments,
    private val grants: List<SaveFolderGrant>,
    private val appPackage: String,
    private val rommMedia: Path?,
) {
    fun read(player: Player, game: Game, target: LaunchTarget.SharedFile, bound: FolderRef): BesideGameCandidate? {
        val probe = player as? SaveLocationProbe ?: return null
        val slot = player.saveDeclarations(game).singleOrNull()?.slot ?: return null
        val rom = Paths.get(target.path).toAbsolutePath().normalize()
        val directory = rom.parent ?: return null
        val romName = docs.displayName(target.uri)?.takeIf { savePathParts(it)?.size == 1 } ?: return null
        val file = probe.saveBesideGame(slot, romName)?.takeIf { it != romName && savePathParts(it)?.size == 1 } ?: return null
        val granted = grantedDirectory(directory)
        val direct = rommMedia?.takeIf { directory.startsWith(it) }
        if (granted == null && direct == null) return null
        val folderUri = granted ?: storageFolderDocument(storageIdentity(directory.toString()))
        val identity = storageIdentity(directory.toString()).takeIf { it.startsWith("primary:") } ?: storageIdentity(folderUri, appPackage)
        val root = FolderRef(identity, identity.removePrefix("primary:"))
        if (root.identity == bound.identity || storageFolderPath(storageFolderDocument(bound.identity)) == directory) return null

        val base = romName.substringBeforeLast('.')
        val filter = (player as? ExPlusPlayer)?.besideSaveArchiveFilter(slot, romName)
        val spot = SaveSpot(slot, folderUri, if (filter == null) file else "$base.zip")
        var blankBattery = false
        val read = try {
            val owned = direct != null && directory.toRealPath() == direct.toRealPath().resolve(direct.relativize(directory))
            val children: (String) -> List<SaveChild>? = if (owned) { relative -> diskChildren(directory, relative) }
                else if (granted != null) { relative -> docs.children(folderUri, relative) }
                else return BesideGameCandidate(root, spot, FileReading.Unreadable)
            val files = children("") ?: throw SaveNotPlaced()
            val readFile: (SaveChild, Int) -> ByteArray? = if (owned) { child, limit ->
                Files.newInputStream(Paths.get(URI(child.uri)), NOFOLLOW_LINKS).use { readSaveStream(it, limit) }
            } else { child, limit -> docs.readBounded(child.uri, limit) }
            if (filter == null) {
                val matches = files.filter { it.name == file }
                if (matches.size > 1) throw SaveNotPlaced()
                val child = matches.singleOrNull()
                if (child == null) FileReading.Missing
                else {
                    val bytes = readChild(child, SAVE_ZIP_LIMIT.toInt(), readFile)
                    // melonDS can create an empty .sav just by opening a ROM.
                    if (bytes.isEmpty()) FileReading.Missing else FileReading.Bytes(bytes)
                }
            } else {
                val entries = sortedMapOf<String, ByteArray>()
                var remaining = SAVE_ZIP_LIMIT.toInt()
                fun add(path: String, child: SaveChild) {
                    if (!filter.accepts(path, ByteArray(0))) return
                    if (path in entries) throw SaveNotPlaced()
                    val bytes = readChild(child, remaining, readFile)
                    entries[path] = bytes
                    remaining -= bytes.size
                }
                files.filter { it.name != romName }.forEach { add(it.name, it) }
                if (files.any { it.name == base && it.isFolder }) {
                    children("$base/saves").orEmpty().filter { it.isFolder && savePathParts(it.name)?.size == 1 }.forEach { named ->
                        val path = "$base/saves/${named.name}"
                        val contents = children(path) ?: throw SaveNotPlaced()
                        contents.forEach { add("$path/${it.name}", it) }
                    }
                }
                if (player.saveFolderKey in setOf("gba-emu", "gbc-emu")) {
                    // An initialized battery (plus the GBC clock) proves a write without carrying game progress.
                    blankBattery = entries.all { (name, bytes) -> name == file || name == "$base.rtc" && bytes.size == 4 } && entries[file]?.let { battery ->
                        battery.firstOrNull()?.let { fill -> (fill == 0.toByte() || fill == (-1).toByte()) && battery.all { it == fill } }
                    } == true
                }
                if (entries.isEmpty()) FileReading.Missing else FileReading.Bytes(saveZip(entries))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { FileReading.Unreadable }
        return BesideGameCandidate(root, spot, read, blankBattery)
    }

    private fun grantedDirectory(directory: Path): String? = grants.asSequence().filter { it.read }.mapNotNull { grant ->
        val authority = URI(grant.uri).authority
        if (authority != SharedPaths.EXTERNAL_STORAGE_AUTHORITY && authority != "$appPackage${SharedStorageProvider.AUTHORITY_SUFFIX}") return@mapNotNull null
        val id = folderTreeId(grant.uri)
        val path = storageFolderPath(storageFolderDocument(id)) ?: return@mapNotNull null
        if (!directory.startsWith(path)) return@mapNotNull null
        val relative = path.relativize(directory).toString()
        val childId = if (relative.isEmpty()) id else id.trimEnd('/') + (if (id.endsWith(':')) "" else "/") + relative
        Triple(grant, path.nameCount, childId)
    }.sortedWith(compareBy({ it.first.allFiles }, { -it.second })).firstOrNull()
        ?.let { (grant, _, id) -> folderDocumentUri(grant.uri, id) }

    private fun storageFolderPath(uri: String): Path? = when (folderDocumentId(uri)) {
        "primary:" -> Paths.get("/storage/emulated/0")
        "home:" -> Paths.get("/storage/emulated/0/Documents")
        else -> SharedPaths.externalStoragePath(uri)?.let { Paths.get(it) }
    }

    private fun diskChildren(directory: Path, relative: String): List<SaveChild>? {
        var folder = directory
        for (part in relative.split('/').filter(String::isNotEmpty)) {
            folder = folder.resolve(part)
            if (Files.notExists(folder, NOFOLLOW_LINKS)) return null
            if (!Files.isDirectory(folder, NOFOLLOW_LINKS)) throw SaveNotPlaced()
        }
        return Files.list(folder).use { paths -> paths.map { path ->
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            SaveChild(path.fileName.toString(), path.toUri().toString(), attrs.isDirectory, attrs.size())
        }.iterator().asSequence().toList() }
    }

    private fun readChild(child: SaveChild, limit: Int, read: (SaveChild, Int) -> ByteArray?): ByteArray {
        if (child.isFolder || child.size > limit) throw SaveNotPlaced()
        return read(child, limit) ?: throw SaveNotPlaced()
    }
}

internal data class BesideGameCandidate(val root: FolderRef, val spot: SaveSpot, val read: FileReading, val blankBattery: Boolean = false) {
    val source: BesideSaveSource? get() = when (read) {
        is FileReading.Bytes -> if (blankBattery) BesideSaveSource.Blank else BesideSaveSource.Progress
        FileReading.Missing -> BesideSaveSource.Missing
        FileReading.Unreadable -> null
    }

    fun reading() = RootReading(root, false, emptyList(), (read as? FileReading.Bytes)?.bytes?.let(::md5Hex),
        read != FileReading.Unreadable, null, null, spotFile = spot.fileName, besideGame = true)
}

/** A vanished source can be skipped after capture. An unreadable source may still contain new progress. */
internal fun besideBytesForRepair(candidate: BesideGameCandidate?, incident: SaveIncident): ByteArray? = when (val reading = candidate?.read) {
    is FileReading.Bytes -> reading.bytes.takeUnless { candidate.blankBattery }
    null, FileReading.Missing -> {
        // An earlier capture or blank battery permits restoring the watched folder. Any newer
        // bytes beside the ROM stay on disk, and this read gives no evidence that they were kept.
        if (incident.capturedAlternateHashes.isEmpty() && incident.kept.isEmpty() && incident.besideSource != BesideSaveSource.Blank)
            throw RepairFoldersUnavailable()
        null
    }
    FileReading.Unreadable -> throw RepairFoldersUnavailable()
}

/** The caller holds saveFlow across the folder grant, local capture and durable repair transition. */
internal suspend fun confirmBesideSaveFolder(
    store: SaveProtectionStore,
    incidentId: String,
    picked: String,
    watched: String?,
    hasRomm: (SaveIncidentGame) -> Boolean,
    preserveLocal: suspend (SaveIncident, SaveIncidentGame) -> BesideSaveSource,
    remember: () -> Boolean,
): Boolean {
    val current = store.incident(incidentId)?.takeIf {
        it.besideGame && it.repair == RepairState.Binding && it.bound?.identity == picked &&
            (watched == null || watched == picked) && it.live?.identity != picked
    } ?: return false
    val subject = current.subject ?: throw SaveNotPlaced()
    val restoreHead = hasRomm(subject)
    val source = if (restoreHead) null else preserveLocal(current, subject)
    if (!remember()) return false
    val latest = store.incident(incidentId) ?: return false
    store.put(latest.copy(repair = if (restoreHead) RepairState.Keeping else RepairState.Done,
        recoveryRequired = restoreHead, besideSource = source ?: latest.besideSource))
    return restoreHead
}
