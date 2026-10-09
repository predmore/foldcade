package app.foldcade.host

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.playersForPlatform
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.SyncResult
import java.util.ServiceLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Loads plugins and calls library I/O and metadata fetch off [io].
 * Those calls are suspending. This type does not block the caller.
 * [cachedMetadata] stays synchronous because [MetadataProvider.cached] is already in memory.
 * Register from one thread before calling the suspending methods.
 */
class PluginHost(
    private val io: CoroutineDispatcher,
) {
    private val platforms = linkedMapOf<String, Platform>()
    private val players = linkedMapOf<String, Player>()
    private val libraries = linkedMapOf<String, LibraryBackend>()
    private val metadataProviders = linkedMapOf<String, MetadataProvider>()
    private val platformNames = mutableListOf<Pair<String, String>>()

    fun register(entry: PluginEntry) {
        if (entry.apiVersion != PLUGIN_API_VERSION) {
            error("Plugin API major ${entry.apiVersion} is incompatible with $PLUGIN_API_VERSION")
        }
        registerPlatforms(entry.platforms)
        entry.players.forEach { put("player", players, it.id, it) }
        entry.libraries.forEach { put("library", libraries, it.id, it) }
        entry.metadataProviders.forEach { put("metadata", metadataProviders, it.id, it) }
    }

    /** Loads every [PluginEntry] advertised by [classLoader]. Same path for a bundled jar or another APK. */
    fun load(classLoader: ClassLoader) {
        ServiceLoader.load(PluginEntry::class.java, classLoader).forEach(::register)
    }

    fun platform(id: String): Platform? = platforms[id]

    fun player(id: String): Player? = players[id]

    fun playersFor(platformId: String): List<Player> =
        playersForPlatform(platforms.values, players.values, platformId)

    fun library(id: String): LibraryBackend? = libraries[id]

    fun metadata(id: String): MetadataProvider? = metadataProviders[id]

    /** Already loaded. Not a fetch, and not moved onto [io]. */
    fun cachedMetadata(providerId: String, game: Game): GameMeta? =
        requireMetadata(providerId).cached(game)

    suspend fun connect(libraryId: String) = offMain { requireLibrary(libraryId).connect() }

    suspend fun disconnect(libraryId: String) = offMain { requireLibrary(libraryId).disconnect() }

    suspend fun listPlatforms(libraryId: String): List<ListedPlatform> =
        offMain { requireLibrary(libraryId).listPlatforms() }

    suspend fun listGames(libraryId: String, platformId: String, query: GameQuery): GamePage =
        offMain { requireLibrary(libraryId).listGames(platformId, query) }

    suspend fun ensureLocal(libraryId: String, game: Game): LaunchTarget =
        offMain { requireLibrary(libraryId).ensureLocal(game) }

    suspend fun saves(libraryId: String, game: Game): SaveSet =
        offMain { requireLibrary(libraryId).saves(game) }

    suspend fun prepareLaunch(libraryId: String, game: Game, player: Player): Placement =
        offMain { requireLibrary(libraryId).prepareLaunch(game, player) }

    suspend fun reconcile(
        libraryId: String,
        game: Game,
        player: Player,
        observed: ObservedSaves,
    ): SyncResult = offMain { requireLibrary(libraryId).reconcile(game, player, observed) }

    suspend fun fetchMetadata(providerId: String, game: Game): GameMeta? =
        offMain { requireMetadata(providerId).fetch(game) }

    private suspend fun <T> offMain(block: suspend () -> T): T = withContext(io) { block() }

    private fun requireLibrary(id: String): LibraryBackend =
        libraries[id] ?: error("No library registered with id $id")

    private fun requireMetadata(id: String): MetadataProvider =
        metadataProviders[id] ?: error("No metadata provider registered with id $id")

    /**
     * Rejects a platform id or alias that matches another, ignoring case,
     * including a repeat on the same platform. Nothing from [incoming] is
     * stored when one name collides.
     */
    private fun registerPlatforms(incoming: List<Platform>) {
        val pending = mutableListOf<Pair<String, String>>()
        for (platform in incoming) {
            val names = ArrayList<String>(1 + platform.aliases.size)
            names.add(platform.id)
            names.addAll(platform.aliases)
            for (index in names.indices) {
                val name = names[index]
                val repeated = names.subList(0, index).any { it.equals(name, ignoreCase = true) }
                if (repeated) error("Platform ${platform.id} repeats $name")
                val claimed = platformNames + pending
                val owner = claimed.firstOrNull { it.first.equals(name, ignoreCase = true) }
                if (owner != null) error("Platform $name collides with ${owner.second}")
            }
            names.forEach { pending.add(it to platform.id) }
        }
        platformNames.addAll(pending)
        incoming.forEach { put("platform", platforms, it.id, it) }
    }

    private fun <T> put(slot: String, into: MutableMap<String, T>, id: String, value: T) {
        if (into.containsKey(id)) error("$slot id $id is already registered")
        into[id] = value
    }
}
