package app.foldcade

import app.foldcade.language.HOME_ALL
import app.foldcade.language.HomeBoard
import app.foldcade.language.HomeFolder
import app.foldcade.language.HomeItem
import app.foldcade.language.HomeKind
import app.foldcade.language.HomePlatform
import app.foldcade.language.Meaning
import app.foldcade.language.encodeHome
import app.foldcade.language.platformFolderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A library that reports while a tile is in the hand changes Home only once the tile is down. */
class HomeMergeDuringMoveTest {
    private val platforms = listOf(
        HomePlatform("nintendo-3ds", "Nintendo 3DS", setOf("3ds")),
        HomePlatform("nintendo-ds", "Nintendo DS", setOf("nds")),
    )
    private val threeDs = platformFolderId("nintendo-3ds")
    private val ds = platformFolderId("nintendo-ds")
    private val quests = "home.user.1"
    private val zelda = rom("lib:local:zelda", "3ds")
    private val mario = rom("lib:local:mario", "3ds")
    private val kirby = rom("lib:romm:kirby", "nds")

    /** All, Quests (Zelda), the 3DS folder. */
    private fun session(): HomeSession {
        val board = HomeBoard(
            slots = listOf(HOME_ALL, quests, threeDs),
            folders = mapOf(
                quests to HomeFolder(quests, "Quests", userMade = true, slots = listOf(zelda.id)),
                threeDs to HomeFolder(threeDs, "Nintendo 3DS", platformId = "nintendo-3ds", slots = listOf(zelda.id, mario.id)),
            ),
            recentAfter = Long.MAX_VALUE,
        )
        return HomeSession(encodeHome(board)).also { it.remerge(emptyList(), emptyList(), listOf(zelda, mario), platforms) }
    }

    @Test
    fun aFolderALibraryAddsMidCarryStaysOnHomeAfterTheDrop() {
        val home = session()
        home.handle(Meaning.Activate, 1)
        assertEquals(quests, home.board.openFolderId)
        home.startMove(0)
        home.handle(Meaning.MoveUp, 0)
        home.handle(Meaning.Activate, 0)
        assertNull(home.board.openFolderId)
        assertNotNull(home.board.hold)
        // A library still loading finishes now, with a game for a new folder.
        home.remerge(emptyList(), emptyList(), listOf(zelda, mario, kirby), platforms)
        home.handle(Meaning.MoveRight, home.board.hold!!.index)
        home.handle(Meaning.Activate, home.board.hold!!.index)
        assertNull(home.board.hold)
        assertTrue(ds in home.board.slots)
        assertEquals(listOf(kirby.id), home.board.folders.getValue(ds).slots)
        assertTrue(zelda.id in home.board.slots)
        assertFalse(zelda.id in home.board.folders.getValue(quests).slots)
    }

    @Test
    fun aFolderALibraryAddsMidMoveStaysOnHomeAfterTheDropOrACancel() {
        for (end in listOf(Meaning.Activate, Meaning.CancelHold)) {
            val home = session()
            home.startMove(1)
            home.remerge(emptyList(), emptyList(), listOf(zelda, mario, kirby), platforms)
            home.handle(Meaning.MoveRight, 1)
            home.handle(end, home.board.hold!!.index)
            assertNull(home.board.hold)
            assertTrue("$end", ds in home.board.slots)
            assertEquals(listOf(kirby.id), home.board.folders.getValue(ds).slots)
            assertTrue(quests in home.board.slots && threeDs in home.board.slots)
        }
    }

    @Test
    fun aGameItsSourceDropsMidCarryIsGoneOnceTheTileIsDown() {
        for (end in listOf(Meaning.Activate, Meaning.CancelHold)) {
            val home = session()
            home.handle(Meaning.Activate, 1)
            home.startMove(0)
            home.handle(Meaning.MoveUp, 0)
            home.handle(Meaning.Activate, 0)
            // The source stops listing Zelda while it is in the hand. The carry goes on undisturbed.
            home.remerge(emptyList(), emptyList(), listOf(mario), platforms)
            assertEquals(zelda.id, home.carry()?.id)
            home.handle(end, home.board.hold!!.index)
            assertNull(home.board.hold)
            val board = home.board
            assertFalse("$end", zelda.id in board.slots)
            assertTrue(board.folders.values.none { zelda.id in it.slots })
            assertEquals(listOf(mario.id), board.folders.getValue(threeDs).slots.filterNotNull())
        }
    }

    @Test
    fun tidyKeepsAFolderALibraryAddsRightAfter() {
        val home = session()
        home.startMove(2)
        repeat(4) { home.handle(Meaning.MoveRight, home.board.hold!!.index) }
        home.handle(Meaning.Activate, home.board.hold!!.index)
        val focus = home.tidyBoard(2)
        home.remerge(emptyList(), emptyList(), listOf(zelda, mario, kirby), platforms)
        assertEquals(listOf(HOME_ALL, quests, threeDs, ds), home.board.slots)
        assertEquals(2, focus)
    }

    private fun rom(id: String, platform: String): HomeItem =
        HomeItem(id = id, title = id.substringAfterLast(':'), kind = HomeKind.Rom, platformId = platform)
}
