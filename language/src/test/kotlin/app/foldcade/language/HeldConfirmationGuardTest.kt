package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class HeldConfirmationGuardTest {
    @Test fun capturedConfirmationStaysConsumedAcrossWindowCancellationUntilGenuineKeyUp() {
        val shared = HeldKeyGuard()
        val key = 1
        shared.capture(key)
        assertFalse(shared.swallows(key, repeatCount = 1, capturing = true))
        // The deletion has completed and focus transfers to another window.
        shared.up(key, canceled = true)
        assertTrue(shared.swallows(key, repeatCount = 2, capturing = false))
        assertTrue(shared.swallows(key, repeatCount = 3, capturing = false))
        shared.up(key)
        assertFalse(shared.swallows(key, repeatCount = 0, capturing = false))
    }
}
