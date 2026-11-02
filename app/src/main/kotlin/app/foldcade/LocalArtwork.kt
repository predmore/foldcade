// SPDX-License-Identifier: MIT

package app.foldcade

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Only an image document actually enumerated by the media index, under a tree
 * still configured as a library. Opaque provider ids are supported: enumeration
 * establishes containment, rather than guessing a path from the document id.
 */
internal fun allowsLibraryArtworkUri(raw: String, trees: Collection<String>, indexedUris: Set<String>): Boolean =
    raw in indexedUris && trees.any { tree -> raw.startsWith("${folderGrantUri(tree)}/document/") }

/** The source-purpose suffix belongs only on trees with media and no launchable games. */
internal fun isMediaOnlyLibraryTree(tree: String, sources: Collection<String>, games: Collection<String>): Boolean {
    val grant = folderGrantUri(tree)
    return sources.any { folderGrantUri(it) == grant } && games.none { folderGrantUri(it) == grant }
}

/** Refuses oversized input rather than decoding a truncated image or XML document. */
internal fun readBoundedBytes(stream: InputStream, maxBytes: Int): ByteArray? {
    require(maxBytes in 1 until Int.MAX_VALUE)
    val bytes = stream.readNBytes(maxBytes + 1)
    return bytes.takeIf { it.size <= maxBytes }
}

/** SAF image bytes on IO, under current library grants. No bytes stay in the media index. */
internal suspend fun readLibraryArtwork(
    resolver: ContentResolver,
    uri: String,
    trees: Collection<String>,
    indexedUris: Set<String>,
): ByteArray? = withContext(Dispatchers.IO) {
    if (!allowsLibraryArtworkUri(uri, trees, indexedUris)) return@withContext null
    ensureActive()
    val bytes = try {
        resolver.openInputStream(Uri.parse(uri))?.use { readBoundedBytes(it, MAX_LOCAL_ARTWORK_BYTES) }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
    ensureActive()
    bytes
}

private const val MAX_LOCAL_ARTWORK_BYTES = 20 * 1024 * 1024
