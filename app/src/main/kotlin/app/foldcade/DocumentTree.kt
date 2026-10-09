package app.foldcade

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import app.foldcade.api.plugin.PluginException
import app.foldcade.localfolder.DOCUMENT_DIRECTORY_MIME
import app.foldcade.localfolder.FolderEntry

/**
 * Children of a folder the user picked with the system document-tree picker.
 * Document URIs only. This type does not read a filesystem path.
 */
internal class DocumentTree(
    private val resolver: ContentResolver,
    private val tree: Uri,
) {
    fun root(): FolderEntry {
        val documentId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (failure: IllegalArgumentException) {
            throw PluginException.Unavailable("Couldn’t reach the library", failure)
        }
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
        val (name, mime) = read(document)
        return FolderEntry(
            documentUri = document.toString(),
            displayName = name,
            mimeType = mime.ifBlank { DOCUMENT_DIRECTORY_MIME },
        )
    }

    fun children(parent: FolderEntry): List<FolderEntry> {
        val parentId = try {
            DocumentsContract.getDocumentId(Uri.parse(parent.documentUri))
        } catch (failure: IllegalArgumentException) {
            throw PluginException.Unavailable("Couldn’t reach the library", failure)
        }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cursor = resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        ) ?: throw PluginException.Unavailable("Couldn’t reach the library")
        cursor.use { rows ->
            val idColumn = rows.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = rows.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = rows.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val listed = ArrayList<FolderEntry>()
            while (rows.moveToNext()) {
                val id = rows.getString(idColumn) ?: continue
                val document = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                listed += FolderEntry(
                    documentUri = document.toString(),
                    displayName = rows.getString(nameColumn).orEmpty(),
                    mimeType = rows.getString(mimeColumn).orEmpty(),
                )
            }
            return listed
        }
    }

    private fun read(document: Uri): Pair<String, String> {
        val cursor = resolver.query(
            document,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        ) ?: throw PluginException.Unavailable("Couldn’t reach the library")
        cursor.use { rows ->
            if (!rows.moveToFirst()) return "" to DOCUMENT_DIRECTORY_MIME
            val name = rows.getString(0).orEmpty()
            val mime = rows.getString(1).orEmpty()
            return name to mime
        }
    }
}
