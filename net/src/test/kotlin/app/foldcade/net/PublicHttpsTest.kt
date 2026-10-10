package app.foldcade.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PublicHttpsTest {
    private val hosts = setOf("thumbnails.libretro.com", ".steamgriddb.com")

    @Test
    fun onlyHttpsToAListedHostIsAllowed() {
        PublicHttps(hosts).use { http ->
            assertTrue(http.allows("https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20DS/Named_Boxarts/x.png"))
            assertTrue(http.allows("https://cdn2.steamgriddb.com/grid/abc.png"))
            assertTrue(http.allows("https://www.steamgriddb.com/api/v2/search/autocomplete/tetris"))
            assertFalse(http.allows("http://thumbnails.libretro.com/x.png"))
            assertFalse(http.allows("https://steamgriddb.com.evil.example/x.png"))
            assertFalse(http.allows("https://evil.example/x.png"))
            assertFalse(http.allows("https://notsteamgriddb.com/x.png"))
            assertFalse(http.allows("not a url"))
        }
    }

    @Test
    fun aRefusedAddressFailsBeforeAnyRequest() = runBlocking {
        PublicHttps(hosts).use { http ->
            try {
                http.get("http://thumbnails.libretro.com/x.png")
                fail("plain http was fetched")
            } catch (_: PublicHttpsException) {
            }
        }
    }

    @Test
    fun aDotEntryMatchesSubdomainsOnly() {
        assertTrue(hostAllowed(hosts, "cdn2.steamgriddb.com"))
        assertTrue(hostAllowed(hosts, "CDN2.SteamGridDB.com."))
        assertFalse(hostAllowed(hosts, "steamgriddb.com"))
        assertTrue(hostAllowed(hosts, "thumbnails.libretro.com"))
        assertFalse(hostAllowed(hosts, "x.thumbnails.libretro.com"))
    }
}
