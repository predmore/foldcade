package app.foldcade

import app.foldcade.language.Copy
import app.foldcade.plugins.gamenative.CatalogGame
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfHintTest {
    @After
    fun restoreShelf() {
        Shelf.gameNativeInstalled = false
        Shelf.catalog = emptyList()
    }

    @Test
    fun installedGameNativeWithNoShortcutsAsksForOne() {
        Shelf.gameNativeInstalled = true
        Shelf.catalog = emptyList()
        val pc = pcTile()
        assertTrue(pc.emptyShelfHint)
        assertEquals(Copy.addShortcutInGameNative, pc.shortText)
        assertEquals("PC", pc.title)
        assertFalse(pc.occupiesBothDisplays)
    }

    @Test
    fun aMissingInstallKeepsTheProgressSentence() {
        Shelf.gameNativeInstalled = false
        Shelf.catalog = emptyList()
        val pc = pcTile()
        assertFalse(pc.emptyShelfHint)
        assertEquals(Copy.progressInGameNative, pc.shortText)
    }

    @Test
    fun aStoredGameReplacesTheEmptyHint() {
        Shelf.gameNativeInstalled = true
        Shelf.catalog = listOf(
            ShelfGame(
                id = "gamenative.STEAM_730",
                title = "Counter-Strike",
                shortText = Copy.progressInGameNative,
                platformId = "pc",
                libraryId = "gamenative",
                remoteKey = "STEAM_730",
            ),
        )
        val pc = pcTile()
        assertFalse(pc.emptyShelfHint)
        assertEquals(Copy.progressInGameNative, pc.shortText)
        assertEquals("Counter-Strike", Shelf.games.last().title)
        assertEquals(CatalogGame("Counter-Strike", 730, "STEAM").remoteKey, Shelf.games.last().remoteKey)
    }

    private fun pcTile(): ShelfGame = Shelf.games.first { it.id == Shelf.PC_TILE }
}
