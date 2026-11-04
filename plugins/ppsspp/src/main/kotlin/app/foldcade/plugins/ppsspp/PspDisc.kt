package app.foldcade.plugins.ppsspp

import app.foldcade.api.plugin.GameFileReader
import java.util.zip.Inflater

private fun pspSfo(game: GameFileReader): ByteArray? {
    val head = game.read(0, 40) ?: return null
    if (head.size < 4) return null
    if (head.copyOfRange(0, 4).contentEquals(byteArrayOf(0, 0x50, 0x42, 0x50))) {
        val start = u32(head, 8) ?: return null
        val end = u32(head, 12) ?: return null
        if (start < 40 || end <= start || end - start > SFO_CAP) return null
        return game.read(start, (end - start).toInt())?.takeIf { it.size.toLong() == end - start }
    }
    val reader = if (String(head, 0, 4, Charsets.US_ASCII) == "CISO") csoReader(game, head) ?: return null else game
    return paramSfo(reader)
}

/** Bounded CSO v1 random reads. Only the few ISO metadata frames are inflated. */
private fun csoReader(game: GameFileReader, head: ByteArray): GameFileReader? {
    if (head.size < 24 || head[20] != 1.toByte()) return null
    val frame = u32(head, 16)?.toInt() ?: return null
    val shift = head[21].toInt() and 0xff
    val lo = u32(head, 8) ?: return null
    val hi = u32(head, 12) ?: return null
    if (hi != 0L || lo <= 0 || frame !in 2048..(1024 * 1024) || frame and (frame - 1) != 0 || shift > 20) return null
    return object : GameFileReader {
        private val cached = HashMap<Long, ByteArray>()
        override fun read(offset: Long, length: Int): ByteArray? {
            if (offset < 0 || length !in 0..DIRECTORY_CAP || offset > lo - length) return null
            val out = ByteArray(length)
            var done = 0
            while (done < length) {
                val position = offset + done
                val number = position / frame
                val data = cached[number] ?: run {
                    val indices = game.read(24 + number * 4, 8) ?: return null
                    val index = u32(indices, 0) ?: return null
                    val next = u32(indices, 4) ?: return null
                    val start = (index and 0x7fffffff) shl shift
                    val end = (next and 0x7fffffff) shl shift
                    if (end <= start || end - start > frame + (1L shl shift)) return null
                    val wanted = minOf(frame.toLong(), lo - number * frame).toInt()
                    val block = if (index and 0x80000000 != 0L) {
                        game.read(start, wanted)?.takeIf { it.size == wanted } ?: return null
                    } else {
                        val bytes = game.read(start, (end - start).toInt()) ?: return null
                        val inflater = Inflater(true)
                        try {
                            inflater.setInput(bytes)
                            val inflated = ByteArray(wanted)
                            var count = 0
                            while (!inflater.finished() && count < wanted) {
                                val n = inflater.inflate(inflated, count, wanted - count)
                                if (n == 0) return null
                                count += n
                            }
                            if (count != wanted || !inflater.finished()) return null
                            inflated
                        } finally { inflater.end() }
                    }
                    if (cached.size >= 64) return null
                    cached[number] = block
                    block
                }
                val at = (position % frame).toInt()
                val count = minOf(length - done, data.size - at)
                if (count <= 0) return null
                data.copyInto(out, done, at, at + count)
                done += count
            }
            return out
        }
    }
}

/** DISC_ID from PARAM.SFO in ISO/CSO PSP_GAME, or a PBP's SFO section.
 * PPSSPP Core/ELF/ParamSFO.cpp, Core/ELF/PBPReader.cpp and
 * Core/FileSystems/BlockDevices.cpp at 3bc60270eae3069f13dfbfdb7292a7e1375de6f6:
 * https://github.com/hrydgard/ppsspp/tree/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/Core
 * No filename or catalog fallback. Invalid/unreadable metadata has no save id.
 */
internal fun pspDiscId(game: GameFileReader): String? = try {
    val id = sfoValue(pspSfo(game) ?: return null, "DISC_ID") ?: return null
    id.trim('\u0000').takeIf { TITLE_ID.matches(it) }
} catch (_: Exception) {
    null
} catch (_: OutOfMemoryError) {
    null
}

/**
 * The string [key] in an SFO, or null when [sfo] is not one or has no such key.
 * The value stops at the first NUL.
 */
internal fun sfoValue(sfo: ByteArray, key: String): String? {
    if (sfo.size < 20 || sfo[0] != 0.toByte() || !sfo.copyOfRange(1, 4).contentEquals(PSF)) return null
    val keyTable = u32(sfo, 8) ?: return null
    val dataTable = u32(sfo, 12) ?: return null
    val count = u32(sfo, 16) ?: return null
    if (count > MAX_ENTRIES) return null
    if (keyTable < 20 + count * 16 || dataTable < keyTable || dataTable > sfo.size) return null
    var found: String? = null
    for (index in 0L until count) {
        val at = 20L + index * 16L
        if (at < 0 || at > Int.MAX_VALUE - 16 || at + 16 > sfo.size) return null
        val entry = at.toInt()
        val keyOffset = u16(sfo, entry) ?: return null
        val length = u32(sfo, entry + 4) ?: return null
        val dataOffset = u32(sfo, entry + 12) ?: return null
        val name = cString(sfo, keyTable + keyOffset, dataTable.toInt()) ?: return null
        if (name != key) continue
        if (found != null || u16(sfo, entry + 2) != 0x0204L || length == 0L || length > (u32(sfo, entry + 8) ?: return null)) return null
        val start = dataTable + dataOffset
        if (start < 0 || length < 0 || start > sfo.size) return null
        val end = start + length
        if (start > end || end > sfo.size || end > Int.MAX_VALUE) return null
        val text = sfo.copyOfRange(start.toInt(), end.toInt()).toString(Charsets.UTF_8)
        val nul = text.indexOf('\u0000')
        if (nul < 0) return null
        found = text.substring(0, nul)
    }
    return found
}

private fun paramSfo(game: GameFileReader): ByteArray? {
    val pvd = game.read(PVD_OFFSET, SECTOR) ?: return null
    if (pvd.size < ROOT_RECORD + 34 || pvd[0] != 1.toByte() || !pvd.copyOfRange(1, 6).contentEquals(CD001)) return null
    val root = directoryFile(pvd, ROOT_RECORD) ?: return null
    val rootDir = readAt(game, root.lba, root.size, DIRECTORY_CAP) ?: return null
    val gameDir = findEntry(rootDir, "PSP_GAME", wantDirectory = true) ?: return null
    val listing = readAt(game, gameDir.lba, gameDir.size, DIRECTORY_CAP) ?: return null
    val sfo = findEntry(listing, "PARAM.SFO", wantDirectory = false) ?: return null
    return readAt(game, sfo.lba, sfo.size, SFO_CAP)
}

private data class IsoEntry(val lba: Long, val size: Long)

private fun directoryFile(bytes: ByteArray, at: Int): IsoEntry? {
    if (at < 0 || at + 33 > bytes.size) return null
    val length = bytes[at].toInt() and 0xFF
    if (length < 33 || at + length > bytes.size) return null
    val lba = u32(bytes, at + 2) ?: return null
    val size = u32(bytes, at + 10) ?: return null
    if (lba < 0 || size < 0) return null
    return IsoEntry(lba, size)
}

/** [name] in an ISO 9660 directory, ignoring case and a trailing `;1`. A 0 length skips to the next sector. */
private fun findEntry(bytes: ByteArray, name: String, wantDirectory: Boolean): IsoEntry? {
    var at = 0
    while (at + 1 < bytes.size && at < DIRECTORY_CAP) {
        val length = bytes[at].toInt() and 0xFF
        if (length == 0) {
            val next = ((at / SECTOR) + 1) * SECTOR
            if (next <= at) return null
            at = next
            continue
        }
        if (length < 33 || at + length > bytes.size) return null
        val flags = bytes[at + 25].toInt() and 0xFF
        val isDirectory = flags and 2 != 0
        val nameLength = bytes[at + 32].toInt() and 0xFF
        if (nameLength > length - 33) return null
        if (isDirectory == wantDirectory && at + 33 + nameLength <= bytes.size) {
            val raw = bytes.copyOfRange(at + 33, at + 33 + nameLength).toString(Charsets.ISO_8859_1)
            if (raw.substringBefore(';').equals(name, ignoreCase = true)) {
                if (flags and 0x80 != 0) return null
                return directoryFile(bytes, at)
            }
        }
        at += length
    }
    return null
}

private fun readAt(game: GameFileReader, lba: Long, size: Long, cap: Int): ByteArray? {
    if (lba < 0 || size <= 0 || lba > Long.MAX_VALUE / SECTOR) return null
    if (size > cap) return null
    val length = size
    if (length > Int.MAX_VALUE) return null
    val bytes = game.read(lba * SECTOR, length.toInt()) ?: return null
    return bytes.takeIf { it.size.toLong() == length }
}

private fun cString(bytes: ByteArray, at: Long, limit: Int = bytes.size): String? {
    if (at < 0 || at >= limit || limit > bytes.size) return null
    val start = at.toInt()
    var end = start
    while (end < limit && bytes[end] != 0.toByte()) {
        end += 1
        if (end - start > 256) return null
    }
    if (end == limit) return null
    return bytes.copyOfRange(start, end).toString(Charsets.UTF_8)
}

private fun u16(bytes: ByteArray, at: Int): Long? {
    if (at < 0 || at + 2 > bytes.size) return null
    return (bytes[at].toLong() and 0xFF) or ((bytes[at + 1].toLong() and 0xFF) shl 8)
}

private fun u32(bytes: ByteArray, at: Int): Long? {
    if (at < 0 || at + 4 > bytes.size) return null
    return (bytes[at].toLong() and 0xFF) or
        ((bytes[at + 1].toLong() and 0xFF) shl 8) or
        ((bytes[at + 2].toLong() and 0xFF) shl 16) or
        ((bytes[at + 3].toLong() and 0xFF) shl 24)
}

private val CD001 = "CD001".toByteArray(Charsets.US_ASCII)
private val PSF = "PSF".toByteArray(Charsets.US_ASCII)
private val TITLE_ID = Regex("^[A-Z]{4}\\d{5}$")

private const val SECTOR = 2048
private const val PVD_OFFSET = 16L * SECTOR
private const val ROOT_RECORD = 156
private const val DIRECTORY_CAP = 64 * 1024
private const val SFO_CAP = 64 * 1024
private const val MAX_ENTRIES = 10_000L
