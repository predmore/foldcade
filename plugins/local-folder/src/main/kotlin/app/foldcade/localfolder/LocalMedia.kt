// SPDX-License-Identifier: MIT

package app.foldcade.localfolder

import app.foldcade.api.plugin.Artwork
import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameDetails
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.PluginException
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** The kind of deferred media read discovered by the ROM scan. */
enum class MediaSourceKind { DownloadedMedia, Gamelists, Media, Role, Gamelist }

/** [entry] needs no image bytes during a scan. [relativeDirectory] is below [systemRoot]. */
data class FolderMediaSource(
    val entry: FolderEntry,
    val kind: MediaSourceKind,
    val platformId: String? = null,
    val systemRoot: FolderEntry? = null,
    val relativeDirectory: String = "",
)

/** A real ROM system directory, used to resolve legacy gamelist image paths. */
data class FolderMediaSystem(val platformId: String, val entry: FolderEntry)

/** A compact, immutable result. Only matched games and short facts survive a build. No image bytes. */
class LocalMediaIndex internal constructor(
    internal val games: Map<String, MediaEntry>,
    /** Sources that contained art or info, including sources for games not in the library yet. */
    val sourceUris: Set<String>,
) {
    /** Image documents actually enumerated under the picked trees, for the app's SAF allow rule. */
    val artworkUris: Set<String> = games.values.flatMap { it.artwork }.mapTo(HashSet()) { it.uri }

    companion object {
        val Empty = LocalMediaIndex(emptyMap(), emptySet())
    }
}

/**
 * Metadata for local-folder games. The app builds and [replace]s an index after
 * a ROM scan. Fetches never walk SAF or send data anywhere, even with Game art off.
 */
class LocalMediaProvider : MetadataProvider {
    override val id = ID
    override val displayName = "Local media"
    @Volatile private var index = LocalMediaIndex.Empty

    /** Swaps the whole snapshot. The owner then forgets held artwork and details and notifies Compose. */
    fun replace(next: LocalMediaIndex): Boolean {
        val changed = index.games != next.games || index.sourceUris != next.sourceUris
        index = next
        return changed
    }

    fun index(): LocalMediaIndex = index

    override fun cached(game: Game): GameMeta? = entry(game)?.let {
        GameMeta(it.title?.takeIf(String::isNotBlank) ?: game.label, artwork = it.artwork)
    }

    override suspend fun fetch(game: Game): GameMeta? = cached(game)
    override fun cachedDetails(game: Game): GameDetails? = entry(game)?.details
    override suspend fun details(game: Game): GameDetails? = cachedDetails(game)

    private fun entry(game: Game): MediaEntry? = if (game.backendId == LocalFolderBackend.ID) {
        index.games[game.remoteKey]?.takeIf { it.platformId == game.platformId }
    } else null

    companion object { const val ID = "local.media" }
}

/**
 * Builds off the main thread after [scan] has already listed the ROMs.
 * Each directory is queried at most once per build, even for gamelist references.
 * [readText] must refuse a body larger than its byte limit. Only enumerated
 * documents are used: a gamelist cannot invent a content URI or leave a system root.
 */
fun buildLocalMediaIndex(
    scan: FolderScan,
    childrenOf: (FolderEntry) -> List<FolderEntry>,
    readText: (FolderEntry, Int) -> String?,
    isCancelled: () -> Boolean = { false },
): LocalMediaIndex = MediaIndexBuilder(scan, childrenOf, readText, isCancelled).build()

private class MediaIndexBuilder(
    private val scan: FolderScan,
    private val childrenOf: (FolderEntry) -> List<FolderEntry>,
    private val readText: (FolderEntry, Int) -> String?,
    private val isCancelled: () -> Boolean,
) {
    private val directories = HashMap<String, List<FolderEntry>>()
    private val candidates = LinkedHashMap<Pair<String, String>, Candidate>()
    private val sources = HashSet<String>()
    private val walkedRoles = HashSet<String>()
    private val parsedLists = HashSet<String>()
    private var contributions = 0

    fun build(): LocalMediaIndex {
        for (source in scan.mediaSources) {
            checkActive()
            when (source.kind) {
                MediaSourceKind.DownloadedMedia, MediaSourceKind.Gamelists -> {
                    for (system in children(source.entry).filter { it.isDirectory() }) {
                        val platform = platformFromFolderName(system.displayName)?.id ?: continue
                        val before = contributions
                        if (source.kind == MediaSourceKind.DownloadedMedia) roles(system, platform, "")
                        else for (file in children(system).filter(::isGamelist)) {
                            gamelist(file, platform, "", null)
                        }
                        if (contributions > before) sources += source.entry.documentUri
                    }
                }
                else -> {
                    val platform = source.platformId ?: scan.mediaSystems.filter {
                        it.entry.documentUri == source.systemRoot?.documentUri
                    }.map { it.platformId }.distinct().singleOrNull() ?: continue
                    val before = contributions
                    when (source.kind) {
                        MediaSourceKind.Role -> mediaRole(source.entry.displayName)?.let {
                            roleFiles(source.entry, platform, source.relativeDirectory, it)
                        }
                        MediaSourceKind.Media -> roles(source.entry, platform, source.relativeDirectory)
                        MediaSourceKind.Gamelist -> gamelist(source.entry, platform, source.relativeDirectory, source.systemRoot)
                        else -> Unit
                    }
                    if (contributions > before) sources += source.entry.documentUri
                }
            }
        }
        val matcher = MediaMatcher(candidates.values.map { it.freeze() })
        val found = LinkedHashMap<String, MediaEntry>()
        for (game in scan.games) {
            checkActive()
            matcher.match(game)?.let { match ->
                if (match.artwork.isNotEmpty() || match.details != null) found[game.documentUri] = match
            }
        }
        return LocalMediaIndex(found, sources)
    }

    private fun roles(root: FolderEntry, platform: String, prefix: String) {
        for (dir in children(root)) {
            if (!dir.isDirectory()) continue
            val role = mediaRole(dir.displayName) ?: continue
            roleFiles(dir, platform, prefix, role)
        }
    }

    private fun roleFiles(dir: FolderEntry, platform: String, prefix: String, role: MediaRole) {
        if (!walkedRoles.add(dir.documentUri)) return
        for (file in children(dir)) {
            checkActive()
            val path = joined(prefix, file.displayName)
            if (file.isDirectory()) roleFiles(file, platform, path, role)
            else if (isImage(file)) {
                contributions++
                candidate(platform, path.substringBeforeLast('.')).add(role.role, file.documentUri, role.rank + 10)
            }
        }
    }

    private fun gamelist(file: FolderEntry, platform: String, prefix: String, system: FolderEntry?) {
        if (!parsedLists.add(file.documentUri)) return
        val text = readable { readText(file, MAX_GAMELIST_BYTES) } ?: return
        for (game in parseGamelist(text, isCancelled)) {
            checkActive()
            val path = gamelistRomPath(game.path, prefix) ?: continue
            val entry = candidate(platform, path.substringBeforeLast('.', path))
            entry.romPath = path
            entry.title = game.name
            // A ROM-side gamelist is more specific than a standalone ES-DE gamelist.
            if (entry.details == null || system != null) entry.details = game.details ?: entry.details
            if (game.details != null) contributions++
            val roots = system?.let { listOf(it) } ?: scan.mediaSystems.filter { it.platformId == platform }.map { it.entry }
            for (image in game.images) {
                val imagePath = mediaPath(joined(prefix, image.path)) ?: continue
                val documents = roots.mapNotNull { resolve(it, imagePath) }.distinctBy { it.documentUri }
                val document = documents.singleOrNull()?.takeIf(::isImage) ?: continue
                contributions++
                entry.add(image.role, document.documentUri, image.rank)
            }
        }
    }

    private fun resolve(root: FolderEntry, path: String): FolderEntry? {
        var current = root
        for (segment in path.split('/')) {
            if (!current.isDirectory()) return null
            val matches = children(current).filter { it.displayName.equals(segment, ignoreCase = true) }
            current = matches.firstOrNull { it.displayName == segment } ?: matches.singleOrNull() ?: return null
        }
        return current.takeUnless { it.isDirectory() }
    }

    private fun candidate(platform: String, path: String): Candidate {
        return candidates.getOrPut(platform to path.lowercase(Locale.ROOT)) { Candidate(platform, path) }
    }

    private fun children(entry: FolderEntry): List<FolderEntry> {
        checkActive()
        return directories.getOrPut(entry.documentUri) { readable { childrenOf(entry) }.orEmpty() }
    }

    /** An unavailable document affects only that source, never the rest of the index. */
    private inline fun <T> readable(read: () -> T): T? = try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: PluginException.Unavailable) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun checkActive() {
        if (isCancelled()) throw CancellationException("Media index was cancelled")
    }
}

private class Candidate(private val platform: String, private val path: String) {
    var romPath: String? = null
    var title: String? = null
    var details: GameDetails? = null
    private val art = LinkedHashMap<ArtworkRole, Pair<Int, Artwork>>()

    fun add(role: ArtworkRole, uri: String, rank: Int) {
        val current = art[role]
        if (current == null || rank < current.first || rank == current.first && uri < current.second.uri) {
            art[role] = rank to Artwork(role, uri)
        }
    }

    fun freeze() = MediaEntry(platform, path, romPath, title, art.values.map { it.second }, details)
}

private fun joined(prefix: String, path: String): String = if (prefix.isEmpty()) path else "$prefix/$path"
private fun isGamelist(entry: FolderEntry): Boolean = !entry.isDirectory() && entry.displayName.equals("gamelist.xml", ignoreCase = true)
private fun isImage(entry: FolderEntry): Boolean = !entry.isDirectory() && fileExtension(entry.displayName) in IMAGE_EXTENSIONS
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
