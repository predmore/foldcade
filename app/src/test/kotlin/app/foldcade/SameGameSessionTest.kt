package app.foldcade

import app.foldcade.language.BulkAction
import app.foldcade.language.HOME_ALL
import app.foldcade.language.HomeBoard
import app.foldcade.language.HomeFolder
import app.foldcade.language.HomeItem
import app.foldcade.language.HomeKind
import app.foldcade.language.HomeUndo
import app.foldcade.language.HOME_ROOT
import app.foldcade.language.encodeHome
import app.foldcade.language.tidy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A copy named before it folded acts on its tile. */
class SameGameSessionTest {
    private val local = HomeItem("lib:local-folder:rf4", "RF4", HomeKind.Rom)
    private val server = HomeItem("lib:romm:31", "RF4", HomeKind.Rom)
    private val tile = HomeItem("same:nintendo-3ds:rf4.cci", "RF4", HomeKind.Rom)
    private val other = HomeItem("lib:romm:7", "Mario", HomeKind.Rom)
    private val aliases = mapOf(local.id to tile.id, server.id to tile.id)

    private fun home() = HomeSession(encodeHome(HomeBoard(slots = listOf(HOME_ALL, local.id, other.id))))
        .also { it.remerge(emptyList(), emptyList(), listOf(local, other), emptyList()) }

    @Test
    fun aBulkHideOfACopyThatFoldedMeanwhileHidesItsTile() {
        val home = home()
        home.startSelection(local.id)
        // RomM lists its copy while the selection is open. The merge waits for it.
        home.remerge(emptyList(), emptyList(), listOf(tile, other), emptyList(), sameGames = aliases)
        assertEquals(listOf(local.id), home.selectedIds())
        home.bulk(BulkAction.Hide, home.selectedIds(), null, 1)
        home.remerge(emptyList(), emptyList(), listOf(tile, other), emptyList(), sameGames = aliases)
        assertTrue(tile.id in home.board.hidden)
        assertFalse(tile.id in home.board.slots)
    }

    @Test
    fun aMenuThatKeptACopyActsOnItsTile() {
        val quests = HomeFolder("home.user.1", "Quests", userMade = true)
        val home = HomeSession(encodeHome(HomeBoard(slots = listOf(HOME_ALL, local.id, other.id, quests.id), folders = mapOf(quests.id to quests))))
            .also { it.remerge(emptyList(), emptyList(), listOf(local, other), emptyList()) }
        home.keepMenu(local.id)
        home.remerge(emptyList(), emptyList(), listOf(tile, other), emptyList(), sameGames = aliases)
        home.toggleCollection(0, quests.id)
        assertEquals(listOf(tile.id), home.board.folders.getValue(quests.id).slots)
    }

    @Test
    fun aMenuOpenOnAPinnedCopyStillSeesTheTileAsPinned() {
        val home = home()
        home.keepMenu(local.id)
        home.remerge(emptyList(), emptyList(), listOf(tile, other), emptyList(), sameGames = aliases)
        assertEquals(true, home.actions(1)?.collections?.single { it.folderId == HOME_ROOT }?.member)
        home.toggleCollection(1, HOME_ROOT)
        assertFalse(home.board.slots.contains(tile.id))
    }

    @Test
    fun anUndoTakenBeforeACopyFoldedPutsTheTileBack() {
        val before = HomeBoard(slots = listOf(HOME_ALL, null, local.id, other.id))
        val tidied = tidy(before)
        val undo = HomeUndo(before, "Tidied", 0L, after = tidied)
        val folded = app.foldcade.language.adoptSameGames(tidied, aliases)
        val restored = undo.adopt(aliases).restore(folded)
        assertEquals(listOf(HOME_ALL, null, tile.id, other.id), restored.slots)
    }
}
