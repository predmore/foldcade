package app.foldcade

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * The default display's brightness, through `Settings.System`. On the Thor that is
 * the top screen; the bottom screen takes a call only system apps may make.
 *
 * Writing needs "Modify system settings", which Android grants from
 * [Settings.ACTION_MANAGE_WRITE_SETTINGS], not a runtime prompt.
 */
class ScreenBrightness(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun allowed(): Boolean = Settings.System.canWrite(context)

    /** The stored level, 1 to 255. Null when the setting cannot be read. */
    fun level(): Int? = try {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (_: Settings.SettingNotFoundException) {
        null
    }

    /**
     * Stores [level]. A device in automatic mode would ignore it, so this turns
     * automatic off first, the way moving Android's own slider does.
     */
    fun write(level: Int): Boolean {
        if (!allowed()) return false
        return try {
            val mode = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            if (mode != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, level)
        } catch (_: SecurityException) {
            false
        }
    }

    /** Android's page for this app's "Modify system settings" switch. */
    fun accessIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /** Calls [onChange] on the main thread whenever anything changes the level. */
    fun observe(onChange: () -> Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
    }
}
