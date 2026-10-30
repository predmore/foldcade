package app.foldcade

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/** Foldcade's optional shared-storage tree. No picker roots and no access for another uid. */
class SharedStorageProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    private fun storage(): SharedStorage {
        requireStorageAccess(Binder.getCallingUid(), Process.myUid(), Environment.isExternalStorageManager())
        return SharedStorage(Environment.getExternalStorageDirectory())
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        storage()
        return MatrixCursor(projection ?: HostedSavesProvider.ROOT_COLUMNS)
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val file = storage().fileOf(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return MatrixCursor(projection ?: HostedSavesProvider.DOCUMENT_COLUMNS).also { it.addDocument(documentId, file) }
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val storage = storage()
        val parent = storage.fileOf(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException(parentDocumentId)
        val children = parent.listFiles() ?: throw FileNotFoundException(parentDocumentId)
        val cursor = MatrixCursor(projection ?: HostedSavesProvider.DOCUMENT_COLUMNS)
        children.sortedBy { it.name }.forEach { child ->
            val id = try {
                storage.childId(parentDocumentId, child.name).also { storage.fileOf(it) }
            } catch (_: SecurityException) {
                return@forEach
            } catch (_: IllegalArgumentException) {
                return@forEach
            }
            cursor.addDocument(id, child)
        }
        cursor.setNotificationUri(context?.contentResolver, childrenUri(parentDocumentId))
        return cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val flags = ParcelFileDescriptor.parseMode(mode)
        fun open(): ParcelFileDescriptor {
            val file = storage().fileOf(documentId)
            if (!file.isFile) throw FileNotFoundException(documentId)
            return ParcelFileDescriptor.open(file, flags)
        }
        // Opening to write can empty the file, so it waits for a placement in progress.
        return if (flags and ParcelFileDescriptor.MODE_WRITE_ONLY == 0) open() else synchronized(SaveFileLock) { open() }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String = synchronized(SaveFileLock) {
        val storage = storage()
        val parent = storage.fileOf(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException(parentDocumentId)
        val file = HostedSavesProvider.uniqueFile(parent, HostedSavesProvider.plainName(displayName))
        val id = storage.childId(parentDocumentId, file.name)
        storage.fileOf(id)
        val made = if (mimeType == Document.MIME_TYPE_DIR) file.mkdir() else file.createNewFile()
        if (!made) throw IllegalStateException("$displayName could not be made")
        changed(parentDocumentId)
        id
    }

    override fun deleteDocument(documentId: String) = synchronized(SaveFileLock) {
        val storage = storage()
        storage.requireMutable(documentId)
        val file = storage.fileOf(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        // Only files and empty folders: deletion never walks into app data or follows a link.
        if (!file.delete()) throw IllegalStateException("$documentId could not be deleted")
        changed(storage.parentId(documentId))
    }

    override fun renameDocument(documentId: String, displayName: String): String = synchronized(SaveFileLock) {
        val storage = storage()
        storage.requireMutable(documentId)
        val file = storage.fileOf(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        val name = HostedSavesProvider.plainName(displayName)
        if (name == file.name) return@synchronized documentId
        val parentId = storage.parentId(documentId)
        val id = storage.childId(parentId, name)
        val target = storage.fileOf(id)
        if (target.exists() || !file.renameTo(target)) throw IllegalStateException("$documentId could not be renamed")
        changed(parentId)
        id
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = storage().isChild(parentDocumentId, documentId)

    override fun getDocumentType(documentId: String): String {
        val file = storage().fileOf(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return if (file.isDirectory) Document.MIME_TYPE_DIR else HostedSavesProvider.OCTET_STREAM
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        storage()
        return super.call(method, arg, extras)
    }

    override fun canonicalize(uri: Uri): Uri? {
        storage().fileOf(folderDocumentId(uri.toString()))
        return super.canonicalize(uri)
    }

    private fun childrenUri(documentId: String): Uri = DocumentsContract.buildChildDocumentsUri("${context?.packageName}$AUTHORITY_SUFFIX", documentId)

    private fun changed(parentDocumentId: String) {
        context?.contentResolver?.notifyChange(childrenUri(parentDocumentId), null, false)
    }

    private fun MatrixCursor.addDocument(documentId: String, file: File) {
        val folder = file.isDirectory
        val mutable = documentId != "primary:" && documentId != "primary:Android"
        val flags = (if (folder) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE) or
            (if (mutable) Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME else 0)
        newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_MIME_TYPE, if (folder) Document.MIME_TYPE_DIR else HostedSavesProvider.OCTET_STREAM)
            .add(Document.COLUMN_SIZE, if (folder) null else file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(Document.COLUMN_FLAGS, flags)
    }

    internal companion object {
        const val AUTHORITY_SUFFIX = ".storage"
        fun tree(packageName: String): String = "content://$packageName$AUTHORITY_SUFFIX/tree/primary%3A"
    }
}
