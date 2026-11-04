package app.foldcade

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class CatalogLaunchTest {
    @Test fun joinsStartupAndAcceptsACachedRouteBeforeTheSlowSyncEnds() = runBlocking {
        val stop = CompletableDeferred<Unit>()
        val startup = launch { stop.await() }
        var route: String? = null
        var asks = 0
        val pending = async { awaitCatalogGame(resolve = { route }, sync = { asks++; startup }) }
        yield()
        assertFalse(pending.isCompleted)
        route = "romm:23"
        assertEquals("romm:23", pending.await())
        assertEquals(1, asks)
        assertTrue(startup.isActive)
        startup.cancelAndJoin()
    }

    @Test fun goneGameAndUnreachableCatalogDoNotWaitForever() = runBlocking {
        val finished = launch {}.also { it.join() }
        assertNull(awaitCatalogGame(resolve = { null }, sync = { finished }))
        val stuck = launch { CompletableDeferred<Unit>().await() }
        assertNull(awaitCatalogGame(resolve = { null }, sync = { stuck }, limitMillis = 60))
        assertTrue(stuck.isActive)
        stuck.cancelAndJoin()
    }

    @Test fun cancellingThePressDoesNotCancelTheSharedSyncOrLaunchLater() = runBlocking {
        val sync = launch { CompletableDeferred<Unit>().await() }
        var delivered = false
        val work = LaunchWork()
        val ticket = work.begin("tile-23")
        val pending = launch {
            awaitCatalogGame(resolve = { null }, sync = { sync })
            delivered = true
        }
        work.job = pending
        yield()
        assertTrue(work.pending("tile-23"))
        assertFalse(work.pending("other-tile"))
        work.cancel()
        pending.join()
        assertFalse(delivered)
        assertFalse(work.current(ticket))
        assertTrue(sync.isActive)
        sync.cancelAndJoin()
    }

    @Test fun followsARefreshReplacedByEditingTheConnection() = runBlocking {
        val old = launch {}.also { it.cancelAndJoin() }
        var route: String? = null
        val new = launch { route = "new-route" }
        var asks = 0
        assertEquals("new-route", awaitCatalogGame(resolve = { route }, sync = { if (asks++ == 0) old else new }))
    }
}
