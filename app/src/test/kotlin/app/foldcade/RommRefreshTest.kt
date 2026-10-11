package app.foldcade

import app.foldcade.api.plugin.PluginException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RommRefreshTest {
    @Test
    fun aSlowFirstServerDoesNotDelayTheSecond() = runBlocking {
        withTimeout(3_000) {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val secondLoaded = CompletableDeferred<Unit>()
            val loaded = mutableListOf<String>()
            val refresh = async {
                refreshRommServers(
                    ids = listOf("romm", "romm#2"),
                    load = { id ->
                        if (id == "romm") { firstStarted.complete(Unit); releaseFirst.await() }
                        id
                    },
                    onLoaded = { id, _ -> loaded += id; if (id == "romm#2") secondLoaded.complete(Unit) },
                    onSignIn = { error("Unexpected sign-in") },
                    onFailure = { _, failure -> throw failure },
                )
            }
            firstStarted.await()
            secondLoaded.await()
            assertEquals(listOf("romm#2"), loaded)
            releaseFirst.complete(Unit)
            refresh.await()
            assertEquals(listOf("romm#2", "romm"), loaded)
        }
    }

    @Test
    fun authAndOtherFailuresAreIsolatedToTheirServers() = runBlocking {
        val signedOut = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val loaded = mutableListOf<String>()
        refreshRommServers(
            ids = listOf("romm", "romm#2", "romm#3", "romm#4"),
            load = { id -> when (id) {
                "romm" -> throw PluginException.NotAuthenticated("expired")
                "romm#2" -> throw IllegalStateException("offline")
                "romm#3" -> null
                else -> id
            } },
            onLoaded = { id, _ -> loaded += id },
            onSignIn = { signedOut += it },
            onFailure = { id, _ -> failed += id },
        )
        assertEquals(listOf("romm"), signedOut)
        assertEquals(listOf("romm#2"), failed)
        assertEquals(listOf("romm#4"), loaded)
    }

    @Test
    fun cancellingARefreshCancelsEveryLoadAndDeliversNoLateResults() = runBlocking {
        withTimeout(3_000) {
            val entered = List(2) { CompletableDeferred<Unit>() }
            val release = CompletableDeferred<Unit>()
            val delivered = mutableListOf<String>()
            val cancelled = mutableSetOf<String>()
            val refresh = launch {
                refreshRommServers(
                    ids = listOf("romm", "romm#2"),
                    load = { id ->
                        entered[if (id == "romm") 0 else 1].complete(Unit)
                        try { release.await(); id } finally { cancelled += id }
                    },
                    onLoaded = { id, _ -> delivered += id },
                    onSignIn = { error("Cancellation is not a sign-in failure") },
                    onFailure = { _, _ -> error("Cancellation is not a connection failure") },
                )
            }
            entered.forEach { it.await() }
            refresh.cancelAndJoin()
            release.complete(Unit)
            assertEquals(setOf("romm", "romm#2"), cancelled)
            assertTrue(delivered.isEmpty())
        }
    }

    @Test
    fun refreshConcurrencyIsBounded() = runBlocking {
        withTimeout(3_000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var running = 0
            var maximum = 0
            var delivered = 0
            val refresh = async {
                refreshRommServers(
                    ids = (1..6).map { "romm#$it" },
                    load = { id ->
                        running++
                        maximum = maxOf(maximum, running)
                        if (running == 3) entered.complete(Unit)
                        try { release.await(); id } finally { running-- }
                    },
                    onLoaded = { _, _ -> delivered++ },
                    onSignIn = { error("Unexpected sign-in") },
                    onFailure = { _, failure -> throw failure },
                )
            }
            entered.await()
            yield()
            assertEquals(3, running)
            release.complete(Unit)
            refresh.await()
            assertEquals(3, maximum)
            assertEquals(6, delivered)
        }
    }
}
