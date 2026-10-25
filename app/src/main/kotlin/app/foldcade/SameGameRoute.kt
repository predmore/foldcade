package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game

/**
 * How one Home tile starts. [play] is the copy the player opens, and play is kept under
 * [tileId]. [saves] is the copy whose library keeps the saves, which may be another copy
 * of the same file: a game in a folder that a server also has syncs through the server.
 * [servers] are the server copies used by save actions and sync notices.
 */
internal data class GameRoute(
    val tileId: String,
    val play: Game,
    val saves: Game,
    val servers: List<Game> = listOf(saves).filter { it.availability != Availability.LocalOnly },
)

/**
 * Picks the copy of a game that plays and the one that keeps its saves. A copy already on the
 * device plays before one that must download, and [pressed] plays when a library grid named
 * it. Saves go to a pressed server copy, else the server copy by [libraryRank], so every
 * copy shares one save.
 */
internal fun routeSameGame(
    tileId: String,
    copies: List<Game>,
    pressed: Game? = null,
    libraryRank: (String) -> Int,
): GameRoute {
    val all = (listOfNotNull(pressed) + copies).distinctBy { it.backendId to it.remoteKey }
    val play = pressed ?: all.minWith(
        compareBy<Game>({ onDeviceRank(it.availability) }, { libraryRank(it.backendId) }, { it.backendId }, { it.remoteKey }),
    )
    val servers = all.filter { it.availability != Availability.LocalOnly }
        .sortedWith(compareBy<Game>({ libraryRank(it.backendId) }, { it.backendId }, { it.remoteKey.length }, { it.remoteKey }))
    val saves = pressed?.takeIf { it in servers } ?: servers.firstOrNull() ?: play
    return GameRoute(tileId, play, saves, servers)
}

private fun onDeviceRank(availability: Availability): Int = when (availability) {
    Availability.LocalOnly -> 0
    Availability.Cached -> 1
    else -> 2
}
