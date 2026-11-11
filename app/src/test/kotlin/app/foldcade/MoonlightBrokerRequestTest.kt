package app.foldcade

import app.foldcade.net.LanHttpException
import app.foldcade.net.LanReplyException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MoonlightBrokerRequestTest {
    private val old = LocatedHost("host-uuid", "old-address", 47984)
    private val moved = old.copy(address = "new-address")

    @Test fun retriesATransportFailureOnceAtTheNewlyLocatedAddress() = runBlocking {
        val sent = mutableListOf<LocatedHost>()
        var located = 0
        val reply = requestLocatedStreamHost(old, { located++; moved }) { at ->
            sent += at
            if (at == old) throw LanHttpException("Connection failed")
            "XML"
        }
        assertEquals("XML", reply)
        assertEquals(listOf(old, moved), sent)
        assertEquals(1, located)
    }

    @Test fun doesNotRetryAnUnchangedOrMissingAddressOrAnyBodyFailure() = runBlocking {
        for (next in listOf(old, null)) {
            val failure = LanHttpException("Connection failed")
            var sent = 0
            try {
                requestLocatedStreamHost(old, { next }) { sent++; throw failure }
                fail("Failure was swallowed")
            } catch (actual: LanHttpException) { assertSame(failure, actual) }
            assertEquals(1, sent)
        }
        val bodyFailure = LanReplyException("Too large")
        try {
            requestLocatedStreamHost(old, { error("Body failure must not relocate") }) { throw bodyFailure }
            fail("Failure was swallowed")
        } catch (actual: LanReplyException) { assertSame(bodyFailure, actual) }
    }

    @Test fun aFailedRetryDoesNotLoopAndANewLookupIsNotRetried() = runBlocking {
        var located = 0
        var sent = 0
        try {
            requestLocatedStreamHost(old, { located++; moved }) { sent++; throw LanHttpException("Still unreachable") }
            fail("Failure was swallowed")
        } catch (_: LanHttpException) { }
        assertEquals(2, sent)
        assertEquals(1, located)
        located = 0
        sent = 0
        try {
            requestLocatedStreamHost(null, { located++; moved }) { sent++; throw LanHttpException("Unreachable") }
            fail("Failure was swallowed")
        } catch (_: LanHttpException) { }
        assertEquals(1, sent)
        assertEquals(1, located)
    }
}
