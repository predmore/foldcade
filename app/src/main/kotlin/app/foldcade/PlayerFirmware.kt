package app.foldcade

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import app.foldcade.api.plugin.FirmwareBytes
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.localfolder.localBiosFolderPaths
import app.foldcade.plugins.retroarch.RetroArchPlayer
import app.foldcade.plugins.romm.unpackLocalFirmwareZip
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.cancellation.CancellationException

internal data class FirmwareResult(val written: List<String>, val missing: List<String>, val needsFolder: Boolean, val place: String) {
    // A missing grant cannot prove the emulator lacks its own BIOS.
    val blocksLaunch: Boolean get() = !needsFolder && missing.isNotEmpty()
}

/** Upload advice is actionable only when this launch negotiates firmware with RomM. */
internal fun canUseRommFirmware(backendId: String, connected: Boolean): Boolean =
    connected && backendId == app.foldcade.plugins.romm.ROMM_LIBRARY_ID

/** Launch-only I/O. Every source is the player's storage or a checked RomM download. */
internal class PlayerFirmware(private val context: Context, private val saves: PlayerSaves, private val systemFolder: () -> String? = { null }) {
    private val docs = AndroidSaveDocuments(context.contentResolver)
    private val android = AndroidSaveFolderFinder(context)
    private val sourceCache = FirmwareSourceCache()

    /** Payloads wait until their destination is writable; an unknown folder is optional setup. */
    fun canPlace(player: Player, game: Game, picked: String?, target: LaunchTarget): Boolean {
        if (!firmwareRequired(player, game)) return false
        val grants = firmwareGrants(player)
        val folder = if (player is RetroArchPlayer) retroArchFolder(grants) else
            declaredFolder(player, game, picked)?.takeIf { android.canReach(it, grants) }
        folder ?: return false
        val access = saves.firmwareFolder(player, folder)
        return if (player is RetroArchPlayer) player.missingFirmware(firmwareGame(game, target, docs), access).isNotEmpty()
            else player.needsFirmware(access)
    }

    private fun retroArchFolder(grants: List<SaveFolderGrant>): String? =
        findFirmwareFolder(systemFolder(), grants, android::isDirectory, ::systemUnder)

    private fun systemUnder(root: String): String? = if (docs.createFolder(root, "system"))
        docs.children(root, "")?.firstOrNull { it.isFolder && it.name == "system" }?.uri else null

    private fun declaredFolder(player: Player, game: Game, picked: String?): String? = picked ?:
        if (player is app.foldcade.api.plugin.HostedSaveFolder) null else player.saveDeclarations(game).firstNotNullOfOrNull { it.locationUri }

    private fun firmwareGrants(player: Player): List<SaveFolderGrant> {
        val holder = player as? SaveFolderHolder
        val holders = if (holder == null) emptyList() else listOf(if (player is RetroArchPlayer || player is app.foldcade.plugins.melonds.MelonDsPlayer) {
            object : SaveFolderHolder by holder {
                override val saveFolderPlaces = holder.saveFolderPlaces + listOf("RetroArch", "RetroArch/system")
            }
        } else holder)
        return android.grants(holders)
    }

    fun prepare(player: Player, game: Game, target: LaunchTarget, picked: String?, libraries: List<String>, onCopy: () -> Unit = {}): FirmwareResult {
        val grants by lazy { firmwareGrants(player) }
        val place = if (player is RetroArchPlayer) systemFolder()?.let(::folderPathLabel) ?: "RetroArch/system" else when (player.id) {
            "duckstation" -> (picked?.let(::folderPathLabel) ?: "Android/data/com.github.stenzek.duckstation/files") + "/bios"
            "armsx2" -> (picked?.let(::folderPathLabel) ?: "Android/data/com.armsx2/files") + "/bios"
            "aps3e" -> "aPS3e/firmware"
            else -> player.displayName
        }
        return prepareFirmware(player, game, target, place, docs, saves,
            folder = { if (player is RetroArchPlayer) retroArchFolder(grants)
                else declaredFolder(player, game, picked)?.takeIf(android::isDirectory) },
            local = { localFirmware(player, libraries, grants, onCopy) }, onCopy = onCopy)
    }

    private fun localFirmware(player: Player, libraries: List<String>, grants: List<SaveFolderGrant>, onCopy: () -> Unit): List<FirmwareBytes> {
        val roots = linkedSetOf<String>()
        val readable = grants.filter { it.read }
        fun reachable(id: String, authority: String? = null): String? = readable.firstOrNull { grant ->
            (authority == null || Uri.parse(grant.uri).authority == authority) && containsFolder(folderTreeId(grant.uri), id)
        }?.let { folderDocumentUri(it.uri, id) }
        // Explicit BIOS tree grants count even without a game library in that tree.
        readable.forEach { grant ->
            val id = folderTreeId(grant.uri)
            val path = id.substringAfter(':')
            if (path.substringAfterLast('/').equals("bios", true) || path == "RetroArch/system") roots += grant.uri
        }
        for (library in libraries) {
            val id = runCatching { folderDocumentId(library) }.getOrNull() ?: continue
            val volume = id.substringBefore(':')
            localBiosFolderPaths(id.substringAfter(':')).forEach { path ->
                reachable("$volume:$path", Uri.parse(library).authority)?.let(roots::add)
            }
        }
        listOf("RetroArch/system", "bios", "BIOS", "ROMs/bios", "roms/bios", "ES-DE/bios", "ES-DE/BIOS",
            "Batocera/bios", "batocera/bios", "Emulation/bios", "emulation/bios").forEach { path ->
            reachable("primary:$path")?.let(roots::add)
        }
        val files = linkedMapOf<String, SaveChild>()
        var visited = 0
        fun visit(root: String, depth: Int, relative: String = "") {
            if (depth > 5 || visited++ >= 128 || files.size >= 512) return
            if (Thread.currentThread().isInterrupted) throw CancellationException("BIOS search cancelled")
            val children = runCatching { docs.children(root, "") }.getOrNull().orEmpty()
            for (child in children) {
                if (files.size >= 512) break
                val name = listOf(relative, child.name).filter { it.isNotEmpty() }.joinToString("/")
                // Emulator asset/media trees are not BIOS collections for other cores.
                val assetTree = child.name in setOf("PPSSPP", "thumbnails", "screenshots", "artwork")
                val usesAssets = player is RetroArchPlayer && player.platformId == "psp"
                if (child.isFolder) {
                    if (!assetTree || usesAssets) visit(child.uri, depth + 1, name)
                }
                else {
                    val offeredName = if (player.firmwareKeepsFolders) name else child.name
                    if (player.firmwareFileName(offeredName) != null && child.size in 1..LOCAL_FILE_CAP) files.putIfAbsent(child.uri, child.copy(name = offeredName))
                }
            }
        }
        roots.forEach { visit(it, 0) }
        val playerKey = player.id + if (player is RetroArchPlayer) ":${player.pinnedCore}:${player.cores.activePackage()?.let(player::coreChoice)}" else ""
        var bytesRead = 0L
        var archiveBytes = 0L
        var unpackedBytes = 0L
        val chosen = files.values.flatMap { file ->
            val key = "$playerKey:${player.firmwareKeepsFolders}:${file.uri}"
            val version = FirmwareSourceVersion(file.size, file.modifiedMillis)
            sourceCache.get(key, version)?.takeIf { cached -> cached.all { docs.length(it.contentUri) != null } }
                ?.let { return@flatMap it }
            if (file.size !in 1..LOCAL_FILE_CAP) return@flatMap emptyList()
            val archiveName = file.name.endsWith(".zip", ignoreCase = true)
            if (archiveName && file.size > LOCAL_ARCHIVE_CAP || !archiveName && !localCandidate(player, file)) {
                sourceCache.put(key, version, emptyList())
                return@flatMap emptyList()
            }
            val zipped = archiveName && docs.head(file.uri, 4)?.contentEquals(byteArrayOf(0x50, 0x4b, 3, 4)) == true
            var complete = true
            val candidates = if (!zipped) listOf(file) else try {
                val zip = cacheArchive(file, onCopy) { count ->
                    archiveBytes += count
                    check(archiveBytes <= LOCAL_ARCHIVE_CAP)
                }
                val remaining = LOCAL_UNPACK_CAP - unpackedBytes
                val unpacked = unpackLocalFirmwareZip(zip.toPath(), keepFolders = player.firmwareKeepsFolders,
                    totalCap = remaining, selectionKey = "v2:$playerKey", candidate = { name, size ->
                        size in 1..LOCAL_FILE_CAP && player.firmwareFileName(name) != null && localCandidate(player, SaveChild(name, "", false, size))
                    }) { player.firmwareFileName(it) != null }
                unpackedBytes += unpacked.sumOf { it.second.toFile().length() }
                unpacked.map { (name, path) -> SaveChild(name,
                        FileProvider.getUriForFile(context, "${context.packageName}.romm.cache", path.toFile()).toString(), false,
                        path.toFile().length(), path.toFile().lastModified()) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { complete = false; emptyList() }
            val accepted = candidates.mapNotNull { candidate ->
                if (!localCandidate(player, candidate)) return@mapNotNull null
                val count = if (candidate.size > 0) minOf(candidate.size, player.firmwareHeaderSize.toLong()).toInt() else player.firmwareHeaderSize
                if (bytesRead + count > LOCAL_PROBE_CAP) { complete = false; return@mapNotNull null }
                val header = docs.head(candidate.uri, count) ?: return@mapNotNull null
                bytesRead += header.size
                val offer = app.foldcade.api.plugin.FirmwareOffer(candidate.name, header)
                if (player.chooseFirmware(listOf(offer)).isEmpty()) return@mapNotNull null
                FirmwareBytes(candidate.name, candidate.uri)
            }
            if (complete) sourceCache.put(key, version, accepted)
            accepted
        }
        return chosen.distinctBy { it.contentUri }
    }

    private fun localCandidate(player: Player, file: SaveChild): Boolean = when (player) {
        is RetroArchPlayer -> player.localFirmwareCandidate(file.name, file.size)
        is app.foldcade.plugins.duckstation.DuckStationPlayer -> file.size <= 0 || file.size in setOf(524288L, 4194304L, 0x3E66F0L)
        is app.foldcade.plugins.armsx2.ArmsX2Player -> file.size <= 0 || file.size in setOf(1048576L, 2097152L, 4194304L)
        is app.foldcade.plugins.melonds.MelonDsPlayer -> file.size <= 0 || file.name.substringAfterLast('/').lowercase() in setOf("nand.bin", "dsi_nand.bin") ||
            file.size in setOf(4096L, 16384L, 65536L, 131072L, 262144L, 524288L)
        else -> true
    }

    private fun cacheArchive(file: SaveChild, onCopy: () -> Unit, onBytes: (Long) -> Unit): File =
        reuseFirmwareArchive(File(context.cacheDir, "local-firmware"), file.uri, file.name.substringAfterLast('/'),
            FirmwareSourceVersion(file.size, file.modifiedMillis)) { temp ->
            onCopy()
            context.contentResolver.openInputStream(Uri.parse(file.uri))!!.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw CancellationException("BIOS copy cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        onBytes(count.toLong())
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
        }

    private companion object {
        const val LOCAL_FILE_CAP = 512L * 1024 * 1024
        const val LOCAL_PROBE_CAP = 64L * 1024 * 1024
        // Fits the player's 181 MiB PS2 dump set while bounding a BIOS collection scan.
        const val LOCAL_ARCHIVE_CAP = 256L * 1024 * 1024
        const val LOCAL_UNPACK_CAP = 512L * 1024 * 1024
    }
}
