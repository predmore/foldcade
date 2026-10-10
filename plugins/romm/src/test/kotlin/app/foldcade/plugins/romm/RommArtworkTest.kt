package app.foldcade.plugins.romm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        val wiring = RommWiring("http://romm.example", RommTokenSource { "rmm_test" }, cache)
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
            RommWiring(origin, RommTokenSource { null }, cache).apply {
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
}
