package app.foldcade.localfolder

import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import java.util.Locale

/**
 * One document in a SAF tree.
 * [documentUri] is the document URI, [displayName] is DISPLAY_NAME,
 * and [mimeType] is MIME_TYPE.
 * [text] is a playlist body the caller already read. Other files leave it null.
 */
data class FolderEntry(
    val documentUri: String,
    val displayName: String,
    val mimeType: String,
    val text: String? = null,
)

/** `DocumentsContract.Document.MIME_TYPE_DIR`. */
const val DOCUMENT_DIRECTORY_MIME: String = "vnd.android.document/directory"

fun FolderEntry.isDirectory(): Boolean =
    mimeType == DOCUMENT_DIRECTORY_MIME || mimeType == "inode/directory"

/**
 * A game file found in the tree.
 * [documentUri] is the local-folder remote key.
 * [title] is the file name without its extension or its No-Intro tags.
 * [fileName] keeps the whole name, tags and extension included.
 * [folderName] is the display name of the directory that contains the file.
 * The hero uses it as the short text. A file with no containing directory has none.
 */
data class FolderGame(
    val documentUri: String,
    val fileName: String,
    val title: String,
    val platformId: String,
    val folderName: String = "",
)

data class FolderScan(
    val games: List<FolderGame>,
    val platforms: List<FolderPlatform>,
    val cancelled: Boolean = false,
    val steamShortcuts: List<SteamShortcut> = emptyList(),
)

/**
 * A `.steam` file: the convention frontends such as Cocoon, Daijishou, and
 * ES-DE read, and GameNative exports. The body is the Steam app id. The file
 * name is the game's name, with `_` where the store name had a `:`.
 * The shell plays these through GameNative, not as a folder game.
 */
data class SteamShortcut(
    val documentUri: String,
    val title: String,
    val appId: Int,
)

/** The [SteamShortcut] in [entry], or null when it is not a `.steam` file with an app id. */
fun steamShortcutOf(entry: FolderEntry): SteamShortcut? {
    val name = entry.displayName.trim()
    if (fileExtension(name) != "steam") return null
    val appId = Regex("""\d+""").find(entry.text.orEmpty())?.value?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val title = titleFromFileName(name).replace("_ ", ": ").trim().ifEmpty { return null }
    return SteamShortcut(documentUri = entry.documentUri, title = title, appId = appId)
}

/**
 * Walk a SAF-style document tree and classify game files.
 *
 * [childrenOf] lists the documents directly inside a directory, the same
 * shape as a SAF children query. [isCancelled] stops the walk before the
 * next document. Games already recorded stay in the result, and
 * [FolderScan.cancelled] is true.
 *
 * The nearest platform folder wins for extensions that folder accepts,
 * including zip and 7z. A unique extension still classifies a file that
 * was filed under a different platform. Identity is the document URI.
 * This function does not read a filesystem path.
 *
 * An .m3u whose folder accepts it is the game. Files named in
 * [FolderEntry.text] are left out. A missing body leaves those files in place.
 */
fun scanFolderTree(
    root: FolderEntry,
    isCancelled: () -> Boolean = { false },
    childrenOf: (FolderEntry) -> List<FolderEntry>,
): FolderScan {
    val found = LinkedHashMap<String, FolderGame>()
    val steam = LinkedHashMap<String, SteamShortcut>()
    val playlists = LinkedHashMap<String, String>()
    val parentOf = HashMap<String, String>()
    val childrenByParent = HashMap<String, List<ChildRef>>()
    val seen = HashSet<String>()
    val stack = ArrayDeque<Frame>()
    stack.addLast(Frame(root, folderPlatform = null, folderName = ""))
    var cancelled = false

    while (stack.isNotEmpty()) {
        if (isCancelled()) {
            cancelled = true
            break
        }
        val frame = stack.removeLast()
        val uri = frame.entry.documentUri.trim()
        if (uri.isEmpty() || !seen.add(uri)) continue

        if (frame.entry.isDirectory()) {
            if (isSkippedFolder(frame.entry.displayName)) continue
            val folderPlatform = platformFromFolderName(frame.entry.displayName)
                ?: frame.folderPlatform
            val children = childrenOf(frame.entry)
            val listed = ArrayList<ChildRef>(children.size)
            for (child in children) {
                val childUri = child.documentUri.trim()
                if (childUri.isEmpty()) continue
                listed += ChildRef(childUri, child.displayName.trim(), child.isDirectory())
                parentOf.putIfAbsent(childUri, uri)
            }
            childrenByParent[uri] = listed
            val containing = frame.entry.displayName
            for (index in children.lastIndex downTo 0) {
                stack.addLast(Frame(children[index], folderPlatform, containing))
            }
        } else {
            val shortcut = steamShortcutOf(frame.entry)
            if (shortcut != null) {
                steam.putIfAbsent(uri, shortcut)
                continue
            }
            val game = toGame(frame.entry, uri, frame.folderPlatform, frame.folderName) ?: continue
            if (found.putIfAbsent(game.documentUri, game) != null) continue
            val body = frame.entry.text
            if (fileExtension(game.fileName) == "m3u" && !body.isNullOrBlank()) {
                playlists[game.documentUri] = body
            }
        }
    }

    if (!cancelled) hideListedDiscs(found, playlists, parentOf, childrenByParent)
    return finish(found.values, cancelled).copy(steamShortcuts = steam.values.sortedBy { it.title.lowercase(Locale.ROOT) })
}

private class Frame(
    val entry: FolderEntry,
    val folderPlatform: FolderPlatform?,
    val folderName: String,
)

private class ChildRef(
    val uri: String,
    val name: String,
    val directory: Boolean,
)

private val SKIPPED_FOLDERS: Set<String> = setOf(
    "bios",
    "firmware",
    "saves",
    "save",
    "states",
    "state",
    "savestates",
    "save states",
    "cheats",
    "artwork",
    "covers",
    "media",
    "thumbnails",
)

private fun isSkippedFolder(name: String): Boolean =
    normalizeFolderName(name) in SKIPPED_FOLDERS

private fun toGame(
    entry: FolderEntry,
    uri: String,
    folder: FolderPlatform?,
    folderName: String,
): FolderGame? {
    val name = entry.displayName.trim()
    if (name.isEmpty() || name.startsWith('.')) return null
    val extension = fileExtension(name) ?: return null
    if (extension == "md" && normalizeFolderName(fileStem(name)) in DOC_STEMS) {
        return null
    }
    val platform = resolvePlatform(extension, folder) ?: return null
    return FolderGame(
        documentUri = uri,
        fileName = name,
        title = titleFromFileName(name),
        platformId = platform.id,
        folderName = folderName,
    )
}

private fun hideListedDiscs(
    found: MutableMap<String, FolderGame>,
    playlists: Map<String, String>,
    parentOf: Map<String, String>,
    childrenByParent: Map<String, List<ChildRef>>,
) {
    if (playlists.isEmpty()) return
    val hidden = HashSet<String>()
    for ((uri, text) in playlists) {
        val start = parentOf[uri] ?: continue
        for (segments in playlistPaths(text)) {
            val target = listedUri(start, segments, parentOf, childrenByParent) ?: continue
            if (target != uri) hidden += target
        }
    }
    for (uri in hidden) found.remove(uri)
}

/** Playlist lines, as path segments. Comments, blanks, and remote URLs are skipped. */
private fun playlistPaths(text: String): List<List<String>> {
    var body = text
    if (body.startsWith("\uFEFF")) body = body.substring(1)
    val paths = ArrayList<List<String>>()
    for (raw in body.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        val decoded = percentDecode(line.removeSurrounding("\"").removeSurrounding("'"))
        if ("://" in decoded) continue
        val segments = decoded.split('/', '\\')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." }
        if (segments.isNotEmpty()) paths += segments
    }
    return paths
}

private fun percentDecode(raw: String): String {
    if ('%' !in raw) return raw
    val buffer = ByteArrayOutputStream(raw.length)
    var index = 0
    while (index < raw.length) {
        val current = raw[index]
        if (
            current == '%' &&
            index + 2 < raw.length &&
            isHex(raw[index + 1]) &&
            isHex(raw[index + 2])
        ) {
            buffer.write(raw.substring(index + 1, index + 3).toInt(16))
            index += 3
        } else {
            buffer.write(current.toString().toByteArray(Charsets.UTF_8))
            index += 1
        }
    }
    return buffer.toString(Charsets.UTF_8)
}

private fun isHex(char: Char): Boolean =
    char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

/**
 * Document named by one playlist line, starting at the playlist's directory.
 * A relative walk wins. A line that leaves the tree through `..` matches nothing.
 * A line whose first folder is not a child matches the file name in that directory,
 * which covers a dumped absolute path.
 */
private fun listedUri(
    start: String,
    segments: List<String>,
    parentOf: Map<String, String>,
    childrenByParent: Map<String, List<ChildRef>>,
): String? {
    walkPlaylist(start, segments, parentOf, childrenByParent)?.let { return it }
    if (segments.any { it == ".." }) return null
    val children = childrenByParent[start].orEmpty()
    if (segments.size > 1 && matchChild(children, segments.first(), directory = true) != null) {
        return null
    }
    return matchChild(children, segments.last(), directory = false)?.uri
}

private fun walkPlaylist(
    start: String,
    segments: List<String>,
    parentOf: Map<String, String>,
    childrenByParent: Map<String, List<ChildRef>>,
): String? {
    var dir = start
    for (index in segments.indices) {
        val segment = segments[index]
        val last = index == segments.lastIndex
        if (segment == "..") {
            if (last) return null
            dir = parentOf[dir] ?: return null
            continue
        }
        val children = childrenByParent[dir].orEmpty()
        if (last) return matchChild(children, segment, directory = false)?.uri
        dir = matchChild(children, segment, directory = true)?.uri ?: return null
    }
    return null
}

private fun matchChild(children: List<ChildRef>, name: String, directory: Boolean): ChildRef? {
    val sameKind = children.filter { it.directory == directory }
    return sameKind.firstOrNull { it.name == name }
        ?: sameKind.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

private fun finish(games: Collection<FolderGame>, cancelled: Boolean): FolderScan {
    val names = PlatformCatalog.byId.mapValues { (_, spec) -> spec.platform.name }
    val sorted = games.sortedWith(
        compareBy(
            { names.getValue(it.platformId).lowercase(Locale.ROOT) },
            { it.title.lowercase(Locale.ROOT) },
            { it.documentUri },
        ),
    )
    val platforms = sorted
        .map { it.platformId }
        .distinct()
        .map { PlatformCatalog.byId.getValue(it).platform }
        .sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) }, { it.id }))
    return FolderScan(
        games = sorted,
        platforms = platforms,
        cancelled = cancelled,
    )
}
