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
    /** A picked tree root, including roots below a synthetic forest. */
    val isScanRoot: Boolean = false,
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
    /** The name exactly as the folder lists it, spaces included, which a player names its save after. */
    val displayName: String = fileName,
    /** Path below the nearest system folder, including subdirectories and the ROM extension. */
    val relativePath: String = fileName,
    /** The actual system directory, never inferred from an opaque document id. */
    val systemUri: String = "",
)

data class FolderScan(
    val games: List<FolderGame>,
    val platforms: List<FolderPlatform>,
    val cancelled: Boolean = false,
    val steamShortcuts: List<SteamShortcut> = emptyList(),
    /** Media directories and gamelists to read after publishing the game list. */
    val mediaSources: List<FolderMediaSource> = emptyList(),
    val mediaSystems: List<FolderMediaSystem> = emptyList(),
)

/**
 * A store export file (`.steam`, `.epic`, `.gog`, `.amazon`, `.pcgame`): the convention frontends such as Cocoon, Daijishou, and
 * ES-DE read, and GameNative and DroidDeck export. The body is the game id.
 * The file name is the game's name, with `_` where the store name had a `:`.
 * The shell plays these through GameNative, or Steam exports through DroidDeck.
 *
 * [gameId] is canonical decimal: a Steam app id, or the 64-bit id Steam gives a
 * game added outside Steam, which only DroidDeck writes. [fromDroidDeck] is true
 * when the file sits beside [DROIDDECK_INVENTORY], so DroidDeck's game-file sync wrote it.
 */
data class SteamShortcut(
    val documentUri: String,
    val title: String,
    val gameId: String,
    val fromDroidDeck: Boolean = false,
    val gameSource: String = "STEAM",
) {
    /** [gameId] as a Steam app id. Null for an id too large to be one. */
    val appId: Int? get() = gameId.toIntOrNull()
}

/**
 * The inventory DroidDeck's game-file sync keeps in the folder it writes,
 * from DroidDeck `GameFiles.INVENTORY`.
 */
const val DROIDDECK_INVENTORY: String = ".droiddeck-sync.json"

/**
 * The store export in [entry], or null when it has no supported store and game id.
 * The id is the first run of digits, without leading zeros, up to the largest unsigned 64-bit value.
 */
fun steamShortcutOf(entry: FolderEntry, fromDroidDeck: Boolean = false): SteamShortcut? {
    val name = entry.displayName.trim()
    val extension = fileExtension(name) ?: return null
    val source = gameNativeExportSource(extension) ?: return null
    if (source != "STEAM") {
        val id = entry.text?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val title = titleFromFileName(name).replace("_ ", ": ").trim().ifEmpty { return null }
        return SteamShortcut(entry.documentUri, title, id.toString(), gameSource = source)
    }
    val digits = Regex("""\d+""").find(entry.text.orEmpty())?.value?.trimStart('0') ?: return null
    val gameId = digits.takeIf { it.isNotEmpty() && it.length <= 20 && it.toULongOrNull() != null } ?: return null
    val title = titleFromFileName(name).replace("_ ", ": ").trim().ifEmpty { return null }
    return SteamShortcut(documentUri = entry.documentUri, title = title, gameId = gameId, fromDroidDeck = fromDroidDeck)
}

/**
 * Upstream FrontendSyncManager.extensionFor + syncGame: decimal Int body, store in
 * extension. CUSTOM_GAME uses .pcgame. No path or executable belongs in these files.
 * https://github.com/utkarshdalal/GameNative/blob/a2a121bb477070026fed8f6ee66f8d3e8b350971/app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt#L107-L114
 */
fun gameNativeExportSource(extension: String): String? = when (extension.lowercase(Locale.ROOT)) {
    "steam" -> "STEAM"
    "epic" -> "EPIC"
    "gog" -> "GOG"
    "amazon" -> "AMAZON"
    "pcgame" -> "CUSTOM_GAME"
    else -> null
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
    val mediaSources = ArrayList<FolderMediaSource>()
    val mediaSystems = LinkedHashMap<Pair<String, String>, FolderMediaSystem>()
    val seen = HashSet<String>()
    val stack = ArrayDeque<Frame>()
    stack.addLast(Frame(root, folderPlatform = null, folderName = "", droidDeckFolder = false, parent = null, system = null, relativePath = ""))
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
            val name = frame.entry.displayName.lowercase(Locale.ROOT)
            val scanRoot = frame.parent == null || frame.entry.isScanRoot
            val sourceKind = when {
                name == "downloaded_media" -> MediaSourceKind.DownloadedMedia
                name == "gamelists" -> MediaSourceKind.Gamelists
                !scanRoot && name == "media" -> MediaSourceKind.Media
                !scanRoot && mediaRole(name) != null -> MediaSourceKind.Role
                else -> null
            }
            if (sourceKind != null) {
                mediaSources += FolderMediaSource(frame.entry, sourceKind, frame.folderPlatform?.id,
                    frame.system ?: frame.parent, frame.relativePath.substringBeforeLast('/', ""))
                continue
            }
            if (!scanRoot && (name in IGNORED_MEDIA_FOLDERS || isSkippedFolder(frame.entry.displayName))) continue
            // ES-DE's data directories can contain theme/demo ROM-shaped assets.
            if (frame.parent?.displayName.equals("ES-DE", ignoreCase = true) && name in ES_DE_DATA_FOLDERS) continue
            val detected = platformFromFolderName(frame.entry.displayName)
            val folderPlatform = detected ?: frame.folderPlatform
            val system = if (detected != null) frame.entry else frame.system
            val relative = if (detected != null) "" else frame.relativePath
            if (system != null && folderPlatform != null) {
                mediaSystems[folderPlatform.id to system.documentUri] = FolderMediaSystem(folderPlatform.id, system)
            }
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
            val droidDeck = listed.any { !it.directory && it.name == DROIDDECK_INVENTORY }
            for (index in children.lastIndex downTo 0) {
                val child = children[index]
                stack.addLast(Frame(child, folderPlatform, containing, droidDeck, frame.entry, system,
                    if (system == null || relative.isEmpty()) child.displayName else "$relative/${child.displayName}"))
            }
        } else {
            val shortcut = steamShortcutOf(frame.entry, frame.droidDeckFolder)
            if (shortcut != null) {
                steam.putIfAbsent(uri, shortcut)
                continue
            }
            if (frame.entry.displayName.equals("gamelist.xml", ignoreCase = true)) {
                mediaSources += FolderMediaSource(frame.entry, MediaSourceKind.Gamelist, frame.folderPlatform?.id,
                    frame.system ?: frame.parent, frame.relativePath.substringBeforeLast('/', ""))
                continue
            }
            val game = toGame(frame.entry, uri, frame.folderPlatform, frame.folderName)?.copy(
                relativePath = if (frame.system == null) frame.entry.displayName else frame.relativePath,
                systemUri = (frame.system ?: frame.parent)?.documentUri.orEmpty(),
            ) ?: continue
            val system = frame.system ?: frame.parent
            if (system != null) mediaSystems[game.platformId to system.documentUri] = FolderMediaSystem(game.platformId, system)
            if (found.putIfAbsent(game.documentUri, game) != null) continue
            val body = frame.entry.text
            if (fileExtension(game.fileName) == "m3u" && !body.isNullOrBlank()) {
                playlists[game.documentUri] = body
            }
        }
    }

    if (!cancelled) hideListedDiscs(found, playlists, parentOf, childrenByParent)
    return finish(found.values, cancelled).copy(
        steamShortcuts = steam.values.sortedBy { it.title.lowercase(Locale.ROOT) },
        mediaSources = mediaSources,
        mediaSystems = mediaSystems.values.toList(),
    )
}

private class Frame(
    val entry: FolderEntry,
    val folderPlatform: FolderPlatform?,
    val folderName: String,
    /** The containing directory holds [DROIDDECK_INVENTORY]. */
    val droidDeckFolder: Boolean,
    val parent: FolderEntry?,
    val system: FolderEntry?,
    val relativePath: String,
)

private class ChildRef(
    val uri: String,
    val name: String,
    val directory: Boolean,
)

private val ES_DE_DATA_FOLDERS = setOf("themes", "custom_systems", "collections", "resources", "scripts", "screensavers", "settings")

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
    val extension = fileExtension(name) ?: return null ?: return null
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
        displayName = entry.displayName,
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
