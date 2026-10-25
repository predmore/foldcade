package app.foldcade

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import app.foldcade.api.plugin.FirmwareOffer
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.HostedSaveFolder
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.ObservedSlot
import app.foldcade.api.plugin.PlacedSave
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SaveFolderPart
import app.foldcade.api.plugin.SaveFolderSearch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.SortedMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * One save file in a player's folder: the [slot] a library knows, the folder
 * the user picked for the player, and the file name the player uses there.
 * A player that keeps the slot as [folders] has them moved as one zip, which
 * [fileName] then names.
 */
internal data class SaveSpot(
    val slot: String,
    val folderUri: String,
    val fileName: String,
    val folders: List<SaveFolderPart> = emptyList(),
    /** Set when the folders are found by name at read time, rather than named up front. */
    val search: SaveFolderSearch? = null,
)

/**
 * Held while the shell checks a save file and writes it, and while a player
 * changes a file in a folder Foldcade hosts, see [HostedSavesProvider]. A
 * player's write then lands before the check, which sees it, or after the
 * write, and never between them.
 */
internal object SaveFileLock

/** A file or folder below a folder, at [path] below it, separated by `/`. */
internal data class SaveEntry(
    val path: String,
    val uri: String,
    val isFolder: Boolean,
)

/** One document directly inside a folder. [size] is zero when the provider does not say. */
internal data class SaveChild(
    val name: String,
    val uri: String,
    val isFolder: Boolean,
    val size: Long,
)

/** Content-resolver work [PlayerSaves] needs, so its decisions run on the JVM in tests. */
internal interface SaveDocuments {
    /** Display name of the document at [uri], or null. */
    fun displayName(uri: String): String?

    /**
     * The document at [name] inside the tree [folderUri], or null. [name] may
     * hold folders separated by `/`.
     */
    fun find(folderUri: String, name: String): String?

    /**
     * A new empty document at exactly [name] inside [folderUri], or null.
     * A folder [name] holds is created when it is missing.
     */
    fun create(folderUri: String, name: String): String?

    fun read(uri: String): ByteArray?

    /** Replaces the whole document. */
    fun write(uri: String, bytes: ByteArray)

    /** True when the document is gone. A folder goes with all it holds. */
    fun delete(uri: String): Boolean

    /** Up to [size] bytes from the start of the document at [uri], or null. */
    fun head(uri: String, size: Int): ByteArray? = null

    /** MD5 of the document at [uri], or null when it cannot be read. */
    fun md5(uri: String): String? = read(uri)?.let(::md5Hex)

    /**
     * Copies [sourceUri] onto [targetUri], replacing it. False when the source
     * cannot be read or the copy cannot be written.
     */
    fun copy(sourceUri: String, targetUri: String): Boolean {
        val bytes = read(sourceUri) ?: return false
        return try {
            write(targetUri, bytes)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Length of the document at [uri], in bytes, or null when it is not known.
     * The default reads the document. A streaming implementation reports the
     * length without reading it, so a large file is not held to decide that.
     */
    fun length(uri: String): Long? = read(uri)?.size?.toLong()

    /**
     * Up to [length] bytes at [offset], fewer at the end of the file, or null
     * when it cannot be read. The default reads nothing.
     */
    fun readAt(uri: String, offset: Long, length: Int): ByteArray? = null

    /**
     * Everything below the folder at [name] inside [folderUri], or null when
     * there is no such folder. Throws when a folder cannot be listed, so a
     * folder that is there is never taken for one that is not.
     */
    fun list(folderUri: String, name: String): List<SaveEntry>? = null

    /** Makes the folder at exactly [name] inside [folderUri], and those it is in. False when it could not. */
    fun createFolder(folderUri: String, name: String): Boolean = false

    /** The names directly inside the tree [folderUri], or null when it cannot be listed. */
    fun childNames(folderUri: String): List<String>? = null

    /**
     * The documents directly inside [path], or the tree itself when [path] is empty.
     * Null when that folder is missing. Throws when it cannot be listed.
     */
    fun children(folderUri: String, path: String): List<SaveChild>? = null
}

/** What the shell last placed in, or read from, each save file. */
internal interface SaveMarks {
    fun mark(key: String): String?

    fun setMark(key: String, hash: String)

    fun clearMark(key: String)
}

/**
 * Moves a library's saves into the player's save folder before a launch, and
 * reads them back after the player returns. The shell touches only the one
 * file [Player.saveFileName] names in the folder the player declares, or the
 * folders [Player.saveFolders] names there, which move as one zip. A player can
 * instead name those folders by a search, see [Player.findSaveFolders]. The
 * shell finds them when it reads or writes, and sets the card up only on a launch.
 *
 * Nothing in the player's folder is overwritten unseen:
 * - a file whose hash differs from the last one the shell placed or read was
 *   written outside Foldcade, or by a session whose return was missed. It is
 *   handed to the library through [changedSince] before anything is placed;
 * - [place] refuses a file that changed again after that, or one it cannot read;
 * - a file about to be replaced is copied to [backups] first;
 * - a placed file is read back and must match the library's hash. A write that
 *   fails puts the old bytes back.
 * Folders follow the same rules, with the hash of their zip.
 *
 * A placement is recorded as pending before it writes. A failed write is undone,
 * or its leftover bytes are recorded as torn: those bytes are not progress, so
 * [changedSince] does not hand them over and the next [place] replaces them.
 * Folders still pending when read stopped part way, as when the app died, and
 * are recorded as torn too.
 * Callers run one save flow at a time. A zip handed over waits in [handover]
 * until the library has kept it.
 */
internal class PlayerSaves(
    private val docs: SaveDocuments,
    private val marks: SaveMarks,
    private val backups: Path,
    private val handover: Path,
    private val gameFile: (String) -> GameFileReader = { _ -> UnreadableGameFile },
) {
    /**
     * False when [folderUri], just picked for [player], holds none of its
     * [SaveFolderHolder.saveFolderMarkers], so it is not the player's folder and no
     * save would move through it. A player with no markers, or a folder that cannot
     * be listed, passes.
     */
    fun looksLikeSaveFolder(player: Player?, folderUri: String): Boolean {
        val markers = (player as? SaveFolderHolder)?.saveFolderMarkers.orEmpty()
        if (markers.isEmpty()) return true
        val names = try {
            docs.childNames(folderUri)
        } catch (_: Exception) {
            null
        } ?: return true
        return names.any { name -> markers.any { it.equals(name, ignoreCase = true) } }
    }

    /**
     * [forLaunch] is true on the way into the player and false on the way back.
     * A card is created or rewritten only on the way in. A slot whose setup
     * changed the folder is added to [changed], the same list a placed save uses,
     * so the player starts fresh and reads what was just written.
     */
    fun spots(
        player: Player,
        game: Game,
        target: LaunchTarget,
        forLaunch: Boolean = false,
        changed: MutableList<String>? = null,
    ): List<SaveSpot> {
        val uri = when (target) {
            is LaunchTarget.ContentUri -> target.uri
            is LaunchTarget.SharedFile -> target.uri
            else -> return emptyList()
        }
        val romName = docs.displayName(uri) ?: return emptyList()
        val header by lazy {
            if (player.romHeaderSize > 0) docs.head(uri, player.romHeaderSize) ?: ByteArray(0) else ByteArray(0)
        }
        val reader = gameFile(uri)
        return player.saveDeclarations(game).mapNotNull { declaration ->
            val folder = declaration.locationUri ?: return@mapNotNull null
            val access = folderAccess(player, folder, forLaunch)
            // A throw while setting a card up is a save that could not be placed. On the way back it is no spot.
            val search = try {
                player.findSaveFolders(declaration.slot, reader, access, forLaunch)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (forLaunch) throw SaveNotPlaced() else null
            }
            if (access.changed) changed?.add(declaration.slot)
            if (search != null && validSearch(search)) {
                val zipName = romName.substringBeforeLast('.').ifEmpty { romName } + ".zip"
                return@mapNotNull SaveSpot(declaration.slot, folder, zipName, search = search)
            }
            val folders = player.saveFolders(declaration.slot, romName, header)
            if (folders != null) {
                if (!validFolders(folders)) return@mapNotNull null
                val zipName = romName.substringBeforeLast('.').ifEmpty { romName } + ".zip"
                return@mapNotNull SaveSpot(declaration.slot, folder, zipName, folders)
            }
            val name = player.saveFileName(declaration.slot, romName) ?: return@mapNotNull null
            SaveSpot(declaration.slot, folder, name)
        }
    }

    /**
     * Saves in [spots] whose bytes differ from the last ones the shell placed or
     * read. A file that holds the bytes of a failed placement is not progress
     * and is left out. Any other bytes are handed over, even after a failed
     * placement, because the player may have run on its own since.
     */
    fun changedSince(libraryId: String, game: Game, player: Player, spots: List<SaveSpot>): ObservedSaves {
        val changed = spots.mapNotNull { spot ->
            val (bytes, uri) = readSpot(spot) ?: return@mapNotNull null
            val hash = md5Hex(bytes)
            val key = key(libraryId, game, player, spot)
            when (hash) {
                marks.mark(key) -> null
                // The last placement wrote its bytes but stopped before it was recorded.
                marks.mark(pendingKey(key)) -> {
                    settle(key, hash)
                    null
                }
                marks.mark(tornKey(key)) -> null
                else -> if ((spot.folders.isNotEmpty() || spot.search != null) && marks.mark(pendingKey(key)) != null) {
                    // A folder placement is many writes, and one still pending stopped part way, so
                    // these files are half the old save and half the new. Later changes are progress.
                    marks.clearMark(pendingKey(key))
                    marks.setMark(tornKey(key), hash)
                    null
                } else {
                    ObservedSlot(spot.slot, hash, player.id, uri ?: stage(key, bytes))
                }
            }
        }
        return ObservedSaves(changed)
    }

    /** Call once the library has kept [observed]. */
    fun noteHandedOver(libraryId: String, game: Game, player: Player, spots: List<SaveSpot>, observed: ObservedSaves) {
        for (slot in observed.slots) {
            val spot = spots.firstOrNull { it.slot == slot.slot } ?: continue
            val key = key(libraryId, game, player, spot)
            settle(key, slot.contentHash)
            Files.deleteIfExists(handover.resolve("$key.zip"))
        }
    }

    /**
     * Writes each of [placed] into its spot. False when a write could not be
     * finished and checked. The game must not start then, or it would play
     * from a save the library does not know. Each slot whose file got new
     * bytes is added to [changed], so a player still holding the game can be
     * started fresh.
     */
    fun place(
        libraryId: String,
        game: Game,
        player: Player,
        spots: List<SaveSpot>,
        placed: List<PlacedSave>,
        changed: MutableList<String>? = null,
    ): Boolean {
        for (spot in spots) {
            val save = placed.firstOrNull { it.slot == spot.slot } ?: continue
            val key = key(libraryId, game, player, spot)
            val bytes = docs.read(save.contentUri) ?: return false
            if (md5Hex(bytes) != save.contentHash) return false
            val outcome = synchronized(SaveFileLock) {
                if (spot.folders.isEmpty() && spot.search == null) placeFile(key, player, spot, bytes) else placeFolders(key, player, spot, bytes)
            }
            when (outcome) {
                Placing.Failed -> return false
                Placing.Written -> changed?.add(spot.slot)
                Placing.AlreadyThere -> Unit
            }
        }
        return true
    }

    /**
     * Writes the firmware in [target] that [player] chooses, see [Player.chooseFirmware],
     * into [folder], the folder the user picked for it, or else the one its save
     * declarations for [game] name, unless that is a folder Foldcade hosts, which holds
     * saves alone. Each file [Player.firmwareFileName] names is offered with its first
     * [Player.firmwareHeaderSize] bytes. A file there is replaced only when it holds what
     * the shell last wrote there, so a library's new copy follows, and a file the user
     * put there or changed stays. A player that no longer needs it, see [Player.needsFirmware],
     * writes nothing. A written file is read back; one that does not match goes, and a
     * small file it replaced comes back. An exception from the player writes nothing.
     * Never throws, since firmware does not stop a launch. Returns the names written.
     */
    fun placeFirmware(player: Player, game: Game, target: LaunchTarget, folder: String? = null): List<String> {
        val firmware = (target as? LaunchTarget.ContentUri)?.firmware.orEmpty()
        if (firmware.isEmpty()) return emptyList()
        val into = folder ?: if (player is HostedSaveFolder) return emptyList() else try {
            player.saveDeclarations(game).firstNotNullOfOrNull { it.locationUri }
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val needed = try {
            player.needsFirmware(folderAccess(player, into, allowWrite = false))
        } catch (_: Exception) {
            true
        }
        if (!needed) return emptyList()
        val named = try {
            firmware.mapNotNull { file ->
                val name = player.firmwareFileName(file.name)?.takeIf { savePathParts(it) != null }
                    ?: return@mapNotNull null
                file to name
            }
        } catch (_: Exception) {
            return emptyList()
        }
        if (named.isEmpty()) return emptyList()
        val headerSize = try {
            player.firmwareHeaderSize
        } catch (_: Exception) {
            return emptyList()
        }
        if (headerSize < 0) return emptyList()
        val offers = named.map { (file, _) ->
            val header = if (headerSize == 0) {
                ByteArray(0)
            } else {
                try {
                    docs.head(file.contentUri, headerSize) ?: ByteArray(0)
                } catch (_: Exception) {
                    ByteArray(0)
                }
            }
            FirmwareOffer(file.name, header)
        }
        val chosen = try {
            player.chooseFirmware(offers).map { it.name }.toSet()
        } catch (_: Exception) {
            return emptyList()
        }
        return named.mapNotNull { (file, name) ->
            if (file.name !in chosen) return@mapNotNull null
            try {
                if (synchronized(SaveFileLock) { placeFirmwareFile(into, name, file.contentUri) }) name else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun placeFirmwareFile(folder: String, name: String, source: String): Boolean {
        val key = "firmware:$folder/$name"
        val hash = docs.md5(source) ?: return false
        val existing = docs.find(folder, name)
        val currentHash = existing?.let(docs::md5)
        val mine = marks.mark(key)
        // Unreadable, or not the bytes the shell wrote or left: the user's own, which stays.
        if (existing != null && (currentHash == null || currentHash.let { it != hash && it != mine && mine != ANY_BYTES })) return false
        // Already there. Bytes the user put there stay theirs, so a later copy does not replace them.
        // Under the any-bytes mark they are the shell's own write, finished before it could be marked.
        if (currentHash != null && currentHash == hash) {
            if (mine == ANY_BYTES) marks.setMark(key, hash)
            return false
        }
        // Until the write is settled, whatever is there is the shell's, so one cut short, even by the
        // app dying as it makes the file, is replaced next time rather than kept as the user's.
        marks.setMark(key, ANY_BYTES)
        val written = existing ?: docs.create(folder, name) ?: run {
            // Nothing was written, so whatever the folder holds, once it can be reached again, keeps its mark.
            if (mine == null) marks.clearMark(key) else marks.setMark(key, mine)
            return false
        }
        // A small file can be put back. A large one, such as a firmware image, is not held in memory:
        // a failed check deletes a file the shell just created, and leaves one that was already there
        // under the any-bytes mark so the next launch replaces it.
        val previous = if (existing != null) restorableFirmware(existing) else null
        val checked = try {
            docs.copy(source, written) && docs.md5(written) == hash
        } catch (_: Exception) {
            false
        }
        if (checked) {
            marks.setMark(key, hash)
            return true
        }
        // Gone only when the shell's own delete says so: a file it cannot find may be in a folder it cannot reach.
        val gone = try {
            when {
                previous != null -> {
                    docs.write(written, previous)
                    false
                }
                existing == null -> docs.delete(written)
                else -> false
            }
        } catch (_: Exception) {
            false
        }
        if (gone) {
            marks.clearMark(key)
            return false
        }
        if (existing != null && previous == null) return false
        // What is there now is the shell's. Bytes it cannot find or read keep the mark they have, so they are replaced.
        try {
            val left = docs.find(folder, name)?.let(docs::md5) ?: return false
            marks.setMark(key, left)
        } catch (_: Exception) {
            Unit
        }
        return false
    }

    /** The file at [uri] when it is small enough to put back, or null. */
    private fun restorableFirmware(uri: String): ByteArray? {
        val known = try {
            docs.length(uri)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
        if (known != null && known > FIRMWARE_RESTORE_LIMIT) return null
        val bytes = try {
            docs.read(uri)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null
        return bytes.takeIf { it.size <= FIRMWARE_RESTORE_LIMIT }
    }

    private enum class Placing { Failed, AlreadyThere, Written }

    private fun placeFile(key: String, player: Player, spot: SaveSpot, bytes: ByteArray): Placing {
        val hash = md5Hex(bytes)
        val existing = docs.find(spot.folderUri, spot.fileName)
        val current = existing?.let(docs::read)
        // A file that is there and cannot be read is never written over blind.
        if (existing != null && current == null) return Placing.Failed
        val currentHash = current?.let(::md5Hex)
        if (currentHash == hash) {
            settle(key, hash)
            return Placing.AlreadyThere
        }
        // Bytes the library has not kept, such as a player still writing: hand them over first.
        if (currentHash != null && currentHash !in known(key)) return Placing.Failed
        val target = existing ?: docs.create(spot.folderUri, spot.fileName) ?: return Placing.Failed
        if (current != null) backup(player, spot.fileName, current)
        marks.setMark(pendingKey(key), hash)
        val written = try {
            docs.write(target, bytes)
            docs.read(target)
        } catch (_: Exception) {
            null
        }
        if (written == null || md5Hex(written) != hash) {
            undo(key, target, current)
            return Placing.Failed
        }
        settle(key, hash)
        return Placing.Written
    }

    /**
     * Makes the folders [zip] holds match it. A folder it does not hold keeps
     * what it has, and the hash checked and marked is that of the whole spot.
     */
    private fun placeFolders(key: String, player: Player, spot: SaveSpot, zip: ByteArray): Placing {
        val allowed = spot.search?.let { search ->
            val roots = saveZipRoots(zip) ?: return Placing.Failed
            if (savePathParts(search.parent) == null || roots.isEmpty() ||
                roots.any { savePathParts(it)?.size != 1 || !saveFolderNameMatches(it, search.nameContains) }
            ) {
                return Placing.Failed
            }
            roots
        } ?: spot.folders.map { it.name }.toSet()
        val incoming = saveEntries(zip, allowed) ?: return Placing.Failed
        val current = when (val held = readFolders(spot)) {
            Held.Unreadable -> return Placing.Failed
            Held.Missing -> FolderSave.EMPTY
            is Held.Found -> held.save
        }
        val parts = incoming.filterValues { it != null }.keys.map(::partOf).toSet()
        val wanted = sortedMapOf<String, ByteArray?>()
        current.entries.filterKeys { partOf(it) !in parts }.let(wanted::putAll)
        incoming.filterKeys { partOf(it) in parts }.let(wanted::putAll)
        val hash = md5Hex(saveZip(wanted))
        val currentZip = current.entries.takeIf { it.isNotEmpty() }?.let(::saveZip)
        val currentHash = currentZip?.let(::md5Hex)
        if (currentHash == hash) {
            settle(key, hash)
            return Placing.AlreadyThere
        }
        // Folders with no file in them hold no progress, so there is nothing to hand over or keep.
        val progress = current.entries.values.any { it != null }
        if (progress && currentHash !in known(key)) return Placing.Failed
        if (progress && currentZip != null) backup(player, spot.fileName, currentZip)
        marks.setMark(pendingKey(key), hash)
        val placed = try {
            writeFolders(spot, current, wanted)
            folderHash(spot) == hash
        } catch (_: Exception) {
            false
        }
        if (!placed) {
            undoFolders(key, spot, current)
            return Placing.Failed
        }
        settle(key, hash)
        return Placing.Written
    }

    /** Turns what [from] holds into [to]: what is gone goes first, a folder's files before it. */
    private fun writeFolders(spot: SaveSpot, from: FolderSave, to: SortedMap<String, ByteArray?>) {
        for (name in from.entries.keys.reversed()) {
            if (name in to) continue
            if (!docs.delete(from.uris.getValue(name))) error("save could not be removed")
        }
        for ((name, bytes) in to) {
            val path = documentPath(spot, name)
            if (bytes == null) {
                if (name !in from.entries && !docs.createFolder(spot.folderUri, path)) error("save folder could not be made")
                continue
            }
            if (from.entries[name]?.contentEquals(bytes) == true) continue
            val uri = from.uris[name] ?: docs.create(spot.folderUri, path) ?: error("save could not be made")
            docs.write(uri, bytes)
        }
    }

    /** After a failed write: puts [previous] back, or records what is left as torn. */
    private fun undoFolders(key: String, spot: SaveSpot, previous: FolderSave) {
        val undone = try {
            val now = when (val held = readFolders(spot)) {
                Held.Unreadable -> error("save could not be read")
                Held.Missing -> FolderSave.EMPTY
                is Held.Found -> held.save
            }
            writeFolders(spot, now, previous.entries)
            folderHash(spot) == previous.entries.takeIf { it.isNotEmpty() }?.let { md5Hex(saveZip(it)) }
        } catch (_: Exception) {
            false
        }
        if (undone) {
            marks.clearMark(pendingKey(key))
            return
        }
        val left = try {
            folderHash(spot)
        } catch (_: Exception) {
            null
        }
        if (left != null) {
            marks.setMark(tornKey(key), left)
            // What is left is recorded, so a change after this is progress.
            marks.clearMark(pendingKey(key))
        }
    }

    /** The hash of [spot]'s zip, or null when it holds nothing. Throws when it cannot be read. */
    private fun folderHash(spot: SaveSpot): String? = when (val held = readFolders(spot)) {
        Held.Unreadable -> error("save could not be read")
        Held.Missing -> null
        is Held.Found -> md5Hex(saveZip(held.save.entries))
    }

    /** The hashes a file may hold and still be replaced: what was placed or read, or a placement that stopped. */
    private fun known(key: String): Set<String> =
        setOfNotNull(marks.mark(key), marks.mark(pendingKey(key)), marks.mark(tornKey(key)))

    /** What Settings shows for [player], read from [folderUri]. Never writes. */
    fun saveSync(player: Player, folderUri: String): app.foldcade.api.plugin.SaveSyncState? = try {
        player.saveSyncState(folderAccess(player, folderUri, allowWrite = false))
    } catch (_: Exception) {
        null
    }

    /**
     * What [player] needs once before it can start, read from [folderUri], or
     * asked with no folder when none is picked. Never writes. A throw means it
     * does not say, so the launch goes on.
     */
    fun setupSteps(player: Player, folderUri: String?): String? = try {
        player.setupSteps(folderUri?.let { folderAccess(player, it, allowWrite = false) })
    } catch (_: Exception) {
        null
    }

    private fun folderAccess(player: Player, folderUri: String, allowWrite: Boolean) =
        DocumentSaveFolder(docs, folderUri, allowWrite) { name, bytes -> backup(player, name, bytes) }

    /** The bytes [spot] holds and the document they are in, or null when it holds none or cannot be read. */
    private fun readSpot(spot: SaveSpot): Pair<ByteArray, String?>? {
        if (spot.folders.isNotEmpty() || spot.search != null) {
            val held = readFolders(spot) as? Held.Found ?: return null
            // Folders with no file are no save, and a zip of them could never be placed.
            if (held.save.entries.values.none { it != null }) return null
            return saveZip(held.save.entries) to null
        }
        val uri = docs.find(spot.folderUri, spot.fileName) ?: return null
        return (docs.read(uri) ?: return null) to uri
    }

    /** Everything in [spot]'s folders, named as in its zip. A search lists its parent and takes the matches. */
    private fun readFolders(spot: SaveSpot): Held {
        val parts = try {
            folderParts(spot)
        } catch (_: Exception) {
            return Held.Unreadable
        } ?: return Held.Unreadable
        if (parts.isEmpty()) return Held.Missing
        val entries = sortedMapOf<String, ByteArray?>()
        val uris = HashMap<String, String>()
        try {
            for (part in parts) {
                val listed = docs.list(spot.folderUri, part.path) ?: continue
                for (entry in listed) {
                    val name = "${part.name}/${entry.path}" + if (entry.isFolder) "/" else ""
                    entries[name] = if (entry.isFolder) null else docs.read(entry.uri) ?: return Held.Unreadable
                    uris[name] = entry.uri
                }
            }
        } catch (_: Exception) {
            return Held.Unreadable
        }
        return if (entries.isEmpty()) Held.Missing else Held.Found(FolderSave(entries, uris))
    }

    /** A zip the library reads from a file, as it has no document. */
    private fun stage(key: String, bytes: ByteArray): String {
        Files.createDirectories(handover)
        val file = handover.resolve("$key.zip")
        Files.write(file, bytes)
        return file.toUri().toString()
    }

    /** The folders [spot] moves. A search takes the matching children of its parent. */
    private fun folderParts(spot: SaveSpot): List<SaveFolderPart>? {
        val search = spot.search ?: return spot.folders
        if (savePathParts(search.parent) == null) return null
        val children = docs.children(spot.folderUri, search.parent) ?: return emptyList()
        return children.mapNotNull { child ->
            if (!child.isFolder || savePathParts(child.name)?.size != 1) return@mapNotNull null
            if (!saveFolderNameMatches(child.name, search.nameContains)) return@mapNotNull null
            SaveFolderPart(child.name, "${search.parent}/${child.name}")
        }
    }

    /** Where the entry [name] of [spot]'s zip lives, below the player's folder. */
    private fun documentPath(spot: SaveSpot, name: String): String {
        val partName = partOf(name)
        val base = spot.folders.firstOrNull { it.name == partName }?.path
            ?: spot.search?.takeIf { saveFolderNameMatches(partName, it.nameContains) }?.let { "${it.parent}/$partName" }
            ?: error("save could not be placed")
        return base + "/" + name.substringAfter('/').removeSuffix("/")
    }

    /** What a spot kept as folders holds. */
    private sealed interface Held {
        data object Missing : Held

        data object Unreadable : Held

        class Found(val save: FolderSave) : Held
    }

    /** A spot's folders: zip entries and the document each is. */
    private class FolderSave(val entries: SortedMap<String, ByteArray?>, val uris: Map<String, String>) {
        companion object {
            val EMPTY = FolderSave(sortedMapOf(), emptyMap())
        }
    }

    /** Records [hash] as what the shell and the library agree the file holds. */
    private fun settle(key: String, hash: String) {
        marks.setMark(key, hash)
        marks.clearMark(pendingKey(key))
        marks.clearMark(tornKey(key))
    }

    /**
     * After a failed write: puts [previous] back, or removes a file the write
     * created. When that does not work either, whatever the file now holds is
     * recorded as torn, so it is never handed over as progress.
     */
    private fun undo(key: String, target: String, previous: ByteArray?) {
        val undone = try {
            if (previous == null) {
                docs.delete(target)
            } else {
                docs.write(target, previous)
                docs.read(target)?.let { md5Hex(it) == md5Hex(previous) } ?: false
            }
        } catch (_: Exception) {
            false
        }
        if (undone) {
            marks.clearMark(pendingKey(key))
            return
        }
        val left = try {
            docs.read(target)
        } catch (_: Exception) {
            null
        }
        if (left != null) marks.setMark(tornKey(key), md5Hex(left))
    }

    /**
     * Keeps the newest [BACKUPS_KEPT] replaced files per player. The copy is read
     * back before this returns, so a caller can delete the original only once the
     * backup holds every byte.
     */
    private fun backup(player: Player, fileName: String, bytes: ByteArray) {
        val dir = backups.resolve(safeName(player.id))
        Files.createDirectories(dir)
        val stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC).format(Instant.now())
        val file = dir.resolve("$stamp-${safeName(fileName)}")
        Files.write(file, bytes)
        if (!Files.readAllBytes(file).contentEquals(bytes)) error("backup did not match")
        val all = Files.list(dir).use { stream -> stream.collect(java.util.stream.Collectors.toList()) }
        all.sortedByDescending { it.fileName.toString() }.drop(BACKUPS_KEPT).forEach { Files.deleteIfExists(it) }
    }

    companion object {
        const val BACKUPS_KEPT = 20

        /** One mark per library, game, player, slot, and file. Hashed so any key fits a preference name. */
        fun key(libraryId: String, game: Game, player: Player, spot: SaveSpot): String =
            sha256("$libraryId\u0000${game.remoteKey}\u0000${player.id}\u0000${spot.slot}\u0000${spot.fileName}")

        /**
         * A firmware mark that makes whatever is there the shell's: set while it writes,
         * and kept when what a failed write left cannot be read.
         */
        private const val ANY_BYTES = "any-bytes"

        private fun pendingKey(key: String): String = "placing:$key"

        private fun tornKey(key: String): String = "torn:$key"

        private fun safeName(name: String): String =
            name.map { if (it.isLetterOrDigit() || it in "._-() ") it else '_' }.joinToString("").ifBlank { "save" }

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/**
 * The folders and file name in a save name such as `mGBA/Game.srm`. Null for
 * an empty part, `.` or `..`, so a save never lands outside the folder the user picked.
 */
internal fun savePathParts(name: String): List<String>? {
    val parts = name.split('/')
    if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
    return parts
}

/** A search the shell can follow: a path inside the save folder, and a name to look for. */
private fun validSearch(search: SaveFolderSearch): Boolean =
    savePathParts(search.parent) != null && folderKey(search.nameContains).isNotEmpty()

/**
 * [name], upper-cased with `-` and `_` removed, contains [contains] written the same way.
 * How a PS2 folder card groups one game's saves, which share its serial.
 * A name that starts with `_` is never a match: PCSX2 keeps a deleted save as
 * `_pcsx2_deleted_<name>`, and a real save folder does not start with `_`.
 */
internal fun saveFolderNameMatches(name: String, contains: String): Boolean {
    if (name.startsWith("_")) return false
    val needle = folderKey(contains)
    return needle.isNotEmpty() && needle in folderKey(name)
}

private fun folderKey(name: String): String = buildString(name.length) {
    for (char in name) {
        if (char == '-' || char == '_') continue
        append(char.uppercaseChar())
    }
}

/** A game file this shell cannot read. A player that does not ask gets nothing, not a failure. */
private object UnreadableGameFile : GameFileReader {
    override fun read(offset: Long, length: Int): ByteArray? = null
}

/** Folders the shell can move: at least one, each name one distinct part and each path inside the player's folder. */
private fun validFolders(folders: List<SaveFolderPart>): Boolean =
    folders.isNotEmpty() &&
        folders.map { it.name }.distinct().size == folders.size &&
        folders.all { savePathParts(it.name)?.size == 1 && savePathParts(it.path) != null }

/** The part an entry of a save zip is in: its first folder. */
private fun partOf(name: String): String = name.substringBefore('/')

/** The largest a save zip may unpack to, so a broken or hostile one cannot fill memory. */
private const val SAVE_ZIP_LIMIT = 256L * 1024 * 1024

/**
 * One fixed local date and time for every entry, so a zip's bytes depend only on
 * its entries. A zip keeps local time, converted in the zone in force when the
 * entry is made, so this is worked out then too: the zone can change while Foldcade runs.
 */
private fun saveZipTime(): Long =
    LocalDateTime.of(2000, 1, 1, 0, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** [entries] as a zip whose bytes depend only on them: in name order, with one fixed time. */
internal fun saveZip(entries: Map<String, ByteArray?>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, bytes) in entries.toSortedMap()) {
            zip.putNextEntry(ZipEntry(name).apply { time = saveZipTime() })
            if (bytes != null) zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/**
 * The first folder of each entry in [zip], or null when [zip] is not a whole zip.
 * Used to refuse a save that names a folder the search does not allow.
 */
internal fun saveZipRoots(zip: ByteArray): Set<String>? {
    val listed = zipEntryCount(zip) ?: return null
    val roots = linkedSetOf<String>()
    var read = 0
    try {
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                read++
                val first = entry.name.removeSuffix("/").substringBefore('/')
                if (first.isEmpty()) return null
                roots += first
            }
        }
    } catch (_: Exception) {
        return null
    }
    if (read != listed) return null
    return roots
}

/**
 * The entries of a save zip, a folder's name ending in `/` with null bytes.
 * Every folder below a part is named, so two zips of the same files have the
 * same entries, whichever folders they list. Null when [zip] is not a whole
 * zip, holds no file, unpacks too large, or names anything outside [parts]: an
 * unknown first folder, an empty name, `.`, `..`, or one name twice.
 */
internal fun saveEntries(zip: ByteArray, parts: Set<String>): SortedMap<String, ByteArray?>? {
    // A zip cut short still reads as one with fewer files, which would remove the rest.
    val listed = zipEntryCount(zip) ?: return null
    val entries = sortedMapOf<String, ByteArray?>()
    var total = 0L
    var read = 0
    try {
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                read++
                val isFolder = entry.name.endsWith("/")
                val segments = entry.name.removeSuffix("/").split('/')
                if (segments.first() !in parts) return null
                if (segments.any { it.isEmpty() || it == "." || it == ".." || '\\' in it }) return null
                // A part's own folder is not an entry. A file cannot be one.
                if (segments.size == 1) {
                    if (isFolder) continue else return null
                }
                for (depth in 2 until segments.size) entries[segments.take(depth).joinToString("/") + "/"] = null
                val name = entry.name.removeSuffix("/") + if (isFolder) "/" else ""
                if (isFolder) {
                    entries[name] = null
                    continue
                }
                if (name in entries) return null
                val bytes = readCapped(input, SAVE_ZIP_LIMIT - total) ?: return null
                total += bytes.size
                entries[name] = bytes
            }
        }
    } catch (_: Exception) {
        return null
    }
    if (read != listed) return null
    // A name that is both a file and a folder cannot be written.
    if (entries.any { (name, bytes) -> bytes != null && "$name/" in entries }) return null
    return entries.takeIf { it.values.any { bytes -> bytes != null } }
}

/**
 * How many entries the zip's central directory lists, read from its end
 * record, or null when [zip] has none: it is cut short, or not a zip.
 */
private fun zipEntryCount(zip: ByteArray): Int? {
    val earliest = maxOf(0, zip.size - ZIP_END_RECORD - 0xffff)
    for (at in zip.size - ZIP_END_RECORD downTo earliest) {
        if (zip[at] == 0x50.toByte() && zip[at + 1] == 0x4b.toByte() && zip[at + 2] == 0x05.toByte() && zip[at + 3] == 0x06.toByte()) {
            return (zip[at + 10].toInt() and 0xff) or ((zip[at + 11].toInt() and 0xff) shl 8)
        }
    }
    return null
}

private const val ZIP_END_RECORD = 22

/** All of [input], or null when it holds more than [limit] bytes. */
private fun readCapped(input: InputStream, limit: Long): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var read = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return out.toByteArray()
        read += count
        if (read > limit) return null
        out.write(buffer, 0, count)
    }
}

internal fun md5Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

private fun digestHex(input: InputStream): String {
    val digest = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** A firmware file at most this large is held so a failed write can put it back. */
private const val FIRMWARE_RESTORE_LIMIT = 16 * 1024 * 1024

/** [SaveDocuments] over a [ContentResolver] and the tree grants the user gave. */
internal class AndroidSaveDocuments(private val resolver: ContentResolver) : SaveDocuments {
    override fun displayName(uri: String): String? = try {
        resolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    override fun find(folderUri: String, name: String): String? {
        val tree = Uri.parse(folderUri)
        val parts = savePathParts(name) ?: return null
        return try {
            var parent = DocumentsContract.getTreeDocumentId(tree)
            for (part in parts.dropLast(1)) {
                parent = child(tree, parent, part, directory = true) ?: return null
            }
            child(tree, parent, parts.last(), directory = false)
                ?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it).toString() }
        } catch (_: Exception) {
            null
        }
    }

    override fun create(folderUri: String, name: String): String? {
        val tree = Uri.parse(folderUri)
        val parts = savePathParts(name) ?: return null
        val created = try {
            var parent = DocumentsContract.getTreeDocumentId(tree)
            for (part in parts.dropLast(1)) {
                parent = child(tree, parent, part, directory = true)
                    ?: createIn(tree, parent, DocumentsContract.Document.MIME_TYPE_DIR, part)
                    ?: return null
            }
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parent)
            DocumentsContract.createDocument(resolver, parentUri, "application/octet-stream", parts.last())
        } catch (_: Exception) {
            null
        } ?: return find(folderUri, name)
        // A provider that renamed the file would leave the player reading a different one.
        if (displayName(created.toString()) != parts.last()) {
            try {
                DocumentsContract.deleteDocument(resolver, created)
            } catch (_: Exception) {
                Unit
            }
            return null
        }
        return created.toString()
    }

    /** The id of the child of [parentId] named exactly [name], a folder or not as asked, or null. */
    private fun child(tree: Uri, parentId: String, name: String, directory: Boolean): String? {
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        return resolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val isDirectory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                if (cursor.getString(1) == name && isDirectory == directory) return@use cursor.getString(0)
            }
            null
        }
    }

    /** A new folder named exactly [name] in [parentId], or null. */
    private fun createIn(tree: Uri, parentId: String, mime: String, name: String): String? {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val created = DocumentsContract.createDocument(resolver, parent, mime, name) ?: return null
        if (displayName(created.toString()) != name) {
            DocumentsContract.deleteDocument(resolver, created)
            return null
        }
        return DocumentsContract.getDocumentId(created)
    }

    override fun read(uri: String): ByteArray? = try {
        resolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    override fun md5(uri: String): String? = try {
        resolver.openInputStream(Uri.parse(uri))?.use { input -> digestHex(input) }
    } catch (_: Exception) {
        null
    }

    override fun copy(sourceUri: String, targetUri: String): Boolean = try {
        val source = resolver.openInputStream(Uri.parse(sourceUri)) ?: return false
        val target = resolver.openOutputStream(Uri.parse(targetUri), "wt") ?: run {
            source.close()
            return false
        }
        source.use { input ->
            target.use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }
        true
    } catch (_: Exception) {
        false
    }

    override fun length(uri: String): Long? = try {
        val parsed = Uri.parse(uri)
        val reported = resolver.query(parsed, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
        reported ?: resolver.openFileDescriptor(parsed, "r")?.use { descriptor ->
            descriptor.statSize.takeIf { it >= 0 }
        }
    } catch (_: Exception) {
        null
    }

    override fun readAt(uri: String, offset: Long, length: Int): ByteArray? = try {
        resolver.openFileDescriptor(Uri.parse(uri), "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                val size = channel.size()
                if (offset in 0 until size) channel.position(offset)
                readSlice(size, offset, length) { channel.read(it) }
            }
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    override fun write(uri: String, bytes: ByteArray) {
        val out = resolver.openOutputStream(Uri.parse(uri), "wt") ?: error("save could not be opened for writing")
        out.use { it.write(bytes) }
    }

    override fun delete(uri: String): Boolean = try {
        DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
    } catch (_: Exception) {
        false
    }

    override fun head(uri: String, size: Int): ByteArray? = try {
        resolver.openInputStream(Uri.parse(uri))?.use { input ->
            val bytes = ByteArray(size)
            var filled = 0
            while (filled < size) {
                val count = input.read(bytes, filled, size - filled)
                if (count < 0) break
                filled += count
            }
            bytes.copyOf(filled)
        }
    } catch (_: Exception) {
        null
    }

    override fun childNames(folderUri: String): List<String>? = try {
        val tree = Uri.parse(folderUri)
        listChildren(tree, DocumentsContract.getTreeDocumentId(tree)).map { it.name }
    } catch (_: Exception) {
        null
    }

    override fun list(folderUri: String, name: String): List<SaveEntry>? {
        val tree = Uri.parse(folderUri)
        val parts = savePathParts(name) ?: return null
        var folder = DocumentsContract.getTreeDocumentId(tree)
        for (part in parts) {
            folder = listChildren(tree, folder).firstOrNull { it.name == part && it.isFolder }?.id ?: return null
        }
        val found = mutableListOf<SaveEntry>()
        val pending = ArrayDeque(listOf("" to folder))
        while (pending.isNotEmpty()) {
            val (path, id) = pending.removeFirst()
            for (child in listChildren(tree, id)) {
                val childPath = if (path.isEmpty()) child.name else "$path/${child.name}"
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, child.id).toString()
                found += SaveEntry(childPath, uri, child.isFolder)
                if (child.isFolder) pending += childPath to child.id
            }
        }
        return found
    }

    override fun createFolder(folderUri: String, name: String): Boolean {
        val tree = Uri.parse(folderUri)
        val parts = savePathParts(name) ?: return false
        return try {
            var parent = DocumentsContract.getTreeDocumentId(tree)
            for (part in parts) {
                parent = child(tree, parent, part, directory = true)
                    ?: createIn(tree, parent, DocumentsContract.Document.MIME_TYPE_DIR, part)
                    ?: return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun children(folderUri: String, path: String): List<SaveChild>? {
        val tree = Uri.parse(folderUri)
        val parent = if (path.isEmpty()) {
            DocumentsContract.getTreeDocumentId(tree)
        } else {
            val parts = savePathParts(path) ?: return null
            var folder = DocumentsContract.getTreeDocumentId(tree)
            for (part in parts) {
                folder = listChildren(tree, folder).firstOrNull { it.name == part && it.isFolder }?.id ?: return null
            }
            folder
        }
        return listChildren(tree, parent).map { child ->
            SaveChild(
                child.name,
                DocumentsContract.buildDocumentUriUsingTree(tree, child.id).toString(),
                child.isFolder,
                child.size,
            )
        }
    }

    private class Child(val id: String, val name: String, val isFolder: Boolean, val size: Long)

    /** The children of [parentId]. Throws when the provider does not answer, unlike [child]. */
    private fun listChildren(tree: Uri, parentId: String): List<Child> {
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cursor = resolver.query(children, columns, null, null, null) ?: error("save folder could not be listed")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val size = if (it.isNull(3)) 0L else it.getLong(3)
                    add(Child(it.getString(0), it.getString(1), it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR, size))
                }
            }
        }
    }
}
