package app.foldcade

import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.PluginException
import app.foldcade.host.PluginCallException
import app.foldcade.host.PluginHost
import app.foldcade.plugins.romm.RommArtwork
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import okio.Buffer

/**
 * Cover addresses for library games, from the metadata providers.
 * [cached] is what a provider already holds. [fetch] asks each provider once
 * per game, off the main thread. A game with no cover is remembered, so it is
 * not asked again.
 */
class CoverArt(private val plugins: PluginHost) {
    private val fetched = ConcurrentHashMap<String, String>()

    fun cached(game: Game): String? {
        fetched[keyOf(game)]?.let { return it.ifEmpty { null } }
        return plugins.metadataIds().firstNotNullOfOrNull { id ->
            quietly { plugins.cachedMetadata(id, game) }?.cover()
        }
    }

    suspend fun fetch(game: Game): String? {
        val key = keyOf(game)
        fetched[key]?.let { return it.ifEmpty { null } }
        val cover = plugins.metadataIds().firstNotNullOfOrNull { id ->
            quietly { plugins.fetchMetadata(id, game) }?.cover()
        }
        fetched[key] = cover.orEmpty()
        return cover
    }

    private fun keyOf(game: Game): String = game.backendId + "\u0000" + game.remoteKey

    private fun GameMeta.cover(): String? = artwork.firstOrNull { it.role == ArtworkRole.Cover }?.uri

    /** A provider that fails has no cover for this game. Cancellation still propagates. */
    private inline fun <T> quietly(block: () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: PluginException) {
        null
    } catch (_: PluginCallException) {
        null
    }
}

/** A cover address a metadata provider gave. [CoverFetcher] loads it. */
data class CoverImage(val uri: String)

/**
 * Loads a [CoverImage] through RomM's client. That client refuses an address
 * off the RomM origin, so a cover hosted anywhere else does not load.
 */
class CoverFetcher(
    private val data: CoverImage,
    private val options: Options,
    private val artwork: RommArtwork,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = artwork.load(data.uri) ?: throw IllegalStateException("RomM is not set up")
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val artwork: RommArtwork) : Fetcher.Factory<CoverImage> {
        override fun create(data: CoverImage, options: Options, imageLoader: ImageLoader): Fetcher =
            CoverFetcher(data, options, artwork)
    }
}

object CoverKeyer : Keyer<CoverImage> {
    override fun key(data: CoverImage, options: Options): String = data.uri
}
