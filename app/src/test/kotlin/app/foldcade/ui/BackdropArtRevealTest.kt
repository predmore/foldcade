package app.foldcade.ui

import org.junit.Assert.*
import org.junit.Test

class BackdropArtRevealTest {
    @Test fun memoryCacheArtCanOccludeImmediately() {
        assertTrue(BackdropArtReveal(0).complete)
    }

    @Test fun lateArtWaitsForItsActualFirstDrawAndTheWholeCrossfade() {
        val reveal = BackdropArtReveal(200)
        assertFalse(reveal.complete)
        // Success may have arrived long before the image is actually drawn.
        val firstDraw = 5_000_000_000L
        reveal.drew(firstDraw)
        assertFalse(reveal.complete)
        // Ending after the deadline isn't enough if this frame began before it.
        reveal.drew(firstDraw + 199_000_000L, firstDraw + 205_000_000L)
        assertFalse(reveal.complete)
        reveal.drew(firstDraw + 200_000_000L)
        assertTrue(reveal.complete)
    }
}
