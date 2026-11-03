package app.foldcade

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupReadinessTest {
    @Test
    fun aFailedPreparationReleasesWaitingLaunchesAndLaterSyncsNormally() = runBlocking {
        val entries = CompletableDeferred<Unit>()
        val romm = CompletableDeferred<Unit>()
        val saves = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val failure = IllegalStateException("save provider unavailable")
        val logged = mutableListOf<Exception>()
        val preparation = async {
            prepareStartup(entries, romm, saves, onFailure = { logged += it }) {
                entries.complete(Unit)
                release.await()
                throw failure
            }
        }
        val waitingLaunch = async { saves.await(); "launch" }
        yield()
        entries.await()
        assertFalse(waitingLaunch.isCompleted)
        assertFalse(romm.isCompleted)
        release.complete(Unit)
        preparation.await()
        assertEquals("launch", waitingLaunch.await())
        assertSame(failure, logged.single())
        // Later sync and launch requests also await normally; no gate is exceptional.
        listOf(entries, romm, saves).forEach { it.await(); assertFalse(it.isCancelled) }
    }

    @Test
    fun cancellationPropagatesAndStillReleasesTheStartupGates() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        var logged = false
        var continued = false
        val preparation = launch {
            prepareStartup(ready, onFailure = { logged = true }) {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
            continued = true
        }
        started.await()
        preparation.cancelAndJoin()
        assertTrue(preparation.isCancelled)
        ready.await()
        assertFalse(logged)
        assertFalse(continued)
    }
}
