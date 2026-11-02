package app.foldcade

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import app.foldcade.host.play.UsageMove
import app.foldcade.host.play.UsageSample

/**
 * Optional usage access. The system grants it from
 * [Settings.ACTION_USAGE_ACCESS_SETTINGS], not from a runtime permission.
 */
class UsageAccess(private val context: Context) {
    fun granted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        // The attribution-tag overload is absent on API 33. Calling it crashes at resume.
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun samples(fromMillis: Long, toMillis: Long): List<UsageSample> {
        if (!granted()) return emptyList()
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val events = try {
            manager.queryEvents(fromMillis, toMillis)
        } catch (_: SecurityException) {
            return emptyList()
        }
        val event = UsageEvents.Event()
        val samples = mutableListOf<UsageSample>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val move = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageMove.Resumed
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageMove.Paused
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageMove.ScreenOff
                UsageEvents.Event.SCREEN_INTERACTIVE -> UsageMove.ScreenOn
                else -> null
            } ?: continue
            // A screen move belongs to the device. A blank package is dropped for the rest.
            val packageName = event.packageName ?: ""
            samples += UsageSample(packageName, move, event.timeStamp)
        }
        return samples
    }

    /**
     * The activities of [packageName] that came to the front since [sinceMillis],
     * oldest first. Empty without usage access, which is the only way Android shows
     * a normal app another app's activities; it keeps no record of their closing.
     */
    fun resumedSince(packageName: String, sinceMillis: Long, nowMillis: Long): List<String> {
        if (!granted()) return emptyList()
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val events = try {
            manager.queryEvents(sinceMillis, nowMillis)
        } catch (_: SecurityException) {
            return emptyList()
        }
        val event = UsageEvents.Event()
        val resumed = mutableListOf<String>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED || event.packageName != packageName) continue
            event.className?.let { resumed += it }
        }
        return resumed
    }

    /**
     * The package whose activity came to the front last between [sinceMillis] and [nowMillis],
     * passing over packages [skip] names. Null without usage access, or with nothing new.
     */
    fun lastResumed(sinceMillis: Long, nowMillis: Long, skip: (String) -> Boolean): String? {
        if (!granted()) return null
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val events = try {
            manager.queryEvents(sinceMillis, nowMillis)
        } catch (_: SecurityException) {
            return null
        }
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val name = event.packageName?.takeIf { it.isNotEmpty() && !skip(it) } ?: continue
            last = name
        }
        return last
    }

    companion object {
        const val LOOKBACK_MS = 7L * 24L * 60L * 60L * 1000L
    }
}

/**
 * The app in front of a screen Foldcade did not open an app on, as Recents, a notification or
 * an emulator's own setup leaves it. Android names only the app that came to the front last,
 * not its screen; the screen beside a command center is Foldcade's, so the last other app is
 * the one beside it. Each look reads only what is new since the last one.
 */
class FrontApps(private val access: UsageAccess, private val ownPackage: String) {
    private var checkedAt = 0L
    private var front: String? = null

    @Synchronized
    fun latest(nowMillis: Long = System.currentTimeMillis()): String? {
        val since = if (checkedAt == 0L) nowMillis - FRONT_LOOKBACK_MS else checkedAt - FRONT_SLACK_MS
        access.lastResumed(since, nowMillis) { it == ownPackage || it == SYSTEM_UI }?.let { front = it }
        checkedAt = nowMillis
        return front
    }

    private companion object {
        const val FRONT_LOOKBACK_MS = 24L * 60L * 60L * 1000L
        /** Events can land a moment after their time stamp. */
        const val FRONT_SLACK_MS = 2_000L
        const val SYSTEM_UI = "com.android.systemui"
    }
}
