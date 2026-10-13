package app.foldcade

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.foldcade.language.brightnessFraction
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Both screens' brightness. The default display's level lives in
 * `Settings.System`; the other panel's is set through [AynBrightness], off the
 * main thread. One level goes to both, as AYN's "both screens" slider does.
 *
 * Writing needs "Modify system settings", which Android grants from
 * [Settings.ACTION_MANAGE_WRITE_SETTINGS], not a runtime prompt.
 */
class ScreenBrightness(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val displays = Displays(context)
    private val other = Executors.newSingleThreadExecutor()

    /** The newest level for the other panel, or -1 once sent. A held key sends only the latest. */
    private val pending = AtomicInteger(-1)

    /** The other panel's level as last read or written there, so the main thread never waits on AYN. */
    @Volatile private var otherLevel: Int? = null
        set(value) {
            field = value
            revision++
        }

    /** Moves whenever either panel's level may have, so a dimmed window can follow. */
    var revision by mutableIntStateOf(0)
        private set

    init {
        refreshOther()
    }

    fun allowed(): Boolean = Settings.System.canWrite(context)

    /** The default display's stored level, 1 to 255. Null when the setting cannot be read. */
    fun level(): Int? = try {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (_: Settings.SettingNotFoundException) {
        null
    }

    /** The level [displayId] holds, as a window brightness from 0 to 1. Null when it cannot be read. */
    fun fraction(displayId: Int): Float? =
        if (displayId == Display.DEFAULT_DISPLAY) {
            level()?.let(::brightnessFraction)
        } else {
            otherLevel?.let { it / 255f }
        }

    /**
     * Stores [level] on both screens. A device in automatic mode would ignore it,
     * so this turns automatic off first, the way moving Android's own slider does.
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
            if (pending.getAndSet(level) < 0) {
                other.execute {
                    val next = pending.getAndSet(-1)
                    displays.otherDisplayId()?.let { if (AynBrightness.set(it, next)) otherLevel = next }
                }
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, level)
        } catch (_: SecurityException) {
            false
        }
    }

    /** Reads the other panel's level again, off the main thread. AYN's own menu can move it, so resume calls this. */
    fun refreshOther() = other.execute {
        otherLevel = displays.otherDisplayId()?.let(AynBrightness::get)
    }

    /** Android's page for this app's "Modify system settings" switch. */
    fun accessIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /** Calls [onChange] on the main thread whenever anything changes the level. */
    fun observe(onChange: () -> Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                revision++
                onChange()
            }
        }
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
    }
}
