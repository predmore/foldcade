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
    override val saveFolderPlaces = listOf(LEGACY_FOLDER)
    override val saveFolderMarkers = setOf("GC", "Config")
    // Official provider exposes Android/data without an all-files workaround.
    // Source/Android/.../features/DocumentProvider.kt, ROOT_ID = "root".
    override val suggestedSaveFolder = "content://$PROVIDER/root/root"
    override fun saveDeclarations(game: Game) = listOf(SaveDeclaration(SLOT, folder))

    override fun findSaveFolderParts(slot: String, game: GameFileReader, folder: SaveFolderAccess): List<SaveFolderPart>? {
        if (slot != SLOT) return null
        val disc = gameCubeDisc(game) ?: return null
        if (readDolphinCard(folder, disc) != DolphinCard.Default) return null
        // Flat *.gci bundle, as Argosy's GciSaveHandler.createBundle uses:
        // https://github.com/rommapp/argosy-launcher/blob/dbe576f3cfa5dfb9db7001f3d2de7f473e5238fe/app/src/main/kotlin/com/nendo/argosy/data/sync/platform/GciSaveHandler.kt
        return listOf(SaveFolderPart("", disc.defaultCardPath))
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

    override fun saveSyncState(folder: SaveFolderAccess): SaveSyncState? = when (readDolphinCard(folder, null)) {
        DolphinCard.Default -> SaveSyncState.Syncing
        DolphinCard.Unknown -> null
        else -> SaveSyncState.NeedsSetup(SETUP)
    }

    companion object {
        const val SLOT = "gci"
        const val PROVIDER = "org.dolphinemu.dolphinemu.user"
        const val LEGACY_FOLDER = "dolphin-emu"
        // Shared language copy is supplied by the shell for this stable reason key.
        const val SETUP = "dolphin-folder-card"
    }
}

data class GameCubeDisc(val id: String, val region: String, val revision: Int = 0) {
    val defaultCardPath: String get() = "GC/$region/Card A"
}

/** Authoritative ISO/GCM header and region word, never a title/filename guess.
 * VolumeGC.cpp GetRegion reads 0x458; Blob.cpp checks the 0x1c GameCube magic.
 * https://github.com/dolphin-emu/dolphin/tree/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Core/DiscIO
 * Compressed images need a real blob reader; they remain visibly unresolved.
 */
fun gameCubeDisc(game: GameFileReader): GameCubeDisc? {
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
    return GameCubeDisc(id, region, head[7].toInt() and 0xff)
}

/** MainSettings.cpp defaults SlotA to MemoryCardFolder (8). Never change choices.
 * Custom folders, raw cards and non-default per-game card settings stay unresolved.
 */
sealed interface DolphinCard {
    data object Unknown : DolphinCard
    data object Default : DolphinCard
    data object OtherDevice : DolphinCard
    /** [path] is the resolved setting for the disc if the config names an absolute primary-storage path. */
    data class Custom(val path: String?) : DolphinCard
}

fun readDolphinCard(folder: SaveFolderAccess, disc: GameCubeDisc?): DolphinCard {
    val config = folder.list("Config") ?: return DolphinCard.Unknown
    val values = mutableMapOf<String, String>()
    if (config.any { it.name == "Dolphin.ini" }) {
        values.putAll(readDolphinIni(folder, "Config/Dolphin.ini", game = false) ?: return DolphinCard.Unknown)
    }
    if (disc != null) {
        val settings = folder.list("GameSettings")
        if (settings == null && folder.list("")?.none { it.name == "GameSettings" } != true) return DolphinCard.Unknown
        val id = disc.id
        // GameConfigLoader.cpp loads the local system, title, region and exact revision in this order.
        for (name in listOf("${id.take(1)}.ini", "${id.take(3)}.ini", "$id.ini", "${id}r${disc.revision}.ini")) {
            if (settings?.any { it.name == name } != true) continue
            values.putAll(readDolphinIni(folder, "GameSettings/$name", game = true) ?: return DolphinCard.Unknown)
        }
    }
    val device = values["slota"]?.let { it.toIntOrNull() ?: return DolphinCard.Unknown } ?: 8
    if (device != 8) return DolphinCard.OtherDevice
    // EXI_DeviceMemoryCard.cpp uses the override literally; MainSettings.cpp adds/replaces the region otherwise.
    val override = values["gcifolderapathoverride"].orEmpty()
    if (override.isNotEmpty()) return DolphinCard.Custom(override.primaryPath())
    val base = values["gcifolderapath"].orEmpty()
    if (base.isEmpty()) return DolphinCard.Default
    val path = base.replace('\\', '/').primaryPath()
    val region = if (disc?.region == "JAP") "JPN" else disc?.region
    return DolphinCard.Custom(if (path != null && region != null) {
        val parent = if (path.substringAfterLast('/') in setOf("USA", "EUR", "JPN", "DEV")) path.substringBeforeLast('/') else path
        "$parent/$region"
    } else null)
}

private fun String.primaryPath(): String? {
    val path = when {
        startsWith("/sdcard/") -> removePrefix("/sdcard/")
        startsWith("/storage/emulated/0/") -> removePrefix("/storage/emulated/0/")
        else -> return null
    }.trimEnd('/')
    return path.takeIf { it.isNotEmpty() && it.split('/').none { part -> part.isEmpty() || part == "." || part == ".." } &&
        it.none { char -> char.isISOControl() || char == '\\' } }
}

private fun readDolphinIni(folder: SaveFolderAccess, path: String, game: Boolean): Map<String, String>? {
    val bytes = folder.read(path, 256 * 1024) ?: return null
    val text = bytes.toString(Charsets.UTF_8)
    if ('\u0000' in text || '\uFFFD' in text) return null
    val core = coreSettings(text)
    if (!game) return core
    val mainCore = coreSettings(text, sectionName = "main.core")
    // The two spellings can map to the same key. Leave conflicting spellings unresolved.
    if (core.any { (key, value) -> key in mainCore && mainCore[key] != value }) return null
    return core + mainCore
}

/** Common/IniFile.cpp: case-insensitive names, UTF-8 BOM, first closing bracket,
 * quoted values and last assignment wins. Leading whitespace does not start a section.
 * https://github.com/dolphin-emu/dolphin/blob/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Core/Common/IniFile.cpp
 */
private fun coreSettings(text: String, sectionName: String = "core"): Map<String, String> {
    val values = mutableMapOf<String, String>()
    var section = ""
    for (line in text.removePrefix("\uFEFF").lineSequence()) {
        if (line.startsWith('[')) {
            val end = line.indexOf(']')
            if (end > 0) section = line.substring(1, end).lowercase()
            continue
        }
        if (section != sectionName) continue
        if ('=' !in line || line.firstOrNull() in listOf('#', '$', '+', '*')) continue
        val key = line.substringBefore('=').trim().lowercase()
        if (key in setOf("slota", "gcifolderapath", "gcifolderapathoverride")) {
            values[key] = line.substringAfter('=').trim().removeSurrounding("\"")
        }
    }
    return values
}
