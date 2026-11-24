package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.plugins.azahar.AzaharPlayer
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SaveSignatureRootsTest {
    private val volume = Files.createTempDirectory("signature-roots")
    private val player = AzaharPlayer()
    private val reads = mutableListOf<String>()
    @After fun cleanup() { volume.toFile().deleteRecursively() }

    private fun log(path: String, at: Long, names: String = path, file: String = AzaharPlayer.LOG) {
        val target = volume.resolve(path).resolve(file)
        Files.createDirectories(target.parent)
        Files.write(target, ("User directory path: " + treeUri("com.android.externalstorage.documents", "primary:$names") + "\nAzahar Version: 2126.2 | HEAD-219d743\n").toByteArray())
        assertTrue(target.toFile().setLastModified(at))
    }

    private fun discover(): List<String> = saveSignatureRoots(volume.toFile(), player.signatureFile, 50_000) { id ->
        reads += id
        val directory = volume.resolve(id.removePrefix("primary:"))
        player.liveSignals(object : SaveFolderAccess {
            override fun list(path: String): List<SaveFolderEntry>? = null
            override fun read(path: String, limit: Int): ByteArray? = error("bounded header read required")
            override fun read(path: String, offset: Long, length: Int): ByteArray? =
                Files.newInputStream(directory.resolve(path)).use { readSaveStream(it, length) }
            override fun modifiedAtMillis(path: String): Long? = directory.resolve(path).toFile().lastModified()
            override fun write(path: String, bytes: ByteArray) = error("read only")
            override fun createFolder(path: String) = error("read only")
            override fun moveToBackup(path: String) = error("read only")
        })
    }

    @Test fun arbitraryNamesAndSecondLevelRootsAreFoundButDepthThreeIsNeverRead() {
        log("AzaharQA", 60_000)
        log("Emulation/My 3DS", 70_000)
        log("Emulation/nested/too-deep", 80_000)
        assertEquals(listOf("primary:Emulation/My 3DS", "primary:AzaharQA"), discover())
        assertFalse(reads.any { "too-deep" in it })
    }

    @Test fun staleCopiedOldLogsAndPrivateDirectoriesDoNotBecomeRoots() {
        log("stale", 40_000)
        log("copied", 60_000, names = "AzaharQA")
        log("old-only", 60_000, file = "log/azahar_log.old.txt")
        log("Android/data", 70_000)
        assertTrue(discover().isEmpty())
        assertFalse("primary:stale" in reads)
        assertFalse("primary:Android/data" in reads)
    }
}
