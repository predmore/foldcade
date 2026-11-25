package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.language.FolderRef
import app.foldcade.plugins.explus.ExPlusEntry
import app.foldcade.plugins.melonds.MelonDsPlayer
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test

class BesideGameSavesTest {
    private val authority = SharedPaths.EXTERNAL_STORAGE_AUTHORITY
    private val bound = FolderRef("primary:saves/nds", "saves/nds")
    private val ds = MelonDsPlayer()
    private val game = Game("local-folder", "one", ds.platformId, Availability.LocalOnly, "Counter")
    private val docs = object : SaveDocuments {
        var name = "Counter.nds"
        val bytes = mutableMapOf<String, ByteArray>()
        val folders = mutableMapOf<Pair<String, String>, List<SaveChild>>()
        override fun displayName(uri: String) = name
        override fun find(folderUri: String, name: String) = error("Expected exact child enumeration")
        override fun create(folderUri: String, name: String) = error("A probe is read-only")
        override fun read(uri: String) = bytes[uri]
        override fun write(uri: String, bytes: ByteArray) = error("A probe is read-only")
        override fun delete(uri: String) = error("A probe is read-only")
        override fun children(folderUri: String, path: String) = folders[folderUri to path]
    }

    @Test fun ownMediaDistinguishesAnActualSaveFromAnEmptyFileCreatedAtBoot() {
        val media = Files.createTempDirectory("beside-romm")
        val dir = Files.createDirectory(media.resolve("game"))
        val target = LaunchTarget.SharedFile(dir.resolve(docs.name).toString(), "content://romm/game")
        val files = BesideGameSaves(docs, emptyList(), "app.foldcade", media)
        fun read() = files.read(ds, game, target, bound)!!

        assertEquals(FileReading.Missing, read().read)
        Files.createFile(dir.resolve("Counter.sav"))
        assertNull(read().reading().spotHash)
        assertTrue(read().reading().spotReadable)
        val progress = byteArrayOf(10, 20, 30)
        Files.write(dir.resolve("Counter.sav"), progress)
        assertEquals(md5Hex(progress), read().reading().spotHash)
        assertTrue(read().reading().besideGame)
        assertNull(BesideGameSaves(docs, emptyList(), "app.foldcade", null).read(ds, game, target, bound))
    }

    @Test fun localProbeReadsThroughAReadOnlyGrantAndExcludesTheBoundFolderAlias() {
        val target = LaunchTarget.SharedFile("/storage/emulated/0/Documents/ROMs/Counter.nds", "content://library/opaque")
        val tree = treeUri(authority, "home:")
        val folder = folderDocumentUri(tree, "home:ROMs")
        val save = folderDocumentUri(tree, "home:ROMs/Counter.sav")
        docs.folders[folder to ""] = listOf(SaveChild("Counter.sav", save, false, 1))
        docs.bytes[save] = byteArrayOf(17)
        val files = BesideGameSaves(docs, listOf(SaveFolderGrant(tree, read = true, write = false)), "app.foldcade", null)
        val observed = files.read(ds, game, target, bound)!!
        assertEquals("primary:Documents/ROMs", observed.root.identity)
        assertEquals(md5Hex(byteArrayOf(17)), observed.reading().spotHash)
        assertNull(files.read(ds, game, target, FolderRef("home:ROMs", "This folder")))
        docs.bytes.clear()
        assertFalse(files.read(ds, game, target, bound)!!.reading().spotReadable)
    }

    @Test fun exArchivesCarryOnlyThisGamesBatteryAndClockFiles() {
        val media = Files.createTempDirectory("beside-ex")
        val named = Files.createDirectories(media.resolve("Counter/saves/run 2"))
        docs.name = "Counter.sfc"
        val player = ExPlusEntry().players.single { it.id == "snes9x-ex.snes" }
        val target = LaunchTarget.SharedFile(media.resolve(docs.name).toString(), "content://romm/game")
        for (file in listOf(media.resolve("Counter.srm"), media.resolve("Counter.rtc"), named.resolve("auto.srm"),
            named.resolve("auto.rtc"), media.resolve("Other.srm"), named.resolve("auto.frz"))) {
            Files.write(file, byteArrayOf(1, 2, 3))
        }
        val files = BesideGameSaves(docs, emptyList(), "app.foldcade", media)
        val result = files.read(player, game.copy(platformId = "snes"), target, bound)!!
        val entries = ZipInputStream(ByteArrayInputStream((result.read as FileReading.Bytes).bytes)).use { zip ->
            generateSequence { zip.nextEntry?.name }.toList()
        }
        assertEquals(listOf("Counter.rtc", "Counter.srm", "Counter/saves/run 2/auto.srm"), entries)
        docs.name = "Counter.zip"
        val nes = ExPlusEntry().players.single { it.id == "nes-emu.nes" }
        assertNull(files.read(nes, game.copy(platformId = "nes"), target, bound))
    }

    @Test fun ownMediaDoesNotTreatASymlinkAsAReadableCandidate() {
        val media = Files.createTempDirectory("beside-links")
        val other = Files.createTempFile("outside-save", ".sav")
        Files.write(other, byteArrayOf(1))
        Files.createSymbolicLink(media.resolve("Counter.sav"), other)
        val target = LaunchTarget.SharedFile(media.resolve(docs.name).toString(), "content://romm/game")
        val read = BesideGameSaves(docs, emptyList(), "app.foldcade", media).read(ds, game, target, bound)!!
        assertEquals(FileReading.Unreadable, read.read)
    }

    @Test fun initializedGbaAndGbcBatteriesStillHaveEvidenceIncludingAClockButNoProgressToKeep() {
        for ((id, rom) in listOf("gba-emu.game-boy-advance" to "Counter.gba", "gbc-emu.game-boy-color" to "Counter.gbc")) {
            val media = Files.createTempDirectory("blank-battery")
            val player = ExPlusEntry().players.single { it.id == id }
            docs.name = rom
            val target = LaunchTarget.SharedFile(media.resolve(rom).toString(), "content://romm/game")
            val files = BesideGameSaves(docs, emptyList(), "app.foldcade", media)
            fun read() = files.read(player, game.copy(platformId = player.platformId), target, bound)!!
            if (rom.endsWith(".gbc")) Files.write(media.resolve("Counter.rtc"), byteArrayOf(4, 3, 2, 1))
            for (fill in listOf(0.toByte(), (-1).toByte())) {
                val bytes = ByteArray(8192) { fill }
                Files.write(media.resolve("Counter.sav"), bytes)
                val initialized = read()
                assertTrue(initialized.blankBattery)
                assertNotNull(initialized.reading().spotHash)
                bytes[11] = 1
                Files.write(media.resolve("Counter.sav"), bytes)
                val progress = read()
                assertFalse(progress.blankBattery)
                assertNotEquals(initialized.reading().spotHash, progress.reading().spotHash)
            }
        }
    }
}
