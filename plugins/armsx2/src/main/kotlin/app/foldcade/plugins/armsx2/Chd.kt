package app.foldcade.plugins.armsx2

import app.foldcade.api.plugin.GameFileReader
import java.io.ByteArrayInputStream
import java.util.LinkedHashMap
import java.util.zip.Inflater
import org.tukaani.xz.LZMAInputStream

/**
 * A PlayStation 2 disc in a CHD v5 image, read far enough to find `SYSTEM.CNF`.
 *
 * A DVD image has 2048-byte units and is presented as stored. A CD image has
 * 2448-byte units. The track table (`CHT2`, or `CHTR`, or the older `CHCD`)
 * names the first data track, where it starts after chdman's 4-frame padding,
 * and whether its pregap is stored in the image. The bytes presented are that
 * track's 2048-byte sectors. A cooked sector keeps those bytes at the start of
 * the frame. A raw sector keeps 2352 bytes, with the data 16 bytes in for mode 1
 * and 24 for mode 2. Hunks may be stored with zlib, lzma, or huff, or the CD
 * forms cdzl and cdlz, or uncompressed. A hunk stored with FLAC or Zstandard is
 * unread, and so is a hunk taken from a parent CHD. The map, the metadata, the
 * hunk size, and each read are capped. Anything unexpected is unread: the result
 * is null, and this does not throw.
 */
internal fun chdImage(game: GameFileReader): GameFileReader? = try {
    ChdImage.open(game)
} catch (_: Exception) {
    null
} catch (_: OutOfMemoryError) {
    null
}

/** The first data track, as 2048-byte sectors, inside a CD CHD's frame stream. */
private class CdView(val startFrame: Long, val frames: Long, val dataAt: Int)

private class ChdImage private constructor(
    private val game: GameFileReader,
    private val hunkBytes: Int,
    private val cdView: CdView?,
    private val codecs: IntArray,
    private val entries: Array<MapEntry>,
    private val size: Long,
) : GameFileReader {
    private val hunkSpan: Int = if (cdView != null) (hunkBytes / FRAME) * SECTOR else hunkBytes
    private val cache = object : LinkedHashMap<Int, ByteArray>(CACHE_HUNKS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>): Boolean = size > CACHE_HUNKS
    }
    private val stack = ArrayList<Int>(MAX_SELF_DEPTH)

    override fun read(offset: Long, length: Int): ByteArray? {
        if (offset < 0 || length < 0 || hunkSpan <= 0) return null
        if (length == 0 || offset >= size) return ByteArray(0)
        val count = minOf(length.toLong(), size - offset).toInt()
        val view = cdView
        if (view != null) return readTrack(view, offset, count)
        val out = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val pos = offset + filled
            val hunk = (pos / hunkSpan).toInt()
            val within = (pos % hunkSpan).toInt()
            val data = hunk(hunk) ?: return null
            if (within >= data.size) return null
            val take = minOf(data.size - within, count - filled)
            data.copyInto(out, filled, within, within + take)
            filled += take
        }
        return out
    }

    /** 2048-byte sectors of the data track, taken out of the 2352 bytes stored per frame. */
    private fun readTrack(view: CdView, offset: Long, count: Int): ByteArray? {
        val per = hunkBytes / FRAME
        if (per <= 0) return null
        val out = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val pos = offset + filled
            val frame = view.startFrame + pos / USER_DATA
            val within = (pos % USER_DATA).toInt()
            if (frame < 0 || frame / per > Int.MAX_VALUE) return null
            val data = hunk((frame / per).toInt()) ?: return null
            val at = (frame % per).toInt() * SECTOR + view.dataAt + within
            val want = minOf(USER_DATA - within, count - filled)
            if (at < 0 || at + want > data.size) return null
            data.copyInto(out, filled, at, at + want)
            filled += want
        }
        return out
    }

    private fun hunk(index: Int): ByteArray? {
        if (index < 0 || index >= entries.size) return null
        cache[index]?.let { return it }
        if (index in stack || stack.size >= MAX_SELF_DEPTH) return null
        stack.add(index)
        val decoded = try {
            decode(entries[index])
        } finally {
            stack.removeAt(stack.lastIndex)
        } ?: return null
        if (decoded.size != hunkSpan) return null
        cache[index] = decoded
        return decoded
    }

    private fun decode(entry: MapEntry): ByteArray? {
        return when (entry.type) {
            SELF -> {
                if (entry.offset !in 0 until entries.size.toLong()) return null
                hunk(entry.offset.toInt())
            }
            PARENT -> null
            NONE -> {
                val raw = if (entry.offset == 0L) {
                    ByteArray(hunkBytes)
                } else {
                    readFully(entry.offset, hunkBytes) ?: return null
                }
                if (entry.crc >= 0 && crc16(raw) != entry.crc) return null
                present(raw)
            }
            in 0..3 -> {
                val codec = codecs.getOrNull(entry.type) ?: return null
                if (codec == 0 || entry.length <= 0 || entry.length > hunkBytes) return null
                val compressed = readFully(entry.offset, entry.length) ?: return null
                if (cdView != null && isCdCodec(codec)) return inflateCd(compressed, codec)
                if (!isPlainCodec(codec)) return null
                val raw = inflatePlain(compressed, codec, hunkBytes) ?: return null
                if (entry.crc >= 0 && crc16(raw) != entry.crc) return null
                present(raw)
            }
            else -> null
        }
    }

    /** 2352-byte sectors from a raw hunk. A DVD hunk is already the bytes to present. */
    private fun present(raw: ByteArray): ByteArray? {
        if (cdView == null) return raw
        if (raw.size != hunkBytes || hunkBytes % FRAME != 0) return null
        val frames = hunkBytes / FRAME
        val out = ByteArray(frames * SECTOR)
        for (frame in 0 until frames) {
            raw.copyInto(out, frame * SECTOR, frame * FRAME, frame * FRAME + SECTOR)
        }
        return out
    }

    private fun inflatePlain(src: ByteArray, codec: Int, destLen: Int): ByteArray? = when (codec) {
        ZLIB -> inflateRaw(src, destLen)
        LZMA -> inflateLzma(src, destLen, lzmaDictSize(destLen))
        HUFF -> inflateHuff(src, destLen)
        else -> null
    }

    /**
     * The CD codecs store each hunk as a small header, the base codec over the
     * 2352-byte sectors (sync and ECC zeroed where they can be rebuilt), and a
     * subcode stream. The serial lives in the sector data, so the subcode is
     * left unread and the sync pattern is put back where the header says it
     * was removed.
     */
    private fun inflateCd(src: ByteArray, codec: Int): ByteArray? {
        if (hunkBytes % FRAME != 0) return null
        val frames = hunkBytes / FRAME
        val sectorBytes = frames * SECTOR
        val lengthBytes = if (hunkBytes < 65536) 2 else 3
        val eccBytes = (frames + 7) / 8
        val header = eccBytes + lengthBytes
        if (src.size < header) return null
        val baseLen = if (lengthBytes == 2) u16(src, eccBytes) else u24(src, eccBytes)
        if (baseLen <= 0 || header + baseLen > src.size) return null
        val baseCodec = when (codec) {
            CDZL -> ZLIB
            CDLZ -> LZMA
            else -> return null
        }
        val sectors = inflatePlain(src.copyOfRange(header, header + baseLen), baseCodec, sectorBytes) ?: return null
        val sync = byteArrayOf(0, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0)
        for (frame in 0 until frames) {
            val flags = src[frame / 8].toInt()
            if (flags and (1 shl (frame % 8)) != 0) sync.copyInto(sectors, frame * SECTOR)
        }
        return sectors
    }

    private fun readFully(offset: Long, length: Int): ByteArray? {
        if (offset < 0 || length <= 0 || length > MAX_HUNK_BYTES) return null
        val got = game.read(offset, length) ?: return null
        return got.takeIf { it.size == length }
    }

    private class MapEntry(val type: Int, val length: Int, val offset: Long, val crc: Int)

    companion object {
        fun open(game: GameFileReader): ChdImage? {
            val header = game.read(0, HEADER_BYTES) ?: return null
            if (header.size < HEADER_BYTES || !header.copyOfRange(0, 8).contentEquals(MAGIC)) return null
            if (u32(header, 8) != HEADER_BYTES.toLong() || u32(header, 12) != 5L) return null
            val codecs = IntArray(4) { u32(header, 16 + it * 4).toInt() }
            if (!codecsKnown(codecs)) return null
            val logical = u64(header, 32)
            val mapOffset = u64(header, 40)
            val hunkBytes = u32(header, 56).toInt()
            val unitBytes = u32(header, 60).toInt()
            if (logical <= 0 || mapOffset <= 0) return null
            if (hunkBytes <= 0 || hunkBytes > MAX_HUNK_BYTES || unitBytes <= 0) return null
            if (hunkBytes % unitBytes != 0) return null
            val cd = when (unitBytes) {
                SECTOR_DVD -> false
                FRAME -> true
                else -> return null
            }
            if (!cd && codecs.any { isCdCodec(it) }) return null
            val hunks = ((logical + hunkBytes - 1) / hunkBytes)
            if (hunks <= 0 || hunks > MAX_HUNKS) return null
            val cdView = if (!cd) null else {
                val meta = u64(header, 48)
                if (meta <= 0) return null
                cdView(game, meta, logical / FRAME) ?: return null
            }
            val count = hunks.toInt()
            val entries = if (codecs[0] == 0) {
                readUncompressedMap(game, mapOffset, count, hunkBytes)
            } else {
                readCompressedMap(game, mapOffset, count, hunkBytes, unitBytes)
            } ?: return null
            val size = cdView?.frames?.times(USER_DATA) ?: logical
            if (size <= 0) return null
            return ChdImage(game, hunkBytes, cdView, codecs, entries, size)
        }

        private fun codecsKnown(codecs: IntArray): Boolean {
            var seenZero = false
            var usable = codecs[0] == 0
            for (codec in codecs) {
                if (codec == 0) {
                    seenZero = true
                    continue
                }
                if (seenZero || !isKnownCodec(codec)) return false
                if (isPlainCodec(codec) || isCdCodec(codec)) usable = true
            }
            return usable
        }

        private fun readUncompressedMap(game: GameFileReader, offset: Long, hunks: Int, hunkBytes: Int): Array<MapEntry>? {
            val bytes = hunks.toLong() * 4
            if (bytes <= 0 || bytes > MAX_MAP_BYTES) return null
            val raw = game.read(offset, bytes.toInt()) ?: return null
            if (raw.size != bytes.toInt()) return null
            return Array(hunks) { index ->
                val units = u32(raw, index * 4)
                if (units < 0) return null
                val at = units * hunkBytes
                // A zero offset is an empty hunk, or a parent. There is no parent here.
                MapEntry(NONE, hunkBytes, at, crc = -1)
            }
        }

        private fun readCompressedMap(
            game: GameFileReader,
            offset: Long,
            hunks: Int,
            hunkBytes: Int,
            unitBytes: Int,
        ): Array<MapEntry>? {
            val prologue = game.read(offset, 16) ?: return null
            if (prologue.size < 16) return null
            val mapBytes = u32(prologue, 0)
            if (mapBytes <= 0 || mapBytes > MAX_MAP_BYTES) return null
            val first = u48(prologue, 4)
            val expected = u16(prologue, 10)
            val lengthBits = prologue[12].toInt() and 0xFF
            val selfBits = prologue[13].toInt() and 0xFF
            val parentBits = prologue[14].toInt() and 0xFF
            if (first < 0 || lengthBits > 32 || selfBits > 32 || parentBits > 32) return null
            val compressed = game.read(offset + 16, mapBytes.toInt()) ?: return null
            if (compressed.size != mapBytes.toInt()) return null
            val bits = BitIn(compressed)
            val decoder = Huffman(MAP_CODES, MAP_BITS)
            if (!decoder.importRle(bits)) return null
            val types = IntArray(hunks) { -1 }
            var last = 0
            var repeat = 0
            for (hunk in 0 until hunks) {
                if (repeat > 0) {
                    types[hunk] = last
                    repeat--
                    continue
                }
                val value = decoder.decode(bits) ?: return null
                when (value) {
                    RLE_SMALL -> {
                        types[hunk] = last
                        val extra = decoder.decode(bits) ?: return null
                        repeat = 2 + extra
                    }
                    RLE_LARGE -> {
                        types[hunk] = last
                        val high = decoder.decode(bits) ?: return null
                        val low = decoder.decode(bits) ?: return null
                        repeat = 2 + 16 + (high shl 4) + low
                    }
                    else -> {
                        types[hunk] = value
                        last = value
                    }
                }
            }
            val entries = arrayOfNulls<MapEntry>(hunks)
            var cursor = first
            var lastSelf = 0L
            var lastParent = 0L
            val raw = ByteArray(hunks * 12)
            for (hunk in 0 until hunks) {
                var type = types[hunk]
                var length = 0
                var at = cursor
                var crc = 0
                when (type) {
                    TYPE_0, TYPE_1, TYPE_2, TYPE_3 -> {
                        val wide = bits.readLong(lengthBits)
                        if (wide < 0 || wide > Int.MAX_VALUE) return null
                        length = wide.toInt()
                        crc = bits.read(16)
                        cursor += length
                    }
                    NONE -> {
                        length = hunkBytes
                        crc = bits.read(16)
                        cursor += hunkBytes.toLong()
                    }
                    SELF -> {
                        at = bits.readLong(selfBits)
                        lastSelf = at
                    }
                    PARENT -> {
                        at = bits.readLong(parentBits)
                        lastParent = at
                    }
                    SELF_1 -> {
                        lastSelf++
                        type = SELF
                        at = lastSelf
                    }
                    SELF_0 -> {
                        type = SELF
                        at = lastSelf
                    }
                    PARENT_SELF -> {
                        type = PARENT
                        at = hunk.toLong() * hunkBytes / unitBytes
                        lastParent = at
                    }
                    PARENT_1 -> {
                        lastParent += hunkBytes / unitBytes
                        type = PARENT
                        at = lastParent
                    }
                    PARENT_0 -> {
                        type = PARENT
                        at = lastParent
                    }
                    else -> return null
                }
                entries[hunk] = MapEntry(type, length, at, crc)
                val record = hunk * 12
                raw[record] = type.toByte()
                put24(raw, record + 1, length)
                put48(raw, record + 4, at)
                put16(raw, record + 10, crc)
            }
            if (bits.overflow() || crc16(raw) != expected) return null
            @Suppress("UNCHECKED_CAST")
            return entries as Array<MapEntry>
        }
    }
}

/**
 * The first data track's 2048-byte sectors.
 *
 * `CHT2` and `CHTR` are the text track records MAME writes, one per track.
 * `CHCD` is the older binary table, used when those are absent. Each track is
 * padded to a multiple of 4 frames, and a pregap whose type starts with `V`
 * is stored at the front of its track.
 */
private fun cdView(game: GameFileReader, metaOffset: Long, totalFrames: Long): CdView? {
    if (totalFrames <= 0) return null
    val metas = metadata(game, metaOffset) ?: return null
    val text = textTracks(metas)
    val tracks = when {
        text == null -> chcdTracks(metas, totalFrames) ?: return null
        text.isEmpty() -> return null
        else -> text
    }
    return viewOf(tracks, totalFrames)
}

private class Meta(val tag: String, val data: ByteArray)

private class CdTrack(
    val frames: Long,
    val extra: Long,
    val pregap: Long,
    val pregapStored: Boolean,
    val audio: Boolean,
    val dataAt: Int?,
)

/** Metadata blobs from the header's offset, following each record's `next` link. */
private fun metadata(game: GameFileReader, start: Long): List<Meta>? {
    val out = ArrayList<Meta>(8)
    val seen = HashSet<Long>()
    var offset = start
    var total = 0
    var steps = 0
    while (offset != 0L) {
        if (offset < 0 || !seen.add(offset) || steps++ >= MAX_META) return null
        val header = game.read(offset, META_HEADER) ?: return null
        if (header.size < META_HEADER) return null
        val length = u24(header, 5)
        val next = u64(header, 8)
        if (length < 0 || length > MAX_META_ENTRY || next < 0) return null
        total += length
        if (total > MAX_META_BYTES) return null
        val data = if (length == 0) {
            ByteArray(0)
        } else {
            val got = game.read(offset + META_HEADER, length) ?: return null
            if (got.size != length) return null
            got
        }
        val tagBytes = header.copyOfRange(0, 4)
        if (tagBytes.all { it in 0x20..0x7E }) out.add(Meta(tagBytes.toString(Charsets.US_ASCII), data))
        offset = next
    }
    return out
}

/**
 * Tracks from `CHTR` or `CHT2`. Null when neither tag is present.
 * An empty list means a tag was present and did not parse.
 */
private fun textTracks(metas: List<Meta>): List<CdTrack>? {
    val chtr = metas.filter { it.tag == "CHTR" }
    val cht2 = metas.filter { it.tag == "CHT2" }
    if (chtr.isEmpty() && cht2.isEmpty()) return null
    val slots = arrayOfNulls<CdTrack>(MAX_TRACKS + 1)
    var seen = 0
    var index = 0
    while (index < MAX_TRACKS) {
        val raw = chtr.getOrNull(index) ?: cht2.getOrNull(index) ?: break
        val track = if (raw.tag == "CHTR") parseChtr(raw.data) else parseCht2(raw.data)
        val parsed = track ?: return emptyList()
        if (parsed.number !in 1..MAX_TRACKS || slots[parsed.number] != null) return emptyList()
        slots[parsed.number] = parsed.track
        seen = maxOf(seen, parsed.number)
        index++
    }
    if (index == 0 || seen == 0) return emptyList()
    val tracks = ArrayList<CdTrack>(seen)
    for (number in 1..seen) tracks.add(slots[number] ?: return emptyList())
    return tracks
}

private class Numbered(val number: Int, val track: CdTrack)

private fun parseCht2(data: ByteArray): Numbered? {
    val match = CHT2_TEXT.find(metaText(data)) ?: return null
    val pregap = match.groupValues[5].toLongOrNull() ?: return null
    val stored = pregap > 0 && match.groupValues[6].startsWith('V')
    return numbered(match.groupValues[1], match.groupValues[2], match.groupValues[4], pregap, stored)
}

private fun parseChtr(data: ByteArray): Numbered? {
    val match = CHTR_TEXT.find(metaText(data)) ?: return null
    return numbered(match.groupValues[1], match.groupValues[2], match.groupValues[4], pregap = 0, stored = false)
}

private fun numbered(number: String, type: String, frames: String, pregap: Long, stored: Boolean): Numbered? {
    val n = number.toIntOrNull() ?: return null
    val count = frames.toLongOrNull() ?: return null
    val kind = trackKind(type) ?: return null
    if (count < 0 || pregap < 0) return null
    return Numbered(n, CdTrack(count, padFrames(count), pregap, stored, kind.audio, kind.dataAt))
}

/** `CHCD`: `numtrks` and then, per track, type, subtype, data size, sub size, frames, extra frames. */
private fun chcdTracks(metas: List<Meta>, totalFrames: Long): List<CdTrack>? {
    val raw = metas.firstOrNull { it.tag == "CHCD" }?.data ?: return null
    if (raw.size < 4) return null
    val header = u32(raw, 0)
    if (header < 0) return null
    val little = header > MAX_TRACKS
    val count = u32e(raw, 0, little)
    if (count <= 0 || count > MAX_TRACKS) return null
    val need = (1 + count * 6) * 4
    if (need < 0 || raw.size < need) return null
    val tracks = ArrayList<CdTrack>(count.toInt())
    var at = 4
    repeat(count.toInt()) {
        val type = u32e(raw, at, little)
        val frames = u32e(raw, at + 16, little)
        val extra = u32e(raw, at + 20, little)
        at += 24
        if (type < 0 || frames < 0 || extra < 0 || frames > totalFrames || extra > totalFrames) return null
        val kind = kindForCode(type) ?: return null
        tracks.add(CdTrack(frames, extra, pregap = 0, pregapStored = false, kind.audio, kind.dataAt))
    }
    return tracks
}

private class TrackKind(val audio: Boolean, val dataAt: Int?)

/** MAME's type names. The offset is where the 2048 user bytes sit in the stored 2352. */
private fun trackKind(type: String): TrackKind? = when (type) {
    "AUDIO", "CDG" -> TrackKind(audio = true, dataAt = null)
    "MODE1", "MODE1/2048" -> TrackKind(false, 0)
    "MODE1_RAW", "MODE1/2352" -> TrackKind(false, 16)
    "MODE2", "MODE2/2336" -> TrackKind(false, 8)
    "MODE2_FORM1", "MODE2/2048" -> TrackKind(false, 0)
    "MODE2_FORM_MIX" -> TrackKind(false, 8)
    "MODE2_RAW", "MODE2/2352", "CDI/2352" -> TrackKind(false, 24)
    "MODE2_FORM2", "MODE2/2324" -> TrackKind(false, null)
    else -> null
}

/** MAME's `CD_TRACK_*` values, in the order `CHCD` stores them. */
private fun kindForCode(type: Long): TrackKind? = when (type) {
    0L, 3L -> TrackKind(false, 0)
    1L -> TrackKind(false, 16)
    2L, 5L -> TrackKind(false, 8)
    4L -> TrackKind(false, null)
    6L -> TrackKind(false, 24)
    7L -> TrackKind(true, null)
    else -> null
}

private fun viewOf(tracks: List<CdTrack>, totalFrames: Long): CdView? {
    var cursor = 0L
    for (track in tracks) {
        if (track.frames < 0 || track.extra < 0 || track.frames > totalFrames || track.extra > totalFrames) return null
        val span = track.frames + track.extra
        if (cursor > totalFrames - span) return null
        if (track.audio) {
            cursor += span
            continue
        }
        val dataAt = track.dataAt ?: return null
        if (dataAt < 0 || dataAt + USER_DATA > SECTOR) return null
        val skip = if (track.pregapStored) track.pregap else 0L
        if (skip < 0 || skip > track.frames) return null
        val logical = track.frames - skip
        val start = cursor + skip
        if (logical <= 0 || start < 0 || start + logical > totalFrames) return null
        return CdView(start, logical, dataAt)
    }
    return null
}

/** chdman pads every track out to a multiple of [TRACK_PADDING] frames. */
private fun padFrames(frames: Long): Long {
    if (frames <= 0) return 0
    val padded = (frames + TRACK_PADDING - 1) / TRACK_PADDING
    return padded * TRACK_PADDING - frames
}

private fun metaText(data: ByteArray): String =
    data.toString(Charsets.ISO_8859_1).trim('\u0000', ' ', '\r', '\n')

private fun isPlainCodec(codec: Int): Boolean = codec == ZLIB || codec == LZMA || codec == HUFF

private fun isCdCodec(codec: Int): Boolean = codec == CDZL || codec == CDLZ

private fun isKnownCodec(codec: Int): Boolean =
    isPlainCodec(codec) || isCdCodec(codec) || codec == FLAC || codec == ZSTD || codec == CDFL || codec == CDZS

/** The LZMA dictionary MAME derives from the hunk size: level 6, then the smallest step that holds it. */
internal fun lzmaDictSize(hunkBytes: Int): Int {
    val reduce = hunkBytes.coerceAtLeast(0).toLong()
    val preset = 1 shl 25
    if (reduce >= preset) return preset
    var shift = 11
    while (shift <= 30) {
        val two = 2 shl shift
        if (reduce <= two) return two
        val three = 3 shl shift
        if (reduce <= three) return three
        shift++
    }
    return preset
}

private fun inflateRaw(src: ByteArray, destLen: Int): ByteArray? {
    if (destLen <= 0 || destLen > MAX_HUNK_BYTES) return null
    val inflater = Inflater(true)
    return try {
        inflater.setInput(src)
        val out = ByteArray(destLen)
        var filled = 0
        while (filled < destLen) {
            if (inflater.finished()) return null
            val n = inflater.inflate(out, filled, destLen - filled)
            if (n <= 0) return null
            filled += n
        }
        out
    } catch (_: Exception) {
        null
    } finally {
        inflater.end()
    }
}

private fun inflateLzma(src: ByteArray, destLen: Int, dict: Int): ByteArray? {
    if (destLen <= 0 || destLen > MAX_HUNK_BYTES || dict <= 0) return null
    return try {
        LZMAInputStream(ByteArrayInputStream(src), destLen.toLong(), LZMA_PROPS, dict).use { input ->
            val out = ByteArray(destLen)
            var filled = 0
            while (filled < destLen) {
                val n = input.read(out, filled, destLen - filled)
                if (n <= 0) return null
                filled += n
            }
            out
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

private fun inflateHuff(src: ByteArray, destLen: Int): ByteArray? {
    if (destLen <= 0 || destLen > MAX_HUNK_BYTES) return null
    val bits = BitIn(src)
    val decoder = Huffman(256, 16)
    if (!decoder.importHuffman(bits)) return null
    val out = ByteArray(destLen)
    for (index in 0 until destLen) {
        val symbol = decoder.decode(bits) ?: return null
        out[index] = symbol.toByte()
    }
    bits.flush()
    return if (bits.overflow()) null else out
}

/** MAME's bit buffer: the next bit is the high bit of the next byte. */
private class BitIn(private val data: ByteArray) {
    private var buffer = 0
    private var bits = 0
    private var index = 0
    private var bitOff = 0

    fun overflow(): Boolean = (index - bits / 8) > data.size

    fun peek(count: Int): Int {
        if (count == 0) return 0
        if (count !in 1..32) return 0
        if (count > bits) fill()
        return if (count == 32) buffer else buffer ushr (32 - count)
    }

    fun remove(count: Int) {
        buffer = if (count >= 32) 0 else buffer shl count
        bits -= count
    }

    fun read(count: Int): Int {
        if (count !in 0..31) return -1
        val value = peek(count)
        remove(count)
        return value
    }

    fun readLong(count: Int): Long {
        if (count !in 0..32) return -1
        val value = peek(count).toLong() and 0xFFFFFFFFL
        remove(count)
        val mask = if (count == 32) 0xFFFFFFFFL else (1L shl count) - 1
        return value and mask
    }

    fun flush() {
        while (bits >= 8) {
            index--
            bits -= 8
        }
        if (bitOff > bits) index++
        bits = 0
        buffer = 0
        bitOff = 0
    }

    private fun fill() {
        while (bits < 32) {
            var next = 0
            if (index < data.size) next = ((data[index].toInt() and 0xFF) shl bitOff) and 0xFF
            if (bits + 8 > 32) {
                bitOff = 32 - bits
                next = next ushr (8 - bitOff)
                buffer = buffer or next
                bits += bitOff
            } else {
                buffer = buffer or (next shl (24 - bits))
                bits += 8 - bitOff
                bitOff = 0
                index++
            }
        }
    }
}

/** Canonical Huffman codes, in the shape MAME uses for a CHD map and for the huff codec. */
private class Huffman(private val count: Int, private val maxBits: Int) {
    private val lengths = IntArray(count)
    private val lookup = IntArray(1 shl maxBits)

    fun decode(bits: BitIn): Int? {
        val peeked = bits.peek(maxBits)
        if (peeked < 0 || peeked >= lookup.size) return null
        val packed = lookup[peeked]
        val width = packed and 0x1F
        if (width == 0) return null
        bits.remove(width)
        return packed ushr 5
    }

    fun importRle(bits: BitIn): Boolean {
        val width = when {
            maxBits >= 16 -> 5
            maxBits >= 8 -> 4
            else -> 3
        }
        var at = 0
        while (at < count) {
            var node = bits.read(width)
            if (node < 0) return false
            if (node != 1) {
                lengths[at++] = node
            } else {
                node = bits.read(width)
                if (node < 0) return false
                if (node == 1) {
                    lengths[at++] = node
                } else {
                    val reps = bits.read(width) + 3
                    if (reps < 3 || at + reps > count) return false
                    repeat(reps) { lengths[at++] = node }
                }
            }
        }
        return at == count && assignAndBuild()
    }

    fun importHuffman(bits: BitIn): Boolean {
        val small = Huffman(24, 6)
        small.lengths[0] = bits.read(3)
        if (small.lengths[0] < 0) return false
        val start = bits.read(3) + 1
        if (start < 1) return false
        var marker = 0
        for (index in 1 until 24) {
            if (index < start || marker == 7) {
                small.lengths[index] = 0
            } else {
                marker = bits.read(3)
                if (marker < 0) return false
                small.lengths[index] = if (marker == 7) 0 else marker
            }
        }
        if (!small.assignAndBuild()) return false
        var temp = count - 9
        var full = 0
        while (temp != 0) {
            temp = temp ushr 1
            full++
        }
        var last = 0
        var at = 0
        while (at < count) {
            val value = small.decode(bits) ?: return false
            if (value != 0) {
                last = value - 1
                if (last < 0) return false
                lengths[at++] = last
            } else {
                var run = bits.read(3) + 2
                if (run == 9) run += bits.read(full)
                if (run < 2) return false
                while (run != 0 && at < count) {
                    lengths[at++] = last
                    run--
                }
            }
        }
        return at == count && assignAndBuild()
    }

    private fun assignAndBuild(): Boolean {
        val histo = IntArray(33)
        for (length in lengths) {
            if (length > maxBits || length < 0) return false
            histo[length]++
        }
        var cursor = 0
        for (length in 32 downTo 1) {
            val sum = cursor + histo[length]
            val next = sum ushr 1
            if (length != 1 && next * 2 != sum) return false
            histo[length] = cursor
            cursor = next
        }
        val codes = IntArray(count)
        for (symbol in 0 until count) {
            val length = lengths[symbol]
            if (length > 0) codes[symbol] = histo[length]++
        }
        for (symbol in 0 until count) {
            val length = lengths[symbol]
            if (length == 0) continue
            val shift = maxBits - length
            val start = codes[symbol] shl shift
            val end = ((codes[symbol] + 1) shl shift) - 1
            if (start < 0 || end >= lookup.size || start > end) return false
            val packed = (symbol shl 5) or length
            for (slot in start..end) lookup[slot] = packed
        }
        return true
    }
}

/** CRC-16/CCITT-FALSE, the checksum MAME stores on a hunk and on the map. */
internal fun crc16(data: ByteArray, length: Int = data.size): Int {
    var crc = 0xFFFF
    val n = minOf(length, data.size)
    for (index in 0 until n) {
        val byte = data[index].toInt() and 0xFF
        crc = ((crc shl 8) xor CRC16[(crc ushr 8) xor byte]) and 0xFFFF
    }
    return crc
}

private val CRC16 = IntArray(256) { index ->
    var crc = index shl 8
    repeat(8) {
        crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
        crc = crc and 0xFFFF
    }
    crc
}

private fun u16(bytes: ByteArray, at: Int): Int {
    if (at < 0 || at + 2 > bytes.size) return -1
    return ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
}

private fun u24(bytes: ByteArray, at: Int): Int {
    if (at < 0 || at + 3 > bytes.size) return -1
    return ((bytes[at].toInt() and 0xFF) shl 16) or
        ((bytes[at + 1].toInt() and 0xFF) shl 8) or
        (bytes[at + 2].toInt() and 0xFF)
}

private fun u32e(bytes: ByteArray, at: Int, little: Boolean): Long {
    if (!little) return u32(bytes, at)
    if (at < 0 || at + 4 > bytes.size) return -1
    return (bytes[at].toLong() and 0xFF) or
        ((bytes[at + 1].toLong() and 0xFF) shl 8) or
        ((bytes[at + 2].toLong() and 0xFF) shl 16) or
        ((bytes[at + 3].toLong() and 0xFF) shl 24)
}

private fun u32(bytes: ByteArray, at: Int): Long {
    if (at < 0 || at + 4 > bytes.size) return -1
    return ((bytes[at].toLong() and 0xFF) shl 24) or
        ((bytes[at + 1].toLong() and 0xFF) shl 16) or
        ((bytes[at + 2].toLong() and 0xFF) shl 8) or
        (bytes[at + 3].toLong() and 0xFF)
}

private fun u48(bytes: ByteArray, at: Int): Long {
    if (at < 0 || at + 6 > bytes.size) return -1
    var value = 0L
    for (index in 0 until 6) value = (value shl 8) or (bytes[at + index].toLong() and 0xFF)
    return value
}

private fun u64(bytes: ByteArray, at: Int): Long {
    if (at < 0 || at + 8 > bytes.size) return -1
    var value = 0L
    for (index in 0 until 8) value = (value shl 8) or (bytes[at + index].toLong() and 0xFF)
    return value
}

private fun put16(bytes: ByteArray, at: Int, value: Int) {
    bytes[at] = (value ushr 8).toByte()
    bytes[at + 1] = value.toByte()
}

private fun put24(bytes: ByteArray, at: Int, value: Int) {
    bytes[at] = (value ushr 16).toByte()
    bytes[at + 1] = (value ushr 8).toByte()
    bytes[at + 2] = value.toByte()
}

private fun put48(bytes: ByteArray, at: Int, value: Long) {
    for (index in 0 until 6) bytes[at + index] = (value ushr (40 - 8 * index)).toByte()
}

private fun tag(text: String): Int {
    var value = 0
    for (char in text) value = (value shl 8) or char.code
    return value
}

private val MAGIC = "MComprHD".toByteArray(Charsets.US_ASCII)

private const val HEADER_BYTES = 124
private const val META_HEADER = 16
private const val SECTOR = 2352
private const val USER_DATA = 2048
private const val SECTOR_DVD = 2048
private const val FRAME = 2448
private const val TRACK_PADDING = 4
private const val MAX_TRACKS = 99
private const val MAX_META = 128
private const val MAX_META_ENTRY = 64 * 1024
private const val MAX_META_BYTES = 256 * 1024
private const val MAX_HUNK_BYTES = 16 * 1024 * 1024
private const val MAX_HUNKS = 2_500_000
private const val MAX_MAP_BYTES = 32 * 1024 * 1024
private const val MAX_SELF_DEPTH = 8
private const val CACHE_HUNKS = 4
private const val MAP_CODES = 16
private const val MAP_BITS = 8
private const val LZMA_PROPS: Byte = 0x5D

private const val TYPE_0 = 0
private const val TYPE_1 = 1
private const val TYPE_2 = 2
private const val TYPE_3 = 3
private const val NONE = 4
private const val SELF = 5
private const val PARENT = 6
private const val RLE_SMALL = 7
private const val RLE_LARGE = 8
private const val SELF_0 = 9
private const val SELF_1 = 10
private const val PARENT_SELF = 11
private const val PARENT_0 = 12
private const val PARENT_1 = 13

private val ZLIB = tag("zlib")
private val LZMA = tag("lzma")
private val HUFF = tag("huff")
private val FLAC = tag("flac")
private val ZSTD = tag("zstd")
private val CDZL = tag("cdzl")
private val CDLZ = tag("cdlz")
private val CDFL = tag("cdfl")
private val CDZS = tag("cdzs")

private val CHT2_TEXT = Regex("""^TRACK:(\d+) TYPE:(\S+) SUBTYPE:(\S+) FRAMES:(\d+) PREGAP:(\d+) PGTYPE:(\S+) PGSUB:(\S+) POSTGAP:(\d+)""")
private val CHTR_TEXT = Regex("""^TRACK:(\d+) TYPE:(\S+) SUBTYPE:(\S+) FRAMES:(\d+)""")
