package app.foldcade.plugins.droiddeck

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
 * Games DroidDeck put where Foldcade can see them, keyed by game id.
 *
 * The catalog is the pinned game shortcuts Foldcade last read, then the
 * `.steam` files DroidDeck's game-file sync wrote into a library folder. The
 * shell reads both and hands them over with [replaceGames]. This type does not
 * open DroidDeck's files, and it does not download.
 *
 * [reconcile] is a no-op. Steam Cloud keeps the saves. RomM does not configure
 * this library, and this entry does not provide metadata.
 */
class DroidDeckLibrary : LibraryBackend {
    override val id: String = ID
    override val displayName: String = DISPLAY_NAME

    private val lock = Any()
    private var games: List<DroidDeckGame> = emptyList()

    /** Replaces the catalog. The first game with an id keeps its place and its title. */
    fun replaceGames(next: List<DroidDeckGame>) {
        synchronized(lock) {
            games = next.distinctBy { it.remoteKey }
        }
    }

    fun knownGames(): List<DroidDeckGame> = snapshot()

    override suspend fun connect() {
        coroutineContext.ensureActive()
    }

    override suspend fun disconnect() {
        coroutineContext.ensureActive()
    }

    override suspend fun listPlatforms(): List<ListedPlatform> {
        coroutineContext.ensureActive()
        return listOf(ListedPlatform(platformId = DroidDeckPlatform.ID, displayName = DroidDeckPlatform.displayName))
    }

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage {
        coroutineContext.ensureActive()
        if (platformId != DroidDeckPlatform.ID) {
            return GamePage(games = emptyList(), nextOffset = null, total = 0)
        }
        val needle = query.text.trim()
        val matched = snapshot().filter { game ->
            needle.isEmpty() || game.title.contains(needle, ignoreCase = true)
        }
        val offset = query.offset.coerceAtLeast(0)
        val limit = query.limit.coerceAtLeast(0)
        val page = matched.drop(offset).take(limit)
        val consumed = offset + page.size
        return GamePage(
            games = page.map { it.toGame() },
            nextOffset = consumed.takeIf { page.isNotEmpty() && it < matched.size },
            total = matched.size,
        )
    }

    override suspend fun ensureLocal(game: Game): LaunchTarget {
        coroutineContext.ensureActive()
        return requireGame(game).toTarget()
    }

    override suspend fun saves(game: Game): SaveSet {
        coroutineContext.ensureActive()
        requireGame(game)
        return SaveSet(ownerBackendId = id, slots = emptyList())
    }

    override suspend fun prepareLaunch(game: Game, player: Player): Placement {
        coroutineContext.ensureActive()
        return Placement(
            target = requireGame(game).toTarget(),
            sync = SyncResult(SyncOutcome.Unchanged, PROGRESS_NOTE),
        )
    }

    /** No-op. Does not read [observed], upload, or download. Steam Cloud keeps the saves. */
    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult {
        coroutineContext.ensureActive()
        return SyncResult(SyncOutcome.Unchanged, PROGRESS_NOTE)
    }

    private fun snapshot(): List<DroidDeckGame> = synchronized(lock) { games.toList() }

    private fun requireGame(game: Game): DroidDeckGame {
        if (game.backendId != id) {
            throw PluginException.NotFound("DroidDeck does not have this game.")
        }
        return snapshot().firstOrNull { it.remoteKey == game.remoteKey }
            ?: throw PluginException.NotFound("DroidDeck does not have this game.")
    }

    private fun DroidDeckGame.toGame(): Game = Game(
        backendId = id,
        remoteKey = remoteKey,
        platformId = DroidDeckPlatform.ID,
        availability = Availability.LocalOnly,
        label = title,
    )

    private fun DroidDeckGame.toTarget(): LaunchTarget.AppRef =
        LaunchTarget.AppRef(values = mapOf(DroidDeckPlayer.GAME_ID to gameId))

    companion object {
        const val ID: String = "droiddeck"
        const val DISPLAY_NAME: String = "DroidDeck"
        const val PROGRESS_NOTE: String = "Progress lives in DroidDeck."
    }
}
