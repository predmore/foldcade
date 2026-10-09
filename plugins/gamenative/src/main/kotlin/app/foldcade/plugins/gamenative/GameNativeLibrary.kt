package app.foldcade.plugins.gamenative

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
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Games already in GameNative, keyed by `(game_source, app_id)`.
 *
 * The catalog is a list the caller confirms, or shortcuts GameNative already
 * published. [catalogGameFromShortcut] reads the same extras `ShortcutUtils`
 * writes. This backend does not open GameNative's database and does not download.
 *
 * [reconcile] is a no-op. Store logins and saves stay inside GameNative.
 * [PROGRESS_NOTE] is the sentence the UI shows. RomM does not configure this
 * backend, and this entry does not provide metadata.
 */
class GameNativeLibrary : LibraryBackend {
    override val id: String = ID
    override val displayName: String = DISPLAY_NAME

    @Volatile
    private var catalog: List<CatalogGame> = emptyList()

    /** Games accepted by the last [confirm], in that order. */
    fun confirmed(): List<CatalogGame> = catalog

    /**
     * Replaces the catalog with [entries] the caller already holds.
     * A non-positive app id or an unknown source is dropped.
     * The same `source_appId` keeps its first place and takes the later title.
     */
    fun confirm(entries: List<CatalogGame>) {
        val next = LinkedHashMap<String, CatalogGame>()
        for (entry in entries) {
            val game = entry.accepted() ?: continue
            next[game.remoteKey] = game
        }
        catalog = next.values.toList()
    }

    override suspend fun connect() {
        coroutineContext.ensureActive()
    }

    override suspend fun disconnect() {
        coroutineContext.ensureActive()
    }

    override suspend fun listPlatforms(): List<ListedPlatform> {
        coroutineContext.ensureActive()
        return listOf(ListedPlatform(PcPlatform.ID, PcPlatform.DISPLAY_NAME))
    }

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage {
        coroutineContext.ensureActive()
        if (platformId != PcPlatform.ID) {
            return GamePage(games = emptyList(), nextOffset = null, total = 0)
        }
        val needle = query.text.trim()
        val matched = catalog.filter { game ->
            needle.isEmpty() || game.title.contains(needle, ignoreCase = true)
        }
        val offset = query.offset.coerceAtLeast(0)
        val limit = query.limit.coerceAtLeast(0)
        val games = matched.drop(offset).take(limit).map { it.toGame() }
        val consumed = offset + games.size
        val nextOffset = if (consumed < matched.size) consumed else null
        return GamePage(games = games, nextOffset = nextOffset, total = matched.size)
    }

    override suspend fun ensureLocal(game: Game): LaunchTarget {
        coroutineContext.ensureActive()
        return appRef(requireGame(game))
    }

    override suspend fun saves(game: Game): SaveSet {
        coroutineContext.ensureActive()
        requireGame(game)
        return SaveSet(ownerBackendId = id, slots = emptyList())
    }

    override suspend fun prepareLaunch(game: Game, player: Player): Placement {
        coroutineContext.ensureActive()
        val found = requireGame(game)
        return Placement(
            target = appRef(found),
            savesToPlace = emptyList(),
            sync = SyncResult(SyncOutcome.Unchanged, PROGRESS_NOTE),
        )
    }

    /**
     * No-op. [observed] is ignored. Progress stays in GameNative, so nothing
     * here is uploaded, downloaded, or paired with a RomM slot.
     */
    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult {
        coroutineContext.ensureActive()
        requireGame(game)
        return SyncResult(SyncOutcome.Unchanged, PROGRESS_NOTE)
    }

    private fun requireGame(game: Game): CatalogGame {
        if (game.backendId != id) {
            throw PluginException.NotFound("GameNative does not have this game.")
        }
        return catalog.firstOrNull { it.remoteKey == game.remoteKey }
            ?: throw PluginException.NotFound("GameNative does not have this game.")
    }

    companion object {
        const val ID: String = "gamenative"
        const val DISPLAY_NAME: String = "GameNative"
        const val PROGRESS_NOTE: String = "Progress lives in GameNative."
    }
}

/**
 * One game the user already has in GameNative.
 * [remoteKey] matches the in-app id `IntentLaunchManager` builds: `SOURCE_appId`.
 */
data class CatalogGame(
    val title: String,
    val appId: Int,
    val gameSource: String,
) {
    val remoteKey: String get() = "${gameSource}_$appId"

    fun accepted(): CatalogGame? {
        val source = gameSource.trim().uppercase(Locale.ROOT)
        if (appId <= 0 || source !in GAME_SOURCES) return null
        val name = title.trim().replace('\n', ' ').replace('\r', ' ').ifEmpty { "$source $appId" }
        return CatalogGame(title = name, appId = appId, gameSource = source)
    }
}

/**
 * Facts copied off a shortcut intent. The shell reads the shortcut. This type
 * does not.
 *
 * [appIdExtra] is the `app_id` int, or -1 when that extra is absent.
 * [viewAppId] and [viewGameSource] are the `gamenative://run` query, when the
 * action is `ACTION_VIEW`.
 */
data class ShortcutLaunch(
    val action: String?,
    val appIdExtra: Int,
    val gameSourceExtra: String?,
    val viewAppId: String?,
    val viewGameSource: String?,
    val label: String,
)

/**
 * A catalog row from a shortcut, using the same rules as `IntentLaunchManager`.
 * `LAUNCH_GAME` defaults a missing or unknown source to `STEAM`.
 * `ACTION_VIEW` on `gamenative://run` does not. Any other action is ignored.
 */
fun catalogGameFromShortcut(launch: ShortcutLaunch): CatalogGame? {
    val parsed = when (launch.action) {
        GameNativePlayer.ACTION_LAUNCH_GAME -> {
            val raw = launch.gameSourceExtra?.trim()?.uppercase(Locale.ROOT)
            val source = if (raw != null && raw in GAME_SOURCES) raw else GameNativePlayer.DEFAULT_SOURCE
            launch.appIdExtra to source
        }
        ACTION_VIEW -> {
            val source = launch.viewGameSource?.trim()?.uppercase(Locale.ROOT)
            val appId = launch.viewAppId?.toIntOrNull() ?: -1
            if (source == null || source !in GAME_SOURCES) return null
            appId to source
        }
        else -> return null
    }
    return CatalogGame(
        title = launch.label,
        appId = parsed.first,
        gameSource = parsed.second,
    ).accepted()
}

/** One confirmed game, stored as `SOURCE`, app id, and title. */
fun catalogLine(game: CatalogGame): String {
    val accepted = game.accepted() ?: return ""
    val title = accepted.title.replace('\t', ' ')
    return "${accepted.gameSource}\t${accepted.appId}\t$title"
}

/** The inverse of [catalogLine]. A bad source or app id is dropped. */
fun catalogGameFromLine(line: String): CatalogGame? {
    val parts = line.split('\t', limit = 3)
    if (parts.size != 3) return null
    val appId = parts[1].trim().toIntOrNull() ?: return null
    return CatalogGame(
        title = parts[2],
        appId = appId,
        gameSource = parts[0],
    ).accepted()
}

private fun appRef(game: CatalogGame): LaunchTarget.AppRef = LaunchTarget.AppRef(
    values = mapOf(
        GameNativePlayer.EXTRA_APP_ID to game.appId.toString(),
        GameNativePlayer.EXTRA_GAME_SOURCE to game.gameSource,
    ),
)

private fun CatalogGame.toGame(): Game = Game(
    backendId = GameNativeLibrary.ID,
    remoteKey = remoteKey,
    platformId = PcPlatform.ID,
    availability = Availability.LocalOnly,
    label = title,
)

private const val ACTION_VIEW: String = "android.intent.action.VIEW"
