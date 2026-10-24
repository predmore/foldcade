package app.foldcade.plugins.armsx2

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A PS2 memory-card superblock, the first page of an 8 MB card, written out as
 * the 8192-byte `_pcsx2_superblock` a folder card keeps.
 *
 * The field values are the standard 8 MB layout: 512-byte pages, 2 pages in a
 * cluster, 8192 clusters, the first allocatable cluster at 41, and one indirect
 * FAT cluster. PCSX2 treats the block as formatted when it is this long and
 * byte 0x16 is 0x6F, which is the `o` in the magic. A version that is not 1.2
 * is left alone, so a card this does not recognise is not rewritten.
 */
internal object Ps2Superblock {
    const val SIZE = 8192
    const val FILE_NAME = "_pcsx2_superblock"

    /** `Sony PS2 Memory Card Format `, 28 bytes, with 0x6F at offset 0x16. */
    const val MAGIC = "Sony PS2 Memory Card Format "

    private const val VERSION = "1.2.0.0"
    private const val FORMATTED_AT = 0x16
    private const val FORMATTED_MARK: Byte = 0x6F

    /** A block PCSX2 will index: full length, the magic, and a 1.2 version. */
    fun isFormatted(bytes: ByteArray?): Boolean {
        if (bytes == null || bytes.size < SIZE) return false
        if (bytes[FORMATTED_AT] != FORMATTED_MARK) return false
        if (!bytes.copyOfRange(0, MAGIC.length).contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) return false
        val version = bytes.copyOfRange(0x1C, 0x28)
        val text = version.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.US_ASCII)
        return text.startsWith("1.2")
    }

    /** A formatted 8 MB superblock, erased bytes with the standard fields written in. */
    fun formatted8Mb(): ByteArray {
        val bytes = ByteArray(SIZE) { 0xFF.toByte() }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(MAGIC.toByteArray(Charsets.US_ASCII))
        val version = VERSION.toByteArray(Charsets.US_ASCII)
        buffer.put(version)
        buffer.put(ByteArray(12 - version.size))
        buffer.putShort(512) // page_len
        buffer.putShort(2) // pages_per_cluster
        buffer.putShort(16) // pages_per_block
        buffer.putShort(0xFF00.toShort())
        buffer.putInt(8192) // clusters_per_card
        buffer.putInt(41) // alloc_offset: the cluster after the FAT
        buffer.putInt(8135) // alloc_end
        buffer.putInt(0) // rootdir_cluster, relative to alloc_offset
        buffer.putInt(1023) // backup_block1, the last erase block
        buffer.putInt(1022) // backup_block2
        buffer.putLong(0)
        buffer.putInt(8) // ifc_list[0], the one indirect FAT cluster
        repeat(31) { buffer.putInt(0) }
        repeat(32) { buffer.putInt(-1) } // bad_block_list, none
        buffer.put(2) // card_type, a PS2 card
        buffer.put(0x52) // card_flags
        return bytes
    }
}
