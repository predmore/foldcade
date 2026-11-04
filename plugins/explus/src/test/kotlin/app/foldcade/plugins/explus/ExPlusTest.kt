package app.foldcade.plugins.explus

import app.foldcade.api.plugin.*
import org.junit.Assert.*
import org.junit.Test
import java.util.ServiceLoader

class ExPlusTest {
    private val players = ExPlusEntry().players
    private fun player(prefix: String, platform: String? = null) = players.first { it.id.startsWith(prefix) && (platform == null || it.platformId == platform) }
    private fun reader(filename: String) = object : GameFileReader { override val fileName = filename; override fun read(offset: Long, length: Int): ByteArray? = null }
    private fun accepts(prefix: String, rom: String, save: String, platform: String? = null) =
        player(prefix, platform).saveArchiveFilter("battery", reader(rom))!!.accepts(save, byteArrayOf())

    @Test fun serviceKeepsStableChoicesAndSharesFoldersOnlyWithinOneApp() {
        assertEquals(players.map { it.id }, ServiceLoader.load(PluginEntry::class.java).filterIsInstance<ExPlusEntry>().single().players.map { it.id })
        assertEquals(players.size, players.map { it.id }.distinct().size)
        assertEquals("gbc-emu", (player("gbc-emu") as SaveFolderHolder).saveFolderKey)
        assertEquals(3, players.count { (it as SaveFolderHolder).saveFolderKey == "md-emu" })
    }
    @Test fun sourceExtensionsAndCompoundFdsExtensionAreExact() {
        for ((emulator, rom, save) in listOf(Triple("gba-emu", "Game.gba", "Game.sav"), Triple("gbc-emu", "Game.gbc", "Game.sav"),
            Triple("nes-emu", "Game.nes", "Game.sav"), Triple("nes-emu", "Game.fds", "Game.fds.sav"),
            Triple("snes9x-ex", "Game.sfc", "Game.srm"), Triple("md-emu", "Game.md", "Game.srm"))) {
            assertTrue(accepts(emulator,rom,save)); assertFalse(accepts(emulator,rom,"Other"+save.removePrefix("Game")))
            assertFalse(accepts(emulator,rom,"../$save")); assertFalse(accepts(emulator,rom,"$save.bak"))
        }
        assertFalse(accepts("nes-emu", "Game.fds", "Game.sav"))
        assertTrue(accepts("md-emu", "Disc.cue", "Disc.brm", "sega-cd"))
        assertFalse(accepts("md-emu", "Disc.cue", "Disc.srm", "sega-cd"))
    }
    // Nested GBC/RTC fixtures exercise the filter; upstream writes these files at the root.
    @Test fun gbcClockAndMatchingNestedFilesAreAcceptedButStatesAreNot() {
        for (path in listOf("Pokemon.sav", "Pokemon.rtc", "Pokemon/saves/run 2/auto.sav", "Pokemon/saves/run 2/auto.rtc"))
            assertTrue(path, accepts("gbc-emu", "Pokemon.gbc", path))
        for (path in listOf("Pokemon.0A.gqs", "Pokemon/saves/run 2/auto.0A.gqs", "Other/saves/run 2/auto.sav", "Pokemon/saves/../auto.sav", "auto.sav"))
            assertFalse(path, accepts("gbc-emu", "Pokemon.gbc", path))
    }
    @Test fun snesRootClocksTravelWithSramAndMatchingNestedFiles() {
        for (path in listOf("Game.srm", "Game.rtc", "Game/saves/run 2/auto.srm", "Game/saves/run 2/auto.rtc"))
            assertTrue(path, accepts("snes9x-ex", "Game.sfc", path))
        for (path in listOf("Other.rtc", "Game.0A.frz", "Game.rtc.bak"))
            assertFalse(path, accepts("snes9x-ex", "Game.sfc", path))
    }
    @Test fun unsupportedNamesAndUnknownSlotsCannotResolve() {
        for (name in listOf("Game.rar", "Game", "../Game.gba", "Game.gba\u0000", ".gba"))
            assertNull(name, player("gba-emu").saveArchiveFilter("battery",reader(name)))
        assertNull(player("gba-emu").saveArchiveFilter("sav",reader("Game.gba")))
        assertTrue(accepts("gba-emu", "One.Two.GBA", "One.Two.sav"))
    }
    @Test fun archivesUseTheOuterBasenameForSingleBatteryTypeSystems() {
        for (archive in listOf("Pokemon.zip", "Pokemon.7z")) {
            assertTrue(accepts("gba-emu", archive, "Pokemon.sav"))
            assertTrue(accepts("gbc-emu", archive, "Pokemon.rtc"))
            assertTrue(accepts("snes9x-ex", archive, "Pokemon.srm"))
            assertTrue(accepts("snes9x-ex", archive, "Pokemon.rtc"))
            assertFalse(accepts("gbc-emu", archive, "Inner ROM.sav"))
        }
        assertNull(player("nes-emu").saveArchiveFilter("battery", reader("Game.zip")))
        assertTrue((player("gbc-emu") as SaveFolderHolder).saveFolderPlaces.any { it == "EmuEx/GB/saves" })
        assertTrue((player("nes-emu") as SaveFolderHolder).saveFolderPlaces.any { it == "EmuEx/FC-NES/saves" })
    }
}
