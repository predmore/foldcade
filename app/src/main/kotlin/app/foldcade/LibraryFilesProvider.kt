package app.foldcade

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * The ROM folders Foldcade found by itself ([findRomRoots]), as document trees, so their
 * games are documents like those in a folder the player picked: the library scans them the
 * same way, and a player opens a game through the read grant its launch carries, with the
 * files beside it for a cue sheet's tracks or a playlist's discs ([sheetTreeOf]).
 *
 * Document ids are written as Android's external storage provider writes them,
 * `<volume>:<path>`, so [SharedPaths] knows each one's path for a player that opens games
 * by path. Only documents inside a root the library reads now are served, only for reading,
 * and only while Foldcade holds all-files access. There are no roots, so no picker lists it,
 * and the manifest guards it with MANAGE_DOCUMENTS: another app reads it only through a grant.
 * App data, traversal and symbolic links are refused, as in [SharedStorage].
 */
class LibraryFilesProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    @Volatile
    private var volumes: List<VolumeDir> = emptyList()

    private fun fileOf(documentId: String): File {
        if (!Environment.isExternalStorageManager()) throw SecurityException("Found folders need all-files access")
        // Read as stored, not from the application: a player can resume into a cold Foldcade whose
        // first look for folders has not finished, and its grant must still open the game.
        val prefs = context?.getSharedPreferences(SessionStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val roots = prefs?.let(SessionStore::foundRoots).orEmpty()
        if (roots.none { containsFolder(it, documentId) }) throw FileNotFoundException(documentId)
        val volume = documentId.substringBefore(':')
        val dir = volumeDir(volume) ?: throw FileNotFoundException(documentId)
        return SharedStorage(dir, volume).fileOf(documentId)
    }

    /** A card can be put in or taken out while Foldcade runs, so a volume not seen yet asks again. */
    private fun volumeDir(id: String): File? {
        volumes.firstOrNull { it.id == id }?.let { return it.dir }
        val context = context ?: return null
        volumes = mountedVolumes(context)
        return volumes.firstOrNull { it.id == id }?.dir
    }

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: HostedSavesProvider.ROOT_COLUMNS)

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val file = fileOf(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return MatrixCursor(projection ?: HostedSavesProvider.DOCUMENT_COLUMNS).also { it.addDocument(documentId, file) }
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val parent = fileOf(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException(parentDocumentId)
        val children = parent.listFiles() ?: throw FileNotFoundException(parentDocumentId)
        val cursor = MatrixCursor(projection ?: HostedSavesProvider.DOCUMENT_COLUMNS)
        children.sortedBy { it.name }.forEach { child ->
            val id = "${parentDocumentId.trimEnd('/')}/${child.name}"
            try {
                fileOf(id)
            } catch (_: SecurityException) {
                return@forEach
            } catch (_: FileNotFoundException) {
                return@forEach
            }
            cursor.addDocument(id, child)
        }
        return cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw UnsupportedOperationException("Found folders open only for reading")
        val file = fileOf(documentId)
        if (!file.isFile) throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId != documentId && containsFolder(parentDocumentId, documentId)

    override fun getDocumentType(documentId: String): String {
        val file = fileOf(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return if (file.isDirectory) Document.MIME_TYPE_DIR else HostedSavesProvider.OCTET_STREAM
    }

    private fun MatrixCursor.addDocument(documentId: String, file: File) {
        val folder = file.isDirectory
        newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_MIME_TYPE, if (folder) Document.MIME_TYPE_DIR else HostedSavesProvider.OCTET_STREAM)
            .add(Document.COLUMN_SIZE, if (folder) null else file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(Document.COLUMN_FLAGS, 0)
    }

    internal companion object {
        const val AUTHORITY_SUFFIX = ".library"
    }
}
