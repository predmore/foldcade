package app.foldcade

import app.foldcade.language.FolderRef
import app.foldcade.language.SAVE_START_CHECK_MIN_MILLIS
import app.foldcade.plugins.duckstation.DuckStationCardSettings
import app.foldcade.plugins.duckstation.DuckStationPlayer
import java.net.URI
import java.nio.file.Paths

internal const val DUCKSTATION_SHARED_CARD_1 = "shared_card_1.mcd"

internal class DuckStationSaveEvidence(
    private val settings: List<DuckStationCardSettings>,
    private val dataUri: String,
    private val packageName: String,
) {
    val hasPersistentCard1: Boolean get() = settings.any { it.card1Type != "None" && it.card1Type != "NonPersistent" }
    val mayHaveDisabledCard1: Boolean get() = settings.any { it.card1Type == "None" || it.card1Type == "NonPersistent" }

    /** All possible game settings must name the same root before it can stop a launch. */
    fun directory(): FolderRef? {
        val roots = settings.map { it.directory?.let(::folder) }
        return roots.firstOrNull()?.takeIf { root -> roots.all { it?.identity == root.identity } }
    }

    fun sharedCard1(bound: FolderRef): String? =
        settings.map { if (it.card1Type == "Shared") hostedCard(it.card1Path ?: DUCKSTATION_SHARED_CARD_1, it, bound) else null }
            .distinct().singleOrNull()

    fun possibleSharedCards(bound: FolderRef): Set<String> = settings.mapNotNull { setting ->
        setting.card1Path?.let { hostedCard(it, setting, bound) }
    }.toSet() + DUCKSTATION_SHARED_CARD_1

    fun possibleSlot2Cards(bound: FolderRef): Set<String> = settings.mapNotNull { setting ->
        setting.card2Path?.let { hostedCard(it, setting, bound) }
    }.toSet()

    private fun hostedCard(path: String, setting: DuckStationCardSettings, bound: FolderRef): String? {
        val parent = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        if (!name.endsWith(".mcd", ignoreCase = true)) return null
        val root = if (parent.isEmpty()) setting.directory?.let(::folder) else folder(parent)
        return name.takeIf { root?.identity == bound.identity }
    }

    private fun folder(path: String): FolderRef? = runCatching {
        val uri = when {
            path.startsWith("/android_content/") -> duckStationDirectoryUri(path) ?: return null
            path.startsWith("content://") -> path
            else -> {
                val raw = path.removePrefix("file://")
                val absolute = (if (raw.startsWith('/')) Paths.get(raw) else {
                    val data = storageIdentity(dataUri, packageName).removePrefix("primary:")
                    Paths.get("/storage/emulated/0", data, raw)
                }).normalize().toString()
                val identity = storageIdentity(absolute, packageName)
                if (identity != absolute || absolute.startsWith("/storage/") || absolute.startsWith("/data/")) {
                    return FolderRef(identity, Paths.get(absolute).fileName.toString())
                }
                return null
            }
        }
        val id = folderDocumentId(uri).trimEnd('/')
        if (URI(uri).authority.isNullOrBlank() || id.isEmpty() || id.split('/').any { it == ".." || it == "." }) return null
        FolderRef(storageIdentity(uri, packageName), id.substringAfterLast('/').substringAfter(':').ifEmpty { "Storage" })
    }.getOrNull()
}

/** Foldcade can observe this only for ROMs it serves. A file manager's open cannot prove a boot. */
internal class DuckStationRomOpens {
    private data class Open(val caller: HostedCardCaller, val documentId: String, val atMillis: Long)
    private val reads = ArrayDeque<Open>()

    @Synchronized fun opened(caller: HostedCardCaller, documentId: String, atMillis: Long) {
        if (reads.size == 64) reads.removeFirst()
        reads.addLast(Open(caller, documentId, atMillis))
    }

    @Synchronized fun firstOpen(caller: HostedCardCaller, documentId: String, start: Long, end: Long): Long? =
        reads.firstOrNull { it.caller == caller && it.documentId == documentId && it.atMillis in start..end }?.atMillis
}

/** Require sustained foreground time in the gameplay activity after DuckStation opened the ROM. */
internal fun duckStationGameReached(moves: List<FrontMove>, romOpenedAt: Long, start: Long, end: Long): Boolean {
    var showingSince: Long? = null
    for (move in moves) {
        if (move.packageName != DuckStationPlayer.PACKAGE || move.className != DuckStationPlayer.EMULATION_ACTIVITY ||
            move.atMillis !in start..end) continue
        if (move.shown) {
            if (showingSince == null) showingSince = move.atMillis
        } else {
            val since = showingSince
            if (since != null && move.atMillis - maxOf(since, romOpenedAt) >= SAVE_START_CHECK_MIN_MILLIS) return true
            showingSince = null
        }
    }
    val since = showingSince ?: return false
    return end - maxOf(since, romOpenedAt) >= SAVE_START_CHECK_MIN_MILLIS
}

/** Invert the SAF path form that DuckStation stores, including a document below a granted tree. */
private fun duckStationDirectoryUri(path: String): String? {
    val parts = path.removePrefix("/android_content/").trimEnd('/').split('/')
    val authority = parts.first()
    if (!authority.matches(Regex("[A-Za-z0-9_.-]+"))) return null
    val document = parts.indexOfFirst { it == "document" || it.startsWith("document@") }
    val tree = duckStationDocumentId(parts.subList(1, if (document > 0) document else parts.size), "tree") ?: return null
    val uri = treeUri(authority, tree)
    if (document < 0) return uri
    val id = duckStationDocumentId(parts.subList(document, parts.size), "document") ?: return null
    return folderDocumentUri(uri, id)
}

private fun duckStationDocumentId(parts: List<String>, kind: String): String? {
    val key = parts.firstOrNull() ?: return null
    val rest = parts.drop(1)
    if (rest.any { it.isEmpty() || it == "." || it == ".." }) return null
    if (key == kind) return rest.joinToString("/").takeIf(String::isNotEmpty)
    val volume = key.removePrefix("$kind@").takeIf { it != key && it.isNotEmpty() } ?: return null
    return volume + ":" + rest.joinToString("/")
}
