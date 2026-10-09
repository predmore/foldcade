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
 * This bundled entry stays under the GNU GPLv3. An independent plugin that
 * uses only the published API may use any license.
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
 * Azahar on Android.
 *
 * The intent is `ACTION_VIEW` with the game content URI. There is no path extra.
 * [activityClass] picks the component for the installed application id.
 *
 * Saves are one slot per title. The location is a content URI the user already
 * picked. A missing folder does not stop launch. Azahar keeps its own saves.
 * This type does not invent a directory and does not read private storage.
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
        val activity = activityClass(request.resolvedPackage)
            ?: throw PluginException.NotFound("Azahar is not installed.")
        val target = request.target as? LaunchTarget.ContentUri
        val uri = target?.uri
        if (uri == null || !uri.startsWith("content:")) {
            throw PluginException.NotFound("Azahar needs a content URI.")
        }
        return PlayerIntent(
            packageName = request.resolvedPackage,
            componentClass = activity,
            action = ACTION_VIEW,
            dataUri = uri,
            grantReadUri = true,
            mimeType = CONTENT_MIME,
            flags = setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask),
        )
    }

    companion object {
        const val ID: String = "azahar"
        const val DISPLAY_NAME: String = "Azahar"
        const val ACTION_VIEW: String = "android.intent.action.VIEW"
        const val TITLE_SLOT: String = "title"

        /** Matches the VIEW filter on EmulationActivity: content + this MIME type. */
        const val CONTENT_MIME: String = "application/octet-stream"
        val PACKAGES: List<String> = listOf(VANILLA_PACKAGE, PLAY_PACKAGE)
    }
}

/**
 * Component class for an Azahar application id.
 *
 * Read from Azahar `a5c3d4f8eb34493f37479158128de134c3e700a9`:
 * - `src/android/app/build.gradle.kts` sets `namespace = "org.citra.citra_emu"` once.
 *   The default `applicationId` is [VANILLA_PACKAGE]. The `googlePlay` flavor sets
 *   `applicationId = "io.github.lime3ds.android"` so the existing Play listing can
 *   stay. That flavor does not change the namespace.
 * - `src/android/app/src/main/AndroidManifest.xml` declares
 *   `org.citra.citra_emu.activities.EmulationActivity` with an absolute name,
 *   exported, `singleTop`, and an `ACTION_VIEW` filter for `content` plus
 *   `application/octet-stream`.
 * - `src/android/app/src/googlePlay/AndroidManifest.xml` only removes storage
 *   permissions. It does not replace the activity.
 *
 * The class string is therefore the same for both application ids. The package
 * half of the component is the application id that was resolved.
 */
fun activityClass(packageName: String): String? = when (packageName) {
    VANILLA_PACKAGE, PLAY_PACKAGE -> EMULATION_ACTIVITY
    else -> null
}

const val VANILLA_PACKAGE: String = "org.azahar_emu.azahar"
const val PLAY_PACKAGE: String = "io.github.lime3ds.android"
const val EMULATION_ACTIVITY: String = "org.citra.citra_emu.activities.EmulationActivity"

/**
 * Where the title save lives, if the user has already pointed at that folder.
 * A content URI is kept as given. Any other string is not a location.
 * [SaveLocation.askForFolder] is a hint for save sync. It does not stop launch.
 * Azahar keeps playing with its own saves when this is unset.
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
