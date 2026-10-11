package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException
import app.foldcade.romm.RommUnavailable
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class RommArtworkTest {
    private val cache = Files.createTempDirectory("romm-artwork")

    @Test
    fun nothingLoadsBeforeRommIsSetUp() = runBlocking {
        assertNull(RommArtwork { null }.load("http://romm.example/assets/romm/resources/cover.png"))
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
        val artwork = RommArtwork { wiring }
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
            RommWiring(origin, RommTokenSource { null }, cache, cache.resolve("data")).apply {
                open = { _, _ -> ScriptedOps().apply { onArtwork = { ByteArray(1) }; onClose = { closed += 1 } } }
            }
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
        RommArtwork { wiring }.use { assertArrayEquals(image, it.load(uri)) }

        var requests = 0
        wiring.open = { _, token ->
            assertNull(token())
            ScriptedOps().apply { onArtwork = { requests++; throw RommUnavailable("offline") } }
        }
        RommArtwork { wiring }.use {
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
            RommWiring(origin, RommTokenSource { null }, cache, cache.resolve("data")).apply {
                open = { _, _ -> ScriptedOps().apply { onArtwork = { requests++; byteArrayOf(7) } } }
            }
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
