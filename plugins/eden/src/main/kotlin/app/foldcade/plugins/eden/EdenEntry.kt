package app.foldcade.plugins.eden

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
import app.foldcade.api.plugin.SaveLocationProbe
import app.foldcade.api.plugin.StartDisplay

/** Nintendo Switch player. This bundled entry is licensed under MIT. */
class EdenEntry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR
    val player: EdenPlayer = EdenPlayer()
    override val players: List<Player> = listOf(player)
}

/** Eden's application ids, current build then the legacy one. The shell shares this list. */
val EDEN_PACKAGES: List<String> = listOf("dev.eden.eden_emulator", "dev.legacy.eden_emulator")

/**
 * Eden's current profile's account saves. The shell binds Eden's existing data
 * folder grant, shared with its keys and firmware. A zip holds title folders,
 * so another device places it under its own profile. Device saves and BCAT stay.
 * A fresh Eden can start before it has made a profile.
 */
class EdenPlayer : Player, SaveFolderHolder, SaveLocationProbe {
    override val id: String = "eden"
    override val displayName: String = "Eden"
    override val platformId: String = "nintendo-switch"
    override val packageNames: List<String> = EDEN_PACKAGES
    override val needsLocalFile: Boolean = true
    override val startDisplay: StartDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays: Boolean = false
    override val requiresImportedGame: Boolean = false
    override val suggestedSaveFolder: String = "content://${EDEN_PACKAGES.first()}.user/root/root"

    // DirectoryInitialization.initializeInternalStorage uses getExternalFilesDir(null).
    // path_util.cpp roots config, keys and nand below that directory:
    // https://git.eden-emu.dev/eden-emu/eden/src/commit/5df599128d6828fa0e42968909da848c255d22b1/src/common/fs/path_util.cpp
    override val saveFolderPlaces: List<String> = EDEN_PACKAGES.map { "Android/data/$it/files" }
    override val saveFolderMarkers: Set<String> = setOf("config", "keys", "nand")

    @Volatile
    private var saveFolder: String? = null

    override fun bindSaveFolder(contentUri: String) {
        saveFolder = contentUri.takeIf { it.startsWith("content:") }
    }

    override fun hasSaveFolder(): Boolean = saveFolder != null

    override fun saveDeclarations(game: Game): List<SaveDeclaration> =
        saveFolder?.let { listOf(SaveDeclaration(SLOT, it)) }.orEmpty()

    override val locationSteps: String = "1. Settings › Advanced settings › Custom Paths › Save Data Directory.\n" +
        "2. Settings › Profile Manager: check the user you play as."

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage !in EDEN_PACKAGES) throw PluginException.NotFound("Eden is not installed.")
        val uri = (request.target as? LaunchTarget.ContentUri)?.uri
        if (uri == null || !uri.startsWith("content:")) throw PluginException.NotFound("Eden needs a content URI.")
        return PlayerIntent(
            packageName = request.resolvedPackage,
            componentClass = EMULATION_ACTIVITY,
            action = "android.nfc.action.TECH_DISCOVERED",
            dataUri = uri,
            grantReadUri = true,
            flags = setOf(LaunchFlag.NewTask),
        )
    }

    companion object {
        const val EMULATION_ACTIVITY: String = "org.yuzu.yuzu_emu.activities.EmulationActivity"
        const val SLOT: String = "savedata"
    }
}
