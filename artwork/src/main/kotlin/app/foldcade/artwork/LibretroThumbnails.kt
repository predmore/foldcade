package app.foldcade.artwork

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Box art from the libretro thumbnail server, for ROMs. No key is needed.
 *
 * The server names each image after the No-Intro name of the game. One
 * directory listing per system is read and kept in [cacheDir] for
 * [LISTING_MAX_AGE_MS], so a library of a hundred games costs one request per
 * system, not one per game. A file named exactly as No-Intro names it matches
 * at once. Any other name matches on its title, and the region in its tags
 * picks between releases.
 */
class LibretroThumbnails(
    private val http: ArtHttp,
    private val cacheDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) : ArtSource {
    override val id: String = "libretro"

    private val listings = ConcurrentHashMap<String, List<String>>()
    private val reading = Mutex()

    override fun handles(query: ArtQuery): Boolean =
        query.platformId in SYSTEMS && (query.fileName != null || query.title.isNotBlank())

    override suspend fun find(query: ArtQuery): ArtSet? {
        val system = SYSTEMS[query.platformId] ?: return null
        val name = bestThumbnail(boxarts(system), query.fileName, query.title) ?: return null
        return ArtSet(source = id, cover = imageUrl(system, name))
    }

    /**
     * The game's real title screen, or a snapshot when there is none. The
     * server names those as it names the box, so the box's match finds them
     * without another listing. On DS and 3DS the image holds both screens.
     */
    override suspend fun scene(query: ArtQuery): ArtScene? {
        val system = SYSTEMS[query.platformId] ?: return null
        // The best box match may have no title screen when a near release does, so try a few.
        val names = rankedThumbnails(boxarts(system), query.fileName, query.title).take(SCENE_TRIES)
        for (kind in listOf("Named_Titles", "Named_Snaps")) {
            for (name in names) {
                val url = imageUrl(system, name, kind)
                if (http.status(url) == 200) return ArtScene(screen = url)
            }
        }
        return null
    }

    private suspend fun boxarts(system: String): List<String> {
        listings[system]?.let { return it }
        return reading.withLock {
            listings[system]?.let { return@withLock it }
            val file = File(cacheDir, "${system.replace(Regex("[^A-Za-z0-9]+"), "_")}.txt")
            val kept = if (file.isFile) file.readLines().filter { it.isNotBlank() } else null
            val names = if (kept != null && clock() - file.lastModified() < LISTING_MAX_AGE_MS) {
                kept
            } else {
                try {
                    readListing(system).also { read ->
                        cacheDir.mkdirs()
                        val temp = File(cacheDir, "${file.name}.tmp")
                        temp.writeText(read.joinToString("\n"))
                        if (!temp.renameTo(file)) temp.delete()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    // An old copy stays in use while the server cannot be read. With none, the lookup fails.
                    kept ?: throw failure
                }
            }
            listings[system] = names
            names
        }
    }

    private suspend fun readListing(system: String): List<String> {
        val response = http.get("https://$HOST/${segment(system)}/Named_Boxarts/")
        if (response.status != 200) throw IllegalStateException("libretro listing returned ${response.status}")
        return parseListing(response.body.decodeToString())
    }

    companion object {
        const val HOST: String = "thumbnails.libretro.com"
        const val LISTING_MAX_AGE_MS: Long = 14L * 24 * 60 * 60 * 1000
        private const val SCENE_TRIES = 3

        /** Canonical platform ids to the server's system folders. */
        val SYSTEMS: Map<String, String> = mapOf(
            "nintendo-3ds" to "Nintendo - Nintendo 3DS",
            "nintendo-ds" to "Nintendo - Nintendo DS",
            "game-boy" to "Nintendo - Game Boy",
            "game-boy-color" to "Nintendo - Game Boy Color",
            "game-boy-advance" to "Nintendo - Game Boy Advance",
            "nes" to "Nintendo - Nintendo Entertainment System",
            "snes" to "Nintendo - Super Nintendo Entertainment System",
            "nintendo-64" to "Nintendo - Nintendo 64",
            "gamecube" to "Nintendo - GameCube",
            "wii" to "Nintendo - Wii",
            "playstation" to "Sony - PlayStation",
            "playstation-2" to "Sony - PlayStation 2",
            "psp" to "Sony - PlayStation Portable",
            "genesis" to "Sega - Mega Drive - Genesis",
            "master-system" to "Sega - Master System - Mark III",
            "game-gear" to "Sega - Game Gear",
            "saturn" to "Sega - Saturn",
            "dreamcast" to "Sega - Dreamcast",
        )

        internal fun imageUrl(system: String, name: String, kind: String = "Named_Boxarts"): String =
            "https://$HOST/${segment(system)}/$kind/${segment("$name.png")}"
    }
}

/** Image names in an Apache directory listing, decoded, without `.png`. */
internal fun parseListing(html: String): List<String> =
    Regex("""href="([^"?/][^"]*\.png)"""")
        .findAll(html)
        .map { decodeSegment(it.groupValues[1]).removeSuffix(".png") }
        .distinct()
        .toList()

/**
 * The listing name for a ROM. [fileName] named as No-Intro names it matches
 * as it is. Otherwise the title must match, and the release whose region the
 * file names wins, then USA, World, Europe, and Japan. A clean release beats
 * a revision, beta, or demo of the same region.
 */
internal fun bestThumbnail(names: List<String>, fileName: String?, title: String): String? =
    rankedThumbnails(names, fileName, title).firstOrNull()

/** Every listing name for the ROM, best first, by the rules of [bestThumbnail]. */
internal fun rankedThumbnails(names: List<String>, fileName: String?, title: String): List<String> {
    val stem = fileName?.let(::fileStem)
    val exact = stem?.let(::libretroSafe)?.takeIf { it in names }
    val key = matchKey(stem ?: title).ifEmpty { matchKey(title) }
    if (key.isEmpty()) return listOfNotNull(exact)
    val candidates = names.filter { matchKey(it) == key }
        .ifEmpty { if (stem != null) names.filter { matchKey(it) == matchKey(title) } else emptyList() }
    val wanted = regionsIn(stem.orEmpty()) + DEFAULT_REGIONS
    val ranked = candidates.sortedWith(
        compareBy<String>(
            { name -> regionsIn(name).minOfOrNull { region -> wanted.indexOf(region).takeIf { it >= 0 } ?: wanted.size } ?: wanted.size },
            { name -> if (UNOFFICIAL.containsMatchIn(name)) 1 else 0 },
            { name -> name.length },
        ),
    )
    return listOfNotNull(exact) + (ranked - setOfNotNull(exact))
}

/** The title alone, for comparing: tags cut, articles dropped, letters and digits only. */
internal fun matchKey(name: String): String {
    val cut = name.indexOfAny(charArrayOf('(', '[')).let { if (it > 0) name.substring(0, it) else name }
    return cut.lowercase()
        .replace(Regex(""",\s*(the|a|an)\b"""), " ")
        .replace(Regex("""^(the|a|an)\s+"""), "")
        .replace(Regex("[^a-z0-9]+"), "")
}

/** The server writes these characters as `_` in an image name. */
internal fun libretroSafe(name: String): String = name.replace(Regex("""[&*/:`<>?\\|"]"""), "_")

private fun fileStem(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    return if (dot > 0) fileName.substring(0, dot) else fileName
}

private val DEFAULT_REGIONS = listOf("USA", "World", "Europe", "Japan")

private val REGION_CODES = mapOf(
    "U" to "USA",
    "E" to "Europe",
    "J" to "Japan",
    "W" to "World",
    "UE" to "USA",
    "JU" to "USA",
)

private val UNOFFICIAL = Regex("""\((Rev [^)]*|Beta[^)]*|Demo[^)]*|Proto[^)]*|Sample|Kiosk[^)]*)\)""", RegexOption.IGNORE_CASE)

/** The regions a name's tags name: "(USA, Europe)" is both, "(U)" is USA. */
private fun regionsIn(name: String): List<String> =
    Regex("""\(([^)]*)\)""").findAll(name).flatMap { group ->
        group.groupValues[1].split(',').map { it.trim() }
    }.mapNotNull { tag ->
        REGION_CODES[tag] ?: tag.takeIf { it in setOf("USA", "World", "Europe", "Japan", "Korea", "Asia", "Australia") }
    }.toList()

private fun segment(text: String): String =
    URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")

private fun decodeSegment(text: String): String =
    URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8)
