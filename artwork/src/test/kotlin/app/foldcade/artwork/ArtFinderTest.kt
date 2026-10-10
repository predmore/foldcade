package app.foldcade.artwork

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArtFinderTest {
    private val query = ArtQuery("lib:local-folder:tetris", "Tetris DS", "nintendo-ds", "Tetris DS (USA).nds")

    @Test
    fun theFirstSourceWithACoverWinsAndIsRemembered() = runBlocking {
        val first = Scripted("first", null)
        val second = Scripted("second", ArtSet("second", "https://a/cover.png"))
        val third = Scripted("third", ArtSet("third", "https://b/cover.png"))
        val finder = ArtFinder({ listOf(first, second, third) }, ArtIndex(tempFile()))
        assertEquals("https://a/cover.png", finder.find(query)?.cover)
        assertEquals(0, third.asked)
        finder.find(query)
        assertEquals(1, second.asked)
        assertEquals("https://a/cover.png", finder.cached(query)?.cover)
    }

    @Test
    fun aMissIsRememberedButAnOutageIsNot() = runBlocking {
        val offline = Scripted("offline", null, fails = true)
        val file = tempFile()
        val finder = ArtFinder({ listOf(offline) }, ArtIndex(file))
        assertNull(finder.find(query))
        assertNull(finder.find(query))
        assertEquals(2, offline.asked)

        val empty = Scripted("empty", null)
        val quiet = ArtFinder({ listOf(empty) }, ArtIndex(file))
        quiet.find(query)
        quiet.find(query)
        assertEquals(1, empty.asked)
        quiet.forgetMisses()
        quiet.find(query)
        assertEquals(2, empty.asked)
    }

    @Test
    fun twoAsksAtOnceShareOneLookup() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val slow = object : ArtSource {
            var asked = 0
            override val id = "slow"
            override fun handles(query: ArtQuery) = true
            override suspend fun find(query: ArtQuery): ArtSet? {
                asked++
                gate.await()
                return ArtSet(id, "https://a/cover.png")
            }
        }
        val finder = ArtFinder({ listOf(slow) }, ArtIndex(tempFile()))
        val hero = async { finder.find(query) }
        val tile = async { finder.find(query) }
        yield()
        gate.complete(Unit)
        assertEquals("https://a/cover.png", hero.await()?.cover)
        assertEquals("https://a/cover.png", tile.await()?.cover)
        assertEquals(1, slow.asked)
    }

    @Test
    fun anOldMissIsAskedAgain() = runBlocking {
        var now = 0L
        val empty = Scripted("empty", null)
        val finder = ArtFinder({ listOf(empty) }, ArtIndex(tempFile()) { now })
        finder.find(query)
        now += ArtFinder.MISS_MAX_AGE_MS + 1
        finder.find(query)
        assertEquals(2, empty.asked)
    }

    @Test
    fun nothingIsSentWhileArtworkIsOff() = runBlocking {
        val source = Scripted("s", ArtSet("s", "https://a/cover.png"))
        var on = false
        val finder = ArtFinder({ listOf(source) }, ArtIndex(tempFile())) { on }
        assertNull(finder.find(query))
        assertEquals(0, source.asked)
        on = true
        assertEquals("https://a/cover.png", finder.find(query)?.cover)
    }

    @Test
    fun theIndexSurvivesARestartAndCompacts() {
        val file = tempFile()
        val index = ArtIndex(file)
        repeat(200) { index.put("k", listOf("s", "https://a/$it.png", "https://a/sq\t$it.png", "")) }
        index.put("gone", null)
        val reopened = ArtIndex(file)
        assertEquals(listOf("s", "https://a/199.png", "https://a/sq\t199.png", ""), reopened.hit("k"))
        assertEquals(ArtIndex.Known.Miss, reopened.lookup("gone"))
        assert(file.readLines().size < 200)
        // A line from before this layout is dropped, so it is looked up again.
        file.appendText("old\t1\tlibretro\thttps://a/x.png\t\t\n")
        assertNull(ArtIndex(file).lookup("old"))
    }

    @Test
    fun aSceneTakesEachPartFromTheFirstSourceThatHasIt() = runBlocking {
        val retro = SceneOnly("retro", ArtScene(screen = "https://r/title.png"))
        val grid = SceneOnly("grid", ArtScene(background = "https://g/hero.png", logo = "https://g/logo.png", screen = "https://g/x.png"))
        val never = SceneOnly("never", ArtScene(background = "https://n/hero.png"))
        val finder = ArtFinder({ listOf(retro, grid, never) }, ArtIndex(tempFile()))
        val scene = finder.findScene(query)
        assertEquals(ArtScene("https://g/hero.png", "https://g/logo.png", "https://r/title.png"), scene)
        // Wide art and a logo complete the scene, so the last source is not asked.
        assertEquals(0, never.asked)
        finder.findScene(query)
        assertEquals(1, grid.asked)
        assertEquals(scene, finder.cachedScene(query))
    }

    @Test
    fun aPartFoundBeforeAnOutageIsKept() = runBlocking {
        val retro = SceneOnly("retro", ArtScene(screen = "https://r/title.png"))
        val offline = SceneOnly("offline", null, fails = true)
        val finder = ArtFinder({ listOf(retro, offline) }, ArtIndex(tempFile()))
        assertEquals("https://r/title.png", finder.findScene(query)?.screen)
        finder.findScene(query)
        assertEquals(1, retro.asked)
        val dark = ArtFinder({ listOf(offline) }, ArtIndex(tempFile()))
        assertNull(dark.findScene(query))
        assertNull(dark.findScene(query))
        assertEquals(3, offline.asked)
    }

    @Test
    fun steamTheSceneIsTheLibraryHeroAndLogo() = runBlocking {
        val base = "https://shared.steamstatic.com/store_item_assets/steam/apps/1145350"
        val http = FakeHttp(emptyMap(), mapOf("$base/library_hero.jpg" to 200, "$base/logo.png" to 200))
        assertEquals(
            ArtScene(background = "$base/library_hero.jpg", logo = "$base/logo.png"),
            SteamStoreArt(http).scene(ArtQuery("k", "Hades II", "pc", steamAppId = 1145350)),
        )
        assertNull(SteamStoreArt(FakeHttp(emptyMap())).scene(ArtQuery("k", "Racer", "pc", steamAppId = 4078430)))
    }

    @Test
    fun steamArtTakesTheSharpestCapsuleThere() = runBlocking {
        val base = "https://shared.steamstatic.com/store_item_assets/steam/apps/1245620"
        val http = FakeHttp(emptyMap(), mapOf("$base/library_600x900.jpg" to 200))
        val found = SteamStoreArt(http).find(ArtQuery("k", "Elden Ring", "pc", steamAppId = 1245620))
        assertEquals("$base/library_600x900.jpg", found?.cover)
        assertEquals("$base/library_hero.jpg", found?.background)
        assertEquals(listOf("$base/library_600x900_2x.jpg", "$base/library_600x900.jpg"), http.heads)
        assertNull(SteamStoreArt(FakeHttp(emptyMap())).find(ArtQuery("k", "Old", "pc", steamAppId = 10)))
    }

    private class SceneOnly(
        override val id: String,
        private val answer: ArtScene?,
        private val fails: Boolean = false,
    ) : ArtSource {
        var asked = 0

        override fun handles(query: ArtQuery): Boolean = true

        override suspend fun find(query: ArtQuery): ArtSet? = null

        override suspend fun scene(query: ArtQuery): ArtScene? {
            asked++
            if (fails) throw java.io.IOException("offline")
            return answer
        }
    }

    private fun tempFile(): File = File(Files.createTempDirectory("art").toFile(), "index.tsv")

    private class Scripted(
        override val id: String,
        private val answer: ArtSet?,
        private val fails: Boolean = false,
    ) : ArtSource {
        var asked = 0

        override fun handles(query: ArtQuery): Boolean = true

        override suspend fun find(query: ArtQuery): ArtSet? {
            asked++
            if (fails) throw java.io.IOException("offline")
            return answer
        }
    }
}
