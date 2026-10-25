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
            "PlayStation 3" to "playstation-3",
            "PS3" to "playstation-3",
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
    fun namesFollowOneConvention() {
        // Every Sega system carries the maker, as Sega CD and Sega 32X always did; Nintendo
        // keeps the names players use, so Home reads Sega Genesis beside Nintendo DS. The
        // PlayStations are numbered in full, so PlayStation 2 sits beside PlayStation, not PS2.
        val names = PlatformCatalog.specs.associate { it.platform.id to it.platform.name }
        val sega = listOf("genesis", "master-system", "game-gear", "saturn", "dreamcast", "sega-cd", "sega-32x")
        assertEquals(
            listOf("Sega Genesis", "Sega Master System", "Sega Game Gear", "Sega Saturn", "Sega Dreamcast", "Sega CD", "Sega 32X"),
            sega.map { names[it] },
        )
        assertEquals(
            listOf("NES", "SNES", "Nintendo 64", "GameCube", "Wii", "Nintendo Switch", "Nintendo DS", "Nintendo 3DS", "Game Boy"),
            listOf("nes", "snes", "nintendo-64", "gamecube", "wii", "nintendo-switch", "nintendo-ds", "nintendo-3ds", "game-boy").map { names[it] },
        )
        assertEquals(listOf("PlayStation", "PlayStation 2", "PlayStation 3", "PSP"), listOf("playstation", "playstation-2", "playstation-3", "psp").map { names[it] })
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
                "atari-2600",
                "atari-7800",
                "atari-lynx",
                "colecovision",
                "dreamcast",
                "game-boy",
                "game-boy-advance",
                "game-boy-color",
                "game-gear",
                "gamecube",
                "genesis",
                "master-system",
                "neo-geo-pocket",
                "neo-geo-pocket-color",
                "nes",
                "nintendo-3ds",
                "nintendo-64",
                "nintendo-ds",
                "nintendo-switch",
                "pc-engine",
                "pc-engine-cd",
                "playstation",
                "playstation-2",
                "playstation-3",
                "psp",
                "saturn",
                "sega-32x",
                "sega-cd",
                "snes",
                "virtual-boy",
                "wii",
                "wonderswan",
                "wonderswan-color",
            ),
            PlatformCatalog.specs.map { it.platform.id }.sorted(),
        )
    }

    @Test
    fun romTitlesDropNoIntroTagsAndKeepTheName() {
        val cases = mapOf(
            "Bravely Second - End Layer (USA) (En,Fr,Es).cci" to "Bravely Second - End Layer",
            "Super Mario 3D Land (CTR-P-AREE) (v0.3.0) (U).legit.cci" to "Super Mario 3D Land",
            "Sonic Boom- Fire & Ice (CTR-P-BS6E) (v0.0.0) (W).piratelegit.cci" to "Sonic Boom- Fire & Ice",
            "Digimon Universe Appli Monsters [ENG v1.3] For Emu [Citra].cci" to "Digimon Universe Appli Monsters",
            "Legend of Zelda, The - Ocarina of Time 3D (USA) (En,Fr,Es) (Rev 1).cci" to
                "The Legend of Zelda - Ocarina of Time 3D",
            "Disney Collection, The - QuackShot (Europe).md" to "The Disney Collection - QuackShot",
            "Kid Icarus- Uprising.cci" to "Kid Icarus- Uprising",
            "super_mario_world.sfc" to "super mario world",
            "(USA) Odd.nes" to "(USA) Odd",
            "Tetris DS (USA).nds" to "Tetris DS",
            "Final Fantasy VII (USA) (Disc 2).cue" to "Final Fantasy VII (Disc 2)",
            "Game (Europe) (Disk 1 of 3).adf" to "Game (Disk 1 of 3)",
        )
        cases.forEach { (file, title) -> assertEquals(file, title, titleFromFileName(file)) }
    }

    @Test
    fun retroSystemsFollowEsDeFoldersAndUniqueExtensions() {
        assertEquals("sega-cd", platformFromFolderName("segacd")?.id)
        assertEquals("sega-cd", platformFromFolderName("Mega-CD")?.id)
        assertEquals("sega-cd", resolvePlatform("chd", platformFromFolderName("segacd"))?.id)
        assertEquals("sega-32x", platformFromExtension("32x")?.id)
        assertEquals("pc-engine", platformFromExtension("pce")?.id)
        assertEquals("pc-engine", platformFromFolderName("tg16")?.id)
        assertEquals("pc-engine-cd", resolvePlatform("cue", platformFromFolderName("pcenginecd"))?.id)
        assertEquals("atari-2600", platformFromExtension("a26")?.id)
        assertEquals("atari-7800", platformFromExtension("a78")?.id)
        assertEquals("atari-lynx", platformFromExtension("lnx")?.id)
        assertEquals("neo-geo-pocket", platformFromExtension("ngp")?.id)
        assertEquals("neo-geo-pocket-color", platformFromExtension("ngc")?.id)
        assertEquals("wonderswan", platformFromExtension("ws")?.id)
        assertEquals("wonderswan-color", platformFromExtension("wsc")?.id)
        assertEquals("virtual-boy", platformFromExtension("vb")?.id)
        assertEquals("colecovision", platformFromExtension("col")?.id)
        // A disc image outside a system folder is still nobody's.
        assertNull(platformFromExtension("cue"))
        // A zip in a 2600 folder is that system's; a .bin is a disc track everywhere.
        assertEquals("atari-2600", resolvePlatform("zip", platformFromFolderName("atari2600"))?.id)
        assertNull(resolvePlatform("bin", platformFromFolderName("atari2600")))
    }
}
