package app.foldcade.api.plugin

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

data class Artwork(
    val role: ArtworkRole,
    val uri: String,
)

enum class ArtworkRole {
    Cover,
    Background,
    Icon,
    Logo,
    Screenshot,
}
