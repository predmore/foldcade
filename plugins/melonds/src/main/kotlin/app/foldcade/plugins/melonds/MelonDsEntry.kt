package app.foldcade.plugins.melonds

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
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.StartDisplay

/**
 * Nintendo DS player for upstream melonDS Android.
 * The platform id is the local-folder catalog's `nintendo-ds`. This entry does
 * not declare that platform again. On this host a second declaration is rejected
 * and the player would be dropped with it. This entry does not scan a folder.
 *
 * A plugin Foldcade loads in-process is part of the GPLv3 work when distributed.
 */
class MelonDsEntry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR

    val player: MelonDsPlayer = MelonDsPlayer()

    override val players: List<Player> = listOf(player)
}

/**
 * Upstream melonDS Android, 2.0.0 or newer.
 *
 * The launch is `ACTION_VIEW` with the game content URI and a read grant.
 * The activity reads `intent.data` before the deprecated `uri` and `PATH`
 * extras. Those extras are not set. Both application ids use
 * [EMULATOR_ACTIVITY]. The namespace stays `me.magnum.melonds`. The nightly
 * flavor only adds an application id suffix. The issue lists the stable id
 * first. That is the package used when both are installed.
 *
 * melonDS can place a save next to the ROM only after it has scanned that ROM,
 * and only while "Save next to ROM file" is on. With no save directory set, it
 * writes under its own `Android/data` directory. Foldcade does not read that
 * directory and does not request all-files access. The location declared here
 * is a content tree the user already picked. Point melonDS's save directory at
 * that same folder. A missing folder does not stop launch.
 *
 * The DS top screen belongs on the primary display. The touch screen belongs
 * on the other display. That layout is a melonDS setting. This intent does not
 * invent a layout extra. Whether the touch screen is the Thor's bottom panel
 * is a device check, not this plugin.
 */
class MelonDsPlayer : Player, SaveFolderHolder {
    override val id: String = ID
    override val displayName: String = DISPLAY_NAME
    override val platformId: String = PLATFORM_ID
    override val packageNames: List<String> = PACKAGES
    override val needsLocalFile: Boolean = true
    override val startDisplay: StartDisplay = StartDisplay.Primary
    override val occupiesBothDisplays: Boolean = true
    override val requiresImportedGame: Boolean = false

    @Volatile
    private var saveFolder: String? = null

    override fun bindSaveFolder(contentUri: String) {
        saveFolder = contentUri.takeIf { it.startsWith("content:") }
    }

    override fun hasSaveFolder(): Boolean = saveFolder != null

    override fun saveDeclarations(game: Game): List<SaveDeclaration> =
        listOf(SaveDeclaration(slot = SAVE_SLOT, locationUri = saveFolder))

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage !in PACKAGES) {
            throw PluginException.NotFound("melonDS is not installed.")
        }
        val target = request.target as? LaunchTarget.ContentUri
        val uri = target?.uri
        if (uri == null || !uri.startsWith("content:")) {
            throw PluginException.NotFound("melonDS needs a content URI.")
        }
        return PlayerIntent(
            packageName = request.resolvedPackage,
            componentClass = EMULATOR_ACTIVITY,
            action = ACTION_VIEW,
            dataUri = uri,
            grantReadUri = true,
            flags = setOf(LaunchFlag.NewTask),
        )
    }

    companion object {
        const val ID: String = "melonds"
        const val DISPLAY_NAME: String = "melonDS"
        const val PLATFORM_ID: String = "nintendo-ds"
        const val ACTION_VIEW: String = "android.intent.action.VIEW"
        const val SAVE_SLOT: String = "sav"
        const val STABLE_PACKAGE: String = "me.magnum.melonds"
        const val NIGHTLY_PACKAGE: String = "me.magnum.melonds.nightly"
        val PACKAGES: List<String> = listOf(STABLE_PACKAGE, NIGHTLY_PACKAGE)
    }
}

/**
 * Absolute activity name. The manifest declares `.ui.emulator.EmulatorActivity`
 * under namespace `me.magnum.melonds`, so the class does not follow the nightly
 * application id.
 */
const val EMULATOR_ACTIVITY: String = "me.magnum.melonds.ui.emulator.EmulatorActivity"
