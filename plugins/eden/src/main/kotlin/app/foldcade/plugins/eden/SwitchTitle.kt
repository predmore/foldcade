package app.foldcade.plugins.eden

import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.SaveFolderAccess
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Application id from a filename tag, PFS0 ticket name, or encrypted NCA header.
 * Layouts: https://switchbrew.org/wiki/NCA#PFS0, https://switchbrew.org/wiki/NCA
 * and https://switchbrew.org/wiki/XCI. No emulator code is used here.
 * Reads and tables are bounded. Ambiguous files have no save spot.
 */
internal fun switchTitleId(game: GameFileReader, folder: SaveFolderAccess): String? = try {
    readTitleId(game, folder)
} catch (_: Exception) {
    null
}

private fun readTitleId(game: GameFileReader, folder: SaveFolderAccess): String? {
    val name = game.fileName.orEmpty()
    if (name.length > 4096) return null
    val tags = Regex("\\[([0-9a-fA-F]{16})]").findAll(name)
        .mapNotNull { applicationId(it.groupValues[1]) }.toSet()
    if (tags.isNotEmpty()) return tags.singleOrNull()
    val file = Region(game, 0, Long.MAX_VALUE)
    val entries = if (file.read(0, 4)?.text() == "PFS0") {
        val pfs = partition(file, false) ?: return null
        val tickets = pfs.mapNotNull { entry ->
            Regex("([0-9a-fA-F]{32})\\.tik", RegexOption.IGNORE_CASE).matchEntire(entry.name)
                ?.groupValues?.get(1)?.take(16)?.let(::applicationId)
        }.toSet()
        if (tickets.isNotEmpty()) return tickets.singleOrNull()
        pfs
    } else {
        val card = when {
            file.read(0x100, 4)?.text() == "HEAD" -> 0L
            file.read(0x1100, 4)?.text() == "HEAD" -> 0x1000L
            else -> return null
        }
        val rootOffset = file.read(card + 0x130, 8)?.u64(0) ?: return null
        if (rootOffset < 0x200 || rootOffset > Long.MAX_VALUE - card) return null
        val root = partition(Region(game, card + rootOffset, Long.MAX_VALUE - card - rootOffset), true) ?: return null
        val secure = root.singleOrNull { it.name == "secure" } ?: return null
        partition(secure.data, true) ?: return null
    }
    val ncas = entries.filter { it.name.endsWith(".nca", true) || it.name.endsWith(".ncz", true) }
    // Do not sample a larger file: a title outside the sample could share the wrong save.
    if (ncas.isEmpty() || ncas.size > 16) return null
    val key = headerKey(folder) ?: return null
    val headers = ncas.map { entry ->
        val encrypted = entry.data.read(0x200, 0x200) ?: return null
        val header = decryptNcaSector(encrypted, key, 1)
        if (header.copyOfRange(0, 4).text() !in setOf("NCA3", "NCA2")) return null
        val type = header[5].toInt() and 0xFF
        if (type !in 0..5) return null
        type to applicationId(header.u64(0x10) ?: return null)
    }
    val bases = headers.mapNotNull { it.second }.toSet()
    if (bases.size != 1) return null
    // Prefer Program or Meta. Control and Data can still prove an application id.
    return headers.firstOrNull { it.first in 0..1 && it.second != null }?.second ?: bases.single()
}

/** Base, update, DLC and multi-program ids all name the application's save. */
internal fun applicationId(hex: String): String? = hex.takeIf { it.matches(Regex("[0-9a-fA-F]{16}")) }
    ?.toLongOrNull(16)?.let(::applicationId)

private fun applicationId(id: Long): String? {
    if (id ushr 56 != 1L) return null
    val base = (if (id and 0x1000L != 0L) id xor 0x1000L else id) and 0xFFFL.inv()
    return "%016X".format(base)
}

private fun headerKey(folder: SaveFolderAccess): ByteArray? {
    val text = folder.read("keys/prod.keys", 1024 * 1024)?.toString(Charsets.UTF_8) ?: return null
    val values = text.lineSequence().map { it.substringBefore('#').substringBefore(';').trim() }
        .filter { it.substringBefore('=').trim() == "header_key" }
        .map { it.substringAfter('=', "").trim() }.toList()
    val value = values.singleOrNull()?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) } ?: return null
    return ByteArray(32) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

private class Region(val game: GameFileReader, val start: Long, val size: Long) {
    fun read(offset: Long, length: Int): ByteArray? {
        if (offset < 0 || length !in 0..MAX_TABLE || offset > size - length || start > Long.MAX_VALUE - offset) return null
        return game.read(start + offset, length)?.takeIf { it.size == length }
    }
}

private data class Entry(val name: String, val data: Region)

/** PFS0 and HFS0 differ only in the entry stride for the fields needed here. */
private fun partition(region: Region, hashed: Boolean): List<Entry>? {
    val header = region.read(0, 0x10) ?: return null
    if (header.copyOfRange(0, 4).text() != if (hashed) "HFS0" else "PFS0") return null
    val count = header.u32(4)
    val strings = header.u32(8)
    if (count !in 1..4096 || strings !in 1..MAX_TABLE.toLong()) return null
    val stride = if (hashed) 0x40 else 0x18
    val tableSize = count * stride + strings
    if (tableSize > MAX_TABLE) return null
    val table = region.read(0x10, tableSize.toInt()) ?: return null
    val namesStart = count.toInt() * stride
    val dataStart = 0x10 + tableSize
    val names = HashSet<String>()
    return (0 until count.toInt()).map { index ->
        val at = index * stride
        val offset = table.u64(at) ?: return null
        val size = table.u64(at + 8) ?: return null
        val nameOffset = table.u32(at + 0x10)
        if (nameOffset >= strings) return null
        val nameStart = namesStart + nameOffset.toInt()
        val end = (nameStart until minOf(table.size, nameStart + 256)).firstOrNull { table[it] == 0.toByte() } ?: return null
        if (end - nameStart !in 1..255) return null
        val name = table.copyOfRange(nameStart, end).text()
        if ('/' in name || '\\' in name || name in setOf(".", "..") || !names.add(name)) return null
        if (dataStart > region.size || offset > region.size - dataStart || size > region.size - dataStart - offset) return null
        val relative = dataStart + offset
        if (region.start > Long.MAX_VALUE - relative) return null
        Entry(name, Region(region.game, region.start + relative, size))
    }
}

/**
 * One NCA header sector. Nintendo encodes the sector number big endian before
 * AES, then uses the usual little endian GF multiply within that sector.
 * See https://switchbrew.org/wiki/NCA#Encryption.
 */
internal fun decryptNcaSector(encrypted: ByteArray, key: ByteArray, sector: Long): ByteArray {
    require(encrypted.size == 0x200 && key.size == 32 && sector >= 0)
    val data = Cipher.getInstance("AES/ECB/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key.copyOfRange(0, 16), "AES"))
    }
    val tweakCipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOfRange(16, 32), "AES"))
    }
    val number = ByteArray(16)
    ByteBuffer.wrap(number).order(ByteOrder.BIG_ENDIAN).putLong(8, sector)
    val tweak = tweakCipher.doFinal(number)
    val result = ByteArray(encrypted.size)
    for (offset in encrypted.indices step 16) {
        val block = ByteArray(16) { (encrypted[offset + it].toInt() xor tweak[it].toInt()).toByte() }
        val plain = data.doFinal(block)
        for (i in 0 until 16) result[offset + i] = (plain[i].toInt() xor tweak[i].toInt()).toByte()
        var carry = 0
        for (i in tweak.indices) {
            val value = tweak[i].toInt() and 0xFF
            tweak[i] = ((value shl 1) or carry).toByte()
            carry = value ushr 7
        }
        if (carry != 0) tweak[0] = (tweak[0].toInt() xor 0x87).toByte()
    }
    return result
}

private const val MAX_TABLE: Int = 1024 * 1024
private fun ByteArray.text(): String = toString(Charsets.US_ASCII)
private fun ByteArray.u32(at: Int): Long = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).getInt(at).toLong() and 0xFFFFFFFFL
private fun ByteArray.u64(at: Int): Long? = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).getLong(at).takeIf { it >= 0 }
