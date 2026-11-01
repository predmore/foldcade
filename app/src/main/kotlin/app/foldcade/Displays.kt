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
 * Starts [intent] on [displayId] in a task of its own. A start that must reuse the
 * task its player already has, to bring a game back or to replace it, passes
 * [multipleTask] false and its own task flags. [bloom]: see [bloomStart].
 */
fun Context.startOnDisplay(intent: Intent, displayId: Int, multipleTask: Boolean = true, bloom: Boolean = false) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (multipleTask) Intent.FLAG_ACTIVITY_MULTIPLE_TASK else 0)
    if (bloom) bloomStart(intent)
    val options = ActivityOptions.makeBasic()
    options.launchDisplayId = displayId
    startActivity(intent, options.toBundle())
}

/**
 * A start under a launch's art. Android 13 refuses an app's own animation for another
 * app's new task, so its slide would carry Home and the art away. The game opens over
 * the art instead, with no slide.
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
