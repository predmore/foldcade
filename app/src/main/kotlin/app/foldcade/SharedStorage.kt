package app.foldcade

import java.io.File
import java.nio.file.Files

/** Shared storage stays private to Foldcade and needs Android's current all-files access. */
internal fun requireStorageAccess(callingUid: Int, ownUid: Int, granted: Boolean) {
    if (callingUid != ownUid || !granted) throw SecurityException("Shared storage is for Foldcade with all-files access")
}

/**
 * Document ids of one volume, `<volume>:<path>` as Android's external storage provider
 * writes them, stay on that volume, outside app data, without following links. [volume]
 * is `primary`, or a removable volume's id such as `1A2B-3C4D`.
 */
internal class SharedStorage(root: File, private val volume: String = PRIMARY) {
    private val root = root.canonicalFile
    private val prefix = "$volume:"

    fun fileOf(documentId: String): File {
        if (!documentId.startsWith(prefix)) throw SecurityException("Only $volume storage")
        val path = documentId.removePrefix(prefix)
        val parts = if (path.isEmpty()) emptyList() else path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.any { char -> char == '\\' || char == '\u0000' } }) {
            throw SecurityException("A document stays on the volume")
        }
        if (parts.size >= 2 && parts[0].equals("Android", ignoreCase = true) &&
            (parts[1].equals("data", ignoreCase = true) || parts[1].equals("obb", ignoreCase = true))) {
            throw SecurityException("App data needs its own folder grant")
        }
        var file = root
        for (part in parts) {
            file = File(file, part)
            if (Files.isSymbolicLink(file.toPath())) throw SecurityException("A document cannot follow a link")
        }
        if (file.canonicalFile != file || !file.toPath().startsWith(root.toPath())) {
            throw SecurityException("A document stays on the volume")
        }
        return file
    }

    fun isChild(parentId: String, documentId: String): Boolean {
        val parent = fileOf(parentId).toPath()
        val child = fileOf(documentId).toPath()
        return child != parent && child.startsWith(parent)
    }

    fun childId(parentId: String, name: String): String {
        HostedSavesProvider.plainName(name)
        return (parentId.trimEnd('/') + if (parentId == prefix) "" else "/") + name
    }

    fun parentId(documentId: String): String = documentId.substringBeforeLast('/', prefix)

    /** The volume and Android's app-data parent cannot be moved or removed. */
    fun requireMutable(documentId: String) {
        val file = fileOf(documentId)
        if (file == root || file.parentFile == root && file.name.equals("Android", ignoreCase = true)) {
            throw SecurityException("The storage root stays")
        }
    }
}

private const val PRIMARY = "primary"
