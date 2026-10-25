// SPDX-License-Identifier: MIT

package app.foldcade.api.plugin

/**
 * A source of games and of the saves that belong to them.
 * This is not a [MetadataProvider]. The host stores those choices in separate slots.
 * First-party and community backends share this type. There is no blocking variant.
 *
 * [id] and [displayName] are already loaded and stay ordinary properties.
 * [connect], [disconnect], [listPlatforms], [listGames], [ensureLocal], [removeLocal],
 * [saves], [prepareLaunch], and [reconcile] read or write the backend. Each is a cancellable
 * `suspend` function. An implementation must stop that work when the calling
 * coroutine is cancelled, including a scan or an HTTP call.
 * Implementations rethrow [kotlin.coroutines.cancellation.CancellationException].
 * They must not wrap it in [PluginException].
 * The host calls these off the main thread and does not block on them.
 *
 * The shell calls [ensureLocal], then [prepareLaunch], then [reconcile], in that order.
 * For a launch that opens another library's copy of the same file, it calls
 * [prepareSaves] in place of [ensureLocal] and [prepareLaunch].
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

    /**
     * Deletes the copy [ensureLocal] downloaded for [game], so it is on the server
     * only again and the next launch downloads it. Saves stay. The host offers
     * this only for an [Availability.Cached] game. A backend that keeps no
     * copy leaves the default, which refuses.
     */
    suspend fun removeLocal(game: Game): Unit =
        throw PluginException.Unavailable("No downloaded copy to remove")

    suspend fun saves(game: Game): SaveSet

    /**
     * Remote keys of games whose locally held saves have not reached the server.
     * Includes saves with no sync record. The host reads this at startup and
     * after activity reports. Default: nothing waiting. Added in API minor 15.
     */
    suspend fun unsyncedSaves(): Set<String> = emptySet()

    /** Every server version for this game, newest first. Added in API minor 16. */
    suspend fun serverSaves(game: Game, player: Player? = null): List<ServerSave> = emptyList()

    /** IDs of this library's own backups in its most recent [serverSaves] listing. No I/O. */
    suspend fun serverSaveBackups(game: Game): Set<String> = emptySet()

    /** Optional origins by save ID from the latest [serverSaves] listing. No I/O. */
    suspend fun serverSaveOrigins(game: Game): Map<String, ServerSaveOrigin> = emptyMap()

    /** Sync held bytes now, even with no installed player. Refuse offline or incomplete syncs. */
    suspend fun syncServerSaves(game: Game, player: Player? = null): SyncResult =
        throw PluginException.Unavailable("Server save sync is not supported")

    /**
     * Keep current progress on the server, verify [saveId], and make it the player's slot head.
     * Neither version is deleted. Return snapshots to place through the normal player-save flow.
     * The host captures current player bytes first. [beforeCommit] persists a fresh-load marker
     * after all checks, under the library's save lock, immediately before promotion. A definitive
     * refusal is [PluginException.SaveChoiceRefused]; an uncertain commit must not use that failure.
     * Incomplete syncs refuse the choice. Added in API minor 16.
     */
    suspend fun playServerSave(game: Game, player: Player, saveId: String, beforeCommit: () -> Unit = {}): List<PlacedSave> =
        throw PluginException.Unavailable("Choosing a server save is not supported")

    /**
     * Copy [saveId] into an existing slot, keeping its previous versions. The host captures
     * [player]'s changes first. If the destination belongs to that player, return snapshots
     * for placement and a fresh start, as [playServerSave] does. Otherwise return no snapshots.
     */
    suspend fun copyServerSave(
        game: Game, saveId: String, toSlot: String, toEmulator: String?, player: Player? = null, beforeCommit: () -> Unit = {},
    ): List<PlacedSave> = throw PluginException.Unavailable("Copying a server save is not supported")

    /**
     * Negotiate this game's saves before the shell places them and starts [player].
     * Called after [ensureLocal]. [Placement.savesToPlace] is what the shell writes
     * into the player's locations.
     */
    suspend fun prepareLaunch(game: Game, player: Player): Placement

    /**
     * Negotiate [game]'s saves for a launch that opens another library's copy of the
     * same file. [target] is that copy, and [Placement.target] returns it unchanged.
     * The shell calls [reconcile] afterwards as usual. The default calls [prepareLaunch],
     * which may fetch this library's copy too. A backend that keeps saves apart from
     * its copy overrides this. Added in API minor 10.
     */
    suspend fun prepareSaves(game: Game, player: Player, target: LaunchTarget): Placement =
        prepareLaunch(game, player).copy(target = target)

    /**
     * The name of the file a player opens for [game], such as `Tetris DS (USA).nds`,
     * even before it is here. Players name a game's saves after it, so the host shows
     * games of two libraries with the same platform and file name as one game with
     * one save. Null when the game is more than one file, or the name is not known
     * without the network. The default is null. Added in API minor 10.
     */
    suspend fun fileName(game: Game): String? = null

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

    /**
     * Firmware the player needs is arriving before the game starts, such as a
     * console's BIOS or system update. [totalBytes] is null when the size is not
     * known. Added in API minor 14.
     */
    data class GettingFirmware(val bytesSoFar: Long, val totalBytes: Long?) : GameActivity

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
