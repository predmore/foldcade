package app.foldcade

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderEntry
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * [GameFileReader] over a content URI. Each read is positioned. The buffer is
 * only as long as the bytes left in the file. An oversized request, or one that
 * runs out of memory, returns null.
 */
internal class ContentGameFile(
    private val resolver: ContentResolver,
    private val uri: String,
) : GameFileReader {
    override fun read(offset: Long, length: Int): ByteArray? {
        if (offset < 0 || length < 0) return null
        if (length == 0) return ByteArray(0)
        return try {
            resolver.openFileDescriptor(Uri.parse(uri), "r")?.use { descriptor -> readAt(descriptor, offset, length) }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    override fun sibling(name: String): GameFileReader? {
        val next = siblingContentUri(uri, name) ?: return null
        val opened = try {
            resolver.openFileDescriptor(Uri.parse(next), "r")
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null
        try {
            opened.close()
        } catch (_: Exception) {
            return null
        }
        return ContentGameFile(resolver, next)
    }

    private fun readAt(descriptor: ParcelFileDescriptor, offset: Long, length: Int): ByteArray? {
        FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
            val size = channel.size()
            if (offset in 0 until size) channel.position(offset)
            return readSlice(size, offset, length) { channel.read(it) }
        }
    }
}

/**
 * How many bytes to read at [offset] in a file of [size]. Zero at the end.
 * Null when [offset] or [length] is nonsense.
 */
internal fun gameReadCount(size: Long, offset: Long, length: Int): Int? {
    if (size < 0 || offset < 0 || length < 0) return null
    if (length == 0 || offset >= size) return 0
    return minOf(length.toLong(), size - offset).toInt()
}

/**
 * [length] bytes at [offset], capped at the bytes left in a file of [size].
 * [read] fills the buffer from the current position. Null when the request is
 * nonsense, the read fails, or allocating the buffer runs out of memory.
 */
internal fun readSlice(size: Long, offset: Long, length: Int, read: (ByteBuffer) -> Int): ByteArray? {
    val count = gameReadCount(size, offset, length) ?: return null
    if (count == 0) return ByteArray(0)
    return try {
        val buffer = ByteBuffer.allocate(count)
        while (buffer.hasRemaining()) {
            if (read(buffer) < 0) break
        }
        buffer.array().copyOf(buffer.position())
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

/**
 * [SaveFolderAccess] over the save folder's tree. Writes are refused when
 * [allowWrite] is false, which is the return path: a card is only set up
 * before a launch.
 */
internal class DocumentSaveFolder(
    private val docs: SaveDocuments,
    private val folderUri: String,
    private val allowWrite: Boolean,
    private val backup: (String, ByteArray) -> Unit,
) : SaveFolderAccess {
    /** True once a write, a new folder, or a move to backups changed the tree. */
    var changed: Boolean = false
        private set

    override fun list(path: String): List<SaveFolderEntry>? {
        if (path.isNotEmpty() && savePathParts(path) == null) return null
        val children = try {
            docs.children(folderUri, path)
        } catch (_: Exception) {
            return null
        } ?: return null
        return children.map { SaveFolderEntry(it.name, it.isFolder, it.size) }
    }

    override fun read(path: String, limit: Int): ByteArray? {
        if (limit < 0 || savePathParts(path) == null) return null
        val parent = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val file = list(parent)?.firstOrNull { it.name == name && !it.isFolder } ?: return null
        if (file.size > limit) return null
        val bytes = docs.find(folderUri, path)?.let(docs::read) ?: return null
        return bytes.takeIf { it.size <= limit }
    }

    override fun read(path: String, offset: Long, length: Int): ByteArray? {
        if (offset < 0 || length < 0 || savePathParts(path) == null) return null
        if (length == 0) return ByteArray(0)
        val uri = docs.find(folderUri, path) ?: return null
        return try {
            docs.readAt(uri, offset, length)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    override fun write(path: String, bytes: ByteArray): Boolean {
        if (!allowWrite || savePathParts(path) == null) return false
        val ok = try {
            val existingUri = docs.find(folderUri, path)
            val existing = if (existingUri == null) null else docs.read(existingUri) ?: return false
            if (existing != null && existing.contentEquals(bytes)) return true
            if (existing != null) {
                try {
                    backup(path, existing)
                } catch (_: Exception) {
                    return false
                }
            }
            val uri = existingUri ?: docs.create(folderUri, path) ?: return false
            docs.write(uri, bytes)
            docs.read(uri)?.contentEquals(bytes) == true
        } catch (_: Exception) {
            false
        }
        if (ok) changed = true
        return ok
    }

    override fun createFolder(path: String): Boolean {
        if (!allowWrite || savePathParts(path) == null) return false
        val parent = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val existed = list(parent)?.any { it.name == name && it.isFolder } == true
        val ok = try {
            docs.createFolder(folderUri, path)
        } catch (_: Exception) {
            false
        }
        if (ok && !existed) changed = true
        return ok
    }

    override fun moveToBackup(path: String): Boolean {
        if (!allowWrite || savePathParts(path) == null) return false
        val uri = docs.find(folderUri, path) ?: return false
        val bytes = docs.read(uri) ?: return false
        val kept = try {
            backup(path, bytes)
            true
        } catch (_: Exception) {
            false
        }
        if (!kept) return false
        val gone = try {
            docs.delete(uri) && docs.find(folderUri, path) == null
        } catch (_: Exception) {
            false
        }
        if (!gone) return false
        changed = true
        return true
    }
}
