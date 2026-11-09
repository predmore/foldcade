package app.foldcade.romm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RommReadRetryTest {
    @Test fun conversionUsesRetryAfterThenBoundedBackoffAndReturnsTheReadyFile() = runBlocking {
        val waits = mutableListOf<Long>()
        val states = mutableListOf<Boolean>()
        var clock = 0L
        var reads = 0
        val retry = RommReadRetry(nowMillis = { clock }, pause = { waits += it; clock += it })
        val ready = retry.run(allowConversion = true, onWait = { states += it }) {
            when (reads++) {
                0 -> throw RommConversionPending(7)
                in 1..6 -> throw RommConversionPending(null)
                else -> "ready"
            }
        }
        assertEquals("ready", ready)
        assertEquals(listOf(7_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L), waits)
        assertTrue(states.all { it })
    }

    @Test fun missingZeroNegativeAndOverflowingRetryHintsCannotSpinOrOverflow() = runBlocking {
        for (hint in listOf(null, 0L, -1L, Long.MAX_VALUE)) {
            var reads = 0
            val waits = mutableListOf<Long>()
            val retry = RommReadRetry(pause = { waits += it })
            val error = runCatching { retry.run(allowConversion = true) {
                if (reads++ == 0) throw RommConversionPending(hint)
                "ready"
            } }.exceptionOrNull()
            if (hint == Long.MAX_VALUE) {
                assertTrue(error is RommConversionPending)
                assertTrue(waits.isEmpty())
            } else {
                assertNull(error)
                assertEquals(listOf(2_000L), waits)
            }
        }
    }

    @Test fun perpetualConversionEndsAtTheOverallCapWithoutOneMoreRequest() = runBlocking {
        var clock = 0L
        var reads = 0
        val retry = RommReadRetry(conversionLimitMillis = 10_000, nowMillis = { clock }, pause = { clock += it })
        val failure = runCatching { retry.run(allowConversion = true) { reads++; throw RommConversionPending(5) } }.exceptionOrNull()
        assertTrue(failure is RommConversionPending)
        assertEquals(10_000L, clock)
        assertEquals(2, reads)
    }

    @Test fun transientReadsRetryThreeTimesButAuthMissingAndIntegrityErrorsStop() = runBlocking {
        for (error in listOf(RommUnavailable("blip"), RommHttpException(503, "busy"), RommHttpException(429, "busy"))) {
            val waits = mutableListOf<Long>()
            var reads = 0
            val failure = runCatching { RommReadRetry(pause = { waits += it }).run { reads++; throw error } }.exceptionOrNull()
            assertSame(error, failure)
            assertEquals(4, reads)
            assertEquals(listOf(1_000L, 2_000L, 4_000L), waits)
        }
        for (error in listOf(RommUnauthorized("token"), RommHttpException(404, "gone"),
            RommResponseException("wrong hash"), RommSlotMoved("newer save"), RommConversionPending(1),
            RommUnavailable("policy", CleartextRejected("http://other.example")),
            RommUnavailable("offline", java.net.NoRouteToHostException()),
            RommUnavailable("bad name", java.net.UnknownHostException()),
            RommUnavailable("recovered coroutine", RommUnavailable("offline", java.net.NoRouteToHostException())))) {
            var reads = 0
            val failure = runCatching { RommReadRetry(pause = { error("must not wait") }).run { reads++; throw error } }.exceptionOrNull()
            assertSame(error, failure)
            assertEquals(1, reads)
        }
    }

    @Test fun aDownloadThatKeepsMovingIsNeverGivenUpOn() = runBlocking {
        val waits = mutableListOf<Long>()
        var reads = 0
        var moved = false
        val done = RommReadRetry(pause = { waits += it }).run(progressed = { moved.also { moved = false } }) {
            // Twenty stalls, each after a little more of the file arrived.
            if (reads++ < 20) {
                moved = true
                throw RommUnavailable("timeout", java.net.SocketTimeoutException())
            }
            "done"
        }
        assertEquals("done", done)
        assertEquals(21, reads)
        assertTrue(waits.all { it == 1_000L })
    }

    @Test fun aMovedDownloadThatStallsStopsAfterAboutAMinute() = runBlocking {
        val waits = mutableListOf<Long>()
        var reads = 0
        var moved = false
        val error = RommUnavailable("offline", java.net.NoRouteToHostException())
        val failure = runCatching {
            RommReadRetry(pause = { waits += it }).run(progressed = { moved.also { moved = false } }) {
                if (reads++ == 0) moved = true
                throw error
            }
        }.exceptionOrNull()
        assertSame(error, failure)
        assertEquals(7, reads)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L), waits)
    }

    @Test fun aMovedDownloadRetriesALostLinkButNotPolicyOrCertificateFailures() = runBlocking {
        for (error in listOf(RommUnavailable("offline", java.net.NoRouteToHostException()),
            RommUnavailable("dns", java.net.UnknownHostException()),
            RommUnavailable("tls read", javax.net.ssl.SSLException("Read error: Connection reset by peer")))) {
            var reads = 0
            var moved = false
            val done = RommReadRetry(pause = {}).run(progressed = { moved.also { moved = false } }) {
                if (reads++ == 0) { moved = true; throw error }
                "done"
            }
            assertEquals("done", done)
        }
        for (error in listOf(RommUnavailable("policy", CleartextRejected("http://other.example")),
            RommUnavailable("cert", javax.net.ssl.SSLHandshakeException("untrusted")),
            RommResponseException("wrong hash"), RommUnauthorized("token"))) {
            var reads = 0
            var moved = false
            val failure = runCatching {
                RommReadRetry(pause = { error("must not wait") }).run(progressed = { moved.also { moved = false } }) {
                    reads++
                    moved = true
                    throw error
                }
            }.exceptionOrNull()
            assertSame(error, failure)
            assertEquals(1, reads)
        }
    }

    @Test fun cancelDuringBackoffNeverTriesTheRequestAgain() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        var reads = 0
        val job = launch {
            RommReadRetry(pause = { waiting.complete(Unit); CompletableDeferred<Unit>().await() })
                .run(allowConversion = true) { reads++; throw RommConversionPending(2) }
        }
        waiting.await()
        job.cancelAndJoin()
        assertEquals(1, reads)
    }
}
