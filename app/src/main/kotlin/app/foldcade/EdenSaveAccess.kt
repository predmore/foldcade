package app.foldcade

import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.language.SaveProtectionCopy
import app.foldcade.plugins.eden.EDEN_PACKAGES
import app.foldcade.plugins.eden.EdenPlayer
import app.foldcade.plugins.eden.EdenProfile
import app.foldcade.plugins.eden.readEdenProfile
import app.foldcade.plugins.eden.readEdenStorageConfig
import java.net.URI
import java.util.Locale

internal data class EdenSaveDirectory(val identity: String, val uri: String, val prefix: String = "")

internal sealed interface EdenSaveAccess {
    data object Unknown : EdenSaveAccess
    data class NeedsGrant(val documentId: String) : EdenSaveAccess
    data class Ready(
        val dataUri: String,
        val save: EdenSaveDirectory,
        val nand: EdenSaveDirectory,
        val profile: EdenProfile,
        val configured: Boolean,
    ) : EdenSaveAccess {
        fun session(): EdenSaveSession? = when (profile) {
            is EdenProfile.User -> EdenSaveSession(save.identity, nand.identity, profile.uuid)
            EdenProfile.Missing -> EdenSaveSession(save.identity, nand.identity, null)
            EdenProfile.Unreadable -> null
        }
    }
}

/** A null UUID here means the profile database was absent at prepare. */
internal data class EdenSaveSession(val saveRoot: String, val nandRoot: String, val user: String?)

internal data class EdenReturnPlan(val spots: List<SaveSpot>, val backupOnly: Boolean = false, val change: String? = null)

internal fun planEdenReturn(saves: PlayerSaves, pending: LibraryReturn, player: EdenPlayer): EdenReturnPlan {
    val frozen = pending.edenSession ?: return EdenReturnPlan(pending.spots, backupOnly = true)
    val access = saves.edenSaveAccess(player, pending.game)
    val current = (access as? EdenSaveAccess.Ready)?.session() ?: return EdenReturnPlan(pending.spots, backupOnly = true)
    if (frozen.user != null && current.user != frozen.user) {
        return EdenReturnPlan(pending.spots, backupOnly = true, change = SaveProtectionCopy.edenUserChanged)
    }
    if (frozen.saveRoot != current.saveRoot || frozen.nandRoot != current.nandRoot) {
        return EdenReturnPlan(pending.spots, backupOnly = true, change = SaveProtectionCopy.edenLocationChanged)
    }
    val fresh = pending.targetUri?.let { saves.spots(player, pending.game, LaunchTarget.ContentUri(it), eden = access) }.orEmpty()
    val placed = pending.spots.singleOrNull()
    val live = fresh.singleOrNull()
    if (placed != null && live == null) return EdenReturnPlan(pending.spots, backupOnly = true)
    if (placed != null && live != null && placed.folders != live.folders) {
        return EdenReturnPlan(listOf(placed, live), backupOnly = true, change = SaveProtectionCopy.edenLocationChanged)
    }
    return EdenReturnPlan(fresh, backupOnly = pending.unprotectedSession || pending.preserveAsBackup)
}

internal fun resolveEdenSaveAccess(
    dataUri: String,
    access: (String) -> SaveFolderAccess,
    granted: (String) -> String?,
): EdenSaveAccess {
    val authority = URI(dataUri).authority
    val documentId = folderDocumentId(dataUri)
    val pkg = EDEN_PACKAGES.firstOrNull { edenDataRoot(it, authority, documentId) } ?: return EdenSaveAccess.Unknown
    val dataId = "primary:Android/data/$pkg/files"
    val config = readEdenStorageConfig(access(dataUri)) ?: return EdenSaveAccess.Unknown
    val nandId = config.nandDirectory?.let { edenStorageDocumentId(it) ?: return EdenSaveAccess.Unknown } ?: "$dataId/nand"
    val saveId = config.saveDirectory?.let { edenStorageDocumentId(it) ?: return EdenSaveAccess.Unknown } ?: nandId

    fun directory(id: String): EdenSaveDirectory? = when {
        id == dataId -> EdenSaveDirectory(id, dataUri)
        id.startsWith("$dataId/") -> EdenSaveDirectory(id, dataUri, id.removePrefix("$dataId/"))
        else -> granted(id)?.let { EdenSaveDirectory(id, it) }
    }

    val save = directory(saveId) ?: return EdenSaveAccess.NeedsGrant(saveId)
    val nand = if (nandId == saveId) save else directory(nandId) ?: return EdenSaveAccess.NeedsGrant(nandId)
    // Eden only adopts existing custom directories. Do not create one as a side effect of placing a save.
    for (dir in setOf(save, nand)) {
        if (dir.identity != "$dataId/nand" && access(dir.uri).list(dir.prefix) == null) return EdenSaveAccess.Unknown
    }
    // A profile read failure does not erase positive evidence about the save folder.
    val profile = try { readEdenProfile(access(nand.uri), nand.prefix, config.currentUser) }
        catch (_: SaveNotPlaced) { EdenProfile.Unreadable }
    return EdenSaveAccess.Ready(dataUri, save, nand, profile, saveId != "$dataId/nand")
}

internal fun edenExternalGrant(documentId: String, grants: List<SaveFolderGrant>): String? = grants.asSequence()
    .filter { it.read && it.write && URI(it.uri).authority == SharedPaths.EXTERNAL_STORAGE_AUTHORITY }
    .filter { containsFolder(folderTreeId(it.uri), documentId) }
    .maxByOrNull { folderTreeId(it.uri).length }
    ?.uri?.let { if (folderTreeId(it) == documentId) folderGrantUri(it) else folderDocumentUri(it, documentId) }

/** Android's picker supplies filesystem paths. Normalize aliases before testing grant ancestry. */
internal fun edenStorageDocumentId(path: String): String? {
    val parts = path.split('/').filter(String::isNotEmpty)
    if (!path.startsWith('/') || parts.any { it == "." || it == ".." || '\\' in it }) return null
    val primaryPrefix = listOf(listOf("storage", "emulated", "0"), listOf("storage", "self", "primary"),
        listOf("sdcard"), listOf("mnt", "sdcard")).firstOrNull { parts.take(it.size) == it }
    if (primaryPrefix != null) return "primary:${parts.drop(primaryPrefix.size).joinToString("/")}"
    val volumeIndex = when {
        parts.firstOrNull() == "storage" -> 1
        parts.take(2) == listOf("mnt", "media_rw") -> 2
        else -> return null
    }
    val volume = parts.getOrNull(volumeIndex)?.takeIf { it.matches(Regex("[0-9a-fA-F]{4}-[0-9a-fA-F]{4}")) } ?: return null
    return "${volume.uppercase(Locale.ROOT)}:${parts.drop(volumeIndex + 1).joinToString("/")}"
}
