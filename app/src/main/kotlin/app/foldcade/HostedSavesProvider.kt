package app.foldcade

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * The save folders Foldcade hosts for players that cannot keep saves anywhere
 * Foldcade can read them, see [app.foldcade.api.plugin.HostedSaveFolder]: one
 * root per player, titled Foldcade, in Android's folder picker. The user picks it
 * once in the player's own settings, and the player then reads and writes its
 * saves here, through the grant the picker gives it.
 *
 * The files are in Foldcade's own storage, see [HostedFolders]. A folder holds
 * files and folders, which the player creates, writes, renames, and deletes; a
 * name never leaves its folder. A new file never takes a name in use, and a file
 * renamed onto another file's name replaces it. Document ids are paths, `foldcade:<folder>/<path>`,
 * as Android's own storage writes `primary:<path>`: DuckStation takes only a
 * folder whose ids are paths, and finds a file's folder by the id it starts with
 * and a `/`.
 */
class HostedSavesProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    private fun folders(): HostedFolders? = (context?.applicationContext as? FoldcadeApp)?.hostedFolders

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: ROOT_COLUMNS)
        val folders = folders() ?: return cursor
        for (folder in folders.list()) {
            cursor.newRow()
                .add(Root.COLUMN_ROOT_ID, folder.id)
                .add(Root.COLUMN_DOCUMENT_ID, HostedFolders.rootId(folder.id))
                .add(Root.COLUMN_TITLE, ROOT_TITLE)
                .add(Root.COLUMN_SUMMARY, folder.title)
                .add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_LOCAL_ONLY)
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val file = fileOf(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return MatrixCursor(projection ?: DOCUMENT_COLUMNS).also { it.addDocument(documentId, file) }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val folder = fileOf(parentDocumentId)
        if (!folder.isDirectory) throw FileNotFoundException(parentDocumentId)
        val cursor = MatrixCursor(projection ?: DOCUMENT_COLUMNS)
        folder.listFiles().orEmpty().sortedBy { it.name }.forEach { child ->
            cursor.addDocument(HostedFolders.childId(parentDocumentId, child.name), child)
        }
        cursor.setNotificationUri(context?.contentResolver, childrenUri(parentDocumentId))
        return cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val file = fileOf(documentId)
        if (file.isDirectory) throw FileNotFoundException(documentId)
        val flags = ParcelFileDescriptor.parseMode(mode)
        if (flags and ParcelFileDescriptor.MODE_CREATE == 0 && !file.isFile) throw FileNotFoundException(documentId)
        // Opening to write can empty the file, so it waits for a placement in progress.
        if (flags and ParcelFileDescriptor.MODE_WRITE_ONLY == 0) return ParcelFileDescriptor.open(file, flags)
        return synchronized(SaveFileLock) { ParcelFileDescriptor.open(file, flags) }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String = synchronized(SaveFileLock) {
        val parent = fileOf(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException(parentDocumentId)
        val file = uniqueFile(parent, plainName(displayName))
        val made = if (mimeType == Document.MIME_TYPE_DIR) file.mkdir() else file.createNewFile()
        if (!made) throw IllegalStateException("$displayName could not be made")
        changed(parentDocumentId)
        HostedFolders.childId(parentDocumentId, file.name)
    }

    override fun renameDocument(documentId: String, displayName: String): String = synchronized(SaveFileLock) {
        val file = fileOf(documentId)
        if (!file.exists() || HostedFolders.isRoot(documentId)) throw FileNotFoundException(documentId)
        val parentId = HostedFolders.parentId(documentId)
        val name = plainName(displayName)
        if (name == file.name) return@synchronized documentId
        val target = renameTarget(file, name)
        if (!file.renameTo(target)) throw IllegalStateException("$documentId could not be renamed")
        changed(parentId)
        HostedFolders.childId(parentId, target.name)
    }

    override fun deleteDocument(documentId: String) = synchronized(SaveFileLock) {
        val file = fileOf(documentId)
        if (HostedFolders.isRoot(documentId)) throw UnsupportedOperationException("A hosted folder stays")
        if (!file.exists()) throw FileNotFoundException(documentId)
        if (!file.deleteRecursively()) throw IllegalStateException("$documentId could not be deleted")
        changed(HostedFolders.parentId(documentId))
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        HostedFolders.isInside(parentDocumentId, documentId)

    override fun getDocumentType(documentId: String): String {
        val file = fileOf(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return if (file.isDirectory) Document.MIME_TYPE_DIR else OCTET_STREAM
    }

    /** The file [documentId] names: its root's folder, then the path below it. */
    private fun fileOf(documentId: String): File =
        folders()?.fileOf(documentId) ?: throw FileNotFoundException(documentId)

    private fun childrenUri(parentDocumentId: String) =
        DocumentsContract.buildChildDocumentsUri(authority(), parentDocumentId)

    private fun changed(parentDocumentId: String) {
        context?.contentResolver?.notifyChange(childrenUri(parentDocumentId), null, false)
    }

    private fun authority(): String = "${context?.packageName}$AUTHORITY_SUFFIX"

    private fun MatrixCursor.addDocument(documentId: String, file: File) {
        val folder = file.isDirectory
        val flags = if (folder) {
            Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        } else {
            Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        }
        newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(
                Document.COLUMN_DISPLAY_NAME,
                if (HostedFolders.isRoot(documentId)) folders()?.title(HostedFolders.folderOf(documentId)) ?: file.name else file.name,
            )
            .add(Document.COLUMN_MIME_TYPE, if (folder) Document.MIME_TYPE_DIR else OCTET_STREAM)
            .add(Document.COLUMN_SIZE, if (folder) null else file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(Document.COLUMN_FLAGS, flags)
    }

    internal companion object {
        /** After the application id. */
        const val AUTHORITY_SUFFIX = ".saves"
        const val ROOT_TITLE = "Foldcade"
        const val OCTET_STREAM = "application/octet-stream"
        val ROOT_COLUMNS = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_ICON,
            Root.COLUMN_FLAGS,
        )
        val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )

        /** [name] as one file in its folder, or a refusal. */
        fun plainName(name: String): String {
            if (name.isBlank() || name == "." || name == ".." || name.any { it == '/' || it == '\\' || it == '\u0000' }) {
                throw IllegalArgumentException("A name stays in its folder")
            }
            return name
        }

        /**
         * Where [file] goes when renamed [name]: onto a file of that name, which it replaces, as
         * rename(2) does, so a player that writes a save whole and renames it into place wins over
         * a save placed there a moment before; otherwise a name that is free, see [uniqueFile].
         */
        fun renameTarget(file: File, name: String): File {
            val taken = File(file.parentFile!!, name)
            return if (file.isFile && taken.isFile) taken else uniqueFile(file.parentFile!!, name)
        }

        /** [name] in [parent], or the first of `name (1).ext`, `name (2).ext` and on that is free. */
        fun uniqueFile(parent: File, name: String): File {
            val first = File(parent, name)
            if (!first.exists()) return first
            val dot = name.lastIndexOf('.')
            val (stem, extension) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
            return generateSequence(1) { it + 1 }.map { File(parent, "$stem ($it)$extension") }.first { !it.exists() }
        }
    }
}

/** A save folder Foldcade hosts: [id] is its root and first document id, [title] what it holds. */
internal data class HostedFolder(val id: String, val title: String)

/**
 * The folders [HostedSavesProvider] serves, each under [root]. A document id is
 * `foldcade:`, the folder's id, and the path below it, each name after a `/`:
 * `foldcade:duckstation` is the folder, `foldcade:duckstation/Game_1.mcd` a file in it.
 */
internal class HostedFolders(private val root: File, private val folders: () -> List<HostedFolder>) {
    fun list(): List<HostedFolder> = folders()

    fun title(id: String): String? = folders().firstOrNull { it.id == id }?.title

    /** Null for an id that names no folder, or would leave it. The folder is made when it is missing. */
    fun fileOf(documentId: String): File? {
        if (!documentId.startsWith(PREFIX)) return null
        val parts = documentId.substring(PREFIX.length).split('/')
        val id = parts.first()
        if (folders().none { it.id == id }) return null
        val below = parts.drop(1)
        if (below.any { it.isEmpty() || it == "." || it == ".." || it.contains('\\') }) return null
        val folder = File(root, id).also { it.mkdirs() }
        return below.fold(folder) { parent, part -> File(parent, part) }
    }

    companion object {
        private const val PREFIX = "foldcade:"

        fun rootId(folderId: String): String = "$PREFIX$folderId"

        fun folderOf(documentId: String): String = documentId.removePrefix(PREFIX).substringBefore('/')

        fun isRoot(documentId: String): Boolean = documentId.startsWith(PREFIX) && '/' !in documentId

        fun childId(parentId: String, name: String): String = "$parentId/$name"

        fun parentId(documentId: String): String = documentId.substringBeforeLast('/')

        /** True when [documentId] is [parentId] or below it. */
        fun isInside(parentId: String, documentId: String): Boolean =
            documentId == parentId || documentId.startsWith("$parentId/")
    }
}
