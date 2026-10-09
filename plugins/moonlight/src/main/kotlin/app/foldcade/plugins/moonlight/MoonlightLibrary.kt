package app.foldcade.plugins.moonlight

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Games from a paired Moonlight host.
 *
 * The catalog is pinned shortcuts, or the list the caller confirmed.
 * [confirmImport] stores that list and shows it. [useCatalog] switches back to
 * pinned shortcuts, or to the imported list, without clearing either one.
 * This type does not open Moonlight's database, and it does not pair.
 *
 * [reconcile] is a no-op. Progress stays in Moonlight. RomM does not configure
 * this library: there is no metadata provider, and this method does not call RomM.
 */
class MoonlightLibrary : LibraryBackend {
    override val id: String = ID
    override val displayName: String = DISPLAY_NAME

    private val lock = Any()
    private var imported: List<MoonlightApp>? = null
    private var pinned: List<MoonlightApp> = emptyList()
    private var catalog: MoonlightCatalog = MoonlightCatalog.Pinned

    /** Pinned shortcuts Foldcade read. Shown while [catalog] is [MoonlightCatalog.Pinned]. */
    fun replacePinned(apps: List<MoonlightApp>) {
        synchronized(lock) {
            pinned = apps.distinctBy { it.remoteKey }
        }
    }

    fun pinnedApps(): List<MoonlightApp> = synchronized(lock) { pinned.toList() }

    fun importedApps(): List<MoonlightApp> = synchronized(lock) { imported.orEmpty().toList() }

    fun catalog(): MoonlightCatalog = synchronized(lock) { catalog }

    /**
     * Selects which list [listGames] returns.
     * Neither list is cleared. A source change does not remove grid placements;
     * those live outside this catalog.
     */
    fun useCatalog(next: MoonlightCatalog) {
        synchronized(lock) {
            catalog = next
        }
    }

    /**
     * Stores the list the user confirmed and shows it.
     * An empty list is a confirmed empty catalog.
     * Pinned shortcuts stay available through [useCatalog].
     */
    fun confirmImport(apps: List<MoonlightApp>) {
        synchronized(lock) {
            imported = apps.distinctBy { it.remoteKey }
            catalog = MoonlightCatalog.Imported
        }
    }

    fun hasConfirmedImport(): Boolean = synchronized(lock) { imported != null }

    override suspend fun connect() {
        coroutineContext.ensureActive()
    }

    override suspend fun disconnect() {
        coroutineContext.ensureActive()
    }

    override suspend fun listPlatforms(): List<ListedPlatform> {
        coroutineContext.ensureActive()
        return listOf(ListedPlatform(platformId = MoonlightPlatform.ID, displayName = MoonlightPlatform.displayName))
    }

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage {
        coroutineContext.ensureActive()
        if (platformId != MoonlightPlatform.ID) {
            return GamePage(games = emptyList(), nextOffset = null, total = 0)
        }
        val matched = snapshot().filter { app ->
            val text = query.text.trim()
            text.isEmpty() || app.label.contains(text, ignoreCase = true)
        }
        val offset = query.offset.coerceAtLeast(0)
        val limit = query.limit.coerceAtLeast(0)
        if (offset >= matched.size || limit == 0) {
            return GamePage(games = emptyList(), nextOffset = null, total = matched.size)
        }
        val slice = matched.drop(offset).take(limit)
        val next = offset + slice.size
        return GamePage(
            games = slice.map { it.toGame() },
            nextOffset = next.takeIf { it < matched.size },
            total = matched.size,
        )
    }

    override suspend fun ensureLocal(game: Game): LaunchTarget {
        coroutineContext.ensureActive()
        return requireApp(game).toTarget()
    }

    override suspend fun saves(game: Game): SaveSet {
        coroutineContext.ensureActive()
        requireApp(game)
        return SaveSet(ownerBackendId = id, slots = emptyList())
    }

    override suspend fun prepareLaunch(game: Game, player: Player): Placement {
        coroutineContext.ensureActive()
        return Placement(target = requireApp(game).toTarget())
    }

    /**
     * No-op. Does not read [observed], upload, or download.
     * RomM is not called. Moonlight keeps its own progress.
     */
    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult {
        coroutineContext.ensureActive()
        return SyncResult(SyncOutcome.Unchanged)
    }

    private fun snapshot(): List<MoonlightApp> = synchronized(lock) {
        when (catalog) {
            MoonlightCatalog.Imported -> (imported ?: emptyList()).toList()
            MoonlightCatalog.Pinned -> pinned.toList()
        }
    }

    private fun requireApp(game: Game): MoonlightApp {
        if (game.backendId != id) {
            throw PluginException.NotFound("Game is not in Moonlight.")
        }
        return snapshot().firstOrNull { it.remoteKey == game.remoteKey }
            ?: throw PluginException.NotFound("Game is not in Moonlight.")
    }

    private fun MoonlightApp.toGame(): Game = Game(
        backendId = id,
        remoteKey = remoteKey,
        platformId = MoonlightPlatform.ID,
        availability = Availability.LocalOnly,
        label = label,
    )

    private fun MoonlightApp.toTarget(): LaunchTarget.AppRef = LaunchTarget.AppRef(
        values = mapOf(
            MoonlightPlayer.HOST_UUID to hostUuid,
            MoonlightPlayer.APP_ID to appId,
        ),
    )

    companion object {
        const val ID: String = "moonlight"
        const val DISPLAY_NAME: String = "Moonlight"
    }
}

/** Which list [MoonlightLibrary.listGames] returns. */
enum class MoonlightCatalog {
    Imported,
    Pinned,
}
