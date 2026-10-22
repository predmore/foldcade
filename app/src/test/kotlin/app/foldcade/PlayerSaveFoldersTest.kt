package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.PlacedSave
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveFolderPart
import app.foldcade.api.plugin.StartDisplay
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Saves a player keeps as folders, such as Azahar's, moved as one zip. */
class PlayerSaveFoldersTest {
    private lateinit var backups: Path
    private lateinit var handover: Path
    private val docs = TreeDocuments()
    private val marks = MemoryMarks()
    private lateinit var saves: PlayerSaves

    @Before
    fun setUp() {
        backups = Files.createTempDirectory("folder-save-backups")
        handover = Files.createTempDirectory("folder-save-handover")
        saves = PlayerSaves(docs, marks, backups, handover)
        docs.names[ROM_URI] = "Game (USA).cci"
        docs.heads[ROM_URI] = HEADER
    }

    @After
    fun tearDown() {
        backups.toFile().deleteRecursively()
        handover.toFile().deleteRecursively()
    }

    @Test
    fun aSpotIsTheFoldersThePlayerNamesFromTheHeader() {
        val player = Folders()
        val spots = saves.spots(player, GAME, LaunchTarget.ContentUri(ROM_URI))
        assertEquals(listOf(SaveSpot("title", TREE, "Game (USA).zip", PARTS)), spots)
        assertArrayEquals(HEADER.copyOf(4), player.headerSeen)
        assertTrue(saves.spots(Folders(parts = listOf(SaveFolderPart("a/b", "x"))), GAME, LaunchTarget.ContentUri(ROM_URI)).isEmpty())
        assertTrue(saves.spots(Folders(parts = listOf(SaveFolderPart("a", "../x"))), GAME, LaunchTarget.ContentUri(ROM_URI)).isEmpty())
        assertTrue(saves.spots(Folders(parts = listOf(SaveFolderPart("a", "x"), SaveFolderPart("a", "y"))), GAME, LaunchTarget.ContentUri(ROM_URI)).isEmpty())
        assertTrue(saves.spots(Folders(parts = emptyList()), GAME, LaunchTarget.ContentUri(ROM_URI)).isEmpty())
    }

    @Test
    fun localFoldersAreHandedOverAsOneZipOnce() {
        docs.put("$DATA/00000001/00.SAV", "progress")
        docs.put("$DATA/00000001.metadata", "meta")
        docs.mkdirs("$EXTDATA/user")
        val spots = spots()
        val observed = saves.changedSince("romm", GAME, PLAYER, spots).slots.single()
        val staged = Files.readAllBytes(Paths.get(URI(observed.contentUri!!)))
        assertEquals(md5Hex(staged), observed.contentHash)
        assertEquals(
            listOf("data/00000001.metadata", "data/00000001/", "data/00000001/00.SAV", "extdata/user/"),
            saveEntries(staged, setOf("data", "extdata"))!!.keys.toList(),
        )
        saves.noteHandedOver("romm", GAME, PLAYER, spots, ObservedSaves(listOf(observed)))
        assertFalse(Files.list(handover).use { it.findAny().isPresent })
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
        docs.put("$DATA/00000001/00.SAV", "more progress")
        assertEquals(1, saves.changedSince("romm", GAME, PLAYER, spots).slots.size)
    }

    @Test
    fun anArgosyZipIsUnpackedIntoAnEmptyFolder() {
        // As Argosy uploads them: a folder entry for the part, other folders listed, its own times.
        docs.library[SERVER] = zipOf(
            "data/" to null,
            "data/00000001/" to null,
            "data/00000001/00.SAV" to "server",
            "data/00000001.metadata" to "meta",
            "extdata/" to null,
            "extdata/user/" to null,
            "extdata/icon" to "icon",
        )
        val spots = spots()
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed())))
        assertEquals("server", docs.text("$DATA/00000001/00.SAV"))
        assertEquals("meta", docs.text("$DATA/00000001.metadata"))
        assertEquals("icon", docs.text("$EXTDATA/icon"))
        assertTrue("$EXTDATA/user" in docs.folders)
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
        val writes = docs.writes
        val changed = mutableListOf<String>()
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed()), changed))
        assertEquals(writes, docs.writes)
        assertTrue(changed.isEmpty())
    }

    @Test
    fun aKnownSaveIsBackedUpAndReplacedButAFolderTheZipLacksStays() {
        docs.put("$DATA/00000001/00.SAV", "old")
        docs.put("$DATA/00000001/03.SAV", "stale")
        docs.put("$EXTDATA/user/progress", "kept")
        val spots = spots()
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        docs.library[SERVER] = zipOf("data/00000001/00.SAV" to "new")
        val changed = mutableListOf<String>()
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed()), changed))
        assertEquals(listOf("title"), changed)
        assertEquals("new", docs.text("$DATA/00000001/00.SAV"))
        assertNull(docs.text("$DATA/00000001/03.SAV"))
        assertEquals("kept", docs.text("$EXTDATA/user/progress"))
        val kept = Files.walk(backups).use { stream -> stream.filter { Files.isRegularFile(it) }.toList() }.single()
        assertTrue(kept.fileName.toString().endsWith("Game (USA).zip"))
        val backedUp = saveEntries(Files.readAllBytes(kept), setOf("data", "extdata"))!!
        assertEquals("old", backedUp["data/00000001/00.SAV"]!!.toString(Charsets.UTF_8))
        assertEquals("stale", backedUp["data/00000001/03.SAV"]!!.toString(Charsets.UTF_8))
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
    }

    @Test
    fun anUnseenLocalSaveIsNeverReplaced() {
        docs.put("$DATA/00000001/00.SAV", "player")
        docs.library[SERVER] = zipOf("data/00000001/00.SAV" to "server")
        assertFalse(saves.place("romm", GAME, PLAYER, spots(), listOf(placed())))
        assertEquals("player", docs.text("$DATA/00000001/00.SAV"))
        assertEquals(0, docs.writes)
    }

    @Test
    fun aFolderThatCannotBeListedIsNeitherHandedOverNorWrittenOver() {
        docs.put("$DATA/00000001/00.SAV", "player")
        docs.unlistable = true
        val spots = spots()
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
        docs.library[SERVER] = zipOf("data/00000001/00.SAV" to "server")
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed())))
        assertEquals(0, docs.writes)
    }

    @Test
    fun aZipThatReachesOutsideItsFoldersIsRefused() {
        val spots = spots()
        for (zip in listOf(
            zipOf("data/../../escape" to "x"),
            zipOf("other/file" to "x"),
            zipOf("data" to "x"),
            zipOf("data/" to null, "extdata/user/" to null),
            "not a zip".toByteArray(),
        )) {
            docs.library[SERVER] = zip
            assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed())))
        }
        assertTrue(docs.files.isEmpty())
    }

    @Test
    fun aFailedWritePutsTheOldFilesBack() {
        docs.put("$DATA/00000001/00.SAV", "old")
        docs.put("$DATA/00000001/01.SAV", "old one")
        val spots = spots()
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        docs.library[SERVER] = zipOf("data/00000001/00.SAV" to "new", "data/00000001/02.SAV" to "new two")
        docs.failWritesTo = "$DATA/00000001/02.SAV"
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed())))
        assertEquals("old", docs.text("$DATA/00000001/00.SAV"))
        assertEquals("old one", docs.text("$DATA/00000001/01.SAV"))
        assertNull(docs.text("$DATA/00000001/02.SAV"))
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
    }

    @Test
    fun twoZipsOfTheSameFilesHaveTheSameEntriesAndBytes() {
        val listed = saveEntries(zipOf("data/" to null, "data/a/" to null, "data/a/b" to "x"), setOf("data"))!!
        val bare = saveEntries(zipOf("data/a/b" to "x"), setOf("data"))!!
        assertEquals(listed.keys, bare.keys)
        assertArrayEquals(saveZip(listed), saveZip(bare))
        assertNull(saveEntries(zipOf("data/a" to "x", "data/a/b" to "y"), setOf("data")))
    }

    private fun spots(): List<SaveSpot> = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))

    private fun placed(): PlacedSave = PlacedSave("title", SERVER, md5Hex(docs.library.getValue(SERVER)))

    /** A zip as other tools write it: entries as given, with a time of their own. */
    private fun zipOf(vararg entries: Pair<String, String?>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name).apply { time = 1_760_000_000_000 })
                if (text != null) zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private class Folders(private val parts: List<SaveFolderPart> = PARTS) : Player {
        var headerSeen: ByteArray? = null
        override val id = "azahar"
        override val displayName = "Azahar"
        override val platformId = "nintendo-3ds"
        override val packageNames = emptyList<String>()
        override val needsLocalFile = true
        override val startDisplay = StartDisplay.Primary
        override val occupiesBothDisplays = true
        override val requiresImportedGame = false
        override val romHeaderSize = 4
        override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("title", TREE))
        override fun saveFolders(slot: String, romFileName: String, romHeader: ByteArray): List<SaveFolderPart> {
            headerSeen = romHeader
            return parts
        }
        override fun launchIntent(request: LaunchRequest): PlayerIntent = error("unused")
    }

    private class MemoryMarks : SaveMarks {
        private val stored = mutableMapOf<String, String>()

        override fun mark(key: String): String? = stored[key]

        override fun setMark(key: String, hash: String) {
            stored[key] = hash
        }

        override fun clearMark(key: String) {
            stored.remove(key)
        }
    }

    /** A document tree: files and folders by path below [TREE]. */
    private class TreeDocuments : SaveDocuments {
        val names = mutableMapOf<String, String>()
        val heads = mutableMapOf<String, ByteArray>()
        val library = mutableMapOf<String, ByteArray>()
        val files = sortedMapOf<String, ByteArray>()
        val folders = sortedSetOf<String>()
        var writes = 0
        var unlistable = false
        var failWritesTo: String? = null

        fun mkdirs(path: String) {
            var folder = path
            while (folder.isNotEmpty()) {
                folders += folder
                folder = folder.substringBeforeLast('/', "")
            }
        }

        fun put(path: String, text: String) {
            mkdirs(path.substringBeforeLast('/'))
            files[path] = text.toByteArray()
        }

        fun text(path: String): String? = files[path]?.toString(Charsets.UTF_8)

        private fun uri(path: String) = "$TREE/$path"

        private fun path(uri: String) = uri.removePrefix("$TREE/")

        override fun displayName(uri: String): String? = names[uri]

        override fun head(uri: String, size: Int): ByteArray? = heads[uri]?.let { it.copyOf(minOf(size, it.size)) }

        override fun find(folderUri: String, name: String): String? = uri(name).takeIf { name in files }

        override fun create(folderUri: String, name: String): String {
            mkdirs(name.substringBeforeLast('/', ""))
            files[name] = ByteArray(0)
            return uri(name)
        }

        override fun read(uri: String): ByteArray? = library[uri] ?: files[path(uri)]

        override fun write(uri: String, bytes: ByteArray) {
            val path = path(uri)
            if (path == failWritesTo) {
                files[path] = bytes.copyOf(1)
                throw IOException("disk full")
            }
            writes++
            files[path] = bytes
        }

        override fun delete(uri: String): Boolean {
            val path = path(uri)
            files.remove(path)
            files.keys.removeIf { it.startsWith("$path/") }
            folders.removeIf { it == path || it.startsWith("$path/") }
            return true
        }

        override fun list(folderUri: String, name: String): List<SaveEntry>? {
            if (unlistable) throw IOException("provider did not answer")
            if (name !in folders) return null
            val below = "$name/"
            return folders.filter { it.startsWith(below) }.map { SaveEntry(it.removePrefix(below), uri(it), isFolder = true) } +
                files.keys.filter { it.startsWith(below) }.map { SaveEntry(it.removePrefix(below), uri(it), isFolder = false) }
        }

        override fun createFolder(folderUri: String, name: String): Boolean {
            mkdirs(name)
            return true
        }
    }

    private companion object {
        const val TREE = "content://tree/azahar"
        const val ROM_URI = "content://roms/game.cci"
        const val SERVER = "lib://title"
        const val DATA = "sdmc/title/00040000/00030100/data"
        const val EXTDATA = "sdmc/extdata/00000000/00000301"
        val PARTS = listOf(SaveFolderPart("data", DATA), SaveFolderPart("extdata", EXTDATA))
        val HEADER = byteArrayOf(1, 2, 3, 4, 5, 6)
        val PLAYER = Folders()
        val GAME = Game(
            backendId = "romm",
            remoteKey = "29",
            platformId = "nintendo-3ds",
            availability = Availability.Cached,
            label = "Game (USA)",
        )
    }
}
