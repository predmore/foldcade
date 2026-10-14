package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class UndoMenuInvalidationTest {
    private val panel = SidePanel(Side.Left, HostScreen.Top, PanelLevel.Actions, 0, GridFocus())

    @Test fun invalidatingFocusedUndoMovesToTheNextRowWithoutAnEffect() {
        val closed = PickerModel(1, 3, tileActions = TileActions(undo = true, move = true, pin = true))
        val opened = retainMenuRows(closed, closed.copy(panel = panel))
        val edited = retainMenuRows(opened, opened.copy(tileActions = TileActions(move = true, pin = true)))
        assertEquals(listOf(Row.MoveTile, Row.PinTile), panelRows(edited.panel!!, edited))
        assertEquals(0, edited.panel!!.index)
        assertFalse(edited.arranging)
        assertNull(edited.hold)
    }

}
