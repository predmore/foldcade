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
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * One save file in a player's folder: the [slot] a library knows, the folder
 * the user picked for the player, and the file name the player uses there.
 */
internal data class SaveSpot(
    val slot: String,
    val folderUri: String,
    val fileName: String,
)

/** Content-resolver work [PlayerSaves] needs, so its decisions run on the JVM in tests. */
internal interface SaveDocuments {
    /** Display name of the document at [uri], or null. */
    fun displayName(uri: String): String?

    /** The document named [name] directly inside the tree [folderUri], or null. */
    fun find(folderUri: String, name: String): String?

    /** A new empty document named exactly [name] inside [folderUri], or null. */
    fun create(folderUri: String, name: String): String?

    fun read(uri: String): ByteArray?

    /** Replaces the whole document. */
    fun write(uri: String, bytes: ByteArray)

    /** True when the document is gone. */
    fun delete(uri: String): Boolean
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
 * file [Player.saveFileName] names in the folder the player declares.
 *
 * Nothing in the player's folder is overwritten unseen:
 * - a file whose hash differs from the last one the shell placed or read was
 *   written outside Foldcade, or by a session whose return was missed. It is
 *   handed to the library through [changedSince] before anything is placed;
 * - [place] refuses a file that changed again after that, or one it cannot read;
 * - a file about to be replaced is copied to [backups] first;
 * - a placed file is read back and must match the library's hash. A write that
 *   fails puts the old bytes back.
 *
 * A placement is recorded as pending before it writes. A failed write is undone,
 * or its leftover bytes are recorded as torn: those bytes are not progress, so
 * [changedSince] does not hand them over and the next [place] replaces them.
 * Callers run one save flow at a time.
 */
internal class PlayerSaves(
    private val docs: SaveDocuments,
    private val marks: SaveMarks,
    private val backups: Path,
) {
    fun spots(player: Player, game: Game, target: LaunchTarget): List<SaveSpot> {
        val uri = (target as? LaunchTarget.ContentUri)?.uri ?: return emptyList()
        val romName = docs.displayName(uri) ?: return emptyList()
        return player.saveDeclarations(game).mapNotNull { declaration ->
            val folder = declaration.locationUri ?: return@mapNotNull null
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
            val uri = docs.find(spot.folderUri, spot.fileName) ?: return@mapNotNull null
            val bytes = docs.read(uri) ?: return@mapNotNull null
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
                else -> ObservedSlot(spot.slot, hash, player.id, uri)
            }
        }
        return ObservedSaves(changed)
    }

    /** Call once the library has kept [observed]. */
    fun noteHandedOver(libraryId: String, game: Game, player: Player, spots: List<SaveSpot>, observed: ObservedSaves) {
        for (slot in observed.slots) {
            val spot = spots.firstOrNull { it.slot == slot.slot } ?: continue
            settle(key(libraryId, game, player, spot), slot.contentHash)
        }
    }

    /**
     * Writes each of [placed] into its spot. False when a write could not be
     * finished and checked. The game must not start then, or it would play
     * from a save the library does not know.
     */
    fun place(libraryId: String, game: Game, player: Player, spots: List<SaveSpot>, placed: List<PlacedSave>): Boolean {
        for (spot in spots) {
            val save = placed.firstOrNull { it.slot == spot.slot } ?: continue
            val key = key(libraryId, game, player, spot)
            val bytes = docs.read(save.contentUri) ?: return false
            if (md5Hex(bytes) != save.contentHash) return false
            val existing = docs.find(spot.folderUri, spot.fileName)
            val current = existing?.let(docs::read)
            // A file that is there and cannot be read is never written over blind.
            if (existing != null && current == null) return false
            val currentHash = current?.let(::md5Hex)
            if (currentHash == save.contentHash) {
                settle(key, save.contentHash)
                continue
            }
            // Bytes the library has not kept, such as a player still writing: hand them over first.
            val known = setOfNotNull(marks.mark(key), marks.mark(pendingKey(key)), marks.mark(tornKey(key)))
            if (currentHash != null && currentHash !in known) return false
            val target = existing ?: docs.create(spot.folderUri, spot.fileName) ?: return false
            if (current != null) backup(player, spot.fileName, current)
            marks.setMark(pendingKey(key), save.contentHash)
            val written = try {
                docs.write(target, bytes)
                docs.read(target)
            } catch (_: Exception) {
                null
            }
            if (written == null || md5Hex(written) != save.contentHash) {
                undo(key, target, current)
                return false
            }
            settle(key, save.contentHash)
        }
        return true
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
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            resolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == name) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)).toString()
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun create(folderUri: String, name: String): String? {
        val tree = Uri.parse(folderUri)
        val created = try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
        } catch (_: Exception) {
            null
        } ?: return find(folderUri, name)
        // A provider that renamed the file would leave the player reading a different one.
        if (displayName(created.toString()) != name) {
            try {
                DocumentsContract.deleteDocument(resolver, created)
            } catch (_: Exception) {
                Unit
            }
            return null
        }
        return created.toString()
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
}
