package app.foldcade.api.plugin

/**
 * A source of games and of the saves that belong to them.
 * This is not a [MetadataProvider]. The host stores those choices in separate slots.
 * First-party and community backends share this type. There is no blocking variant.
 *
 * [id] and [displayName] are already loaded and stay ordinary properties.
 * [connect], [disconnect], [listPlatforms], [listGames], [ensureLocal], [saves],
 * [prepareLaunch], and [reconcile] read or write the backend. Each is a cancellable
 * `suspend` function. An implementation must stop that work when the calling
 * coroutine is cancelled, including a scan or an HTTP call. The host calls these
 * off the main thread and does not block on them.
 */
interface LibraryBackend {
    val id: String
    val displayName: String

    suspend fun connect()

    suspend fun disconnect()

    suspend fun listPlatforms(): List<ListedPlatform>

    suspend fun listGames(platformId: String, query: GameQuery): GamePage

    /** Bytes the player can open. A no-op when the game is already a local file. */
    suspend fun ensureLocal(game: Game): LocalCopy

    suspend fun saves(game: Game): SaveSet

    /**
     * Negotiate this game's saves before the shell places them and starts [player].
     * [Placement.savesToPlace] is what the shell writes into the player's locations.
     */
    suspend fun prepareLaunch(game: Game, player: Player): Placement

    /** After the player returns, record what it wrote and sync when this backend does. */
    suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult
}

/** A platform this backend can list. [displayName] is a name the backend already has. */
data class ListedPlatform(
    val platformId: String,
    val displayName: String,
)

/**
 * A game the player can open, addressed by a content URI.
 * [firmware] is present only when this backend has bytes that player requires.
 */
data class LocalCopy(
    val contentUri: String,
    val firmware: List<FirmwareBytes> = emptyList(),
)

/** Firmware this backend already has. [contentUri] addresses the bytes. */
data class FirmwareBytes(
    val name: String,
    val contentUri: String,
)

/** Saves this backend owns for one game. The core does not interpret the slot files. */
data class SaveSet(
    val ownerBackendId: String,
    val slots: List<SaveSlot>,
)

data class SaveSlot(
    val slot: String,
    val contentHash: String,
    val lastPlayerId: String?,
)

/**
 * Result of [LibraryBackend.prepareLaunch].
 * The shell writes [savesToPlace] into the matching [SaveDeclaration] locations.
 */
data class Placement(
    val local: LocalCopy,
    val savesToPlace: List<PlacedSave> = emptyList(),
    val sync: SyncResult = SyncResult(SyncOutcome.Unchanged),
)

data class PlacedSave(
    val slot: String,
    val contentUri: String,
    val contentHash: String,
)

/** Bytes the shell observed in the player's save locations after it returned. */
data class ObservedSaves(
    val slots: List<ObservedSlot>,
)

data class ObservedSlot(
    val slot: String,
    val contentHash: String,
    val playerId: String,
    val contentUri: String?,
)

data class SyncResult(
    val outcome: SyncOutcome,
    val note: String? = null,
)

enum class SyncOutcome {
    Unchanged,
    Downloaded,
    Uploaded,
    KeptBoth,
    Queued,
}
