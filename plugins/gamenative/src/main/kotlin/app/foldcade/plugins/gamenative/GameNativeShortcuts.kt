package app.foldcade.plugins.gamenative

/**
 * Upstream a2a121bb, utils/ShortcutUtils.kt: requestPinShortcut uses game_<id>
 * for every store. Android hides intents from launchers, so retain the publisher
 * and launch the original shortcut rather than inventing a STEAM source.
 * https://github.com/utkarshdalal/GameNative/blob/a2a121bb477070026fed8f6ee66f8d3e8b350971/app/src/main/java/app/gamenative/utils/ShortcutUtils.kt#L88-L166
 */
fun gameNativeShortcutId(id: String): Int? = id.removePrefix("game_")
    .takeIf { id.startsWith("game_") && it.all { c -> c in '0'..'9' } }
    ?.toIntOrNull()?.takeIf { it > 0 }

fun gameNativeShortcut(packageName: String, id: String, label: String?, pinned: Boolean, enabled: Boolean): CatalogGame? {
    if (!pinned || !enabled) return null
    return CatalogGame(label.orEmpty(), gameNativeShortcutId(id) ?: return null, "SHORTCUT", packageName).accepted()
}

/**
 * An unambiguous pin/export match keeps the export's identity, Steam art and Home
 * history while launching the pin's actual publisher. Never infer store from id alone.
 */
fun mergeGameNativeGames(pins: List<CatalogGame>, files: List<CatalogGame>): List<CatalogGame> {
    val mergedPins = HashSet<String>()
    val exports = files.map { file ->
        val pin = pins.singleOrNull { sameGame(it, file) }
        if (pin == null || files.count { sameGame(pin, it) } != 1) file else {
            mergedPins += pin.remoteKey
            file.copy(shortcutPackage = pin.shortcutPackage, stableKey = pin.stableKey)
        }
    }
    return pins.filter { it.remoteKey !in mergedPins } + exports
}

private fun sameGame(pin: CatalogGame, file: CatalogGame): Boolean =
    pin.appId == file.appId && pin.title.equals(file.title, ignoreCase = true) &&
        (pin.gameSource == "SHORTCUT" || pin.gameSource == file.gameSource)

/** A live pin keeps the identity and store previously learned for the same publisher, id and name. */
fun restoreGameNativePins(live: List<CatalogGame>?, stored: List<CatalogGame>): List<CatalogGame> =
    live?.map { pin ->
        val previous = stored.firstOrNull {
            it.shortcutPackage == pin.shortcutPackage && it.appId == pin.appId && it.title.equals(pin.title, ignoreCase = true)
        }
        previous?.copy(title = pin.title, stableKey = previous.remoteKey) ?: pin
    } ?: stored
