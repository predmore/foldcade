package app.foldcade.plugins.ppsspp

import app.foldcade.api.plugin.*

/** PSP saves, separately from the launch-only emulator table. Licensed under MIT. */
class PpssppEntry : PluginEntry {
    override val apiVersion = PLUGIN_API_VERSION
    override val apiMinor = PLUGIN_API_MINOR
    override val players: List<Player> = listOf(PpssppPlayer())
}

class PpssppPlayer : Player, SaveFolderHolder {
    // Keep the former table id, including saved per-game emulator choices.
    override val id = "ppsspp.psp"
    override val displayName = "PPSSPP"
    override val platformId = "psp"
    override val packageNames = listOf("org.ppsspp.ppssppgold", "org.ppsspp.ppsspp")
    override val needsLocalFile = true
    override val startDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays = false
    override val requiresImportedGame = false
    @Volatile private var folder: String? = null
    override fun bindSaveFolder(contentUri: String) { folder = contentUri.takeIf { it.startsWith("content:") } }
    override fun hasSaveFolder() = folder != null
    // PPSSPP Core/Config.cpp GetSysDirectory and UI/MemStickScreen.cpp:
    // https://github.com/hrydgard/ppsspp/tree/3bc60270eae3069f13dfbfdb7292a7e1375de6f6
    // Pick PSP itself, whose children include SAVEDATA and SYSTEM.
    override val saveFolderPlaces = listOf("PSP",
        "Android/data/org.ppsspp.ppsspp/files/PSP", "Android/data/org.ppsspp.ppssppgold/files/PSP")
    override val saveFolderMarkers = setOf("SAVEDATA", "SYSTEM")
    override val saveFolderPickedInPlayer = true
    override val suggestedSaveFolder = "content://com.android.externalstorage.documents/document/primary%3APSP"
    override fun saveDeclarations(game: Game) = listOf(SaveDeclaration(SLOT, folder))

    // Argosy uses the same prefix-folder ZIP layout:
    // https://github.com/rommapp/argosy-launcher/blob/dbe576f3cfa5dfb9db7001f3d2de7f473e5238fe/app/src/main/kotlin/com/nendo/argosy/data/sync/platform/PrefixBundleFolderHandler.kt
    override fun findSaveFolders(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean): SaveFolderSearch? =
        if (slot == SLOT) pspDiscId(game)?.let { SaveFolderSearch("SAVEDATA", it) } else null

    override fun saveArchiveFilter(slot: String, game: GameFileReader): SaveArchiveFilter? {
        if (slot != SLOT) return null
        val id = pspDiscId(game) ?: return null
        return object : SaveArchiveFilter {
            override val headerSize = 0
            // A substring match alone could steal a different game's folder. Require
            // the actual nine-character DISC_ID at the start, without normalization.
            override fun accepts(path: String, header: ByteArray) = path.substringBefore('/').startsWith(id)
        }
    }

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage !in packageNames) throw PluginException.NotFound("PPSSPP is not installed.")
        val uri = (request.target as? LaunchTarget.ContentUri)?.uri?.takeIf { it.startsWith("content:") }
            ?: throw PluginException.NotFound("PPSSPP needs a content URI.")
        return PlayerIntent(request.resolvedPackage, "org.ppsspp.ppsspp.PpssppActivity", "android.intent.action.VIEW",
            dataUri = uri, grantReadUri = true,
            flags = setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask))
    }

    companion object { const val SLOT = "savedata" }
}
