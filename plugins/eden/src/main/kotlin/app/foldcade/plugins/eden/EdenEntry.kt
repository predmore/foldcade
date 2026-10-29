package app.foldcade.plugins.eden

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameFileReader
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
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SaveFolderSearch
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
class EdenPlayer : Player, SaveFolderHolder {
    override val id: String = "eden"
    override val displayName: String = "Eden"
    override val platformId: String = "nintendo-switch"
    override val packageNames: List<String> = EDEN_PACKAGES
    override val needsLocalFile: Boolean = true
    override val startDisplay: StartDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays: Boolean = false
    override val requiresImportedGame: Boolean = false
    override val suggestedSaveFolder: String = "content://${EDEN_PACKAGES.first()}.user/root/root"

    @Volatile
    private var saveFolder: String? = null

    override fun bindSaveFolder(contentUri: String) {
        saveFolder = contentUri.takeIf { it.startsWith("content:") }
    }

    override fun hasSaveFolder(): Boolean = saveFolder != null

    override fun saveDeclarations(game: Game): List<SaveDeclaration> =
        saveFolder?.let { listOf(SaveDeclaration(SLOT, it)) }.orEmpty()

    override fun findSaveFolders(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean): SaveFolderSearch? {
        if (slot != SLOT) return null
        val title = switchTitleId(game, folder) ?: return null
        val user = edenUser(folder) ?: return null
        return SaveFolderSearch("$SAVES/$user", title)
    }

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage !in EDEN_PACKAGES) throw PluginException.NotFound("Eden is not installed.")
        val uri = (request.target as? LaunchTarget.ContentUri)?.uri
        if (uri == null || !uri.startsWith("content:")) throw PluginException.NotFound("Eden needs a content URI.")
        return PlayerIntent(
            packageName = request.resolvedPackage,
            componentClass = "org.yuzu.yuzu_emu.activities.EmulationActivity",
            action = "android.nfc.action.TECH_DISCOVERED",
            dataUri = uri,
            grantReadUri = true,
            flags = setOf(LaunchFlag.NewTask),
        )
    }

    companion object {
        const val SLOT: String = "savedata"
        const val SAVES: String = "nand/user/save/0000000000000000"
        const val PROFILES: String = "nand/system/save/8000000000000010/su/avators/profiles.dat"
    }
}

/** Read only. An unreadable profile may use one unambiguous existing user folder. */
internal fun edenUser(folder: SaveFolderAccess): String? = try {
    (try { profileUser(folder) } catch (_: Exception) { null }) ?: folder.list(EdenPlayer.SAVES)?.filter {
        it.isFolder && it.name.matches(Regex("[0-9a-fA-F]{32}")) && it.name.any { char -> char != '0' }
    }?.singleOrNull()?.name
} catch (_: Exception) {
    null
}

private fun profileUser(folder: SaveFolderAccess): String? {
    val config = folder.read("config/config.ini", 1024 * 1024)?.toString(Charsets.UTF_8).orEmpty()
    var system = false
    var index = 0
    for (raw in config.lineSequence()) {
        val line = raw.substringBefore(';').substringBefore('#').trim()
        if (line.startsWith('[')) system = line == "[System]"
        if (system && line.substringBefore('=').trim() == "current_user") {
            index = line.substringAfter('=', "").trim().toIntOrNull() ?: return null
        }
    }
    if (index !in 0..7) return null
    val profiles = folder.read(EdenPlayer.PROFILES, 0x650) ?: return null
    if (profiles.size != 0x650) return null
    val uuid = profiles.copyOfRange(0x10 + index * 0xC8, 0x20 + index * 0xC8)
    if (uuid.all { it == 0.toByte() }) return null
    return uuid.reversedArray().joinToString("") { "%02X".format(it.toInt() and 0xFF) }
}
