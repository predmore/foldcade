package app.foldcade

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostedFoldersTest {
    private val root: File = Files.createTempDirectory("hosted").toFile()
    private val folders = HostedFolders(root) { listOf(HostedFolder("duckstation", "DuckStation memory cards")) }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun aDocumentIsItsFolderThenThePathBelowIt() {
        assertEquals(File(root, "duckstation"), folders.fileOf("foldcade:duckstation"))
        assertTrue(File(root, "duckstation").isDirectory)
        assertEquals(File(root, "duckstation/Game_1.mcd"), folders.fileOf("foldcade:duckstation/Game_1.mcd"))
        assertEquals(File(root, "duckstation/a/b.mcd"), folders.fileOf("foldcade:duckstation/a/b.mcd"))
        assertEquals("DuckStation memory cards", folders.title("duckstation"))
        // Ids are paths, as DuckStation needs: it finds a file's folder by the id and a slash the file's starts with.
        assertEquals("foldcade:duckstation", HostedFolders.rootId("duckstation"))
        assertEquals("foldcade:duckstation/Game_1.mcd", HostedFolders.childId("foldcade:duckstation", "Game_1.mcd"))
        assertEquals("foldcade:duckstation/a/b.mcd", HostedFolders.childId("foldcade:duckstation/a", "b.mcd"))
        assertEquals("foldcade:duckstation", HostedFolders.parentId("foldcade:duckstation/Game_1.mcd"))
        assertEquals("foldcade:duckstation/a", HostedFolders.parentId("foldcade:duckstation/a/b.mcd"))
        assertEquals("duckstation", HostedFolders.folderOf("foldcade:duckstation/a/b.mcd"))
        assertTrue(HostedFolders.isRoot("foldcade:duckstation"))
        assertTrue(!HostedFolders.isRoot("foldcade:duckstation/a"))
        assertTrue(HostedFolders.isInside("foldcade:duckstation", "foldcade:duckstation/a/b.mcd"))
        assertTrue(HostedFolders.isInside("foldcade:duckstation/a", "foldcade:duckstation/a/b.mcd"))
        assertTrue(!HostedFolders.isInside("foldcade:duckstation/a", "foldcade:duckstation/ab.mcd"))
        assertTrue(!HostedFolders.isInside("foldcade:duck", "foldcade:duckstation/a.mcd"))
    }

    @Test
    fun nothingLeavesItsFolder() {
        assertNull(folders.fileOf("foldcade:retroarch"))
        assertNull(folders.fileOf("duckstation"))
        assertNull(folders.fileOf("primary:duckstation"))
        assertNull(folders.fileOf("foldcade:duckstation/../other"))
        assertNull(folders.fileOf("foldcade:duckstation/a//Game.mcd"))
        assertNull(folders.fileOf("foldcade:duckstation/./Game.mcd"))
        assertNull(folders.fileOf("foldcade:duckstation/"))
        assertNull(folders.fileOf("foldcade:duckstation/a\\b"))
        // A name may hold a colon.
        assertEquals(File(root, "duckstation/Game: Two_1.mcd"), folders.fileOf("foldcade:duckstation/Game: Two_1.mcd"))
        for (name in listOf("", " ", ".", "..", "a/b", "a\\b", "a\u0000")) {
            assertTrue(name, runCatching { HostedSavesProvider.plainName(name) }.isFailure)
        }
        assertEquals("Game_1.mcd", HostedSavesProvider.plainName("Game_1.mcd"))
    }

    @Test
    fun aNameTakenGetsTheNextFreeOne() {
        val folder = folders.fileOf("foldcade:duckstation")!!
        assertEquals(File(folder, "Game_1.mcd"), HostedSavesProvider.uniqueFile(folder, "Game_1.mcd"))
        File(folder, "Game_1.mcd").createNewFile()
        assertEquals(File(folder, "Game_1 (1).mcd"), HostedSavesProvider.uniqueFile(folder, "Game_1.mcd"))
        File(folder, "Game_1 (1).mcd").createNewFile()
        assertEquals(File(folder, "Game_1 (2).mcd"), HostedSavesProvider.uniqueFile(folder, "Game_1.mcd"))
        File(folder, "memcards").mkdir()
        assertEquals(File(folder, "memcards (1)"), HostedSavesProvider.uniqueFile(folder, "memcards"))
    }

    @Test
    fun aFileRenamedOntoAnotherFilesNameReplacesIt() {
        val folder = folders.fileOf("foldcade:duckstation")!!
        val card = File(folder, "Game_1.mcd").apply { writeText("placed") }
        val written = File(folder, "Game_1.mcd.tmp").apply { writeText("played") }
        // As DuckStation's save lands just after a placement put the card back.
        val target = HostedSavesProvider.renameTarget(written, "Game_1.mcd")
        assertEquals(card, target)
        assertTrue(written.renameTo(target))
        assertEquals("played", card.readText())
        // A folder's name, or a file onto a folder, still gets a name of its own.
        File(folder, "memcards").mkdir()
        assertEquals(File(folder, "memcards (1)"), HostedSavesProvider.renameTarget(card, "memcards"))
        val sub = File(folder, "old").apply { mkdir() }
        assertEquals(File(folder, "Game_1 (1).mcd"), HostedSavesProvider.renameTarget(sub, "Game_1.mcd"))
    }
}
