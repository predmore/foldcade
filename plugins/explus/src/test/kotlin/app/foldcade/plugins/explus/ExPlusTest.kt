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
    @Test fun gbcClockAndEveryNamedBatterySlotTravelTogetherButStatesDoNot() {
        for (path in listOf("Pokemon.sav", "Pokemon.rtc", "Pokemon/saves/run 2/auto.sav", "Pokemon/saves/run 2/auto.rtc"))
            assertTrue(path, accepts("gbc-emu", "Pokemon.gbc", path))
        for (path in listOf("Pokemon.0A.gqs", "Pokemon/saves/run 2/auto.0A.gqs", "Other/saves/run 2/auto.sav", "Pokemon/saves/../auto.sav", "auto.sav"))
            assertFalse(path, accepts("gbc-emu", "Pokemon.gbc", path))
    }
    @Test fun unsupportedNamesAndUnknownSlotsCannotResolve() {
        for (name in listOf("Game.zip", "Game.7z", "Game", "../Game.gba", "Game.gba\u0000", ".gba"))
            assertNull(name, player("gba-emu").saveArchiveFilter("battery",reader(name)))
        assertNull(player("gba-emu").saveArchiveFilter("sav",reader("Game.gba")))
        assertTrue(accepts("gba-emu", "One.Two.GBA", "One.Two.sav"))
    }
}
