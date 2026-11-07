package app.foldcade.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class StatusChipCutoutTest {
    @Test fun anOldTileLeavingDoesNotRemoveTheNewTilesCutout() {
        val cutout = StatusChipCutout()
        val old = StatusChipSource { 1f }
        val next = StatusChipSource { 1f }
        cutout.show(old)
        cutout.show(next)
        cutout.hide(old)
        assertSame(next, cutout.source)
        cutout.hide(next)
        assertNull(cutout.source)
    }

    @Test fun aWiderPendingLabelKeepsTheSameRightEdgeAndCornerSeat() {
        // The Thor's 222 px tile and 62 px rounded corner, at Afterglow's half-outside seat.
        val badge = statusChipRect(Size(222f, 222f), IntSize(36, 36), 36f, .5f, 62f)
        val pending = statusChipRect(Size(222f, 222f), IntSize(150, 36), 36f, .5f, 62f)
        assertEquals(203.4f, badge.center.x, .001f)
        assertEquals(18.6f, badge.center.y, .001f)
        assertEquals(badge.right, pending.right, 0f)
        assertEquals(badge.top, pending.top, 0f)
        assertEquals(150f, pending.width, .001f)
    }
}
