package app.foldcade.plugins.flycast

import app.foldcade.api.plugin.*
import org.junit.Assert.*
import org.junit.Test
import java.util.ServiceLoader

class FlycastTest {
    private fun reader(name: String, bytes: ByteArray, siblings: Map<String, GameFileReader> = emptyMap()) = object : GameFileReader {
        override val fileName = name
        override fun read(offset: Long, length: Int): ByteArray? = if (offset in 0..bytes.size.toLong())
            bytes.copyOfRange(offset.toInt(), minOf(bytes.size.toLong(), offset + length).toInt()) else null
        override fun sibling(name: String) = siblings[name]
    }
    private fun boot(id: String = "T13008D 05") = ByteArray(256) { ' '.code.toByte() }.also {
        "SEGA SEGAKATANA ".toByteArray().copyInto(it); id.toByteArray().copyInto(it, 64)
    }
    private fun folder(config: String = "[config]\nPerGameVmu = yes\n", files: Map<String, ByteArray> = emptyMap()) = object : SaveFolderAccess {
        override fun list(path: String): List<SaveFolderEntry>? = when(path) {
            "" -> listOf(SaveFolderEntry("emu.cfg", false, config.length.toLong()), SaveFolderEntry("data", true, 0)) +
                files.filterKeys { '/' !in it }.map { SaveFolderEntry(it.key, false, it.value.size.toLong()) }
            "data" -> files.filterKeys { it.startsWith("data/") }.map { SaveFolderEntry(it.key.removePrefix("data/"), false, it.value.size.toLong()) }
            else -> emptyList()
        }
        override fun read(path: String, limit: Int) = read(path, 0L, limit)
        override fun read(path: String, offset: Long, length: Int) = (if (path == "emu.cfg") config.toByteArray() else files[path])?.let {
            it.copyOfRange(minOf(offset.toInt(), it.size), minOf(it.size.toLong(), offset + length).toInt())
        }
        override fun write(path: String, bytes: ByteArray) = error("read only")
        override fun createFolder(path: String) = error("read only")
        override fun moveToBackup(path: String) = error("read only")
    }
    private val player = FlycastPlayer().also { it.bindSaveFolder("content://com.android.externalstorage.documents/tree/primary%3AFlycast") }

    @Test fun serviceAndStablePlayerId() {
        assertEquals("flycast.dreamcast", ServiceLoader.load(PluginEntry::class.java).filterIsInstance<FlycastEntry>().single().players.single().id)
    }
    @Test fun authoritativeProductNumberPreservesInternalSpaceAndNeverUsesFilename() {
        assertEquals("T13008D 05", dreamcastId(reader("wrong.iso", boot())))
        assertEquals("T13008D_05_vmu_save_A1.bin", vmuName("T13008D 05"))
        assertNull(dreamcastId(reader("T13008D 05.chd", boot())))
        assertNull(dreamcastId(reader("T13008D 05.iso", ByteArray(256))))
        assertNull(dreamcastId(reader("short.iso", boot().take(75).toByteArray())))
    }
    @Test fun gdiUsesHighDensityTrackAndOffsetWithQuotedFilename() {
        val gdi = "3\n1 0 4 2352 track01.bin 0\n2 450 0 2352 track02.raw 0\n3 45000 4 2048 \"track 03.bin\" 2048\n"
        val track = reader("track 03.bin", ByteArray(2048) + boot())
        val siblings = mapOf("track 03.bin" to track)
        assertEquals("T13008D 05", dreamcastId(reader("game.gdi", gdi.toByteArray(), siblings)))
        assertEquals("T13008D 05", dreamcastId(reader("game.gdi", gdi.replace("3 45000", "03 045000").toByteArray(), siblings)))
        assertNull(dreamcastId(reader("game.gdi", gdi.toByteArray())))
        assertNull(dreamcastId(reader("game.gdi", gdi.replace("45000", "45001").toByteArray(), siblings)))
        assertNull(dreamcastId(reader("game.gdi", gdi.replace("track 03.bin", "../track03.bin").toByteArray(), siblings)))
        assertNull(dreamcastId(reader("game.gdi", gdi.replace("3 45000", "1 45000").toByteArray(), siblings)))
    }
    @Test fun rawTrackValidatesSyncAndModeBeforeReadingIpBin() {
        val sector = ByteArray(2352)
        (1..10).forEach { sector[it] = 0xff.toByte() }; sector[15] = 1
        boot().copyInto(sector,16)
        val gdi = "3\n1 0 4 2352 a.bin 0\n2 300 0 2352 b.raw 0\n3 45000 4 2352 c.bin 0\n".toByteArray()
        assertEquals("T13008D 05", dreamcastId(reader("game.gdi", gdi, mapOf("c.bin" to reader("c.bin",sector)))))
        sector[15] = 0
        assertNull(dreamcastId(reader("game.gdi", gdi, mapOf("c.bin" to reader("c.bin",sector)))))
    }
    @Test fun configHonorsGlobalAndPerGameSettingsWithoutChangingThem() {
        val game = reader("title.iso", boot())
        assertEquals(listOf(SaveFolderPart("", "data")), player.findSaveFolderParts("vmu", game, folder()))
        assertNull(player.findSaveFolderParts("vmu", game, folder("[config]\nPerGameVmu = no\n")))
        assertNull(player.findSaveFolderParts("vmu", game, folder("[config]\r\nPerGameVmu = yes\r\n")))
        assertNull(player.findSaveFolderParts("vmu", game, folder("[config]\nUsePhysicalVmuMemory = yes\n")))
        assertNull(player.findSaveFolderParts("vmu", game, folder("[config]\nPerGameVmu = yes\n[T13008D 05]\nconfig.PerGameVmu = no\n")))
        assertNotNull(player.findSaveFolderParts("vmu", game, folder("[config]\nPerGameVmu = no\n[T13008D 05]\nconfig.PerGameVmu = yes\n")))
        assertTrue(player.saveSyncState(folder("[config]\nPerGameVmu = no\n")) is SaveSyncState.NeedsSetup)
        assertNull(player.findSaveFolderParts("vmu", game, folder("[config]\nDreamcast.VMUPath = /sdcard/Other\n")))
        assertEquals(listOf(SaveFolderPart("", "cards")), player.findSaveFolderParts("vmu", game, folder("[config]\nDreamcast.VMUPath = /sdcard/Flycast/cards\n")))
        assertNull(player.findSaveFolderParts("vmu", game, folder("[config]\nDreamcast.VMUPath = /sdcard/Flycast/../Other\n")))
    }
    @Test fun onlyPerGameA1CanBeReadOrPlaced() {
        val filter = player.saveArchiveFilter("vmu", reader("title.iso", boot()))!!
        assertTrue(filter.accepts("T13008D_05_vmu_save_A1.bin", byteArrayOf()))
        for (path in listOf("vmu_save_A1.bin", "T13008D_05_vmu_save_A2.bin", "OTHER_vmu_save_A1.bin", "../T13008D_05_vmu_save_A1.bin")) assertFalse(path, filter.accepts(path, byteArrayOf()))
        assertNull(player.findSaveFolderParts("vmu", reader("title.iso", boot()), folder(files = mapOf("T13008D_05_vmu_save_A1.bin" to byteArrayOf(1)))))
    }
}
