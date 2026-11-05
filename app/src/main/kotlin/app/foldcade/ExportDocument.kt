package app.foldcade

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import app.foldcade.language.PlayCopy

/** Both exports start in Downloads instead of inheriting another app's last picker folder. */
internal class ExportDocument : ActivityResultContracts.CreateDocument("application/zip") {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input)
        .putExtra(DocumentsContract.EXTRA_INITIAL_URI,
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download"))
}

/** Name the saved file and where it went, including when the player leaves Downloads. */
internal fun Context.exportResult(uri: Uri): String {
    val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
    val folder = exportFolder(uri.authority, documentId)
    // The file's own name too: a second backup the same day is "(2).zip", so the folder alone can't say which (review-D 141).
    val name = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return PlayCopy.exported(folder, name)
}

/** Document providers with a known folder path. Opaque cloud ids fall back to the saved file's name. */
internal fun exportFolder(authority: String?, documentId: String?): String? = when (authority) {
    "com.android.externalstorage.documents" -> documentId?.substringAfter(':', "")?.substringBeforeLast('/', "")
        ?.takeIf { it.isNotBlank() }?.replaceFirst(Regex("^Download(?=/|$)"), "Downloads")
    "com.android.providers.downloads.documents" -> {
        if (documentId?.startsWith("raw:") == true) documentId.removePrefix("raw:")
            .substringAfter("/Download/", "").substringBeforeLast('/', "").let { if (it.isEmpty()) "Downloads" else "Downloads/$it" }
        else "Downloads"
    }
    else -> null
}
