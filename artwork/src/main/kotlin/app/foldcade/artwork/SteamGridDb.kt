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

/**
 * Covers and square art from SteamGridDB, with the user's own API key.
 *
 * A Steam game is found by its app id. Anything else, such as a Moonlight
 * stream or a game the other sources missed, is found by its title. The cover
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

    private suspend fun gameId(token: String, query: ArtQuery): Int? {
        query.steamAppId?.let { appId ->
            val found = data(token, "games/steam/$appId")
            (found as? JsonObject)?.get("id")?.jsonPrimitive?.int?.let { return it }
        }
        val title = query.title.trim()
        if (title.isEmpty()) return null
        val results = data(token, "search/autocomplete/${segment(title)}") as? JsonArray ?: return null
        val wanted = normalized(title)
        val games = results.mapNotNull { it as? JsonObject }
        val exact = games.firstOrNull { normalized(it["name"]?.jsonPrimitive?.content.orEmpty()) == wanted }
        return (exact ?: games.firstOrNull())?.get("id")?.jsonPrimitive?.int
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

private fun normalized(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "")

private fun segment(text: String): String =
    URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")
