package app.foldcade

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** The tree grant a document uses, independent of its folder inside the tree. */
internal fun folderGrantUri(uri: String): String {
    val parsed = URI(uri)
    val parts = parsed.rawPath.orEmpty().split('/')
    val tree = parts.indexOf("tree")
    if (tree < 0 || tree + 1 >= parts.size) return uri
    return "${parsed.scheme}://${parsed.rawAuthority}/tree/${encodeDocumentId(decodeDocumentId(parts[tree + 1]))}"
}

/** A save folder roots at its document, or at its tree when no document is named. */
internal fun folderDocumentId(uri: String): String {
    val parts = URI(uri).rawPath.orEmpty().split('/')
    val document = parts.indexOf("document").takeIf { it >= 0 } ?: parts.indexOf("tree")
    require(document >= 0 && document + 1 < parts.size)
    return decodeDocumentId(parts[document + 1])
}

internal fun folderTreeId(uri: String): String = folderDocumentId(folderGrantUri(uri))

internal fun folderDocumentUri(tree: String, documentId: String): String =
    "${folderGrantUri(tree)}/document/${encodeDocumentId(documentId)}"

internal fun primaryFolderDocument(path: String): String =
    "content://com.android.externalstorage.documents/document/${encodeDocumentId("primary:$path")}"

/** Document ids contain a volume and path segments; a name prefix alone is not an ancestor. */
internal fun containsFolder(parent: String, child: String): Boolean {
    if (parent.substringBefore(':') != child.substringBefore(':')) return false
    val path = parent.substringAfter(':', "").trimEnd('/')
    val below = child.substringAfter(':', "").trimEnd('/')
    return path.isEmpty() || below == path || below.startsWith("$path/")
}

private fun decodeDocumentId(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
/** As Android's `Uri.encode`, which leaves `!'()*~` as they are, so a rebuilt tree URI matches the grant's. */
private fun encodeDocumentId(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    .replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~")
