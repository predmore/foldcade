package app.foldcade

import app.foldcade.artwork.ArtScene
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class HeroLayoutTest {
    @Test fun wideLayoutWaitsForItsLogoAndRetainsDecodedImages() = runBlocking {
        val logo = CompletableDeferred<String?>()
        val calls = mutableListOf<Pair<String, HeroImageRole>>()
        val pending = async {
            loadHeroLayout("box", ArtScene(background = "wide", logo = "logo", screen = "snap")) { uri, role ->
                calls += uri to role
                if (uri == "logo") logo.await() else "decoded:$uri"
            }
        }
        yield()
        assertFalse(pending.isCompleted)
        logo.complete("decoded:logo")
        val ready = pending.await()!!
        assertEquals("decoded:wide", ready.backdrop)
        assertEquals("decoded:logo", ready.logo)
        assertNull(ready.cover)
        assertNull(ready.screen)
        assertEquals(listOf("wide" to HeroImageRole.Backdrop, "logo" to HeroImageRole.Logo), calls)
    }

    @Test fun failedReplacementCannotPublishAPartialLayout() = runBlocking {
        for (failed in listOf("wide", "logo")) {
            assertNull(loadHeroLayout("box", ArtScene(background = "wide", logo = "logo")) { uri, _ ->
                uri.takeUnless { it == failed }
            })
        }
        for (failed in listOf("box", "snap")) {
            assertNull(loadHeroLayout("box", ArtScene(screen = "snap")) { uri, _ -> uri.takeUnless { it == failed } })
        }
    }

    @Test fun fallbackIsACompleteBoxAndSnapLayoutAndMissingArtIsValid() = runBlocking {
        val ready = loadHeroLayout("box", ArtScene(screen = "snap")) { uri, role -> "$uri:$role" }!!
        assertFalse(ready.wide)
        assertEquals("box:Ambient", ready.backdrop)
        assertEquals("box:Cover", ready.cover)
        assertEquals("snap:Screen", ready.screen)
        assertNull(ready.logo)
        assertEquals(HeroLayout<String>(), loadHeroLayout<String>(null, null) { _, _ -> error("No images to decode") })
    }
}
