package app.foldcade.plugins.aps3e

import app.foldcade.api.plugin.FirmwareOffer
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SaveFolderSearch
import app.foldcade.api.plugin.StartDisplay

/**
 * PlayStation 3 player for aPS3e. The platform id is the local-folder
 * catalog's `playstation-3`. This entry does not declare that platform again,
 * and does not scan a folder.
 *
 * This bundled entry is licensed under MIT.
 */
class Aps3eEntry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR

    val player: Aps3ePlayer = Aps3ePlayer()

    override val players: List<Player> = listOf(player)
}

/**
 * aPS3e (read from 2.43).
 *
 * The launch starts `EmulatorActivity` with action `aenu.intent.action.APS3E`
 * and the game's content URI as the string extra `iso_uri`, and as the intent
 * data so the read grant is on the clip. GitHub and the free Play build are
 * `aenu.aps3e`. The premium Play build is `aenu.aps3e.premium`, listed second;
 * that id is not confirmed on a device. The activity is the same class for each.
 * [LaunchFlag.NewTask] and [LaunchFlag.ClearTask] replace a game that is already
 * running. [LaunchFlag.ClearTop] is sent with them. `NO_HISTORY` is not: it
 * finishes the game when the user presses Home.
 *
 * aPS3e reads a decrypted `.iso` only. An encrypted disc image and a folder
 * game do not open this way. The file name, after the URI is decoded, has to
 * end in `.iso`.
 *
 * It keeps its files under `Android/data/aenu.aps3e/files/aps3e`. Another app
 * cannot write the native save files through the external-storage tree, so the
 * folder the user picks is aPS3e's own documents provider, where the picker
 * opens, see [suggestedSaveFolder]. The tree holds `config`.
 *
 * Firmware is a `PS3UPDAT.PUP`. aPS3e decrypts it itself; Foldcade only stages
 * the file at `firmware/PS3UPDAT.PUP`, where aPS3e's installer can see it.
 * One PUP is chosen by the version in its header, see [chooseFirmware]. A file
 * the user put there stays. Once `config/dev_flash/.installed` exists, the PUP
 * is not written again.
 *
 * aPS3e is not started until its first-run wizard has written
 * `config/default_config.yml` and it has installed firmware. Launching before
 * either leaves a black screen. [setupSteps] says what is left.
 *
 * Saves are the folders under `config/dev_hdd0/home/00000001/savedata` whose
 * names contain the game's title id, read from `PS3_GAME/PARAM.SFO`. aPS3e
 * creates the folder. Trophies are not part of that slot.
 *
 * aPS3e draws on one screen. The game opens on the screen the picker targets.
 */
class Aps3ePlayer : Player, SaveFolderHolder {
    override val id: String = ID
    override val displayName: String = DISPLAY_NAME
    override val platformId: String = PLATFORM_ID
    override val packageNames: List<String> = PACKAGES
    override val needsLocalFile: Boolean = true
    override val startDisplay: StartDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays: Boolean = false
    override val requiresImportedGame: Boolean = false

    @Volatile
    private var saveFolder: String? = null

    override fun bindSaveFolder(contentUri: String) {
        saveFolder = contentUri.takeIf { it.startsWith("content:") }
    }

    override fun hasSaveFolder(): Boolean = saveFolder != null

    /** aPS3e's own folder, as a document the picker opens at. */
    override val suggestedSaveFolder: String = DATA_FOLDER

    /** The data folder holds this, and so does a folder it was moved to. */
    override val saveFolderMarkers: Set<String> = setOf(CONFIG)

    /** The game's save folders, once the data folder is picked. Found in [findSaveFolders]. */
    override fun saveDeclarations(game: Game): List<SaveDeclaration> =
        saveFolder?.let { listOf(SaveDeclaration(SLOT, it)) }.orEmpty()

    override fun findSaveFolders(
        slot: String,
        game: GameFileReader,
        folder: SaveFolderAccess,
        forLaunch: Boolean,
    ): SaveFolderSearch? {
        if (slot != SLOT) return null
        val titleId = ps3TitleId(game) ?: return null
        return SaveFolderSearch(SAVEDATA, titleId)
    }

    /** Every `.pup` is the one firmware file, under its own name there. */
    override fun firmwareFileName(name: String): String? {
        if (!plainPupName(name)) return null
        return FIRMWARE_FILE
    }

    /** Enough of a PUP to hold its header and a version.txt that sits in it. */
    override val firmwareHeaderSize: Int = PUP_HEADER

    override fun chooseFirmware(offered: List<FirmwareOffer>): List<FirmwareOffer> = choosePs3Pup(offered)

    override fun needsFirmware(folder: SaveFolderAccess): Boolean = !folderHas(folder, INSTALLED)

    override fun setupSteps(folder: SaveFolderAccess): String? {
        val wizard = folderHas(folder, WIZARD_DONE)
        val installed = folderHas(folder, INSTALLED)
        if (wizard && installed) return null
        val staged = folderHas(folder, FIRMWARE_FILE)
        return when {
            !staged -> NEEDS_FIRMWARE
            !wizard -> FINISH_SETUP
            else -> INSTALL_FIRMWARE
        }
    }

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage !in PACKAGES) {
            throw PluginException.NotFound("$DISPLAY_NAME is not installed.")
        }
        val uri = (request.target as? LaunchTarget.ContentUri)?.uri
        if (uri == null || !uri.startsWith("content:")) {
            throw PluginException.NotFound("$DISPLAY_NAME needs a content URI.")
        }
        if (!isoFileName(uri)) {
            throw PluginException.NotFound("$DISPLAY_NAME opens PlayStation 3 games as decrypted .iso files.")
        }
        return PlayerIntent(
            packageName = request.resolvedPackage,
            componentClass = EMULATOR_ACTIVITY,
            action = ACTION,
            dataUri = uri,
            extras = listOf(PlayerExtra.Text("iso_uri", uri)),
            grantReadUri = true,
            flags = setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask),
        )
    }

    companion object {
        const val ID: String = "aps3e"
        const val DISPLAY_NAME: String = "aPS3e"
        const val PLATFORM_ID: String = "playstation-3"

        /** The free build, then the premium Play build. */
        val PACKAGES: List<String> = listOf(
            "aenu.aps3e",
            "aenu.aps3e.premium",
        )

        const val EMULATOR_ACTIVITY: String = "aenu.aps3e.EmulatorActivity"
        const val ACTION: String = "aenu.intent.action.APS3E"

        /**
         * aPS3e's documents provider, opened at its data folder. The granted tree
         * is that folder. The external-storage `Android/data` tree cannot write
         * the saves aPS3e creates.
         */
        const val DATA_FOLDER: String = "content://aenu.aps3e.provider/root/root"

        const val CONFIG: String = "config"
        const val SLOT: String = "savedata"
        const val SAVEDATA: String = "config/dev_hdd0/home/00000001/savedata"
        const val FIRMWARE_FILE: String = "firmware/PS3UPDAT.PUP"
        const val INSTALLED: String = "config/dev_flash/.installed"
        const val WIZARD_DONE: String = "config/default_config.yml"

        /** Bytes read from the start of a PUP. The header and a short version.txt fit in this. */
        const val PUP_HEADER: Int = 0x1000

        const val NEEDS_FIRMWARE: String =
            "aPS3e needs PlayStation 3 firmware. Add PS3UPDAT.PUP to the PlayStation 3 firmware on your RomM server, then start the game again."
        const val FINISH_SETUP: String =
            "Open aPS3e and finish its setup. When it asks for firmware, choose aPS3e › firmware › PS3UPDAT.PUP. Foldcade put it there from RomM. Then start the game again."
        const val INSTALL_FIRMWARE: String =
            "In aPS3e, open the menu and choose Install Firmware, then aPS3e › firmware › PS3UPDAT.PUP. Foldcade put it there from RomM. Then start the game again."
    }
}

/** True when [folder] lists the file at [path]. A missing folder is not that file. */
private fun folderHas(folder: SaveFolderAccess, path: String): Boolean {
    val parent = path.substringBeforeLast('/', "")
    val name = path.substringAfterLast('/')
    return folder.list(parent)?.any { it.name == name && !it.isFolder } == true
}

/** A single `.pup` file name, not a path and not `.` or `..`. */
internal fun plainPupName(name: String): Boolean {
    if (name.isBlank() || name == "." || name == "..") return false
    if ('/' in name || '\\' in name) return false
    return name.endsWith(".pup", ignoreCase = true)
}

/**
 * True when the file [uri] names, after percent-decoding its last segment,
 * ends in `.iso`. A decoded segment may itself hold folders.
 */
internal fun isoFileName(uri: String): Boolean {
    val segment = uri.substringBefore('?').substringBefore('#').substringAfterLast('/')
    val decoded = percentDecode(segment)
    val name = decoded.substringAfterLast('/').substringAfterLast(':')
    return name.endsWith(".iso", ignoreCase = true)
}

private fun percentDecode(text: String): String = buildString {
    var index = 0
    while (index < text.length) {
        val byte = if (text[index] == '%' && index + 2 < text.length) {
            text.substring(index + 1, index + 3).toIntOrNull(16)
        } else {
            null
        }
        if (byte != null) {
            append(byte.toChar())
            index += 3
        } else {
            append(text[index])
            index += 1
        }
    }
}
