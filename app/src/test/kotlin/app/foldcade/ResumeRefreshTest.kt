package app.foldcade

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRefreshTest {
    @Test
    fun aCompanionResumeCannotConsumeTheGamesActualSaveCheck() {
        val returns = HomeSaveReturns()
        assertFalse(returns.needsRefresh(returnedFromGame = false)) // Startup already checks.
        returns.notePaused()
        assertTrue(returns.needsRefresh(returnedFromGame = false)) // Lid/Move while still playing.
        assertFalse(returns.needsRefresh(returnedFromGame = false)) // Other panel shares it.
        assertTrue(returns.needsRefresh(returnedFromGame = true)) // Actual game return still checks.
        assertFalse(returns.needsRefresh(returnedFromGame = false))
    }

    @Test
    fun bothPanelsShareTheRunningAndRecentWalkButReloadBypassesTheInterval() {
        var now = 0L
        var walks = 0
        val refreshes = FolderRefreshes(5_000) { now }
        fun walk() = Job().also { walks++ }
        val first = refreshes.quietly(start = ::walk)
        now = 10_000
        assertSame(first, refreshes.quietly(start = ::walk)) // Still running, even after the interval.
        (first as kotlinx.coroutines.CompletableJob).complete()
        val second = refreshes.quietly(start = ::walk)
        (second as kotlinx.coroutines.CompletableJob).complete()
        now += 1
        assertSame(second, refreshes.quietly(start = ::walk))
        val explicit = refreshes.quietly(force = true, start = ::walk)
        (explicit as kotlinx.coroutines.CompletableJob).complete()
        val reload = refreshes.record(walk())
        assertSame(reload, refreshes.quietly(start = ::walk))
        assertEquals(4, walks)
    }

    @Test
    fun initialDiscoveryAndBothPanelResumesReadHealthOnce() = runBlocking {
        val entries = CompletableDeferred<Unit>()
        val reads = mutableListOf<Boolean>()
        val checking = mutableListOf<Boolean>()
        val refreshes = SaveRefreshes(this, { checking += it }) { discover ->
            entries.await()
            reads += discover
        }
        val startup = refreshes.request(discover = true, changed = false)
        yield()
        assertSame(startup, refreshes.request(discover = true, changed = false))
        assertSame(startup, refreshes.request(discover = false, changed = false))
        assertEquals(listOf(true), checking)
        entries.complete(Unit)
        startup.await()
        assertEquals(listOf(true), reads)
        assertEquals(listOf(true, false), checking)
    }

    @Test
    fun aGrantChangedDuringDiscoveryGetsAFreshPassBeforeCheckingEnds() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val reads = mutableListOf<Boolean>()
        val checking = mutableListOf<Boolean>()
        val refreshes = SaveRefreshes(this, { checking += it }) { discover ->
            reads += discover
            if (reads.size == 1) release.await()
        }
        val first = refreshes.request(discover = true)
        yield()
        refreshes.request(discover = false)
        refreshes.request(discover = true)
        refreshes.request(discover = true, changed = false)
        release.complete(Unit)
        first.await()
        assertEquals(listOf(true, true), reads)
        assertEquals(listOf(true, false), checking)
    }

    @Test
    fun discoveryRequestedDuringHealthRunsAfterItAndLaterReturnsStillRead() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val reads = mutableListOf<Boolean>()
        val refreshes = SaveRefreshes(this, {}) { discover ->
            reads += discover
            if (reads.size == 1) release.await()
        }
        val health = refreshes.request(discover = false)
        yield()
        refreshes.request(discover = true, changed = false)
        release.complete(Unit)
        health.await()
        refreshes.request(discover = true).await()
        assertEquals(listOf(false, true, true), reads)
    }

    @Test
    fun cancellationEndsCheckingAndDoesNotPreventTheNextRead() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val checking = mutableListOf<Boolean>()
        var reads = 0
        val refreshes = SaveRefreshes(this, { checking += it }) {
            if (++reads == 1) release.await()
        }
        val first = refreshes.request(discover = true)
        yield()
        first.cancel()
        first.join()
        refreshes.request(discover = true).await()
        assertEquals(2, reads)
        assertEquals(listOf(true, false, true, false), checking)
    }
}
