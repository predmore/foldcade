package app.foldcade

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchWorkTest {
    @Test
    fun cancellationFromEitherDisplayStopsWorkAndInvalidatesLateResults() = runBlocking {
        val work = LaunchWork()
        val top = work.begin()
        val started = CompletableDeferred<Unit>()
        var delivered = false
        val download = launch {
            started.complete(Unit)
            CompletableDeferred<Unit>().await()
            delivered = true
        }
        work.job = download
        started.await()
        // The bottom activity uses this same coordinator when B or a shoulder is pressed.
        work.cancel()
        download.join()
        assertTrue(download.isCancelled)
        assertFalse(delivered)
        assertFalse(work.current(top))
        assertFalse(work.active)

        val next = work.begin()
        work.job = launch { CompletableDeferred<Unit>().await() }
        val replacement = work.begin()
        assertFalse(work.current(next))
        assertTrue(work.current(replacement))
    }

    @Test
    fun destroyingTheStarterCancelsOnlyItsLaunch() = runBlocking {
        val work = LaunchWork()
        val first = work.begin()
        val second = work.begin()
        cancelLaunchIfOwned(work, first)
        assertTrue(work.current(second))

        val started = CompletableDeferred<Unit>()
        var delivered = false
        val download = launch {
            started.complete(Unit)
            CompletableDeferred<Unit>().await()
            delivered = true
        }
        work.job = download
        started.await()
        cancelLaunchIfOwned(work, second)
        download.join()
        assertFalse(delivered)
        assertTrue(download.isCancelled)
        assertFalse(work.current(second))
        assertFalse(work.active)
    }
}
