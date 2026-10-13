package app.foldcade.artwork

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ArtCandidatesTest {
    private val api = "https://www.steamgriddb.com/api/v2"
    private val safe = "types=static&nsfw=false&humor=false"
    private class Http(val responses: Map<String, String>) : ArtHttp {
        val calls = mutableListOf<String>()
        override suspend fun get(url: String, headers: Map<String, String>): ArtResponse {
            assertEquals(mapOf("Authorization" to "Bearer key"), headers)
            assertTrue(url.startsWith("https://www.steamgriddb.com/api/v2/"))
            calls += url
            return responses[url]?.let { ArtResponse(200, it.toByteArray()) } ?: ArtResponse(404, byteArrayOf())
        }
        override suspend fun status(url: String) = 404
    }

    @Test fun candidateFixturesKeepThumbnailsSkipUnsafeUrlsAndDeduplicate() = runBlocking {
        val http = Http(mapOf(
            "$api/games/steam/42" to """{"data":{"id":8}}""",
            "$api/grids/game/8?dimensions=512x512,1024x1024&$safe" to """{"data":[
                {"id":1,"url":"https://cdn2.steamgriddb.com/grid/1.png","thumb":"https://cdn2.steamgriddb.com/thumb/1.png"},
                {"id":2,"url":"http://unsafe"},
                {"id":3,"url":"https://cdn2.steamgriddb.com/grid/1.png"},
                {"id":4,"url":"https://cdn2.steamgriddb.com/grid/4.png","thumb":"http://unsafe"},
                {"id":5}
            ]}""",
        ))
        val result = SteamGridDb(http) { "key" }.candidates(ArtQuery("game", "Game", steamAppId = 42), "grids")
        assertEquals(listOf(
            GridCandidate("1", "https://cdn2.steamgriddb.com/grid/1.png", "https://cdn2.steamgriddb.com/thumb/1.png"),
            GridCandidate("4", "https://cdn2.steamgriddb.com/grid/4.png", "https://cdn2.steamgriddb.com/grid/4.png"),
        ), result)
    }

    @Test fun pickerReusesTheAutomaticMatchForAllKinds() = runBlocking {
        val http = Http(mapOf(
            "$api/search/autocomplete/Game" to """{"data":[{"id":1,"name":"Game Two"},{"id":2,"name":"Game"}]}""",
            "$api/grids/game/2?dimensions=600x900&$safe" to """{"data":[{"url":"https://cdn/auto"}]}""",
            "$api/heroes/game/2?$safe" to """{"data":[{"id":9,"url":"https://cdn/hero","thumb":"https://cdn/thumb"}]}""",
            "$api/logos/game/2?$safe" to """{"data":[{"id":10,"url":"https://cdn/logo"}]}""",
        ))
        val source = SteamGridDb(http) { "key" }
        val query = ArtQuery("game", "Game")
        source.find(query)
        assertEquals("https://cdn/hero", source.candidates(query, "heroes").single().url)
        assertEquals("https://cdn/logo", source.candidates(query, "logos").single().url)
        assertEquals(1, http.calls.count { it.contains("autocomplete") })
    }

    @Test fun fallbackUsesTheFirstResultWithoutChangingAutomaticMatching() = runBlocking {
        val http = Http(mapOf(
            "$api/search/autocomplete/Game" to """{"data":[{"id":1,"name":"Game Deluxe"},{"id":2,"name":"Game Two"}]}""",
            "$api/heroes/game/1?$safe" to """{"data":[{"id":9,"url":"https://cdn/hero"}]}""",
        ))
        val source = SteamGridDb(http) { "key" }
        val query = ArtQuery("game", "Game")
        assertEquals("https://cdn/hero", source.candidates(query, "heroes").single().url)
        assertNull(source.find(query))
        assertTrue(SteamGridDb(http) { null }.candidates(query, "heroes").isEmpty())
    }

    @Test fun missingArtAndRefusedKeysStayDistinct() = runBlocking {
        assertTrue(SteamGridDb(Http(emptyMap())) { "key" }.candidates(ArtQuery("k", "Missing"), "heroes").isEmpty())
        val refused = object : ArtHttp {
            override suspend fun get(url: String, headers: Map<String, String>) = ArtResponse(401, byteArrayOf())
            override suspend fun status(url: String) = 401
        }
        try {
            SteamGridDb(refused) { "key" }.candidates(ArtQuery("k", "Game"), "logos")
            fail("A refused key must be an error")
        } catch (_: SteamGridDbKeyRefused) { }
    }
}
