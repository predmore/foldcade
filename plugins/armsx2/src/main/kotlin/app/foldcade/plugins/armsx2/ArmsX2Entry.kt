package app.foldcade.plugins.armsx2

import app.foldcade.api.plugin.FirmwareOffer
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SaveFolderSearch
import app.foldcade.api.plugin.SaveSyncState
import app.foldcade.api.plugin.StartDisplay
import java.util.Locale

/**
 * PlayStation 2 player for ARMSX2. The platform id is the local-folder
 * catalog's `playstation-2`. This entry does not declare that platform again,
 * and does not scan a folder.
 *
 * NetherSX2 stays in the emulators table. The shell ranks this player ahead of
 * that table, so both can be installed and ARMSX2 still launches. That rank is
 * not RetroArch's `aheadOfStandalone`: that flag only moves RetroArch, and the
 * PlayStation 2 has no RetroArch row.
 *
 * This bundled entry is licensed under MIT.
 */
class ArmsX2Entry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR

    val player: ArmsX2Player = ArmsX2Player()

    override val players: List<Player> = listOf(player)
}

/**
 * ARMSX2 (read from 2.8.4).
 *
 * The launch starts `MainActivity` with action `VIEW` and the game as the intent
 * data, which is what its exported alias reads. GitHub stable is `com.armsx2`,
 * the Play Store build is `come.nanodata.armsx2`, and nightly is
 * `com.armsx2.nightly`, in that order. The same activity class is used for each.
 * Whether the Play Store and nightly builds still export that alias is for a
 * device to confirm.
 *
 * [LaunchFlag.NewTask], [LaunchFlag.ClearTop], and [LaunchFlag.ClearTask] replace
 * a game that is already running, as DuckStation does. Whether that also drops
 * ARMSX2's first-run setup is for a device to confirm.
 *
 * ARMSX2 keeps `bios` and `memcards` in `Android/data/<package>/files`. The folder
 * the user picks is that data folder, where the picker opens, see
 * [suggestedSaveFolder]. A BIOS a library has goes in `bios`, see [firmwareFileName].
 * ARMSX2 finds a BIOS there by its bytes, under any file name. One console BIOS
 * is chosen out of a zip of dumps, see [chooseFirmware]. A file the user put in
 * `bios` stays.
 *
 * Slot 1's card is the save. The card is the one `armsx2-settings.json` names
 * for that game, and the ini only when that mirror is missing. Each game's
 * folders on a folder card sync as one zip, found from the serial in the game
 * file. See [findSaveFolders]. An empty 8 MB file card becomes a folder card;
 * any other file card, such as one that holds saves, is left as it is.
 *
 * ARMSX2 draws on one screen. Its second-screen panel is off unless the user
 * turns it on. The game opens on the screen the picker targets.
 */
class ArmsX2Player : Player, SaveFolderHolder {
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

    /** The GitHub build's data folder. Android lets the picker choose it on the Thor. */
    override val suggestedSaveFolder: String = DATA_FOLDER

    /** The data folder holds these, and so does a folder they were moved to. */
    override val saveFolderMarkers: Set<String> = setOf(MEMCARDS, BIOS)

    /** Slot 1, once the data folder is picked. The card itself is chosen in [findSaveFolders]. */
    override fun saveDeclarations(game: Game): List<SaveDeclaration> =
        saveFolder?.let { listOf(SaveDeclaration(Ps2Card.SLOT, it)) }.orEmpty()

    override fun findSaveFolders(
        slot: String,
        game: GameFileReader,
        folder: SaveFolderAccess,
        forLaunch: Boolean,
    ): SaveFolderSearch? = Ps2Card.find(slot, game, folder, forLaunch)

    override fun saveSyncState(folder: SaveFolderAccess): SaveSyncState? = Ps2Card.state(folder)

    /**
     * `bios/<file>`. A plain file keeps its name. A file inside a zip,
     * `<zip>/<file>`, is written under the file's own name: ARMSX2 does not
     * care what the file is called. An unsafe name is refused.
     */
    override fun firmwareFileName(name: String): String? {
        val file = firmwareLeaf(name) ?: return null
        return "$BIOS/$file"
    }

    /**
     * Enough of each image to reach `ROMVER`. Retail dumps keep that file a
     * short way in, after the `RESET` module. The probe list for this library's
     * zip records each image's `ROMVER` text and not the offset it was found at.
     */
    override val firmwareHeaderSize: Int = BIOS_HEADER

    override fun chooseFirmware(offered: List<FirmwareOffer>): List<FirmwareOffer> =
        choosePs2Bios(offered, Locale.getDefault().country)

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage !in PACKAGES) {
            throw PluginException.NotFound("$DISPLAY_NAME is not installed.")
        }
        val uri = (request.target as? LaunchTarget.ContentUri)?.uri
        if (uri == null || !uri.startsWith("content:")) {
            throw PluginException.NotFound("$DISPLAY_NAME needs a content URI.")
        }
        return PlayerIntent(
            packageName = request.resolvedPackage,
            componentClass = MAIN_ACTIVITY,
            action = ACTION_VIEW,
            dataUri = uri,
            grantReadUri = true,
            // Replaces a game that is already running. Device QA confirms first-run setup survives this.
            flags = setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask),
        )
    }

    companion object {
        const val ID: String = "armsx2"
        const val DISPLAY_NAME: String = "ARMSX2"
        const val PLATFORM_ID: String = "playstation-2"

        /** GitHub stable, then the Play Store build, then nightly. */
        val PACKAGES: List<String> = listOf(
            "com.armsx2",
            "come.nanodata.armsx2",
            "com.armsx2.nightly",
        )

        const val MAIN_ACTIVITY: String = "com.armsx2.MainActivity"
        const val ACTION_VIEW: String = "android.intent.action.VIEW"

        /**
         * The GitHub build's data folder in shared storage, as a document Android's
         * picker opens at. Nightly and the Play Store build keep their own folders.
         */
        const val DATA_FOLDER: String =
            "content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata%2Fcom.armsx2%2Ffiles"

        const val MEMCARDS: String = "memcards"
        const val BIOS: String = "bios"

        /**
         * Bytes read from the start of a BIOS image to find `ROMVER`. From version 2.20 on,
         * `ROMVER` sits at 0x7ff00. 0x81000 is 516 KiB, a little margin past 512 KiB.
         */
        const val BIOS_HEADER: Int = 0x81000
    }
}

/** The file's own name, or null when [name] would not stay one file under `bios`. */
internal fun firmwareLeaf(name: String): String? {
    if (name.isBlank() || name.any { it == '\\' || it.code <= 31 }) return null
    val parts = name.split('/')
    if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
    return parts.last()
}
