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

/** Pin and export of the same id and name produce one tile; never guess a pin's store from its id alone. */
fun mergeGameNativeGames(pins: List<CatalogGame>, files: List<CatalogGame>): List<CatalogGame> = pins + files.filter { file ->
    pins.none { pin -> pin.appId == file.appId && pin.title.equals(file.title, ignoreCase = true) }
}
