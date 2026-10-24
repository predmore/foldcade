package app.foldcade.plugins.romm

import app.foldcade.romm.writeDurably
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException

/** A zip member larger than this is not unpacked. */
internal const val FIRMWARE_ZIP_FILE_CAP: Long = 32L * 1024 * 1024

/** Unpacked firmware from one zip larger than this is not unpacked. */
internal const val FIRMWARE_ZIP_TOTAL_CAP: Long = 1024L * 1024 * 1024

private val ZIP_LOCAL_HEADER = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

/**
 * True when [path] starts with a zip local-file header. The name is not consulted:
 * a library may keep a zip under any file name.
 */
internal fun isZipFile(path: Path): Boolean {
    if (!Files.isRegularFile(path) || Files.size(path) < ZIP_LOCAL_HEADER.size) return false
    val magic = ByteArray(ZIP_LOCAL_HEADER.size)
    Files.newInputStream(path).use { input ->
        if (input.read(magic) != magic.size) return false
    }
    return magic.contentEquals(ZIP_LOCAL_HEADER)
}

/**
 * Files already unpacked from [zip], named `<zip name>/<file name>`. Empty when
 * the unpack is missing, only partly written, or belongs to an older copy of [zip].
 */
internal fun checkedZipMembers(zip: Path): List<Pair<String, Path>> {
    if (!unpackMatches(zip)) return emptyList()
    val dir = unpackDir(zip)
    val zipName = zip.fileName.toString()
    val files = Files.list(dir).use { stream -> stream.collect(java.util.stream.Collectors.toList()) }
    return files
        .filter { Files.isRegularFile(it) }
        .map { "$zipName/${it.fileName}" to it }
        .sortedBy { it.first }
}

/**
 * Unpacks [zip] beside itself when [wants] wants at least one file inside it.
 * The files land in `<zip name>.unpacked`, and that folder is offered only once
 * every file is whole and marked with the zip's own verified stamp. A member
 * whose CRC-32 or byte count is not what the zip says rejects the whole unpack:
 * nothing from it is offered, and no stamp is written. A newer copy of the zip
 * replaces the old unpack. A zip over [fileCap] or [totalCap] is skipped, and
 * nothing from it is left to offer. A player that wants no file inside leaves
 * the zip packed. [planning] runs once the entries are chosen, before they are
 * written. Two calls for the same zip take turns, from the cache check through
 * publication and a failed cleanup. They share one work folder.
 */
internal fun unpackFirmwareZip(
    zip: Path,
    fileCap: Long = FIRMWARE_ZIP_FILE_CAP,
    totalCap: Long = FIRMWARE_ZIP_TOTAL_CAP,
    planning: () -> Unit = {},
    wants: (String) -> Boolean,
): List<Pair<String, Path>> {
    val lock = unpackLock(zip)
    lock.lock()
    try {
        return unpackFirmwareZipLocked(zip, fileCap, totalCap, planning, wants)
    } finally {
        lock.unlock()
    }
}

private fun unpackFirmwareZipLocked(
    zip: Path,
    fileCap: Long,
    totalCap: Long,
    planning: () -> Unit,
    wants: (String) -> Boolean,
): List<Pair<String, Path>> {
    if (unpackMatches(zip)) return checkedZipMembers(zip)
    if (!isZipFile(zip) || !Files.isRegularFile(zipMarker(zip))) return emptyList()
    val work = unpackWork(zip)
    var published = false
    try {
        // Before planning, so a zip replaced while the entries are chosen is not stamped as this one.
        val stamp = Files.readAllBytes(zipMarker(zip))
        when (val plan = planZip(zip, wants, fileCap, totalCap, planning)) {
            ZipPlan.None -> return emptyList()
            ZipPlan.Skip -> {
                dropUnpack(zip)
                return emptyList()
            }
            is ZipPlan.Unpack -> {
                if (Thread.currentThread().isInterrupted) throw CancellationException("firmware unpack was cancelled")
                deleteTree(work)
                Files.createDirectories(work)
                extract(zip, work, plan.members, fileCap, totalCap)
                // The zip was replaced while this copy was planned or written. Leave it for the new one.
                if (!stamp.contentEquals(Files.readAllBytes(zipMarker(zip)))) {
                    deleteTree(work)
                    return emptyList()
                }
                // Unpublished until the stamp is written, so a copy cut short is not offered.
                Files.deleteIfExists(unpackMarker(zip))
                deleteTree(unpackDir(zip))
                moveDir(work, unpackDir(zip))
                writeDurably(unpackMarker(zip), stamp)
                published = true
                return checkedZipMembers(zip)
            }
        }
    } catch (cancelled: CancellationException) {
        if (!published) deleteTree(work)
        throw cancelled
    } catch (_: Exception) {
        if (!published) deleteTree(work)
        return emptyList()
    }
}

private sealed interface ZipPlan {
    /** No member is wanted, or the zip cannot be read as one. */
    data object None : ZipPlan

    /** Over a cap. Nothing from it should be offered. */
    data object Skip : ZipPlan

    data class Unpack(val members: List<String>) : ZipPlan
}

private fun planZip(
    zip: Path,
    wants: (String) -> Boolean,
    fileCap: Long,
    totalCap: Long,
    planning: () -> Unit,
): ZipPlan {
    if (Files.size(zip) > totalCap) return ZipPlan.Skip
    val zipName = zip.fileName.toString()
    // Android refuses a zip whose entries repeat a name, so nothing in it is offered.
    val members = ZipFile(zip.toFile()).use { archive ->
        val seen = HashSet<String>()
        val chosen = mutableListOf<String>()
        var total = 0L
        val entries = archive.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val fileName = zipLeaf(entry.name) ?: continue
            // The first of each flattened name. A later copy is not a second file.
            if (!seen.add(fileName)) continue
            val size = entry.size
            if (size > fileCap) return ZipPlan.Skip
            if (size >= 0) {
                total += size
                if (total > totalCap) return ZipPlan.Skip
            }
            chosen += fileName
        }
        chosen
    }
    planning()
    if (members.isEmpty() || members.none { wants("$zipName/$it") }) return ZipPlan.None
    return ZipPlan.Unpack(members)
}

private fun extract(zip: Path, work: Path, members: List<String>, fileCap: Long, totalCap: Long) {
    ZipFile(zip.toFile()).use { archive ->
        // In archive order, so the first of each flattened name is the one written.
        // Looking the name up again would return the last copy.
        val pending = members.toHashSet()
        var total = 0L
        val entries = archive.entries()
        while (entries.hasMoreElements()) {
            if (Thread.currentThread().isInterrupted) throw CancellationException("firmware unpack was cancelled")
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val fileName = zipLeaf(entry.name) ?: continue
            if (!pending.remove(fileName)) continue
            // Under its own name in the unpublished work folder. A temporary beside it
            // could be named the same as another member.
            val dest = work.resolve(fileName)
            if (dest.parent != work) throw IOException("zip entry left its folder")
            var written = 0L
            val crc = CRC32()
            archive.getInputStream(entry).use { input ->
                FileChannel.open(
                    dest,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                ).use { channel ->
                    val bytes = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(bytes)
                        if (count < 0) break
                        crc.update(bytes, 0, count)
                        written += count
                        total += count
                        if (written > fileCap || total > totalCap) throw IOException("firmware zip is over the cap")
                        val buffer = ByteBuffer.wrap(bytes, 0, count)
                        while (buffer.hasRemaining()) channel.write(buffer)
                    }
                    channel.force(true)
                }
            }
            // A stored member does not check this on its own. One mismatch rejects the whole unpack.
            if (written != entry.size || crc.value != entry.crc) throw IOException("zip entry does not match")
        }
        if (pending.isNotEmpty()) throw IOException("missing zip entry")
    }
}

/** The file's own name. Null for a folder, an empty name, `.`, `..`, or a name holding `\`. */
private fun zipLeaf(entryName: String): String? {
    if (entryName.isEmpty() || entryName.endsWith("/") || '\\' in entryName) return null
    val name = entryName.substringAfterLast('/')
    if (name.isEmpty() || name == "." || name == ".." || name.any { it.code <= 31 }) return null
    return name
}

private fun unpackMatches(zip: Path): Boolean {
    val marker = unpackMarker(zip)
    val stamp = zipMarker(zip)
    val dir = unpackDir(zip)
    if (!Files.isRegularFile(marker) || !Files.isRegularFile(stamp) || !Files.isDirectory(dir)) return false
    return Files.readAllBytes(marker).contentEquals(Files.readAllBytes(stamp))
}

private fun dropUnpack(zip: Path) {
    deleteTree(unpackWork(zip))
    deleteTree(unpackDir(zip))
    Files.deleteIfExists(unpackMarker(zip))
}

private fun deleteTree(path: Path) {
    if (Files.exists(path)) path.toFile().deleteRecursively()
}

private fun moveDir(from: Path, to: Path) {
    try {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(from, to)
    }
}

/** One unpack of a zip at a time, keyed by its absolute path. */
private val unpackLocks = ConcurrentHashMap<Path, ReentrantLock>()

private fun unpackLock(zip: Path): ReentrantLock =
    unpackLocks.computeIfAbsent(zip.toAbsolutePath().normalize()) { ReentrantLock() }

private fun unpackDir(zip: Path): Path = zip.resolveSibling(zip.fileName.toString() + ".unpacked")

private fun unpackWork(zip: Path): Path = zip.resolveSibling(zip.fileName.toString() + ".unpacked.partial")

private fun unpackMarker(zip: Path): Path = zip.resolveSibling(zip.fileName.toString() + ".unpacked.verified")

private fun zipMarker(zip: Path): Path = zip.resolveSibling(zip.fileName.toString() + ".verified")
