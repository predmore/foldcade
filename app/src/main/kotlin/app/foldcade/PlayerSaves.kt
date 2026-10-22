package app.foldcade

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.ObservedSlot
import app.foldcade.api.plugin.PlacedSave
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveFolderPart
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
)

/** A file or folder below a folder, at [path] below it, separated by `/`. */
internal data class SaveEntry(
    val path: String,
    val uri: String,
    val isFolder: Boolean,
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

    /**
     * Everything below the folder at [name] inside [folderUri], or null when
     * there is no such folder. Throws when a folder cannot be listed, so a
     * folder that is there is never taken for one that is not.
     */
    fun list(folderUri: String, name: String): List<SaveEntry>? = null

    /** Makes the folder at exactly [name] inside [folderUri], and those it is in. False when it could not. */
    fun createFolder(folderUri: String, name: String): Boolean = false
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
 * folders [Player.saveFolders] names there, which move as one zip.
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
 * Callers run one save flow at a time. A zip handed over waits in [handover]
 * until the library has kept it.
 */
internal class PlayerSaves(
    private val docs: SaveDocuments,
    private val marks: SaveMarks,
    private val backups: Path,
    private val handover: Path,
) {
    fun spots(player: Player, game: Game, target: LaunchTarget): List<SaveSpot> {
        val uri = when (target) {
            is LaunchTarget.ContentUri -> target.uri
            is LaunchTarget.SharedFile -> target.uri
            else -> return emptyList()
        }
        val romName = docs.displayName(uri) ?: return emptyList()
        val header by lazy {
            if (player.romHeaderSize > 0) docs.head(uri, player.romHeaderSize) ?: ByteArray(0) else ByteArray(0)
        }
        return player.saveDeclarations(game).mapNotNull { declaration ->
            val folder = declaration.locationUri ?: return@mapNotNull null
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
                else -> ObservedSlot(spot.slot, hash, player.id, uri ?: stage(key, bytes))
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
            val outcome = if (spot.folders.isEmpty()) placeFile(key, player, spot, bytes) else placeFolders(key, player, spot, bytes)
            when (outcome) {
                Placing.Failed -> return false
                Placing.Written -> changed?.add(spot.slot)
                Placing.AlreadyThere -> Unit
            }
        }
        return true
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
        val incoming = saveEntries(zip, spot.folders.map { it.name }.toSet()) ?: return Placing.Failed
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
        if (currentHash != null && currentHash !in known(key)) return Placing.Failed
        if (currentZip != null) backup(player, spot.fileName, currentZip)
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
        if (left != null) marks.setMark(tornKey(key), left)
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

    /** The bytes [spot] holds and the document they are in, or null when it holds none or cannot be read. */
    private fun readSpot(spot: SaveSpot): Pair<ByteArray, String?>? {
        if (spot.folders.isNotEmpty()) {
            val held = readFolders(spot) as? Held.Found ?: return null
            return saveZip(held.save.entries) to null
        }
        val uri = docs.find(spot.folderUri, spot.fileName) ?: return null
        return (docs.read(uri) ?: return null) to uri
    }

    /** Everything in [spot]'s folders, named as in its zip. */
    private fun readFolders(spot: SaveSpot): Held {
        val entries = sortedMapOf<String, ByteArray?>()
        val uris = HashMap<String, String>()
        try {
            for (part in spot.folders) {
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

    /** Where the entry [name] of [spot]'s zip lives, below the player's folder. */
    private fun documentPath(spot: SaveSpot, name: String): String {
        val part = spot.folders.first { it.name == partOf(name) }
        return part.path + "/" + name.substringAfter('/').removeSuffix("/")
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

    /** Keeps the newest [BACKUPS_KEPT] replaced files per player. */
    private fun backup(player: Player, fileName: String, bytes: ByteArray) {
        val dir = backups.resolve(safeName(player.id))
        Files.createDirectories(dir)
        val stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC).format(Instant.now())
        Files.write(dir.resolve("$stamp-${safeName(fileName)}"), bytes)
        val all = Files.list(dir).use { stream -> stream.collect(java.util.stream.Collectors.toList()) }
        all.sortedByDescending { it.fileName.toString() }.drop(BACKUPS_KEPT).forEach { Files.deleteIfExists(it) }
    }

    companion object {
        const val BACKUPS_KEPT = 20

        /** One mark per library, game, player, slot, and file. Hashed so any key fits a preference name. */
        fun key(libraryId: String, game: Game, player: Player, spot: SaveSpot): String =
            sha256("$libraryId\u0000${game.remoteKey}\u0000${player.id}\u0000${spot.slot}\u0000${spot.fileName}")

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

/** Folders the shell can move: at least one, each name one distinct part and each path inside the player's folder. */
private fun validFolders(folders: List<SaveFolderPart>): Boolean =
    folders.isNotEmpty() &&
        folders.map { it.name }.distinct().size == folders.size &&
        folders.all { savePathParts(it.name)?.size == 1 && savePathParts(it.path) != null }

/** The part an entry of a save zip is in: its first folder. */
private fun partOf(name: String): String = name.substringBefore('/')

/** The largest a save zip may unpack to, so a broken or hostile one cannot fill memory. */
private const val SAVE_ZIP_LIMIT = 256L * 1024 * 1024

/** One fixed time for every entry, as the zip's local date and time, so its bytes depend only on its entries. */
private val SAVE_ZIP_TIME: Long =
    LocalDateTime.of(2000, 1, 1, 0, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** [entries] as a zip whose bytes depend only on them: in name order, with one fixed time. */
internal fun saveZip(entries: Map<String, ByteArray?>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, bytes) in entries.toSortedMap()) {
            zip.putNextEntry(ZipEntry(name).apply { time = SAVE_ZIP_TIME })
            if (bytes != null) zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
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

    private class Child(val id: String, val name: String, val isFolder: Boolean)

    /** The children of [parentId]. Throws when the provider does not answer, unlike [child]. */
    private fun listChildren(tree: Uri, parentId: String): List<Child> {
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cursor = resolver.query(children, columns, null, null, null) ?: error("save folder could not be listed")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(Child(it.getString(0), it.getString(1), it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
                }
            }
        }
    }
}
