package app.foldcade

import app.foldcade.api.plugin.FirmwareBytes
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.plugins.melonds.MelonDsPlayer
import app.foldcade.plugins.retroarch.RetroArchPlayer

/** Optional BIOS must never add storage discovery, downloads or setup to a game start. */
internal fun firmwareRequired(player: Player, game: Game): Boolean = when (player) {
    is RetroArchPlayer -> player.needsFirmwareFolder(game)
    // Android has no public API to select an external BIOS. Leave its own
    // configured folders alone; default DS play uses the bundled BIOS.
    is MelonDsPlayer -> false
    else -> player.firmwareFileName("bios.bin") != null || player.firmwareFileName("PS3UPDAT.PUP") != null
}

internal fun firmwareGame(game: Game, target: LaunchTarget, docs: SaveDocuments): Game {
    // RomM's title omits region tags; the document name identifies disc region.
    val name = when (target) {
        is LaunchTarget.ContentUri -> docs.displayName(target.uri)
        is LaunchTarget.SharedFile -> target.path.substringAfterLast('/')
        else -> null
    }
    return name?.let { game.copy(label = it) } ?: game
}

/** The actual launch pipeline, with lazy Android access so its fast paths do no source I/O. */
internal fun prepareFirmware(
    player: Player, game: Game, target: LaunchTarget, place: String,
    docs: SaveDocuments, saves: PlayerSaves,
    folder: () -> String?, local: () -> List<FirmwareBytes>, onCopy: () -> Unit = {},
): FirmwareResult {
    fun result(written: List<String> = emptyList(), missing: List<String> = emptyList(), needsFolder: Boolean = false) =
        FirmwareResult(written, missing, needsFolder, place)
    if (!firmwareRequired(player, game)) return result()
    val launchGame = firmwareGame(game, target, docs)
    val into = folder() ?: return result(needsFolder = true)
    val access = saves.firmwareFolder(player, into)
    if (access.list("") == null) return result(needsFolder = true)
    if (!player.needsFirmware(access)) return result()
    val missing = player.missingFirmware(launchGame, access)
    if (player is RetroArchPlayer && missing.isEmpty()) return result()
    val sources = (target as? LaunchTarget.ContentUri)?.firmware.orEmpty()
    val localSources = if (missing.isNotEmpty() || player is app.foldcade.plugins.aps3e.Aps3ePlayer) local() else emptyList()
    val uri = (target as? LaunchTarget.ContentUri)?.uri ?: "content://firmware/launch"
    val written = saves.placeFirmware(player, launchGame, LaunchTarget.ContentUri(uri, sources + localSources), into, onWrite = onCopy)
    return result(written, saves.missingFirmware(player, launchGame, into))
}

/** A confirmed custom system folder stays chosen. Only an explicit RetroArch root is narrowed. */
internal fun pickedFirmwareFolder(picked: String, isDirectory: (String) -> Boolean, system: (String) -> String?): String? {
    if (!isDirectory(picked)) return null
    return if (folderDocumentId(picked).substringAfter(':').substringAfterLast('/').equals("RetroArch", true)) system(picked) else picked
}

/** Reuse ordinary grants on any volume. An explicit custom destination always wins. */
internal fun findFirmwareFolder(
    picked: String?, grants: List<SaveFolderGrant>, isDirectory: (String) -> Boolean, system: (String) -> String?,
): String? {
    val writable = grants.filter { it.read && it.write }
    if (picked != null) return picked.takeIf { uri ->
        writable.any { folderGrantUri(it.uri) == folderGrantUri(uri) } && isDirectory(uri)
    }
    val finder = SaveFolderFinder(writable, isDirectory, { null }, { false }, { false })
    val volumes = (listOf("primary") + writable.map { folderTreeId(it.uri).substringBefore(':') }).distinct()
    for (volume in volumes) {
        finder.reachable("$volume:RetroArch/system")?.takeIf(isDirectory)?.let { return it }
        finder.reachable("$volume:RetroArch")?.takeIf(isDirectory)?.let { root -> system(root)?.let { return it } }
    }
    return null
}
