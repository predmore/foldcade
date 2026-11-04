package app.foldcade.plugins.explus

import app.foldcade.api.plugin.*

/** EX battery saves, separate from the data-only emulator table. MIT. */
class ExPlusEntry : PluginEntry {
    override val apiVersion = PLUGIN_API_VERSION
    override val apiMinor = PLUGIN_API_MINOR
    override val players: List<Player> = listOf(
        ExPlusPlayer("gba-emu", "GBA.emu", "com.explusalpha.GbaEmu", "GBA", "game-boy-advance"),
        ExPlusPlayer("gbc-emu", "GBC.emu", "com.explusalpha.GbcEmu", "GB", "game-boy"),
        ExPlusPlayer("gbc-emu", "GBC.emu", "com.explusalpha.GbcEmu", "GB", "game-boy-color"),
        ExPlusPlayer("nes-emu", "NES.emu", "com.explusalpha.NesEmu", "FC-NES", "nes"),
        ExPlusPlayer("snes9x-ex", "Snes9x EX+", "com.explusalpha.Snes9xPlus", "SFC-SNES", "snes"),
        ExPlusPlayer("md-emu", "MD.emu", "com.explusalpha.MdEmu", "MD-Genesis", "genesis"),
        ExPlusPlayer("md-emu", "MD.emu", "com.explusalpha.MdEmu", "MD-Genesis", "master-system"),
        ExPlusPlayer("md-emu", "MD.emu", "com.explusalpha.MdEmu", "MD-Genesis", "sega-cd"),
    )
}

/** EmuSystem::contentNameExt, with companion files in the same archive.
 * https://github.com/Rakashazi/emu-ex-plus-alpha/blob/1c12fac5ce49badaadff2e2f210dcc30b89f4943/EmuFramework/src/EmuSystem.cc
 * Each system's src/main/Main.cc defines the extensions; GBC's .rtc is a
 * separate four-byte big-endian clock. SNES S-RTC/SPC7110
 * cartridges also keep a 20-byte .rtc (CMemory::SaveSRTC / S9XApi.cc). MD's .brm includes internal and cartridge BRAM.
 */
class ExPlusPlayer(
    private val emulatorId: String,
    override val displayName: String,
    packageName: String,
    shortSystemName: String,
    override val platformId: String,
) : Player, SaveFolderHolder {
    override val id = "$emulatorId.$platformId"
    override val packageNames = listOf(packageName)
    override val needsLocalFile = true
    override val startDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays = false
    override val requiresImportedGame = false
    override val saveFolderKey = emulatorId
    @Volatile private var folder: String? = null
    override fun bindSaveFolder(contentUri: String) { folder = contentUri.takeIf { it.startsWith("content:") } }
    override fun hasSaveFolder() = folder != null
    // Current Android storagePath is externalDataPath, NOT /sdcard. The private
    // default is a hint only unless a held SAF grant actually reaches it.
    // imagine/src/base/android/ApplicationContext.cc; EmuSystem::fallbackSaveDirectory.
    override val saveFolderPlaces = listOf("saves/$displayName", "EmuEx/$shortSystemName/saves",
        "Android/data/$packageName/files/EmuEx/$shortSystemName/saves")
    override val saveFolderPickedInPlayer = true
    override val suggestedSaveFolder = "content://com.android.externalstorage.documents/document/primary%3Asaves"
    override fun saveDeclarations(game: Game) = listOf(SaveDeclaration(SLOT, folder))

    override fun findSaveFolderParts(slot: String, game: GameFileReader, folder: SaveFolderAccess): List<SaveFolderPart>? =
        if (slot == SLOT && saveNames(game.fileName) != null && folder.list("") != null)
            listOf(SaveFolderPart("", "")) else null

    override fun saveArchiveFilter(slot: String, game: GameFileReader): SaveArchiveFilter? {
        if (slot != SLOT) return null
        val names = saveNames(game.fileName) ?: return null
        val base = game.fileName!!.substringBeforeLast('.')
        return object : SaveArchiveFilter {
            override val headerSize = 0
            override fun accepts(path: String, header: ByteArray): Boolean {
                if (path in names) return true
                // EmuApp::contentSaveFilePath uses <content>/saves/<slot>/auto.ext
                // for named autosave slots. Keep every battery slot together; never
                // infer which slot the private runtime currently has open. States stay out.
                val parts = path.split('/')
                return parts.size == 4 && parts[0] == base && parts[1] == "saves" &&
                    parts[2].isNotEmpty() && parts[2] !in setOf(".", "..") &&
                    names.any { parts[3] == "auto" + it.removePrefix(base) }
            }
        }
    }

    private fun saveNames(filename: String?): Set<String>? {
        if (filename.isNullOrBlank() || filename.any { it == '/' || it == '\\' || it.code < 32 } || '.' !in filename) return null
        val base = filename.substringBeforeLast('.').takeIf { it.isNotBlank() && it !in setOf(".", "..") } ?: return null
        val extension = filename.substringAfterLast('.').lowercase()
        val suffixes = when (emulatorId) {
            // EmuSystem::loadContentFromFile retains the archive's display-name
            // basename when it selects an inner ROM. These apps have one battery type.
            "gba-emu" -> if (extension in setOf("gba", "mb", "zip", "7z")) setOf(".sav") else null
            "gbc-emu" -> if (extension in setOf("gb", "gbc", "zip", "7z")) setOf(".sav", ".rtc") else null
            "nes-emu" -> when (extension) { "nes", "unf", "unif" -> setOf(".sav"); "fds" -> setOf(".fds.sav"); else -> null }
            "snes9x-ex" -> if (extension in setOf("sfc", "smc", "zip", "7z")) setOf(".srm", ".rtc") else null
            "md-emu" -> if (platformId == "sega-cd") {
                if (extension in setOf("cue", "chd", "iso", "bin")) setOf(".brm") else null
            } else if (extension in setOf("gen", "smd", "md", "bin", "sms")) setOf(".srm") else null
            else -> null
        } ?: return null
        return suffixes.map { base + it }.toSet()
    }

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage !in packageNames) throw PluginException.NotFound("$displayName is not installed.")
        val uri = (request.target as? LaunchTarget.ContentUri)?.uri?.takeIf { it.startsWith("content:") }
            ?: throw PluginException.NotFound("$displayName needs a content URI.")
        return PlayerIntent(request.resolvedPackage, "com.imagine.BaseActivity", "android.intent.action.VIEW",
            dataUri = uri, grantReadUri = true,
            flags = setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask))
    }

    companion object { const val SLOT = "battery" }
}
