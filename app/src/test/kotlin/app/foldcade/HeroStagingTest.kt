package app.foldcade

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class HeroStagingTest {
    @Test fun aFastSceneUpgradeDoesNotWaitForTheCoverToDwellAgain() = runBlocking {
        val cover = HeroLayout("ambient", cover = "box")
        val wide = HeroLayout("wide", wide = true, logo = "logo")
        var stagedSubject = "old"
        var stagedAt = 1_000L
        var now = 1_100L
        val arrivals = mutableListOf<Pair<HeroLayout<String>?, Long>>()
        loadHeroLayouts(
            initial = { cover },
            refresh = { now += 20L; wide },
        ) { images ->
            now += heroStageDelay(stagedSubject != "next", stagedAt, now, dwell = 180)
            arrivals += images to now
            stagedSubject = "next"
            stagedAt = now
        }
        // The first layout still waits 80 ms for the old scene; the upgrade uses only its 20 ms decode.
        assertEquals(listOf(cover to 1_180L, wide to 1_200L), arrivals)
    }

    @Test fun aNewSubjectThatDecodedPastTheDwellNeedsNoFurtherWait() {
        assertEquals(0L, heroStageDelay(subjectChanged = true, stagedAt = 1_000L, now = 1_300L, dwell = 180))
    }

    @Test fun slowDecodeKeepsThePreviousStagePastTheOldDeadline() = runBlocking {
        val decoded = CompletableDeferred<HeroLayout<String>>()
        val old = HeroLayout("old", cover = "old box")
        val next = HeroLayout("next", logo = "next logo")
        val stages = mutableListOf(old)
        val load = async {
            loadHeroLayouts(initial = { decoded.await() }, refresh = { null }) { stages += it!! }
        }
        assertNull(withTimeoutOrNull(450L) { load.await() })
        assertEquals(listOf(old), stages)
        decoded.complete(next)
        load.await()
        assertEquals(listOf(old, next), stages)
    }

    @Test fun missingUrisWaitForTheSceneLookupAndItsDecode() = runBlocking {
        val scene = CompletableDeferred<HeroLayout<String>?>()
        val stages = mutableListOf<HeroLayout<String>?>()
        val load = async {
            loadHeroLayouts(
                initial = { loadHeroLayout<String>(null, null) { _, _ -> error("No URI") } },
                refresh = { scene.await() },
            ) { stages += it }
        }
        yield()
        assertTrue(stages.isEmpty())
        val ready = HeroLayout("found wide", logo = "found logo")
        scene.complete(ready)
        load.await()
        assertEquals(listOf(ready), stages)
    }

    @Test fun noArtAndFailedArtArePublishedOnlyAfterTheLookupFinishes() = runBlocking {
        for (initial in listOf(null, HeroLayout<String>())) {
            val lookup = CompletableDeferred<HeroLayout<String>?>()
            val stages = mutableListOf<HeroLayout<String>?>()
            val load = async {
                loadHeroLayouts(initial = { initial }, refresh = { lookup.await() }) { stages += it }
            }
            yield()
            assertTrue(stages.isEmpty())
            lookup.complete(null)
            load.await()
            assertEquals(listOf(initial), stages)
        }
    }

    @Test fun aUsableFirstLayoutCanUpgradeButAnEmptyRefreshCannotClearIt() = runBlocking {
        val box = HeroLayout("ambient", cover = "box")
        val wide = HeroLayout("wide", logo = "logo")
        for (refreshed in listOf(null, HeroLayout(), box, wide)) {
            val lookup = CompletableDeferred<HeroLayout<String>?>()
            val stages = mutableListOf<HeroLayout<String>?>()
            val load = async {
                loadHeroLayouts(initial = { box }, refresh = { lookup.await() }) { stages += it }
            }
            yield()
            assertEquals(listOf(box), stages)
            lookup.complete(refreshed)
            load.await()
            assertEquals(if (refreshed == wide) listOf(box, wide) else listOf(box), stages)
        }
    }

    @Test fun leavingFocusCancelsThePendingReplacement() = runBlocking {
        val decoded = CompletableDeferred<HeroLayout<String>>()
        val stages = mutableListOf<HeroLayout<String>?>()
        val load = async {
            loadHeroLayouts(initial = { decoded.await() }, refresh = { null }) { stages += it }
        }
        yield()
        load.cancel()
        load.join()
        decoded.complete(HeroLayout("old focus"))
        assertTrue(stages.isEmpty())
    }
}
