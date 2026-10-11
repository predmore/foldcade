package app.foldcade.plugins.moonlight

import java.util.Locale

/**
 * What a launcher sees of one Moonlight shortcut. Android never shows a
 * launcher the shortcut's intent, so the host and app come from [id] and [label].
 *
 * Read from moonlight-android `ShortcutHelper`:
 * - A computer shortcut is dynamic. Its id is the host UUID and its label is the computer name.
 * - A game shortcut is pinned. Its id is the host UUID followed by the app id, with
 *   nothing between them. Its label is the app name, a space, and the computer
 *   name in parentheses.
 * - Moonlight disables a game shortcut when the host or the app goes away.
 */
data class MoonlightShortcut(
    val id: String,
    val label: String?,
    val pinned: Boolean,
    val enabled: Boolean,
)

/**
 * Games from enabled pinned shortcuts, and the computer names Moonlight's
 * shortcuts carry. [hostNames] is keyed by lowercase host UUID.
 */
data class MoonlightShortcutRead(
    val games: List<MoonlightApp>,
    val hostNames: Map<String, String>,
)

fun readMoonlightShortcuts(shortcuts: List<MoonlightShortcut>): MoonlightShortcutRead {
    val hostNames = LinkedHashMap<String, String>()
    shortcuts.forEach { shortcut ->
        val name = shortcut.label?.trim().orEmpty()
        if (isCanonicalUuid(shortcut.id) && name.isNotEmpty()) {
            hostNames[shortcut.id.lowercase(Locale.ROOT)] = name
        }
    }
    val games = shortcuts.mapNotNull { shortcut ->
        if (!shortcut.pinned || !shortcut.enabled) return@mapNotNull null
        val (hostUuid, appId) = splitGameShortcutId(shortcut.id) ?: return@mapNotNull null
        val key = hostUuid.lowercase(Locale.ROOT)
        val (label, labelHost) = splitGameLabel(shortcut.label, hostNames[key])
        if (key !in hostNames && labelHost != null) hostNames[key] = labelHost
        moonlightApp(hostUuid = hostUuid, appId = appId, label = label)
    }.distinctBy { it.remoteKey }
    return MoonlightShortcutRead(games = games, hostNames = hostNames)
}

/** [MoonlightApp.remoteKey] for a game shortcut id, or null when the id is not a game. */
fun moonlightShortcutKey(id: String): String? {
    val (hostUuid, appId) = splitGameShortcutId(id) ?: return null
    return "${hostUuid.lowercase(Locale.ROOT)}|$appId"
}

/**
 * The UUID is always 36 characters, so the app id is whatever follows it.
 * A computer shortcut has nothing after the UUID and is not a game.
 */
private fun splitGameShortcutId(id: String): Pair<String, String>? {
    if (id.length <= UUID_LENGTH) return null
    val hostUuid = id.substring(0, UUID_LENGTH)
    val appId = id.substring(UUID_LENGTH)
    if (!isCanonicalUuid(hostUuid) || !isAppId(appId)) return null
    return hostUuid to appId
}

/**
 * Takes the computer name off the end of a game label. A known name is matched
 * exactly. Otherwise the last balanced parenthesized group is the name, so a
 * game called "Halo (2003)" keeps its year. Returns the game name and the
 * computer name the label held, if any.
 */
private fun splitGameLabel(label: String?, hostName: String?): Pair<String?, String?> {
    val text = label?.trim() ?: return null to null
    if (hostName != null) {
        val suffix = " ($hostName)"
        if (text.endsWith(suffix) && text.length > suffix.length) {
            return text.removeSuffix(suffix).trim() to hostName
        }
        return text to null
    }
    if (!text.endsWith(")")) return text to null
    var depth = 0
    for (at in text.indices.reversed()) {
        when (text[at]) {
            ')' -> depth++
            '(' -> {
                depth--
                if (depth == 0) {
                    if (at < 2 || text[at - 1] != ' ') return text to null
                    val name = text.substring(at + 1, text.length - 1).trim()
                    val game = text.substring(0, at - 1).trim()
                    if (name.isEmpty() || game.isEmpty()) return text to null
                    return game to name
                }
            }
        }
    }
    return text to null
}

private fun isCanonicalUuid(value: String): Boolean = CANONICAL_UUID.matches(value)

private const val UUID_LENGTH: Int = 36
private val CANONICAL_UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
