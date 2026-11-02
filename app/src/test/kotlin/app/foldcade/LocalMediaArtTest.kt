package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.artwork.*
import app.foldcade.host.PluginHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalMediaArtTest {
    @get:Rule val temp = TemporaryFolder()
    private val game = Game("local-folder", "content://rom", "snes", Availability.LocalOnly, "Game")
    private val query = ArtQuery("game", "Game", platformId = "snes")
    private class Provider : MetadataProvider {
        override val id = "local.media"
        override val displayName = id
        var meta: GameMeta? = null
        var read: suspend () -> GameMeta? = { meta }
        override fun cached(game: Game) = meta
        override suspend fun fetch(game: Game) = read()
    }
    private fun host(provider: MetadataProvider) = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
        register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val metadataProviders = listOf(provider)
        })
    }

    @Test fun providerArtWorksWithPublicArtOffAndPartialScenesStillMerge() = runBlocking {
        val provider = Provider()
        val cover = "content://library/cover"
        val screen = "content://library/screen"
        provider.meta = GameMeta("Game", artwork = listOf(Artwork(ArtworkRole.Cover, cover), Artwork(ArtworkRole.Screenshot, screen)))
        var allowed = false
        var networkCalls = 0
        val source = object : ArtSource {
            override val id = "public"
            override fun handles(query: ArtQuery) = true
            override suspend fun find(query: ArtQuery): ArtSet? { networkCalls++; return null }
            override suspend fun scene(query: ArtQuery): ArtScene {
                networkCalls++
                return ArtScene(background = "https://public/background", logo = "https://public/logo", screen = "https://public/screen")
            }
        }
        val covers = CoverArt(host(provider), ArtFinder({ listOf(source) }, ArtIndex(temp.newFile()), enabled = { allowed }))
        assertEquals(cover, covers.fetch(game, query)?.cover)
        assertEquals(ArtScene(screen = screen), covers.fetchScene(game, query))
        assertEquals(0, networkCalls)
        allowed = true
        assertEquals(ArtScene("https://public/background", "https://public/logo", screen), covers.fetchScene(game, query))
    }

    @Test fun forgettingDuringAReadCannotRestoreAHeldMiss() = runBlocking {
        val provider = Provider()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        provider.read = { started.complete(Unit); release.await(); null }
        val covers = CoverArt(host(provider), ArtFinder({ emptyList() }, ArtIndex(temp.newFile())))
        val fetched = async { covers.fetch(game, query) }
        started.await()
        provider.meta = GameMeta("Game", artwork = listOf(Artwork(ArtworkRole.Cover, "content://library/new-cover")))
        provider.read = { provider.meta }
        covers.forget(game.backendId) { true }
        release.complete(Unit)
        assertEquals("content://library/new-cover", fetched.await()?.cover)
        assertEquals("content://library/new-cover", covers.cached(game, query)?.cover)
    }
}
