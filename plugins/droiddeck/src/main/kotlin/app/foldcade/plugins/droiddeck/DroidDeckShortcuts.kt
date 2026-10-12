package app.foldcade.plugins.droiddeck

/**
 * What a launcher sees of one DroidDeck shortcut. Android never shows a
 * launcher the shortcut's intent, so the game comes from [id].
 *
 * Read from DroidDeck `GameShortcuts`:
 * - A game shortcut is pinned through Add to home screen. Its id is `game:`
 *   followed by the game id. Its short label is the name cut to 40 characters,
 *   and its long label is the whole name.
 * - DroidDeck publishes no dynamic shortcuts.
 */
data class DroidDeckShortcut(
    val id: String,
    val shortLabel: String?,
    val longLabel: String?,
    val pinned: Boolean,
    val enabled: Boolean,
)

/** Games from enabled pinned game shortcuts, one per game id, in the order read. */
fun readDroidDeckShortcuts(shortcuts: List<DroidDeckShortcut>): List<DroidDeckGame> =
    shortcuts.mapNotNull { shortcut ->
        if (!shortcut.pinned || !shortcut.enabled) return@mapNotNull null
        val gameId = droidDeckShortcutKey(shortcut.id) ?: return@mapNotNull null
        val label = shortcut.longLabel?.takeIf { it.isNotBlank() } ?: shortcut.shortLabel
        droidDeckGame(gameId, label)
    }.distinctBy { it.remoteKey }

/** [DroidDeckGame.remoteKey] for a game shortcut id, or null when the id is not a game. */
fun droidDeckShortcutKey(id: String): String? {
    if (!id.startsWith(SHORTCUT_PREFIX)) return null
    val gameId = id.removePrefix(SHORTCUT_PREFIX)
    return droidDeckGameId(gameId)?.takeIf { it == gameId }
}

/**
 * The pinned ids to keep when the game [remoteKey] leaves Home. Ids that are
 * not game shortcuts are kept.
 */
fun droidDeckPinsWithout(pinnedIds: List<String>, remoteKey: String): List<String> =
    pinnedIds.filter { droidDeckShortcutKey(it) != remoteKey }

private const val SHORTCUT_PREFIX: String = "game:"
