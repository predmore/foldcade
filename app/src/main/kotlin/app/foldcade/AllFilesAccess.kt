package app.foldcade

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings

/** Optional shared-storage access. Android owns its state; only the user's action opens its page. */
internal class AllFilesAccess(private val context: Context) {
    fun granted(): Boolean = Environment.isExternalStorageManager()

    fun settingsIntents(): List<Intent> = listOf(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null)),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
    )
}
