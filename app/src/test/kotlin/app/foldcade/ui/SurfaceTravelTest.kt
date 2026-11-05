package app.foldcade.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SurfaceTravelTest {
    @Test fun theCoveredWindowKeepsOneSurfaceUntilItDraws() = runBlocking {
        val travel = SurfaceTravel("grid")
        val commit = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            travel.change("center", { commit.await() }) { next ->
                assertEquals("grid", next.back)
                assertEquals("center", next.front)
                assertEquals(0f, next.incoming.value, 0f)
                assertEquals(1f, next.outgoing.value, 0f)
                finish.await()
            }
        }
        assertEquals("grid", travel.frame.front)
        assertNull(travel.frame.back)
        commit.complete(Unit)
        finish.complete(Unit)
        job.join()
        assertEquals("center", travel.frame.front)
        assertNull(travel.frame.back)
    }

    @Test fun cancellingTheWaitCreatesNoSplitAndInterruptingAFadeSettlesIt() = runBlocking {
        val travel = SurfaceTravel("grid")
        val pending = CompletableDeferred<Unit>()
        val wait = launch(start = CoroutineStart.UNDISPATCHED) {
            travel.change("center", { pending.await() }) { fail("Not drawn yet") }
        }
        wait.cancelAndJoin()
        assertEquals("grid", travel.frame.front)
        assertNull(travel.frame.back)

        val fade = launch(start = CoroutineStart.UNDISPATCHED) {
            travel.change("center", {}) { pending.await() }
        }
        val first = travel.frame
        assertNotEquals(first.front, first.back)
        fade.cancelAndJoin()
        assertNull(travel.frame.back)
        travel.change("grid", {}) { next ->
            assertNotEquals(next.front, next.back)
            assertNotSame(first.incoming, next.incoming)
            assertNotSame(first.outgoing, next.outgoing)
        }
        assertEquals("grid", travel.frame.front)
        assertNull(travel.frame.back)
    }

    @Test fun aFailedDrawDoesNotPublishTheDestinationOrDisableTheOldSurface() = runBlocking {
        val travel = SurfaceTravel("grid")
        try {
            travel.change("center", { error("detached") }) { fail("No submitted frame") }
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals("grid", travel.frame.front)
        assertNull(travel.frame.back)
    }
}
