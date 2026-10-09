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
 * [remoteKey] is opaque: a document URI, a server id, or whatever that backend uses.
 */
data class Game(
    val backendId: String,
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

/** One page. [nextOffset] is null when there is no further page. */
data class GamePage(
    val games: List<Game>,
    val nextOffset: Int?,
)
