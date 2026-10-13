package app.foldcade

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryResyncTest {
    private val interval = 60_000L
    private var clock = 1_000L
    private var syncs = 0
    private val running = mutableListOf<Boolean>()

    private fun resync(scope: CoroutineScope, work: suspend () -> Unit = {}) = LibraryResync(
        scope,
        intervalMillis = interval,
        now = { clock },
        onRunning = { running += it },
        work = { syncs++; work() },
    )

    @Test
    fun theStartUpReadCountsAsTheFirstSync() = runBlocking {
        val resync = resync(this)
        assertEquals(interval, resync.untilDue())
        clock += 40_000
        assertEquals(20_000L, resync.untilDue())
        clock += interval
        assertEquals(0L, resync.untilDue())
        resync.syncNow()
        assertEquals(interval, resync.untilDue())
    }

    @Test
    fun aSecondAskJoinsTheSyncRunning() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val resync = resync(this) { release.await() }
        resync.syncNow()
        yield()
        resync.syncNow()
        yield()
        assertEquals(1, syncs)
        assertEquals(listOf(true), running)
        release.complete(Unit)
        yield()
        assertEquals(listOf(true, false), running)
    }

    @Test
    fun comingForwardSyncsOnlyOnceTheLastSyncIsOld() = runBlocking {
        val resync = resync(this)
        resync.noteShown(true)
        yield()
        assertEquals(0, syncs)
        resync.noteShown(false)

        clock += interval
        resync.noteShown(true)
        yield()
        yield()
        assertEquals(1, syncs)
        assertEquals(interval, resync.untilDue())

        // Covering Home stops the clock. Coming back right after does not sync again.
        resync.noteShown(false)
        resync.noteShown(true)
        yield()
        assertEquals(1, syncs)
        resync.noteShown(false)
    }

    @Test
    fun aSyncAskedForWhileWaitingMovesTheNextOne() = runBlocking {
        val start = System.currentTimeMillis()
        var count = 0
        val resync = LibraryResync(
            this,
            intervalMillis = 400,
            now = { System.currentTimeMillis() - start },
            onRunning = {},
            work = { count++ },
        )
        resync.noteShown(true)
        delay(200)
        resync.syncNow()
        // Past the deadline the clock had when it began waiting, short of the new one.
        delay(300)
        assertEquals(1, count)
        delay(400)
        assertEquals(2, count)
        resync.noteShown(false)
    }
}
