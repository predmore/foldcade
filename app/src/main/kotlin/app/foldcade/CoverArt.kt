package app.foldcade

import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.PluginException
import app.foldcade.host.PluginCallException
import app.foldcade.host.PluginHost
import app.foldcade.artwork.ArtFinder
import app.foldcade.artwork.ArtQuery
import app.foldcade.artwork.ArtSet
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
 * Cover addresses for tiles. A metadata provider's cover for the library
 * record comes first, so RomM's own art wins. Then [art] asks the public
 * sources with what the shell knows about the tile.
 *
 * [cached] is what is already known. [fetch] asks, off the main thread. A
 * provider is asked once per game; [art] keeps its own record.
 */
class CoverArt(private val plugins: PluginHost, private val art: ArtFinder) {
    private val fetched = ConcurrentHashMap<String, String>()

    fun cached(record: Game?, query: ArtQuery?): ArtSet? =
        record?.let(::cachedCover)?.let { ArtSet(PROVIDER, it) } ?: query?.let(art::cached)

    suspend fun fetch(record: Game?, query: ArtQuery?): ArtSet? =
        record?.let { fetchCover(it) }?.let { ArtSet(PROVIDER, it) } ?: query?.let { art.find(it) }

    private fun cachedCover(game: Game): String? {
        fetched[keyOf(game)]?.let { return it.ifEmpty { null } }
        return plugins.metadataIds().firstNotNullOfOrNull { id ->
            quietly { plugins.cachedMetadata(id, game) }?.cover()
        }
    }

    private suspend fun fetchCover(game: Game): String? {
        val key = keyOf(game)
        fetched[key]?.let { return it.ifEmpty { null } }
        val cover = plugins.metadataIds().firstNotNullOfOrNull { id ->
            quietly { plugins.fetchMetadata(id, game) }?.cover()
        }
        fetched[key] = cover.orEmpty()
        return cover
    }

    private companion object {
        const val PROVIDER = "provider"
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
 * Loads a [CoverImage] with [load], which picks the client for its host: RomM's
 * for a RomM address, the public art cache for a public one. Null means the
 * image is not there, or not allowed now.
 */
class CoverFetcher(
    private val data: CoverImage,
    private val options: Options,
    private val load: suspend (String) -> ByteArray?,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = load(data.uri) ?: throw IllegalStateException("No image at ${data.uri}")
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val load: suspend (String) -> ByteArray?) : Fetcher.Factory<CoverImage> {
        override fun create(data: CoverImage, options: Options, imageLoader: ImageLoader): Fetcher =
            CoverFetcher(data, options, load)
    }
}

object CoverKeyer : Keyer<CoverImage> {
    override fun key(data: CoverImage, options: Options): String = data.uri
}
