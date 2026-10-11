package app.foldcade.romm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class RommCoverSearchTest {
    @Test
    fun searchesByTitleWithTheExistingTokenAndNoLibraryEntry() = runBlocking {
        MockRomm().use { server ->
            server.route("GET", "/api/search/cover") { exchange, _ ->
                json(exchange, 200, """[
                  {"provider":"sgdb","name":"Pokémon & Friends","resources":[
                    {"thumb":"https://cdn2.steamgriddb.com/thumb.png",
                     "url":"https://cdn2.steamgriddb.com/cover.png","type":"static",
                     "width":600,"height":900,"nsfw":false,"humor":false,"epilepsy":false,
                     "style":"alternate","author":"artist","score":10}
                  ]},
                  {"provider":"steam","name":"Pokémon & Friends","resources":[]}
                ]""")
            }
            RommClient(server.origin, { "rmm_existing" }).use { client ->
                val matches = client.searchCovers("  Pokémon & Friends  ")
                assertEquals(listOf("Pokémon & Friends", "Pokémon & Friends"), matches.map { it.name })
                assertEquals(
                    RomCoverResource("https://cdn2.steamgriddb.com/cover.png", "static", 600, 900, false, false, false),
                    matches.first().resources.single(),
                )
                assertTrue(matches.last().resources.isEmpty())
            }
            val request = server.recorded.single()
            assertEquals("GET", request.method)
            assertEquals("/api/search/cover", request.path)
            assertEquals("search_term=Pokémon & Friends", URLDecoder.decode(request.query, "UTF-8"))
            assertEquals(
                "Bearer rmm_existing",
                request.headers.entries.first { it.key.equals("Authorization", ignoreCase = true) }.value.single(),
            )
            server.assertKnownPaths()
        }
    }

    @Test
    fun emptyTitlesSendNothingAndAuthFailuresAreNotReadAsMisses() = runBlocking {
        MockRomm().use { server ->
            server.route("GET", "/api/search/cover") { exchange, _ ->
                json(exchange, 401, """{"detail":"Invalid token"}""")
            }
            RommClient(server.origin, { "rmm_expired" }).use { client ->
                assertTrue(client.searchCovers("  ").isEmpty())
                assertTrue(server.recorded.isEmpty())
                val failure = runCatching { client.searchCovers("Game") }.exceptionOrNull()
                assertTrue(failure is RommUnauthorized)
            }
            server.assertKnownPaths()
        }
    }

    @Test
    fun malformedSearchDataFailsRatherThanCachingAMiss() = runBlocking {
        MockRomm().use { server ->
            server.route("GET", "/api/search/cover") { exchange, _ ->
                json(exchange, 200, """{"items":[]}""")
            }
            RommClient(server.origin, { "rmm_existing" }).use { client ->
                assertTrue(runCatching { client.searchCovers("Game") }.exceptionOrNull() is RommResponseException)
            }
            server.assertKnownPaths()
        }
    }
}
