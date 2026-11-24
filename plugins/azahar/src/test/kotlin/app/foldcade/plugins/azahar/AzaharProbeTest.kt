package app.foldcade.plugins.azahar

import app.foldcade.api.plugin.*
import org.junit.Assert.*
import org.junit.Test

class AzaharProbeTest {
    private val fixture = javaClass.getResource("/azahar_log.txt")!!.readText()
    private val player = AzaharPlayer()

    @Test fun parsesTrimmedThorLogWithoutDecodingTheUri() {
        val folder = Folder(mapOf(AzaharPlayer.LOG to fixture))
        val signal = player.liveSignals(folder).single()
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3AAzahar", signal.namesUri)
        assertEquals(200L, signal.modifiedAtMillis)
        assertEquals(SignalStrength.Proves, signal.strength)
        assertTrue(signal.perStart) // Enabled for Foldcade starts; emulator-grant evidence stays off.
        assertEquals(AzaharPlayer.LOG, player.signatureFile)
        val encoded = fixture.replace("primary%3AAzahar", "primary%3Asaves%2FAzahar")
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3Asaves%2FAzahar",
            player.liveSignals(Folder(mapOf(AzaharPlayer.LOG to encoded))).single().namesUri)
        assertEquals(listOf(AzaharPlayer.LOG), folder.reads)
    }

    @Test fun missingLineMissingFileAndOldLogProvideNoSignal() {
        for (files in listOf(emptyMap(), mapOf("log/azahar_log.old.txt" to fixture),
            mapOf(AzaharPlayer.LOG to fixture.lineSequence().filterNot { "User directory path:" in it }.joinToString("\n")))) {
            assertTrue(player.liveSignals(Folder(files)).isEmpty())
        }
    }

    private class Folder(private val files: Map<String, String>) : SaveFolderAccess {
        val reads = mutableListOf<String>()
        override fun list(path: String): List<SaveFolderEntry>? = null
        override fun read(path: String, limit: Int): ByteArray? = error("Probe must read only the first 32 KiB")
        override fun read(path: String, offset: Long, length: Int): ByteArray? {
            reads += path
            assertEquals(0L, offset)
            assertEquals(32 * 1024, length)
            return files[path]?.toByteArray()?.take(length)?.toByteArray()
        }
        override fun modifiedAtMillis(path: String): Long? = 200
        override fun write(path: String, bytes: ByteArray): Boolean = error("Read only")
        override fun createFolder(path: String): Boolean = error("Read only")
        override fun moveToBackup(path: String): Boolean = error("Read only")
    }
}
