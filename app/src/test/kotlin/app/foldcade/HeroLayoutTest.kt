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

    @Test fun aFailedLogoKeepsTheWideArtWithTheBoxAndTextTitle() = runBlocking {
        val ready = loadHeroLayout("box", ArtScene(background = "wide", logo = "logo")) { uri, _ ->
            uri.takeUnless { it == "logo" }
        }!!
        assertTrue(ready.wide)
        assertEquals("wide", ready.backdrop)
        assertEquals("box", ready.cover)
        assertNull(ready.logo)
    }

    @Test fun failedWideArtFallsBackToTheCompleteBoxAndSnapLayout() = runBlocking {
        val ready = loadHeroLayout("box", ArtScene(background = "wide", logo = "logo", screen = "snap")) { uri, _ ->
            uri.takeUnless { it == "wide" }
        }!!
        assertFalse(ready.wide)
        assertEquals("box", ready.backdrop)
        assertEquals("box", ready.cover)
        assertEquals("snap", ready.screen)
        assertNull(ready.logo)
    }

    @Test fun failedScreenshotKeepsTheBoxAndFailedBoxKeepsTheScreenshot() = runBlocking {
        val box = loadHeroLayout("box", ArtScene(screen = "snap")) { uri, _ -> uri.takeUnless { it == "snap" } }!!
        assertEquals("box", box.backdrop)
        assertEquals("box", box.cover)
        assertNull(box.screen)
        val shot = loadHeroLayout("box", ArtScene(screen = "snap")) { uri, _ -> uri.takeUnless { it == "box" } }!!
        assertEquals("snap", shot.backdrop)
        assertEquals("snap", shot.cover)
        assertNull(shot.screen)
        assertNull(loadHeroLayout<String>("bad", ArtScene(background = "bad", logo = "bad")) { _, _ -> null })
    }

    @Test fun boxAndSnapDecodeConcurrentlyAndPublishTogether() = runBlocking {
        val ambient = CompletableDeferred<String?>()
        val box = CompletableDeferred<String?>()
        val screen = CompletableDeferred<String?>()
        val started = mutableSetOf<HeroImageRole>()
        val allStarted = CompletableDeferred<Unit>()
        val pending = async {
            loadHeroLayout("box", ArtScene(screen = "snap")) { _, role ->
                started += role
                if (started.size == 3) allStarted.complete(Unit)
                when (role) {
                    HeroImageRole.Ambient -> ambient.await()
                    HeroImageRole.Cover -> box.await()
                    HeroImageRole.Screen -> screen.await()
                    else -> error("Unexpected role")
                }
            }
        }
        allStarted.await()
        assertEquals(setOf(HeroImageRole.Ambient, HeroImageRole.Cover, HeroImageRole.Screen), started)
        ambient.complete("ambient")
        box.complete("box")
        yield()
        assertFalse(pending.isCompleted)
        screen.complete("snap")
        assertEquals(HeroLayout("ambient", cover = "box", screen = "snap"), pending.await())
        assertEquals(HeroLayout<String>(), loadHeroLayout<String>(null, null) { _, _ -> error("No images to decode") })
    }
}
