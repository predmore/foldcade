package app.foldcade

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display
import app.foldcade.api.DisplayAssignment
import app.foldcade.api.Panel
import app.foldcade.api.assignDisplays

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
    val intent = Intent(this, activity).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        configure()
    }
    val options = ActivityOptions.makeBasic()
    options.launchDisplayId = displayId
    startActivity(intent, options.toBundle())
}
