package app.foldcade

import app.foldcade.language.*
import org.junit.Assert.*
import org.junit.Test

/**
 * A new collection is only named until Done makes it, so B Cancel leaves Home as it was, even
 * after a scan (review-C 70 / 230, the review bot on #388), and Done makes it as the board is then.
 */
class NewCollectionCancelTest {
    private val a = HomeItem("a", "Alpha", HomeKind.Rom)
    private val b = HomeItem("b", "Beta", HomeKind.Rom)
    private val notes = HomeItem(androidHomeId("notes"), "Notes", HomeKind.AndroidApp)
    private fun home() = HomeSession(encodeHome(HomeBoard(slots = listOf(HOME_ALL, "a", "b", null))))
        .also { it.remerge(emptyList(), emptyList(), listOf(a, b), emptyList()) }

    @Test fun cancellingTheNameOfACollectionForAGameLeavesTheBoardUnchanged() {
        val home = home()
        val before = home.board
        assertEquals(1, home.newCollection(1))
        assertNotNull(home.typing)
        // Nothing is made while the name is typed; the top screen shows a preview of it.
        assertEquals(before, home.board)
        assertEquals(listOf("a"), home.namingPreview?.slots)
        assertEquals("${Copy.collection} 1", home.namingPreview?.name)
        val step = home.handle(Meaning.Back, 1)
        assertNull(home.typing)
        assertNull(home.namingPreview)
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
        assertEquals(0, home.newCollection(0))
        assertEquals(0, home.handle(Meaning.Back, 0)?.focus?.cellIndex)
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
        // Every pin is where it was; the new game keeps the place the scan gave it.
        assertEquals(before.slots.take(3), home.board.slots.take(3))
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

    @Test fun anAppPinStaysWhereItWasWhenASelectionsNameIsCancelledAfterAScan() {
        val home = HomeSession(encodeHome(HomeBoard(slots = listOf(HOME_ALL, notes.id, "a", null))))
            .also { it.remerge(emptyList(), listOf(notes), listOf(a, b), emptyList()) }
        val before = home.board.slots
        home.startSelection(notes.id)
        home.handle(Meaning.Activate, 2)
        home.bulk(BulkAction.NewCollection, home.selectedIds(), null, 1)
        home.remerge(emptyList(), listOf(notes), listOf(a, b, HomeItem("c", "Gamma", HomeKind.Rom)), emptyList())
        home.handle(Meaning.Back, 1)
        assertEquals(before, home.board.slots.take(before.size))
    }

    @Test fun savingTheNameMakesTheCollectionOnceAndAddToComesBackTicked() {
        val home = home()
        home.newCollection(1)
        home.handle(Meaning.Done, 1)
        assertNull(home.typing)
        assertEquals(1, home.board.folders.values.count { it.collection })
        assertEquals("a", home.takeMade()?.gameId)
        // B after a Done still makes the next one "Collection 2", and a cancelled one is never counted.
        home.newCollection(2)
        home.handle(Meaning.Back, 2)
        home.newCollection(2)
        assertEquals("${Copy.collection} 2", home.namingPreview?.name)
    }

    @Test fun doneFromTheAllTileMakesItInTheFirstFreeSlotAndFocusGoesThere() {
        val home = home()
        home.newCollection(0)
        val step = home.handle(Meaning.Done, 0)
        val made = home.board.folders.values.single { it.collection }
        assertEquals(3, home.board.slots.indexOf(made.id))
        assertEquals(3, step?.focus?.cellIndex)
    }

    @Test fun doneAfterAScanMakesTheCollectionAsTheBoardIsNow() {
        val home = home()
        home.startSelection("a")
        home.handle(Meaning.Activate, 2)
        home.bulk(BulkAction.NewCollection, home.selectedIds(), null, 1)
        // B went away while the name was typed: the collection holds only A.
        home.remerge(emptyList(), emptyList(), listOf(a), emptyList())
        home.handle(Meaning.Done, 1)
        assertEquals(listOf("a"), home.board.folders.values.single { it.collection }.slots.filterNotNull())
    }

    @Test fun doneForAGameAScanTookAwayMakesNothing() {
        val home = home()
        home.newCollection(1)
        home.remerge(emptyList(), emptyList(), listOf(b), emptyList())
        home.handle(Meaning.Done, 1)
        assertTrue(home.board.folders.values.none { it.collection })
        assertNull(home.takeMade())
    }

    @Test fun doneOnASlotAScanFilledPutsFocusOnTheCollectionWhereverItWent() {
        val home = home()
        home.newCollection(3)
        // A scan takes A away and brings two games: they fill slot 1 and the slot naming began on.
        home.remerge(emptyList(), emptyList(), listOf(b, HomeItem("c", "Gamma", HomeKind.Rom), HomeItem("d", "Delta", HomeKind.Rom)), emptyList())
        val step = home.handle(Meaning.Done, 3)
        val made = home.board.folders.values.single { it.collection }
        assertEquals(home.board.slots.indexOf(made.id), step?.focus?.cellIndex)
    }
}
