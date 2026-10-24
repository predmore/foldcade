package app.foldcade.plugins.armsx2

import app.foldcade.api.plugin.FirmwareOffer

/**
 * A PS2 console BIOS, from the `ROMVER` file in its ROM directory.
 * [version] is the four-digit version (`0230` is 230). [region] is the region
 * letter. [date] is `YYYYMMDD`.
 */
internal data class Ps2Romver(
    val version: Int,
    val region: Char,
    val date: Int,
    val text: String,
)

/**
 * [header] as a PS2 console BIOS: a ROM directory whose first entry is `RESET`,
 * and a `ROMVER` of type `C`. A devkit (`D`) or an arcade image (region `T`,
 * including `COH-H`) is not one. Null when [header] does not hold both.
 */
internal fun parsePs2ConsoleBios(header: ByteArray): Ps2Romver? {
    var offset = 0
    while (offset + ENTRY_SIZE <= header.size) {
        if (entryName(header, offset) == "RESET") return romverAfter(header, offset)
        offset += ENTRY_SIZE
    }
    return null
}

/**
 * One console BIOS out of [offered], for a device in [country] (ISO 3166-1 alpha-2).
 * Japan takes region `J`. The US, Canada, and Latin America take `A`. Everyone
 * else takes `E`. When that region has none, the order is `A`, then `E`, then
 * `J`, then any console BIOS. Within a region, the highest version, then the
 * latest date, then the earliest name.
 */
internal fun choosePs2Bios(offered: List<FirmwareOffer>, country: String): List<FirmwareOffer> {
    val parsed = offered.mapNotNull { offer -> parsePs2ConsoleBios(offer.header)?.let { offer to it } }
    if (parsed.isEmpty()) return emptyList()
    val preferred = preferredRegion(country)
    val pools = listOf(preferred, 'A', 'E', 'J').distinct().map { region ->
        parsed.filter { it.second.region == region }
    } + listOf(parsed)
    val pool = pools.first { it.isNotEmpty() }
    val best = pool.sortedWith(
        compareByDescending<Pair<FirmwareOffer, Ps2Romver>> { it.second.version }
            .thenByDescending { it.second.date }
            .thenBy { it.first.name },
    ).first()
    return listOf(best.first)
}

/** `J` for Japan, `A` for the US, Canada, and Latin America, `E` otherwise. */
internal fun preferredRegion(country: String): Char = when (country.uppercase()) {
    "JP" -> 'J'
    "US", "CA" -> 'A'
    in LATIN_AMERICA -> 'A'
    else -> 'E'
}

/**
 * Latin America, the countries whose PS2 consoles shipped with the Americas BIOS.
 * Belize, Guyana, Suriname, and the English-speaking Caribbean are not in this set.
 */
private val LATIN_AMERICA = setOf(
    "AR", "BO", "BR", "CL", "CO", "CR", "CU", "DO", "EC", "SV",
    "GT", "HT", "HN", "MX", "NI", "PA", "PY", "PE", "PR", "UY", "VE",
)

private fun romverAfter(header: ByteArray, table: Int): Ps2Romver? {
    var entry = table
    var fileOffset = 0L
    var guard = 0
    while (entry + ENTRY_SIZE <= header.size && guard++ < header.size / ENTRY_SIZE) {
        val name = entryName(header, entry) ?: return null
        if (name.isEmpty()) return null
        val size = unsignedInt(header, entry + 12)
        if (name == "ROMVER") {
            if (fileOffset < 0 || fileOffset + ROMVER_LENGTH > header.size) return null
            val text = ascii(header, fileOffset.toInt(), ROMVER_LENGTH) ?: return null
            return consoleRomver(text)
        }
        fileOffset += aligned(size)
        if (fileOffset < 0) return null
        entry += ENTRY_SIZE
    }
    return null
}

/** A 14-character `ROMVER` of type `C` that is not an arcade image. */
private fun consoleRomver(text: String): Ps2Romver? {
    if (text.length != ROMVER_LENGTH) return null
    if (!text.substring(0, 4).all { it.isDigit() } || !text.substring(6).all { it.isDigit() }) return null
    val region = text[4]
    val type = text[5]
    if (type != 'C' || region == 'T' || region !in 'A'..'Z') return null
    val date = text.substring(6).toInt()
    val month = date / 100 % 100
    val day = date % 100
    if (month !in 1..12 || day !in 1..31) return null
    return Ps2Romver(version = text.substring(0, 4).toInt(), region = region, date = date, text = text)
}

/** The directory entry's name, or null when it is not NUL-terminated within the field. */
private fun entryName(header: ByteArray, offset: Int): String? {
    var end = 0
    while (end < NAME_FIELD && header[offset + end] != 0.toByte()) {
        val byte = header[offset + end].toInt() and 0xff
        if (byte < 0x20 || byte > 0x7e) return null
        end += 1
    }
    if (end == NAME_FIELD) return null
    return header.decodeToString(offset, offset + end)
}

private fun unsignedInt(header: ByteArray, offset: Int): Long =
    (header[offset].toLong() and 0xff) or
        ((header[offset + 1].toLong() and 0xff) shl 8) or
        ((header[offset + 2].toLong() and 0xff) shl 16) or
        ((header[offset + 3].toLong() and 0xff) shl 24)

private fun ascii(header: ByteArray, offset: Int, length: Int): String? {
    val chars = CharArray(length)
    for (index in 0 until length) {
        val byte = header[offset + index].toInt() and 0xff
        if (byte < 0x20 || byte > 0x7e) return null
        chars[index] = byte.toChar()
    }
    return String(chars)
}

/** The next file's offset: a size that is not a multiple of 16 rounds up to one. */
private fun aligned(size: Long): Long = if (size % ENTRY_SIZE == 0L) size else (size + ENTRY_SIZE) and 0xfffffff0L

private const val ENTRY_SIZE = 16
private const val NAME_FIELD = 10
private const val ROMVER_LENGTH = 14
