package app.foldcade.artwork

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SteamGridDbTest {
    private val api = "https://www.steamgriddb.com/api/v2"
    private val safe = "types=static&nsfw=false&humor=false"

    private fun ok(json: String) = ArtResponse(200, json.toByteArray())

    private fun grids(gameId: Int, cover: String, square: String?) = mapOf(
        "$api/grids/game/$gameId?dimensions=600x900&$safe" to ok("""{"success":true,"data":[{"id":1,"url":"$cover"}]}"""),
        "$api/grids/game/$gameId?dimensions=512x512,1024x1024&$safe" to
            ok(if (square == null) """{"success":true,"data":[]}""" else """{"success":true,"data":[{"id":2,"url":"$square"}]}"""),
    )

    @Test
    fun aSteamGameIsFoundByItsAppIdWithTheKeyOnlyOnTheApi() = runBlocking {
        val http = FakeHttp(
            mapOf("$api/games/steam/1245620" to ok("""{"success":true,"data":{"id":5262,"name":"ELDEN RING"}}""")) +
                grids(5262, "https://cdn2.steamgriddb.com/grid/cover.png", "https://cdn2.steamgriddb.com/grid/square.png"),
        )
        val found = SteamGridDb(http) { "k3y" }.find(ArtQuery("k", "Elden Ring", "pc", steamAppId = 1245620))
        assertEquals("https://cdn2.steamgriddb.com/grid/cover.png", found?.cover)
        assertEquals("https://cdn2.steamgriddb.com/grid/square.png", found?.square)
        assertTrue(http.sent.all { (url, headers) -> url.startsWith(api) && headers["Authorization"] == "Bearer k3y" })
    }

    @Test
    fun aTitleSearchPrefersTheExactName() = runBlocking {
        val http = FakeHttp(
            mapOf(
                "$api/search/autocomplete/Forza%20Horizon%206" to ok(
                    """{"success":true,"data":[{"id":1,"name":"Forza Horizon 5"},{"id":2,"name":"Forza Horizon 6"}]}""",
                ),
            ) + grids(2, "https://cdn2.steamgriddb.com/grid/fh6.png", null),
        )
        val found = SteamGridDb(http) { "k" }.find(ArtQuery("k", "Forza Horizon 6"))
        assertEquals("https://cdn2.steamgriddb.com/grid/fh6.png", found?.cover)
        assertNull(found?.square)
    }

    @Test
    fun noKeyHandlesNothingAndARefusedKeyThrows() = runBlocking {
        assertFalse(SteamGridDb(FakeHttp(emptyMap())) { null }.handles(ArtQuery("k", "Anything")))
        val refused = FakeHttp(mapOf("$api/search/autocomplete/Game" to ArtResponse(401, ByteArray(0))))
        try {
            SteamGridDb(refused) { "bad" }.find(ArtQuery("k", "Game"))
            fail("a refused key was read as a miss")
        } catch (_: SteamGridDbKeyRefused) {
        }
    }

    @Test
    fun aKeyIsCheckedWithOneSearch() = runBlocking {
        val url = "$api/search/autocomplete/tetris"
        assertEquals(true, SteamGridDb.accepts(FakeHttp(mapOf(url to ok("""{"success":true,"data":[]}"""))), "k"))
        assertEquals(false, SteamGridDb.accepts(FakeHttp(mapOf(url to ArtResponse(401, ByteArray(0)))), "k"))
        assertEquals(null, SteamGridDb.accepts(FakeHttp(emptyMap()), "k"))
    }
}
