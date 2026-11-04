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
        assertFalse(coreFirmware.single { it.core == "yabasanshiro" && it.path == "saturn_bios.bin" }.optional)
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
        assertTrue(player("playstation").requiredBios(game("playstation")).isEmpty())
        assertTrue(player("playstation", "pcsx_rearmed").requiredBios(game("playstation")).isEmpty())
        assertEquals(listOf(listOf("saturn_bios.bin", "sega_101.bin", "mpr-17933.bin")), player("saturn").requiredBios(game("saturn")))
        assertEquals(listOf(listOf("mpr-17933.bin")), player("saturn", "mednafen_saturn").requiredBios(game("saturn")))
    }

    @Test fun anExistingAlternativeRevisionIsNotMistakenForAMissingBios() {
        val folder = object : app.foldcade.api.plugin.SaveFolderAccess {
            override fun list(path: String) = if (path.isEmpty()) listOf(app.foldcade.api.plugin.SaveFolderEntry("sega_101.bin", false, 524288)) else emptyList()
            override fun read(path: String, limit: Int): ByteArray? = error("must not hash the player's existing BIOS")
            override fun read(path: String, offset: Long, length: Int): ByteArray? = error("installed BIOS needs metadata only")
            override fun write(path: String, bytes: ByteArray) = false
            override fun createFolder(path: String) = false
            override fun moveToBackup(path: String) = false
        }
        assertTrue(player("saturn").missingFirmware(game("saturn"), folder).isEmpty())
        assertTrue(player("saturn", "mednafen_saturn").missingFirmware(game("saturn", "Game (Japan)"), folder).isEmpty())
    }

    private fun installed(name: String, size: Long) = object : app.foldcade.api.plugin.SaveFolderAccess {
        override fun list(path: String) = if (path.isEmpty()) listOf(app.foldcade.api.plugin.SaveFolderEntry(name, false, size)) else emptyList()
        override fun read(path: String, limit: Int): ByteArray? = error("must not hash an installed BIOS")
        override fun read(path: String, offset: Long, length: Int): ByteArray? = error("installed BIOS needs metadata only")
        override fun write(path: String, bytes: ByteArray) = false
        override fun createFolder(path: String) = false
        override fun moveToBackup(path: String) = false
    }

    @Test fun existingMixedCaseBiosUsesTheProviderNameAndDoesNotBlockPlay() {
        val sega = player("sega-cd")
        val folder = installed("BIOS_CD_U.BIN", 131072)
        assertTrue(sega.missingFirmware(game("sega-cd"), folder).isEmpty())
        assertTrue(sega.hasFirmware(folder))
    }

    @Test fun picoDriveAcceptsItsRegionalRawAndZippedAlternativesWithoutChangingOtherCores() {
        val pico = player("sega-cd", "picodrive")
        val regions = mapOf(
            "USA" to listOf("us_scd2_9306", "SegaCDBIOS9303", "us_scd1_9210", "bios_CD_U"),
            "Europe" to listOf("eu_mcd2_9306", "eu_mcd2_9303", "eu_mcd1_9210", "bios_CD_E"),
            "Japan" to listOf("jp_mcd2_921222", "jp_mcd1_9112", "jp_mcd1_9111", "bios_CD_J"),
        )
        for ((region, names) in regions) for (name in names) for (extension in listOf("bin", "zip")) {
            val file = "$name.$extension"
            assertTrue(file, pico.missingFirmware(game("sega-cd", "Game ($region)"), installed(file, 131072)).isEmpty())
            assertTrue(file, pico.hasFirmware(installed(file, 131072)))
        }
        assertFalse(player("sega-cd", "genesis_plus_gx").missingFirmware(game("sega-cd"), installed("us_scd1_9210.bin", 131072)).isEmpty())
        assertTrue(player("genesis", "picodrive").firmwareList().none { it.path.startsWith("us_scd") })
        assertEquals("us_scd1_9210.bin", pico.firmwareDestination(FirmwareOffer("dump.zip/us_scd1_9210.bin", ByteArray(131072))))
        assertNull(pico.firmwareDestination(FirmwareOffer("us_scd1_9210.bin", ByteArray(12))))
        assertEquals(listOf("bios_CD_U.bin"), pico.missingFirmware(game("sega-cd"), installed("empty.bin", 0)))
    }

    @Test fun ps1CoresKeepTheirOwnDirectorySearchAndOverrideBiosChoices() {
        for (name in listOf("SCPH5501.BIN", "SCPH-5501.bin", "scph7003.bin", "openbios.bin", "my-console.dump")) {
            assertTrue(name, player("playstation", "mednafen_psx").missingFirmware(game("playstation"), installed(name, 524288)).isEmpty())
        }
        assertTrue(player("playstation", "mednafen_psx_hw").hasFirmware(installed("SCPH-5903.bin", 1048576)))
        val swan = player("playstation", "swanstation")
        for (size in listOf(524288L, 4194304L, 0x3E66F0L)) {
            assertTrue(swan.missingFirmware(game("playstation"), installed("scph1001.bin", size)).isEmpty())
            assertTrue(swan.hasFirmware(installed("renamed.bin", size)))
        }
        assertTrue(swan.firmwareList().any { it.path == "scph1001.bin" && it.md5 == "924e392ed05558ffdb115408c263dccf" })
        assertFalse(swan.missingFirmware(game("playstation"), installed("empty.bin", 0)).isEmpty())
    }

    @Test fun optionalBiosDoesNotNagForAFolderOnCartridgeLaunches() {
        for (system in listOf("nes", "snes", "game-boy", "game-boy-advance", "genesis", "psp")) {
            val p = player(system)
            assertFalse(system, p.needsFirmwareFolder(game(system)))
        }
        assertTrue(player("sega-cd").needsFirmwareFolder(game("sega-cd")))
        assertFalse(player("playstation", "mednafen_psx").needsFirmwareFolder(game("playstation")))
    }

    @Test fun metadataFilteringStillFindsRenamedBiosWithUnusualDocumentedSizes() {
        val color = player("game-boy-color", "sameboy")
        assertTrue(color.localFirmwareCandidate("renamed.dump", 2304))
        assertFalse(color.localFirmwareCandidate("unrelated-image.dat", 4194304))
        assertTrue(player("playstation").localFirmwareCandidate("renamed.dump", 524288))
        assertFalse(player("playstation").localFirmwareCandidate("ps2-dump.bin", 4194304))
        assertFalse(player("playstation").localFirmwareCandidate("scph5501.bin", -1))
        assertFalse(player("playstation").localFirmwareCandidate("scph5501.bin", 0))
    }

    @Test fun openBiosAndBundledAssetsNeverBlockWorkingCores() {
        assertTrue(player("game-boy-advance", "gpsp").requiredBios(game("game-boy-advance")).isEmpty())
        assertTrue(player("psp").requiredBios(game("psp")).isEmpty())
        assertTrue(player("playstation", "mednafen_psx").requiredBios(game("playstation")).isEmpty())
        assertTrue(player("playstation", "mednafen_psx_hw").requiredBios(game("playstation")).isEmpty())
        assertTrue(coreFirmware.any { it.core == "yabasanshiro" && it.path == "sega_101.bin" && it.md5 == "85ec9ca47d8f6807718151cbcca8b964" })
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
