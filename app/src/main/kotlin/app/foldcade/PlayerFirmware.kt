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
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

internal data class FirmwareResult(val written: List<String>, val missing: List<String>, val needsFolder: Boolean, val place: String)

/** Launch-only I/O. Every source is the player's storage or a checked RomM download. */
internal class PlayerFirmware(private val context: Context, private val saves: PlayerSaves) {
    private val docs = AndroidSaveDocuments(context.contentResolver)
    private val android = AndroidSaveFolderFinder(context)

    fun prepare(player: Player, game: Game, target: LaunchTarget, picked: String?, libraries: List<String>): FirmwareResult {
        val grants = android.grants(listOfNotNull(player as? SaveFolderHolder))
        val finder = android.finder(player, grants)
        val retro = player is RetroArchPlayer
        val melon = player is app.foldcade.plugins.melonds.MelonDsPlayer
        val systemFolder = retro || melon
        val folder = if (systemFolder) {
            finder.reachable("primary:RetroArch/system")?.takeIf { android.isDirectory(it) }
                ?: finder.reachable("primary:RetroArch")?.let { root ->
                    if (docs.createFolder(root, "system")) docs.children(root, "")?.firstOrNull { it.isFolder && it.name == "system" }?.uri else null
                }
        } else picked
        val place = if (systemFolder) "RetroArch/system" else when (player.id) {
            "duckstation" -> (picked?.let(::folderPathLabel) ?: "Android/data/com.github.stenzek.duckstation/files") + "/bios"
            "armsx2" -> (picked?.let(::folderPathLabel) ?: "Android/data/com.armsx2/files") + "/bios"
            "aps3e" -> "aPS3e/firmware"
            else -> player.displayName
        }
        // An uninterested player must not scan storage on launch.
        if (player.firmwareFileName("bios.bin") == null && player.firmwareFileName("PS3UPDAT.PUP") == null) {
            return FirmwareResult(emptyList(), emptyList(), false, place)
        }
        val empty = EmptyFirmwareFolder
        val needed = if (folder == null) player.missingFirmware(game, empty) else saves.missingFirmware(player, game, folder)
        val sources = (target as? LaunchTarget.ContentUri)?.firmware.orEmpty()
        // Do not touch the player's existing valid BIOS or spend time searching for another.
        // Optional core firmware is still placed when supplied by RomM or local storage.
        val local = if (needed.isNotEmpty() || systemFolder || player.id == "aps3e") localFirmware(player, libraries, grants) else emptyList()
        if (folder == null) {
            return FirmwareResult(emptyList(), needed, needed.isNotEmpty() || usable(player, sources + local), place)
        }
        val offered = sources + local
        val uri = (target as? LaunchTarget.ContentUri)?.uri ?: "content://firmware/launch"
        val written = saves.placeFirmware(player, game, LaunchTarget.ContentUri(uri, firmware = offered), folder)
        return FirmwareResult(written, saves.missingFirmware(player, game, folder), false, place)
    }

    private fun usable(player: Player, files: List<FirmwareBytes>): Boolean = files.any { file ->
        val offer = app.foldcade.api.plugin.FirmwareOffer(file.name, docs.head(file.contentUri, player.firmwareHeaderSize) ?: byteArrayOf())
        player.chooseFirmware(listOf(offer)).isNotEmpty()
    }

    private fun localFirmware(player: Player, libraries: List<String>, grants: List<SaveFolderGrant>): List<FirmwareBytes> {
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
                if (child.isFolder) visit(child.uri, depth + 1, name)
                else {
                    val offeredName = if (player.firmwareKeepsFolders) name else child.name
                    if (player.firmwareFileName(offeredName) != null && child.size <= LOCAL_FILE_CAP) files.putIfAbsent(child.uri, child.copy(name = offeredName))
                }
            }
        }
        roots.forEach { visit(it, 0) }
        val offered = files.values.flatMap { file ->
            val plain = FirmwareBytes(file.name, file.uri)
            // Detect ZIP by bytes as RomM does, not just by its extension.
            if (docs.head(file.uri, 4)?.contentEquals(byteArrayOf(0x50, 0x4b, 3, 4)) != true) listOf(plain)
            else try {
                val zip = cacheArchive(file)
                listOf(plain) + unpackLocalFirmwareZip(zip.toPath(), keepFolders = player.firmwareKeepsFolders) { player.firmwareFileName(it) != null }
                    .map { (name, path) -> FirmwareBytes(name, FileProvider.getUriForFile(context, "${context.packageName}.romm.cache", path.toFile()).toString()) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() }
        }
        // Probe one image at a time. An unrelated BIOS collection must not turn
        // into hundreds of retained multi-megabyte headers on a game launch.
        var headers = 0L
        val destinations = HashSet<String>()
        return offered.filter { file ->
            if (headers >= 64L * 1024 * 1024) return@filter false
            val header = docs.head(file.contentUri, player.firmwareHeaderSize) ?: return@filter false
            val offer = app.foldcade.api.plugin.FirmwareOffer(file.name, header)
            if (player.chooseFirmware(listOf(offer)).isEmpty()) return@filter false
            val destination = player.firmwareDestination(offer) ?: return@filter false
            if (!destinations.add(destination)) return@filter false
            headers += header.size
            true
        }
    }

    private fun cacheArchive(file: SaveChild): File {
        val key = MessageDigest.getInstance("SHA-256").digest(file.uri.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val dir = File(context.cacheDir, "local-firmware/$key").apply { mkdirs() }
        val target = File(dir, file.name.substringAfterLast('/'))
        val temp = File(dir, ".archive.partial")
        try {
            context.contentResolver.openInputStream(Uri.parse(file.uri))!!.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw CancellationException("BIOS copy cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= LOCAL_FILE_CAP)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            check(temp.renameTo(target))
            return target
        } finally { temp.delete() }
    }

    private companion object { const val LOCAL_FILE_CAP = 512L * 1024 * 1024 }
}

private object EmptyFirmwareFolder : app.foldcade.api.plugin.SaveFolderAccess {
    override fun list(path: String) = emptyList<app.foldcade.api.plugin.SaveFolderEntry>()
    override fun read(path: String, limit: Int): ByteArray? = null
    override fun read(path: String, offset: Long, length: Int): ByteArray? = null
    override fun write(path: String, bytes: ByteArray) = false
    override fun createFolder(path: String) = false
    override fun moveToBackup(path: String) = false
}
