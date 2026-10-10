package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * Turns a cache file into the content URI a player opens.
 * [uriFor] and [pathFor] are the same mapping.
 * The URI is `content://<authority>/romm/` plus the path under the cache root. The host
 * serves it with a FileProvider whose authority is `<application id>.romm.cache`.
 */
interface ContentUriAdapter {
    fun uriFor(path: Path): String

    fun pathFor(uri: String): Path?
}

class CachePathContentUri(
    private val root: Path,
    private val authority: String = ROMM_CONTENT_AUTHORITY,
) : ContentUriAdapter {
    override fun uriFor(path: Path): String {
        val relative = relativeToRoot(path)
        val encoded = relative.joinToString("/") { segment -> encode(segment.toString()) }
        return "content://$authority/$CACHE_PATH/$encoded"
    }

    override fun pathFor(uri: String): Path? {
        val prefix = "content://$authority/$CACHE_PATH/"
        if (!uri.startsWith(prefix)) return null
        val encoded = uri.removePrefix(prefix)
        if (encoded.isEmpty()) return null
        val segments = encoded.split("/")
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        val relative = segments.map { decode(it) }
        if (relative.any { it.isEmpty() || it == "." || it == ".." || it.contains('/') || it.contains('\\') }) {
            return null
        }
        return relative.fold(root) { parent, segment -> parent.resolve(segment) }.normalize()
    }

    private fun relativeToRoot(path: Path): Path {
        val base = root.toAbsolutePath().normalize()
        val absolute = path.toAbsolutePath().normalize()
        if (!absolute.startsWith(base) || absolute == base) {
            throw PluginException.NotFound("RomM cache file is outside the cache")
        }
        val relative = base.relativize(absolute)
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
