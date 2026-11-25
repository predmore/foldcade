package app.foldcade

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
        val file = folderDocumentUri(current, config)
        val docs = object : ProbeDocuments() {
            override fun children(folderUri: String, path: String): List<SaveChild> {
                assertEquals(current, folderUri)
                assertEquals("", path)
                return listOf(SaveChild("retroarch.cfg", file, false, bytes.size.toLong()))
            }
            override fun read(uri: String): ByteArray? = bytes.takeIf { uri == file }
        }
        val files = RetroArchSaveFiles(listOf(SaveFolderGrant(current, true, false), SaveFolderGrant(other, true, true)), app, pkg,
            docs, null)
        assertTrue(files.hasConfigGrant())
        assertEquals(RetroArchConfigFile.Read(bytes.decodeToString()), files.config(config))
        assertEquals(RetroArchConfigFile.Missing, files.config("$parent/missing.cfg"))
        val denied = RetroArchSaveFiles(listOf(SaveFolderGrant(other, true, true)), app, pkg, ProbeDocuments(), null)
        assertFalse(denied.hasConfigGrant())
        assertEquals(RetroArchConfigFile.Unreadable, denied.config(config))
    }

    @Test fun aBroadStorageGrantCannotEstablishAbsenceInsideAndroidData() {
        val uri = treeUri(SharedPaths.EXTERNAL_STORAGE_AUTHORITY, "primary:")
        val files = RetroArchSaveFiles(listOf(SaveFolderGrant(uri, true, true)), app, pkg, ProbeDocuments(), null)
        assertFalse(files.hasConfigGrant())
        assertEquals(RetroArchConfigFile.Unreadable, files.config(config))
    }

    @Test fun aGrantToAnotherPackagesAppDataCannotProveRetroArchUsesIt() {
        val dir = "$SHARED_STORAGE/Android/data/com.retroarch/files"
        val uri = treeUri(SharedPaths.EXTERNAL_STORAGE_AUTHORITY, "primary:Android/data/com.retroarch/files")
        val files = RetroArchSaveFiles(listOf(SaveFolderGrant(uri, true, true)), app, pkg, ProbeDocuments(), null)
        assertFalse(files.isDirectory(dir))
        assertEquals(RetroArchConfigFile.Unreadable, files.config("$dir/mGBA.cfg"))
    }

    @Test fun besideRomCandidateWalksEachGrantedFolderOnceAndReadsTheListedDocument() {
        val grant = treeUri(SharedPaths.EXTERNAL_STORAGE_AUTHORITY, "primary:ROMs")
        val dir = folderDocumentUri(grant, "primary:ROMs/GBA")
        val save = folderDocumentUri(grant, "primary:ROMs/GBA/Title.srm")
        val listed = mutableListOf<String>()
        val docs = object : ProbeDocuments() {
            override fun children(folderUri: String, path: String): List<SaveChild> {
                assertEquals("", path)
                listed += folderUri
                return when (folderUri) {
                    grant -> listOf(SaveChild("GBA", dir, true, -1))
                    dir -> listOf(SaveChild("Title.srm", save, false, 1))
                    else -> error("unrelated folder")
                }
            }
            override fun read(uri: String): ByteArray {
                assertEquals(save, uri)
                return byteArrayOf(42)
            }
        }
        val files = RetroArchSaveFiles(listOf(SaveFolderGrant(grant, true, false)), app, pkg, docs, null)
        assertArrayEquals(byteArrayOf(42), (files.candidate("$SHARED_STORAGE/ROMs/GBA/Title.srm").read as FileReading.Bytes).bytes)
        assertEquals(listOf(grant, dir), listed)
    }

    @Test fun ownedRommCandidateDistinguishesAnAbsentSaveFromAnUnreadableOrUnownedOne() {
        val owned = Files.createTempDirectory("retroarch-owned").toRealPath()
        val game = Files.createDirectory(owned.resolve("game"))
        val other = Files.createTempDirectory("retroarch-other").toRealPath()
        try {
            val files = RetroArchSaveFiles(emptyList(), app, pkg, ProbeDocuments(), owned)
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

    private open class ProbeDocuments : SaveDocuments {
        override fun displayName(uri: String): String? = null
        override fun find(folderUri: String, name: String): String? = error("use the listed URI")
        override fun create(folderUri: String, name: String): String? = error("read only")
        override fun read(uri: String): ByteArray? = error("unexpected document")
        override fun write(uri: String, bytes: ByteArray) = error("read only")
        override fun delete(uri: String): Boolean = error("read only")
        override fun children(folderUri: String, path: String): List<SaveChild>? = error("unexpected folder")
    }
}
