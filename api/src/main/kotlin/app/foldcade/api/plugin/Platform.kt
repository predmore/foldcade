package app.foldcade.api.plugin

/**
 * A system a library can classify a game into.
 * First-party and community platforms share this type.
 * [id] and [displayName] are already loaded. This type does no I/O.
 */
interface Platform {
    val id: String
    val displayName: String

    /**
     * File extensions this platform claims, without a leading dot, lowercase.
     * Empty when the platform is not a file type.
     */
    val extensions: Set<String>
}
