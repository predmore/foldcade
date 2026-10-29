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
     * Whether this player supports save sync, even when it has no reachable slot
     * for [game] yet. Folder holders support it before setup. A player with no
     * save support leaves this false. Added in API minor 15.
     */
    fun supportsSaveSync(game: Game): Boolean =
        this is SaveFolderHolder || this is HostedSaveFolder || saveDeclarations(game).isNotEmpty()

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

    /**
     * How many bytes from the start of the game file the shell reads and passes
     * to [saveFolders]. Zero reads none. Added in API minor 11.
     */
    val romHeaderSize: Int
        get() = 0

    /**
     * The folders this player keeps [slot] in, below the folder
     * [SaveDeclaration.locationUri], for a game file named [romFileName] that
     * starts with [romHeader]: [romHeaderSize] bytes, or fewer for a shorter file.
     * The shell moves them together as one zip, with the files below each
     * [SaveFolderPart.path] under [SaveFolderPart.name], and reads and writes
     * nothing else. Placing a zip makes each folder it holds match it, and leaves
     * a folder it does not hold as it is. Null means the slot is not kept as
     * folders. The shell asks for folders before [saveFileName]. Added in API minor 11.
     */
    fun saveFolders(slot: String, romFileName: String, romHeader: ByteArray): List<SaveFolderPart>? = null

    /**
     * Where this player looks for the firmware file [name], such as a console's
     * BIOS, below its save folder, with folders separated by `/`: the folder the
     * user picked for it, see [SaveFolderHolder], or else the one its save
     * declarations name, unless Foldcade hosts that one, see [HostedSaveFolder].
     * A library that has [name] for the game's platform downloads it and
     * returns it in [LaunchTarget.ContentUri.firmware], and the shell writes it
     * there. A file already there is replaced only when it still holds what the
     * shell last wrote, so a file the user put there stays. Null means the player
     * does not use [name]. Added in API minor 12.
     *
     * [name] may be `<zip name>/<file name>`: a file inside a zip the library
     * keeps for the platform, the file's own name after the zip's, whatever
     * folder the zip holds it in. A library opens a zip only when the player
     * wants the zip's own name too. A player that wants files from a zip should
     * name the zip as well and leave it out in [chooseFirmware]. A player that
     * does not want files from a zip returns null for a name that holds `/`.
     * Added in API minor 13.
     */
    fun firmwareFileName(name: String): String? = null

    /**
     * How many bytes from the start of each firmware file the shell reads for
     * [chooseFirmware]. Zero reads none. Added in API minor 13.
     */
    val firmwareHeaderSize: Int
        get() = 0

    /**
     * The firmware to write, out of [offered]: each file the library has for the
     * game's platform that [firmwareFileName] names. Default: all of them.
     * A player that wants one of several, such as one console BIOS out of a set
     * of dumps, picks it by its bytes. Added in API minor 13.
     */
    fun chooseFirmware(offered: List<FirmwareOffer>): List<FirmwareOffer> = offered

    /**
     * Folders this player keeps [slot] in, found by name among other games' in one
     * folder, such as a game's saves on a PS2 folder memory card. The shell moves
     * the matching folders as one zip, each under its own name, with the rules of
     * [saveFolders]: placing makes each folder the zip holds match it and leaves the
     * rest alone. [game] reads the game file. [folder] reads the save folder; the
     * player may also set it up here, before a launch only ([forLaunch] true), as
     * when the folder it needs is missing, and must leave anything holding saves
     * as it is. Null means the slot is not kept this way, or cannot be now.
     * A player that has started that setup and cannot finish throws. On a launch
     * the shell treats the throw as a save that could not be placed, and the game
     * does not start. On the way back the shell only reads, and a throw means no spot.
     * Asked before [saveFolders]. Added in API minor 13.
     */
    fun findSaveFolders(
        slot: String,
        game: GameFileReader,
        folder: SaveFolderAccess,
        forLaunch: Boolean,
    ): SaveFolderSearch? = null

    /**
     * Whether the player's saves sync now, for Settings. [folder] is read only
     * there: [SaveFolderAccess.write], [SaveFolderAccess.createFolder], and
     * [SaveFolderAccess.moveToBackup] return false. Null: the player does not say.
     * Added in API minor 13.
     */
    fun saveSyncState(folder: SaveFolderAccess): SaveSyncState? = null

    /**
     * What the user does once in the player before it can start any game, such as
     * finishing its first-run setup and installing the console's firmware, or null
     * when it can start. [folder] is the folder the user picked for it, read only,
     * or null when no folder is picked: a player that checks its setup through
     * that folder then says how to pick it. The shell asks before every start of
     * a [SaveFolderHolder], after it has placed saves and firmware, and does not
     * start the player while this says what to do: it shows the steps, with Open
     * for the player. Settings shows them too, once a folder is picked.
     * Added in API minor 14.
     */
    fun setupSteps(folder: SaveFolderAccess?): String? = null

    /**
     * False when the player no longer needs the firmware its library has, such as a
     * console firmware it has already installed from that file, so the shell does
     * not write it again. [folder] is read only. Added in API minor 14.
     */
    fun needsFirmware(folder: SaveFolderAccess): Boolean = true

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
 * One folder of a save kept as folders. [path] is below the player's save
 * folder, with folders separated by `/`. [name] is the folder the zip keeps
 * its files under, one name with no `/`. Added in API minor 11.
 */
data class SaveFolderPart(
    val name: String,
    val path: String,
)

/**
 * A firmware file the shell can write for a player, with its first
 * [Player.firmwareHeaderSize] bytes. [header] is compared by its bytes.
 * Added in API minor 13.
 */
data class FirmwareOffer(
    val name: String,
    val header: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is FirmwareOffer && name == other.name && header.contentEquals(other.header)

    override fun hashCode(): Int = 31 * name.hashCode() + header.contentHashCode()
}

/**
 * The game file, read at any offset, for a player that finds its saves from what
 * the game holds. Added in API minor 13.
 */
interface GameFileReader {
    /** The game document's display name, when known. Added in API minor 17. */
    val fileName: String?
        get() = null

    /** Up to [length] bytes at [offset], fewer at the end of the file, or null when it cannot be read. */
    fun read(offset: Long, length: Int): ByteArray?

    /**
     * Another file in the same folder as the game file, by its name, such as a cue
     * sheet's track or a playlist's disc. Null when there is none, or the shell
     * cannot reach the folder. A name that contains `/` or `\`, or is `.` or `..`,
     * is refused. Added in API minor 13.
     */
    fun sibling(name: String): GameFileReader? = null
}

/**
 * The folder the user picked for a player, read and written by paths below it,
 * folders separated by `/`. Added in API minor 13.
 */
interface SaveFolderAccess {
    /** What the folder at [path] holds, or null when it is missing or cannot be listed. "" is the folder itself. */
    fun list(path: String): List<SaveFolderEntry>?

    /** The file at [path], or null when it is missing, unreadable, or larger than [limit]. */
    fun read(path: String, limit: Int): ByteArray?

    /**
     * Up to [length] bytes of the file at [path] from [offset], fewer at the end
     * of the file, or null when it is missing or cannot be read.
     */
    fun read(path: String, offset: Long, length: Int): ByteArray?

    /**
     * Writes [bytes] as the file at [path], making missing folders, and reads it back.
     * When the file is already there and its bytes differ, those bytes are copied into
     * Foldcade's save backups first, the same copy [moveToBackup] keeps. False when the
     * backup, the write, or the read-back fails. The file may then hold part of [bytes],
     * and its old bytes are in the backups.
     */
    fun write(path: String, bytes: ByteArray): Boolean

    /** Makes the folder at [path] and missing folders above it. True when it is there. */
    fun createFolder(path: String): Boolean

    /**
     * Moves the file at [path] out of the folder, into Foldcade's save backups for this player.
     * True when it is gone from [path] and kept. Backups keep the newest 20 files per player.
     */
    fun moveToBackup(path: String): Boolean
}

/**
 * One file or folder directly inside a [SaveFolderAccess] path.
 * [size] is the file's length in bytes, or 0 when the provider does not report one.
 * Added in API minor 13.
 */
data class SaveFolderEntry(val name: String, val isFolder: Boolean, val size: Long)

/**
 * Folders found by name: each folder directly below [parent] (a path below the
 * save folder) whose name, upper-cased with `-` and `_` removed, contains
 * [nameContains] upper-cased with `-` and `_` removed. A name that starts with
 * `_` is never a match. PCSX2 keeps a deleted save as `_pcsx2_deleted_<name>`,
 * and a real save folder does not start with `_`. Added in API minor 13.
 */
data class SaveFolderSearch(val parent: String, val nameContains: String)

/**
 * Whether a player's saves sync, for Settings. Open. A plugin that branches on
 * this must use an `else` branch. Added in API minor 13.
 */
sealed interface SaveSyncState {
    /** The saves sync. */
    data object Syncing : SaveSyncState

    /** The user does [steps] once, in the player's own settings, before saves can sync. */
    data class NeedsSetup(val steps: String) : SaveSyncState
}

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

    /**
     * This start sent again, without [ClearTop] and [ClearTask], brings back the
     * game it started while that game runs, and starts the game anew when it has
     * closed. The shell resumes the game that way, not through the player's
     * launcher, which may open the player's own menu in front of a game that is
     * gone without saying so. It does not place the library's saves first, since
     * the game may still be running with them open: a game that had closed loads
     * what the player last saved on this device, and the next read-back hands that
     * to the library as any other change. Added in API minor 14.
     */
    SameStartResumes,
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
