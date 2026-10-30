package app.foldcade

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveFolderHolder
import java.io.File

/** Android grants and folder probes for the pure finder. Probes never write a save. */
internal class AndroidSaveFolderFinder(private val context: Context) {
    private val resolver = context.contentResolver
    private val documents = AndroidSaveDocuments(resolver)
    private val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun finder(player: Player, grants: List<SaveFolderGrant>): SaveFolderFinder = SaveFolderFinder(
        grants,
        ::isDirectory,
        documents::childNames,
        { id -> playerHolds(player, id) },
        { path -> File(Environment.getExternalStorageDirectory(), path).isDirectory },
    )

    fun grants(holders: List<SaveFolderHolder>): List<SaveFolderGrant> = resolver.persistedUriPermissions.filter { DocumentsContract.isTreeUri(it.uri) }.map { permission ->
        val tree = permission.uri.toString()
        val needsWrite = permission.isReadPermission && !permission.isWritePermission && holders.any { holder ->
            holder.saveFolderPlaces.any { containsFolder(folderTreeId(tree), "primary:$it") }
        }
        val upgraded = needsWrite && upgradeTreeGrant { resolver.takePersistableUriPermission(permission.uri, flags) }
        SaveFolderGrant(tree, permission.isReadPermission, permission.isWritePermission || upgraded)
    } + if (AllFilesAccess(context).granted()) listOf(SaveFolderGrant(SharedStorageProvider.tree(context.packageName), true, true, allFiles = true)) else emptyList()

    fun reachable(player: Player, holder: SaveFolderHolder, uri: String, grants: List<SaveFolderGrant>): Boolean {
        return canReach(uri, grants) && finder(player, grants).fits(holder, uri) &&
            (!holder.saveFolderPickedInPlayer || playerHolds(player, folderDocumentId(uri)))
    }

    fun canReach(uri: String, grants: List<SaveFolderGrant>): Boolean = holdsGrant(uri, grants) && isDirectory(uri)

    /** Whether Foldcade still holds the tree [uri] is in, read and write. No I/O. */
    fun holdsGrant(uri: String, grants: List<SaveFolderGrant>): Boolean =
        grants.any { it.read && it.write && folderGrantUri(it.uri) == folderGrantUri(uri) }

    fun isDirectory(uri: String): Boolean = try {
        val document = Uri.parse(folderDocumentUri(uri, folderDocumentId(uri)))
        resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use {
            it.moveToFirst() && it.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
        } == true
    } catch (_: Exception) {
        false
    }

    private fun playerHolds(player: Player, documentId: String): Boolean {
        val uid = player.packageNames.firstNotNullOfOrNull { name ->
            try {
                context.packageManager.getApplicationInfo(name, 0).uid
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        } ?: return false
        // The player's own picker grants the very folder it uses. A grant on a folder above it means it uses that one.
        val tree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", documentId)
        return context.checkUriPermission(tree, -1, uid, flags) == PackageManager.PERMISSION_GRANTED
    }
}
