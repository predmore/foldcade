package app.foldcade.artwork

import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtSceneRecoveryTest {
    private val root = Files.createTempDirectory("scene-recovery").toFile()
    private val file = root.resolve("covers.tsv")
    private val query = ArtQuery("tetris", "Tetris DS", "nintendo-ds")
    private val screenshot = ArtScene(screen = "https://retro/title.png")
    private val hero = ArtScene(background = "https://grid/hero.png", logo = "https://grid/logo.png")

    @After fun cleanup() { root.deleteRecursively() }

    @Test fun addingASourceEnrichesAPersistedPartialSceneAndKeepsItsScreenshot() = runBlocking {
        val retro = Source("retro") { screenshot }
        val grid = Source("grid") { hero }
        var available: List<ArtSource> = listOf(retro)
        val finder = ArtFinder({ available }, ArtIndex(file))
        assertEquals(screenshot, finder.findScene(query))
        val restarted = ArtFinder({ available }, ArtIndex(file))
        assertEquals(screenshot, restarted.findScene(query))
        assertEquals(1, retro.calls)
        available = listOf(retro, grid)
        assertEquals(screenshot.or(hero), restarted.findScene(query))
        assertEquals(1, grid.calls)
        assertEquals(screenshot.or(hero), restarted.cachedScene(query))
        restarted.findScene(query)
        assertEquals(1, grid.calls)
    }

    @Test fun forgettingMissesRetainsPartialArtButRetriesMissingParts() = runBlocking {
        var answer: ArtScene? = null
        val retro = Source("retro") { screenshot }
        val grid = Source("grid") { answer }
        val finder = ArtFinder({ listOf(retro, grid) }, ArtIndex(file))
        assertEquals(screenshot, finder.findScene(query))
        answer = hero
        finder.forgetMisses()
        assertEquals(screenshot, finder.cachedScene(query))
        assertEquals(screenshot.or(hero), finder.findScene(query))
        assertEquals(2, grid.calls)
    }

    @Test fun partialArtRecoversAfterAnOutageIncludingAcrossRestart() = runBlocking {
        var offline = true
        val retro = Source("retro") { screenshot }
        val grid = Source("grid") { if (offline) error("offline") else hero }
        val finder = ArtFinder({ listOf(retro, grid) }, ArtIndex(file))
        assertEquals(screenshot, finder.findScene(query))
        offline = false
        val restarted = ArtFinder({ listOf(retro, grid) }, ArtIndex(file))
        assertEquals(screenshot, restarted.cachedScene(query))
        assertEquals(screenshot.or(hero), restarted.findScene(query))
        assertEquals(2, grid.calls)
    }

    @Test fun legacyPartialHitsAreEnrichedWhileCompleteHitsStayCached() = runBlocking {
        ArtIndex(root.resolve("scenes-${file.name}")).put(query.key, listOf("", "", screenshot.screen!!))
        val grid = Source("grid") { hero }
        val finder = ArtFinder({ listOf(grid) }, ArtIndex(file))
        assertEquals(screenshot.or(hero), finder.findScene(query))
        finder.forgetMisses()
        finder.findScene(query)
        assertEquals(1, grid.calls)
    }

    @Test fun partialMissesExpireWithoutLosingExistingParts() = runBlocking {
        var now = 0L
        var answer: ArtScene? = screenshot
        val source = Source("source") { answer }
        val scenes = ArtIndex(root.resolve("scenes.tsv")) { now }
        val finder = ArtFinder({ listOf(source) }, ArtIndex(file), scenes)
        assertEquals(screenshot, finder.findScene(query))
        answer = hero
        finder.findScene(query)
        assertEquals(1, source.calls)
        now = ArtFinder.MISS_MAX_AGE_MS
        assertEquals(screenshot.or(hero), finder.findScene(query))
        assertEquals(2, source.calls)
    }

    @Test fun artworkOffMakesNoRequestsEvenWithAStalePartialScene() = runBlocking {
        var enabled = true
        val source = Source("source") { screenshot }
        val finder = ArtFinder({ listOf(source) }, ArtIndex(file), enabled = { enabled })
        finder.findScene(query)
        finder.forgetMisses()
        enabled = false
        assertNull(finder.cachedScene(query))
        assertNull(finder.findScene(query))
        assertEquals(1, source.calls)
    }

    @Test fun concurrentPartialRefreshesShareOneSuccessfulLookup() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        val retro = Source("retro") { screenshot }
        val grid = object : ArtSource {
            var calls = 0
            override val id = "grid"
            override fun handles(query: ArtQuery) = true
            override suspend fun find(query: ArtQuery): ArtSet? = null
            override suspend fun scene(query: ArtQuery): ArtScene { calls++; ready.await(); return hero }
        }
        var available: List<ArtSource> = listOf(retro)
        val finder = ArtFinder({ available }, ArtIndex(file))
        finder.findScene(query)
        available = listOf(retro, grid)
        val first = async { finder.findScene(query) }
        val second = async { finder.findScene(query) }
        yield()
        ready.complete(Unit)
        assertEquals(screenshot.or(hero), first.await())
        assertEquals(first.await(), second.await())
        assertEquals(1, grid.calls)
    }

    @Test fun cancellingARefreshKeepsCachedPartsAndAllowsAnotherAttempt() = runBlocking {
        var cancel = false
        var answer: ArtScene? = screenshot
        val source = Source("source") { if (cancel) throw CancellationException() else answer }
        val finder = ArtFinder({ listOf(source) }, ArtIndex(file))
        finder.findScene(query)
        finder.forgetMisses()
        cancel = true
        val refresh = async { finder.findScene(query) }
        try { refresh.await(); error("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(screenshot, finder.cachedScene(query))
        cancel = false
        answer = hero
        assertEquals(screenshot.or(hero), finder.findScene(query))
    }

    private class Source(override val id: String, val answer: () -> ArtScene?) : ArtSource {
        var calls = 0
        override fun handles(query: ArtQuery) = true
        override suspend fun find(query: ArtQuery): ArtSet? = null
        override suspend fun scene(query: ArtQuery): ArtScene? { calls++; return answer() }
    }
}
