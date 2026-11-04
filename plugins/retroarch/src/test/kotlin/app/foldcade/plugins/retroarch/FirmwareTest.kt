package app.foldcade.plugins.retroarch

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.FirmwareOffer
import app.foldcade.api.plugin.Game
import org.junit.Assert.*
import org.junit.Test

class FirmwareTest {
    private val entry = RetroArchEntry()
    private fun player(platform: String, core: String? = null): RetroArchPlayer {
        val base = entry.players.single { it.platformId == platform } as RetroArchPlayer
        return core?.let(base::withCore) ?: base
    }
    private fun game(platform: String, label: String = "Game (USA)") = Game("local-folder", "game", platform, Availability.LocalOnly, label)

    @Test fun everyCatalogCoreHasASnapshotIncludingRequiredAndOptionalFiles() {
        assertEquals("scph5501.bin", coreFirmware.single { it.core == "mednafen_psx" && it.path == "scph5501.bin" }.path)
        assertFalse(coreFirmware.single { it.core == "yabasanshiro" }.optional)
        assertEquals("dc/dc_boot.bin", coreFirmware.first { it.core == "flycast" }.path)
        assertTrue(coreFirmware.any { it.core == "gpsp" && !it.optional })
        assertTrue(coreFirmware.any { it.path == "Machines/Shared Roms/MSX.rom" && !it.optional })
        assertEquals("2efd74e3232ff260e371b99f84024f7f", coreFirmware.single { it.core == "genesis_plus_gx" && it.path == "bios_CD_U.bin" }.md5)
    }

    @Test fun aRenamedDumpIsIdentifiedByBytesAndBadBytesCannotImpersonateAKnownName() {
        val bytes = "test-only dump".toByteArray()
        val spec = CoreFirmware("test", "scph5501.bin", false, biosMd5(bytes))
        assertEquals(spec, identifyBios(listOf(spec), FirmwareOffer("anything.zip/renamed.dat", bytes)))
        assertNull(identifyBios(listOf(spec), FirmwareOffer("scph5501.bin", "bad".toByteArray())))
        assertNull(player("playstation").firmwareDestination(FirmwareOffer("scph5501.bin", bytes)))
    }

    @Test fun discBiosIsRequiredOnlyForDiscSystemsAndTheDiscRegion() {
        assertEquals(listOf(listOf("bios_CD_U.bin")), player("sega-cd").requiredBios(game("sega-cd")))
        assertEquals(listOf(listOf("bios_CD_J.bin")), player("sega-cd").requiredBios(game("sega-cd", "Game (Japan)")))
        assertEquals(listOf(listOf("bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin")), player("sega-cd").requiredBios(game("sega-cd", "Game")))
        assertTrue(player("genesis").requiredBios(game("genesis")).isEmpty())
        assertTrue(player("pc-engine").requiredBios(game("pc-engine")).isEmpty())
        assertEquals(listOf(listOf("syscard3.pce")), player("pc-engine-cd").requiredBios(game("pc-engine-cd")))
    }

    @Test fun pinnedAndInstalledCoreFirmwareFollowTheCoreThatWillLaunch() {
        entry.cores.update(mapOf(PACKAGES.first() to setOf("mednafen_psx_libretro_android.so")))
        assertEquals(listOf(listOf("scph5501.bin")), player("playstation").requiredBios(game("playstation")))
        assertTrue(player("playstation", "pcsx_rearmed").requiredBios(game("playstation")).isEmpty())
        assertEquals(listOf(listOf("saturn_bios.bin")), player("saturn").requiredBios(game("saturn")))
        assertEquals(listOf(listOf("mpr-17933.bin")), player("saturn", "mednafen_saturn").requiredBios(game("saturn")))
    }

    @Test fun blueMsxGetsWholeDirectoryBundlesRatherThanOnlyTheirMarkerFiles() {
        val blue = player("colecovision", "bluemsx")
        assertTrue(blue.firmwareKeepsFolders)
        assertEquals("Machines/COL - ColecoVision/config.ini", blue.firmwareDestination(FirmwareOffer("bios.zip/bundle/Machines/COL - ColecoVision/config.ini", "config".toByteArray())))
        assertEquals("Databases/msxromdb.xml", blue.firmwareDestination(FirmwareOffer("Databases/msxromdb.xml", byteArrayOf(1))))
        assertNull(blue.firmwareDestination(FirmwareOffer("bios.zip/../Machines/A/config.ini", byteArrayOf(1))))
        assertNull(blue.firmwareDestination(FirmwareOffer("random.bin", byteArrayOf(1))))
    }

    @Test fun foldersInArchivesAreSafeAndUnknownOptionalFirmwareKeepsItsDocumentedPath() {
        assertNull(player("sega-cd").firmwareFileName("dump.zip/../bios_CD_U.bin"))
        assertEquals("bios_CD_U.bin", player("sega-cd").firmwareFileName("dump.zip/folder/bios_CD_U.bin"))
        val file = CoreFirmware("test", "dir/firmware.bin", true, null)
        assertEquals(file, identifyBios(listOf(file), FirmwareOffer("dump.zip/firmware.bin", byteArrayOf(1))))
    }
}
