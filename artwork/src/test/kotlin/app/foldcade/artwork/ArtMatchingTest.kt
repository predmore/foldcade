package app.foldcade.artwork

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ArtMatchingTest {
    @get:Rule val temp = TemporaryFolder()
    private val title = "The Disney Collection - QuackShot Starring Donald Duck"
    private val api = "https://www.steamgriddb.com/api/v2"
    private fun search(title: String) = "$api/search/autocomplete/${URLEncoder.encode(title, StandardCharsets.UTF_8).replace("+", "%20") }"

    @Test fun compilationPrefixesAndSubtitlesProduceUsefulSearches() {
        assertEquals(listOf(title, "QuackShot Starring Donald Duck"), artworkSearchTitles(title))
        assertEquals(listOf("Collection: QuackShot", "QuackShot"), artworkSearchTitles("Collection: QuackShot"))
        assertEquals(listOf("Sonic"), artworkSearchTitles(" Sonic "))
        assertTrue(artworkSearchTitles(" ").isEmpty())
    }

    @Test fun actualGameAndNearSuffixMatchesBeatPackagingWords() {
        val collection = GridGame(1, "The Disney Afternoon Collection")
        val game = GridGame(2, "QuackShot Starring Donald Duck")
        assertEquals(game, rankedArtworkGames(title, listOf(collection, game)).first())
        assertEquals(GridGame(3, "QuackShot"), rankedArtworkGames(title, listOf(collection, GridGame(3, "QuackShot"))).first())
        assertEquals(1.0, artworkMatchScore("Pokémon & Friends", "The Pokemon and Friends"), 0.0)
        assertEquals(GridGame(4, title), rankedArtworkGames(title, listOf(game, GridGame(4, title))).first())
        val many = (1..20).map { GridGame(it, "Other $it") }
        assertEquals(many.take(8), rankedArtworkGames(title, many + many))
    }

    @Test fun aPoorFullTitleSearchAlsoAsksForTheGameAfterTheSeparator() = runBlocking {
        val calls = mutableListOf<String>()
        val http = object : ArtHttp {
            override suspend fun get(url: String, headers: Map<String, String>): ArtResponse {
                calls += url
                val json = when (url) {
                    search(title) -> """{"data":[{"id":1,"name":"The Disney Afternoon Collection"}]}"""
                    search("QuackShot Starring Donald Duck") -> """{"data":[{"id":2,"name":"QuackShot Starring Donald Duck"}]}"""
                    else -> """{"data":[]}"""
                }
                return ArtResponse(200, json.toByteArray())
            }
            override suspend fun status(url: String) = 404
        }
        val source = SteamGridDb(http) { "key" }
        val games = source.games(ArtQuery("game", title))
        assertEquals(listOf(2, 1), games.map { it.id })
        assertEquals(listOf(search(title), search("QuackShot Starring Donald Duck")), calls)
        source.games(ArtQuery("game", title))
        assertEquals(2, calls.size)
    }

    @Test fun anExactFullTitleDoesNotMakeUnnecessarySuffixRequests() = runBlocking {
        val calls = mutableListOf<String>()
        val source = SteamGridDb(object : ArtHttp {
            override suspend fun get(url: String, headers: Map<String, String>): ArtResponse {
                calls += url
                return ArtResponse(200, """{"data":[{"id":1,"name":"$title"}]}""".toByteArray())
            }
            override suspend fun status(url: String) = 404
        }) { "key" }
        assertEquals(1, source.games(ArtQuery("game", title)).single().id)
        assertEquals(listOf(search(title)), calls)
    }

    @Test fun selectedMatchBypassesSearchAndOldCacheForEveryAutomaticKindAcrossRestarts() = runBlocking {
        val calls = mutableListOf<String>()
        val source = SteamGridDb(object : ArtHttp {
            override suspend fun get(url: String, headers: Map<String, String>): ArtResponse {
                calls += url
                val type = when {
                    "/grids/" in url -> "cover"
                    "/heroes/" in url -> "hero"
                    "/logos/" in url -> "logo"
                    else -> error("A saved match must not search: $url")
                }
                return ArtResponse(200, """{"data":[{"url":"https://cdn/$type"}]}""".toByteArray())
            }
            override suspend fun status(url: String) = 404
        }) { "key" }
        val previous = object : ArtSource {
            override val id = "previous"
            override fun handles(query: ArtQuery) = true
            override suspend fun find(query: ArtQuery) = ArtSet(id, "https://cdn/wrong")
            override suspend fun scene(query: ArtQuery) = ArtScene("https://cdn/wrong-hero", "https://cdn/wrong-logo")
        }
        val index = temp.newFile()
        val scenes = temp.newFile()
        val finder = ArtFinder({ listOf(previous, source) }, ArtIndex(index), ArtIndex(scenes))
        val old = ArtQuery("game", title)
        assertEquals("https://cdn/wrong", finder.find(old)?.cover)
        assertEquals("https://cdn/wrong-hero", finder.findScene(old)?.background)
        val chosen = old.copy(steamGridDbGameId = 2)
        assertNull(finder.cached(chosen))
        assertEquals("https://cdn/cover", finder.find(chosen)?.square)
        assertEquals(ArtScene("https://cdn/hero", "https://cdn/logo"), finder.findScene(chosen))
        val restarted = ArtFinder({ listOf(previous, source) }, ArtIndex(index), ArtIndex(scenes))
        assertEquals("https://cdn/cover", restarted.cached(chosen)?.square)
        assertEquals("https://cdn/hero", restarted.cachedScene(chosen)?.background)
        assertEquals("https://cdn/wrong", restarted.cached(old)?.cover)
        assertEquals(4, calls.size)
        assertTrue(calls.all { it.contains("/game/2?") })
    }

    @Test fun steamIdentityStaysFirstWhenTheDisplayTitleMatchesAnotherGame() = runBlocking {
        val source = SteamGridDb(object : ArtHttp {
            override suspend fun get(url: String, headers: Map<String, String>): ArtResponse = ArtResponse(200,
                (if (url.endsWith("games/steam/42")) """{"data":{"id":8,"name":"Actual Steam Game"}}"""
                else """{"data":[{"id":1,"name":"Alias"}]}""").toByteArray())
            override suspend fun status(url: String) = 404
        }) { "key" }
        assertEquals(listOf(8, 1), source.games(ArtQuery("game", "Alias", steamAppId = 42)).map { it.id })
    }

}
