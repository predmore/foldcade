package app.foldcade.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherArtTest {
    @Test
    fun anAdaptiveLayersVisibleMiddleFillsTheBitmap() {
        // A 108dp layer shows its middle 72dp: half as big again as the bitmap, a quarter over each side.
        assertEquals(-96 to 480, bleedBounds(384))
        val (from, to) = bleedBounds(TILE_PX)
        assertEquals(TILE_PX * 1.5f, (to - from).toFloat(), 1f)
        assertEquals(TILE_PX / 2f, (from + to) / 2f, 0.5f)
    }
}
