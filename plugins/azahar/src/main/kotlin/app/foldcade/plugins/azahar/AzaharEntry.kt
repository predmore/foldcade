package app.foldcade.plugins.azahar

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.StartDisplay

/**
 * Nintendo 3DS platform and the Azahar player.
 * The local-folder catalog classifies the same id. The host keeps one copy
 * when both declarations match. This entry does not scan a folder.
 *
 * A plugin Foldcade loads in-process is part of the GPLv3 work when distributed.
 */
class AzaharEntry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR

    val player: AzaharPlayer = AzaharPlayer()

    override val platforms: List<Platform> = listOf(Nintendo3dsPlatform)

    override val players: List<Player> = listOf(player)
}

object Nintendo3dsPlatform : Platform {
    override val id: String = Nintendo3ds.ID
    override val displayName: String = Nintendo3ds.DISPLAY_NAME
    override val extensions: Set<String> = Nintendo3ds.extensions
    override val aliases: Set<String> = Nintendo3ds.aliases
}

/**
 * File types Azahar can open. Decrypted `.cci` and `.zcci` are preferred.
 * `.3ds` is accepted as best-effort. Nothing here rewrites a file.
 */
object Nintendo3ds {
    const val ID: String = "nintendo-3ds"
    const val DISPLAY_NAME: String = "Nintendo 3DS"
    val aliases: Set<String> = setOf("3ds", "n3ds", "new-nintendo-3ds")
    val extensions: Set<String> = setOf("cci", "zcci", "3ds")
    val preferredExtensions: Set<String> = setOf("cci", "zcci")
    val bestEffortExtensions: Set<String> = setOf("3ds")

    fun acceptance(extension: String): ExtensionAcceptance {
        val normalized = extension.trim().removePrefix(".").lowercase()
        return when (normalized) {
            in preferredExtensions -> ExtensionAcceptance.Preferred
            in bestEffortExtensions -> ExtensionAcceptance.BestEffort
            else -> ExtensionAcceptance.Rejected
        }
    }
}

enum class ExtensionAcceptance {
    Preferred,
    BestEffort,
    Rejected,
}

/**
 * Azahar on Android. Vanilla and Play are both official package ids.
 * The intent is `ACTION_VIEW` with the game content URI. There is no path extra.
 *
 * Saves are one slot per title. The location is a content URI the user already
 * picked. This type does not invent a directory and does not read private storage.
 */
class AzaharPlayer : Player, SaveFolderHolder {
    override val id: String = ID
    override val displayName: String = DISPLAY_NAME
    override val platformId: String = Nintendo3ds.ID
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

    override fun saveDeclarations(game: Game): List<SaveDeclaration> {
        val location = saveLocation(saveFolder)
        return listOf(SaveDeclaration(slot = location.slot, locationUri = location.locationUri))
    }

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage !in PACKAGES) {
            throw PluginException.NotFound("Azahar is not installed.")
        }
        val target = request.target as? LaunchTarget.ContentUri
        val uri = target?.uri
        if (uri == null || !uri.startsWith("content:")) {
            throw PluginException.NotFound("Azahar needs a content URI.")
        }
        return PlayerIntent(
            packageName = request.resolvedPackage,
            componentClass = ACTIVITY,
            action = ACTION_VIEW,
            dataUri = uri,
            grantReadUri = true,
            flags = setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask),
        )
    }

    companion object {
        const val ID: String = "azahar"
        const val DISPLAY_NAME: String = "Azahar"
        const val ACTIVITY: String = "org.citra.citra_emu.activities.EmulationActivity"
        const val ACTION_VIEW: String = "android.intent.action.VIEW"
        const val TITLE_SLOT: String = "title"
        val PACKAGES: List<String> = listOf(
            "org.azahar_emu.azahar",
            "io.github.lime3ds.android",
        )
    }
}

/**
 * Where the title save lives, if the user has already pointed at that folder.
 * A content URI is kept as given. Any other string is not a location.
 */
fun saveLocation(folderUri: String?): SaveLocation {
    val uri = folderUri?.trim()?.takeIf { it.startsWith("content:") }
    return SaveLocation(
        slot = AzaharPlayer.TITLE_SLOT,
        locationUri = uri,
        askForFolder = uri == null,
    )
}

data class SaveLocation(
    val slot: String,
    val locationUri: String?,
    val askForFolder: Boolean,
)
