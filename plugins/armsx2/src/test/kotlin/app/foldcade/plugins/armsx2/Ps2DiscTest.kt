package app.foldcade.plugins.armsx2

import app.foldcade.api.plugin.GameFileReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ps2DiscTest {
    @Test
    fun anIsoReadsTheBoot2Serial() {
        val image = iso("BOOT2 = cdrom0:\\SLUS_210.65;1\nVER = 1.00\nVMODE = NTSC\n")
        assertEquals("SLUS-21065", ps2Serial(Bytes(image)))
    }

    @Test
    fun theNameIsTakenAfterTheLastSeparatorAndNormalised() {
        assertEquals("SLUS-21065", boot2Serial("BOOT2=cdrom0:\\SLUS_210.65;1"))
        assertEquals("SLUS-21065", boot2Serial("BOOT2 = cdrom:SLUS_210.65;2"))
        assertEquals("SLES-12345", boot2Serial("boot2 = cdrom0:\\SLES_123.45;1\n".replace("boot2", "BOOT2")))
        assertEquals("SLUS-21065EXTRA", boot2Serial("BOOT2 = SLUS_210.65extra"))
    }

    @Test
    fun aPlayStation1DiscHasNoPs2Serial() {
        val image = iso("BOOT = cdrom0:\\SLUS_123.45;1\n")
        assertNull(ps2Serial(Bytes(image)))
        assertNull(boot2Serial("BOOT = cdrom0:\\SLUS_210.65;1"))
    }

    @Test
    fun aNameThatIsNotASerialIsIgnored() {
        assertNull(boot2Serial("BOOT2 = cdrom0:\\NOT_A_SERIAL;1"))
        assertNull(boot2Serial("BOOT2 = "))
        assertNull(ps2Serial(Bytes("MComprHD".toByteArray())))
    }

    @Test
    fun systemCnfIsFoundIgnoringCaseAndTheVersionSuffix() {
        val image = iso("BOOT2 = cdrom0:\\SLUS_210.65;1\n", fileName = "system.cnf;1")
        assertEquals("SLUS-21065", ps2Serial(Bytes(image)))
    }

    @Test
    fun aRawMode1ImageReadsTheSameSerial() {
        assertEquals("SLUS-21065", ps2Serial(Bytes(raw(iso("BOOT2 = cdrom0:\\SLUS_210.65;1\n"), mode = 1))))
    }

    @Test
    fun aRawMode2ImageReadsTheSameSerial() {
        assertEquals("SLUS-21065", ps2Serial(Bytes(raw(iso("BOOT2 = cdrom0:\\SLUS_210.65;1\n"), mode = 2))))
    }

    @Test
    fun aCueNamesTheTrackAndItsSectorLayout() {
        val text = "BOOT2 = cdrom0:\\SLUS_123.45;1\n"
        val image = iso(text)
        val files = mapOf(
            "Game.cue" to cue("Game.bin", "MODE1/2048").toByteArray(),
            "Game.bin" to image,
            "Mode1.cue" to cue("Mode1.bin", "MODE1/2352").toByteArray(),
            "Mode1.bin" to raw(image, mode = 1),
            "Mode2.cue" to cue("Mode2.bin", "MODE2/2352").toByteArray(),
            "Mode2.bin" to raw(image, mode = 2),
        )
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Game.cue")))
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Mode1.cue")))
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Mode2.cue")))
    }

    @Test
    fun theFirstDataTrackIsReadWhenAnEarlierTrackIsAudio() {
        val sheet = """
            FILE "audio.bin" BINARY
              TRACK 01 AUDIO
                INDEX 01 00:00:00
            FILE "data.bin" BINARY
              TRACK 02 MODE2/2352
                INDEX 00 00:00:00
                INDEX 01 00:02:00
        """.trimIndent().toByteArray()
        val files = mapOf(
            "Game.cue" to sheet,
            "data.bin" to gap(150, raw(iso("BOOT2 = cdrom0:\\SLUS_123.45;1\n"), mode = 2)),
        )
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Game.cue")))
    }

    @Test
    fun aStoredPregapStartsAtIndex01() {
        val sheet = """
            FILE "pregap.bin" BINARY
              TRACK 01 MODE2/2352
                INDEX 00 00:00:00
                INDEX 01 00:02:00
        """.trimIndent().toByteArray()
        val files = mapOf(
            "Game.cue" to sheet,
            "pregap.bin" to gap(150, raw(iso("BOOT2 = cdrom0:\\SLUS_123.45;1\n"), mode = 2)),
        )
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Game.cue")))
    }

    @Test
    fun aPregapThatIsNotInTheFileStaysAtIndex01() {
        val sheet = """
            FILE "data.bin" BINARY
              TRACK 01 MODE2/2352
                PREGAP 00:02:00
                INDEX 01 00:00:00
        """.trimIndent().toByteArray()
        val files = mapOf(
            "Game.cue" to sheet,
            "data.bin" to raw(iso("BOOT2 = cdrom0:\\SLUS_123.45;1\n"), mode = 2),
        )
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Game.cue")))
    }

    @Test
    fun audioAndDataInOneBinStartAtTheDataTracksIndex() {
        val sheet = """
            FILE "combined.bin" BINARY
              TRACK 01 AUDIO
                INDEX 01 00:00:00
              TRACK 02 MODE2/2352
                INDEX 01 00:00:04
        """.trimIndent().toByteArray()
        val files = mapOf(
            "Game.cue" to sheet,
            "combined.bin" to gap(4, raw(iso("BOOT2 = cdrom0:\\SLUS_123.45;1\n"), mode = 2)),
        )
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Game.cue")))
    }

    @Test
    fun dataFollowedByAnotherTrackInOneBinKeepsTheDataIndex() {
        val image = raw(iso("BOOT2 = cdrom0:\\SLUS_123.45;1\n"), mode = 2)
        for (type in listOf(
            "AUDIO", "CDG", "MODE1/2048", "MODE1/2352", "MODE2/2336", "MODE2/2352", "CDI/2336", "CDI/2352",
        )) {
            val sheet = """
                FILE "combined.bin" BINARY
                  TRACK 01 MODE2/2352
                    INDEX 01 00:00:00
                  TRACK 02 $type
                    INDEX 01 00:04:00
            """.trimIndent().toByteArray()
            val files = mapOf("Game.cue" to sheet, "combined.bin" to image)
            assertEquals(type, "SLUS-12345", ps2Serial(Folder(files, "Game.cue")))
        }
    }

    @Test
    fun aSheetDecodesUtf8StripsABomAndFallsBackToLatin1() {
        val image = iso("BOOT2 = cdrom0:\\SLUS_123.45;1\n")
        val unicode = "Disc – 1.iso"
        val latin = "Disc\u00e9.iso"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val files = mapOf(
            "Uni.cue" to bom + cue(unicode, "MODE1/2048").toByteArray(Charsets.UTF_8),
            unicode to image,
            "Uni.m3u" to bom + "$unicode\n".toByteArray(Charsets.UTF_8),
            "Latin.cue" to cue(latin, "MODE1/2048").toByteArray(Charsets.ISO_8859_1),
            latin to image,
            "Latin.m3u" to "$latin\n".toByteArray(Charsets.ISO_8859_1),
        )
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Uni.cue")))
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Uni.m3u")))
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Latin.cue")))
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Latin.m3u")))
    }

    @Test
    fun aPlaylistReadsTheFirstDiscAndDoesNotFollowAnotherPlaylist() {
        val image = iso("BOOT2 = cdrom0:\\SLUS_123.45;1\n")
        val files = mapOf(
            "Set.m3u" to "#EXTM3U\n\n# comment\nDisc.cue\nOther.iso\n".toByteArray(),
            "Disc.cue" to cue("Disc.bin", "MODE1/2048").toByteArray(),
            "Disc.bin" to image,
            "Iso.m3u" to "Game.iso\n".toByteArray(),
            "Game.iso" to image,
            "Nest.m3u" to "Inner.m3u\n".toByteArray(),
            "Inner.m3u" to "Game.iso\n".toByteArray(),
            "Gone.cue" to cue("missing.bin", "MODE1/2048").toByteArray(),
            "Outside.cue" to cue("dir/track.bin", "MODE1/2048").toByteArray(),
        )
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Set.m3u")))
        assertEquals("SLUS-12345", ps2Serial(Folder(files, "Iso.m3u")))
        assertNull(ps2Serial(Folder(files, "Nest.m3u")))
        assertNull(ps2Serial(Folder(files, "Gone.cue")))
        assertNull(ps2Serial(Folder(files, "Outside.cue")))
    }

    @Test
    fun aChdReadsTheSerialForTheCodecsChdmanUses() {
        assertEquals(0x29B1, crc16("123456789".toByteArray()))
        assertEquals(4096, lzmaDictSize(4096))
        for (name in listOf(
            "dvd-default.chd", "dvd-zlib.chd", "dvd-lzma.chd", "dvd-huff.chd", "dvd-none.chd",
            "cd-cdlz.chd", "cd-cdzl.chd", "cd-cooked.chd", "cd-audio-first.chd", "cd-pregap.chd",
            "cd-zlib.chd", "cd-lzma.chd", "cd-huff.chd", "cd-chcd.chd", "cd-chcd-le.chd",
        )) {
            assertEquals(name, "SLUS-12345", ps2Serial(Bytes(resource(name))))
        }
        assertNull(ps2Serial(Bytes(resource("dvd-flac.chd"))))
        assertNull(ps2Serial(Bytes(resource("dvd-zstd.chd"))))
        assertNull(ps2Serial(Bytes(resource("dvd-lzma.chd").copyOf(40))))
    }

    private fun resource(name: String): ByteArray =
        javaClass.classLoader.getResourceAsStream("ps2/$name")!!.use { it.readBytes() }

    private fun cue(file: String, mode: String) = "FILE \"$file\" BINARY\n  TRACK 01 $mode\n    INDEX 01 00:00:00\n"

    /** [data] after [sectors] of leading sectors, the way a stored pregap or an earlier track is laid out. */
    private fun gap(sectors: Int, data: ByteArray, sector: Int = 2352): ByteArray {
        val out = ByteArray(sectors * sector + data.size)
        data.copyInto(out, sectors * sector)
        return out
    }

    private class Folder(private val files: Map<String, ByteArray>, private val name: String) : GameFileReader {
        override fun read(offset: Long, length: Int): ByteArray? {
            val bytes = files[name] ?: return null
            return Bytes(bytes).read(offset, length)
        }

        override fun sibling(name: String): GameFileReader? {
            if (name.isEmpty() || name == "." || name == ".." || name.any { it == '/' || it == '\\' }) return null
            if (name !in files) return null
            return Folder(files, name)
        }
    }

    private class Bytes(private val data: ByteArray) : GameFileReader {
        override fun read(offset: Long, length: Int): ByteArray? {
            if (offset < 0 || length < 0) return null
            if (offset >= data.size) return ByteArray(0)
            val start = offset.toInt()
            return data.copyOfRange(start, minOf(start + length, data.size))
        }
    }
}

/** A tiny ISO 9660 image whose root holds [fileName] in sector 21. */
internal fun iso(systemCnf: String, fileName: String = "SYSTEM.CNF;1"): ByteArray {
    val text = systemCnf.toByteArray(Charsets.ISO_8859_1)
    val image = ByteArray(22 * 2048)
    val pvd = 16 * 2048
    image[pvd] = 1
    "CD001".toByteArray().copyInto(image, pvd + 1)
    writeRecord(image, pvd + 156, 20, 2048, 2, byteArrayOf(0))
    var at = 20 * 2048
    at = writeRecord(image, at, 20, 2048, 2, byteArrayOf(0))
    at = writeRecord(image, at, 20, 2048, 2, byteArrayOf(1))
    writeRecord(image, at, 21, text.size, 0, fileName.toByteArray(Charsets.ISO_8859_1))
    text.copyInto(image, 21 * 2048)
    return image
}

/** [iso] wrapped in 2352-byte sectors. Mode 2 keeps the data 24 bytes in, mode 1 at 16. */
internal fun raw(iso: ByteArray, mode: Int): ByteArray {
    val dataAt = if (mode == 2) 24 else 16
    val sectors = iso.size / 2048
    val out = ByteArray(sectors * 2352)
    for (sector in 0 until sectors) {
        val at = sector * 2352
        out[at] = 0
        for (index in 1..10) out[at + index] = 0xFF.toByte()
        out[at + 11] = 0
        out[at + 15] = mode.toByte()
        iso.copyInto(out, at + dataAt, sector * 2048, sector * 2048 + 2048)
    }
    return out
}

private fun writeRecord(image: ByteArray, at: Int, lba: Int, size: Int, flags: Int, name: ByteArray): Int {
    val padded = 33 + name.size + if ((33 + name.size) % 2 == 0) 0 else 1
    image[at] = padded.toByte()
    putLe(image, at + 2, lba)
    putLe(image, at + 10, size)
    image[at + 25] = flags.toByte()
    image[at + 32] = name.size.toByte()
    name.copyInto(image, at + 33)
    return at + padded
}

private fun putLe(image: ByteArray, at: Int, value: Int) {
    image[at] = value.toByte()
    image[at + 1] = (value ushr 8).toByte()
    image[at + 2] = (value ushr 16).toByte()
    image[at + 3] = (value ushr 24).toByte()
}
