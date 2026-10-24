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

private const val CONTENT_SCHEME = "content://"

/** Sheets that name other files beside them: CUE, Dreamcast GDI, CloneCD, and a playlist of discs. */
private val SHEET_EXTENSIONS = setOf("cue", "gdi", "ccd", "m3u")
