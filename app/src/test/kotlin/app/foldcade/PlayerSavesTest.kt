package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.FirmwareBytes
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
        saves = PlayerSaves(docs, marks, backups, backups.resolveSibling(backups.fileName.toString() + "-handover"))
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
    fun aPlayerChangingAHostedFileWaitsWhileAPlacementChecksAndWritesIt() {
        docs.library["lib://sav"] = "server".toByteArray()
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        val playerWrote = CountDownLatch(1)
        var wroteDuringPlacement = true
        docs.onWrite = {
            docs.onWrite = null
            // What HostedSavesProvider does for a player's create, rename, delete, or write.
            Thread { synchronized(SaveFileLock) { playerWrote.countDown() } }.start()
            wroteDuringPlacement = playerWrote.await(200, TimeUnit.MILLISECONDS)
        }
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server"))))
        assertFalse(wroteDuringPlacement)
        // It goes ahead once the placement is done.
        assertTrue(playerWrote.await(5, TimeUnit.SECONDS))
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

    @Test
    fun onlyNewBytesCountAsAChangedSave() {
        docs.library["lib://sav"] = "server".toByteArray()
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM_URI))
        val first = mutableListOf<String>()
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server")), first))
        assertEquals(listOf("sav"), first)
        val again = mutableListOf<String>()
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed("server")), again))
        assertTrue(again.isEmpty())
    }

    @Test
    fun retroArchSavesUnderItsCoreFolderForAGameOpenedByPath() {
        val entry = app.foldcade.plugins.retroarch.RetroArchEntry()
        entry.cores.update(mapOf("com.retroarch.aarch64" to setOf("melondsds_libretro_android.so")))
        val player = entry.players.single { it.platformId == "nintendo-ds" }
        player as app.foldcade.api.plugin.SaveFolderHolder
        player.bindSaveFolder(FOLDER)
        val target = LaunchTarget.SharedFile("/storage/emulated/0/Android/media/app.foldcade/romm/1.nds", ROM_URI)
        assertEquals(
            listOf(SaveSpot("srm", FOLDER, "melonDS DS/Mario Kart DS (USA).srm")),
            saves.spots(player, GAME, target),
        )
    }

    @Test
    fun firmwareThePlayerAsksForIsWrittenBesideItsSavesOnce() {
        docs.library["lib://bios"] = "bios".toByteArray()
        docs.library["lib://other"] = "other".toByteArray()
        val target = LaunchTarget.ContentUri(
            ROM_URI,
            firmware = listOf(FirmwareBytes("SCPH1001.BIN", "lib://bios"), FirmwareBytes("other.bin", "lib://other")),
        )
        val player = Melon(FOLDER, firmware = setOf("SCPH1001.BIN"))
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target))
        assertEquals("bios", docs.text(FOLDER, "SCPH1001.BIN"))
        assertEquals(null, docs.text(FOLDER, "other.bin"))
        // A file there already is never replaced: it may be the user's own.
        docs.put(FOLDER, "SCPH1001.BIN", "the user's own")
        assertTrue(saves.placeFirmware(player, GAME, target).isEmpty())
        assertEquals("the user's own", docs.text(FOLDER, "SCPH1001.BIN"))
        // With no folder chosen there is nowhere to put it.
        assertTrue(saves.placeFirmware(Melon(null, firmware = setOf("SCPH1001.BIN")), GAME, target).isEmpty())
    }

    @Test
    fun firmwareTheShellWroteFollowsTheLibrarysNewCopy() {
        docs.library["lib://bios"] = "bios v1".toByteArray()
        val player = Melon(FOLDER, firmware = setOf("SCPH1001.BIN"))
        fun target() = LaunchTarget.ContentUri(ROM_URI, firmware = listOf(FirmwareBytes("SCPH1001.BIN", "lib://bios")))
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target()))
        // The same bytes are not written again.
        val writes = docs.writes
        assertTrue(saves.placeFirmware(player, GAME, target()).isEmpty())
        assertEquals(writes, docs.writes)
        // RomM's copy changed, and the file still holds what the shell wrote: it follows.
        docs.library["lib://bios"] = "bios v2".toByteArray()
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target()))
        assertEquals("bios v2", docs.text(FOLDER, "SCPH1001.BIN"))
        // The user put their own there since: it stays, whatever RomM has.
        docs.put(FOLDER, "SCPH1001.BIN", "the user's own")
        docs.library["lib://bios"] = "bios v3".toByteArray()
        assertTrue(saves.placeFirmware(player, GAME, target()).isEmpty())
        assertEquals("the user's own", docs.text(FOLDER, "SCPH1001.BIN"))
    }

    @Test
    fun firmwareAnUpdateLeftBrokenIsReplacedNextTime() {
        docs.library["lib://bios"] = "bios v1".toByteArray()
        val player = Melon(FOLDER, firmware = setOf("SCPH1001.BIN"))
        fun target() = LaunchTarget.ContentUri(ROM_URI, firmware = listOf(FirmwareBytes("SCPH1001.BIN", "lib://bios")))
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target()))
        // The update and the putting back both tear.
        docs.library["lib://bios"] = "bios v2".toByteArray()
        docs.tearWrites = true
        assertTrue(saves.placeFirmware(player, GAME, target()).isEmpty())
        docs.tearWrites = false
        // What was left is the shell's, not the user's, so the next launch replaces it.
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target()))
        assertEquals("bios v2", docs.text(FOLDER, "SCPH1001.BIN"))
    }

    @Test
    fun firmwareAnUpdateLeftUnreadableIsReplacedOnceItReadsAgain() {
        docs.library["lib://bios"] = "bios v1".toByteArray()
        val player = Melon(FOLDER, firmware = setOf("SCPH1001.BIN"))
        fun target() = LaunchTarget.ContentUri(ROM_URI, firmware = listOf(FirmwareBytes("SCPH1001.BIN", "lib://bios")))
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target()))
        val bios = docs.find(FOLDER, "SCPH1001.BIN")!!
        // The update tears, and from then on the file cannot be read, so what is left is never known.
        docs.library["lib://bios"] = "bios v2".toByteArray()
        docs.tearWrites = true
        docs.onWrite = { docs.unreadable += bios }
        assertTrue(saves.placeFirmware(player, GAME, target()).isEmpty())
        docs.onWrite = null
        docs.tearWrites = false
        docs.unreadable -= bios
        // Whatever is there is the shell's, so the next launch replaces it.
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target()))
        assertEquals("bios v2", docs.text(FOLDER, "SCPH1001.BIN"))
    }

    @Test
    fun firmwareCutShortByTheAppDyingIsReplacedNextTime() {
        docs.library["lib://bios"] = "bios v1".toByteArray()
        val player = Melon(FOLDER, firmware = setOf("SCPH1001.BIN"))
        fun target() = LaunchTarget.ContentUri(ROM_URI, firmware = listOf(FirmwareBytes("SCPH1001.BIN", "lib://bios")))
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target()))
        // The update tears and the app goes before it can check or mark anything.
        docs.library["lib://bios"] = "bios v2".toByteArray()
        docs.onWrite = {
            docs.onWrite = null
            docs.put(FOLDER, "SCPH1001.BIN", "bios")
            throw AppDied()
        }
        assertTrue(runCatching { saves.placeFirmware(player, GAME, target()) }.exceptionOrNull() is AppDied)
        assertEquals("bios", docs.text(FOLDER, "SCPH1001.BIN"))
        // Torn bytes it never marked: still the shell's, so the next launch replaces them.
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target()))
        assertEquals("bios v2", docs.text(FOLDER, "SCPH1001.BIN"))
    }

    @Test
    fun firmwareWhoseFileTheAppDiedMakingIsWrittenNextTime() {
        docs.library["lib://bios"] = "bios".toByteArray()
        val player = Melon(FOLDER, firmware = setOf("SCPH1001.BIN"))
        val target = LaunchTarget.ContentUri(ROM_URI, firmware = listOf(FirmwareBytes("SCPH1001.BIN", "lib://bios")))
        docs.onCreate = {
            docs.onCreate = null
            throw AppDied()
        }
        assertTrue(runCatching { saves.placeFirmware(player, GAME, target) }.exceptionOrNull() is AppDied)
        assertEquals("", docs.text(FOLDER, "SCPH1001.BIN"))
        // The empty file is the shell's, not the user's.
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target))
        assertEquals("bios", docs.text(FOLDER, "SCPH1001.BIN"))
    }

    @Test
    fun firmwareTheAppWroteWholeButDiedBeforeMarkingIsStillTheShells() {
        docs.library["lib://bios"] = "bios v1".toByteArray()
        val player = Melon(FOLDER, firmware = setOf("SCPH1001.BIN"))
        fun target() = LaunchTarget.ContentUri(ROM_URI, firmware = listOf(FirmwareBytes("SCPH1001.BIN", "lib://bios")))
        // The write lands whole, and the app goes before it marks it.
        docs.onWrite = {
            docs.onWrite = null
            docs.put(FOLDER, "SCPH1001.BIN", "bios v1")
            throw AppDied()
        }
        assertTrue(runCatching { saves.placeFirmware(player, GAME, target()) }.exceptionOrNull() is AppDied)
        // The next launch finds it there and settles it as the shell's v1.
        assertTrue(saves.placeFirmware(player, GAME, target()).isEmpty())
        // So a BIOS the user puts there later stays theirs.
        docs.put(FOLDER, "SCPH1001.BIN", "the user's own")
        docs.library["lib://bios"] = "bios v2".toByteArray()
        assertTrue(saves.placeFirmware(player, GAME, target()).isEmpty())
        assertEquals("the user's own", docs.text(FOLDER, "SCPH1001.BIN"))
    }

    @Test
    fun firmwareTheShellWroteStaysItsWhileTheFolderCannotBeReached() {
        docs.library["lib://bios"] = "bios v1".toByteArray()
        val player = Melon(FOLDER, firmware = setOf("SCPH1001.BIN"))
        fun target() = LaunchTarget.ContentUri(ROM_URI, firmware = listOf(FirmwareBytes("SCPH1001.BIN", "lib://bios")))
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target()))
        // RomM has a new copy while the folder's grant is gone: nothing is found or made.
        docs.library["lib://bios"] = "bios v2".toByteArray()
        docs.unreachable += FOLDER
        assertTrue(saves.placeFirmware(player, GAME, target()).isEmpty())
        // With the folder back, v1 is still the shell's, so v2 replaces it.
        docs.unreachable -= FOLDER
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target()))
        assertEquals("bios v2", docs.text(FOLDER, "SCPH1001.BIN"))
    }

    @Test
    fun firmwareTornAsItsFolderGoesIsStillTheShellsWhenItComesBack() {
        docs.library["lib://bios"] = "bios v1".toByteArray()
        val player = Melon(FOLDER, firmware = setOf("SCPH1001.BIN"))
        fun target() = LaunchTarget.ContentUri(ROM_URI, firmware = listOf(FirmwareBytes("SCPH1001.BIN", "lib://bios")))
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target()))
        // The update tears, and the folder goes before the old copy can be put back or the file found.
        docs.library["lib://bios"] = "bios v2".toByteArray()
        docs.tearWrites = true
        docs.onWrite = { docs.onWrite = { docs.unreachable += FOLDER } }
        assertTrue(saves.placeFirmware(player, GAME, target()).isEmpty())
        docs.onWrite = null
        docs.tearWrites = false
        docs.unreachable -= FOLDER
        // Back again, the torn bytes are still the shell's, so v2 replaces them.
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target()))
        assertEquals("bios v2", docs.text(FOLDER, "SCPH1001.BIN"))
    }

    @Test
    fun firmwareNeverGoesIntoAFolderFoldcadeHosts() {
        docs.library["lib://bios"] = "bios".toByteArray()
        val hosted = object : Player by Melon(FOLDER, firmware = setOf("SCPH1001.BIN")), app.foldcade.api.plugin.HostedSaveFolder {
            override val hostedFolderTitle = "cards"
            override val hostedFolderSteps = "steps"
            override fun bindHostedFolder(contentUri: String, inUse: Boolean) = Unit
            override fun hostedFolderInUse() = true
        }
        val target = LaunchTarget.ContentUri(ROM_URI, firmware = listOf(FirmwareBytes("SCPH1001.BIN", "lib://bios")))
        // Its save declarations name the hosted folder, which is not where it looks for firmware.
        assertTrue(saves.placeFirmware(hosted, GAME, target).isEmpty())
        assertEquals(null, docs.text(FOLDER, "SCPH1001.BIN"))
        // The folder the user picked for it is.
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(hosted, GAME, target, folder = "content://tree/data"))
        assertEquals("bios", docs.text("content://tree/data", "SCPH1001.BIN"))
    }

    @Test
    fun firmwareTheUserPutThereStaysEvenWhenItMatchedTheLibrarys() {
        docs.library["lib://bios"] = "bios v1".toByteArray()
        docs.put(FOLDER, "SCPH1001.BIN", "bios v1")
        val player = Melon(FOLDER, firmware = setOf("SCPH1001.BIN"))
        fun target() = LaunchTarget.ContentUri(ROM_URI, firmware = listOf(FirmwareBytes("SCPH1001.BIN", "lib://bios")))
        assertTrue(saves.placeFirmware(player, GAME, target()).isEmpty())
        // The shell never wrote it, so RomM's next copy does not replace it.
        docs.library["lib://bios"] = "bios v2".toByteArray()
        assertTrue(saves.placeFirmware(player, GAME, target()).isEmpty())
        assertEquals("bios v1", docs.text(FOLDER, "SCPH1001.BIN"))
    }

    @Test
    fun aPickedFolderWithNoneOfWhatThePlayersHoldsIsNotIt() {
        val duck = object : Player by Melon(FOLDER), app.foldcade.api.plugin.SaveFolderHolder {
            override fun bindSaveFolder(contentUri: String) = Unit
            override fun hasSaveFolder() = true
            override val saveFolderMarkers = setOf("memcards", "bios")
        }
        docs.put("content://tree/wrong", "Tetris DS (USA).sav", "save")
        assertFalse(saves.looksLikeSaveFolder(duck, "content://tree/wrong"))
        docs.put("content://tree/duckstation", "memcards/Game_1.mcd", "card")
        assertTrue(saves.looksLikeSaveFolder(duck, "content://tree/duckstation"))
        docs.put("content://tree/bios-only", "BIOS/SCPH1001.BIN", "bios")
        assertTrue(saves.looksLikeSaveFolder(duck, "content://tree/bios-only"))
        // A folder that cannot be listed, or a player with no markers, passes.
        assertTrue(saves.looksLikeSaveFolder(duck, "content://tree/unlisted"))
        assertTrue(saves.looksLikeSaveFolder(PLAYER, "content://tree/wrong"))
    }

    @Test
    fun firmwareThatDoesNotReadBackIsRemoved() {
        docs.library["lib://bios"] = "bios".toByteArray()
        docs.tearNextWrite = true
        val target = LaunchTarget.ContentUri(ROM_URI, firmware = listOf(FirmwareBytes("SCPH1001.BIN", "lib://bios")))
        val player = Melon(FOLDER, firmware = setOf("SCPH1001.BIN"))
        assertTrue(saves.placeFirmware(player, GAME, target).isEmpty())
        assertEquals(null, docs.text(FOLDER, "SCPH1001.BIN"))
        // The next launch tries again.
        assertEquals(listOf("SCPH1001.BIN"), saves.placeFirmware(player, GAME, target))
        assertEquals("bios", docs.text(FOLDER, "SCPH1001.BIN"))
    }

    @Test
    fun aSaveNameStaysInsideThePickedFolder() {
        assertEquals(listOf("mGBA", "Game.srm"), savePathParts("mGBA/Game.srm"))
        assertEquals(listOf("Game.sav"), savePathParts("Game.sav"))
        for (name in listOf("", "/Game.srm", "mGBA//Game.srm", "../Game.srm", "mGBA/./Game.srm", "mGBA/")) {
            assertEquals(name, null, savePathParts(name))
        }
    }

    private fun placed(text: String) = PlacedSave("sav", "lib://sav", md5Hex(text.toByteArray()))

    private class FakeDocuments : SaveDocuments {
        val names = mutableMapOf<String, String>()
        val folders = mutableMapOf<String, MutableMap<String, ByteArray>>()
        val library = mutableMapOf<String, ByteArray>()
        var writes = 0
        var tearWrites = false
        var tearNextWrite = false
        var onWrite: (() -> Unit)? = null
        val unreadable = mutableSetOf<String>()

        fun put(folder: String, name: String, text: String) {
            folders.getOrPut(folder) { mutableMapOf() }[name] = text.toByteArray()
        }

        fun text(folder: String, name: String): String? = folders[folder]?.get(name)?.toString(Charsets.UTF_8)

        override fun displayName(uri: String): String? = names[uri]

        override fun find(folderUri: String, name: String): String? =
            if (folderUri !in unreachable && folders[folderUri]?.containsKey(name) == true) "$folderUri/$name" else null

        var onCreate: (() -> Unit)? = null
        /** Folders that cannot be reached, as when their grant is gone: nothing found or made there. */
        val unreachable = mutableSetOf<String>()

        override fun create(folderUri: String, name: String): String? {
            if (folderUri in unreachable) return null
            folders.getOrPut(folderUri) { mutableMapOf() }[name] = ByteArray(0)
            onCreate?.invoke()
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

        override fun childNames(folderUri: String): List<String>? =
            folders[folderUri]?.keys?.map { it.substringBefore('/') }?.distinct()

        override fun write(uri: String, bytes: ByteArray) {
            writes += 1
            onWrite?.invoke()
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

    private class Melon(
        private val folder: String?,
        private val named: Boolean = true,
        private val firmware: Set<String> = emptySet(),
    ) : Player {
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
        override fun firmwareFileName(name: String): String? = name.takeIf { it in firmware }
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

/** Stands for the app dying part way: no `catch (Exception)` stops it. */
private class AppDied : Error()
