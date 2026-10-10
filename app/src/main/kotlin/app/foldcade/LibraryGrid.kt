package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginException
import app.foldcade.host.PluginCallException
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * One cell on the library grid.
 * A platform cell has no [game]. A game cell carries the record the host listed.
 */
data class GridEntry(
    val id: String,
    val title: String,
    val shortText: String,
    val platformId: String?,
    val availabilityLabel: String?,
    val occupiesBothDisplays: Boolean,
    val game: Game? = null,
)

sealed interface LoadedLibrary {
    data class Platforms(val entries: List<GridEntry>) : LoadedLibrary
    data object NoPlatforms : LoadedLibrary
    data object Unreachable : LoadedLibrary
}

/**
 * Lists the platforms of [libraryId] through the host.
 * A failure the shell can show becomes [LoadedLibrary.Unreachable].
 * Cancellation and [VirtualMachineError] still propagate.
 */
internal suspend fun loadPlatforms(
    plugins: PluginHost,
    libraryId: String,
    folderName: (String) -> String = { "" },
    occupiesBoth: (String) -> Boolean = { false },
): LoadedLibrary {
    return try {
        plugins.connect(libraryId)
        val listed = plugins.listPlatforms(libraryId)
        if (listed.isEmpty()) return LoadedLibrary.NoPlatforms
        val entries = listed.map { platform ->
            coroutineContext.ensureActive()
            // A count the listing already has saves one request per platform. RomM lists
            // every platform it knows, most of them empty, so that was most of the load.
            val count = platform.gameCount
                ?: plugins.listGames(libraryId, platform.platformId, GameQuery(limit = 0)).total
            GridEntry(
                id = platform.platformId,
                title = platform.displayName,
                shortText = count?.toString().orEmpty(),
                platformId = platform.platformId,
                availabilityLabel = null,
                occupiesBothDisplays = occupiesBoth(platform.platformId),
                game = null,
            )
        }
        LoadedLibrary.Platforms(entries)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (fatal: VirtualMachineError) {
        throw fatal
    } catch (_: PluginException) {
        LoadedLibrary.Unreachable
    } catch (_: PluginCallException) {
        LoadedLibrary.Unreachable
    }
}

/**
 * Every game [libraryId] lists for [platformId], in the order [listGames] returns.
 * [folderName] is the local-folder directory name. Other libraries pass an empty string.
 */
internal suspend fun loadGames(
    plugins: PluginHost,
    libraryId: String,
    platformId: String,
    folderName: (String) -> String = { "" },
    occupiesBoth: (String) -> Boolean = { false },
): List<GridEntry> {
    val games = ArrayList<Game>()
    var offset = 0
    while (true) {
        coroutineContext.ensureActive()
        val page = plugins.listGames(
            libraryId,
            platformId,
            GameQuery(offset = offset, limit = PAGE),
        )
        games += page.games
        val next = page.nextOffset ?: break
        if (next <= offset) break
        offset = next
    }
    val both = occupiesBoth(platformId)
    return games.map { game ->
        GridEntry(
            id = game.remoteKey,
            title = game.label,
            shortText = folderName(game.remoteKey),
            platformId = game.platformId,
            availabilityLabel = availabilityCopy(game.availability),
            occupiesBothDisplays = both,
            game = game,
        )
    }
}

internal fun availabilityCopy(availability: Availability): String = when (availability) {
    Availability.RemoteOnly -> Copy.notOnDevice
    Availability.Cached -> Copy.onDevice
    Availability.LocalOnly -> Copy.inFolder
}

/** True when every stored player for [platformId] takes both screens. No player does not. */
internal fun platformOccupiesBoth(plugins: PluginHost, platformId: String): Boolean {
    val players = plugins.playersFor(platformId)
    return players.isNotEmpty() && players.all { it.occupiesBothDisplays }
}

sealed interface GameLaunch {
    /** No player is registered. The stand-in opens this content URI with a read grant. */
    data class Dummy(val uri: String) : GameLaunch

    /** The first registered player with an installed package, as in [preferredPlayer]. */
    data class Installed(val player: Player, val packageName: String) : GameLaunch

    /** Players are registered and none of their packages are installed. */
    data class Missing(val playerName: String, val packages: List<String>) : GameLaunch

    /** No player, and the backend did not hand back a content URI. */
    data object NotAFile : GameLaunch
}

/**
 * Picks a launch from the players the host stored and the target the backend returned.
 * [installedPackage] is the first installed package name for that player, or null.
 */
internal fun planLaunch(
    players: List<Player>,
    target: LaunchTarget,
    installedPackage: (Player) -> String?,
): GameLaunch {
    if (players.isEmpty()) {
        val uri = (target as? LaunchTarget.ContentUri)?.uri?.takeIf { it.isNotBlank() }
        return if (uri != null) GameLaunch.Dummy(uri) else GameLaunch.NotAFile
    }
    for (player in players) {
        val packageName = installedPackage(player) ?: continue
        return GameLaunch.Installed(player, packageName)
    }
    return missingOf(players)
}

/**
 * [GameLaunch.Missing] when players are registered and none is installed, else null.
 * Needs no [LaunchTarget], so a launch can ask before it downloads anything.
 */
internal fun missingPlayer(players: List<Player>, installedPackage: (Player) -> String?): GameLaunch.Missing? {
    if (players.isEmpty() || players.any { installedPackage(it) != null }) return null
    return missingOf(players)
}

private fun missingOf(players: List<Player>) = GameLaunch.Missing(
    playerName = players.first().displayName,
    packages = players.flatMap { it.packageNames }.distinct(),
)

private const val PAGE: Int = 50
