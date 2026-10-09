package app.foldcade.host

import app.foldcade.api.plugin.BoundCredentialAccess
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.api.plugin.RommCredentials
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.SyncResult
import java.util.ServiceLoader
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val ROMM_ENTRY_CLASS = "app.foldcade.plugins.romm.RommEntry"

/**
 * Loads plugins and calls library I/O and metadata fetch off [io].
 * Those calls are suspending. This type does not block the caller.
 * [cachedMetadata] stays synchronous because [MetadataProvider.cached] is already in memory.
 * [load] reads the class loader on [io], off the main thread.
 * Register from one thread before calling the suspending methods.
 * [load] records a bad plugin in [rejected]. It does not throw, except [VirtualMachineError].
 *
 * [credentials] defaults to an in-memory store for JVM tests.
 * The Android host must pass the encrypted store instead. The memory default
 * does not encrypt and must not be the store the app process uses.
 */
class PluginHost(
    private val io: CoroutineDispatcher,
    private val credentials: CredentialStore = MemoryCredentialStore(),
) {
    private val platforms = linkedMapOf<String, Platform>()
    private val players = linkedMapOf<String, Player>()
    private val libraries = linkedMapOf<String, LibraryBackend>()
    private val metadataProviders = linkedMapOf<String, MetadataProvider>()
    private val platformNames = mutableListOf<Pair<String, String>>()
    private val playerPlatformIds = linkedMapOf<String, String>()
    private val rejectedPlugins = mutableListOf<RejectedPlugin>()

    /** Plugins [load] skipped, in the order they failed. For the UI. */
    val rejected: List<RejectedPlugin> get() = rejectedPlugins.toList()

    /**
     * Validates [entry], then stores every slot it contributes.
     * A failure stores nothing from this entry.
     */
    fun register(entry: PluginEntry) {
        if (entry.apiVersion != PLUGIN_API_VERSION) {
            error("Plugin API major ${entry.apiVersion} is incompatible with $PLUGIN_API_VERSION")
        }
        if (entry.apiMinor > PLUGIN_API_MINOR) {
            error("Plugin API minor ${entry.apiMinor} is newer than $PLUGIN_API_MINOR")
        }
        val stagedPlatforms = entry.platforms.toList()
        val stagedPlayers = entry.players.toList()
        val stagedLibraries = entry.libraries.toList()
        val stagedMetadata = entry.metadataProviders.toList()
        val stagedPlayerPlatforms = linkedMapOf<String, String>()
        for (player in stagedPlayers) {
            stagedPlayerPlatforms[player.id] = player.platformId
        }
        val names = planPlatformNames(stagedPlatforms, platformNames)
        planIds("player", stagedPlayers.map { it.id }, players.keys)
        planIds("library", stagedLibraries.map { it.id }, libraries.keys)
        planIds("metadata", stagedMetadata.map { it.id }, metadataProviders.keys)
        val ids = buildSet {
            addAll(stagedPlatforms.map { it.id })
            addAll(stagedPlayers.map { it.id })
            addAll(stagedLibraries.map { it.id })
            addAll(stagedMetadata.map { it.id })
        }
        refuseReserved(entry, ids)
        entry.bind(BoundCredentialAccess(ids, credentials))
        platformNames.addAll(names)
        playerPlatformIds.putAll(stagedPlayerPlatforms)
        stagedPlatforms.forEach { platforms[it.id] = it }
        stagedPlayers.forEach { players[it.id] = it }
        stagedLibraries.forEach { libraries[it.id] = it }
        stagedMetadata.forEach { metadataProviders[it.id] = it }
    }

    /**
     * `romm` and `romm.metadata` belong to the built-in RomM entry.
     * Another entry cannot take them, even if it is registered first.
     */
    private fun refuseReserved(entry: PluginEntry, ids: Set<String>) {
        if (entry.javaClass.name == ROMM_ENTRY_CLASS) return
        val taken = ids.filter { it in RommCredentials.RESERVED_IDS }
        if (taken.isNotEmpty()) error("Reserved plugin id: ${taken.joinToString()}")
    }

    /**
     * Loads every [PluginEntry] advertised by [classLoader].
     * The caller supplies the loader. This is the path for a bundled jar and
     * for an out-of-tree jar. It does not discover or open an installed package.
     * Runs on [io], off the main thread.
     * One bad provider is recorded in [rejected] and does not stop the providers
     * that follow. This method does not throw, except [VirtualMachineError].
     * Cancellation still propagates.
     */
    suspend fun load(classLoader: ClassLoader) {
        withContext(io) {
            loadHere(classLoader)
        }
    }

    private fun loadHere(classLoader: ClassLoader) {
        val providers = try {
            ServiceLoader.load(PluginEntry::class.java, classLoader).iterator()
        } catch (failure: Throwable) {
            rethrowVirtualMachineError(failure)
            reject(plugin = null, failure)
            return
        }
        var lastLoaderFailure: String? = null
        var loaderFailureRun = 0
        while (true) {
            val entry = try {
                if (!providers.hasNext()) break
                providers.next()
            } catch (failure: Throwable) {
                rethrowVirtualMachineError(failure)
                val reason = failure.message ?: failure.javaClass.name
                reject(plugin = null, failure)
                if (reason == lastLoaderFailure) {
                    loaderFailureRun++
                    if (loaderFailureRun >= 2) break
                } else {
                    lastLoaderFailure = reason
                    loaderFailureRun = 0
                }
                continue
            }
            lastLoaderFailure = null
            loaderFailureRun = 0
            try {
                register(entry)
            } catch (failure: Throwable) {
                rethrowVirtualMachineError(failure)
                reject(plugin = entry.javaClass.name, failure)
            }
        }
    }

    /**
     * Resolves [id] as a canonical platform id or an alias, ignoring case.
     * The match uses names stored at registration. It does not call platform getters again.
     */
    fun platform(id: String): Platform? {
        val canonical = storedPlatformId(id) ?: return null
        return platforms[canonical]
    }

    fun player(id: String): Player? = players[id]

    /**
     * Players stored for [platformId] or one of its aliases.
     * The platform match uses names stored at registration.
     */
    fun playersFor(platformId: String): List<Player> {
        val canonical = storedPlatformId(platformId) ?: return emptyList()
        return players.mapNotNull { (id, player) ->
            if (playerPlatformIds[id] == canonical) player else null
        }
    }

    /** Library ids stored at registration. This does not call a plugin. */
    fun libraryIds(): List<String> = libraries.keys.toList()

    /**
     * The library's display name, through the same guard as a suspending call.
     * A plugin failure is [PluginCallException] or [PluginException].
     */
    fun libraryLabel(id: String): String {
        val library = requireLibrary(id)
        return callPlugin(id) { library.displayName }
    }

    fun library(id: String): LibraryBackend? = libraries[id]

    fun metadata(id: String): MetadataProvider? = metadataProviders[id]

    /** Already loaded. Not a fetch, and not moved onto [io]. */
    fun cachedMetadata(providerId: String, game: Game): GameMeta? {
        val provider = requireMetadata(providerId)
        return callPlugin(providerId) { provider.cached(game) }
    }

    /** Builds the intent. A plugin failure is [PluginCallException] or [PluginException]. */
    fun launchIntent(playerId: String, request: LaunchRequest): PlayerIntent {
        val player = requirePlayer(playerId)
        return callPlugin(playerId) { player.launchIntent(request) }
    }

    suspend fun connect(libraryId: String) {
        val library = requireLibrary(libraryId)
        offMain(libraryId) { library.connect() }
    }

    suspend fun disconnect(libraryId: String) {
        val library = requireLibrary(libraryId)
        offMain(libraryId) { library.disconnect() }
    }

    suspend fun listPlatforms(libraryId: String): List<ListedPlatform> {
        val library = requireLibrary(libraryId)
        return offMain(libraryId) { library.listPlatforms() }
    }

    suspend fun listGames(libraryId: String, platformId: String, query: GameQuery): GamePage {
        val library = requireLibrary(libraryId)
        return offMain(libraryId) { library.listGames(platformId, query) }
    }

    suspend fun ensureLocal(libraryId: String, game: Game): LaunchTarget {
        val library = requireLibrary(libraryId)
        return offMain(libraryId) { library.ensureLocal(game) }
    }

    suspend fun saves(libraryId: String, game: Game): SaveSet {
        val library = requireLibrary(libraryId)
        return offMain(libraryId) { library.saves(game) }
    }

    suspend fun prepareLaunch(libraryId: String, game: Game, player: Player): Placement {
        val library = requireLibrary(libraryId)
        return offMain(libraryId) { library.prepareLaunch(game, player) }
    }

    suspend fun reconcile(
        libraryId: String,
        game: Game,
        player: Player,
        observed: ObservedSaves,
    ): SyncResult {
        val library = requireLibrary(libraryId)
        return offMain(libraryId) { library.reconcile(game, player, observed) }
    }

    suspend fun fetchMetadata(providerId: String, game: Game): GameMeta? {
        val provider = requireMetadata(providerId)
        return offMain(providerId) { provider.fetch(game) }
    }

    private suspend fun <T> offMain(pluginId: String, block: suspend () -> T): T =
        callPlugin(pluginId) { withContext(io) { block() } }

    private fun requireLibrary(id: String): LibraryBackend =
        libraries[id] ?: error("No library registered with id $id")

    private fun requireMetadata(id: String): MetadataProvider =
        metadataProviders[id] ?: error("No metadata provider registered with id $id")

    private fun requirePlayer(id: String): Player =
        players[id] ?: error("No player registered with id $id")

    private fun reject(plugin: String?, failure: Throwable) {
        rejectedPlugins += RejectedPlugin(
            plugin = plugin ?: "unknown",
            reason = failure.message ?: failure.javaClass.name,
        )
    }

    private fun storedPlatformId(idOrAlias: String): String? =
        platformNames.firstOrNull { it.first.equals(idOrAlias, ignoreCase = true) }?.second

}

/**
 * A plugin [PluginHost.load] did not store.
 * [plugin] is the entry class name when the host has one.
 */
data class RejectedPlugin(
    val plugin: String,
    val reason: String,
)

/**
 * A plugin call failed with something other than [PluginException] or cancellation.
 * [pluginId] is the library, metadata provider, or player the host called.
 */
class PluginCallException(
    val pluginId: String,
    cause: Throwable,
) : Exception("Plugin $pluginId failed", cause)

private inline fun <T> callPlugin(pluginId: String, block: () -> T): T =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (plugin: PluginException) {
        throw plugin
    } catch (failure: Throwable) {
        rethrowVirtualMachineError(failure)
        throw PluginCallException(pluginId, failure)
    }

private fun rethrowVirtualMachineError(failure: Throwable) {
    var current: Throwable? = failure
    val seen = HashSet<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is VirtualMachineError) throw current
        current = current.cause
    }
}

private fun planPlatformNames(
    incoming: List<Platform>,
    already: List<Pair<String, String>>,
): List<Pair<String, String>> {
    val pending = mutableListOf<Pair<String, String>>()
    for (platform in incoming) {
        val names = ArrayList<String>(1 + platform.aliases.size)
        names.add(platform.id)
        names.addAll(platform.aliases)
        for (index in names.indices) {
            val name = names[index]
            val repeated = names.subList(0, index).any { it.equals(name, ignoreCase = true) }
            if (repeated) error("Platform ${platform.id} repeats $name")
            val owner = (already + pending).firstOrNull { it.first.equals(name, ignoreCase = true) }
            if (owner != null) error("Platform $name collides with ${owner.second}")
        }
        names.forEach { pending.add(it to platform.id) }
    }
    return pending
}

private fun planIds(slot: String, incoming: List<String>, already: Set<String>) {
    val seen = HashSet<String>()
    for (id in incoming) {
        if (!seen.add(id) || id in already) error("$slot id $id is already registered")
    }
}
