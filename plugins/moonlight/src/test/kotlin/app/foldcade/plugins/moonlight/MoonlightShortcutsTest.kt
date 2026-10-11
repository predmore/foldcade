package app.foldcade.plugins.moonlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonlightShortcutsTest {
    private val host = "F81D4FAE-7DEC-11D0-A765-00A0C91E6BF6"
    private val key = host.lowercase()

    private fun computer(name: String?) = MoonlightShortcut(id = host, label = name, pinned = false, enabled = true)

    private fun game(appId: String, label: String?, pinned: Boolean = true, enabled: Boolean = true) =
        MoonlightShortcut(id = host + appId, label = label, pinned = pinned, enabled = enabled)

    @Test
    fun aGameShortcutIdIsTheHostUuidThenTheAppId() {
        val read = readMoonlightShortcuts(listOf(game("881448767", "Desktop (Gaming PC)")))
        val app = read.games.single()
        assertEquals(host, app.hostUuid)
        assertEquals("881448767", app.appId)
        assertEquals("Desktop", app.label)
        assertEquals("$key|881448767", app.remoteKey)
        assertEquals("$key|881448767", moonlightShortcutKey(host + "881448767"))
    }

    @Test
    fun theComputerShortcutNamesTheHost() {
        val read = readMoonlightShortcuts(listOf(computer("Gaming PC (Main)"), game("1", "Celeste (Gaming PC (Main))")))
        assertEquals(mapOf(key to "Gaming PC (Main)"), read.hostNames)
        assertEquals("Celeste", read.games.single().label)
    }

    @Test
    fun withoutAComputerShortcutTheLastParenthesizedGroupIsTheHost() {
        val read = readMoonlightShortcuts(listOf(game("2", "Halo (2003) (Gaming PC)")))
        assertEquals("Halo (2003)", read.games.single().label)
        assertEquals(mapOf(key to "Gaming PC"), read.hostNames)
    }

    @Test
    fun aLabelThatDoesNotEndInTheHostIsKeptWhole() {
        val read = readMoonlightShortcuts(listOf(computer("Gaming PC"), game("3", "Celeste")))
        assertEquals("Celeste", read.games.single().label)
        val unnamed = readMoonlightShortcuts(listOf(game("4", null)))
        assertEquals("4", unnamed.games.single().label)
    }

    @Test
    fun onlyEnabledPinnedGameShortcutsAreGames() {
        val read = readMoonlightShortcuts(
            listOf(
                computer("Gaming PC"),
                game("5", "Dynamic (Gaming PC)", pinned = false),
                game("6", "Removed (Gaming PC)", enabled = false),
                game("7", "Kept (Gaming PC)"),
                game("7", "Kept again (Gaming PC)"),
            ),
        )
        assertEquals(listOf("Kept"), read.games.map { it.label })
    }

    @Test
    fun anIdThatIsNotAUuidAndAnAppIdIsNotAGame() {
        assertNull(moonlightShortcutKey(host))
        assertNull(moonlightShortcutKey(host + "abc"))
        assertNull(moonlightShortcutKey("not-a-uuid-at-all-not-a-uuid-at-all-123"))
        assertNull(moonlightShortcutKey("1-1-1-1-1123"))
        val read = readMoonlightShortcuts(listOf(MoonlightShortcut("content://x", "X", pinned = true, enabled = true)))
        assertTrue(read.games.isEmpty())
    }

    @Test
    fun removingAGameKeepsEveryOtherPin() {
        val pinned = listOf(host + "7", host.lowercase() + "8", host, "manifest-shortcut")
        assertEquals(listOf(host.lowercase() + "8", host, "manifest-shortcut"), pinsWithout(pinned, "$key|7"))
        assertEquals(listOf(host + "7", host, "manifest-shortcut"), pinsWithout(pinned, "$key|8"))
        assertEquals(pinned, pinsWithout(pinned, "$key|9"))
    }

    @Test
    fun aMoonlightTileIdIsALowercaseHostABarAndAnAppId() {
        assertTrue(isMoonlightRemoteKey("$key|881448767"))
        assertFalse(isMoonlightRemoteKey("$host|881448767"))
        assertFalse(isMoonlightRemoteKey("$key|"))
        assertFalse(isMoonlightRemoteKey("$key|abc"))
        assertFalse(isMoonlightRemoteKey("moonlight"))
        assertFalse(isMoonlightRemoteKey("lib:romm:12"))
        assertFalse(isMoonlightRemoteKey("gamenative.730"))
    }
}
