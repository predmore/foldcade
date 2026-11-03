package app.foldcade.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LauncherIconCacheTest {
    private fun key(name: String = "app", version: Long = 1, updatedAt: Long = 2, size: Int = 384) =
        LauncherIconKey(name, version, updatedAt, size)

    @Test fun reusesAcrossCallersAndKeysByPackageVersionUpdateAndSize() = runBlocking {
        val cache = LauncherIconCache<Any>(16)
        val first = Any()
        assertSame(first, cache.getOrLoad(key()) { first })
        assertSame(first, cache.getOrLoad(key()) { error("Re-rasterized") })
        for (changed in listOf(key("other"), key(version = 2), key(updatedAt = 3), key(size = 640))) {
            assertNotSame(first, cache.getOrLoad(changed) { Any() })
        }
        cache.clear()
        assertNotSame(first, cache.getOrLoad(key()) { Any() })
    }

    @Test fun evictsTheLeastRecentlyUsedIconAndDoesNotKeepMissingIcons() = runBlocking {
        val cache = LauncherIconCache<String>(2)
        cache.getOrLoad(key("a")) { "a" }
        cache.getOrLoad(key("b")) { "b" }
        cache.getOrLoad(key("a")) { error("a evicted too early") }
        cache.getOrLoad(key("c")) { "c" }
        assertEquals("reloaded", cache.getOrLoad(key("b")) { "reloaded" })
        assertNull(cache.getOrLoad(key("missing")) { null })
        assertEquals("installed", cache.getOrLoad(key("missing")) { "installed" })
    }

    @Test fun aPackageChangeDuringRasterizationCannotRefillTheCache() = runBlocking {
        val cache = LauncherIconCache<String>(2)
        val started = CompletableDeferred<Unit>()
        val finish = CountDownLatch(1)
        val loading = async(Dispatchers.Default) {
            cache.getOrLoad(key()) {
                started.complete(Unit)
                check(finish.await(5, TimeUnit.SECONDS))
                "old"
            }
        }
        started.await()
        cache.clear() // Must not block behind the drawable's draw call.
        finish.countDown()
        assertEquals("old", loading.await())
        assertEquals("new", cache.getOrLoad(key()) { "new" })
    }

    @Test fun twoPanelsShareAnInFlightLoad() = runBlocking {
        val cache = LauncherIconCache<String>(2)
        val first = async { cache.getOrLoad(key()) { "first" } }
        val second = async { cache.getOrLoad(key()) { error("Loaded twice") } }
        assertEquals("first", first.await())
        assertEquals("first", second.await())
    }
}
