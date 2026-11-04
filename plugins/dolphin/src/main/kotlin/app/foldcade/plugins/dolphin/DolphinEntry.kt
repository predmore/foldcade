package app.foldcade.plugins.dolphin

import app.foldcade.api.plugin.*

/** Official Dolphin's GameCube folder card. Wii and forks stay launch-only. MIT. */
class DolphinEntry : PluginEntry {
    override val apiVersion = PLUGIN_API_VERSION
    override val apiMinor = PLUGIN_API_MINOR
    override val players: List<Player> = listOf(DolphinPlayer(), DolphinLaunchPlayer("wii"))
}

open class DolphinLaunchPlayer(override val platformId: String) : Player {
    override val id = "dolphin.$platformId"
    override val displayName = "Dolphin"
    override val packageNames = listOf("org.dolphinemu.dolphinemu")
    override val needsLocalFile = true
    override val startDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays = false
    override val requiresImportedGame = false
    protected open val launchFlags = setOf(LaunchFlag.NewTask)
    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage !in packageNames) throw PluginException.NotFound("Dolphin is not installed.")
        val uri = (request.target as? LaunchTarget.ContentUri)?.uri?.takeIf { it.startsWith("content:") }
            ?: throw PluginException.NotFound("Dolphin needs a content URI.")
        return PlayerIntent(request.resolvedPackage, "org.dolphinemu.dolphinemu.ui.main.TvMainActivity", "android.intent.action.MAIN",
            dataUri = uri, grantReadUri = true, extras = listOf(PlayerExtra.Text("AutoStartFile", uri)),
            flags = launchFlags)
    }
}

class DolphinPlayer : DolphinLaunchPlayer("gamecube"), SaveFolderHolder {
    override val launchFlags = setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask)
    @Volatile private var folder: String? = null
    override fun bindSaveFolder(contentUri: String) { folder = contentUri.takeIf { it.startsWith("content:") } }
    override fun hasSaveFolder() = folder != null
    override val saveFolderKey = "dolphin"
    override val saveFolderPlaces = listOf("dolphin-emu", "Android/data/org.dolphinemu.dolphinemu/files")
    override val saveFolderMarkers = setOf("GC", "Config")
    // Official provider exposes Android/data without an all-files workaround.
    // Source/Android/.../features/DocumentProvider.kt, ROOT_ID = "root".
    override val suggestedSaveFolder = "content://org.dolphinemu.dolphinemu.user/root/root"
    override fun saveDeclarations(game: Game) = listOf(SaveDeclaration(SLOT, folder))

    override fun findSaveFolderParts(slot: String, game: GameFileReader, folder: SaveFolderAccess): List<SaveFolderPart>? {
        if (slot != SLOT) return null
        val disc = gameCubeDisc(game) ?: return null
        if (!defaultFolderCard(folder, disc.id)) return null
        // Flat *.gci bundle, as Argosy's GciSaveHandler.createBundle uses:
        // https://github.com/rommapp/argosy-launcher/blob/dbe576f3cfa5dfb9db7001f3d2de7f473e5238fe/app/src/main/kotlin/com/nendo/argosy/data/sync/platform/GciSaveHandler.kt
        return listOf(SaveFolderPart("", "GC/${disc.region}/Card A"))
    }

    override fun saveArchiveFilter(slot: String, game: GameFileReader): SaveArchiveFilter? {
        if (slot != SLOT) return null
        val disc = gameCubeDisc(game) ?: return null
        return object : SaveArchiveFilter {
            override val headerSize = 64
            override fun accepts(path: String, header: ByteArray): Boolean =
                '/' !in path && path.endsWith(".gci", ignoreCase = true) &&
                    header.size >= 64 && String(header, 0, 6, Charsets.US_ASCII) == disc.id
        }
    }

    override fun saveSyncState(folder: SaveFolderAccess): SaveSyncState =
        if (defaultFolderCard(folder)) SaveSyncState.Syncing else SaveSyncState.NeedsSetup(SETUP)

    companion object {
        const val SLOT = "gci"
        // Shared language copy is supplied by the shell for this stable reason key.
        const val SETUP = "dolphin-folder-card"
    }
}

internal data class GameCubeDisc(val id: String, val region: String)

/** Authoritative ISO/GCM header and region word, never a title/filename guess.
 * VolumeGC.cpp GetRegion reads 0x458; Blob.cpp checks the 0x1c GameCube magic.
 * https://github.com/dolphin-emu/dolphin/tree/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Core/DiscIO
 * Compressed images need a real blob reader; they remain visibly unresolved.
 */
internal fun gameCubeDisc(game: GameFileReader): GameCubeDisc? {
    val head = game.read(0, 0x460) ?: return null
    if (head.size < 0x45c || !head.copyOfRange(0x1c, 0x20).contentEquals(byteArrayOf(0xc2.toByte(), 0x33, 0x9f.toByte(), 0x3d))) return null
    val id = String(head, 0, 6, Charsets.US_ASCII)
    if (!Regex("[A-Z0-9]{6}").matches(id)) return null
    val region = when (head.copyOfRange(0x458, 0x45c).toList()) {
        listOf<Byte>(0, 0, 0, 0) -> "JAP"
        listOf<Byte>(0, 0, 0, 1) -> "USA"
        listOf<Byte>(0, 0, 0, 2) -> "EUR"
        else -> return null
    }
    return GameCubeDisc(id, region)
}

/** MainSettings.cpp defaults SlotA to MemoryCardFolder (8). Never change choices.
 * Custom folders, raw cards, overrides and per-game card settings stay unresolved.
 */
internal fun defaultFolderCard(folder: SaveFolderAccess, gameId: String? = null): Boolean {
    val entries = folder.list("Config") ?: return false
    if (gameId != null) {
        val gameSettings = folder.list("GameSettings").orEmpty().filter { !it.isFolder &&
            (it.name in listOf("${gameId.take(1)}.ini", "${gameId.take(3)}.ini", "$gameId.ini") ||
                (it.name.startsWith(gameId + "r") && it.name.endsWith(".ini"))) }
        for (settings in gameSettings) {
            val text = folder.read("GameSettings/${settings.name}", 256 * 1024)?.toString(Charsets.UTF_8) ?: return false
            if (text.lineSequence().any { it.substringBefore('=').trim().lowercase() in setOf("slota", "gcifolderapath", "gcifolderapathoverride", "memcardapath") }) return false
        }
    }
    if (entries.none { it.name == "Dolphin.ini" }) return true
    val ini = folder.read("Config/Dolphin.ini", 256 * 1024)?.toString(Charsets.UTF_8) ?: return false
    val core = coreSettings(ini)
    return core["slota"].let { it == null || it == "8" } &&
        core["gcifolderapath"].isNullOrEmpty() && core["gcifolderapathoverride"].isNullOrEmpty()
}

/** Common/IniFile.cpp: case-insensitive names, UTF-8 BOM, first closing bracket,
 * quoted values and last assignment wins. Leading whitespace does not start a section.
 * https://github.com/dolphin-emu/dolphin/blob/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Core/Common/IniFile.cpp
 */
private fun coreSettings(text: String): Map<String, String> {
    val values = mutableMapOf<String, String>()
    var section = ""
    for (line in text.removePrefix("\uFEFF").lineSequence()) {
        if (line.startsWith('[')) {
            val end = line.indexOf(']')
            if (end > 0) section = line.substring(1, end).lowercase()
            continue
        }
        if (section != "core" || '=' !in line || line.firstOrNull() in listOf('#', '$', '+', '*')) continue
        values[line.substringBefore('=').trim().lowercase()] = line.substringAfter('=').trim().removeSurrounding("\"")
    }
    return values
}
