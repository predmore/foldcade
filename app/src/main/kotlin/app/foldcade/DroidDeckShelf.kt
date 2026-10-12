package app.foldcade

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameQuery
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import app.foldcade.localfolder.SteamShortcut
import app.foldcade.plugins.gamenative.CatalogGame
import app.foldcade.plugins.gamenative.GameNativePlayer
import app.foldcade.plugins.droiddeck.DroidDeckGame
import app.foldcade.plugins.droiddeck.DroidDeckLibrary
import app.foldcade.plugins.droiddeck.DroidDeckPlatform
import app.foldcade.plugins.droiddeck.DroidDeckPlayer
import app.foldcade.plugins.droiddeck.DroidDeckShortcut
import app.foldcade.plugins.droiddeck.droidDeckGame
import app.foldcade.plugins.droiddeck.droidDeckPinsWithout
import app.foldcade.plugins.droiddeck.readDroidDeckShortcuts

/**
 * DroidDeck's pinned game shortcuts, visible because Foldcade is the home app.
 * A launcher never sees a shortcut's intent, so each game comes from its id.
 * Null when this process cannot ask. An empty list means the query ran and
 * found no game shortcut. This does not read DroidDeck's files, and it does
 * not query every package.
 */
internal fun queryDroidDeckShortcuts(context: Context): List<DroidDeckGame>? {
    val launcher = context.getSystemService(LauncherApps::class.java) ?: return null
    if (!launcher.hasShortcutHostPermission()) return null
    val query = LauncherApps.ShortcutQuery().apply {
        setPackage(DroidDeckPlayer.PACKAGE)
        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
    }
    val shortcuts = try {
        launcher.getShortcuts(query, Process.myUserHandle())
    } catch (_: SecurityException) {
        return null
    } catch (_: IllegalStateException) {
        return null
    }
    return readDroidDeckShortcuts(
        shortcuts.orEmpty().map { shortcut ->
            DroidDeckShortcut(
                id = shortcut.id,
                shortLabel = shortcut.shortLabel?.toString(),
                longLabel = shortcut.longLabel?.toString(),
                pinned = shortcut.isPinned,
                enabled = shortcut.isEnabled,
            )
        },
    )
}

/**
 * Drops Foldcade's pin for the DroidDeck game [remoteKey]. A launcher sets its
 * whole pin list for a package at once, so the current pins are read first and
 * every other one is kept. False when this process cannot ask, the game was
 * not pinned, or Android refused.
 */
internal fun unpinDroidDeckShortcut(context: Context, remoteKey: String): Boolean {
    val launcher = context.getSystemService(LauncherApps::class.java) ?: return false
    if (!launcher.hasShortcutHostPermission()) return false
    val query = LauncherApps.ShortcutQuery().apply {
        setPackage(DroidDeckPlayer.PACKAGE)
        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
    }
    return try {
        val user = Process.myUserHandle()
        val pinned = launcher.getShortcuts(query, user).orEmpty().filter { it.isPinned }.map { it.id }
        val keep = droidDeckPinsWithout(pinned, remoteKey)
        if (keep.size == pinned.size) return false
        launcher.pinShortcuts(DroidDeckPlayer.PACKAGE, keep, user)
        true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalStateException) {
        false
    }
}

/** Games currently in [DroidDeckLibrary], one shelf tile each. */
internal suspend fun PluginHost.droidDeckShelfGames(): List<ShelfGame> {
    if (library(DroidDeckLibrary.ID) !is DroidDeckLibrary) return emptyList()
    val games = ArrayList<Game>()
    var offset = 0
    while (true) {
        val page = listGames(
            DroidDeckLibrary.ID,
            DroidDeckPlatform.ID,
            GameQuery(offset = offset, limit = 50),
        )
        games.addAll(page.games)
        val next = page.nextOffset ?: break
        if (next <= offset) break
        offset = next
        if (games.size > 2000) break
    }
    return games.map { game ->
        ShelfGame(
            id = Shelf.DROIDDECK_PREFIX + game.remoteKey,
            title = game.label,
            shortText = Copy.progressInDroidDeck,
            platformId = game.platformId,
            occupiesBothDisplays = false,
            mark = "droiddeck",
            libraryId = game.backendId,
            remoteKey = game.remoteKey,
        )
    }
}

/** The .steam files from the folders, split between the two apps that play them. */
internal data class SteamFileRoute(
    val gameNative: List<CatalogGame>,
    val droidDeck: List<DroidDeckGame>,
)

/**
 * A .steam file DroidDeck synced, or one whose id only DroidDeck can start,
 * goes to DroidDeck. Any other goes to GameNative, unless DroidDeck is the
 * only one of the two installed. A Steam app id names the same game in both.
 */
internal fun routeSteamFiles(
    files: List<SteamShortcut>,
    gameNativeInstalled: Boolean,
    droidDeckInstalled: Boolean,
): SteamFileRoute {
    val droidDeckOnly = droidDeckInstalled && !gameNativeInstalled
    val (toDroidDeck, toGameNative) = files.partition { file ->
        file.fromDroidDeck || file.appId == null || droidDeckOnly
    }
    return SteamFileRoute(
        gameNative = toGameNative.mapNotNull { file ->
            file.appId?.let { CatalogGame(title = file.title, appId = it, gameSource = GameNativePlayer.DEFAULT_SOURCE) }
        },
        droidDeck = toDroidDeck.mapNotNull { droidDeckGame(it.gameId, it.title) },
    )
}
