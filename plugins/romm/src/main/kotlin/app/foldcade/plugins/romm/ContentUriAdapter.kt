package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * Turns a cache file into the content URI a player opens.
 * [uriFor] and [pathFor] are the same mapping.
 * The URI is `content://<authority>/romm/` plus the path under the cache root, or
 * `content://<authority>/romm-data/` plus the path under the data root. The host
 * serves both with a FileProvider whose authority is `<application id>.romm.cache`.
 */
interface ContentUriAdapter {
    fun uriFor(path: Path): String

    fun pathFor(uri: String): Path?
}

/**
 * [root] is the evictable download cache. [dataRoot] holds saves, which must
 * survive a cache clear. A null [dataRoot] maps only the cache.
 */
class CachePathContentUri(
    private val root: Path,
    private val authority: String = ROMM_CONTENT_AUTHORITY,
    private val dataRoot: Path? = null,
) : ContentUriAdapter {
    override fun uriFor(path: Path): String {
        val absolute = path.toAbsolutePath().normalize()
        val cacheBase = root.toAbsolutePath().normalize()
        val dataBase = dataRoot?.toAbsolutePath()?.normalize()
        // The deeper root wins when one holds the other. The cache wins a tie.
        val inData = dataBase != null && absolute.startsWith(dataBase) &&
            (!absolute.startsWith(cacheBase) || dataBase.nameCount > cacheBase.nameCount)
        val (prefix, relative) = if (inData) {
            DATA_PATH to relativeTo(dataBase!!, absolute)
        } else {
            CACHE_PATH to relativeTo(cacheBase, absolute)
        }
        val encoded = relative.joinToString("/") { segment -> encode(segment.toString()) }
        return "content://$authority/$prefix/$encoded"
    }

    override fun pathFor(uri: String): Path? {
        val cache = "content://$authority/$CACHE_PATH/"
        val data = "content://$authority/$DATA_PATH/"
        val (base, encoded) = when {
            uri.startsWith(cache) -> root to uri.removePrefix(cache)
            dataRoot != null && uri.startsWith(data) -> dataRoot to uri.removePrefix(data)
            else -> return null
        }
        if (encoded.isEmpty()) return null
        val segments = encoded.split("/")
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        val relative = segments.map { decode(it) }
        if (relative.any { it.isEmpty() || it == "." || it == ".." || it.contains('/') || it.contains('\\') }) {
            return null
        }
        return relative.fold(base) { parent, segment -> parent.resolve(segment) }.normalize()
    }

    private fun relativeTo(base: Path, absolute: Path): Path {
        val normalized = base.toAbsolutePath().normalize()
        if (!absolute.startsWith(normalized) || absolute == normalized) {
            throw PluginException.NotFound("RomM cache file is outside the cache")
        }
        val relative = normalized.relativize(absolute)
        if (relative.any { it.toString() == ".." }) {
            throw PluginException.NotFound("RomM cache file is outside the cache")
        }
        return relative
    }

    private fun encode(segment: String): String =
        URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")

    private fun decode(segment: String): String =
        URLDecoder.decode(segment, StandardCharsets.UTF_8)
}

internal const val CACHE_PATH = "romm"
internal const val DATA_PATH = "romm-data"
