package app.foldcade.plugins.armsx2

import app.foldcade.api.plugin.GameFileReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * The PlayStation 2 serial in a disc image, such as `SLUS-21065`.
 *
 * An ISO uses 2048-byte sectors. A raw BIN uses 2352-byte sectors, with the
 * sync pattern at the start of each one and the sector's data 16 bytes in for
 * mode 1, or 24 for mode 2. A `.cue` names that file and its layout. The data
 * track's `INDEX 01` is how far into that file, in the track's own sectors,
 * the data starts, which is what puts a shared BIN's data after its audio.
 * An `.m3u` names the first disc, which is read the same way, and a playlist
 * is not followed again. A sheet is UTF-8, or ISO-8859-1 when the bytes are
 * not UTF-8, and a leading UTF-8 BOM is ignored. A CHD v5 image is read through
 * [chdImage]. The serial is `BOOT2` in `SYSTEM.CNF`. A disc that only has `BOOT`
 * is a PlayStation 1 disc and has no PS2 serial. Anything else, including a CHD
 * whose serial sits in a FLAC or Zstandard hunk, has none here. This does not throw.
 */
internal fun ps2Serial(game: GameFileReader): String? = try {
    serialFrom(game, followPlaylist = true)
} catch (_: Exception) {
    null
} catch (_: OutOfMemoryError) {
    null
}

private fun serialFrom(game: GameFileReader, followPlaylist: Boolean): String? {
    val head = game.read(0, 8) ?: return null
    if (head.size >= 8 && head.copyOfRange(0, 8).contentEquals(CHD_MAGIC)) {
        return imageSerial(chdImage(game) ?: return null, layout = null)
    }
    if (head.isEmpty() || head[0] == 0.toByte()) return imageSerial(game, layout = null)
    val sheet = game.read(0, SHEET_CAP) ?: return null
    if (sheet.any { it == 0.toByte() }) return imageSerial(game, layout = null)
    if (isCue(sheet)) {
        val track = firstDataTrack(sheet) ?: return null
        val file = game.sibling(track.name) ?: return null
        return imageSerial(file, track.layout)
    }
    if (!followPlaylist) return null
    val name = firstPlaylistName(sheet) ?: return null
    val disc = game.sibling(name) ?: return null
    return serialFrom(disc, followPlaylist = false)
}

private fun imageSerial(game: GameFileReader, layout: DiscLayout?): String? {
    val disc = layout ?: discLayout(game) ?: return null
    val pvd = readLogical(game, disc, 16L * LOGICAL, 256) ?: return null
    if (pvd.size < 190 || pvd[0] != 1.toByte() || !pvd.copyOfRange(1, 6).contentEquals(CD001)) return null
    val rootLba = u32(pvd, 156 + 2)
    val rootSize = u32(pvd, 156 + 10)
    if (rootLba < 0 || rootSize <= 0) return null
    val directory = readLogical(game, disc, rootLba * LOGICAL, rootSize.coerceAtMost(ROOT_CAP.toLong()).toInt()) ?: return null
    val cnf = findFile(directory, "SYSTEM.CNF") ?: return null
    val text = readLogical(game, disc, cnf.lba * LOGICAL, cnf.size.coerceAtMost(CNF_CAP.toLong()).toInt()) ?: return null
    return boot2Serial(text.toString(Charsets.ISO_8859_1))
}

/** `BOOT2`'s file name, normalised the way PCSX2 normalises a serial. Null for a PlayStation 1 `BOOT` alone. */
internal fun boot2Serial(systemCnf: String): String? {
    var boot2: String? = null
    for (raw in systemCnf.split('\n')) {
        val line = raw.trim().trimEnd('\r')
        val split = line.indexOf('=')
        if (split <= 0) continue
        val key = line.substring(0, split).trim()
        val value = line.substring(split + 1).trim()
        if (value.isEmpty()) continue
        if (key == "BOOT2") boot2 = value
    }
    val path = boot2 ?: return null
    val slash = maxOf(path.lastIndexOf('\\'), path.lastIndexOf(':'))
    var name = if (slash >= 0) path.substring(slash + 1) else path
    val version = name.lastIndexOf(';')
    if (version >= 0) name = name.substring(0, version)
    if (!serialShape(name)) return null
    return buildString(name.length) {
        for (char in name) {
            when (char) {
                '.' -> Unit
                '_' -> append('-')
                else -> append(char.uppercaseChar())
            }
        }
    }
}

private data class DiscLayout(val physical: Int, val dataAt: Int, val index: Long = 0)

private data class CueTrack(val name: String, val layout: DiscLayout)

/** True when [bytes] is a cue sheet: it has a `FILE ... BINARY` line. */
private fun isCue(bytes: ByteArray): Boolean {
    for (line in sheetLines(bytes)) if (FILE_LINE.matchEntire(line) != null) return true
    return false
}

/**
 * The first data track's file, the sector layout its `TRACK` line names, and
 * the file-relative sector `INDEX 01` names. In a BIN that holds an earlier
 * track, that index is where the data sits after it. Any later `TRACK` line
 * ends this track, so the next track's `INDEX 01` does not replace it.
 */
private fun firstDataTrack(bytes: ByteArray): CueTrack? {
    var file: String? = null
    var name: String? = null
    var layout: DiscLayout? = null
    var index = 0L
    for (line in sheetLines(bytes)) {
        val named = FILE_LINE.matchEntire(line)
        if (named != null) {
            if (layout != null) break
            file = named.groupValues[1].ifEmpty { named.groupValues[2] }
            continue
        }
        val indexLine = INDEX_LINE.matchEntire(line)
        if (indexLine != null) {
            if (layout != null && indexLine.groupValues[1].toIntOrNull() == 1) {
                index = msf(indexLine.groupValues[2], indexLine.groupValues[3], indexLine.groupValues[4]) ?: return null
            }
            continue
        }
        val track = TRACK_LINE.matchEntire(line) ?: continue
        if (layout != null) break
        val selected = when (track.groupValues[1].uppercase()) {
            "MODE1/2048" -> DiscLayout(PHYSICAL_ISO, 0)
            "MODE1/2352" -> DiscLayout(PHYSICAL_RAW, 16)
            "MODE2/2352" -> DiscLayout(PHYSICAL_RAW, 24)
            else -> null
        } ?: continue
        name = file ?: return null
        layout = selected
    }
    val found = layout ?: return null
    return CueTrack(name ?: return null, found.copy(index = index))
}

/** `mm:ss:ff` as a sector count. Each second is 75 frames. */
private fun msf(mm: String, ss: String, ff: String): Long? {
    val minutes = mm.toLongOrNull() ?: return null
    val seconds = ss.toLongOrNull() ?: return null
    val frames = ff.toLongOrNull() ?: return null
    if (minutes < 0 || seconds < 0 || frames < 0 || minutes > MAX_INDEX_FRAMES) return null
    val total = minutes * 60L * 75L + seconds * 75L + frames
    if (total > MAX_INDEX_FRAMES) return null
    return total
}

/** The first disc an `.m3u` names: the first line that is not blank and not a `#` comment. */
private fun firstPlaylistName(bytes: ByteArray): String? {
    for (line in sheetLines(bytes)) {
        if (line.isEmpty() || line.startsWith("#")) continue
        return line
    }
    return null
}

private fun sheetLines(bytes: ByteArray): List<String> =
    sheetText(bytes).split('\n').map { it.trim().trimEnd('\r') }

/** UTF-8, with a leading BOM ignored. Bytes that are not UTF-8 are ISO-8859-1. */
private fun sheetText(bytes: ByteArray): String {
    val data = if (
        bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
    ) bytes.copyOfRange(3, bytes.size) else bytes
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(data))
            .toString()
    } catch (_: CharacterCodingException) {
        data.toString(Charsets.ISO_8859_1)
    }
}

private data class DirectoryFile(val lba: Long, val size: Long)

private fun discLayout(game: GameFileReader): DiscLayout? {
    if (isPrimary(game, PHYSICAL_ISO, 0)) return DiscLayout(PHYSICAL_ISO, 0)
    val header = game.read(16L * PHYSICAL_RAW, 16) ?: return null
    if (header.size < 16 || !sync(header)) return null
    val mode = header[15].toInt() and 0xFF
    val dataAt = if (mode == 2) 24 else 16
    if (isPrimary(game, PHYSICAL_RAW, dataAt)) return DiscLayout(PHYSICAL_RAW, dataAt)
    return null
}

private fun isPrimary(game: GameFileReader, physical: Int, dataAt: Int): Boolean {
    val mark = game.read(16L * physical + dataAt, 6) ?: return false
    return mark.size >= 6 && mark[0] == 1.toByte() && mark.copyOfRange(1, 6).contentEquals(CD001)
}

/** The 12-byte CD sync: `00 FF FF FF FF FF FF FF FF FF FF 00`. */
private fun sync(header: ByteArray): Boolean {
    if (header[0] != 0.toByte() || header[11] != 0.toByte()) return false
    for (index in 1..10) if (header[index] != 0xFF.toByte()) return false
    return true
}

private fun readLogical(game: GameFileReader, disc: DiscLayout, offset: Long, length: Int): ByteArray? {
    if (offset < 0 || length < 0) return null
    if (length == 0) return ByteArray(0)
    val out = ByteArray(length)
    var filled = 0
    while (filled < length) {
        val logical = offset + filled
        val sector = logical / LOGICAL
        val within = (logical % LOGICAL).toInt()
        val want = minOf(LOGICAL - within, length - filled)
        val at = sector + disc.index
        if (at < 0 || at > Long.MAX_VALUE / disc.physical) return null
        val got = game.read(at * disc.physical + disc.dataAt + within, want) ?: return null
        if (got.isEmpty()) return out.copyOf(filled)
        val take = minOf(got.size, want)
        got.copyInto(out, filled, 0, take)
        filled += take
        if (take < want) return out.copyOf(filled)
    }
    return out
}

/** [name] in an ISO 9660 directory, ignoring case and a trailing `;1`. */
private fun findFile(directory: ByteArray, name: String): DirectoryFile? {
    var at = 0
    while (at + 1 < directory.size && at < ROOT_CAP) {
        val length = directory[at].toInt() and 0xFF
        if (length == 0) {
            at = ((at / LOGICAL) + 1) * LOGICAL
            continue
        }
        if (length < 33 || at + length > directory.size) return null
        val flags = directory[at + 25].toInt() and 0xFF
        val nameLength = directory[at + 32].toInt() and 0xFF
        if (at + 33 + nameLength <= directory.size && flags and 2 == 0) {
            val raw = directory.copyOfRange(at + 33, at + 33 + nameLength).toString(Charsets.ISO_8859_1)
            val bare = raw.substringBefore(';')
            if (bare.equals(name, ignoreCase = true)) {
                val lba = u32(directory, at + 2)
                val size = u32(directory, at + 10)
                if (lba >= 0 && size >= 0) return DirectoryFile(lba, size)
            }
        }
        at += length
    }
    return null
}

/** `????_???.??` or `????-???.??`, with anything after the two characters that follow the dot. */
private fun serialShape(name: String): Boolean {
    if (name.length < 11) return false
    if (name[4] != '_' && name[4] != '-') return false
    return name[8] == '.'
}

private fun u32(bytes: ByteArray, at: Int): Long {
    if (at < 0 || at + 4 > bytes.size) return -1
    return (bytes[at].toLong() and 0xFF) or
        ((bytes[at + 1].toLong() and 0xFF) shl 8) or
        ((bytes[at + 2].toLong() and 0xFF) shl 16) or
        ((bytes[at + 3].toLong() and 0xFF) shl 24)
}

private val CD001 = "CD001".toByteArray(Charsets.US_ASCII)
private val CHD_MAGIC = "MComprHD".toByteArray(Charsets.US_ASCII)

private val FILE_LINE = Regex("""(?i)FILE\s+(?:"([^"]*)"|(\S+))\s+BINARY\s*""")
private val TRACK_LINE = Regex("""(?i)TRACK\s+\d+\s+(\S+)\s*""")
private val INDEX_LINE = Regex("""(?i)INDEX\s+(\d+)\s+(\d+):(\d+):(\d+)\s*""")

private const val LOGICAL = 2048
private const val PHYSICAL_ISO = 2048
private const val PHYSICAL_RAW = 2352
private const val ROOT_CAP = 64 * 1024
private const val CNF_CAP = 4 * 1024
private const val SHEET_CAP = 64 * 1024
private const val MAX_INDEX_FRAMES = 10_000_000L
