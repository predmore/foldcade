package app.foldcade.api.plugin

/**
 * Where the game bytes are.
 * Remote-only still has to be fetched. Cached is in the app cache.
 * Local-only never had a server copy.
 */
enum class Availability {
    RemoteOnly,
    Cached,
    LocalOnly,
}

/**
 * A game is [backendId] plus [remoteKey], a platform, and [availability].
 * [label] is the name the backend already has, such as a file name.
 * Display metadata belongs to a [MetadataProvider], not to this record.
 */
data class Game(
    val backendId: String,
    /**
     * Opaque key for this game inside the backend.
     * Stable across rescans: a later scan of the same game keeps this key.
     * The shell does not parse it. The backend returns a [LaunchTarget].
     */
    val remoteKey: String,
    val platformId: String,
    val availability: Availability,
    val label: String,
)

/** Page request for [LibraryBackend.listGames]. An empty [text] is not a search. */
data class GameQuery(
    val text: String = "",
    val offset: Int = 0,
    val limit: Int = 50,
)

/**
 * One page. [nextOffset] is null when there is no further page.
 * [total] is null when this backend does not know how many games match.
 */
data class GamePage(
    val games: List<Game>,
    val nextOffset: Int?,
    val total: Int? = null,
)
