// SPDX-License-Identifier: MIT

package app.foldcade.api.plugin

/**
 * Where a player should be started.
 * A display-role setting can swap which panel is primary. Display ids are not stored here.
 * Open. A plugin that branches on this must use an `else` branch.
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
 *
 * [needsLocalFile] is the whole handoff flag. True means the player reads a
 * [LaunchTarget.ContentUri], or a [LaunchTarget.SharedFile] when [opensByPath]
 * is also true. False means it reads a [LaunchTarget.AppRef].
 */
interface Player {
    val id: String
    val displayName: String
    val platformId: String

    /** Package ids this player can bind. Detection is a list, not one constant. */
    val packageNames: List<String>

    val needsLocalFile: Boolean
    val startDisplay: StartDisplay
    val occupiesBothDisplays: Boolean

    /** True when the game must already be imported inside the player. */
    val requiresImportedGame: Boolean

    /**
     * True when the player opens a game file by its path in shared storage, not
     * through a content URI. The shell then hands over a [LaunchTarget.SharedFile],
     * or does not start the player when the file has no such path. Added in API minor 9.
     */
    val opensByPath: Boolean
        get() = false

    /** Save slots this player already knows how to read and write for [game]. */
    fun saveDeclarations(game: Game): List<SaveDeclaration>

    /**
     * The file this player keeps [slot] in, inside a folder [SaveDeclaration.locationUri],
     * when the game file it opens is named [romFileName]. The shell reads and writes
     * that one file there and nothing else. Null means the player does not keep the
     * slot as one file with a name the shell can know, so the shell does not place
     * or read it. Added in API minor 3.
     *
     * The name may hold folders, separated by `/`, below that folder. The shell
     * creates a missing folder when it places a save. Added in API minor 9.
     */
    fun saveFileName(slot: String, romFileName: String): String? = null

    /** Builds the launch intent from [request]. Does not touch the network or disk. */
    fun launchIntent(request: LaunchRequest): PlayerIntent
}

/**
 * A save slot and the writable location the player has already resolved.
 * [slot] is the name that matches [SaveSlot.slot], [PlacedSave.slot], and [ObservedSlot.slot].
 * The player plugin owns this location. The bytes the player reads and writes live at [locationUri].
 * The backend owns its copy of the same [slot].
 * [locationUri] is null when the plugin does not yet have a place to write.
 * The plugin owns what the URI means. The shell does not invent save paths.
 */
data class SaveDeclaration(
    val slot: String,
    val locationUri: String?,
)

/**
 * What the shell already knows when the player builds an intent.
 * The shell builds [LaunchRequest] from [Placement.target].
 */
data class LaunchRequest(
    val game: Game,
    val target: LaunchTarget,
    val resolvedPackage: String,
)

/**
 * Flags the shell applies when it starts [PlayerIntent].
 * Open. A plugin that branches on this must use an `else` branch.
 */
enum class LaunchFlag {
    GrantWriteUri,
    NewTask,

    /** Clears the activity on top. Used when Azahar is relaunched. */
    ClearTop,

    /** Clears the task. Used when Azahar is relaunched. */
    ClearTask,

    /**
     * The player quits, rather than switch games, when it is handed a different
     * game while it runs, and expects to be started again. When the last intent
     * the shell started for that package was a different one, and the player
     * quits within moments of this start, the shell starts this intent again.
     * The same intent again resumes the game that is running. Added in API minor 9.
     */
    ReplaceRunning,
}

/**
 * Intent data the shell turns into a start.
 * When [grantReadUri] is true and [dataUri] is set, the shell grants read on that URI.
 * [mimeType] is set only when the player already knows the MIME type.
 * [flags] are extra start flags. They do not replace [grantReadUri].
 */
data class PlayerIntent(
    val packageName: String,
    val componentClass: String,
    val action: String,
    val dataUri: String? = null,
    val extras: List<PlayerExtra> = emptyList(),
    val grantReadUri: Boolean = false,
    val mimeType: String? = null,
    val flags: Set<LaunchFlag> = emptySet(),
)

/** Open. A plugin that branches on this must use an `else` branch. */
sealed interface PlayerExtra {
    val key: String

    data class Text(override val key: String, val value: String) : PlayerExtra
    data class Integer(override val key: String, val value: Int) : PlayerExtra
    data class LongValue(override val key: String, val value: Long) : PlayerExtra
    data class BooleanFlag(override val key: String, val value: Boolean) : PlayerExtra
}
