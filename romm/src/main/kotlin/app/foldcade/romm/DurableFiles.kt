package app.foldcade.romm

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.stream.Collectors
import java.util.zip.ZipInputStream

/**
 * Writes [bytes] to a temp sibling, syncs it, renames it over [target], then
 * syncs the directory. A crash leaves either the old [target] or the new one,
 * never a torn file. Each call has its own temp name, so two writers do not
 * collide; the last rename wins.
 */
fun writeDurably(target: Path, bytes: ByteArray) {
    target.parent?.let { Files.createDirectories(it) }
    val temp = target.resolveSibling(target.fileName.toString() + "." + UUID.randomUUID().toString().take(8) + ".tmp")
    FileChannel.open(
        temp,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE,
    ).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
    try {
        moveIntoPlace(temp, target)
    } finally {
        Files.deleteIfExists(temp)
    }
}

/** Syncs [file] to storage, so a rename that follows cannot publish a file whose bytes are still in memory. */
internal fun forceToDisk(file: Path) {
    FileChannel.open(file, StandardOpenOption.WRITE).use { it.force(true) }
}

/** Renames [from] over [to], atomically where the file system can, then syncs the directory. */
internal fun moveIntoPlace(from: Path, to: Path) {
    to.parent?.let { Files.createDirectories(it) }
    try {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
    }
    to.parent?.let(::forceDirectory)
}

/** Makes a rename in [dir] survive a power cut. Best effort: not every file system lets a directory be opened. */
private fun forceDirectory(dir: Path) {
    try {
        FileChannel.open(dir, StandardOpenOption.READ).use { it.force(true) }
    } catch (_: IOException) {
        Unit
    } catch (_: UnsupportedOperationException) {
        Unit
    }
}

/** Lowercase hex MD5 of [file], read in blocks. */
fun md5File(file: Path): String {
    val digest = MessageDigest.getInstance("MD5")
    Files.newInputStream(file).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw kotlinx.coroutines.CancellationException("hash was cancelled")
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * RomM's hash of save bytes, the one its sync compares. A zip hashes as the MD5
 * of one `name:md5` line per file entry, in name order, joined by newlines, so
 * the same files hash the same however they were zipped. Anything else is the
 * MD5 of its bytes. Null for a zip that cannot be read, as RomM keeps no hash
 * then. Read from RomM 5.4's `hash_save_content` and `hash_zip_contents`.
 */
fun rommSaveHash(bytes: ByteArray): String? {
    val end = zipEndRecord(bytes) ?: return md5Hex(bytes)
    val lines = mutableListOf<String>()
    var entries = 0
    try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                if (!entry.name.endsWith("/")) lines += "${entry.name}:${md5Hex(zip.readBytes())}"
            }
        }
    } catch (_: IOException) {
        return null
    } catch (_: IllegalArgumentException) {
        // A name that is not UTF-8.
        return null
    }
    // Entries the directory lists and the stream could not reach make the zip unreadable, as in Python.
    val listed = (bytes[end + 10].toInt() and 0xff) or ((bytes[end + 11].toInt() and 0xff) shl 8)
    if (listed != 0xffff && listed != entries) return null
    return md5Hex(lines.sorted().joinToString("\n").toByteArray())
}

/** [rommSaveHash] of [file]. */
fun rommSaveHash(file: Path): String? = rommSaveHash(Files.readAllBytes(file))

/**
 * Where a zip's end-of-central-directory record starts, found the way Python's
 * `zipfile.is_zipfile` looks for it, or null when [bytes] have none.
 */
private fun zipEndRecord(bytes: ByteArray): Int? {
    val earliest = maxOf(0, bytes.size - ZIP_END_RECORD - ZIP_COMMENT_MAX)
    for (at in bytes.size - ZIP_END_RECORD downTo earliest) {
        if (bytes[at] == 0x50.toByte() && bytes[at + 1] == 0x4b.toByte() && bytes[at + 2] == 0x05.toByte() && bytes[at + 3] == 0x06.toByte()) {
            return at
        }
    }
    return null
}

private const val ZIP_END_RECORD = 22
private const val ZIP_COMMENT_MAX = 0xffff

/**
 * Copies [file] into a `.trash` directory beside it under a unique name, then
 * keeps only the newest [SAVE_TRASH_KEEP] there. Returns the copy, or null when
 * [file] does not exist. The original stays in place.
 */
fun copySaveToTrash(file: Path): Path? {
    if (!Files.isRegularFile(file)) return null
    val trash = trashOf(file)
    Files.createDirectories(trash)
    val dest = trash.resolve(trashedName(file.fileName.toString()))
    Files.copy(file, dest, StandardCopyOption.COPY_ATTRIBUTES)
    pruneTrash(trash)
    return dest
}

/**
 * Moves [file] into a `.trash` directory beside it.
 * The name is unique so a second delete does not replace the first.
 * Returns false when [file] is already gone.
 */
internal fun moveSaveToTrash(file: Path): Boolean {
    if (!Files.exists(file)) return false
    val parent = file.parent ?: return Files.deleteIfExists(file)
    val trash = parent.resolve(TRASH_DIR)
    Files.createDirectories(trash)
    val dest = trash.resolve(trashedName(file.fileName.toString()))
    try {
        Files.move(file, dest, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(file, dest)
    }
    pruneTrash(trash)
    return true
}

private fun trashOf(file: Path): Path = (file.parent ?: file.toAbsolutePath().parent).resolve(TRASH_DIR)

/** Names start with a UTC stamp, so name order is age order. */
private fun pruneTrash(trash: Path) {
    val entries = try {
        Files.list(trash).use { stream -> stream.collect(Collectors.toList()) }
    } catch (_: IOException) {
        return
    }
    entries.sortedByDescending { it.fileName.toString() }.drop(SAVE_TRASH_KEEP).forEach { old ->
        try {
            Files.deleteIfExists(old)
        } catch (_: IOException) {
            Unit
        }
    }
}

private fun trashedName(fileName: String): String {
    val stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        .withZone(ZoneOffset.UTC)
        .format(Instant.now())
    val unique = UUID.randomUUID().toString().substring(0, 8)
    return "$stamp-$unique-$fileName"
}

const val TRASH_DIR: String = ".trash"

/** Replaced saves kept per slot. The newest are kept. */
const val SAVE_TRASH_KEEP: Int = 10
