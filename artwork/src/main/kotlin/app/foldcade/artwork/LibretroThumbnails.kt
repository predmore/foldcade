package app.foldcade.artwork

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    private val listings = ConcurrentHashMap<String, ThumbnailIndex>()
    private val reading = Mutex()

    override fun handles(query: ArtQuery): Boolean =
        query.platformId in SYSTEMS && (query.fileName != null || query.title.isNotBlank())

    override suspend fun find(query: ArtQuery): ArtSet? = withContext(Dispatchers.IO) {
        val system = SYSTEMS[query.platformId] ?: return@withContext null
        val name = boxarts(system).best(query.fileName, query.title) ?: return@withContext null
        ArtSet(source = id, cover = imageUrl(system, name))
    }

    /**
     * The game's real title screen, or a snapshot when there is none. The
     * server names those as it names the box, so the box's match finds them
     * without another listing. On DS and 3DS the image holds both screens.
     */
    override suspend fun scene(query: ArtQuery): ArtScene? = withContext(Dispatchers.IO) {
        val system = SYSTEMS[query.platformId] ?: return@withContext null
        // The best box match may have no title screen when a near release does, so try a few.
        val names = boxarts(system).ranked(query.fileName, query.title).take(SCENE_TRIES)
        for (kind in listOf("Named_Titles", "Named_Snaps")) {
            for (name in names) {
                val url = imageUrl(system, name, kind)
                if (http.status(url) == 200) return@withContext ArtScene(screen = url)
            }
        }
        null
    }

    private suspend fun boxarts(system: String): ThumbnailIndex {
        listings[system]?.let { return it }
        return reading.withLock {
            listings[system]?.let { return@withLock it }
            val file = File(cacheDir, "${system.replace(LISTING_FILE_UNSAFE, "_")}.txt")
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
            ThumbnailIndex(names).also { listings[system] = it }
        }
    }

    private suspend fun readListing(system: String): List<String> {
        val response = http.get("https://$HOST/${segment(system)}/Named_Boxarts/")
        if (response.status != 200) throw IllegalStateException("libretro listing returned ${response.status}")
        // find/scene keep listing reads, parsing, indexing, and matching together on IO.
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
            "playstation-3" to "Sony - PlayStation 3",
            "psp" to "Sony - PlayStation Portable",
            "genesis" to "Sega - Mega Drive - Genesis",
            "master-system" to "Sega - Master System - Mark III",
            "game-gear" to "Sega - Game Gear",
            "saturn" to "Sega - Saturn",
            "dreamcast" to "Sega - Dreamcast",
            "sega-cd" to "Sega - Mega-CD - Sega CD",
            "sega-32x" to "Sega - 32X",
            "pc-engine" to "NEC - PC Engine - TurboGrafx 16",
            "pc-engine-cd" to "NEC - PC Engine CD - TurboGrafx-CD",
            "atari-2600" to "Atari - 2600",
            "atari-7800" to "Atari - 7800",
            "atari-lynx" to "Atari - Lynx",
            "neo-geo-pocket" to "SNK - Neo Geo Pocket",
            "neo-geo-pocket-color" to "SNK - Neo Geo Pocket Color",
            "wonderswan" to "Bandai - WonderSwan",
            "wonderswan-color" to "Bandai - WonderSwan Color",
            "virtual-boy" to "Nintendo - Virtual Boy",
            "colecovision" to "Coleco - ColecoVision",
        )

        internal fun imageUrl(system: String, name: String, kind: String = "Named_Boxarts"): String =
            "https://$HOST/${segment(system)}/$kind/${segment("$name.png")}"
    }
}

/** Image names in an Apache directory listing, decoded, without `.png`. */
internal fun parseListing(html: String): List<String> =
    LISTING_LINK.findAll(html)
        .map { decodeSegment(it.groupValues[1]).removeSuffix(".png") }
        .distinct()
        .toList()

/**
 * The listing names for a ROM. A filename named as No-Intro names it matches
 * as it is. Otherwise the title must match, and the release whose region the
 * file names wins, then USA, World, Europe, and Japan. A clean release beats
 * a revision, beta, or demo of the same region. Built once per system listing;
 * each lookup normalizes only the query and ranks that title's releases.
 */
internal class ThumbnailIndex(names: List<String>) {
    private val exactNames = names.toHashSet()
    private val byTitle = names.groupBy(::matchKey)

    /** Covers need only the best name; an exact filename avoids ranking other releases. */
    fun best(fileName: String?, title: String): String? {
        fileName?.let(::fileStem)?.let(::libretroSafe)?.takeIf { it in exactNames }?.let { return it }
        return ranked(fileName, title).firstOrNull()
    }

    /** Every matching release, in listing order when region, status, and length tie. */
    fun ranked(fileName: String?, title: String): List<String> {
        val stem = fileName?.let(::fileStem)
        val exact = stem?.let(::libretroSafe)?.takeIf { it in exactNames }
        val key = matchKey(stem ?: title).ifEmpty { matchKey(title) }
        if (key.isEmpty()) return listOfNotNull(exact)
        val candidates = byTitle[key].orEmpty()
            .ifEmpty { if (stem != null) byTitle[matchKey(title)].orEmpty() else emptyList() }
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
}

/** The title alone, for comparing: tags cut, articles dropped, letters and digits only. */
internal fun matchKey(name: String): String {
    val cut = name.indexOfAny(charArrayOf('(', '[')).let { if (it > 0) name.substring(0, it) else name }
    return cut.lowercase()
        .replace(TRAILING_ARTICLE, " ")
        .replace(LEADING_ARTICLE, "")
        .replace(NON_TITLE, "")
}

/** The server writes these characters as `_` in an image name. */
internal fun libretroSafe(name: String): String = name.replace(UNSAFE_NAME, "_")

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
    REGION_TAG.findAll(name).flatMap { group ->
        group.groupValues[1].split(',').map { it.trim() }
    }.mapNotNull { tag ->
        REGION_CODES[tag] ?: tag.takeIf { it in REGIONS }
    }.toList()

private fun segment(text: String): String =
    URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")

private fun decodeSegment(text: String): String =
    URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8)

private val LISTING_FILE_UNSAFE = Regex("[^A-Za-z0-9]+")
private val LISTING_LINK = Regex("""href="([^"?/][^"]*\.png)"""")
private val TRAILING_ARTICLE = Regex(""",\s*(the|a|an)\b""")
private val LEADING_ARTICLE = Regex("""^(the|a|an)\s+""")
private val NON_TITLE = Regex("[^a-z0-9]+")
private val UNSAFE_NAME = Regex("""[&*/:`<>?\\|"]""")
private val REGION_TAG = Regex("""\(([^)]*)\)""")
private val REGIONS = setOf("USA", "World", "Europe", "Japan", "Korea", "Asia", "Australia")
