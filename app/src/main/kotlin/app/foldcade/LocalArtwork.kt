// SPDX-License-Identifier: MIT

package app.foldcade

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.io.InputStream
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Only an image document actually enumerated by the media index, under a tree
 * still configured as a library. Opaque provider ids are supported: enumeration
 * establishes containment, rather than guessing a path from the document id.
 * Exact tree and authority checks prevent a document from borrowing another grant.
 */
internal fun allowsLibraryArtworkUri(raw: String, trees: Collection<String>, indexedUris: Set<String>): Boolean {
    if (raw !in indexedUris) return false
    return try {
        val uri = URI(raw)
        if (uri.scheme != "content" || uri.rawAuthority.isNullOrEmpty() || uri.rawUserInfo != null ||
            uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) return false
        val parts = uri.rawPath.orEmpty().split('/')
        if (parts.size != 5 || parts[1] != "tree" || parts[3] != "document" || parts[2].isEmpty() || parts[4].isEmpty()) return false
        val treeId = folderTreeId(raw)
        val documentId = folderDocumentId(raw)
        if (uri.rawAuthority == SharedPaths.EXTERNAL_STORAGE_AUTHORITY) {
            if (!containsFolder(treeId, documentId)) return false
            if (documentId.split('/', '\\').any { it == "." || it == ".." } || '\u0000' in documentId) return false
        }
        trees.any { tree ->
            val granted = URI(tree)
            granted.scheme == "content" && granted.rawAuthority == uri.rawAuthority && folderTreeId(tree) == treeId
        }
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: java.net.URISyntaxException) {
        false
    }
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
