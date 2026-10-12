package app.foldcade.plugins.droiddeck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DroidDeckShortcutsTest {
    @Test
    fun enabledPinnedGameShortcutsAreGames() {
        val games = readDroidDeckShortcuts(
            listOf(
                shortcut("game:1145350", short = "Hades II", long = "Hades II"),
                shortcut("game:18446744069448138752", short = "A Very Long Name That DroidDeck Cut At Fo", long = "A Very Long Name That DroidDeck Cut At Forty"),
                shortcut("game:620", short = "Portal 2", long = null),
                shortcut("game:400", short = "Portal", long = "Portal", pinned = false),
                shortcut("game:70", short = "Half-Life", long = "Half-Life", enabled = false),
                shortcut("settings", short = "Settings", long = "Settings"),
                shortcut("game:1145350", short = "Again", long = "Again"),
            ),
        )
        assertEquals(
            listOf(
                DroidDeckGame("1145350", "Hades II"),
                DroidDeckGame("18446744069448138752", "A Very Long Name That DroidDeck Cut At Forty"),
                DroidDeckGame("620", "Portal 2"),
            ),
            games,
        )
    }

    @Test
    fun aShortcutWithNoLabelIsNamedByItsId() {
        assertEquals(listOf(DroidDeckGame("620", "620")), readDroidDeckShortcuts(listOf(shortcut("game:620", null, " "))))
    }

    @Test
    fun onlyACanonicalGameIdIsAKey() {
        assertEquals("620", droidDeckShortcutKey("game:620"))
        assertNull(droidDeckShortcutKey("game:0620"))
        assertNull(droidDeckShortcutKey("game: 620"))
        assertNull(droidDeckShortcutKey("game:"))
        assertNull(droidDeckShortcutKey("620"))
        assertNull(droidDeckShortcutKey("game:18446744073709551616"))
    }

    @Test
    fun unpinningKeepsEveryOtherShortcut() {
        assertEquals(
            listOf("game:620", "settings"),
            droidDeckPinsWithout(listOf("game:1145350", "game:620", "settings"), "1145350"),
        )
    }

    private fun shortcut(id: String, short: String?, long: String?, pinned: Boolean = true, enabled: Boolean = true) =
        DroidDeckShortcut(id = id, shortLabel = short, longLabel = long, pinned = pinned, enabled = enabled)
}
