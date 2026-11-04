package app.foldcade

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import app.foldcade.language.FoundFolderCopy
import app.foldcade.localfolder.platformFromFolderName
import java.io.File
import java.nio.file.Files

/** A mounted storage volume. [id] is how document ids name it: `primary`, or a removable volume's id such as `1A2B-3C4D`. */
internal data class VolumeDir(val id: String, val dir: File)

/**
 * A folder of system folders on one volume, such as `ROMs` or `Emulation/roms`.
 * [systems] are the platforms its folders are named for, A to Z.
 */
internal data class RomRoot(val volume: String, val path: String, val systems: List<String>) {
    val documentId: String get() = "$volume:$path"
}

/**
 * Where frontends keep ROMs, at the top of each volume and in any case: `ROMs`, ES-DE's
 * default on Android, `roms`, Batocera's, and `Emulation/roms`, EmuDeck's. A folder counts
 * only when it holds a system folder ([platformFromFolderName]), so a ROMs folder that holds
 * only Moonlight's `moonlight` shortcuts is not one. Symbolic links are not followed.
 *
 * [list] lists a folder's children, or null when it cannot. Android shows another app's
 * folders, though not its files, without any storage access, so this runs before the player
 * allows all-files access too. Reads the disk: never on the main thread.
 */
internal fun findRomRoots(volumes: List<VolumeDir>, list: (File) -> List<File>? = { it.listFiles()?.toList() }): List<RomRoot> {
    fun folders(dir: File) = list(dir).orEmpty().filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) }
    return volumes.flatMap { volume ->
        val top = folders(volume.dir)
        val candidates = top.filter { it.name.equals(ROMS, ignoreCase = true) } +
            top.filter { it.name.equals(EMULATION, ignoreCase = true) }.flatMap { emulation ->
                folders(emulation).filter { it.name.equals(ROMS, ignoreCase = true) }
            }
        candidates.mapNotNull { dir ->
            val systems = folders(dir).mapNotNull { platformFromFolderName(it.name)?.name }.distinct().sortedBy { it.lowercase() }
            if (systems.isEmpty()) return@mapNotNull null
            RomRoot(volume.id, dir.relativeTo(volume.dir).invariantSeparatorsPath, systems)
        }
    }.distinctBy { it.documentId.lowercase() }
}

/**
 * The roots the library reads: every root in [roots] that no folder the player picked
 * covers or sits in, since that pick already lists those games, and that overlaps nothing
 * the player removed: a removed found folder, or a removed pick such as `ROMs/3ds`, whose
 * games would otherwise come back through the folder around it.
 * [picked] and [removed] are document ids, `<volume>:<path>`.
 */
internal fun usableRomRoots(roots: List<RomRoot>, picked: List<String>, removed: Set<String>): List<RomRoot> =
    roots.filter { romRootUsable(it.documentId, picked, removed) }

internal fun romRootUsable(id: String, picked: List<String>, removed: Set<String>): Boolean =
    (picked + removed).none { containsFolder(it, id) || containsFolder(id, it) }

/** The document ids of picked folders on Android's own storage, which a found root may overlap. */
internal fun pickedStorageFolders(trees: List<String>): List<String> = trees.mapNotNull { tree ->
    runCatching {
        if (java.net.URI(tree).rawAuthority != SharedPaths.EXTERNAL_STORAGE_AUTHORITY) null else folderTreeId(tree)
    }.getOrNull()
}

/** The tree a found root is read through, on [LibraryFilesProvider] at [authority]. */
internal fun romRootTree(authority: String, documentId: String): String = treeUri(authority, documentId)

/** The tile's name: the folder's own on this device's storage, and the card's on a removable one. */
internal fun romRootTileLabel(documentId: String): String =
    if (documentId.substringBefore(':') == PRIMARY_VOLUME) documentId.substringAfter(':').substringAfterLast('/')
    else FoundFolderCopy.sdCard

/** Where the root is, for its page and its notice: `Emulation/roms`, or `SD card · roms`. */
internal fun romRootPlace(documentId: String): String {
    val path = documentId.substringAfter(':')
    return if (documentId.substringBefore(':') == PRIMARY_VOLUME) path else FoundFolderCopy.onCard(path)
}

/** The mounted volumes, primary first. A volume without a directory or an id is left out. */
internal fun mountedVolumes(context: Context): List<VolumeDir> =
    context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { volume ->
        if (volume.state != Environment.MEDIA_MOUNTED && volume.state != Environment.MEDIA_MOUNTED_READ_ONLY) return@mapNotNull null
        val dir = volume.directory ?: return@mapNotNull null
        val id = if (volume.isPrimary) PRIMARY_VOLUME else volume.uuid ?: return@mapNotNull null
        VolumeDir(id, dir)
    }.sortedBy { it.id != PRIMARY_VOLUME }

internal const val PRIMARY_VOLUME = "primary"
private const val ROMS = "roms"
private const val EMULATION = "emulation"
