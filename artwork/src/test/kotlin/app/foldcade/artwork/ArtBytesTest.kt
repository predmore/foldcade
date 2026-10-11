package app.foldcade.artwork

import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtBytesTest {
    @Test
    fun bothDisplaysShareOneCompleteDownload() = runBlocking {
        val dir = Files.createTempDirectory("art-bytes").toFile()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var requests = 0
        val bytes = byteArrayOf(1, 2, 3)
        val cache = ArtBytes(dir, {
            requests++
            started.complete(Unit)
            finish.await()
            bytes
        })
        val first = async { cache.load("https://art.example/cover") }
        started.await()
        val second = async { cache.load("https://art.example/cover") }
        finish.complete(Unit)
        assertArrayEquals(bytes, first.await())
        assertArrayEquals(bytes, second.await())
        assertEquals(1, requests)
        assertEquals(1, dir.listFiles()!!.size)
        val cold = ArtBytes(dir, { error("cache hit must not fetch") })
        assertArrayEquals(bytes, cold.load("https://art.example/cover"))
    }

    @Test
    fun cancelledRequestsLeaveNoPartialImage() = runBlocking {
        val dir = Files.createTempDirectory("art-bytes-cancel").toFile()
        val started = CompletableDeferred<Unit>()
        val cache = ArtBytes(dir, {
            started.complete(Unit)
            awaitCancellation()
        })
        val job = launch { cache.load("https://art.example/cover") }
        started.await()
        job.cancelAndJoin()
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun cacheTrimsLeastRecentlyUsedImages() = runBlocking {
        val dir = Files.createTempDirectory("art-bytes-trim").toFile()
        val cache = ArtBytes(dir, { url -> ByteArray(4) { url.last().code.toByte() } }, maxTotalBytes = 8)
        cache.load("https://art.example/a")
        dir.listFiles()!!.single().setLastModified(1)
        cache.load("https://art.example/b")
        dir.listFiles()!!.single { it.readBytes()[0] == 'b'.code.toByte() }.setLastModified(2)
        cache.load("https://art.example/c")
        val files = dir.listFiles()!!
        assertEquals(8L, files.sumOf { it.length() })
        assertEquals(setOf('b'.code.toByte(), 'c'.code.toByte()), files.map { it.readBytes()[0] }.toSet())
        assertTrue(files.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun missingAndEmptyResponsesAreNotCached() = runBlocking {
        val dir = Files.createTempDirectory("art-bytes-missing").toFile()
        assertNull(ArtBytes(dir, { null }).load("https://art.example/missing"))
        assertNull(ArtBytes(dir, { byteArrayOf() }).load("https://art.example/empty"))
        assertTrue(dir.listFiles()!!.isEmpty())
    }
}
