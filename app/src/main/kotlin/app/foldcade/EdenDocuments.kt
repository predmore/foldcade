package app.foldcade

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.foldcade.plugins.eden.EDEN_PACKAGES
import java.io.InputStream

/** Eden's documents provider: authority `<package>.user`, root id `root`. */
internal object EdenFolders {
    fun authority(packageName: String): String = "$packageName.user"

    /** Where the picker opens: the root Eden publishes, its data directory. */
    fun root(packageName: String): Uri = DocumentsContract.buildRootUri(authority(packageName), ROOT)

    /** The Eden package a granted tree belongs to, or null for any other folder. */
    fun packageOf(tree: Uri): String? = EDEN_PACKAGES.firstOrNull { tree.authority == authority(it) }

    /** True when the tree is Eden's data directory itself, not a folder inside it. */
    fun isRoot(tree: Uri): Boolean {
        val id = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return id == ROOT || id == "$ROOT/"
    }

    private const val ROOT = "root"
}

/**
 * The folder the user chose for [packageName], while Foldcade still holds
 * a persistable read and write grant on Eden's own root.
 */
internal class EdenFolderReader(
    private val context: Context,
    private val chosenFolder: (packageName: String) -> String?,
) {
    fun granted(packageName: String): Uri? {
        val chosen = chosenFolder(packageName)?.let(Uri::parse) ?: return null
        if (chosen.authority != EdenFolders.authority(packageName) || !EdenFolders.isRoot(chosen)) return null
        val held = context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == chosen && permission.isReadPermission && permission.isWritePermission
        }
        return chosen.takeIf { held }
    }
}

/**
 * Reads and writes one granted Eden folder. Paths stay inside `keys/` and
 * `nand/system/Contents/`, plus the two parent directories those need created.
 */
internal class EdenDocumentTree(
    private val resolver: ContentResolver,
    private val tree: Uri,
) : EdenTree {
    private val rootId: String = DocumentsContract.getTreeDocumentId(tree)

    override fun exists(relativePath: String): Boolean = find(relativePath) != null

    override fun size(relativePath: String): Long? = find(relativePath)?.takeIf { !it.directory }?.size

    override fun list(relativeDir: String): List<EdenNode> {
        val dir = find(relativeDir) ?: return emptyList()
        return children(dir.id).map { EdenNode(it.name, it.directory, it.size) }
    }

    override fun read(relativePath: String): ByteArray {
        require(allowedFile(relativePath))
        val node = find(relativePath) ?: error("missing $relativePath")
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, node.id)
        return resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("could not read $relativePath")
    }

    override fun write(relativePath: String, bytes: ByteArray) {
        writeOpening(relativePath).use { it.write(bytes) }
    }

    override fun writeStream(relativePath: String, source: InputStream): Long {
        val output = writeOpening(relativePath)
        output.use { stream ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = source.read(buffer)
                if (read < 0) return total
                stream.write(buffer, 0, read)
                total += read
            }
        }
    }

    override fun delete(relativePath: String) {
        require(allowedFile(relativePath))
        val node = find(relativePath) ?: return
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, node.id)
        DocumentsContract.deleteDocument(resolver, uri)
    }

    override fun mkdir(relativePath: String) {
        require(allowedDir(relativePath))
        if (exists(relativePath)) return
        val parentPath = relativePath.substringBeforeLast('/', "")
        val name = relativePath.substringAfterLast('/')
        if (parentPath.isNotEmpty()) mkdir(parentPath)
        val parent = find(parentPath) ?: error("missing $parentPath")
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parent.id)
        DocumentsContract.createDocument(resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: error("could not create $relativePath")
    }

    override fun rename(path: String, newName: String) {
        require(allowedFile(path))
        val parent = path.substringBeforeLast('/', "")
        val destination = if (parent.isEmpty()) newName else "$parent/$newName"
        require('/' !in newName && '\\' !in newName && allowedFile(destination))
        val node = find(path) ?: error("missing $path")
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, node.id)
        // Eden's provider renames with File.renameTo in the same directory.
        DocumentsContract.renameDocument(resolver, uri, newName) ?: error("could not rename $path")
    }

    private fun writeOpening(relativePath: String): java.io.OutputStream {
        require(allowedFile(relativePath))
        val parentPath = relativePath.substringBeforeLast('/', "")
        val name = relativePath.substringAfterLast('/')
        if (parentPath.isNotEmpty()) mkdir(parentPath)
        if (exists(relativePath)) delete(relativePath)
        val parent = find(parentPath) ?: error("missing $parentPath")
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parent.id)
        val created = DocumentsContract.createDocument(resolver, parentUri, "application/octet-stream", name)
            ?: error("could not create $relativePath")
        return resolver.openOutputStream(created) ?: error("could not write $relativePath")
    }

    private fun find(relativePath: String): Node? {
        val root = Node(rootId, "", directory = true, size = 0L)
        if (relativePath.isEmpty()) return root
        var current = root
        for (segment in relativePath.split('/')) {
            if (segment.isEmpty() || segment == "." || segment == "..") return null
            current = children(current.id).firstOrNull { it.name == segment } ?: return null
        }
        return current
    }

    private fun children(parentId: String): List<Node> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        return resolver.query(uri, COLUMNS, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val directory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                    add(Node(id, name, directory, size))
                }
            }
        } ?: throw IllegalStateException("Eden's folder could not be listed")
    }

    private class Node(val id: String, val name: String, val directory: Boolean, val size: Long)

    private companion object {
        val COLUMNS: Array<String> = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        fun allowedFile(path: String): Boolean {
            if (path == "keys/prod.keys" || path == "keys/title.keys") return true
            if (path == "keys/prod.keys.foldcade-partial" || path == "keys/title.keys.foldcade-partial") return true
            if (path == "nand/system/Contents/foldcade-firmware.partial") return true
            val prefix = "nand/system/Contents/registered/"
            if (!path.startsWith(prefix)) return false
            val name = path.removePrefix(prefix)
            return name.isNotEmpty() && '/' !in name && '\\' !in name && ".." !in name && name.endsWith(".nca", ignoreCase = true)
        }

        fun allowedDir(path: String): Boolean = path in ALLOWED_DIRS

        val ALLOWED_DIRS: Set<String> = setOf(
            "keys",
            "nand",
            "nand/system",
            "nand/system/Contents",
            "nand/system/Contents/registered",
        )
    }
}
