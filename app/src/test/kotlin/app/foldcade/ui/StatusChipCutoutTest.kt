package app.foldcade.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class StatusChipCutoutTest {
    @Test fun matchingFocusScalesKeepTheOriginalSeat() {
        val tile = Size(222f, 222f)
        val chip = statusChipRect(tile, IntSize(36, 36), 36f, .5f, 62f)
        for (focus in listOf(1f, 1.05f, 1.4f)) {
            val matrix = statusChipMatrix(tile, focus, 0f, focus, Matrix())
            assertEquals(chip.topLeft.x, matrix.map(chip.topLeft).x, .001f)
            assertEquals(chip.topLeft.y, matrix.map(chip.topLeft).y, .001f)
            assertEquals(chip.bottomRight.x, matrix.map(chip.bottomRight).x, .001f)
            assertEquals(chip.bottomRight.y, matrix.map(chip.bottomRight).y, .001f)
        }
    }

    @Test fun aCheckedTilesCutFollowsItsExtraScaleAndLift() {
        val tile = Size(222f, 222f)
        val focus = 1.05f
        val chip = statusChipRect(tile, IntSize(36, 36), 36f, .5f, 62f)
        val selectedScale = focus * 1.02f
        val lift = -222f * .025f
        val cut = statusChipMatrix(tile, selectedScale, lift, focus, Matrix()).map(chip.center)
        assertEquals(205.248f, cut.x, .001f)
        assertEquals(11.466286f, cut.y, .001f)
        // Applying the ring's layer must put the hole back on the actual chip on screen.
        val center = Offset(111f, 111f)
        val screenCut = center + (cut - center) * focus
        val screenChip = center + (chip.center - center) * selectedScale + Offset(0f, lift)
        assertEquals(screenChip.x, screenCut.x, .001f)
        assertEquals(screenChip.y, screenCut.y, .001f)
    }

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
