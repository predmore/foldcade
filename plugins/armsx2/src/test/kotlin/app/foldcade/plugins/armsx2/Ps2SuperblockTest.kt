package app.foldcade.plugins.armsx2

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The standard 8 MB superblock: 8192 bytes, and the fields a PS2 card stores at the documented offsets. */
class Ps2SuperblockTest {
    private val block = Ps2Superblock.formatted8Mb()
    private val le = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun theBlockIsTheSizePcsx2Reads() {
        assertEquals(8192, block.size)
    }

    @Test
    fun magicAndVersionMarkAFormattedCard() {
        assertArrayEquals("Sony PS2 Memory Card Format ".toByteArray(), block.copyOfRange(0, 28))
        assertArrayEquals("1.2.0.0".toByteArray() + ByteArray(5), block.copyOfRange(0x1C, 0x28))
        // Byte 0x16 is the `o` in "Format". PCSX2 indexes a folder card only when it is 0x6F.
        assertEquals(0x6F.toByte(), block[0x16])
    }

    @Test
    fun geometryMatchesTheStandard8MbCard() {
        assertEquals(512, le.getShort(0x28).toInt())
        assertEquals(2, le.getShort(0x2A).toInt())
        assertEquals(16, le.getShort(0x2C).toInt())
        assertEquals(0xFF00, le.getShort(0x2E).toInt() and 0xFFFF)
        assertEquals(8192, le.getInt(0x30))
        assertEquals(41, le.getInt(0x34))
        assertEquals(8135, le.getInt(0x38))
        assertEquals(0, le.getInt(0x3C))
        assertEquals(1023, le.getInt(0x40))
        assertEquals(1022, le.getInt(0x44))
        assertEquals(0L, le.getLong(0x48))
    }

    @Test
    fun oneIndirectFatClusterAndNoBadBlocks() {
        assertEquals(8, le.getInt(0x50))
        for (index in 1 until 32) assertEquals(0, le.getInt(0x50 + index * 4))
        for (index in 0 until 32) assertEquals(-1, le.getInt(0xD0 + index * 4))
    }

    @Test
    fun cardTypeAndFlags() {
        assertEquals(2.toByte(), block[0x150])
        assertEquals(0x52.toByte(), block[0x151])
        assertEquals(0xFF.toByte(), block[0x152])
    }

    @Test
    fun anEmptyShortOrUnformattedBlockIsNotFormatted() {
        assertFalse(Ps2Superblock.isFormatted(null))
        assertFalse(Ps2Superblock.isFormatted(ByteArray(0)))
        assertFalse(Ps2Superblock.isFormatted(block.copyOf(8191)))
        assertFalse(Ps2Superblock.isFormatted(ByteArray(8192) { 0xFF.toByte() }))
        val otherVersion = block.copyOf().also { it[0x1C] = '9'.code.toByte() }
        assertFalse(Ps2Superblock.isFormatted(otherVersion))
        assertTrue(Ps2Superblock.isFormatted(block))
    }

    @Test
    fun aSuperblockWrittenByArmsX2IsFormatted() {
        val captured = javaClass.classLoader.getResourceAsStream("ps2/folder-superblock.bin")!!.use { it.readBytes() }
        assertEquals(8192, captured.size)
        assertTrue(Ps2Superblock.isFormatted(captured))
        // The captured card's flags are 0x2B. Indexing does not read them.
        assertEquals(0x2B.toByte(), captured[0x151])
    }
}
