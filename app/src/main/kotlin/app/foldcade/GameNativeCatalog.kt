package app.foldcade

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import app.foldcade.language.Copy
import app.foldcade.plugins.gamenative.CatalogGame
import app.foldcade.plugins.gamenative.GameNativeLibrary
import app.foldcade.plugins.gamenative.GameNativePlayer
import app.foldcade.plugins.gamenative.PcPlatform
import app.foldcade.plugins.gamenative.gameNativeShortcut
import app.foldcade.plugins.gamenative.mergeGameNativeGames
import app.foldcade.plugins.gamenative.restoreGameNativePins
import app.foldcade.plugins.gamenative.catalogGameFromLine
import app.foldcade.plugins.gamenative.catalogLine
import app.foldcade.romm.writeDurably
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads shortcuts GameNative already published and keeps the confirmed list.
 *
 * Android exposes another app's shortcuts to the default launcher. A security
 * failure leaves the list already stored. This does not read GameNative's
 * database and does not request every installed package.
 *
 * [steamFiles] are the games named by all five store export formats in library folders. They
 * join the catalog but are not stored with it, so a deleted file takes its
 * game off the grid at the next scan.
 */
data class GameNativeCatalogRead(val shortcutsRead: Boolean, val hasGames: Boolean, val storedCatalog: Boolean) {
    /** Cached pins remain in the catalog on a denied shortcut read; finished file scans can still remove exports. */
    fun settles(foldersRead: Boolean, hasFolders: Boolean): Boolean = foldersRead || !hasFolders

    fun releasesRestore(foldersRead: Boolean, hasFolders: Boolean): Boolean =
        shortcutsRead && (foldersRead || !hasFolders) && (hasGames || storedCatalog)
}

fun refreshGameNativeCatalog(
    context: Context,
    library: GameNativeLibrary,
    file: File,
    steamFiles: List<CatalogGame> = emptyList(),
): GameNativeCatalogRead {
    val storedCatalog = file.isFile
    val stored = readCatalog(file)
    val shortcuts = readGameNativeShortcuts(context, GameNativePlayer.PACKAGES)
    val pins = restoreGameNativePins(shortcuts, stored)
    library.confirm(mergeGameNativeGames(pins, steamFiles))
    Shelf.catalog = library.confirmed().map { it.toShelfGame() }
    try {
        keepGameNativeCatalog(file, library.confirmed().filter { it.shortcutPackage != null }, storedCatalog)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // The shelf already has the list. The next start reads shortcuts again.
    }
    return GameNativeCatalogRead(shortcuts != null, library.confirmed().isNotEmpty(), storedCatalog)
}

/** An empty first shortcut read must not manufacture the evidence of an earlier setup. */
internal fun keepGameNativeCatalog(file: File, games: List<CatalogGame>, storedCatalog: Boolean) {
    if (storedCatalog || games.isNotEmpty()) writeCatalog(file, games)
}

internal fun readCatalog(file: File): List<CatalogGame> {
    if (!file.isFile) return emptyList()
    return file.readLines().mapNotNull { line ->
        if (line.isBlank()) null else catalogGameFromLine(line)
    }
}

internal fun writeCatalog(file: File, games: List<CatalogGame>) {
    val body = games.map { catalogLine(it) }.filter { it.isNotEmpty() }
    val raw = if (body.isEmpty()) "" else body.joinToString("\n", postfix = "\n")
    writeDurably(file.toPath(), raw.toByteArray(Charsets.UTF_8))
}

internal fun readGameNativeShortcuts(context: Context, packages: List<String>): List<CatalogGame>? {
    val launcher = context.getSystemService(LauncherApps::class.java) ?: return null
    if (!launcher.hasShortcutHostPermission()) return null
    val user = Process.myUserHandle()
    val found = ArrayList<CatalogGame>()
    for (packageName in packages) {
        val query = LauncherApps.ShortcutQuery()
        query.setPackage(packageName)
        query.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val shortcuts = try {
            launcher.getShortcuts(query, user).orEmpty()
        } catch (_: SecurityException) {
            return null
        } catch (_: IllegalStateException) {
            return null
        }
        for (shortcut in shortcuts) {
            val game = gameNativeShortcut(packageName, shortcut.id,
                shortcut.longLabel?.toString() ?: shortcut.shortLabel?.toString(), shortcut.isPinned, shortcut.isEnabled)
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
    shortcut = shortcutPackage != null,
)

/** Removes only this game's pin, preserving every other shortcut and package. */
internal fun unpinGameNativeShortcut(context: Context, game: CatalogGame): Boolean {
    val publisher = game.shortcutPackage ?: return false
    val launcher = context.getSystemService(LauncherApps::class.java) ?: return false
    if (!launcher.hasShortcutHostPermission()) return false
    return try {
        val user = Process.myUserHandle()
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(publisher)
            setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        }
        val ids = launcher.getShortcuts(query, user).orEmpty().filter { it.isPinned }.map { it.id }
        val keep = ids.filter { it != "game_${game.appId}" }
        if (keep == ids) false else { launcher.pinShortcuts(publisher, keep, user); true }
    } catch (_: SecurityException) { false } catch (_: IllegalStateException) { false }
}
