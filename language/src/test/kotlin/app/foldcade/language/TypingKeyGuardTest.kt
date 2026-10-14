package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingKeyGuardTest {
    private val x = KeyEvent.KEYCODE_BUTTON_X
    private val a = KeyEvent.KEYCODE_BUTTON_A

    @Test
    fun aKeyHeldWhileTypingStaysQuietAfterATapCloses() {
        val guard = TypingKeyGuard()
        // X goes down and repeats while the keyboard is open: the keyboard hears every one.
        assertFalse(guard.swallows(x, repeatCount = 0, capturing = true))
        assertFalse(guard.swallows(x, repeatCount = 1, capturing = true))
        // A tap on Done closes it with X still held. Its repeats never reach the grid.
        assertTrue(guard.swallows(x, repeatCount = 2, capturing = false))
        assertTrue(guard.swallows(x, repeatCount = 3, capturing = false))
        // Once X comes up, the next press is the grid's.
        guard.up(x)
        assertFalse(guard.swallows(x, repeatCount = 0, capturing = false))
        assertFalse(guard.swallows(x, repeatCount = 1, capturing = false))
    }

    @Test
    fun theKeyThatClosesTheKeyboardIsHeldBackToo() {
        val guard = TypingKeyGuard()
        assertFalse(guard.swallows(a, repeatCount = 0, capturing = true))
        assertTrue(guard.swallows(a, repeatCount = 1, capturing = false))
    }

    @Test
    fun keysPressedOnTheGridAreLeftAlone() {
        val guard = TypingKeyGuard()
        assertFalse(guard.swallows(a, repeatCount = 0, capturing = false))
        assertFalse(guard.swallows(a, repeatCount = 4, capturing = false))
    }

    @Test
    fun aKeyCancelledByAFocusMoveIsStillHeld() {
        // X goes down on the top screen; the tap on Done moves focus to the bottom one. The top
        // window hears X cancelled, and the bottom one gets its repeats. They stay quiet.
        val guard = TypingKeyGuard()
        guard.swallows(x, repeatCount = 0, capturing = true)
        guard.up(x, canceled = true)
        assertTrue(guard.swallows(x, repeatCount = 5, capturing = false))
        guard.up(x)
        assertFalse(guard.swallows(x, repeatCount = 0, capturing = false))
    }

    @Test
    fun aLostKeyUpDoesNotEatTheNextPress() {
        val guard = TypingKeyGuard()
        guard.swallows(x, repeatCount = 0, capturing = true)
        // No key-up arrived (focus moved). A fresh press is the grid's, and so are its repeats.
        assertFalse(guard.swallows(x, repeatCount = 0, capturing = false))
        assertFalse(guard.swallows(x, repeatCount = 1, capturing = false))
    }
}
