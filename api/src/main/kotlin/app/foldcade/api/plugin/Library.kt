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

    /**
     * One configured copy of [id]. The host keys the catalog and home tiles by this.
     * Default is [id], so the copy that exists today keeps its key.
     */
    val instanceId: String
        get() = id

    val displayName: String

    /**
     * Stored text for this instance, including a RomM origin.
     * The host calls this before [connect] and before [beginDeviceCode].
     * Not the token, and not the shared roots. The default ignores [values].
     *
     * Editing a stored value calls this again and then [connect].
     */
    fun configure(values: Map<String, String>) {}

    /** Ordered setup steps. Empty means nothing to set up. */
    fun setup(): List<Setup> = emptyList()

    /**
     * Starts device-code sign-in. The plugin creates and keeps the device identifier.
     * A backend that does not offer it leaves the default, which refuses.
     */
    suspend fun beginDeviceCode(): DeviceCodeChallenge =
        throw PluginException.Unavailable("No device-code setup")

    /**
     * Polls [deviceCode] from [beginDeviceCode].
     * The host writes an [DeviceCodePoll.Approved] token. The plugin does not.
     */
    suspend fun pollDeviceCode(deviceCode: String): DeviceCodePoll =
        throw PluginException.Unavailable("No device-code setup")

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

    /**
     * The host's ear for [GameActivity]. Called once, before [connect]. A backend
     * that reports nothing leaves this as it is. Report from any thread and
     * return at once: the host only records what it hears.
     */
    fun observeActivity(listener: GameActivityListener) = Unit

    /**
     * Conflicts waiting for a choice. Empty until a sync kept both copies.
     * A deferred choice is left out until [reopenDeferredSaveConflicts].
     */
    suspend fun pendingSaveConflicts(): List<SaveConflict> = emptyList()

    /**
     * Applies [choice] to the conflict [id].
     * Every choice keeps both copies. [SaveConflictChoice.DecideLater] leaves it
     * for the next launch. An unknown id does nothing.
     */
    suspend fun resolveSaveConflict(id: String, choice: SaveConflictChoice) = Unit

    /** Deferred conflicts become pending again, so the next launch can ask. */
    suspend fun reopenDeferredSaveConflicts() = Unit

    /**
     * Called when the pending set may have changed.
     * The listener must not call back into this backend on that same stack.
     * The returned handle removes the listener.
     */
    fun addSaveConflictListener(listener: () -> Unit): AutoCloseable = AutoCloseable { }
}

/** Hears what a [LibraryBackend] is doing with the game whose [Game.remoteKey] it names. */
fun interface GameActivityListener {
    fun onActivity(remoteKey: String, activity: GameActivity)
}

/**
 * What a backend is doing with one game right now. The shell shows it on the
 * game's tile and on the top screen, and never stops the player for it.
 * Open. A host that branches on this must use an `else` branch.
 */
sealed interface GameActivity {
    /** The game's bytes are arriving. [totalBytes] is null when the size is not known. */
    data class Downloading(val bytesSoFar: Long, val totalBytes: Long?) : GameActivity

    /** Every byte is here, and the backend is checking them. */
    data object Checking : GameActivity

    /** The game's saves are syncing. */
    data object SyncingSaves : GameActivity

    /** The work ended with nothing to tell, such as a download that failed or was cancelled. */
    data object Idle : GameActivity

    /** A save sync ended with [result]. */
    data class Synced(val result: SyncResult) : GameActivity
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
