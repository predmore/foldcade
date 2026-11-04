package app.foldcade.plugins.flycast

import app.foldcade.api.plugin.GameFileReader

/** IP.BIN product_number, as loadGameSpecificSettings/reios_disk_id use it.
 * GDI track 3 starts at LBA 45000; RawTrackFile's 2352-byte Mode 1/2 headers
 * leave 16/24 bytes before the user data. No filename or title fallback.
 * https://github.com/flyinghead/flycast/tree/7adbf3caef251b153e348df3f469a0f35878339d/core/imgread
 * https://github.com/flyinghead/flycast/blob/7adbf3caef251b153e348df3f469a0f35878339d/core/reios/reios.h
 */
internal fun dreamcastId(game: GameFileReader): String? = when (game.fileName?.substringAfterLast('.')?.lowercase()) {
    "gdi" -> gdiId(game)
    "iso" -> bootId(game.read(0, 256))
    else -> null // CDI/CHD/CUE and playlists require their own verified disc readers.
}

private fun bootId(bytes: ByteArray?): String? {
    if (bytes == null || bytes.size < 256 || String(bytes, 0, 16, Charsets.US_ASCII) != "SEGA SEGAKATANA ") return null
    val raw = String(bytes, 64, 10, Charsets.US_ASCII).trimEnd(' ', '\t', '\r', '\n').substringBefore('\u0000')
    return raw.takeIf { it.isNotBlank() && it.all { c -> c.code in 32..126 } }
}

private fun gdiId(game: GameFileReader): String? {
    val bytes = game.read(0, 16384) ?: return null
    if (bytes.size >= 16384) return null
    val lines = bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.toList()
    val count = lines.firstOrNull()?.trim()?.toIntOrNull()?.takeIf { it in 3..99 } ?: return null
    if (lines.size != count + 1) return null
    val pattern = Regex("""\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(?:"([^"]+)"|(\S+))\s+(-?\d+)\s*""")
    val tracks = lines.drop(1).map { pattern.matchEntire(it)?.groupValues ?: return null }
    if (tracks.map { it[1].toIntOrNull() }.toSet() != (1..count).toSet()) return null
    val track = tracks.single { it[1].toIntOrNull() == 3 }
    if (track[2].toIntOrNull() != 45000 || track[3].toIntOrNull() != 4) return null
    val size = track[4].toIntOrNull()?.takeIf { it == 2048 || it == 2352 } ?: return null
    val offset = track[7].toLongOrNull()?.takeIf { it in 0..Int.MAX_VALUE } ?: return null
    val filename = track[5].ifEmpty { track[6] }
    if (filename in setOf(".", "..") || filename.any { it == '/' || it == '\\' || it.code < 32 }) return null
    val reader = game.sibling(filename) ?: return null
    val skip = if (size == 2048) 0 else {
        val sector = reader.read(offset, 16) ?: return null
        if (sector.size < 16 || sector[0] != 0.toByte() || sector[11] != 0.toByte() || (1..10).any { sector[it] != 0xff.toByte() }) return null
        when (sector[15].toInt()) { 1 -> 16; 2 -> 24; else -> return null }
    }
    return bootId(reader.read(offset + skip, 256))
}
