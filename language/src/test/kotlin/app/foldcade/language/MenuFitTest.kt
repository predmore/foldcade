package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class MenuFitTest {
    @Test fun shortMenusKeepTheirHeight() {
        assertEquals(MenuFit(List(3) { 52 }, false), menuFit(3, 360, 1f, 3))
        assertEquals(MenuFit(emptyList(), false), menuFit(0, 0, 1f))
    }
    @Test fun commonGameMenusFitWithoutScrolling() {
        for (rows in 7..8) {
            val fit = menuFit(rows, 350, 1f, 3)
            assertTrue(fit.rowHeights.all { it in 40..52 })
            assertFalse(fit.scrolls)
            assertEquals(350, fit.rowHeights.sum() + (rows - 1) * 3)
            assertTrue(fit.rowHeights.max() - fit.rowHeights.min() <= 1)
        }
    }
    @Test fun eightRowsShareTheThorPixelRemainderWithoutOverflow() {
        val fit = menuFit(8, 837, 2.25f)
        assertEquals(listOf(105, 105, 105, 105, 105, 104, 104, 104), fit.rowHeights)
        assertEquals(837, fit.rowHeights.sum())
        assertFalse(fit.scrolls)
        // Six-pixel gaps are also paid from the rounded viewport, not fractional dp.
        val spaced = menuFit(8, 837, 2.25f, 6)
        assertEquals(837, spaced.rowHeights.sum() + 7 * 6)
        assertFalse(spaced.scrolls)
    }
    @Test fun longMenusScrollOnlyAtMinimumHeight() {
        assertEquals(MenuFit(List(20) { 40 }, true), menuFit(20, 350, 1f, 3))
        assertEquals(MenuFit(List(8) { 40 }, false), menuFit(8, 320, 1f))
        assertEquals(MenuFit(listOf(40), true), menuFit(1, 20, 1f))
        assertEquals(MenuFit(List(8) { 90 }, false), menuFit(8, 720, 2.25f))
        assertEquals(MenuFit(List(8) { 90 }, true), menuFit(8, 719, 2.25f))
    }
    @Test fun scrollingKeepsNeighborContextAndClampsAtEnds() {
        assertEquals(70f, menuScrollTarget(200f, 240f, 160f, 280f, 220f, 0f, 600f, 10f), 0.01f)
        assertEquals(150f, menuScrollTarget(200f, 240f, 160f, 280f, 220f, 300f, 600f, 10f), 0.01f)
        assertEquals(0f, menuScrollTarget(0f, 40f, 0f, 80f, 220f, 50f, 600f, 10f), 0.01f)
        assertEquals(590f, menuScrollTarget(760f, 800f, 720f, 800f, 220f, 500f, 600f, 10f), 0.01f)
    }
    @Test fun narrowViewportsStillKeepOneNeighborWhenPossible() {
        assertEquals(190f, menuScrollTarget(200f, 240f, 160f, 280f, 100f, 0f, 600f, 10f), 0.01f)
        assertEquals(150f, menuScrollTarget(200f, 240f, 160f, 240f, 100f, 300f, 600f, 10f), 0.01f)
    }
    @Test fun androidGameArtIsATileAndMoveToLeadsTheRows() {
        val tile = TileActions(move = true, select = true, collections = emptyList(),
            art = true, remove = RemoveVerb.Unpin, hide = true, moveTo = HOME_ANDROID_APPS)
        val rows = actionRows(tile)
        assertEquals(listOf(Row.MoveTile, Row.AddToCollection, Row.ChooseArt, Row.MoveApp,
            Row.SelectTile, Row.RemoveTile, Row.Hide), rows)
        assertEquals(0..2, tileBlock(rows))
        assertEquals(1, rows.count { it == Row.ChooseArt })
    }
    @Test fun hiddenItemsShowArtAsATileAndShowUnderIt() {
        val rows = actionRows(TileActions(show = true, art = true))
        assertEquals(listOf(Row.ChooseArt, Row.ShowHidden), rows)
        assertEquals(0..0, tileBlock(rows))
    }
    @Test fun gameRowsPutFilingBeforeSelectionAndKeepUndoFirst() {
        val rows = actionRows(TileActions(undo = true, move = true, select = true,
            collections = emptyList(), art = true,
            remove = RemoveVerb.Unpin, hide = true, removeDownload = true, deleteShortcut = true))
        assertEquals(listOf(Row.Undo, Row.MoveTile, Row.AddToCollection,
            Row.ChooseArt, Row.SelectTile, Row.RemoveTile, Row.Hide, Row.RemoveDownload, Row.DeleteShortcut), rows)
        assertEquals(Row.MoveTile, rows[chordIndex(rows, Meaning.Options)!!])
        assertEquals(Row.RemoveTile, rows[chordIndex(rows, Meaning.ActivateBottom)!!])
    }
}
