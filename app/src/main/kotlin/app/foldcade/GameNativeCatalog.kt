package app.foldcade

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import app.foldcade.language.Copy
import app.foldcade.plugins.gamenative.CatalogGame
import app.foldcade.plugins.gamenative.GameNativeLibrary
import app.foldcade.plugins.gamenative.GameNativePlayer
import app.foldcade.plugins.gamenative.PcPlatform
import app.foldcade.plugins.gamenative.ShortcutLaunch
import app.foldcade.plugins.gamenative.catalogGameFromLine
import app.foldcade.plugins.gamenative.catalogGameFromShortcut
import app.foldcade.plugins.gamenative.catalogLine
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads shortcuts GameNative already published and keeps the confirmed list.
 * v1 does not offer a screen for typing app ids.
 *
 * Android exposes another app's shortcuts to the default launcher. A security
 * failure leaves the list already stored. This does not read GameNative's
 * database and does not request every installed package.
 *
 * [steamFiles] are the games .steam files in the library folders name. They
 * join the catalog but are not stored with it, so a deleted file takes its
 * game off the grid at the next scan.
 */
fun refreshGameNativeCatalog(
    context: Context,
    library: GameNativeLibrary,
    file: File,
    steamFiles: List<CatalogGame> = emptyList(),
) {
    val stored = readCatalog(file)
    val shortcuts = readGameNativeShortcuts(context, GameNativePlayer.PACKAGES)
    library.confirm(stored + shortcuts + steamFiles)
    Shelf.catalog = library.confirmed().map { it.toShelfGame() }
    val kept = (stored + shortcuts).map { it.remoteKey }.toSet()
    try {
        writeCatalog(file, library.confirmed().filter { it.remoteKey in kept })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // The shelf already has the list. The next start reads shortcuts again.
    }
}

internal fun readCatalog(file: File): List<CatalogGame> {
    if (!file.isFile) return emptyList()
    return file.readLines().mapNotNull { line ->
        if (line.isBlank()) null else catalogGameFromLine(line)
    }
}

internal fun writeCatalog(file: File, games: List<CatalogGame>) {
    file.parentFile?.mkdirs()
    val body = games.map { catalogLine(it) }.filter { it.isNotEmpty() }
    file.writeText(if (body.isEmpty()) "" else body.joinToString("\n", postfix = "\n"))
}

internal fun readGameNativeShortcuts(context: Context, packages: List<String>): List<CatalogGame> {
    val launcher = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
    val user = Process.myUserHandle()
    val found = ArrayList<CatalogGame>()
    for (packageName in packages) {
        val query = LauncherApps.ShortcutQuery()
        query.setPackage(packageName)
        query.setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
        )
        val shortcuts = try {
            launcher.getShortcuts(query, user).orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: IllegalStateException) {
            emptyList()
        }
        for (shortcut in shortcuts) {
            val intent = shortcut.intents?.lastOrNull() ?: continue
            val game = catalogGameFromShortcut(
                ShortcutLaunch(
                    action = intent.action,
                    appIdExtra = intent.getIntExtra(GameNativePlayer.EXTRA_APP_ID, -1),
                    gameSourceExtra = intent.getStringExtra(GameNativePlayer.EXTRA_GAME_SOURCE),
                    viewAppId = intent.data?.getQueryParameter("appid"),
                    viewGameSource = intent.data?.getQueryParameter("gamesource"),
                    label = shortcut.shortLabel?.toString().orEmpty(),
                ),
            )
            if (game != null) found += game
        }
    }
    return found
}

private fun CatalogGame.toShelfGame(): ShelfGame = ShelfGame(
    id = "gamenative.$remoteKey",
    title = title,
    shortText = Copy.progressInGameNative,
    platformId = PcPlatform.ID,
    occupiesBothDisplays = false,
    libraryId = GameNativeLibrary.ID,
    remoteKey = remoteKey,
)
