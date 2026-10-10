// SPDX-License-Identifier: Apache-2.0

package app.foldcade.api.plugin

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * A source of titles, short text, and artwork.
 * This is not a [LibraryBackend]. A game can come from one and its metadata from another.
 * The host stores the two choices separately. First-party and community providers share this type.
 *
 * [id] and [displayName] are already loaded and stay ordinary properties.
 * [cached] returns only what this provider has already loaded, or null. It stays synchronous.
 * [fetch] is the network or disk read. It is a cancellable `suspend` function.
 * It returns null when this provider has nothing for the game.
 * An implementation must stop that work when the calling coroutine is cancelled.
 * Implementations rethrow [kotlin.coroutines.cancellation.CancellationException].
 * They must not wrap it in [PluginException].
 * The host calls [fetch] off the main thread and does not block on it.
 */
interface MetadataProvider {
    val id: String
    val displayName: String

    fun cached(game: Game): GameMeta?

    suspend fun fetch(game: Game): GameMeta?
}

/** Display record. The provider maps its own source onto this. The host can cache [artwork]. */
data class GameMeta(
    val title: String,
    val shortText: String? = null,
    val artwork: List<Artwork> = emptyList(),
)

/**
 * One image address. [uri] is loaded as that string.
 * A provider must not put a username, password, or token in it.
 * The host does not read credentials out of the URI, and it does not send
 * a token of its own when it loads the image.
 */
data class Artwork(
    val role: ArtworkRole,
    val uri: String,
) {
    init {
        require(credentialFreeArtworkUri(uri) == uri) { "Artwork.uri must not include credentials" }
    }
}

private val artworkCredentialNames = setOf(
    "access_token",
    "access-token",
    "token",
    "id_token",
    "refresh_token",
    "api_key",
    "apikey",
    "api-key",
    "client_secret",
    "password",
    "passwd",
    "secret",
    "auth",
    "authorization",
    "jwt",
    "bearer",
)

/**
 * [raw] with userinfo and credential query or fragment parameters removed.
 * Returns [raw] unchanged when it is already an http, https, file, or content
 * address with none of those. Returns null when [raw] is not such an address.
 * A provider passes the result to [Artwork]. The load uses that string alone.
 */
fun credentialFreeArtworkUri(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val uri = try {
        URI(text)
    } catch (_: URISyntaxException) {
        return null
    }
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https" && scheme != "file" && scheme != "content") return null
    if ((scheme == "http" || scheme == "https") && uri.host.isNullOrEmpty()) return null
    val queryDirty = hasArtworkCredential(uri.rawQuery)
    val fragmentDirty = hasArtworkCredential(uri.rawFragment)
    val userDirty = uri.rawUserInfo != null
    if (!queryDirty && !fragmentDirty && !userDirty) return text
    val rebuilt = try {
        URI(
            scheme,
            null,
            uri.host,
            uri.port,
            uri.path,
            if (queryDirty) stripArtworkCredentials(uri.rawQuery) else uri.rawQuery,
            if (fragmentDirty) stripArtworkCredentials(uri.rawFragment) else uri.rawFragment,
        )
    } catch (_: URISyntaxException) {
        return null
    }
    return rebuilt.toASCIIString()
}

private fun hasArtworkCredential(raw: String?): Boolean {
    if (raw.isNullOrEmpty()) return false
    return raw.split('&').any { part ->
        part.isNotEmpty() && artworkCredentialName(part.substringBefore('='))
    }
}

private fun stripArtworkCredentials(raw: String?): String? {
    if (raw.isNullOrEmpty()) return null
    val kept = raw.split('&').filter { part ->
        part.isNotEmpty() && !artworkCredentialName(part.substringBefore('='))
    }
    return kept.joinToString("&").ifEmpty { null }
}

private fun artworkCredentialName(rawName: String): Boolean {
    val decoded = try {
        URLDecoder.decode(rawName, "UTF-8")
    } catch (_: IllegalArgumentException) {
        rawName
    }
    return decoded.lowercase() in artworkCredentialNames
}

/** Open. A plugin that branches on this must use an `else` branch. */
enum class ArtworkRole {
    Cover,
    Background,
    Icon,
    Logo,
    Screenshot,
}
