package app.foldcade

import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.SignalStrength
import app.foldcade.language.ConfigSavePath
import app.foldcade.language.FolderRef
import app.foldcade.plugins.retroarch.RetroArchConfigFile
import app.foldcade.plugins.retroarch.RetroArchPlayer
import app.foldcade.plugins.retroarch.INTERNAL_DATA
import app.foldcade.plugins.retroarch.SHARED_STORAGE
import app.foldcade.plugins.retroarch.absoluteRetroArchPath
import app.foldcade.plugins.retroarch.retroArchSavePath
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.cancellation.CancellationException

/** Reads the launched config and candidate saves through existing grants or our own RomM media directory. */
internal class RetroArchSaveFiles(
    grants: List<SaveFolderGrant>,
    private val appPackage: String,
    private val playerPackage: String,
    private val docs: SaveDocuments,
    private val rommMedia: Path?,
) {
    private data class Tree(val uri: String, val path: String, val restricted: Boolean)
    private val trees = grants.filter { it.read }.mapNotNull { grant ->
        val path = pathOf(grant.uri) ?: return@mapNotNull null
        Tree(grant.uri, path, grant.allFiles || URI(grant.uri).authority == SharedPaths.EXTERNAL_STORAGE_AUTHORITY)
    }.sortedByDescending { it.path.length }

    /** Resolve the identity of the bound directory without treating a provider's arbitrary IDs as paths. */
    fun pathOf(uri: String): String? = runCatching {
        val id = folderDocumentId(uri)
        when (URI(uri).authority) {
            RetroArchCoreReader.documentsAuthority(playerPackage) -> absoluteRetroArchPath(id)
            SharedPaths.EXTERNAL_STORAGE_AUTHORITY, appPackage + SharedStorageProvider.AUTHORITY_SUFFIX -> when (id) {
                "primary:" -> SHARED_STORAGE
                "home:" -> "$SHARED_STORAGE/Documents"
                else -> SharedPaths.externalStoragePath(storageFolderDocument(id))?.let(::absoluteRetroArchPath)
            }
            else -> null
        }
    }.getOrNull()

    fun hasConfigGrant(): Boolean = covering(RetroArchPlayer.configPath(playerPackage)).any()

    fun savePath(player: RetroArchPlayer, target: LaunchTarget.SharedFile, boundUri: String): ConfigSavePath {
        val relative = docs.displayName(target.uri)?.let { player.saveFileName(RetroArchPlayer.SAVE_SLOT, it) }
        val expected = pathOf(boundUri)?.let { path -> relative?.let { absoluteRetroArchPath("$path/$it") } }
        val effective = retroArchSavePath(RetroArchPlayer.configPath(playerPackage), target.path,
            player.saveCore(playerPackage).libraryName, ::config, ::isDirectory)
        val live = effective.path?.substringBeforeLast('/')?.let(::retroArchFolderRef)
        return ConfigSavePath(expected, effective.path, live, effective.strength == SignalStrength.Proves)
    }

    fun config(path: String): RetroArchConfigFile {
        if (foreignAppDirectory(path)) return RetroArchConfigFile.Unreadable
        return when (val read = read(path, CONFIG_LIMIT)) {
            is FileReading.Bytes -> try {
                RetroArchConfigFile.Read(Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(read.bytes)).toString())
            } catch (_: java.nio.charset.CharacterCodingException) { RetroArchConfigFile.Unreadable }
            FileReading.Missing -> RetroArchConfigFile.Missing
            FileReading.Unreadable -> RetroArchConfigFile.Unreadable
        }
    }

    fun isDirectory(path: String): Boolean = !foreignAppDirectory(path) &&
        (owned(path)?.let { Files.isDirectory(it, NOFOLLOW_LINKS) } == true ||
            covering(path).any { (tree, relative) -> readDocument { docs.children(tree.uri, relative) } != null })

    /** A grant to Foldcade cannot prove RetroArch can use another package's app data. */
    private fun foreignAppDirectory(path: String): Boolean {
        val root = listOf("$SHARED_STORAGE/Android/data/", "$INTERNAL_DATA/").firstOrNull(path::startsWith) ?: return false
        return path.removePrefix(root).substringBefore('/') != playerPackage
    }

    fun candidate(path: String): RetroArchCandidate {
        val directory = path.substringBeforeLast('/')
        val file = path.substringAfterLast('/')
        val root = retroArchFolderRef(directory)
        val folderUri = if (root.identity.startsWith("primary:")) storageFolderDocument(root.identity) else directory
        return RetroArchCandidate(
            root,
            SaveSpot(RetroArchPlayer.SAVE_SLOT, folderUri, file),
            read(path, SAVE_LIMIT),
        )
    }

    private fun read(path: String, limit: Int): FileReading {
        owned(path)?.let { file ->
            return try {
                val attrs = Files.readAttributes(file, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                if (!attrs.isRegularFile || attrs.size() > limit) FileReading.Unreadable
                else Files.newInputStream(file, NOFOLLOW_LINKS).use { input ->
                    readSaveStream(input, limit)?.let(FileReading::Bytes) ?: FileReading.Unreadable
                }
            } catch (_: NoSuchFileException) { FileReading.Missing }
            catch (_: java.io.IOException) { FileReading.Unreadable }
        }
        var missing = false
        var unreadable = false
        for ((tree, relative) in covering(path)) {
            when (val read = readTree(tree.uri, relative, limit)) {
                is FileReading.Bytes -> return read
                FileReading.Missing -> missing = true
                FileReading.Unreadable -> unreadable = true
            }
        }
        return if (missing && !unreadable) FileReading.Missing else FileReading.Unreadable
    }

    private fun covering(path: String) = trees.asSequence().filter { tree ->
        (path == tree.path || path.startsWith("${tree.path}/")) &&
            // Android can hide other apps' data from a broad tree, even when it can list the parent.
            (!tree.restricted || !inOtherAppData(path) || inOtherAppData(tree.path) && tree.path.count { it == '/' } >= 6)
    }.map { it to path.removePrefix(it.path).trimStart('/') }

    private fun inOtherAppData(path: String) = path.startsWith("$SHARED_STORAGE/Android/data/")

    private fun owned(path: String): Path? {
        val root = rommMedia ?: return null
        val file = Paths.get(path)
        if (!file.startsWith(root) || file == root) return null
        // An existing ancestor must stay under the app's media root; never follow a symlink to a save.
        val safe = runCatching {
            val realRoot = root.toRealPath()
            var ancestor = file.parent
            while (ancestor != root && !Files.exists(ancestor, NOFOLLOW_LINKS)) ancestor = ancestor.parent
            ancestor.toRealPath() == realRoot.resolve(root.relativize(ancestor))
        }.getOrDefault(false)
        return file.takeIf { safe }
    }

    private fun readTree(treeUri: String, relative: String, limit: Int): FileReading {
        if (relative.isEmpty()) return FileReading.Unreadable
        val parts = relative.split('/')
        var parent = treeUri
        for ((index, name) in parts.withIndex()) {
            val children = readDocument { docs.children(parent, "") } ?: return FileReading.Unreadable
            val matches = children.filter { it.name.equals(name, ignoreCase = true) }
            if (matches.isEmpty()) return FileReading.Missing
            val child = matches.singleOrNull()?.takeIf { it.name == name } ?: return FileReading.Unreadable
            val leaf = index == parts.lastIndex
            if (child.isFolder == leaf) return FileReading.Unreadable
            if (leaf) {
                if (child.size > limit) return FileReading.Unreadable
                return readDocument { docs.readBounded(child.uri, limit) }?.let(FileReading::Bytes) ?: FileReading.Unreadable
            }
            parent = child.uri
        }
        return FileReading.Unreadable
    }

    private inline fun <T> readDocument(read: () -> T): T? = try { read() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }

    private companion object {
        const val CONFIG_LIMIT = 256 * 1024
        const val SAVE_LIMIT = 256 * 1024 * 1024
    }
}

internal sealed interface FileReading {
    data object Unreadable : FileReading
    data object Missing : FileReading
    data class Bytes(val bytes: ByteArray) : FileReading
}

internal data class RetroArchCandidate(val root: FolderRef, val spot: SaveSpot, val read: FileReading)

internal fun retroArchFolderRef(directory: String): FolderRef {
    val identity = if (directory == SHARED_STORAGE) "primary:" else storageIdentity(directory)
    return FolderRef(identity, identity.removePrefix("primary:").ifEmpty { "/" })
}

internal fun retroArchCandidatePath(root: FolderRef, file: String): String? {
    val directory = if (root.identity.startsWith("primary:")) "$SHARED_STORAGE/${root.identity.removePrefix("primary:")}" else root.identity
    return absoluteRetroArchPath("$directory/$file")
}
