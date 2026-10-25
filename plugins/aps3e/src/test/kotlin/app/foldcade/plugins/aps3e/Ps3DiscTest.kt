package app.foldcade.plugins.aps3e

import app.foldcade.api.plugin.GameFileReader
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ps3DiscTest {
    @Test
    fun aDecryptedIsoReadsItsTitleId() {
        val sfo = sfo(mapOf("TITLE_ID" to "BLUS30464", "TITLE" to "Skate 3", "CATEGORY" to "DG"))
        val id = ps3TitleId(Bytes(iso("PS3_GAME", "PARAM.SFO", sfo)))
        assertEquals("BLUS30464", id)
        assertEquals("Skate 3", sfoValue(sfo, "TITLE"))
        assertEquals("DG", sfoValue(sfo, "CATEGORY"))
        assertNull(sfoValue(sfo, "APP_VER"))
    }

    @Test
    fun namesMayBeLowerCaseOrCarryAVersion() {
        val sfo = sfo(mapOf("TITLE_ID" to "BLUS30464"))
        assertEquals("BLUS30464", ps3TitleId(Bytes(iso("ps3_game", "param.sfo", sfo))))
        assertEquals("BLUS30464", ps3TitleId(Bytes(iso("PS3_GAME;1", "PARAM.SFO;1", sfo))))
    }

    @Test
    fun aTitleIdStopsAtTheFirstNul() {
        val sfo = sfo(mapOf("TITLE_ID" to "BLUS30464\u0000nope"))
        assertEquals("BLUS30464", ps3TitleId(Bytes(iso("PS3_GAME", "PARAM.SFO", sfo))))
    }

    @Test
    fun aMissingGameDirectoryHasNoTitleId() {
        val sfo = sfo(mapOf("TITLE_ID" to "BLUS30464"))
        assertNull(ps3TitleId(Bytes(iso("PS3_EXTRA", "PARAM.SFO", sfo))))
    }

    @Test
    fun aBadSfoMagicHasNoTitleId() {
        val sfo = sfo(mapOf("TITLE_ID" to "BLUS30464")).also { it[1] = 'X'.code.toByte() }
        assertNull(ps3TitleId(Bytes(iso("PS3_GAME", "PARAM.SFO", sfo))))
        assertNull(sfoValue(sfo, "TITLE_ID"))
    }

    @Test
    fun aTitleIdOfTheWrongShapeIsRefused() {
        for (id in listOf("BLUS3046", "BLUS304644", "blus30464", "BLU-30464", "")) {
            assertNull(id, ps3TitleId(Bytes(iso("PS3_GAME", "PARAM.SFO", sfo(mapOf("TITLE_ID" to id))))))
        }
    }

    @Test
    fun aTruncatedImageHasNoTitleId() {
        assertNull(ps3TitleId(Bytes(ByteArray(0x100))))
        val cut = iso("PS3_GAME", "PARAM.SFO", sfo(mapOf("TITLE_ID" to "BLUS30464"))).copyOf(0x8000 + 20)
        assertNull(ps3TitleId(Bytes(cut)))
    }

    @Test
    fun aDirectoryGapSkipsToTheNextSector() {
        val sfo = sfo(mapOf("TITLE_ID" to "BLUS30464"))
        val file = record("PARAM.SFO", 20, sfo.size.toLong(), dir = false)
        val game = ByteArray(SECTOR)
        file.copyInto(game)
        val gameRecord = record("PS3_GAME", 19, game.size.toLong(), dir = true)
        val root = ByteArray(SECTOR + gameRecord.size)
        gameRecord.copyInto(root, SECTOR)
        val image = image(root, game, sfo, gameLba = 19, fileLba = 20)
        assertEquals("BLUS30464", ps3TitleId(Bytes(image)))
    }

    @Test
    fun aReadThatFailsHasNoTitleId() {
        val throwing = object : GameFileReader {
            override fun read(offset: Long, length: Int): ByteArray? = error("no")
        }
        val oom = object : GameFileReader {
            override fun read(offset: Long, length: Int): ByteArray? = throw OutOfMemoryError("no")
        }
        assertNull(ps3TitleId(throwing))
        assertNull(ps3TitleId(oom))
    }

    private class Bytes(private val data: ByteArray) : GameFileReader {
        override fun read(offset: Long, length: Int): ByteArray? {
            if (offset < 0 || length < 0 || offset > data.size) return null
            val start = offset.toInt()
            val end = (start.toLong() + length).coerceAtMost(data.size.toLong()).toInt()
            return data.copyOfRange(start, end)
        }
    }
}

internal fun iso(dirName: String, fileName: String, sfo: ByteArray): ByteArray {
    val file = record(fileName, 19, sfo.size.toLong(), dir = false)
    val gameRecord = record(dirName, 18, file.size.toLong(), dir = true)
    return image(gameRecord, file, sfo, gameLba = 18, fileLba = 19)
}

private fun image(root: ByteArray, game: ByteArray, sfo: ByteArray, gameLba: Int, fileLba: Int): ByteArray {
    val pvd = ByteArray(SECTOR)
    pvd[0] = 1
    "CD001".toByteArray(Charsets.US_ASCII).copyInto(pvd, 1)
    pvd[6] = 1
    record("\u0000", 17, root.size.toLong(), dir = true).copyInto(pvd, 156)
    val sectors = fileLba + (sfo.size + SECTOR - 1) / SECTOR
    val image = ByteArray(sectors * SECTOR)
    pvd.copyInto(image, 16 * SECTOR)
    root.copyInto(image, 17 * SECTOR)
    game.copyInto(image, gameLba * SECTOR)
    sfo.copyInto(image, fileLba * SECTOR)
    return image
}

private fun record(name: String, lba: Int, size: Long, dir: Boolean): ByteArray {
    val nameBytes = name.toByteArray(Charsets.ISO_8859_1)
    var length = 33 + nameBytes.size
    if (length % 2 != 0) length += 1
    val bytes = ByteArray(length)
    bytes[0] = length.toByte()
    putU32(bytes, 2, lba.toLong())
    putU32(bytes, 10, size)
    bytes[25] = if (dir) 2 else 0
    bytes[32] = nameBytes.size.toByte()
    nameBytes.copyInto(bytes, 33)
    return bytes
}

internal fun sfo(values: Map<String, String>): ByteArray {
    val keys = values.keys.toList()
    val index = ByteArray(20 + keys.size * 16)
    index[1] = 'P'.code.toByte()
    index[2] = 'S'.code.toByte()
    index[3] = 'F'.code.toByte()
    putU32(index, 4, 0x101)
    val keyBlob = ByteArrayOutputStream()
    val keyOffsets = IntArray(keys.size)
    for ((entry, key) in keys.withIndex()) {
        keyOffsets[entry] = keyBlob.size()
        keyBlob.write(key.toByteArray(Charsets.UTF_8))
        keyBlob.write(0)
    }
    val keyBytes = keyBlob.toByteArray()
    var dataTable = index.size + keyBytes.size
    if (dataTable % 4 != 0) dataTable += 4 - dataTable % 4
    putU32(index, 8, index.size.toLong())
    putU32(index, 12, dataTable.toLong())
    putU32(index, 16, keys.size.toLong())
    val data = ByteArrayOutputStream()
    for ((entry, key) in keys.withIndex()) {
        val offset = data.size()
        val text = values.getValue(key).toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        data.write(text)
        while (data.size() % 4 != 0) data.write(0)
        val at = 20 + entry * 16
        putU16(index, at, keyOffsets[entry])
        putU16(index, at + 2, 0x0204)
        putU32(index, at + 4, text.size.toLong())
        putU32(index, at + 8, text.size.toLong())
        putU32(index, at + 12, offset.toLong())
    }
    val out = ByteArray(dataTable + data.size())
    index.copyInto(out)
    keyBytes.copyInto(out, index.size)
    data.toByteArray().copyInto(out, dataTable)
    return out
}

private fun putU16(bytes: ByteArray, at: Int, value: Int) {
    bytes[at] = value.toByte()
    bytes[at + 1] = (value ushr 8).toByte()
}

private fun putU32(bytes: ByteArray, at: Int, value: Long) {
    bytes[at] = value.toByte()
    bytes[at + 1] = (value ushr 8).toByte()
    bytes[at + 2] = (value ushr 16).toByte()
    bytes[at + 3] = (value ushr 24).toByte()
}

private const val SECTOR = 2048
