package app.foldcade.plugins.romm

import app.foldcade.artwork.ArtFinder
import app.foldcade.artwork.ArtHttp
import app.foldcade.artwork.ArtIndex
import app.foldcade.artwork.ArtQuery
import app.foldcade.artwork.ArtResponse
import app.foldcade.artwork.ArtScene
import app.foldcade.artwork.ArtSet
import app.foldcade.artwork.ArtSource
import app.foldcade.artwork.SteamStoreArt
import app.foldcade.net.PublicHttps
import app.foldcade.artwork.ART_HOSTS
import app.foldcade.romm.RomCoverMatch
import app.foldcade.romm.RomCoverResource
import app.foldcade.romm.RommUnavailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RommCoverArtTest {
    private val root = Files.createTempDirectory("romm-cover-art")
    private val publicImages = PublicHttps(ART_HOSTS)
    private val ops = ScriptedOps()
    private val tokens = mutableListOf<String?>()
    private val wiring = RommWiring(
        "https://romm.example", RommTokenSource { "rmm_existing" }, root, root.resolve("data"),
    ).apply {
        open = { _, token -> tokens += token(); ops }
    }
    private val source = RommCoverArt(publicImages::allows) { wiring }
    private val query = ArtQuery("gamenative:steam:42", "Pokémon & Friends", "pc", steamAppId = 42)
    private val cdn = "https://cdn2.steamgriddb.com/"

    @After
    fun tearDown() = runBlocking {
        source.disconnect()
        publicImages.close()
        root.toFile().deleteRecursively()
        Unit
    }

    @Test
    fun aGameOutsideRommUsesItsTitleAndReusesTheExistingToken() = runBlocking {
        val searched = mutableListOf<String>()
        ops.onSearchCovers = { title ->
            searched += title
            listOf(
                match("Pokémon & Friends Deluxe", cover("wrong.png")),
                match("Pokemon and Friends", cover("square.png", 512, 512), cover("portrait.png")),
            )
        }
        assertEquals(ArtSet(source.id, cdn + "portrait.png", cdn + "square.png"), source.find(query))
        source.find(query)
        assertEquals(listOf(query.title, query.title), searched)
        assertEquals(listOf("rmm_existing"), tokens)
        assertNull(source.find(query.copy(title = "Friends")))
        assertNull(source.scene(query))
        assertEquals(3, searched.size)
    }

    @Test
    fun animatedFlaggedAndDisallowedImagesAreSkipped() = runBlocking {
        ops.onSearchCovers = {
            listOf(match(query.title,
                cover("animated.gif").copy(type = "animated"),
                cover("adult.png").copy(nsfw = true),
                cover("joke.png").copy(humor = true),
                cover("flashing.png").copy(epilepsy = true),
                cover("outside.png").copy(url = "https://untrusted.example/cover.png"),
                cover("cleartext.png").copy(url = "http://cdn2.steamgriddb.com/cover.png"),
                cover("safe.png"),
            ))
        }
        assertEquals(cdn + "safe.png", source.find(query)?.cover)
    }

    @Test
    fun theServersProviderPriorityWinsAndSquareOnlyArtIsUsable() = runBlocking {
        ops.onSearchCovers = {
            listOf(
                match(query.title, cover("first.png", 342, 482)),
                match(query.title, cover("second.png")),
            )
        }
        assertEquals(cdn + "first.png", source.find(query)?.cover)
        ops.onSearchCovers = { listOf(match(query.title, cover("square.png", 1024, 1024))) }
        assertEquals(ArtSet(source.id, cdn + "square.png", cdn + "square.png"), source.find(query))
    }

    @Test
    fun steamWinsThenRommThenTheDirectProviderAndHitsSurviveRestart() = runBlocking {
        var steamPresent = true
        val steam = SteamStoreArt(object : ArtHttp {
            override suspend fun get(url: String, headers: Map<String, String>) = error("unused")
            override suspend fun status(url: String) = if (steamPresent) 200 else 404
        })
        var searches = 0
        ops.onSearchCovers = { searches++; listOf(match(query.title, cover("romm.png"))) }
        var directCalls = 0
        val direct = object : ArtSource {
            override val id = "steamgriddb"
            override fun handles(query: ArtQuery) = true
            override suspend fun find(query: ArtQuery): ArtSet {
                directCalls++
                return ArtSet(id, cdn + "direct.png")
            }
            override suspend fun scene(query: ArtQuery) = ArtScene(logo = cdn + "logo.png")
        }
        val file = File(root.toFile(), "index.tsv")
        val finder = ArtFinder({ listOf(steam, source, direct) }, ArtIndex(file))
        assertEquals("steam", finder.find(query)?.source)
        assertEquals(0, searches)
        steamPresent = false
        val missingSteam = query.copy(key = "missing-steam")
        assertEquals(source.id, finder.find(missingSteam)?.source)
        assertEquals(0, directCalls)
        val restarted = ArtFinder({ listOf(steam, source, direct) }, ArtIndex(file))
        assertEquals(cdn + "romm.png", restarted.find(missingSteam)?.cover)
        assertEquals(1, searches)
        assertEquals(cdn + "logo.png", finder.findScene(missingSteam)?.logo)
        assertEquals(1, searches)
        ops.onSearchCovers = { emptyList() }
        assertEquals("steamgriddb", finder.find(query.copy(key = "missing-romm"))?.source)
        assertEquals(1, directCalls)
        ops.onSearchCovers = { throw RommUnavailable("offline") }
        assertEquals("steamgriddb", finder.find(query.copy(key = "offline-romm"))?.source)
        assertEquals(2, directCalls)
    }

    @Test
    fun disabledArtworkAndDisconnectedRommMakeNoSearchRequests() = runBlocking {
        ops.onSearchCovers = { error("must not search") }
        val finder = ArtFinder({ listOf(source) }, ArtIndex(File(root.toFile(), "disabled.tsv"))) { false }
        assertNull(finder.find(query))
        assertTrue(tokens.isEmpty())
        val disconnected = RommCoverArt(publicImages::allows) { null }
        assertFalse(disconnected.handles(query))
        assertNull(disconnected.find(query))
        assertFalse(source.handles(query.copy(title = "  ")))
    }

    @Test
    fun connectingRommRetriesAMissRecordedBeforeTheServerWasAvailable() = runBlocking {
        var current: RommWiring? = null
        val connecting = RommCoverArt(publicImages::allows) { current }
        val finder = ArtFinder({ listOf(connecting) }, ArtIndex(File(root.toFile(), "connect.tsv")))
        ops.onSearchCovers = { listOf(match(query.title, cover("connected.png"))) }
        assertNull(finder.find(query))
        assertTrue(tokens.isEmpty())
        current = wiring
        assertEquals(cdn + "connected.png", finder.find(query)?.cover)
        connecting.disconnect()
    }

    @Test
    fun outagesAreRetriedAndCancellationPropagates() = runBlocking {
        var searches = 0
        ops.onSearchCovers = { searches++; throw RommUnavailable("offline") }
        val finder = ArtFinder({ listOf(source) }, ArtIndex(File(root.toFile(), "retry.tsv")))
        assertNull(finder.find(query))
        assertNull(finder.find(query))
        assertEquals(2, searches)
        ops.onSearchCovers = { throw CancellationException("cancel") }
        assertTrue(runCatching { finder.find(query) }.exceptionOrNull() is CancellationException)
    }

    private fun cover(name: String, width: Int = 600, height: Int = 900) =
        RomCoverResource(cdn + name, "static", width, height, false, false, false)

    private fun match(name: String, vararg resources: RomCoverResource) = RomCoverMatch(name, resources.toList())
}
