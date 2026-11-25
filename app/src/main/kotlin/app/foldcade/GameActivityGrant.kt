package app.foldcade

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.foldcade.plugins.azahar.activityClass
import java.util.UUID

/** Android owns this temporary grant on the activity record that received the game intent. */
internal data class GameActivityGrant(val uri: Uri, val uid: Int) {
    fun heldByPlayer(context: Context): Boolean? = try {
        context.checkUriPermission(uri, -1, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
    } catch (_: SecurityException) {
        null
    }
}

internal fun tracksGameActivity(packageName: String, activity: String?): Boolean =
    activity != null && activity == activityClass(packageName)

internal fun Context.markGameActivity(start: Intent): GameActivityGrant? {
    val component = start.component ?: return null
    if (!tracksGameActivity(component.packageName, component.className)) return null
    val uid = try {
        packageManager.getPackageUid(component.packageName, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        return null
    }
    // This path is deliberately outside the FileProvider's configured roots. It names no file.
    val uri = Uri.parse("content://$packageName.romm.cache/game-activity/${UUID.randomUUID()}")
    start.clipData = start.clipData?.also { it.addItem(ClipData.Item(uri)) } ?: ClipData.newRawUri(null, uri)
    start.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return GameActivityGrant(uri, uid)
}

internal enum class GameActivityResume { Resume, Warn, Launch }

internal fun gameActivityResume(held: Boolean?, canObserveFront: Boolean, warned: Boolean): GameActivityResume = when {
    held == false -> GameActivityResume.Launch
    warned || (held == true && canObserveFront) -> GameActivityResume.Resume
    else -> GameActivityResume.Warn
}

/** A different activity, or missing usage history, never proves that a game has ended. */
internal fun gameActivityResumed(held: Boolean?, resumed: List<String>, activity: String?, warned: Boolean): GameActivityResume = when {
    held == false -> GameActivityResume.Launch
    warned || resumed.lastOrNull()?.let { it == activity } == true -> GameActivityResume.Resume
    else -> GameActivityResume.Warn
}
