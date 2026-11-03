package app.foldcade.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindResultWindowTest {
    @Test
    fun initialWindowDoesNotComposeTheWholeResultList() {
        assertEquals((0..5).toList(), (0 until 24).filter { findResultInWindow(it.toFloat(), 0f, 4) })
    }

    @Test
    fun middleWindowKeepsBothFadingEdgesAndOneExtraPitch() {
        assertEquals((6..13).toList(), (0 until 24).filter { findResultInWindow(it.toFloat(), 8f, 4) })
        assertTrue(findResultInWindow(7.1f, 8f, 4))
        assertTrue(findResultInWindow(11.9f, 8f, 4))
        assertFalse(findResultInWindow(5.9f, 8f, 4))
        assertFalse(findResultInWindow(13.1f, 8f, 4))
    }

    @Test
    fun followsAnimatedScrollRatherThanItsDestination() {
        assertTrue(findResultInWindow(0f, 1.5f, 4))
        assertTrue(findResultInWindow(6f, 1.5f, 4))
        assertFalse(findResultInWindow(7f, 1.5f, 4))
        assertFalse(findResultInWindow(0f, 8f, 4))
        assertTrue(findResultInWindow(8f, 8f, 4))
    }

    @Test
    fun reorderedResultIsKeptWhileItCrossesTheVisibleWindow() {
        // A result travelling from slot 20 to slot 0 must appear along the way.
        assertFalse(findResultInWindow(20f, 0f, 4))
        assertTrue(findResultInWindow(4.5f, 0f, 4))
        assertTrue(findResultInWindow(0f, 0f, 4))
        // A result travelling out is retained through the entire edge fade.
        assertTrue(findResultInWindow(-0.9f, 0f, 4))
        assertTrue(findResultInWindow(-2f, 0f, 4))
        assertFalse(findResultInWindow(-2.1f, 0f, 4))
    }

    @Test
    fun singleCellViewportStillKeepsItsEdgeFades() {
        assertEquals((3..7).toList(), (0 until 24).filter { findResultInWindow(it.toFloat(), 5f, 1) })
    }
}
