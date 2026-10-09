package app.foldcade.localfolder

import java.util.ArrayDeque
import java.util.Locale

/**
 * One document in a SAF tree.
 * [documentUri] is the document URI, [displayName] is DISPLAY_NAME,
 * and [mimeType] is MIME_TYPE.
 */
data class FolderEntry(
    val documentUri: String,
    val displayName: String,
    val mimeType: String,
)

/** `DocumentsContract.Document.MIME_TYPE_DIR`. */
const val DOCUMENT_DIRECTORY_MIME: String = "vnd.android.document/directory"

fun FolderEntry.isDirectory(): Boolean =
    mimeType == DOCUMENT_DIRECTORY_MIME || mimeType == "inode/directory"

/**
 * A game file found in the tree.
 * [documentUri] is the local-folder remote key.
 * [title] is the file name without its last extension. Nothing is scraped.
 */
data class FolderGame(
    val documentUri: String,
    val fileName: String,
    val title: String,
    val platformId: String,
)

data class FolderScan(
    val games: List<FolderGame>,
    val platforms: List<FolderPlatform>,
    val cancelled: Boolean = false,
)

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
 */
fun scanFolderTree(
    root: FolderEntry,
    isCancelled: () -> Boolean = { false },
    childrenOf: (FolderEntry) -> List<FolderEntry>,
): FolderScan {
    val found = LinkedHashMap<String, FolderGame>()
    val seen = HashSet<String>()
    val stack = ArrayDeque<Frame>()
    stack.addLast(Frame(root, folderPlatform = null))
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
            for (index in children.lastIndex downTo 0) {
                stack.addLast(Frame(children[index], folderPlatform))
            }
        } else {
            val game = toGame(frame.entry, uri, frame.folderPlatform) ?: continue
            found.putIfAbsent(game.documentUri, game)
        }
    }

    return finish(found.values, cancelled)
}

private class Frame(
    val entry: FolderEntry,
    val folderPlatform: FolderPlatform?,
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

private fun toGame(entry: FolderEntry, uri: String, folder: FolderPlatform?): FolderGame? {
    val name = entry.displayName.trim()
    if (name.isEmpty() || name.startsWith('.')) return null
    val extension = fileExtension(name) ?: return null
    if (extension == "md" && normalizeFolderName(titleFromFileName(name)) in DOC_STEMS) {
        return null
    }
    val platform = resolvePlatform(extension, folder) ?: return null
    return FolderGame(
        documentUri = uri,
        fileName = name,
        title = titleFromFileName(name),
        platformId = platform.id,
    )
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
