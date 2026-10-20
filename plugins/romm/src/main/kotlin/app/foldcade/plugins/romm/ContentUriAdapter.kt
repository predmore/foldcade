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
 * A host that keeps downloads in shared storage names that root with its own
 * prefix, such as [ROMM_MEDIA_PATH].
 */
interface ContentUriAdapter {
    fun uriFor(path: Path): String

    fun pathFor(uri: String): Path?

    /**
     * The same download as [uriFor], as a document in a tree that is its folder,
     * so a player can list the files beside it: a game's update and DLC. A host
     * with no provider for that keeps this default, the plain [uriFor].
     */
    fun folderUriFor(path: Path): String = uriFor(path)
}

/**
 * [root] holds downloads, which can be downloaded again. [dataRoot] holds saves,
 * which must survive a cache clear. A null [dataRoot] maps only the downloads.
 * [cachePath] is the URI's first segment for [root].
 *
 * [folderAuthority] serves [folderUriFor]: a documents provider for the download
 * folders, with the document ids of [RomFolderDocuments]. Null keeps [folderUriFor]
 * the plain [uriFor].
 */
class CachePathContentUri(
    private val root: Path,
    private val authority: String = ROMM_CONTENT_AUTHORITY,
    private val dataRoot: Path? = null,
    private val cachePath: String = CACHE_PATH,
    private val folderAuthority: String? = null,
) : ContentUriAdapter {
    private val folders = RomFolderDocuments(mapOf(cachePath to root))

    /** `content://<folderAuthority>/tree/<folder id>/document/<file id>`. */
    override fun folderUriFor(path: Path): String {
        val folderAuthority = folderAuthority ?: return uriFor(path)
        val document = folders.documentId(path)
            ?: throw PluginException.NotFound("RomM cache file is outside the cache")
        val folder = document.substringBeforeLast('/')
        return "content://$folderAuthority/tree/${encode(folder)}/document/${encode(document)}"
    }

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
            cachePath to relativeTo(cacheBase, absolute)
        }
        val encoded = relative.joinToString("/") { segment -> encode(segment.toString()) }
        return "content://$authority/$prefix/$encoded"
    }

    override fun pathFor(uri: String): Path? {
        if (folderAuthority != null && uri.startsWith("content://$folderAuthority/")) {
            val segments = uri.removePrefix("content://$folderAuthority/").substringBefore('?').split('/')
            if (segments.size != 4 || segments[0] != "tree" || segments[2] != "document") return null
            val folder = decode(segments[1])
            val document = decode(segments[3])
            if (!RomFolderDocuments.isInside(folder, document)) return null
            return folders.pathFor(document)
        }
        val cache = "content://$authority/$cachePath/"
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

/**
 * Document ids for download folders a documents provider serves: a root's name,
 * such as [ROMM_MEDIA_PATH], then the path under that root, joined by `/`. A
 * folder's id is the start of its files' ids, so a file's folder is its id up to
 * the last `/`. [roots] maps each name to its root.
 */
class RomFolderDocuments(private val roots: Map<String, Path>) {
    /** Null when [path] is not below one of [roots]. */
    fun documentId(path: Path): String? {
        val absolute = path.toAbsolutePath().normalize()
        // The deeper root wins when one holds the other.
        val (name, root) = roots.entries
            .map { it.key to it.value.toAbsolutePath().normalize() }
            .filter { (_, root) -> absolute.startsWith(root) && absolute != root }
            .maxByOrNull { (_, root) -> root.nameCount } ?: return null
        val relative = root.relativize(absolute).map { it.toString() }
        if (relative.any { it.isEmpty() || it == "." || it == ".." }) return null
        return (listOf(name) + relative).joinToString("/")
    }

    /** Null for an id that names no root, or one that would leave its root. */
    fun pathFor(documentId: String): Path? {
        val segments = documentId.split('/')
        val root = roots[segments.first()] ?: return null
        val relative = segments.drop(1)
        if (relative.isEmpty()) return root.toAbsolutePath().normalize()
        if (relative.any { it.isEmpty() || it == "." || it == ".." || it.contains('\\') }) return null
        return relative.fold(root) { parent, segment -> parent.resolve(segment) }.toAbsolutePath().normalize()
    }

    companion object {
        /** True when [documentId] is [folderId] or below it. */
        fun isInside(folderId: String, documentId: String): Boolean =
            documentId == folderId || documentId.startsWith("$folderId/")
    }
}

internal const val CACHE_PATH = "romm"
internal const val DATA_PATH = "romm-data"

/** The URI segment for downloads a host keeps in its shared media folder. */
const val ROMM_MEDIA_PATH = "romm-media"
