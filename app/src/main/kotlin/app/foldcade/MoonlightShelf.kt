package app.foldcade

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import app.foldcade.api.plugin.GameQuery
import app.foldcade.host.PluginHost
import app.foldcade.plugins.moonlight.MoonlightLibrary
import app.foldcade.plugins.moonlight.MoonlightPlatform
import app.foldcade.plugins.moonlight.MoonlightPlayer
import app.foldcade.plugins.moonlight.MoonlightShortcut
import app.foldcade.plugins.moonlight.MoonlightShortcutRead
import app.foldcade.plugins.moonlight.pinsWithout
import app.foldcade.plugins.moonlight.readMoonlightShortcuts

/**
 * Moonlight's pinned game shortcuts and computer shortcuts, visible because
 * Foldcade is the home app. A launcher never sees a shortcut's intent, so the
 * host and app come from each shortcut's id and label.
 * Null when this process cannot ask. No games means the query ran and found
 * no game shortcut. This does not read Moonlight's database, and it does not
 * query every package.
 */
internal fun queryMoonlightShortcuts(context: Context): MoonlightShortcutRead? {
    val launcher = context.getSystemService(LauncherApps::class.java) ?: return null
    if (!launcher.hasShortcutHostPermission()) return null
    val query = LauncherApps.ShortcutQuery().apply {
        setPackage(MoonlightPlayer.OFFICIAL_PACKAGE)
        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
    }
    val shortcuts = try {
        launcher.getShortcuts(query, Process.myUserHandle())
    } catch (_: SecurityException) {
        return null
    } catch (_: IllegalStateException) {
        return null
    }
    return readMoonlightShortcuts(
        shortcuts.orEmpty().map { shortcut ->
            MoonlightShortcut(
                id = shortcut.id,
                label = shortcut.shortLabel?.toString(),
                pinned = shortcut.isPinned,
                enabled = shortcut.isEnabled,
            )
        },
    )
}

/**
 * Drops Foldcade's pin for the Moonlight game [remoteKey]. A launcher sets its
 * whole pin list for a package at once, so the current pins are read first and
 * every other one is kept, disabled ones included. False when this process
 * cannot ask, the game was not pinned, or Android refused.
 */
internal fun unpinMoonlightShortcut(context: Context, remoteKey: String): Boolean {
    val launcher = context.getSystemService(LauncherApps::class.java) ?: return false
    if (!launcher.hasShortcutHostPermission()) return false
    val query = LauncherApps.ShortcutQuery().apply {
        setPackage(MoonlightPlayer.OFFICIAL_PACKAGE)
        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
    }
    return try {
        val user = Process.myUserHandle()
        val pinned = launcher.getShortcuts(query, user).orEmpty().filter { it.isPinned }.map { it.id }
        val keep = pinsWithout(pinned, remoteKey)
        if (keep.size == pinned.size) return false
        launcher.pinShortcuts(MoonlightPlayer.OFFICIAL_PACKAGE, keep, user)
        true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalStateException) {
        false
    }
}

/** Games currently in [MoonlightLibrary], one shelf tile each. */
internal suspend fun PluginHost.moonlightShelfGames(): List<ShelfGame> {
    val library = library(MoonlightLibrary.ID) as? MoonlightLibrary ?: return emptyList()
    val games = ArrayList<app.foldcade.api.plugin.Game>()
    var offset = 0
    while (true) {
        val page = listGames(
            MoonlightLibrary.ID,
            MoonlightPlatform.ID,
            GameQuery(offset = offset, limit = 50),
        )
        games.addAll(page.games)
        val next = page.nextOffset ?: break
        if (next <= offset) break
        offset = next
        if (games.size > 500) break
    }
    return games.map { game ->
        ShelfGame(
            id = game.remoteKey,
            title = game.label,
            shortText = library.displayName,
            platformId = game.platformId,
            occupiesBothDisplays = false,
            mark = "moonlight",
            libraryId = game.backendId,
            remoteKey = game.remoteKey,
        )
    }
}
