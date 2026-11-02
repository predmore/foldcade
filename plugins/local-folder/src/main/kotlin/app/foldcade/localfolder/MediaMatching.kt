// SPDX-License-Identifier: MIT

package app.foldcade.localfolder

import app.foldcade.api.plugin.Artwork
import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.GameDetails
import java.text.Normalizer
import java.util.Locale

/** One distinct media name inside one platform. [stemPath] retains ES-DE's relative directories. */
data class MediaEntry(
    val platformId: String,
    val stemPath: String,
    val romPath: String? = null,
    val title: String? = null,
    val artwork: List<Artwork> = emptyList(),
    val details: GameDetails? = null,
)

/** Folder role and its fallback rank. A title screen loses to a gameplay screenshot. */
data class MediaRole(val role: ArtworkRole, val rank: Int = 0)

/** Public ES-DE and common Skraper role names. Video, mixed art and back covers have no role. */
fun mediaRole(folder: String): MediaRole? = when (folder.lowercase(Locale.ROOT)) {
    "covers", "box2dfront", "boxart", "boxfront", "box", "images", "imgs" -> MediaRole(ArtworkRole.Cover)
    "screenshots", "snaps", "snap" -> MediaRole(ArtworkRole.Screenshot)
    "titlescreens", "titleshots" -> MediaRole(ArtworkRole.Screenshot, 1)
    "marquees", "wheel", "wheels", "logos" -> MediaRole(ArtworkRole.Logo)
    "fanart", "backgrounds" -> MediaRole(ArtworkRole.Background)
    else -> null
}

internal val IGNORED_MEDIA_FOLDERS = setOf("3dboxes", "backcovers", "miximages", "physicalmedia", "videos", "manuals")

/**
 * Platform, relative stem, file stem, gamelist path, then unambiguous normalized
 * title. Region tags may disambiguate a title; ties return no match. No fuzzy search.
 */
fun matchMedia(game: FolderGame, entries: List<MediaEntry>): MediaEntry? = MediaMatcher(entries).match(game)

/** Lookup tables are temporary build state, discarded when the per-game index is published. */
internal class MediaMatcher(entries: List<MediaEntry>) {
    private val platforms = entries.groupBy { it.platformId }.mapValues { (_, items) -> PlatformMedia(items) }

    fun match(game: FolderGame): MediaEntry? = platforms[game.platformId]?.match(game)
}

private class PlatformMedia(entries: List<MediaEntry>) {
    private val stems = entries.groupBy { it.stemPath.lowercase(Locale.ROOT) }
    private val names = entries.groupBy { it.stemPath.substringAfterLast('/').lowercase(Locale.ROOT) }
    private val paths = entries.filter { it.romPath != null }.groupBy { mediaPath(it.romPath.orEmpty())?.lowercase(Locale.ROOT) }
    private val fileNames = entries.filter { it.romPath != null }.groupBy { it.romPath?.substringAfterLast('/')?.lowercase(Locale.ROOT) }
    private val titles = entries.flatMap { entry ->
        listOfNotNull(entry.title, entry.stemPath.substringAfterLast('/')).map(::normalizeMediaTitle)
            .filter(String::isNotEmpty).distinct().map { it to entry }
    }.groupBy({ it.first }, { it.second })

    fun match(game: FolderGame): MediaEntry? {
        val path = mediaPath(game.relativePath)?.lowercase(Locale.ROOT) ?: return null
        val stem = path.substringBeforeLast('.', path)
        stems[stem]?.let { return choose(it, game.fileName) }
        names[stem.substringAfterLast('/')]?.let { return choose(it, game.fileName) }
        paths[path]?.let { return choose(it, game.fileName) }
        fileNames[game.fileName.lowercase(Locale.ROOT)]?.let { return choose(it, game.fileName) }
        val matches = (titles[normalizeMediaTitle(game.title)].orEmpty() +
            titles[normalizeMediaTitle(fileStem(game.fileName))].orEmpty()).distinct()
        return choose(matches, game.fileName)
    }
}

private fun choose(entries: List<MediaEntry>, fileName: String): MediaEntry? {
    entries.singleOrNull()?.let { return it }
    val regions = regionsOf(fileName)
    if (regions.isEmpty()) return null
    return entries.filter { (regionsOf(it.stemPath) + regionsOf(it.romPath.orEmpty())).intersect(regions).isNotEmpty() }
        .singleOrNull()
}

private val TITLE_TAGS = Regex("\\([^)]*\\)|\\[[^]]*]")
private val TITLE_PUNCTUATION = Regex("[^\\p{L}\\p{N}]+")

/** Filename tags, punctuation, ampersands and either form of the leading article are equivalent. */
fun normalizeMediaTitle(raw: String): String {
    var title = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    title = title.replace(TITLE_TAGS, " ").trim()
    title = title.replace(Regex(",\\s*the$"), "").removePrefix("the ")
    return title.replace("&", " and ").replace(TITLE_PUNCTUATION, " ").trim().replace(Regex("\\s+"), " ")
}

private fun regionsOf(raw: String): Set<String> = TITLE_TAGS.findAll(raw).flatMap { match ->
    match.value.drop(1).dropLast(1).lowercase(Locale.ROOT).split(',', '/', ' ').asSequence().mapNotNull {
        when (it) {
            "u", "us", "usa" -> "us"
            "e", "eu", "europe" -> "eu"
            "j", "jp", "japan" -> "jp"
            "w", "world" -> "world"
            else -> null
        }
    }
}.toSet()

/** Relative, portable path, bounded at its system root. URLs and absolute paths cannot escape SAF. */
internal fun mediaPath(raw: String): String? {
    val text = raw.trim().replace('\\', '/')
    if (text.startsWith('/') || ':' in text || '\u0000' in text) return null
    val parts = ArrayList<String>()
    for (part in text.split('/')) when (part) {
        "", "." -> Unit
        ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.lastIndex)
        else -> parts += part
    }
    return parts.joinToString("/").ifEmpty { null }
}

/** A dumped absolute ROM path can identify a file; it never grants an image read outside SAF. */
internal fun gamelistRomPath(raw: String, prefix: String = ""): String? {
    val path = raw.trim().replace('\\', '/')
    if (path.startsWith('/') || Regex("^[a-zA-Z]:/").containsMatchIn(path)) {
        return mediaPath(path.substringAfterLast('/'))
    }
    return mediaPath(if (prefix.isEmpty()) path else "$prefix/$path")
}
