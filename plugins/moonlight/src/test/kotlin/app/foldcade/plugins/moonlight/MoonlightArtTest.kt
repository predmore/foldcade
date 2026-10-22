package app.foldcade.plugins.moonlight

import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonlightArtTest {
    private val forza = MoonlightApp(hostUuid = "8FE9E201-578A-7C2C-B017-F2A9A9D86886", appId = "68174274", label = "Forza Horizon 6")
    private val library = MoonlightLibrary().apply { replacePinned(listOf(forza)) }
    private val game = Game(
        backendId = MoonlightLibrary.ID,
        remoteKey = forza.remoteKey,
        platformId = MoonlightPlatform.ID,
        availability = Availability.LocalOnly,
        label = forza.label,
    )

    @Test
    fun asksMoonlightForThePosterWithTheUuidAsStored() = runBlocking {
        val asked = mutableListOf<String>()
        val art = MoonlightArt(library).apply { posterExists = { asked += it; true } }
        val cover = art.fetch(game)!!.artwork.single()
        assertEquals(ArtworkRole.Cover, cover.role)
        // Moonlight's poster files are named by the UUID as it stored it, upper case from Sunshine.
        assertEquals("content://poster.com.limelight/boxart/8FE9E201-578A-7C2C-B017-F2A9A9D86886/68174274", cover.uri)
        assertEquals(cover.uri, art.cached(game)!!.artwork.single().uri)
        art.fetch(game)
        assertEquals(1, asked.size)
    }

    @Test
    fun noPosterMeansNoArtAndIsAskedOnce() = runBlocking {
        var asks = 0
        val art = MoonlightArt(library).apply { posterExists = { asks++; false } }
        assertNull(art.fetch(game))
        assertNull(art.fetch(game))
        assertNull(art.cached(game))
        assertEquals(1, asks)
    }

    @Test
    fun answersOnlyForMoonlightGamesInTheCatalog() = runBlocking {
        val art = MoonlightArt(library).apply { posterExists = { true } }
        assertNull(art.fetch(game.copy(backendId = "romm")))
        assertNull(art.fetch(game.copy(remoteKey = "8fe9e201-578a-7c2c-b017-f2a9a9d86886|1")))
        // Until the app says how to ask, nothing has a poster.
        assertNull(MoonlightArt(library).fetch(game))
    }

    @Test
    fun onlyPosterAddressesAreMoonlights() {
        assertTrue(isMoonlightPosterUri(moonlightPosterUri(forza.hostUuid, forza.appId)))
        assertFalse(isMoonlightPosterUri("content://poster.com.limelight/boxart/../../databases/x/1"))
        assertFalse(isMoonlightPosterUri("content://com.android.contacts/boxart/8FE9E201-578A-7C2C-B017-F2A9A9D86886/1"))
        assertFalse(isMoonlightPosterUri("content://poster.com.limelight.unofficial/boxart/8FE9E201-578A-7C2C-B017-F2A9A9D86886/1"))
    }
}
