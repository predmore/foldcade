package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PlacedSave
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderSearch
import app.foldcade.api.plugin.StartDisplay
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A save found by folder name follows the same checks as one whose folders were named up front. */
class PlayerSaveSearchTest {
    private lateinit var backups: java.nio.file.Path
    private val docs = Tree()
    private val marks = MemoryMarks()
    private lateinit var saves: PlayerSaves

    @Before
    fun setUp() {
        backups = Files.createTempDirectory("search-backups")
        saves = PlayerSaves(docs, marks, backups, backups.resolveSibling("search-handover"))
        docs.names[ROM] = "Need for Speed Underground 2 (USA).iso"
        docs.dir("memcards/card")
        docs.file("memcards/card/BASLUS-210651/data", "played")
        docs.file("memcards/card/BESLES-99999/data", "someone else")
    }

    @After
    fun tearDown() {
        backups.toFile().deleteRecursively()
    }

    @Test
    fun aSearchReadsOnlyTheMatchingFolders() {
        val spot = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM), forLaunch = false).single()
        assertEquals(SaveFolderSearch("memcards/card", "SLUS-21065"), spot.search)
        val read = saves.changedSince("romm", GAME, PLAYER, listOf(spot)).slots.single()
        val bytes = Files.readAllBytes(java.nio.file.Path.of(java.net.URI.create(read.contentUri)))
        val entries = saveEntries(bytes, setOf("BASLUS-210651", "BESLES-99999"))!!
        assertEquals("played", entries.getValue("BASLUS-210651/data")!!.toString(Charsets.UTF_8))
        assertTrue(entries.keys.all { it.startsWith("BASLUS-210651") })
    }

    @Test
    fun placingUpdatesTheMatchAndLeavesTheOtherGame() {
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM), forLaunch = true)
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        docs.library[SERVER] = zipOf("BASLUS-210651/data" to "server")
        val changed = mutableListOf<String>()
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed()), changed))
        assertEquals(listOf("card1"), changed)
        assertEquals("server", docs.text("memcards/card/BASLUS-210651/data"))
        assertEquals("someone else", docs.text("memcards/card/BESLES-99999/data"))
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
    }

    @Test
    fun aZipThatNamesAnotherFolderIsRefused() {
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM), forLaunch = true)
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        docs.library[SERVER] = zipOf("BASLUS-210651/data" to "server", "OTHER/data" to "no")
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed())))
        assertEquals("played", docs.text("memcards/card/BASLUS-210651/data"))
    }

    @Test
    fun noMatchingFolderIsNotUploaded() {
        docs.files.entries.removeIf { it.key.startsWith("memcards/card/BASLUS") }
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM), forLaunch = false)
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
    }

    @Test
    fun theReturnPathDoesNotWriteAndALaunchThatSetsUpCountsAsAChange() {
        val changed = mutableListOf<String>()
        assertTrue(saves.spots(Setup(), GAME, LaunchTarget.ContentUri(ROM), forLaunch = false, changed = changed).isEmpty())
        assertTrue(changed.isEmpty())
        assertFalse(docs.files.containsKey("memcards/new/note"))
        val spots = saves.spots(Setup(), GAME, LaunchTarget.ContentUri(ROM), forLaunch = true, changed = changed)
        assertEquals(listOf("card1"), changed)
        assertEquals(1, docs.files.getValue("memcards/new/note").single().toInt())
        assertEquals("memcards/new", spots.single().search?.parent)
    }

    @Test
    fun movingACardToBackupsKeepsEveryByteBeforeDeletingIt() {
        val bytes = ByteArray(32) { it.toByte() }
        docs.file("memcards/old.ps2", bytes)
        val changed = mutableListOf<String>()
        saves.spots(Mover(), GAME, LaunchTarget.ContentUri(ROM), forLaunch = true, changed = changed)
        assertEquals(listOf("card1"), changed)
        assertFalse(docs.files.containsKey("memcards/old.ps2"))
        val kept = Files.list(backups.resolve("mover")).use { stream ->
            stream.map { Files.readAllBytes(it) }.toList()
        }
        assertEquals(1, kept.size)
        assertTrue(bytes.contentEquals(kept.single()))
    }

    @Test
    fun aNameMatchesWithDashesAndUnderscoresRemoved() {
        assertTrue(saveFolderNameMatches("BASLUS-210651", "SLUS-21065"))
        assertTrue(saveFolderNameMatches("ba_slus-21065", "SLUS-21065"))
        assertFalse(saveFolderNameMatches("BESLES-99999", "SLUS-21065"))
        assertFalse(saveFolderNameMatches("_pcsx2_deleted_BASLUS-210651", "SLUS-21065"))
    }

    @Test
    fun aDeletedSaveIsNotPartOfTheGame() {
        docs.dir("memcards/card/_pcsx2_deleted_BASLUS-210651")
        docs.file("memcards/card/_pcsx2_deleted_BASLUS-210651/data", "gone")
        val spot = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM), forLaunch = false).single()
        val read = saves.changedSince("romm", GAME, PLAYER, listOf(spot)).slots.single()
        val bytes = Files.readAllBytes(java.nio.file.Path.of(java.net.URI.create(read.contentUri)))
        val entries = saveEntries(bytes, setOf("BASLUS-210651"))
        assertEquals("played", entries!!.getValue("BASLUS-210651/data")!!.toString(Charsets.UTF_8))
        assertTrue(entries.keys.none { it.startsWith("_") })
    }

    @Test
    fun aZipThatNamesADeletedSaveIsRefused() {
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM), forLaunch = true)
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        docs.library[SERVER] = zipOf(
            "BASLUS-210651/data" to "server",
            "_pcsx2_deleted_BASLUS-210651/data" to "gone",
        )
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed())))
        assertEquals("played", docs.text("memcards/card/BASLUS-210651/data"))
    }

    @Test
    fun aWriteOverDifferentBytesKeepsTheOldOnes() {
        val old = "previous".toByteArray()
        docs.file("memcards/card/note", old)
        val player = object : Player by Idle {
            override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", TREE))
            override fun findSaveFolders(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean): SaveFolderSearch? {
                check(folder.write("memcards/card/note", "next".toByteArray()))
                check(folder.write("memcards/card/note", "next".toByteArray()))
                return SaveFolderSearch("memcards/card", "SLUS-21065")
            }
        }
        saves.spots(player, GAME, LaunchTarget.ContentUri(ROM), forLaunch = true)
        assertEquals("next", docs.text("memcards/card/note"))
        val kept = Files.list(backups.resolve("armsx2")).use { stream ->
            stream.map { Files.readAllBytes(it) }.toList()
        }
        assertEquals(1, kept.size)
        assertTrue(old.contentEquals(kept.single()))
    }

    @Test
    fun aThrowOnTheWayBackIsNoSpotAndAThrowOnLaunchIsNotPlaced() {
        val throwing = object : Player by Idle {
            override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", TREE))
            override fun findSaveFolders(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean): SaveFolderSearch? =
                error("card setup failed")
        }
        assertTrue(saves.spots(throwing, GAME, LaunchTarget.ContentUri(ROM), forLaunch = false).isEmpty())
        assertThrows(SaveNotPlaced::class.java) {
            saves.spots(throwing, GAME, LaunchTarget.ContentUri(ROM), forLaunch = true)
        }
    }

    private fun placed() = PlacedSave("card1", SERVER, md5Hex(docs.library.getValue(SERVER)))

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray =
        saveZip(entries.associate { (name, text) -> name to text.toByteArray() })

    /** Names the card's folders from the serial, which the test already knows. */
    private class Searcher : Player by Idle {
        override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", TREE))
        override fun findSaveFolders(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean) =
            SaveFolderSearch("memcards/card", "SLUS-21065")
    }

    /** Writes a marker. The shell must refuse that write on the way back. */
    private class Setup : Player by Idle {
        override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", TREE))
        override fun findSaveFolders(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean): SaveFolderSearch? {
            if (!folder.write("memcards/new/note", byteArrayOf(1))) return null
            return SaveFolderSearch("memcards/new", "SLUS-21065")
        }
    }

    /** Moves one file into Foldcade's backups. */
    private class Mover : Player by Idle {
        override val id = "mover"
        override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", TREE))
        override fun findSaveFolders(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean): SaveFolderSearch? {
            if (!folder.moveToBackup("memcards/old.ps2")) return null
            return SaveFolderSearch("memcards/new", "SLUS-21065")
        }
    }

    private class Tree : SaveDocuments {
        val names = mutableMapOf<String, String>()
        val library = mutableMapOf<String, ByteArray>()
        val files = sortedMapOf<String, ByteArray>()
        val folders = sortedSetOf<String>()

        fun dir(path: String) {
            var folder = path
            while (folder.isNotEmpty()) {
                folders += folder
                folder = folder.substringBeforeLast('/', "")
            }
        }

        fun file(path: String, text: String) = file(path, text.toByteArray())

        fun file(path: String, bytes: ByteArray) {
            dir(path.substringBeforeLast('/', ""))
            files[path] = bytes
        }

        fun text(path: String): String? = files[path]?.toString(Charsets.UTF_8)

        override fun displayName(uri: String) = names[uri]
        override fun find(folderUri: String, name: String) = "$TREE/$name".takeIf { name in files }
        override fun create(folderUri: String, name: String): String {
            dir(name.substringBeforeLast('/', ""))
            files[name] = ByteArray(0)
            return "$TREE/$name"
        }
        override fun read(uri: String) = library[uri] ?: files[uri.removePrefix("$TREE/")]
        override fun write(uri: String, bytes: ByteArray) {
            files[uri.removePrefix("$TREE/")] = bytes
        }
        override fun list(folderUri: String, name: String): List<SaveEntry>? {
            if (name !in folders) return null
            val below = "$name/"
            return folders.filter { it.startsWith(below) }.map { SaveEntry(it.removePrefix(below), "$TREE/$it", true) } +
                files.keys.filter { it.startsWith(below) }.map { SaveEntry(it.removePrefix(below), "$TREE/$it", false) }
        }

        override fun delete(uri: String): Boolean {
            val path = uri.removePrefix("$TREE/")
            val gone = files.remove(path) != null
            files.keys.removeIf { it.startsWith("$path/") }
            return gone || !files.containsKey(path)
        }
        override fun createFolder(folderUri: String, name: String): Boolean {
            dir(name)
            return true
        }
        override fun children(folderUri: String, path: String): List<SaveChild>? {
            if (path.isNotEmpty() && path !in folders) return null
            val prefix = if (path.isEmpty()) "" else "$path/"
            val found = linkedMapOf<String, SaveChild>()
            for (folder in folders) {
                val rest = folder.removePrefix(prefix)
                if (!folder.startsWith(prefix) || rest.isEmpty() || '/' in rest) continue
                found[rest] = SaveChild(rest, "$TREE/$folder", true, 0)
            }
            for ((file, bytes) in files) {
                val rest = file.removePrefix(prefix)
                if (!file.startsWith(prefix) || rest.isEmpty() || '/' in rest) continue
                found[rest] = SaveChild(rest, "$TREE/$file", false, bytes.size.toLong())
            }
            return found.values.toList()
        }
    }

    private class MemoryMarks : SaveMarks {
        private val stored = mutableMapOf<String, String>()
        override fun mark(key: String) = stored[key]
        override fun setMark(key: String, hash: String) {
            stored[key] = hash
        }
        override fun clearMark(key: String) {
            stored.remove(key)
        }
    }

    private companion object {
        const val TREE = "content://tree/arms"
        const val ROM = "content://roms/nfsu2.iso"
        const val SERVER = "lib://card1"
        val GAME = Game("romm", "37", "playstation-2", Availability.Cached, "Need for Speed Underground 2")
        val Idle = object : Player {
            override val id = "armsx2"
            override val displayName = "ARMSX2"
            override val platformId = "playstation-2"
            override val packageNames = emptyList<String>()
            override val needsLocalFile = true
            override val startDisplay = StartDisplay.PickerChoice
            override val occupiesBothDisplays = false
            override val requiresImportedGame = false
            override fun saveDeclarations(game: Game) = emptyList<SaveDeclaration>()
            override fun launchIntent(request: LaunchRequest): PlayerIntent = error("unused")
        }
        val PLAYER = Searcher()
    }
}
