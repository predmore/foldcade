package app.foldcade

import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UsageReconcileTest {
    @Test
    fun theReadRunsOffTheCallerAndALeftScreenDropsIt() = runBlocking {
        val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val started = CompletableDeferred<String>()
            val release = CompletableDeferred<Unit>()
            val reconcile = UsageReconcile()
            var published = 0
            val running = reconcile.request(
                scope = CoroutineScope(SupervisorJob() + worker),
                deliver = coroutineContext,
                alive = { false },
                read = {
                    started.complete(Thread.currentThread().name)
                    release.await()
                    UsageSnapshot(granted = true, samples = emptyList())
                },
                publish = { published += 1 },
            )
            val reader = started.await()
            assertNotEquals(Thread.currentThread().name, reader)
            release.complete(Unit)
            running.join()
            assertEquals(0, published)
        } finally {
            worker.close()
        }
    }

    @Test
    fun aNewerResumeDropsTheOlderQuery() = runBlocking {
        val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val hold = CompletableDeferred<Unit>()
            val reconcile = UsageReconcile()
            val published = mutableListOf<Boolean>()
            val scope = CoroutineScope(SupervisorJob() + worker)
            reconcile.request(
                scope = scope,
                deliver = coroutineContext,
                alive = { true },
                read = {
                    hold.await()
                    UsageSnapshot(granted = false, samples = emptyList())
                },
                publish = { published += it.granted },
            )
            val latest = reconcile.request(
                scope = scope,
                deliver = coroutineContext,
                alive = { true },
                read = { UsageSnapshot(granted = true, samples = emptyList()) },
                publish = { published += it.granted },
            )
            hold.complete(Unit)
            latest.join()
            assertEquals(listOf(true), published)
        } finally {
            worker.close()
        }
    }

    @Test
    fun cancelBeforeTheReadFinishesPublishesNothing() = runBlocking {
        val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val reconcile = UsageReconcile()
            var published = false
            val running = reconcile.request(
                scope = CoroutineScope(SupervisorJob() + worker),
                deliver = coroutineContext,
                alive = { true },
                read = {
                    started.complete(Unit)
                    release.await()
                    UsageSnapshot(granted = true, samples = emptyList())
                },
                publish = { published = true },
            )
            started.await()
            reconcile.cancel()
            release.complete(Unit)
            running.join()
            assertFalse(published)
        } finally {
            worker.close()
        }
    }
}
