package app.foldcade.ui

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import app.foldcade.language.SceneLive
import org.junit.Assert.*
import org.junit.Test

class BackdropInputsTest {
    @Test fun heldInputsKeepTheLastDrawWithoutReadingLiveState() {
        val scroll = mutableFloatStateOf(12f)
        val inputs = BackdropInputs()
        val live = { SceneLive(scroll = scroll.floatValue) }
        assertEquals(12f, inputs.read(false, live).scroll)
        scroll.floatValue = 99f
        var reads = 0
        Snapshot.observe({ reads++ }, null) {
            assertEquals(12f, inputs.read(true, live).scroll)
        }
        assertEquals(0, reads)
        assertEquals(99f, inputs.read(false, live).scroll)
    }

    @Test fun InitiallyHeldSceneTakesOneUnobservedSnapshot() {
        val scroll = mutableFloatStateOf(12f)
        val inputs = BackdropInputs()
        var reads = 0
        Snapshot.observe({ reads++ }, null) {
            assertEquals(12f, inputs.read(true) { SceneLive(scroll = scroll.floatValue) }.scroll)
        }
        scroll.floatValue = 25f
        assertEquals(12f, inputs.read(true) { error("A held frame must not read live inputs") }.scroll)
        assertEquals(0, reads)
    }

    @Test fun FadingArtHoldsTheClockButOnlyOpaqueArtCanHideTheCanvas() {
        val cover = BackdropCover()
        val opaque = mutableFloatStateOf(0f)
        val first = { opaque.floatValue >= 1f }
        val second = { true }
        cover.add(first)
        assertTrue(cover.covered)
        assertFalse(cover.occluded)
        opaque.floatValue = 1f
        assertTrue(cover.occluded)
        cover.add(second)
        opaque.floatValue = .5f
        assertTrue(cover.occluded)
        cover.remove(second)
        assertFalse(cover.occluded)
        cover.remove(first)
        assertFalse(cover.covered)
    }

    @Test fun UncoverSamplesTheCurrentCameraBeforeRevealingAHeldScene() {
        val inputs = BackdropInputs()
        inputs.read(false) { SceneLive(scroll = 12f) }
        inputs.visibility(true)
        inputs.visibility(false)
        var reads = 0
        Snapshot.observe({ reads++ }, null) {
            assertEquals(99f, inputs.read(true) { SceneLive(scroll = 99f) }.scroll)
        }
        assertEquals(0, reads)
        assertEquals(99f, inputs.read(true) { error("Still held") }.scroll)
    }
}
