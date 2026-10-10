// SPDX-License-Identifier: Apache-2.0

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
 * coroutine is cancelled, including a scan or an HTTP call.
 * Implementations rethrow [kotlin.coroutines.cancellation.CancellationException].
 * They must not wrap it in [PluginException].
 * The host calls these off the main thread and does not block on them.
 *
 * The shell calls [ensureLocal], then [prepareLaunch], then [reconcile], in that order.
 */
interface LibraryBackend {
    val id: String
    val displayName: String

    suspend fun connect()

    suspend fun disconnect()

    suspend fun listPlatforms(): List<ListedPlatform>

    suspend fun listGames(platformId: String, query: GameQuery): GamePage

    /**
     * The [LaunchTarget] for [game]. A file backend returns [LaunchTarget.ContentUri]
     * for a file it already has. A catalog backend returns [LaunchTarget.AppRef]
     * and does not download. The shell does not parse [Game.remoteKey].
     */
    suspend fun ensureLocal(game: Game): LaunchTarget

    suspend fun saves(game: Game): SaveSet

    /**
     * Negotiate this game's saves before the shell places them and starts [player].
     * Called after [ensureLocal]. [Placement.savesToPlace] is what the shell writes
     * into the player's locations.
     */
    suspend fun prepareLaunch(game: Game, player: Player): Placement

    /**
     * After the player returns, record what it wrote and sync when this backend does.
     * Called after [prepareLaunch].
     */
    suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult
}

/**
 * A platform this backend can list. [displayName] is a name the backend already has.
 * [gameCount] is the number of games when the listing already knows it, so the host
 * does not ask [LibraryBackend.listGames] for each platform. Null means unknown.
 */
data class ListedPlatform @JvmOverloads constructor(
    val platformId: String,
    val displayName: String,
    val gameCount: Int? = null,
)

/** Firmware this backend already has. [contentUri] addresses the bytes. */
data class FirmwareBytes(
    val name: String,
    val contentUri: String,
)

/**
 * Saves this backend owns for one game.
 * [ownerBackendId] names the owner of the bytes.
 * [SaveSlot.slot] is the name of each save, and [SaveDeclaration.slot] is that same name.
 * A null location is [SaveDeclaration.locationUri] or [ObservedSlot.contentUri].
 * The core does not interpret the slot files.
 */
data class SaveSet(
    val ownerBackendId: String,
    val slots: List<SaveSlot>,
)

/**
 * One save the backend in [SaveSet] owns.
 * [slot] is its name. [PlacedSave], [ObservedSlot], and [SaveDeclaration] use that name for the same save.
 * The backend owns the bytes. [lastPlayerId] is the player that last wrote them, or null when that player is unknown.
 * A null location is [SaveDeclaration.locationUri] or [ObservedSlot.contentUri].
 */
data class SaveSlot(
    val slot: String,
    /** Lowercase hex MD5 of the save bytes. */
    val contentHash: String,
    val lastPlayerId: String?,
)

/**
 * Result of [LibraryBackend.prepareLaunch].
 * [target] is the same kind of handoff [LibraryBackend.ensureLocal] returned.
 * The shell writes [savesToPlace] into the matching [SaveDeclaration] locations.
 * [PlacedSave.slot] names each save, and the backend owns those bytes.
 * A null [SaveDeclaration.locationUri] has no place for the shell to write.
 */
data class Placement(
    val target: LaunchTarget,
    val savesToPlace: List<PlacedSave> = emptyList(),
    val sync: SyncResult = SyncResult(SyncOutcome.Unchanged),
)

/**
 * Save bytes the backend owns and the shell writes before launch.
 * [slot] matches [SaveDeclaration.slot].
 * [contentUri] addresses the bytes. A save with no bytes is left out of [Placement.savesToPlace].
 * Null [SaveDeclaration.locationUri] means the shell has these bytes and no place to write them.
 */
data class PlacedSave(
    val slot: String,
    val contentUri: String,
    /** Lowercase hex MD5 of the save bytes. */
    val contentHash: String,
)

/**
 * Bytes the shell observed in the player's save locations after it returned.
 * [ObservedSlot.slot] is the name of each save. The player wrote the bytes.
 * Null [ObservedSlot.contentUri] means that slot was observed with no location.
 */
data class ObservedSaves(
    val slots: List<ObservedSlot>,
)

/**
 * One slot the shell read after the player returned.
 * [slot] is the name from [SaveDeclaration]. The player wrote the bytes.
 * [contentUri] addresses those bytes when the shell has a location.
 * Null [contentUri] means [slot], [contentHash], and [playerId] were observed and no location was included.
 */
data class ObservedSlot(
    val slot: String,
    /** Lowercase hex MD5 of the save bytes. */
    val contentHash: String,
    val playerId: String,
    val contentUri: String?,
)

data class SyncResult(
    val outcome: SyncOutcome,
    val note: String? = null,
)

/** Open. A plugin that branches on this must use an `else` branch. */
enum class SyncOutcome {
    Unchanged,
    Downloaded,
    Uploaded,
    KeptBoth,
    Queued,
}
