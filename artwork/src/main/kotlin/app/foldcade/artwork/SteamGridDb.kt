package app.foldcade.artwork

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Covers and square art from SteamGridDB, with the user's own API key.
 *
 * A Steam game is found by its app id. Anything else, such as a Moonlight
 * stream or a game the other sources missed, is found by its title, and only a
 * game of that same title counts, so a tile never wears another game's art. The cover
 * is a 600x900 grid, and a 512 or 1024 square grid is the tile art. Static,
 * safe-for-work images only.
 *
 * The key goes in an `Authorization` header to the API host only. Image
 * addresses are on the CDN and carry no key. A refused key throws
 * [SteamGridDbKeyRefused], so no miss is remembered for it.
 */
class SteamGridDb(
    private val http: ArtHttp,
    private val key: () -> String?,
) : ArtSource {
    override val id: String = "steamgriddb"

    override fun handles(query: ArtQuery): Boolean =
        key() != null && (query.steamAppId != null || query.title.isNotBlank())

    override suspend fun find(query: ArtQuery): ArtSet? {
        val token = key() ?: return null
        val gameId = gameId(token, query) ?: return null
        val cover = firstImage(token, "grids/game/$gameId?dimensions=600x900&$SAFE") ?: return null
        val square = firstImage(token, "grids/game/$gameId?dimensions=512x512,1024x1024&$SAFE")
        return ArtSet(source = id, cover = cover, square = square)
    }

    /** SteamGridDB's first hero and logo for the game. Static, safe-for-work images only. */
    override suspend fun scene(query: ArtQuery): ArtScene? {
        val token = key() ?: return null
        val gameId = gameId(token, query) ?: return null
        val background = firstImage(token, "heroes/game/$gameId?$SAFE")
        val logo = firstImage(token, "logos/game/$gameId?$SAFE")
        return ArtScene(background = background, logo = logo).takeUnless { it.isEmpty }
    }

    private val matches = ConcurrentHashMap<ArtQuery, Int>()

    private val pickerMatches = ConcurrentHashMap<ArtQuery, Int>()

    suspend fun candidates(query: ArtQuery, kind: String): List<GridCandidate> {
        require(kind in listOf("grids", "heroes", "logos"))
        val token = key() ?: return emptyList()
        val game = matches[query] ?: pickerMatches[query]
            ?: gameId(token, query, fallback = true)?.also { pickerMatches[query] = it }
            ?: return emptyList()
        val dimensions = if (kind == "grids") "dimensions=512x512,1024x1024&" else ""
        val images = data(token, "$kind/game/$game?$dimensions$SAFE") as? JsonArray ?: return emptyList()
        return images.mapNotNull { element ->
            val image = element as? JsonObject ?: return@mapNotNull null
            val url = image["url"]?.jsonPrimitive?.content?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val thumb = image["thumb"]?.jsonPrimitive?.content?.takeIf { it.startsWith("https://") } ?: url
            GridCandidate(image["id"]?.jsonPrimitive?.content ?: url, url, thumb)
        }.distinctBy { it.url }
    }

    private suspend fun gameId(token: String, query: ArtQuery, fallback: Boolean = false): Int? {
        matches[query]?.let { return it }
        query.steamAppId?.let { appId ->
            val found = data(token, "games/steam/$appId")
            (found as? JsonObject)?.get("id")?.jsonPrimitive?.int?.let { matches[query] = it; return it }
        }
        val title = query.title.trim()
        if (title.isEmpty()) return null
        val results = data(token, "search/autocomplete/${segment(title)}") as? JsonArray ?: return null
        // Autocomplete answers every search with something, so only a game of the same name is taken.
        val games = results.mapNotNull { it as? JsonObject }
        val match = games.firstOrNull { artworkTitleMatches(it["name"]?.jsonPrimitive?.content.orEmpty(), title) }
            ?: games.firstOrNull().takeIf { fallback }
        return match?.get("id")?.jsonPrimitive?.int?.also {
            if (!fallback || artworkTitleMatches(match["name"]?.jsonPrimitive?.content.orEmpty(), title)) matches[query] = it
        }
    }

    private suspend fun firstImage(token: String, path: String): String? {
        val images = data(token, path) as? JsonArray ?: return null
        return images.firstNotNullOfOrNull { image ->
            (image as? JsonObject)?.get("url")?.jsonPrimitive?.content?.takeIf { it.startsWith("https://") }
        }
    }

    /** The `data` of one API answer. A 404 is no data. A refused key throws. */
    private suspend fun data(token: String, path: String): JsonElement? {
        val response = http.get("$API/$path", mapOf("Authorization" to "Bearer $token"))
        when (response.status) {
            200 -> Unit
            401, 403 -> throw SteamGridDbKeyRefused()
            404 -> return null
            else -> throw IOException("SteamGridDB returned ${response.status}")
        }
        val body = Json.parseToJsonElement(response.body.decodeToString()).jsonObject
        return body["data"]
    }

    companion object {
        const val API_HOST: String = "www.steamgriddb.com"

        /** The API host and the image CDN, `cdn2.steamgriddb.com`. */
        const val HOSTS: String = ".steamgriddb.com"
        const val CREDENTIAL_ID: String = "steamgriddb"
        const val CREDENTIAL_KEY: String = "api-key"
        private const val API = "https://$API_HOST/api/v2"
        private const val SAFE = "types=static&nsfw=false&humor=false"

        /**
         * Whether SteamGridDB accepts [key]: true or false, or null when it
         * cannot be reached. One search, which any valid key may make.
         */
        suspend fun accepts(http: ArtHttp, key: String): Boolean? = try {
            when (http.get("$API/search/autocomplete/tetris", mapOf("Authorization" to "Bearer $key")).status) {
                200 -> true
                401, 403 -> false
                else -> null
            }
        } catch (_: IOException) {
            null
        }
    }
}

class SteamGridDbKeyRefused : IOException("SteamGridDB refused the API key")

/** Exact title matching across punctuation, accents, and a leading "The". */
fun artworkTitleMatches(candidate: String, title: String): Boolean {
    val wanted = normalized(title)
    return wanted.isNotEmpty() && normalized(candidate) == wanted
}

/**
 * What two spellings of one title share. Case, accents, a leading "The",
 * "&" for "and", and punctuation do not count. Letters of every script do.
 */
private fun normalized(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace("&", " and ")
        .replace(Regex("^\\W*the\\s+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")

private fun segment(text: String): String =
    URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")

data class GridCandidate(val id: String, val url: String, val thumbnail: String)
