package app.foldcade.api.plugin

/**
 * A system a library can classify a game into.
 * First-party and community platforms share this type.
 * [id] and [displayName] are already loaded. This type does no I/O.
 *
 * [id] is the canonical id. [aliases] are other ids that mean this platform.
 * Nintendo 3DS is `nintendo-3ds`. `3ds` and `n3ds` are aliases, so a
 * local-folder id and a RomM slug resolve to the same players.
 * This type does not scan a folder and does not talk to RomM.
 */
interface Platform {
    val id: String
    val displayName: String

    /**
     * File extensions this platform claims, without a leading dot, lowercase.
     * Empty when the platform is not a file type.
     */
    val extensions: Set<String>

    /** Other ids that resolve to [id]. Empty unless this platform declares some. */
    val aliases: Set<String>
        get() = emptySet()
}

/**
 * The canonical id for [idOrAlias], or null when no platform claims it.
 * Matching is case-insensitive. The returned id keeps the platform's spelling.
 */
fun canonicalPlatformId(platforms: Iterable<Platform>, idOrAlias: String): String? =
    platforms.firstOrNull { platform ->
        platform.id.equals(idOrAlias, ignoreCase = true) ||
            platform.aliases.any { alias -> alias.equals(idOrAlias, ignoreCase = true) }
    }?.id

/**
 * Players whose [Player.platformId] is the canonical id for [idOrAlias].
 * An alias and the canonical id return the same players.
 * Id and alias matching is case-insensitive.
 */
fun playersForPlatform(
    platforms: Iterable<Platform>,
    players: Iterable<Player>,
    idOrAlias: String,
): List<Player> {
    val canonical = canonicalPlatformId(platforms, idOrAlias) ?: return emptyList()
    return players.filter { it.platformId == canonical }
}
