package app.foldcade.plugins.ppsspp

import app.foldcade.api.plugin.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.ServiceLoader

class PpssppTest {
    @Test fun isoCsoAndPbpUseTheSfoAndNeverTheName() {
        val iso = iso("ULUS12345")
        assertEquals("ULUS12345", pspDiscId(reader(iso)))
        assertEquals("ULUS12345", pspDiscId(reader(cso(iso))))
        assertEquals("ULUS12345", pspDiscId(reader(cso(iso, plain = true))))
        val sfo = sfo("ULUS12345")
        val pbp = ByteArray(40 + sfo.size)
        byteArrayOf(0, 0x50, 0x42, 0x50).copyInto(pbp)
        put32(pbp, 8, 40); put32(pbp, 12, pbp.size); sfo.copyInto(pbp, 40)
        assertEquals("ULUS12345", pspDiscId(reader(pbp)))
        assertNull(pspDiscId(reader(ByteArray(100))))
        assertNull(pspDiscId(reader(iso("../../bad"))))
        assertNull(pspDiscId(reader(pbp.copyOf(50))))
        assertNull(pspDiscId(reader(cso(iso).also { it[21] = 31 })))
    }

    @Test fun preciseFolderFilterAndLaunchKeepTheTableIdentity() {
        val player = PpssppPlayer()
        val id = reader(iso("ULUS12345"))
        assertEquals(SaveFolderSearch("SAVEDATA", "ULUS12345"), player.findSaveFolders("savedata", id, EmptyFolder, false))
        val filter = player.saveArchiveFilter("savedata", id)!!
        assertTrue(filter.accepts("ULUS12345PROFILE/PARAM.SFO", byteArrayOf()))
        assertFalse(filter.accepts("OTHERULUS12345/PARAM.SFO", byteArrayOf()))
        assertFalse(filter.accepts("ULUS12346/PARAM.SFO", byteArrayOf()))
        assertNull(player.findSaveFolders("savedata", reader(ByteArray(10)), EmptyFolder, false))
        val game = Game("test", "1", "psp", Availability.LocalOnly, "Game")
        assertEquals(listOf(SaveDeclaration("savedata", null)), player.saveDeclarations(game))
        player.bindSaveFolder("content://test/tree/PSP")
        assertTrue(player.hasSaveFolder())
        val intent = player.launchIntent(LaunchRequest(game, LaunchTarget.ContentUri("content://test/game.iso"), "org.ppsspp.ppsspp"))
        assertEquals("ppsspp.psp", player.id)
        assertEquals("org.ppsspp.ppsspp.PpssppActivity", intent.componentClass)
        assertTrue(intent.grantReadUri)
        assertEquals("content://test/game.iso", intent.dataUri)
        assertTrue(ServiceLoader.load(PluginEntry::class.java).any { it is PpssppEntry })
    }

    @Test fun badSfoLengthsAndFormatsNeverYieldAnId() {
        assertNull(sfoValue(sfo("ULUS12345").also { put32(it, 24, 99999) }, "DISC_ID"))
        assertNull(sfoValue(sfo("ULUS12345").copyOf(25), "DISC_ID"))
        assertNull(sfoValue(sfo("ULUS12345").also { it[20] = 20 }, "DISC_ID"))
    }

    private fun reader(bytes: ByteArray) = object : GameFileReader {
        override val fileName = "WRONG-ID.iso"
        override fun read(offset: Long, length: Int): ByteArray? = if (offset < 0 || offset >= bytes.size) null
            else bytes.copyOfRange(offset.toInt(), minOf(bytes.size.toLong(), offset + length).toInt())
    }

    private fun sfo(id: String): ByteArray = ByteArray(80).also {
        byteArrayOf(0, 0x50, 0x53, 0x46).copyInto(it)
        put32(it, 4, 0x101); put32(it, 8, 36); put32(it, 12, 48); put32(it, 16, 1)
        it[22] = 4; it[23] = 2; put32(it, 24, id.length + 1); put32(it, 28, 32)
        "DISC_ID".toByteArray().copyInto(it, 36); id.toByteArray().copyInto(it, 48)
    }

    private fun iso(id: String): ByteArray = ByteArray(24 * 2048).also {
        val pvd = 16 * 2048
        it[pvd] = 1; "CD001".toByteArray().copyInto(it, pvd + 1)
        record(it, pvd + 156, "\u0000", 20, 2048, true)
        record(it, 20 * 2048, "PSP_GAME", 21, 2048, true)
        record(it, 21 * 2048, "PARAM.SFO;1", 22, 80, false)
        sfo(id).copyInto(it, 22 * 2048)
    }

    private fun record(bytes: ByteArray, at: Int, name: String, lba: Int, size: Int, directory: Boolean) {
        bytes[at] = (33 + name.length).toByte(); put32(bytes, at + 2, lba); put32(bytes, at + 10, size)
        bytes[at + 25] = if (directory) 2 else 0; bytes[at + 32] = name.length.toByte(); name.toByteArray().copyInto(bytes, at + 33)
    }

    private fun cso(iso: ByteArray, plain: Boolean = false): ByteArray {
        val count = iso.size / 2048
        val head = ByteArray(24 + 4 * (count + 1))
        "CISO".toByteArray().copyInto(head); put32(head, 4, 24); put32(head, 8, iso.size); put32(head, 16, 2048); head[20] = 1
        val out = ByteArrayOutputStream()
        for (i in 0 until count) {
            put32(head, 24 + i * 4, (head.size + out.size()) or if (plain) Int.MIN_VALUE else 0)
            val block = iso.copyOfRange(i * 2048, (i + 1) * 2048)
            if (plain) out.write(block) else {
                val deflater = Deflater(6, true)
                try { deflater.setInput(block); deflater.finish(); val encoded = ByteArray(4096); val size = deflater.deflate(encoded); out.write(encoded, 0, size) }
                finally { deflater.end() }
            }
        }
        put32(head, 24 + count * 4, head.size + out.size())
        return head + out.toByteArray()
    }

    private fun put32(b: ByteArray, at: Int, value: Int) { for (i in 0..3) b[at + i] = (value ushr (8 * i)).toByte() }
    private object EmptyFolder : SaveFolderAccess {
        override fun list(path: String): List<SaveFolderEntry>? = null
        override fun read(path: String, limit: Int): ByteArray? = null
        override fun read(path: String, offset: Long, length: Int): ByteArray? = null
        override fun write(path: String, bytes: ByteArray) = false
        override fun createFolder(path: String) = false
        override fun moveToBackup(path: String) = false
    }
}
