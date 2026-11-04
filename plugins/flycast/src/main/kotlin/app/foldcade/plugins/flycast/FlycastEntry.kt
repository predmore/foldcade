package app.foldcade.plugins.flycast

import app.foldcade.api.plugin.*

/** Official Flycast's isolated console VMU. Shared VMUs never belong to a game. MIT. */
class FlycastEntry : PluginEntry {
    override val apiVersion = PLUGIN_API_VERSION
    override val apiMinor = PLUGIN_API_MINOR
    override val players: List<Player> = listOf(FlycastPlayer())
}

class FlycastPlayer : Player, SaveFolderHolder {
    override val id = "flycast.dreamcast"
    override val displayName = "Flycast"
    override val platformId = "dreamcast"
    override val packageNames = listOf("com.flycast.emulator")
    override val needsLocalFile = true
    override val startDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays = false
    override val requiresImportedGame = false
    @Volatile private var folder: String? = null
    override fun bindSaveFolder(contentUri: String) { folder = contentUri.takeIf { it.startsWith("content:") } }
    override fun hasSaveFolder() = folder != null
    override val saveFolderPlaces = listOf("Flycast", "flycast", ".reicast", "reicast", "Android/data/com.flycast.emulator/files")
    override val saveFolderMarkers = setOf("emu.cfg")
    override val saveFolderPickedInPlayer = true
    override val suggestedSaveFolder = "content://com.android.externalstorage.documents/document/primary%3AFlycast"
    override fun saveDeclarations(game: Game) = listOf(SaveDeclaration(SLOT, folder))

    override fun findSaveFolderParts(slot: String, game: GameFileReader, folder: SaveFolderAccess): List<SaveFolderPart>? {
        if (slot != SLOT) return null
        val id = dreamcastId(game) ?: return null
        val config = flycastConfig(folder) ?: return null
        if (!config.perGame(id)) return null
        val path = config.vmuDirectory(this.folder) ?: return null
        // Android.cpp uses home/data, falling back to home only if mkdir fails.
        // Refuse a legacy read location until Flycast itself has saved to its current path.
        val filename = vmuName(id)
        if (path != "data" && folder.list(path)?.none { it.name == filename } != false &&
            (folder.list("data")?.any { it.name == filename } == true || folder.list("")?.any { it.name == filename } == true)) return null
        if (path == "data" && folder.list("data") == null) return null
        if (path == "data" && folder.list("data")?.none { it.name == filename } == true &&
            (folder.list("")?.any { it.name == filename } == true ||
                folder.list("data")?.any { it.name == game.fileName + "_vmu_save_A1.bin" } == true)) return null
        return listOf(SaveFolderPart("", path))
    }

    override fun saveArchiveFilter(slot: String, game: GameFileReader): SaveArchiveFilter? {
        if (slot != SLOT) return null
        val name = dreamcastId(game)?.let(::vmuName) ?: return null
        return object : SaveArchiveFilter {
            override val headerSize = 0
            override fun accepts(path: String, header: ByteArray) = path == name
        }
    }

    override fun saveSyncState(folder: SaveFolderAccess): SaveSyncState =
        if (flycastConfig(folder)?.let { it.perGame(null) && it.vmuDirectory(this.folder) != null } == true)
            SaveSyncState.Syncing else SaveSyncState.NeedsSetup(SETUP)

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage !in packageNames) throw PluginException.NotFound("Flycast is not installed.")
        val uri = (request.target as? LaunchTarget.ContentUri)?.uri?.takeIf { it.startsWith("content:") }
            ?: throw PluginException.NotFound("Flycast needs a content URI.")
        return PlayerIntent(request.resolvedPackage, "com.flycast.emulator.MainActivity", "android.intent.action.VIEW",
            dataUri = uri, grantReadUri = true,
            flags = setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask))
    }

    companion object {
        const val SLOT = "vmu"
        const val SETUP = "flycast-per-game-vmu"
    }
}

/** oslib.cpp getVmuPath: only A1 is per game, even when PerGameVmu is true.
 * https://github.com/flyinghead/flycast/blob/7adbf3caef251b153e348df3f469a0f35878339d/core/oslib/oslib.cpp
 */
internal fun vmuName(id: String) = id.map { if (it in " /\\:*?|<>\"") '_' else it }.joinToString("") + "_vmu_save_A1.bin"
