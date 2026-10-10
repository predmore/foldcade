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
    fun assignment(defaultDisplayIsTop: Boolean): DisplayAssignment {
        val manager = context.getSystemService(DisplayManager::class.java)
        val others = manager.displays.map { it.displayId }.filter { it != Display.DEFAULT_DISPLAY }
        return assignDisplays(Display.DEFAULT_DISPLAY, others, defaultDisplayIsTop)
    }

    fun panelFor(activity: Activity, defaultDisplayIsTop: Boolean): Panel? {
        val id = activity.display?.displayId ?: Display.DEFAULT_DISPLAY
        return assignment(defaultDisplayIsTop).panelFor(id)
    }
}

fun Context.startOnDisplay(activity: Class<*>, displayId: Int, configure: Intent.() -> Unit = {}) {
    startOnDisplay(Intent(this, activity).apply(configure), displayId)
}

fun Context.startOnDisplay(intent: Intent, displayId: Int) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    val options = ActivityOptions.makeBasic()
    options.launchDisplayId = displayId
    startActivity(intent, options.toBundle())
}

internal fun PlayerIntent.toStartIntent(): Intent {
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
