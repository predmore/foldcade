package app.foldcade

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.foldcade.plugins.armsx2.ArmsX2Player
import app.foldcade.plugins.azahar.activityClass
import app.foldcade.plugins.eden.EDEN_PACKAGES
import app.foldcade.plugins.eden.EdenPlayer
import app.foldcade.plugins.melonds.EMULATOR_ACTIVITY as MELONDS_GAME_ACTIVITY
import app.foldcade.plugins.melonds.MelonDsPlayer
import java.util.UUID

/** Android owns this temporary grant on the activity record that received the game intent. */
internal data class GameActivityGrant(val uri: Uri, val uid: Int) {
    fun heldByPlayer(context: Context): Boolean? = try {
        context.checkUriPermission(uri, -1, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
    } catch (_: SecurityException) {
        null
    }
}

/** These launches name the activity record the emulator removes on native game close. */
internal fun supportsGameActivityGrant(packageName: String, activity: String?): Boolean = when (activity) {
    ArmsX2Player.MAIN_ACTIVITY -> packageName in ArmsX2Player.PACKAGES
    EdenPlayer.EMULATION_ACTIVITY -> packageName in EDEN_PACKAGES
    MELONDS_GAME_ACTIVITY -> packageName in MelonDsPlayer.PACKAGES
    else -> activity != null && activity == activityClass(packageName)
}

internal fun Context.markGameActivity(start: Intent): GameActivityGrant? {
    val component = start.component ?: return null
    if (!supportsGameActivityGrant(component.packageName, component.className)) return null
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

/** With no confirmed grant, null hands the decision back to the existing resume path. */
internal fun gameActivityResume(exited: Boolean, hasActivityGrant: Boolean, held: Boolean?, warned: Boolean): GameActivityResume? = when {
    exited -> GameActivityResume.Launch
    !hasActivityGrant -> null
    held == false -> GameActivityResume.Launch
    held == true || warned -> GameActivityResume.Resume
    else -> GameActivityResume.Warn
}

internal enum class LauncherResumeResult { KeepRun, Warn, ForgetRun }

/** After dispatch, only a revoked grant clears the run. Activity observations can warn without starting a launch. */
internal fun launcherResumeResult(
    hasActivityGrant: Boolean,
    held: Boolean?,
    resumed: List<String>,
    frontDoor: String?,
    activity: String?,
): LauncherResumeResult {
    if (hasActivityGrant && held == false) return LauncherResumeResult.ForgetRun
    val unexpected = if (hasActivityGrant) resumed.lastOrNull()?.let { it != activity } == true
        else resumeFound(resumed, frontDoor, activity) == false
    return if (unexpected) LauncherResumeResult.Warn else LauncherResumeResult.KeepRun
}
