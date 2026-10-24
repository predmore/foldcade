package app.foldcade.plugins.armsx2

import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderEntry
import app.foldcade.api.plugin.SaveFolderSearch
import app.foldcade.api.plugin.SaveSyncState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Ps2CardTest {
    private val game = Bytes(iso("BOOT2 = cdrom0:\\SLUS_210.65;1\n"))

    @Test
    fun aMissingCardBecomesAFolderCardOnlyOnLaunch() {
        val folder = Mem()
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = false))
        assertTrue(folder.writes == 0)
        assertEquals(SaveFolderSearch("memcards/mcd001.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = true))
        assertTrue(Ps2Superblock.isFormatted(folder.files["memcards/mcd001.ps2/_pcsx2_superblock"]))
        assertEquals(SaveSyncState.Syncing, Ps2Card.state(folder))
    }

    @Test
    fun aFormattedFolderCardIsUsedWithoutWriting() {
        val folder = Mem()
        folder.dir("memcards/mcd001.ps2")
        folder.file("memcards/mcd001.ps2/_pcsx2_superblock", Ps2Superblock.formatted8Mb())
        folder.dir("memcards/mcd001.ps2/BASLUS-210651")
        folder.writes = 0
        assertEquals(SaveFolderSearch("memcards/mcd001.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = true))
        assertEquals(0, folder.writes)
        assertEquals(SaveSyncState.Syncing, Ps2Card.state(folder))
    }

    @Test
    fun anUnformattedFolderWithSavesIsLeftAlone() {
        val folder = Mem()
        folder.dir("memcards/mcd001.ps2")
        folder.file("memcards/mcd001.ps2/_pcsx2_superblock", ByteArray(100))
        folder.dir("memcards/mcd001.ps2/BASLUS-210651")
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = true))
        assertEquals(ByteArray(100).toList(), folder.files.getValue("memcards/mcd001.ps2/_pcsx2_superblock").toList())
        assertNull(Ps2Card.state(folder))
    }

    @Test
    fun anEmptyUnformattedFolderGetsASuperblockOnLaunch() {
        val folder = Mem()
        folder.dir("memcards/mcd001.ps2")
        folder.file("memcards/mcd001.ps2/_pcsx2_superblock", ByteArray(0))
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = false))
        assertEquals(0, folder.files.getValue("memcards/mcd001.ps2/_pcsx2_superblock").size)
        assertEquals(SaveFolderSearch("memcards/mcd001.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = true))
        assertTrue(Ps2Superblock.isFormatted(folder.files["memcards/mcd001.ps2/_pcsx2_superblock"]))
    }

    @Test
    fun anEmptyFormattedFileCardIsMovedAsideOnLaunch() {
        val folder = Mem()
        folder.dir("memcards")
        folder.file("memcards/mcd001.ps2", blankCard())
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = false))
        assertTrue(folder.files.containsKey("memcards/mcd001.ps2"))
        assertTrue(folder.backups.isEmpty())
        assertEquals(SaveFolderSearch("memcards/mcd001.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = true))
        assertEquals(listOf("memcards/mcd001.ps2"), folder.backups)
        assertEquals(blankCard().size, folder.backedUp.single().size)
        assertFalse(folder.files.containsKey("memcards/mcd001.ps2"))
        assertTrue(Ps2Superblock.isFormatted(folder.files["memcards/mcd001.ps2/_pcsx2_superblock"]))
        assertEquals(SaveSyncState.Syncing, Ps2Card.state(Mem().also { it.dir("memcards"); it.file("memcards/mcd001.ps2", blankCard()) }))
    }

    @Test
    fun aFileCardHoldingSavesIsNotReplaced() {
        val folder = Mem()
        folder.dir("memcards")
        folder.file("memcards/mcd001.ps2", nfsu2Card())
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = true))
        assertTrue(folder.backups.isEmpty())
        assertEquals(nfsu2Card().size, folder.files.getValue("memcards/mcd001.ps2").size)
        val state = Ps2Card.state(folder)
        assertTrue(state is SaveSyncState.NeedsSetup)
        assertEquals(Ps2Card.SETUP_STEPS, (state as SaveSyncState.NeedsSetup).steps)
    }

    @Test
    fun aCardThatCannotBeParsedIsNotReplaced() {
        val folder = Mem()
        folder.dir("memcards")
        folder.file("memcards/mcd001.ps2", ByteArray(8_650_752))
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = true))
        assertTrue(folder.backups.isEmpty())
        assertTrue(folder.files.containsKey("memcards/mcd001.ps2"))
        assertTrue(Ps2Card.state(folder) is SaveSyncState.NeedsSetup)
        assertNull(fileCardHoldsSaves(ByteArray(100)))
        assertNull(fileCardHoldsSaves(null))
        assertTrue(folder.ranged.all { it <= 512 })
        assertTrue(folder.ranged.sum() < Ps2Card.BLANK_CHUNK)
    }

    @Test
    fun aBlankFileCardIsReplacedOnLaunch() {
        for (size in listOf(8_650_752, 8_388_608)) {
            val folder = Mem()
            folder.dir("memcards")
            folder.file("memcards/mcd001.ps2", ByteArray(size) { 0xFF.toByte() })
            assertNull(Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = false))
            assertTrue(folder.backups.isEmpty())
            assertEquals(SaveFolderSearch("memcards/mcd001.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = true))
            assertEquals(listOf("memcards/mcd001.ps2"), folder.backups)
            assertEquals(size, folder.backedUp.single().size)
            assertTrue(Ps2Superblock.isFormatted(folder.files["memcards/mcd001.ps2/_pcsx2_superblock"]))
            assertTrue(folder.ranged.isNotEmpty())
            assertTrue(folder.ranged.all { it <= Ps2Card.BLANK_CHUNK })
            assertFalse(folder.wholeReads.contains("memcards/mcd001.ps2"))
        }
        assertEquals(false, fileCardHoldsSaves(ByteArray(8_388_608) { 0xFF.toByte() }))
    }

    @Test
    fun aFileCardWithSavesIsReadByThePagesItNeeds() {
        val folder = Mem()
        folder.dir("memcards")
        folder.file("memcards/mcd001.ps2", nfsu2Card())
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = true))
        assertTrue(folder.backups.isEmpty())
        assertTrue(folder.ranged.isNotEmpty())
        assertTrue(folder.ranged.all { it == 512 })
        assertTrue(folder.ranged.sum() < Ps2Card.BLANK_CHUNK)
        assertFalse(folder.wholeReads.contains("memcards/mcd001.ps2"))
    }

    @Test
    fun anErasedSuperblockIsFormattedWhenTheFolderHasNoSaves() {
        val erased = Mem()
        erased.dir("memcards/mcd001.ps2")
        erased.file("memcards/mcd001.ps2/_pcsx2_superblock", ByteArray(8192) { 0xFF.toByte() })
        assertEquals(SaveFolderSearch("memcards/mcd001.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, erased, forLaunch = true))
        assertTrue(Ps2Superblock.isFormatted(erased.files["memcards/mcd001.ps2/_pcsx2_superblock"]))

        val zeros = Mem()
        zeros.dir("memcards/mcd001.ps2")
        zeros.file("memcards/mcd001.ps2/_pcsx2_superblock", ByteArray(8192))
        assertEquals(SaveFolderSearch("memcards/mcd001.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, zeros, forLaunch = true))
        assertTrue(Ps2Superblock.isFormatted(zeros.files["memcards/mcd001.ps2/_pcsx2_superblock"]))

        val held = Mem()
        held.dir("memcards/mcd001.ps2")
        held.file("memcards/mcd001.ps2/_pcsx2_superblock", ByteArray(8192) { 0xFF.toByte() })
        held.dir("memcards/mcd001.ps2/BASLUS-210651")
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, held, forLaunch = true))
        assertTrue(held.files.getValue("memcards/mcd001.ps2/_pcsx2_superblock").all { it == 0xFF.toByte() })

        val other = Mem()
        other.dir("memcards/mcd001.ps2")
        val block = Ps2Superblock.formatted8Mb()
        "1.3.0.0".toByteArray(Charsets.US_ASCII).copyInto(block, 0x1C)
        other.file("memcards/mcd001.ps2/_pcsx2_superblock", block.copyOf())
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, other, forLaunch = true))
        assertTrue(block.contentEquals(other.files.getValue("memcards/mcd001.ps2/_pcsx2_superblock")))

        val longer = Mem()
        longer.dir("memcards/mcd001.ps2")
        longer.file("memcards/mcd001.ps2/_pcsx2_superblock", ByteArray(8193) { 0xFF.toByte() })
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, longer, forLaunch = true))
        assertEquals(8193, longer.files.getValue("memcards/mcd001.ps2/_pcsx2_superblock").size)
    }

    @Test
    fun aSetupWriteThatFailsThrows() {
        val make = object : Mem() {
            override fun write(path: String, bytes: ByteArray) = false
        }
        assertThrows(IllegalStateException::class.java) {
            Ps2Card.find(Ps2Card.SLOT, game, make, forLaunch = true)
        }
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, make, forLaunch = false))

        val convert = object : Mem() {
            override fun write(path: String, bytes: ByteArray) = false
        }
        convert.dir("memcards")
        convert.file("memcards/mcd001.ps2", ByteArray(8_388_608) { 0xFF.toByte() })
        assertThrows(IllegalStateException::class.java) {
            Ps2Card.find(Ps2Card.SLOT, game, convert, forLaunch = true)
        }
        assertEquals(listOf("memcards/mcd001.ps2"), convert.backups)
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, convert, forLaunch = false))
    }

    @Test
    fun theMirrorNamesTheCardForThisGame() {
        val folder = Mem()
        folder.file(
            Ps2Card.SETTINGS,
            mirror(
                """"memoryCardSlot1Enabled":true,"memoryCardSlot1Filename":"shared.ps2","eeCycleRate":0,"biosFilename":"memoryCardSlot1Filename","dev9EthHosts":[{"url":"x","enabled":true}]""",
                """"SLUS-99999":{"memoryCardSlot1Filename":"other.ps2"},"SLUS-21065":{"memoryCardSlot1Filename":"nfsu2.ps2"}""",
            ),
        )
        folder.file(Ps2Card.INI, "[MemoryCards]\nSlot1_Filename = ini.ps2\n".toByteArray())
        assertEquals(SaveFolderSearch("memcards/nfsu2.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = true))
        assertTrue(Ps2Superblock.isFormatted(folder.files["memcards/nfsu2.ps2/_pcsx2_superblock"]))
        assertFalse(folder.files.keys.any { it.contains("ini.ps2") || it.contains("shared.ps2") })
        assertEquals(SaveSyncState.Syncing, Ps2Card.state(folder))
    }

    @Test
    fun aGlobalMirrorNamesTheCardWhenTheGameSetsNone() {
        val folder = Mem()
        folder.file(Ps2Card.SETTINGS, mirror(""""memoryCardSlot1Filename":"global.ps2""""))
        folder.file(Ps2Card.INI, "[MemoryCards]\nSlot1_Filename = ini.ps2\n".toByteArray())
        assertEquals(SaveFolderSearch("memcards/global.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = true))
        val emptyName = Mem()
        emptyName.file(
            Ps2Card.SETTINGS,
            mirror(""""memoryCardSlot1Filename":"global.ps2"""", """"SLUS-21065":{"memoryCardSlot1Filename":""}"""),
        )
        assertEquals(SaveFolderSearch("memcards/global.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, emptyName, forLaunch = true))
    }

    @Test
    fun aMissingOrUnreadableMirrorFallsBackToTheIni() {
        val missing = Mem()
        missing.file(Ps2Card.INI, "[MemoryCards]\nSlot1_Filename = from-ini.ps2\n".toByteArray())
        assertEquals(SaveFolderSearch("memcards/from-ini.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, missing, forLaunch = true))
        val broken = Mem()
        broken.file(Ps2Card.SETTINGS, "{".toByteArray())
        broken.file(Ps2Card.INI, "[MemoryCards]\nSlot1_Filename = from-ini.ps2\n".toByteArray())
        assertEquals(SaveFolderSearch("memcards/from-ini.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, broken, forLaunch = true))
    }

    @Test
    fun theMirrorWinsWhenTheIniNamesAnotherCard() {
        val folder = Mem()
        folder.file(
            Ps2Card.SETTINGS,
            mirror(
                """"memoryCardSlot1Filename":"shared.ps2"""",
                """"SLUS-21065":{"memoryCardSlot1Filename":"nfsu2.ps2"}""",
            ),
        )
        folder.file(Ps2Card.INI, "[MemoryCards]\nSlot1_Filename = ini.ps2\n".toByteArray())
        folder.dir("memcards/shared.ps2")
        folder.file("memcards/shared.ps2/_pcsx2_superblock", Ps2Superblock.formatted8Mb())
        folder.dir("memcards")
        folder.file("memcards/nfsu2.ps2", nfsu2Card())
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, folder, forLaunch = true))
        assertTrue(folder.files.containsKey("memcards/nfsu2.ps2"))
        assertFalse(folder.files.keys.any { it.contains("ini.ps2") })
        assertEquals(SaveSyncState.Syncing, Ps2Card.state(folder))
        val defaults = Mem()
        defaults.file(Ps2Card.SETTINGS, """{"global":{}}""".toByteArray())
        defaults.file(Ps2Card.INI, "[MemoryCards]\nSlot1_Filename = ini.ps2\n".toByteArray())
        assertEquals(SaveFolderSearch("memcards/mcd001.ps2", "SLUS-21065"), Ps2Card.find(Ps2Card.SLOT, game, defaults, forLaunch = true))
    }

    @Test
    fun aDisabledSlotOrAnUnsafeNameDoesNotSync() {
        val disabled = Mem()
        disabled.file("PCSX2-Android.ini", "[MemoryCards]\nSlot1_Enable = false\n".toByteArray())
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, disabled, forLaunch = true))
        assertNull(Ps2Card.state(disabled))
        val unsafe = Mem()
        unsafe.file("PCSX2-Android.ini", "[MemoryCards]\nSlot1_Filename = ../mcd.ps2\n".toByteArray())
        assertNull(Ps2Card.find(Ps2Card.SLOT, game, unsafe, forLaunch = true))
        assertEquals(0, unsafe.writes)
    }

    @Test
    fun theIniNamesTheCardAndDefaultsWhenItSaysNothing() {
        assertEquals(SlotView(true, "mcd001.ps2"), view(parseSlot1("")))
        assertEquals(SlotView(true, "other.ps2"), view(parseSlot1("[MemoryCards]\nSlot1_Filename = other.ps2\n")))
        assertEquals(SlotView(false, "mcd001.ps2"), view(parseSlot1("[EmuCore]\nSlot1_Enable = false\n[MemoryCards]\nSlot1_Enable = 0\n")))
        assertNull(parseSlot1("[MemoryCards]\nSlot1_Enable = maybe\n"))
        assertNull(Ps2Card.cardName("../x"))
        assertNull(Ps2Card.cardName("a/b"))
        assertEquals("My Card.ps2", Ps2Card.cardName(" My Card.ps2 "))
    }

    @Test
    fun noSerialMeansNoSearchEvenWhenTheCardIsReady() {
        val folder = Mem()
        folder.dir("memcards/mcd001.ps2")
        folder.file("memcards/mcd001.ps2/_pcsx2_superblock", Ps2Superblock.formatted8Mb())
        assertNull(Ps2Card.find(Ps2Card.SLOT, Bytes("MComprHD".toByteArray()), folder, forLaunch = true))
        assertNull(Ps2Card.find("card2", game, folder, forLaunch = true))
    }

    @Test
    fun aBlankFormattedCardHoldsNoSaves() {
        assertEquals(false, fileCardHoldsSaves(blankCard()))
    }

    @Test
    fun theNfsu2CardsRootHoldsASaveAndNoneOfItsFiles() {
        // The same root directory is on arms-nfs-profile.ps2 and arms-before-folder.ps2.
        // The fixture keeps that directory and the superblock page, not the save's files.
        val card = nfsu2Card()
        assertEquals(8_650_752, card.size)
        assertEquals(true, fileCardHoldsSaves(card))
        val plain = ByteArray(8_388_608) { 0xFF.toByte() }
        card.copyInto(plain, 0, 0, 512)
        // The directory clusters sit at the same data offsets once ECC bytes are removed.
        copyCluster(card, 528, plain, 512, 8)
        copyCluster(card, 528, plain, 512, 9)
        copyCluster(card, 528, plain, 512, 41)
        copyCluster(card, 528, plain, 512, 42)
        assertEquals(true, fileCardHoldsSaves(plain))
    }

    private fun mirror(global: String, games: String? = null): ByteArray {
        val json = if (games == null) {
            """{"global":{$global}}"""
        } else {
            """{"global":{$global},"games":{$games}}"""
        }
        return json.toByteArray()
    }

    private fun view(slot: Slot1?): SlotView? = slot?.let { SlotView(it.enabled, it.filename) }

    private data class SlotView(val enabled: Boolean, val filename: String)

    private class Bytes(private val data: ByteArray) : GameFileReader {
        override fun read(offset: Long, length: Int): ByteArray? {
            if (offset < 0 || length < 0) return null
            if (offset >= data.size) return ByteArray(0)
            val start = offset.toInt()
            return data.copyOfRange(start, minOf(start + length, data.size))
        }
    }

    /** The save folder, as paths below it. */
    private open class Mem : SaveFolderAccess {
        val files = sortedMapOf<String, ByteArray>()
        val folders = sortedSetOf("")
        val backups = mutableListOf<String>()
        val backedUp = mutableListOf<ByteArray>()
        val ranged = mutableListOf<Int>()
        val wholeReads = mutableListOf<String>()
        var writes = 0

        fun dir(path: String) {
            var folder = path
            while (folder.isNotEmpty()) {
                folders += folder
                folder = folder.substringBeforeLast('/', "")
            }
        }

        fun file(path: String, bytes: ByteArray) {
            dir(path.substringBeforeLast('/', ""))
            files[path] = bytes
        }

        override fun list(path: String): List<SaveFolderEntry>? {
            if (path !in folders) return null
            val prefix = if (path.isEmpty()) "" else "$path/"
            val names = linkedMapOf<String, SaveFolderEntry>()
            for (folder in folders) {
                if (!folder.startsWith(prefix)) continue
                val rest = folder.removePrefix(prefix)
                if (rest.isEmpty() || '/' in rest) continue
                names[rest] = SaveFolderEntry(rest, true, 0)
            }
            for ((file, bytes) in files) {
                if (!file.startsWith(prefix)) continue
                val rest = file.removePrefix(prefix)
                if (rest.isEmpty() || '/' in rest) continue
                names[rest] = SaveFolderEntry(rest, false, bytes.size.toLong())
            }
            return names.values.toList()
        }

        override fun read(path: String, limit: Int): ByteArray? {
            wholeReads += path
            val bytes = files[path] ?: return null
            return bytes.takeIf { it.size <= limit }
        }

        override fun read(path: String, offset: Long, length: Int): ByteArray? {
            if (offset < 0 || length < 0) return null
            val bytes = files[path] ?: return null
            if (offset >= bytes.size) return ByteArray(0)
            val start = offset.toInt()
            val end = minOf((offset + length).toInt(), bytes.size)
            ranged += end - start
            return bytes.copyOfRange(start, end)
        }

        open override fun write(path: String, bytes: ByteArray): Boolean {
            writes++
            file(path, bytes.copyOf())
            return true
        }

        override fun createFolder(path: String): Boolean {
            writes++
            dir(path)
            return true
        }

        override fun moveToBackup(path: String): Boolean {
            val bytes = files[path] ?: return false
            backedUp += bytes.copyOf()
            backups += path
            files.remove(path)
            return !files.containsKey(path)
        }
    }
}

/**
 * A formatted 8 MB card, without ECC, whose root directory is `.` and `..` only.
 * Built here: no captured card is empty.
 */
internal fun blankCard(): ByteArray {
    val card = ByteArray(8_388_608) { 0xFF.toByte() }
    Ps2Superblock.formatted8Mb().copyInto(card, 0, 0, 512)
    val root = ByteArray(1024) { 0 }
    writeDir(root, 0, 0x8427, 2, ".")
    writeDir(root, 512, 0x8427, 0, "..")
    val cluster = 41 * 2 * 512
    root.copyInto(card, cluster)
    return card
}

/**
 * The root of the two captured ARMSX2 cards that hold an NFSU2 save, placed into
 * an ECC image at the clusters the superblock names. The save's own files are not here.
 */
internal fun nfsu2Card(): ByteArray {
    val card = ByteArray(8_650_752) { 0xFF.toByte() }
    val loader = Ps2CardTest::class.java.classLoader
    val page = loader.getResourceAsStream("ps2/nfsu2-superblock-page.bin")!!.use { it.readBytes() }
    val entries = loader.getResourceAsStream("ps2/nfsu2-root-entries.bin")!!.use { it.readBytes() }
    page.copyInto(card, 0)
    putPageInt(card, 8 * 2, 9)
    putPageInt(card, 9 * 2, 0x80000001.toInt())
    putPageInt(card, 9 * 2, -1, at = 4)
    writeData(card, 41, entries.copyOfRange(0, 1024))
    writeData(card, 42, entries.copyOfRange(1024, 1536) + ByteArray(512))
    return card
}

private fun writeData(card: ByteArray, cluster: Int, data: ByteArray) {
    val pages = data.size / 512
    for (page in 0 until pages) {
        val at = (cluster * 2 + page) * 528
        data.copyInto(card, at, page * 512, page * 512 + 512)
    }
}

private fun copyCluster(from: ByteArray, fromStride: Int, to: ByteArray, toStride: Int, cluster: Int) {
    for (page in 0 until 2) {
        val source = (cluster * 2 + page) * fromStride
        val dest = (cluster * 2 + page) * toStride
        from.copyInto(to, dest, source, source + 512)
    }
}

private fun putPageInt(card: ByteArray, page: Int, value: Int, at: Int = 0) {
    val buffer = ByteBuffer.wrap(card).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(page * 528 + at, value)
}

private fun writeDir(cluster: ByteArray, at: Int, mode: Int, length: Int, name: String) {
    val buffer = ByteBuffer.wrap(cluster).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(at, mode)
    buffer.putInt(at + 4, length)
    val bytes = name.toByteArray(Charsets.US_ASCII)
    bytes.copyInto(cluster, at + 0x40)
    cluster[at + 0x40 + bytes.size] = 0
}
