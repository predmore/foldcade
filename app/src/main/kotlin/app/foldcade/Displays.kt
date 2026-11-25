package app.foldcade

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.view.Display
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PlayerIntent

class Displays(private val context: Context) {
    fun assignment(defaultDisplayIsTop: Boolean): DisplayAssignment =
        assignDisplays(Display.DEFAULT_DISPLAY, displayIds(), defaultDisplayIsTop)

    /** The panel that is not the default display, whichever screen that is. */
    fun otherDisplayId(): Int? = otherDisplay(Display.DEFAULT_DISPLAY, displayIds())

    private fun displayIds(): List<Int> {
        val manager = context.getSystemService(DisplayManager::class.java)
        return manager.displays.map { it.displayId }
    }

    fun panelFor(activity: Activity, defaultDisplayIsTop: Boolean): Panel? {
        val id = activity.display?.displayId ?: Display.DEFAULT_DISPLAY
        return assignment(defaultDisplayIsTop).panelFor(id)
    }
}

fun Context.startOnDisplay(activity: Class<*>, displayId: Int, bloom: Boolean = false, configure: Intent.() -> Unit = {}) {
    startOnDisplay(Intent(this, activity).apply(configure), displayId, bloom = bloom)
}

/**
 * Starts [intent] on [displayId]. A start that must reuse the task its player
 * already has passes [multipleTask] false or asks to clear the existing task.
 * [bloom]: see [bloomStart].
 */
fun Context.startOnDisplay(intent: Intent, displayId: Int, multipleTask: Boolean = true, bloom: Boolean = false) {
    intent.flags = displayStartFlags(intent.flags, multipleTask)
    if (bloom) bloomStart(intent)
    val options = ActivityOptions.makeBasic()
    options.launchDisplayId = displayId
    (applicationContext as? FoldcadeApp)?.authorizeApkPluginStart(intent)
    startActivity(intent, options.toBundle())
}

/** CLEAR_TASK needs Android to look for the task to clear. MULTIPLE_TASK skips that lookup. */
internal fun displayStartFlags(flags: Int, multipleTask: Boolean): Int {
    val flagsWithNewTask = flags or Intent.FLAG_ACTIVITY_NEW_TASK
    return if (multipleTask && flags and Intent.FLAG_ACTIVITY_CLEAR_TASK == 0) {
        flagsWithNewTask or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    } else {
        flagsWithNewTask and Intent.FLAG_ACTIVITY_MULTIPLE_TASK.inv()
    }
}

/**
 * A start under a launch's art, or with Motion Off. Android 13 refuses an app's own
 * animation for another app's new task, so its slide would carry Home and the art away.
 * The game opens over the art instead, with no slide; with Motion Off, with no movement.
 */
fun bloomStart(intent: Intent): Intent = intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

internal fun PlayerIntent.toStartIntent(folderAuthority: String? = null): Intent {
    val intent = Intent()
    intent.component = ComponentName(packageName, componentClass)
    intent.action = action
    val parsed = dataUri?.let { Uri.parse(it) }
    if (parsed != null && !mimeType.isNullOrBlank()) {
        intent.setDataAndType(parsed, mimeType)
    } else if (parsed != null) {
        intent.data = parsed
    }
    if (grantReadUri || parsed?.scheme == "content") {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.grantFolderTree(dataUri, folderAuthority)
    }
    if (LaunchFlag.GrantWriteUri in flags) intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    if (LaunchFlag.NewTask in flags) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (LaunchFlag.ClearTop in flags) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (LaunchFlag.ClearTask in flags) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
    for (extra in extras) {
        when (extra) {
            is PlayerExtra.Text -> intent.putExtra(extra.key, extra.value)
            is PlayerExtra.Integer -> intent.putExtra(extra.key, extra.value)
            is PlayerExtra.LongValue -> intent.putExtra(extra.key, extra.value)
            is PlayerExtra.BooleanFlag -> intent.putExtra(extra.key, extra.value)
        }
    }
    return intent
}
