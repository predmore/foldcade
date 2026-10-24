package app.foldcade

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import app.foldcade.plugins.romm.RomFolderDocuments
import java.io.File
import java.io.FileNotFoundException

/**
 * A RomM download's folder as a document tree, so a player handed one game file
 * can list and open the files beside it: a Switch game's update and DLC, which
 * Eden loads from the game's folder when it starts the game, or a cue sheet's
 * tracks. Every RomM game is handed over this way: DuckStation opens only a
 * document in a tree.
 *
 * Nothing here can be browsed. There are no roots, so no file picker lists it,
 * and the manifest guards it with MANAGE_DOCUMENTS. A player reads it only through
 * the grant on one game's folder that the launch hands over, see [folderTreeOf].
 * Only finished, checked downloads are listed or opened, and only for reading.
 */
class RommFolderProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    private fun documents(): RomFolderDocuments =
        (context?.applicationContext as? FoldcadeApp)?.rommFolderDocuments() ?: RomFolderDocuments(emptyMap())

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: ROOT_COLUMNS)

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val file = documents().pathFor(documentId)?.toFile()
        val cursor = MatrixCursor(projection ?: DOCUMENT_COLUMNS)
        when {
            file == null -> throw FileNotFoundException(documentId)
            file.isDirectory -> cursor.addFolder(documentId, file)
            isServed(file) -> cursor.addFile(documentId, file)
            else -> throw FileNotFoundException(documentId)
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val folder = documents().pathFor(parentDocumentId)?.toFile()
        if (folder == null || !folder.isDirectory) throw FileNotFoundException(parentDocumentId)
        val cursor = MatrixCursor(projection ?: DOCUMENT_COLUMNS)
        folder.listFiles().orEmpty().filter(::isServed).sortedBy { it.name }.forEach { file ->
            cursor.addFile("$parentDocumentId/${file.name}", file)
        }
        return cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw UnsupportedOperationException("RomM downloads open only for reading")
        val file = documents().pathFor(documentId)?.toFile()
        if (file == null || !isServed(file)) throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        RomFolderDocuments.isInside(parentDocumentId, documentId)

    /** A download the RomM client finished and checked: it has its marker, and no partial beside it. */
    private fun isServed(file: File): Boolean =
        file.isFile &&
            SIDE_FILES.none { file.name.endsWith(it) } &&
            File(file.parentFile, file.name + VERIFIED).isFile &&
            !File(file.parentFile, file.name + PARTIAL).exists()

    private fun MatrixCursor.addFolder(documentId: String, folder: File) {
        newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, folder.name)
            .add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
            .add(Document.COLUMN_SIZE, null)
            .add(Document.COLUMN_LAST_MODIFIED, folder.lastModified())
            .add(Document.COLUMN_FLAGS, 0)
    }

    private fun MatrixCursor.addFile(documentId: String, file: File) {
        newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_MIME_TYPE, OCTET_STREAM)
            .add(Document.COLUMN_SIZE, file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(Document.COLUMN_FLAGS, 0)
    }

    private companion object {
        val ROOT_COLUMNS = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_DOCUMENT_ID)
        val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
        const val OCTET_STREAM = "application/octet-stream"

        /** The RomM client's names for a download's marker, unfinished bytes, and resume validator. */
        const val VERIFIED = ".verified"
        const val PARTIAL = ".partial"
        val SIDE_FILES = listOf(VERIFIED, PARTIAL, ".validator")
    }
}
