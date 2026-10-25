package app.foldcade.plugins.aps3e

import app.foldcade.api.plugin.GameFileReader

/**
 * The PlayStation 3 title id in a decrypted ISO, such as `BLUS30464`.
 *
 * The image is ISO 9660 with 2048-byte sectors. The primary descriptor is at
 * sector 16, and `PS3_GAME/PARAM.SFO` is found from its root directory.
 * `TITLE_ID` there is four letters and five digits. Anything else, including
 * a file that cannot be read, has none here. This does not throw.
 */
internal fun ps3TitleId(game: GameFileReader): String? = try {
    val id = sfoValue(paramSfo(game) ?: return null, "TITLE_ID") ?: return null
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
    for (index in 0L until count) {
        val at = 20L + index * 16L
        if (at < 0 || at > Int.MAX_VALUE - 16 || at + 16 > sfo.size) return null
        val entry = at.toInt()
        val keyOffset = u16(sfo, entry) ?: return null
        val length = u32(sfo, entry + 4) ?: return null
        val dataOffset = u32(sfo, entry + 12) ?: return null
        val name = cString(sfo, keyTable + keyOffset) ?: return null
        if (name != key) continue
        val start = dataTable + dataOffset
        if (start < 0 || length < 0 || start > sfo.size) return null
        val end = (start + length).coerceAtMost(sfo.size.toLong())
        if (start > end || end > Int.MAX_VALUE) return null
        val text = sfo.copyOfRange(start.toInt(), end.toInt()).toString(Charsets.UTF_8)
        val nul = text.indexOf('\u0000')
        return if (nul >= 0) text.substring(0, nul) else text
    }
    return null
}

private fun paramSfo(game: GameFileReader): ByteArray? {
    val pvd = game.read(PVD_OFFSET, SECTOR) ?: return null
    if (pvd.size < ROOT_RECORD + 34 || pvd[0] != 1.toByte() || !pvd.copyOfRange(1, 6).contentEquals(CD001)) return null
    val root = directoryFile(pvd, ROOT_RECORD) ?: return null
    val rootDir = readAt(game, root.lba, root.size, DIRECTORY_CAP) ?: return null
    val gameDir = findEntry(rootDir, "PS3_GAME", wantDirectory = true) ?: return null
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
        if (isDirectory == wantDirectory && at + 33 + nameLength <= bytes.size) {
            val raw = bytes.copyOfRange(at + 33, at + 33 + nameLength).toString(Charsets.ISO_8859_1)
            if (raw.substringBefore(';').equals(name, ignoreCase = true)) return directoryFile(bytes, at)
        }
        at += length
    }
    return null
}

private fun readAt(game: GameFileReader, lba: Long, size: Long, cap: Int): ByteArray? {
    if (lba < 0 || size <= 0 || lba > Long.MAX_VALUE / SECTOR) return null
    val length = size.coerceAtMost(cap.toLong())
    if (length > Int.MAX_VALUE) return null
    val bytes = game.read(lba * SECTOR, length.toInt()) ?: return null
    return bytes.takeIf { it.size.toLong() == length }
}

private fun cString(bytes: ByteArray, at: Long): String? {
    if (at < 0 || at > bytes.size) return null
    val start = at.toInt()
    var end = start
    while (end < bytes.size && bytes[end] != 0.toByte()) {
        end += 1
        if (end - start > 256) return null
    }
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
