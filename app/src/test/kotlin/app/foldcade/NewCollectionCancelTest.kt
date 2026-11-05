package app.foldcade

import app.foldcade.language.*
import org.junit.Assert.*
import org.junit.Test

/** B Cancel on a new collection's name leaves Home as it was (review-C 70 / 230). */
class NewCollectionCancelTest {
    private val a = HomeItem("a", "Alpha", HomeKind.Rom)
    private val b = HomeItem("b", "Beta", HomeKind.Rom)
    private fun home() = HomeSession(encodeHome(HomeBoard(slots = listOf(HOME_ALL, "a", "b", null))))
        .also { it.remerge(emptyList(), emptyList(), listOf(a, b), emptyList()) }

    @Test fun cancellingTheNameOfACollectionMadeForAGameLeavesTheBoardUnchanged() {
        val home = home()
        val before = home.board
        assertEquals(1, home.newCollection(1))
        assertNotNull(home.typing)
        assertNotEquals(before, home.board)
        val step = home.handle(Meaning.Back, 1)
        assertNull(home.typing)
        assertEquals(before, home.board)
        assertEquals(1, step?.focus?.cellIndex)
        assertNull(home.takeMade())
    }

    @Test fun cancellingOnAnEmptySlotOrTheAllTileLeavesTheBoardUnchangedAndFocusWhereItWas() {
        val home = home()
        val before = home.board
        assertEquals(3, home.newCollection(3))
        home.handle(Meaning.Back, 3)
        assertEquals(before, home.board)

        // From the All tile the collection took the first free slot; B brings focus back to All.
        assertEquals(3, home.newCollection(0))
        assertEquals(0, home.handle(Meaning.Back, 3)?.focus?.cellIndex)
        assertEquals(before, home.board)
    }

    @Test fun cancellingASelectionsNewCollectionPutsEveryGameBack() {
        val home = home()
        val before = home.board
        home.startSelection("a")
        home.handle(Meaning.Activate, 2)
        home.bulk(BulkAction.NewCollection, home.selectedIds(), null, 1)
        assertNotNull(home.typing)
        home.handle(Meaning.Back, 1)
        assertEquals(before.slots, home.board.slots)
        assertEquals(before.folders, home.board.folders)
    }

    @Test fun aScanWhileNamingKeepsItsGameAndCancelStillPutsTheSelectionBack() {
        val home = home()
        val before = home.board
        home.startSelection("a")
        home.handle(Meaning.Activate, 2)
        home.bulk(BulkAction.NewCollection, home.selectedIds(), null, 1)
        // A scan brings a new game while the name is typed.
        home.remerge(emptyList(), emptyList(), listOf(a, b, HomeItem("c", "Gamma", HomeKind.Rom)), emptyList())
        home.handle(Meaning.Back, 1)
        assertTrue(home.board.folders.values.none { it.collection })
        // Every pin is back in its own slot; the new game keeps the place the scan gave it.
        assertEquals(before.slots, home.board.slots.take(before.slots.size))
        assertTrue("c" in home.board.slots || home.board.folders.values.any { "c" in it.slots })
    }

    @Test fun aGameTheScanTookAwayWhileNamingDoesNotComeBackOnCancel() {
        val home = home()
        home.startSelection("a")
        home.handle(Meaning.Activate, 2)
        home.bulk(BulkAction.NewCollection, home.selectedIds(), null, 1)
        home.remerge(emptyList(), emptyList(), listOf(b), emptyList())
        home.handle(Meaning.Back, 1)
        assertTrue(home.board.folders.values.none { it.collection })
        assertFalse("a" in home.board.slots)
        assertTrue("b" in home.board.slots)
    }

    @Test fun savingTheNameStillKeepsTheCollection() {
        val home = home()
        home.newCollection(1)
        home.handle(Meaning.Done, 1)
        assertNull(home.typing)
        assertEquals(1, home.board.folders.values.count { it.collection })
        assertEquals("a", home.takeMade()?.gameId)
    }
}
