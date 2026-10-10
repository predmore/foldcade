package app.foldcade

import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.PluginException
import app.foldcade.host.PluginCallException
import app.foldcade.host.PluginHost
import app.foldcade.artwork.ArtFinder
import app.foldcade.artwork.ArtQuery
import app.foldcade.artwork.ArtScene
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
 * Art for tiles and the top screen. A metadata provider's art for the library
 * record comes first, so RomM's own art wins. Then [art] asks the public
 * sources with what the shell knows about the tile.
 *
 * [cached] and [cachedScene] are what is already known. [fetch] and
 * [fetchScene] ask, off the main thread. A provider is asked once per game;
 * [art] keeps its own record.
 */
class CoverArt(private val plugins: PluginHost, private val art: ArtFinder) {
    /** What a provider gave per game. A held null is a provider with nothing for it. */
    private class Held(val meta: GameMeta?)

    private val held = ConcurrentHashMap<String, Held>()

    fun cached(record: Game?, query: ArtQuery?): ArtSet? =
        record?.let(::cachedMeta)?.cover()?.let { ArtSet(PROVIDER, it) } ?: query?.let(art::cached)

    suspend fun fetch(record: Game?, query: ArtQuery?): ArtSet? =
        record?.let { fetchMeta(it) }?.cover()?.let { ArtSet(PROVIDER, it) } ?: query?.let { art.find(it) }

    /** The top screen's scene: a provider's parts first, then the public sources' for what is missing. */
    fun cachedScene(record: Game?, query: ArtQuery?): ArtScene? =
        merged(record?.let(::cachedMeta)?.scene(), query?.let(art::cachedScene))

    suspend fun fetchScene(record: Game?, query: ArtQuery?): ArtScene? {
        val provided = record?.let { fetchMeta(it) }?.scene()
        if (provided?.complete == true) return provided
        return merged(provided, query?.let { art.findScene(it) })
    }

    private fun merged(provided: ArtScene?, found: ArtScene?): ArtScene? =
        (provided ?: ArtScene()).or(found).takeUnless { it.isEmpty }

    private fun cachedMeta(game: Game): GameMeta? {
        held[keyOf(game)]?.let { return it.meta }
        return plugins.metadataIds().firstNotNullOfOrNull { id ->
            quietly { plugins.cachedMetadata(id, game) }?.takeIf { it.artwork.isNotEmpty() }
        }
    }

    private suspend fun fetchMeta(game: Game): GameMeta? {
        val key = keyOf(game)
        held[key]?.let { return it.meta }
        val meta = plugins.metadataIds().firstNotNullOfOrNull { id ->
            quietly { plugins.fetchMetadata(id, game) }?.takeIf { it.artwork.isNotEmpty() }
        }
        held[key] = Held(meta)
        return meta
    }

    private companion object {
        const val PROVIDER = "provider"
    }

    private fun keyOf(game: Game): String = game.backendId + "\u0000" + game.remoteKey

    private fun GameMeta.role(role: ArtworkRole): String? = artwork.firstOrNull { it.role == role }?.uri

    private fun GameMeta.cover(): String? = role(ArtworkRole.Cover)

    private fun GameMeta.scene(): ArtScene? =
        ArtScene(role(ArtworkRole.Background), role(ArtworkRole.Logo), role(ArtworkRole.Screenshot)).takeUnless { it.isEmpty }

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
