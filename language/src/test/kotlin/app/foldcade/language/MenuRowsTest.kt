package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class MenuRowsTest {
    private val panel = SidePanel(Side.Left, HostScreen.Top, PanelLevel.Actions, 0, GridFocus())

    @Test fun catalogChangesKeepTheSelectedRowUntilClose() {
        val closed = PickerModel(1, 3, tileActions = TileActions(move = true, hide = true, removeDownload = true))
        val opened = retainMenuRows(closed, closed.copy(panel = panel.copy(index = 1)))
        val rows = panelRows(opened.panel!!, opened)
        val refreshed = retainMenuRows(opened, opened.copy(tileActions = TileActions(move = true, art = true)))
        assertEquals(rows, panelRows(refreshed.panel!!, refreshed))
        assertEquals(Row.Hide, panelRows(refreshed.panel!!, refreshed)[refreshed.panel!!.index])
        val dismissed = retainMenuRows(refreshed, refreshed.copy(panel = null))
        val reopened = retainMenuRows(dismissed, dismissed.copy(panel = panel))
        assertEquals(listOf(Row.MoveTile, Row.ChooseArt), panelRows(reopened.panel!!, reopened))
    }

    @Test fun collectionCheckmarksStayInPlaceWhileInvalidUndoLeavesTheParentPage() {
        val closed = PickerModel(1, 3, tileActions = TileActions(undo = true,
            collections = listOf(CollectionChoice("one", "One", false))))
        val opened = retainMenuRows(closed, closed.copy(panel = panel))
        val picker = retainMenuRows(opened, opened.copy(panel = panel.copy(level = PanelLevel.Collections)))
        val rows = panelRows(picker.panel!!, picker)
        val updated = retainMenuRows(picker, picker.copy(tileActions = TileActions(collections = listOf(
            CollectionChoice("one", "One", true), CollectionChoice("two", "Two", false)))))
        assertEquals(rows, panelRows(updated.panel!!, updated))
        assertTrue(updated.tileActions!!.collections!!.first().member)
        val parent = retainMenuRows(updated, updated.copy(panel = panel))
        assertEquals(listOf(Row.AddToCollection), panelRows(parent.panel!!, parent))
    }

    @Test fun aKeptGameLosesOnlySlotActionsAndKeepsItsSelectedRow() {
        val closed = PickerModel(1, 3, tileActions = TileActions(move = true,
            collections = emptyList(), remove = RemoveVerb.Remove, hide = true, removeDownload = true))
        val opened = retainMenuRows(closed, closed.copy(panel = panel.copy(index = 1)))
        val changed = opened.copy(tileActions = TileActions(kept = true,
            collections = emptyList(), hide = true, removeDownload = true))
        val kept = retainMenuRows(opened, changed)
        val rows = panelRows(kept.panel!!, kept)
        // Hide still acts on the kept game, by its id, and takes no chord from Delete download.
        assertEquals(listOf(Row.AddToCollection, Row.Hide, Row.RemoveDownload), rows)
        assertEquals(Row.AddToCollection, rows[kept.panel!!.index])
        assertNull(chordIndex(rows, Meaning.Options))
        assertEquals(Row.RemoveDownload, rows[chordIndex(rows, Meaning.ActivateBottom)!!])
        val restored = retainMenuRows(kept, kept.copy(tileActions = closed.tileActions))
        assertEquals(rows, panelRows(restored.panel!!, restored))
    }

    @Test fun overlayRowsDependOnTheirTargetRatherThanTheCoveredViewsCount() {
        val target = PickerModel(1, 3, panel = panel, tileActions = TileActions(showOnHome = "home.user.1"))
        for (count in listOf(0, 1, 20)) {
            assertEquals(listOf(Row.ShowOnHome), panelRows(panel, target.copy(count = count)))
        }
    }

    @Test fun everyEmptyViewCanOpenJustUndo() {
        for (grid in HomeGrid.entries) {
            val empty = PickerModel(0, 3, homeGrid = grid, atLibraryRoot = false,
                tileActions = TileActions(undo = true, move = true))
            val (opened, effect) = reduce(empty, Meaning.Options, HostScreen.Bottom)
            assertNull(effect)
            assertNotNull(opened.panel)
            assertEquals(listOf(Row.Undo), panelRows(opened.panel!!, opened))
        }
    }

    @Test fun aMenuThatOfferedUndoJustBeforeTheDeadlineKeepsItAtTheBoundary() {
        val undo = HomeUndo(HomeBoard(), Copy.hidden, 100L)
        assertTrue(undo.available(8_099L))
        val paused = undo.pause(8_100L)
        assertTrue(paused.available(20_000L))
        assertFalse(paused.resume(20_000L).available(20_000L))
    }

    @Test fun pausedUndoResumesWithExactlyItsRemainingTime() {
        val undo = HomeUndo(HomeBoard(), Copy.hidden, 100L)
        val paused = undo.pause(7_100L)
        assertTrue(paused.available(100_000L))
        assertEquals(paused, paused.pause(100_000L))
        val resumed = paused.resume(100_000L)
        assertTrue(resumed.available(100_999L))
        assertFalse(resumed.available(101_000L))
        assertEquals(resumed, resumed.resume(101_000L))
    }
}
