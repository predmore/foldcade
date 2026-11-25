package app.foldcade

import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderEntry
import app.foldcade.plugins.retroarch.RetroArchConfigFile
import app.foldcade.plugins.retroarch.RetroArchPlayer
import app.foldcade.plugins.retroarch.SHARED_STORAGE
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RetroArchSaveProbeTest {
    private val pkg = "com.retroarch.aarch64"
    private val app = "app.foldcade"
    private val parent = "$SHARED_STORAGE/Android/data/$pkg/files"
    private val config = RetroArchPlayer.configPath(pkg)

    @Test fun onlyTheSelectedPackagesProviderCanReachItsConfigWithAReadOnlyGrant() {
        val current = treeUri("$pkg.documents", parent)
        val other = treeUri("com.retroarch.documents", parent)
        val bytes = "sort_savefiles_enable = true".toByteArray()
        val access = object : SaveFolderAccess by emptyAccess {
            override fun list(path: String) = if (path.isEmpty()) listOf(SaveFolderEntry("retroarch.cfg", false, bytes.size.toLong())) else null
            override fun read(path: String, limit: Int): ByteArray? = bytes.takeIf { path == "retroarch.cfg" && it.size <= limit }
        }
        val files = RetroArchSaveFiles(listOf(SaveFolderGrant(current, true, false), SaveFolderGrant(other, true, true)), app, pkg,
            { uri -> assertEquals(current, uri); access }, null)
        assertEquals(RetroArchConfigFile.Read(bytes.decodeToString()), files.config(config))
        assertEquals(RetroArchConfigFile.Missing, files.config("$parent/missing.cfg"))
        val denied = RetroArchSaveFiles(listOf(SaveFolderGrant(other, true, true)), app, pkg, { fail("unrelated grant"); access }, null)
        assertEquals(RetroArchConfigFile.Unreadable, denied.config(config))
    }

    @Test fun aBroadStorageGrantCannotEstablishAbsenceInsideAndroidData() {
        val uri = treeUri(SharedPaths.EXTERNAL_STORAGE_AUTHORITY, "primary:")
        val files = RetroArchSaveFiles(listOf(SaveFolderGrant(uri, true, true)), app, pkg, { fail("restricted ancestor"); emptyAccess }, null)
        assertEquals(RetroArchConfigFile.Unreadable, files.config(config))
    }

    @Test fun ownedRommCandidateDistinguishesAnAbsentSaveFromAnUnreadableOrUnownedOne() {
        val owned = Files.createTempDirectory("retroarch-owned").toRealPath()
        val game = Files.createDirectory(owned.resolve("game"))
        val other = Files.createTempDirectory("retroarch-other").toRealPath()
        try {
            val files = RetroArchSaveFiles(emptyList(), app, pkg, { fail("no grants"); emptyAccess }, owned)
            val save = game.resolve("Game.srm")
            assertEquals(FileReading.Missing, files.candidate(save.toString()).read)
            Files.write(save, byteArrayOf(42))
            assertArrayEquals(byteArrayOf(42), (files.candidate(save.toString()).read as FileReading.Bytes).bytes)
            Files.write(other.resolve("Game.srm"), byteArrayOf(43))
            assertEquals(FileReading.Unreadable, files.candidate(other.resolve("Game.srm").toString()).read)
            val link = owned.resolve("link")
            Files.createSymbolicLink(link, other)
            assertEquals(FileReading.Unreadable, files.candidate(link.resolve("Game.srm").toString()).read)
        } finally {
            owned.toFile().deleteRecursively()
            other.toFile().deleteRecursively()
        }
    }

    private val emptyAccess = object : SaveFolderAccess {
        override fun list(path: String): List<SaveFolderEntry>? = null
        override fun read(path: String, limit: Int): ByteArray? = null
        override fun read(path: String, offset: Long, length: Int): ByteArray? = null
        override fun write(path: String, bytes: ByteArray): Boolean = error("read only")
        override fun createFolder(path: String): Boolean = error("read only")
        override fun moveToBackup(path: String): Boolean = error("read only")
    }
}
