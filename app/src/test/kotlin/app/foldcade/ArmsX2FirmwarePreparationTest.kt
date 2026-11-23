package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.language.FirmwareCopy
import app.foldcade.plugins.armsx2.ArmsX2Player
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArmsX2FirmwarePreparationTest {
    @get:Rule val temp = TemporaryFolder()
    private val player = ArmsX2Player()
    private val game = Game("romm", "12", player.platformId, Availability.Cached, "Disc")
    private val root = "content://com.android.externalstorage.documents/tree/primary%3AARMSX2"
    private val privateBios = "/storage/emulated/0/Android/data/com.armsx2/files/bios"

    private class Documents(val root: String, biosFolder: String?) : SaveDocuments {
        val files = linkedMapOf<String, ByteArray>()
        val folders = linkedSetOf(root, "$root/bios")
        init {
            if (biosFolder != null) files["$root/PCSX2-Android.ini"] = "[Folders]\nBios = $biosFolder\n[Filenames]\nBIOS = console.bin\n".toByteArray()
        }
        override fun displayName(uri: String) = uri.substringAfterLast('/')
        override fun find(folderUri: String, name: String) = "$folderUri/$name".takeIf(files::containsKey)
        override fun children(folderUri: String, path: String): List<SaveChild>? {
            val parent = if (path.isEmpty()) folderUri else "$folderUri/$path"
            if (parent !in folders) return null
            return folders.filter { it != parent && it.substringBeforeLast('/') == parent }.map {
                SaveChild(it.substringAfterLast('/'), it, true, 0)
            } + files.filterKeys { it.substringBeforeLast('/') == parent }.map { (uri, bytes) ->
                SaveChild(uri.substringAfterLast('/'), uri, false, bytes.size.toLong())
            }
        }
        override fun create(folderUri: String, name: String) = "$folderUri/$name".also { files[it] = byteArrayOf() }
        override fun read(uri: String) = files[uri]
        override fun head(uri: String, size: Int) = files[uri]?.take(size)?.toByteArray()
        override fun readAt(uri: String, offset: Long, length: Int) = files[uri]?.let {
            val start = minOf(offset, it.size.toLong()).toInt()
            it.copyOfRange(start, minOf(start + length, it.size))
        }
        override fun write(uri: String, bytes: ByteArray) { files[uri] = bytes }
        override fun delete(uri: String) = files.remove(uri) != null
    }

    private fun prepare(docs: Documents, offer: Boolean = false, local: () -> List<FirmwareBytes> = { emptyList() }): FirmwareResult {
        val marks = mutableMapOf<String, String>()
        val saves = PlayerSaves(docs, object : SaveMarks {
            override fun mark(key: String) = marks[key]
            override fun setMark(key: String, hash: String) { marks[key] = hash }
            override fun clearMark(key: String) { marks.remove(key) }
        }, temp.newFolder().toPath(), temp.newFolder().toPath())
        val sources = if (offer) listOf(FirmwareBytes("console.bin", "offered")) else emptyList()
        return prepareFirmware(player, game, LaunchTarget.ContentUri("game", sources), "ARMSX2/bios", docs, saves,
            folder = { root }, local = local)
    }

    @Test fun outsideBiosNeverBlocksEvenWithoutASourceOrWithAnInvalidSource() {
        for (offer in listOf(false, true)) {
            val docs = Documents(root, privateBios).apply { files["offered"] = "not a BIOS".toByteArray() }
            val result = prepare(docs, offer)
            assertTrue(result.requiresImport)
            assertFalse(result.blocksLaunch)
            assertFalse(result.needsFolder)
            assertTrue(result.missing.isNotEmpty()) // The inaccessible private copy cannot be checked.
            assertTrue(result.written.isEmpty())
            assertNull(firmwareWrittenNotice(result, player, "PlayStation 2"))
        }
    }

    @Test fun outsideBiosIsWrittenForImportAndOnlyTheNewWritePostsANotice() {
        val docs = Documents(root, privateBios).apply { files["offered"] = bios() }
        val result = prepare(docs, offer = true)
        assertEquals(listOf("bios/console.bin"), result.written)
        assertArrayEquals(bios(), docs.files["$root/bios/console.bin"])
        assertFalse(result.blocksLaunch)
        val notice = firmwareWrittenNotice(result, player, "PlayStation 2")!!
        assertEquals("firmware:armsx2", notice.id)
        assertEquals("Import your PlayStation 2 BIOS", notice.title)
        assertEquals("ARMSX2 loads its BIOS from its own folder. In ARMSX2, open ☰ › Setup / Change Folders › Select your BIOS and choose ARMSX2/\u2060bios. Then start the game again.", notice.body)
        val again = prepare(docs, offer = true, local = { error("installed BIOS scanned sources") })
        assertFalse(again.blocksLaunch)
        assertTrue(again.written.isEmpty())
        assertNull(firmwareWrittenNotice(again, player, "PlayStation 2"))
    }

    @Test fun insideBiosStillBlocksWhenMissingAndSaysReadyAfterWriting() {
        for (biosFolder in listOf("bios", "/storage/emulated/0/ARMSX2/bios", null)) {
            val docs = Documents(root, biosFolder)
            val missing = prepare(docs)
            assertFalse(missing.requiresImport)
            assertTrue(missing.blocksLaunch)
            assertNull(firmwareWrittenNotice(missing, player, "PlayStation 2"))
            docs.files["offered"] = bios()
            val written = prepare(docs, offer = true)
            assertFalse(written.blocksLaunch)
            assertEquals(listOf("bios/console.bin"), written.written)
            val notice = firmwareWrittenNotice(written, player, "PlayStation 2")!!
            assertEquals("PlayStation 2 BIOS ready", notice.title)
            assertEquals(FirmwareCopy.readyBody, notice.body)
        }
    }

    /** Synthetic console ROM directory and ROMVER, with no console firmware bytes. */
    private fun bios(): ByteArray = ByteArray(144).also { bytes ->
        fun entry(at: Int, name: String, size: Int) {
            name.toByteArray().copyInto(bytes, at)
            bytes[at + 12] = size.toByte()
        }
        entry(64, "RESET", 64)
        entry(80, "ROMDIR", 64)
        entry(96, "ROMVER", 14)
        "0230AC20080220".toByteArray().copyInto(bytes, 128)
    }
}
