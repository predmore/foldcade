package app.foldcade

import app.foldcade.language.*
import org.junit.Assert.*
import org.junit.Test

class HomeSelectionSessionTest {
    private val group = HomeFolder("home.user.1", "Quests", userMade = true, slots = listOf("b", null, "a"))
    private val a = HomeItem("a", "Alpha", HomeKind.Rom)
    private val b = HomeItem("b", "Beta", HomeKind.Rom)
    private val c = HomeItem("c", "Gamma", HomeKind.Rom)
    private fun home() = HomeSession(encodeHome(HomeBoard(slots = listOf(HOME_ALL, "a", "b", null, group.id),
        folders = mapOf(group.id to group)))).also { it.remerge(emptyList(), emptyList(), listOf(a, b), emptyList()) }

    @Test fun selectionInCollectionsAndAllUsesThatViewsOrderAndFindHasNoSelect() {
        val home = home()
        assertFalse(home.actions(4)!!.select)
        home.handle(Meaning.Activate, 4)
        assertTrue(home.actions(0)!!.select)
        home.startSelection("a")
        home.handle(Meaning.Activate, 0)
        assertEquals(listOf("b", "a"), home.selectedIds())
        home.showAll()
        assertNull(home.selection)
        assertTrue(home.actions(0)!!.select)
        home.startSelection("b")
        home.handle(Meaning.Activate, 0)
        assertEquals(listOf("a", "b"), home.selectedIds())
        assertFalse(home.findActions("a").select)
        home.showRoot()
        assertNull(home.selection)
    }

    @Test fun aCatalogRemovalAndArrivalWaitUntilBDone() {
        val home = home()
        home.startSelection("a")
        val before = home.faces()
        home.remerge(emptyList(), emptyList(), listOf(b, c), emptyList())
        assertEquals(before, home.faces())
        assertEquals(listOf("a"), home.selectedIds())
        home.handle(Meaning.Back, 1)
        assertNull(home.selection)
        assertFalse("a" in home.board.slots)
        assertTrue("c" in home.board.slots)
    }

    @Test fun addToHomeIsCheckedOnceEveryGameIsPinnedAndAddsTheRest() {
        val home = home()
        home.startSelection("a")
        home.handle(Meaning.Activate, 2)
        assertEquals(CollectionChoice(HOME_ROOT, Copy.home, member = true), home.actions(2)!!.collections!!.first())
        assertTrue(home.actions(2)!!.hide)
        home.finishSelection()
        home.hide("b")
        home.showAll()
        home.startSelection("b")
        assertEquals(CollectionChoice(HOME_ROOT, Copy.home, member = false), home.actions(1)!!.collections!!.first())
        home.bulk(BulkAction.Collection, home.selectedIds(), HOME_ROOT, 1)
        assertTrue("b" in home.board.slots)
        assertFalse("b" in home.board.hidden)
    }
}
