package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class MenuFitTest {
    @Test fun shortMenusKeepTheirHeight() {
        assertEquals(MenuFit(52f, false), menuFit(3, 360f, 3f))
        assertEquals(MenuFit(52f, false), menuFit(0, 0f))
    }
    @Test fun commonGameMenusFitWithoutScrolling() {
        for (rows in 7..8) {
            val fit = menuFit(rows, 350f, 3f)
            assertTrue(fit.rowHeight in 40f..52f)
            assertFalse(fit.scrolls)
            assertTrue(rows * fit.rowHeight + (rows - 1) * 3f <= 350.01f)
        }
    }
    @Test fun longMenusScrollOnlyAtMinimumHeight() {
        assertEquals(MenuFit(40f, true), menuFit(20, 350f, 3f))
        assertEquals(MenuFit(40f, false), menuFit(8, 320f))
        assertEquals(MenuFit(40f, true), menuFit(1, 20f))
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
    @Test fun gameRowsPutFilingBeforeSelectionAndKeepUndoFirst() {
        val rows = actionRows(TileActions(undo = true, move = true, select = true,
            collections = emptyList(), favorite = false, art = true, pin = true,
            remove = RemoveVerb.Hide, removeDownload = true, deleteShortcut = true), null)
        assertEquals(listOf(Row.Undo, Row.MoveTile, Row.AddToCollection, Row.Favorite,
            Row.ChooseArt, Row.PinTile, Row.SelectTile, Row.RemoveTile, Row.RemoveDownload, Row.DeleteShortcut), rows)
        assertEquals(Row.MoveTile, rows[chordIndex(rows, Meaning.Options)!!])
        assertEquals(Row.RemoveTile, rows[chordIndex(rows, Meaning.ActivateBottom)!!])
    }
}
