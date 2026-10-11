package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException
import app.foldcade.romm.RommUnavailable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class RommArtworkTest {
    private val cache = Files.createTempDirectory("romm-artwork")

    @Test
    fun twoServersKeepSeparateClientsAndCachesWhenTheDefaultIsRemoved() = runBlocking {
        val opened = mutableListOf<String>()
        val closed = mutableListOf<String>()
        val requests = mutableListOf<String>()
        fun wiring(origin: String) = RommWiring(origin, RommTokenSource { error("Artwork must not read credentials") }, cache, cache.resolve("data")).apply {
            open = { server, token ->
                assertNull(token())
                opened += server
                ScriptedOps().apply {
                    onArtwork = { uri -> requests += uri; server.toByteArray() }
                    onClose = { closed += server }
                }
            }
        }
        val first = wiring("https://one.example")
        val second = wiring("https://two.example")
        var servers = listOf(first, second)
        RommArtwork { servers }.use { artwork ->
            for (server in servers) {
                val uri = "${server.origin}/cover.png"
                assertArrayEquals(server.origin.toByteArray(), artwork.load(uri))
                assertArrayEquals(server.origin.toByteArray(), artwork.load(uri))
            }
            assertEquals(listOf(first.origin, second.origin), opened)
            assertEquals(2, requests.size)
            assertTrue(closed.isEmpty())
            servers = listOf(second)
            assertArrayEquals(second.origin.toByteArray(), artwork.load("${second.origin}/cover.png"))
            assertEquals(listOf(first.origin), closed)
            assertTrue(runCatching { artwork.load("${first.origin}/cover.png") }.exceptionOrNull() is PluginException.Unavailable)
            assertEquals(2, requests.size)
        }
        assertEquals(listOf(first.origin, second.origin), closed)
    }

    @Test
    fun switchingServersDoesNotCloseAnImageStillLoading() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var closed = 0
        val first = RommWiring("https://one.example", RommTokenSource { null }, cache, cache.resolve("data")).apply {
            open = { _, _ -> ScriptedOps().apply {
                onArtwork = { started.complete(Unit); release.await(); byteArrayOf(1) }
                onClose = { closed++ }
            } }
        }
        val second = RommWiring("https://two.example", RommTokenSource { null }, cache, cache.resolve("data")).apply {
            open = { _, _ -> ScriptedOps().apply { onArtwork = { byteArrayOf(2) } } }
        }
        var servers = listOf(first, second)
        RommArtwork { servers }.use { artwork ->
            withTimeout(3_000) {
                val pending = async { artwork.load("${first.origin}/cover.png") }
                started.await()
                servers = listOf(second)
                assertArrayEquals(byteArrayOf(2), artwork.load("${second.origin}/cover.png"))
                assertEquals(0, closed)
                release.complete(Unit)
                assertArrayEquals(byteArrayOf(1), pending.await())
                assertEquals(1, closed)
            }
        }
    }

    @Test
    fun serversSharingAHostUseTheirOwnBasePath() = runBlocking {
        fun wiring(path: String) = RommWiring("https://romm.example/$path", RommTokenSource { null }, cache, cache.resolve("data")).apply {
            open = { _, _ -> ScriptedOps().apply { onArtwork = { path.toByteArray() } } }
        }
        val servers = listOf(wiring("one"), wiring("two"))
        RommArtwork { servers }.use { artwork ->
            assertArrayEquals("one".toByteArray(), artwork.load("https://romm.example/one/cover.png"))
            assertArrayEquals("two".toByteArray(), artwork.load("https://romm.example/two/cover.png"))
            assertTrue(runCatching { artwork.load("https://romm.example/two-other/cover.png") }.exceptionOrNull() is PluginException.Unavailable)
        }
    }

    @Test
    fun nothingLoadsBeforeRommIsSetUp() = runBlocking {
        assertNull(RommArtwork { emptyList() }.load("http://romm.example/assets/romm/resources/cover.png"))
    }

    @Test
    fun oneClientWithNoTokenServesEveryLoadOnAnOrigin() = runBlocking {
        val wiring = RommWiring("http://romm.example", RommTokenSource { "rmm_test" }, cache, cache.resolve("data"))
        val tokens = mutableListOf<String?>()
        var opened = 0
        wiring.open = { _, token ->
            opened += 1
            tokens += token()
            ScriptedOps().apply { onArtwork = { uri -> uri.toByteArray() } }
        }
        val artwork = RommArtwork { listOf(wiring) }
        val first = artwork.load("http://romm.example/a.png")
        val second = artwork.load("http://romm.example/b.png")
        assertTrue("http://romm.example/a.png".toByteArray().contentEquals(first))
        assertTrue("http://romm.example/b.png".toByteArray().contentEquals(second))
        assertEquals(1, opened)
        assertEquals(listOf<String?>(null), tokens)
    }

    @Test
    fun aNewOriginClosesTheOldClient() = runBlocking {
        var origin = "http://one.example"
        var closed = 0
        val artwork = RommArtwork {
            listOf(RommWiring(origin, RommTokenSource { null }, cache, cache.resolve("data")).apply {
                open = { _, _ -> ScriptedOps().apply { onArtwork = { ByteArray(1) }; onClose = { closed += 1 } } }
            })
        }
        artwork.load("http://one.example/a.png")
        origin = "http://two.example"
        artwork.load("http://two.example/a.png")
        assertEquals(1, closed)
        artwork.close()
        assertEquals(2, closed)
    }

    @Test
    fun aColdOfflineClientRestoresArtworkWithoutAToken() = runBlocking {
        val wiring = RommWiring("http://romm.example", RommTokenSource { "rmm_test" }, cache, cache.resolve("data"))
        val image = byteArrayOf(1, 2, 3, 4)
        wiring.open = { _, token ->
            assertNull(token())
            ScriptedOps().apply { onArtwork = { image } }
        }
        val uri = "http://romm.example/cover.png?ts=123"
        RommArtwork { listOf(wiring) }.use { assertArrayEquals(image, it.load(uri)) }

        var requests = 0
        wiring.open = { _, token ->
            assertNull(token())
            ScriptedOps().apply { onArtwork = { requests++; throw RommUnavailable("offline") } }
        }
        RommArtwork { listOf(wiring) }.use {
            assertArrayEquals(image, it.load(uri))
            assertEquals(0, requests)
            assertTrue(runCatching { it.load("http://romm.example/uncached.png") }.exceptionOrNull() is PluginException.Unavailable)
            assertEquals(1, requests)
        }
    }

    @Test
    fun cachedImagesCannotBypassOriginAndCredentialChecks() = runBlocking {
        var origin = "https://one.example"
        var requests = 0
        val artwork = RommArtwork {
            listOf(RommWiring(origin, RommTokenSource { null }, cache, cache.resolve("data")).apply {
                open = { _, _ -> ScriptedOps().apply { onArtwork = { requests++; byteArrayOf(7) } } }
            })
        }
        val first = "https://one.example/cover.png"
        artwork.load(first)
        origin = "https://two.example"
        for (uri in listOf(first, "http://two.example/cover.png", "https://two.example:444/cover.png",
            "https://user:secret@two.example/cover.png", "https://two.example/cover.png?token=secret")) {
            assertTrue(runCatching { artwork.load(uri) }.exceptionOrNull() is PluginException.Unavailable)
        }
        assertEquals(1, requests)
        artwork.load("https://two.example/cover.png")
        assertEquals(2, requests)
        origin = "https://one.example"
        assertArrayEquals(byteArrayOf(7), artwork.load(first))
        assertEquals(2, requests)
        artwork.close()
    }
}
