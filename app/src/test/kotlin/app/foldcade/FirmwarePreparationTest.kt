package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.plugins.melonds.MelonDsPlayer
import app.foldcade.plugins.retroarch.RetroArchEntry
import app.foldcade.plugins.retroarch.RetroArchPlayer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FirmwarePreparationTest {
    @get:Rule val temp = TemporaryFolder()
    private val entry = RetroArchEntry()
    private fun player(platform: String, core: String? = null): RetroArchPlayer =
        (entry.players.single { it.platformId == platform } as RetroArchPlayer).let { if (core == null) it else it.withCore(core) }
    private val game = Game("romm", "12", "sega-cd", Availability.Cached, "Disc")
    private val offered = LaunchTarget.ContentUri("game", listOf(FirmwareBytes("bios.zip", "181-MiB-archive")))

    private class Documents : SaveDocuments {
        var metadata = 0
        var reads = 0
        var entries = emptyList<SaveChild>()
        override fun displayName(uri: String): String { metadata++; return "Disc (USA).chd" }
        override fun children(folderUri: String, path: String): List<SaveChild> { metadata++; return entries }
        override fun find(folderUri: String, name: String): String? { metadata++; return null }
        override fun read(uri: String): ByteArray? { reads++; error("fast launch read firmware bytes") }
        override fun head(uri: String, size: Int): ByteArray? { reads++; error("fast launch probed a source") }
        override fun readAt(uri: String, offset: Long, length: Int): ByteArray? { reads++; error("fast launch read installed BIOS") }
        override fun create(folderUri: String, name: String): String? = error("unexpected publication")
        override fun write(uri: String, bytes: ByteArray) = error("unexpected write")
        override fun delete(uri: String): Boolean = error("unexpected delete")
    }

    private fun saves(docs: Documents) = PlayerSaves(docs, object : SaveMarks {
        override fun mark(key: String): String? = null
        override fun setMark(key: String, hash: String) = error("unexpected mark")
        override fun clearMark(key: String) = error("unexpected mark")
    }, temp.newFolder().toPath(), temp.newFolder().toPath())

    @Test fun optionalFirmwareDoesNoIoEvenWithRommOffersAndNoFolderGrant() {
        val players = listOf(player("game-boy"), player("genesis"), player("playstation", "pcsx_rearmed"),
            player("playstation", "mednafen_psx"), player("game-boy-advance", "gpsp"), player("psp"), MelonDsPlayer(), app.foldcade.plugins.explus.ExPlusEntry().players.first())
        for (player in players) {
            val docs = Documents()
            val saves = saves(docs)
            repeat(2) { // Cold and repeated calls take the same zero-I/O path.
                val result = prepareFirmware(player, game.copy(platformId = player.platformId), offered, "system", docs, saves,
                    folder = { error("optional firmware resolved a folder") }, local = { error("optional firmware scanned BIOS") })
                assertFalse(player.id, result.blocksLaunch)
                assertFalse(player.id, result.needsFolder)
                assertTrue(result.written.isEmpty())
            }
            assertEquals(player.id, 0, docs.metadata)
            assertEquals(player.id, 0, docs.reads)
        }
    }

    @Test fun installedRequiredBiosUsesOnlyMetadataWithoutScanningHashingOrReadingOffers() {
        val players = listOf(player("sega-cd") to ("BIOS_CD_U.BIN" to 131072L),
            player("saturn", "mednafen_saturn") to ("mpr-17933.bin" to 524288L),
            player("playstation", "swanstation") to ("my-console.dump" to 524288L))
        for ((player, installed) in players) {
            val docs = Documents().apply { entries = listOf(SaveChild(installed.first, "installed", false, installed.second)) }
            val saves = saves(docs)
            repeat(2) {
                val result = prepareFirmware(player, game.copy(platformId = player.platformId), offered, "system", docs, saves,
                    folder = { "system" }, local = { error("installed BIOS triggered a scan") })
                assertFalse(result.blocksLaunch)
                assertFalse(result.needsFolder)
                assertTrue(result.written.isEmpty())
            }
            assertEquals(player.id, 4, docs.metadata) // Document name and one folder snapshot per launch.
            assertEquals(player.id, 0, docs.reads)
        }
    }

    @Test fun missingGrantNeverBlocksAndNeverScansSources() {
        val docs = Documents()
        val result = prepareFirmware(player("sega-cd"), game, offered, "RetroArch/system", docs, saves(docs),
            folder = { null }, local = { error("ungranted destination scanned storage") })
        assertTrue(result.needsFolder)
        assertFalse(result.blocksLaunch)
        assertEquals(0, docs.reads)
    }

    @Test fun firmwareHashAndCopyHaveHardBoundsEvenWhenTheProviderLiesAboutSize() {
        assertThrows(IllegalStateException::class.java) { digestHex(ByteArrayInputStream(ByteArray(5)), limit = 4) }
        val out = ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) { copyFirmwareBytes(ByteArrayInputStream(ByteArray(5)), out, limit = 4) }
        assertEquals(0, out.size())
    }

    @Test fun measureOptionalAndInstalledLaunchesWithoutAnySourceReads() {
        for ((player, installed) in listOf(player("game-boy") to false, MelonDsPlayer() to false, player("sega-cd") to true)) {
            val docs = Documents().apply { if (installed) entries = listOf(SaveChild("bios_CD_U.bin", "installed", false, 131072)) }
            val saves = saves(docs)
            val start = System.nanoTime()
            repeat(100) {
                val result = prepareFirmware(player, game.copy(platformId = player.platformId), offered, "system", docs, saves,
                    folder = { if (installed) "system" else error("optional folder probe") }, local = { error("unexpected scan") })
                assertFalse(result.blocksLaunch)
            }
            println("FIRMWARE_SPEED ${player.id}: average_us=${(System.nanoTime() - start) / 100000.0}, source_reads=${docs.reads}, metadata_per_launch=${docs.metadata / 100}")
            assertEquals(0, docs.reads)
        }
    }
}
