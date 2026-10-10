package app.foldcade

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import app.foldcade.api.plugin.GameQuery
import app.foldcade.host.PluginHost
import app.foldcade.plugins.moonlight.MoonlightLibrary
import app.foldcade.plugins.moonlight.MoonlightPlatform
import app.foldcade.plugins.moonlight.MoonlightPlayer
import app.foldcade.plugins.moonlight.moonlightApp

/**
 * Pinned Moonlight shortcuts visible because Foldcade is the home app.
 * Null when this process cannot ask. An empty list means the query ran and
 * found no game shortcut. This does not read Moonlight's database, and it
 * does not query every package.
 */
internal fun readPinnedMoonlightShortcuts(context: Context): List<app.foldcade.plugins.moonlight.MoonlightApp>? {
    val launcher = context.getSystemService(LauncherApps::class.java) ?: return null
    if (!launcher.hasShortcutHostPermission()) return null
    val query = LauncherApps.ShortcutQuery().apply {
        setPackage(MoonlightPlayer.OFFICIAL_PACKAGE)
        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
    }
    val shortcuts = try {
        launcher.getShortcuts(query, Process.myUserHandle())
    } catch (_: SecurityException) {
        return null
    } catch (_: IllegalStateException) {
        return null
    } ?: return emptyList()
    return shortcuts.mapNotNull { shortcut ->
        val intent = shortcut.intent ?: return@mapNotNull null
        moonlightApp(
            hostUuid = intent.getStringExtra(MoonlightPlayer.EXTRA_HOST_UUID),
            appId = intent.getStringExtra(MoonlightPlayer.EXTRA_APP_ID),
            label = shortcut.shortLabel?.toString(),
        )
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
