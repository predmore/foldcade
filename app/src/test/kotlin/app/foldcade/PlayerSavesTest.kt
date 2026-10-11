package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PlacedSave
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.StartDisplay
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerSavesTest {
    private lateinit var backups: Path
    private val docs = FakeDocuments()
    private val marks = MemoryMarks()
    private lateinit var saves: PlayerSaves

    @Before
    fun setUp() {
        backups = Files.createTempDirectory("player-save-backups")
        saves = PlayerSaves(docs, marks, backups)
        docs.names[ROM_URI] = "Mario Kart DS (USA).nds"
    }

    @After
    fun tearDown() {
        backups.toFile().deleteRecursively()
    }

    @Test
    fun aSpotIsTheFileThePlayerNamesInItsFolder() {
        val spots = saves.spots(Melon(FOLDER), GAME, LaunchTarget.ContentUri(ROM_URI))
        assertEquals(listOf(SaveSpot("sav", FOLDER, "Mario Kart DS (USA).sav")), spots)
        assertTrue(saves.spots(Melon(null), GAME, LaunchTarget.ContentUri(ROM_URI)).isEmpty())
        assertTrue(saves.spots(Melon(FOLDER), GAME, LaunchTarget.AppRef(mapOf("id" to "1"))).isEmpty())
        assertTrue(saves.spots(Melon(FOLDER, named = false), GAME, LaunchTarget.ContentUri(ROM_URI)).isEmpty())
    }

    @Test
    fun aNewSaveIsWrittenCheckedAndMarked() {
        docs.library["lib://sav"] = "server".toByteArray()
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertEquals("server", docs.text(FOLDER, SAVE_NAME))
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
        assertFalse(Files.list(backups).use { it.findAny().isPresent })
    }

    @Test
    fun aReplacedSaveIsBackedUpFirst() {
        docs.library["lib://sav"] = "server".toByteArray()
        docs.put(FOLDER, SAVE_NAME, "player")
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        // A file the library has not seen is refused until it is handed over.
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertEquals("player", docs.text(FOLDER, SAVE_NAME))
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertEquals("server", docs.text(FOLDER, SAVE_NAME))
        val kept = Files.walk(backups).use { stream -> stream.filter { Files.isRegularFile(it) }.toList() }
        assertEquals(listOf("player"), kept.map { Files.readString(it) })
    }

    @Test
    fun theSameBytesAreNotWrittenAgain() {
        docs.library["lib://sav"] = "same".toByteArray()
        docs.put(FOLDER, SAVE_NAME, "same")
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed("same"))))
        assertEquals(0, docs.writes)
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
    }

    @Test
    fun aSaveChangedOutsideFoldcadeIsReportedBeforeAnythingIsPlaced() {
        docs.library["lib://sav"] = "server".toByteArray()
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        // The player ran on its own and saved.
        docs.put(FOLDER, SAVE_NAME, "played outside")
        val changed = saves.changedSince("romm", GAME, PLAYER, spots)
        assertEquals(listOf("sav"), changed.slots.map { it.slot })
        assertEquals(md5Hex("played outside".toByteArray()), changed.slots.single().contentHash)
        assertEquals("$FOLDER/$SAVE_NAME", changed.slots.single().contentUri)
        assertEquals("melonds", changed.slots.single().playerId)
        saves.noteHandedOver("romm", GAME, PLAYER, spots, changed)
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
        // A file the shell has never seen counts as changed too.
        assertEquals(1, saves.changedSince("local-folder", GAME, PLAYER, spots).slots.size)
    }

    @Test
    fun aWriteThatDoesNotReadBackStopsTheLaunchAndPutsTheOldBytesBack() {
        docs.library["lib://sav"] = "server".toByteArray()
        docs.put(FOLDER, SAVE_NAME, "player")
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        docs.tearNextWrite = true
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertEquals("player", docs.text(FOLDER, SAVE_NAME))
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
    }

    @Test
    fun tornBytesFromAFailedPlacementAreNotHandedOverAsProgress() {
        docs.library["lib://sav"] = "server".toByteArray()
        docs.put(FOLDER, SAVE_NAME, "player")
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        docs.tearWrites = true
        // The write and the undo both tear.
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
        docs.tearWrites = false
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertEquals("server", docs.text(FOLDER, SAVE_NAME))
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
    }

    @Test
    fun playAfterAFailedPlacementIsStillHandedOver() {
        docs.library["lib://sav"] = "server".toByteArray()
        docs.put(FOLDER, SAVE_NAME, "player")
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        docs.tearWrites = true
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        docs.tearWrites = false
        // The user then played in the player directly, for hours.
        docs.put(FOLDER, SAVE_NAME, "hours of play")
        assertEquals(listOf(md5Hex("hours of play".toByteArray())), saves.changedSince("romm", GAME, PLAYER, spots).slots.map { it.contentHash })
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertEquals("hours of play", docs.text(FOLDER, SAVE_NAME))
    }

    @Test
    fun aNewFileFromAFailedPlacementIsRemoved() {
        docs.library["lib://sav"] = "server".toByteArray()
        docs.tearNextWrite = true
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertEquals(null, docs.text(FOLDER, SAVE_NAME))
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertEquals("server", docs.text(FOLDER, SAVE_NAME))
    }

    @Test
    fun aFileChangedAfterItWasHandedOverIsNotOverwritten() {
        docs.library["lib://sav"] = "server".toByteArray()
        docs.put(FOLDER, SAVE_NAME, "player")
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        docs.put(FOLDER, SAVE_NAME, "still playing")
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertEquals("still playing", docs.text(FOLDER, SAVE_NAME))
    }

    @Test
    fun aFileThatCannotBeReadIsNotOverwritten() {
        docs.library["lib://sav"] = "server".toByteArray()
        docs.put(FOLDER, SAVE_NAME, "player")
        docs.unreadable += "$FOLDER/$SAVE_NAME"
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertEquals(0, docs.writes)
    }

    @Test
    fun libraryBytesThatDoNotMatchTheirHashAreNotPlaced() {
        docs.library["lib://sav"] = "torn".toByteArray()
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertEquals(0, docs.writes)
    }

    private fun placed(text: String) = PlacedSave("sav", "lib://sav", md5Hex(text.toByteArray()))

    private class FakeDocuments : SaveDocuments {
        val names = mutableMapOf<String, String>()
        val folders = mutableMapOf<String, MutableMap<String, ByteArray>>()
        val library = mutableMapOf<String, ByteArray>()
        var writes = 0
        var tearWrites = false
        var tearNextWrite = false
        val unreadable = mutableSetOf<String>()

        fun put(folder: String, name: String, text: String) {
            folders.getOrPut(folder) { mutableMapOf() }[name] = text.toByteArray()
        }

        fun text(folder: String, name: String): String? = folders[folder]?.get(name)?.toString(Charsets.UTF_8)

        override fun displayName(uri: String): String? = names[uri]

        override fun find(folderUri: String, name: String): String? =
            if (folders[folderUri]?.containsKey(name) == true) "$folderUri/$name" else null

        override fun create(folderUri: String, name: String): String {
            folders.getOrPut(folderUri) { mutableMapOf() }[name] = ByteArray(0)
            return "$folderUri/$name"
        }

        override fun read(uri: String): ByteArray? {
            library[uri]?.let { return it }
            if (uri in unreadable) return null
            val folder = uri.substringBeforeLast('/')
            return folders[folder]?.get(uri.substringAfterLast('/'))
        }

        var deletes = true

        override fun delete(uri: String): Boolean {
            if (!deletes) return false
            folders[uri.substringBeforeLast('/')]?.remove(uri.substringAfterLast('/'))
            return true
        }

        override fun write(uri: String, bytes: ByteArray) {
            writes += 1
            val folder = uri.substringBeforeLast('/')
            val torn = tearWrites || tearNextWrite
            tearNextWrite = false
            val stored = if (torn) bytes.copyOf(bytes.size / 2) else bytes
            folders.getOrPut(folder) { mutableMapOf() }[uri.substringAfterLast('/')] = stored
        }
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

    private class Melon(private val folder: String?, private val named: Boolean = true) : Player {
        override val id = "melonds"
        override val displayName = "melonDS"
        override val platformId = "nintendo-ds"
        override val packageNames = emptyList<String>()
        override val needsLocalFile = true
        override val startDisplay = StartDisplay.Primary
        override val occupiesBothDisplays = true
        override val requiresImportedGame = false
        override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("sav", folder))
        override fun saveFileName(slot: String, romFileName: String): String? =
            if (named) romFileName.replaceAfterLast('.', "sav") else null
        override fun launchIntent(request: LaunchRequest): PlayerIntent = error("unused")
    }

    private companion object {
        const val FOLDER = "content://tree/saves"
        const val ROM_URI = "content://app.foldcade.romm.cache/romm/roms/1/Mario%20Kart%20DS%20(USA).nds"
        const val SAVE_NAME = "Mario Kart DS (USA).sav"
        val GAME = Game("romm", "1", "nintendo-ds", Availability.Cached, "Mario Kart DS")
        val PLAYER: Player = Melon(FOLDER)
    }
}
