package app.foldcade.localfolder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformDetectionTest {
    @Test
    fun folderNamesMatchOnePlatform() {
        val cases = mapOf(
            "3ds" to "nintendo-3ds",
            "n3ds" to "nintendo-3ds",
            "Nintendo 3DS" to "nintendo-3ds",
            "New Nintendo 3DS" to "nintendo-3ds",
            "3DS (USA)" to "nintendo-3ds",
            "3ds roms" to "nintendo-3ds",
            "nds" to "nintendo-ds",
            "ds" to "nintendo-ds",
            "Nintendo DS" to "nintendo-ds",
            "dsi" to "nintendo-ds",
            "nds roms" to "nintendo-ds",
            "Game Boy" to "game-boy",
            "Game Boy Color" to "game-boy-color",
            "Game Boy Advance" to "game-boy-advance",
            "gameboyadvance" to "game-boy-advance",
            "PlayStation" to "playstation",
            "PlayStation 2" to "playstation-2",
            "PS2" to "playstation-2",
            "snes" to "snes",
            "NES" to "nes",
            "mega-drive" to "genesis",
            "Wii" to "wii",
        )
        for ((name, id) in cases) {
            assertEquals(name, id, platformFromFolderName(name)?.id)
        }
    }

    @Test
    fun lookalikeFoldersDoNotMatch() {
        assertNull(platformFromFolderName("roms"))
        assertNull(platformFromFolderName("bios"))
        assertNull(platformFromFolderName("pc"))
        assertNull(platformFromFolderName("wii u"))
        assertNull(platformFromFolderName("nintendo"))
        assertEquals("nintendo-3ds", platformFromFolderName("3ds")?.id)
        assertEquals("nintendo-ds", platformFromFolderName("ds")?.id)
        assertEquals("game-boy", platformFromFolderName("Game Boy")?.id)
        assertEquals("game-boy-color", platformFromFolderName("Game Boy Color")?.id)
    }

    @Test
    fun uniqueExtensionsClassifyWithoutAFolder() {
        assertEquals("nintendo-3ds", platformFromExtension("cci")?.id)
        assertEquals("nintendo-3ds", platformFromExtension(".ZCCI")?.id)
        assertEquals("nintendo-3ds", platformFromExtension("3ds")?.id)
        assertEquals("nintendo-ds", platformFromExtension("NDS")?.id)
        assertEquals("nintendo-ds", platformFromExtension("dsi")?.id)
        assertEquals("game-boy", platformFromExtension("gb")?.id)
        assertEquals("game-boy-color", platformFromExtension("gbc")?.id)
        assertEquals("game-boy-advance", platformFromExtension("gba")?.id)
        assertEquals("snes", platformFromExtension("sfc")?.id)
        assertEquals("wii", platformFromExtension("wbfs")?.id)
        assertEquals("gamecube", platformFromExtension("gcm")?.id)
        assertEquals("psp", platformFromExtension("cso")?.id)
        assertEquals("nintendo-switch", platformFromExtension("nsp")?.id)
        assertEquals("nintendo-switch", platformFromExtension("xci")?.id)
        assertEquals("dreamcast", platformFromExtension("gdi")?.id)
        assertEquals("dreamcast", platformFromExtension("cdi")?.id)
    }

    @Test
    fun discPlatformsUseTheirFolders() {
        assertEquals("saturn", platformFromFolderName("Sega Saturn")?.id)
        assertEquals("dreamcast", platformFromFolderName("dc")?.id)
        assertEquals("nintendo-switch", platformFromFolderName("Switch ROMs")?.id)
        assertEquals("saturn", resolvePlatform("cue", platformFromFolderName("saturn"))?.id)
        assertEquals("dreamcast", resolvePlatform("chd", platformFromFolderName("dreamcast"))?.id)
        assertNull(platformFromExtension("ccd"))
    }

    @Test
    fun sharedExtensionsNeedAFolder() {
        assertNull(platformFromExtension("zip"))
        assertNull(platformFromExtension("7z"))
        assertNull(platformFromExtension("iso"))
        assertNull(platformFromExtension("chd"))
        assertNull(platformFromExtension("rvz"))
        assertNull(platformFromExtension("pbp"))
        assertNull(platformFromExtension("cue"))
        assertNull(platformFromExtension("md"))
        assertNull(platformFromExtension("bin"))
        assertNull(platformFromExtension("m3u"))
        assertNull(platformFromExtension("m3u8"))
        assertNull(platformFromExtension("exe"))
        assertNull(platformFromExtension("sav"))
        assertNull(platformFromExtension(""))
    }

    @Test
    fun platformIdsStayPut() {
        assertEquals(
            listOf(
                "dreamcast",
                "game-boy",
                "game-boy-advance",
                "game-boy-color",
                "game-gear",
                "gamecube",
                "genesis",
                "master-system",
                "nes",
                "nintendo-3ds",
                "nintendo-64",
                "nintendo-ds",
                "nintendo-switch",
                "playstation",
                "playstation-2",
                "psp",
                "saturn",
                "snes",
                "wii",
            ),
            PlatformCatalog.specs.map { it.platform.id }.sorted(),
        )
    }
}
