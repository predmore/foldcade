package app.foldcade

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PlayerIntent

/**
 * Turns a plugin intent into a start.
 * The read grant is applied when the plugin asked for it.
 * This function does not name a player or a save path.
 * [folderAuthority] is [FoldcadeApp.rommFolderAuthority], see [grantFolderTree].
 */
fun PlayerIntent.toAndroidIntent(folderAuthority: String? = null): Intent {
    val intent = Intent(action)
    intent.component = ComponentName(packageName, componentClass)
    if (dataUri != null && mimeType != null) {
        intent.setDataAndType(Uri.parse(dataUri), mimeType)
    } else if (dataUri != null) {
        intent.data = Uri.parse(dataUri)
    } else if (mimeType != null) {
        intent.type = mimeType
    }
    if (grantReadUri) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.grantFolderTree(dataUri, folderAuthority)
    }
    flags.forEach { flag ->
        when (flag) {
            LaunchFlag.GrantWriteUri -> intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            LaunchFlag.NewTask -> intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            LaunchFlag.ClearTop -> intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            LaunchFlag.ClearTask -> intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            else -> Unit
        }
    }
    extras.forEach { extra ->
        when (extra) {
            is PlayerExtra.Text -> intent.putExtra(extra.key, extra.value)
            is PlayerExtra.Integer -> intent.putExtra(extra.key, extra.value)
            is PlayerExtra.LongValue -> intent.putExtra(extra.key, extra.value)
            is PlayerExtra.BooleanFlag -> intent.putExtra(extra.key, extra.value)
            else -> Unit
        }
    }
    return intent
}

/**
 * The tree to grant along with [dataUri] when it is a file in one of Foldcade's own
 * download folders, `content://<folderAuthority>/tree/<folder>/document/<file>`.
 * Null for any other URI. The grant covers that one folder, so the player can
 * list and open the files beside the game, and nothing outside it.
 */
internal fun folderTreeOf(dataUri: String, folderAuthority: String): String? {
    val prefix = "content://$folderAuthority/tree/"
    if (!dataUri.startsWith(prefix)) return null
    val segments = dataUri.removePrefix(prefix).substringBefore('?').substringBefore('#').split('/')
    if (segments.size != 3 || segments[0].isEmpty() || segments[1] != "document" || segments[2].isEmpty()) return null
    return prefix + segments[0]
}

/**
 * The tree to grant along with [dataUri] when it is a sheet that names the files
 * beside it, a `.cue`, `.gdi`, `.ccd` or `.m3u`, as a document in a tree the user
 * picked, `content://<authority>/tree/<tree>/document/<file>`. A player opens those
 * files through that same tree, by their own ids, and no grant Foldcade can pass on
 * covers them but one on the whole tree: the folder the user picked for games. It
 * is for reading, and lasts as long as the player's start. Null for any other URI.
 */
internal fun sheetTreeOf(dataUri: String): String? {
    if (!dataUri.startsWith(CONTENT_SCHEME)) return null
    val segments = dataUri.removePrefix(CONTENT_SCHEME).substringBefore('?').substringBefore('#').split('/')
    if (segments.size != 5 || segments[0].isEmpty() || segments[1] != "tree" || segments[2].isEmpty()) return null
    if (segments[3] != "document" || segments[4].isEmpty()) return null
    val document = try {
        java.net.URLDecoder.decode(segments[4].replace("+", "%2B"), Charsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        return null
    }
    val name = document.substringAfterLast('/').substringAfterLast(':')
    if (name.substringAfterLast('.', "").lowercase() !in SHEET_EXTENSIONS) return null
    return "$CONTENT_SCHEME${segments[0]}/tree/${segments[2]}"
}

/**
 * Adds a read grant on [dataUri]'s folder when [folderTreeOf] names one, or on the
 * tree a sheet's files are in, see [sheetTreeOf]. The tree rides in the clip data,
 * and the prefix flag lets the grant cover the files in it.
 * Call this only where read on [dataUri] is already granted.
 */
internal fun Intent.grantFolderTree(dataUri: String?, folderAuthority: String?) {
    if (dataUri == null) return
    val tree = folderAuthority?.let { folderTreeOf(dataUri, it) } ?: sheetTreeOf(dataUri) ?: return
    clipData = ClipData.newRawUri(null, Uri.parse(tree))
    addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
}

/**
 * The content URI of [name] beside the file [uri] names, when [uri] is a document
 * in a tree: a RomM download's folder, or a folder the user picked. The tree
 * segment is kept as [uri] wrote it, so a grant on that tree still covers the
 * file. Null when [name] is not a single file name, [uri] is not a document in
 * a tree, or the file would sit outside that tree.
 */
internal fun siblingContentUri(uri: String, name: String): String? {
    if (!isSiblingName(name) || !uri.startsWith(CONTENT_SCHEME)) return null
    val path = uri.removePrefix(CONTENT_SCHEME).substringBefore('?').substringBefore('#')
    val slash = path.indexOf('/')
    if (slash <= 0) return null
    val authority = path.substring(0, slash)
    val segments = path.substring(slash + 1).split('/')
    if (segments.size != 4 || segments[0] != "tree" || segments[2] != "document") return null
    if (segments[1].isEmpty() || segments[3].isEmpty() || authority.isEmpty()) return null
    val tree = percentDecode(segments[1]) ?: return null
    val document = percentDecode(segments[3]) ?: return null
    if (!documentInTree(tree, document)) return null
    val parent = documentParent(document) ?: return null
    val sibling = if (parent.endsWith(":")) parent + name else "$parent/$name"
    if (sibling == document || !documentInTree(tree, sibling)) return null
    return "$CONTENT_SCHEME$authority/tree/${segments[1]}/document/${percentEncode(sibling)}"
}

/** A file name in one folder: not empty, not `.` or `..`, and with no slash. */
internal fun isSiblingName(name: String): Boolean =
    name.isNotEmpty() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }

/** The folder of a storage document id, `primary:Games/file` or `romm/roms/1/file`. */
private fun documentParent(document: String): String? {
    val slash = document.lastIndexOf('/')
    if (slash > 0) return document.substring(0, slash)
    val colon = document.indexOf(':')
    if (colon >= 0 && colon < document.lastIndex) return document.substring(0, colon + 1)
    return null
}

private fun documentInTree(tree: String, document: String): Boolean {
    if (document == tree || document.startsWith("$tree/")) return true
    if (!tree.endsWith(":") || !document.startsWith(tree) || document.length == tree.length) return false
    return document[tree.length] != '/'
}

/** RFC 3986 percent-decoding as UTF-8. A `+` stays a plus. Null for a broken escape. */
private fun percentDecode(text: String): String? {
    val out = java.io.ByteArrayOutputStream(text.length)
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (char == '%') {
            if (index + 2 >= text.length) return null
            val byte = text.substring(index + 1, index + 3).toIntOrNull(16) ?: return null
            out.write(byte)
            index += 3
        } else {
            out.write(char.toString().toByteArray(Charsets.UTF_8))
            index += 1
        }
    }
    return out.toString(Charsets.UTF_8.name())
}

private fun percentEncode(text: String): String {
    val bytes = text.toByteArray(Charsets.UTF_8)
    val out = StringBuilder(bytes.size)
    for (byte in bytes) {
        val value = byte.toInt() and 0xFF
        if (value in PERCENT_UNRESERVED) out.append(value.toChar()) else out.append("%").append("%02X".format(value))
    }
    return out.toString()
}

private val PERCENT_UNRESERVED: Set<Int> = buildSet {
    for (char in 'A'..'Z') add(char.code)
    for (char in 'a'..'z') add(char.code)
    for (char in '0'..'9') add(char.code)
    add('-'.code)
    add('.'.code)
    add('_'.code)
    add('~'.code)
}

private const val CONTENT_SCHEME = "content://"

/** Sheets that name other files beside them: CUE, Dreamcast GDI, CloneCD, and a playlist of discs. */
private val SHEET_EXTENSIONS = setOf("cue", "gdi", "ccd", "m3u")
