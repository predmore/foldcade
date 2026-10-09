package app.foldcade.api.plugin

/**
 * How the game bytes are handed to the installed app.
 * The player plugin chooses this. The shell does not guess it.
 */
enum class GameHandoff {
    ContentUri,
    IntegerAppId,
    HostUuid,
}

/**
 * Where a player should be started.
 * A display-role setting can swap which panel is primary. Display ids are not stored here.
 */
enum class StartDisplay {
    Primary,
    PickerChoice,
}

/**
 * An installed app that runs a game for [platformId].
 * Players hang off a platform. First-party and community players share this type.
 *
 * [launchIntent] and [saveDeclarations] return data the plugin already holds.
 * They stay synchronous. A player uses `suspend` only when that plugin itself does I/O.
 */
interface Player {
    val id: String
    val displayName: String
    val platformId: String

    /** Package ids this player can bind. Detection is a list, not one constant. */
    val packageNames: List<String>

    val handoff: GameHandoff
    val startDisplay: StartDisplay
    val occupiesBothDisplays: Boolean

    /** True when the game must already be imported inside the player. */
    val requiresImportedGame: Boolean

    /** Save slots this player already knows how to read and write for [game]. */
    fun saveDeclarations(game: Game): List<SaveDeclaration>

    /** Builds the launch intent from [request]. Does not touch the network or disk. */
    fun launchIntent(request: LaunchRequest): PlayerIntent
}

/**
 * A save slot and the writable location the player has already resolved.
 * [locationUri] is null when the plugin does not yet have a place to write.
 * The plugin owns what the URI means. The shell does not invent save paths.
 */
data class SaveDeclaration(
    val slot: String,
    val locationUri: String?,
)

/** What the shell already knows when the player builds an intent. */
data class LaunchRequest(
    val game: Game,
    val local: LocalCopy,
    val resolvedPackage: String,
    val integerAppId: Int? = null,
    val textAppId: String? = null,
    val hostUuid: String? = null,
)

/**
 * Intent data the shell turns into a start.
 * When [grantReadUri] is true and [dataUri] is set, the shell grants read on that URI.
 */
data class PlayerIntent(
    val packageName: String,
    val componentClass: String,
    val action: String,
    val dataUri: String? = null,
    val extras: List<PlayerExtra> = emptyList(),
    val grantReadUri: Boolean = false,
)

sealed interface PlayerExtra {
    val key: String

    data class Text(override val key: String, val value: String) : PlayerExtra
    data class Integer(override val key: String, val value: Int) : PlayerExtra
    data class BooleanFlag(override val key: String, val value: Boolean) : PlayerExtra
}
