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
     * Activities that came to the front (true) or left it (false) between [sinceMillis] and
     * [nowMillis], oldest first, as package and class, passing over packages [skip] names.
     * Empty without usage access.
     */
    fun frontMoves(sinceMillis: Long, nowMillis: Long, skip: (String) -> Boolean): List<FrontMove> {
        if (!granted()) return emptyList()
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val events = try {
            manager.queryEvents(sinceMillis, nowMillis)
        } catch (_: SecurityException) {
            return emptyList()
        }
        val event = UsageEvents.Event()
        val moves = mutableListOf<FrontMove>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val shown = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> true
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> false
                else -> null
            } ?: continue
            val name = event.packageName?.takeIf { it.isNotEmpty() && !skip(it) } ?: continue
            moves += FrontMove(name, event.className.orEmpty(), shown, event.timeStamp)
        }
        return moves
    }

    companion object {
        const val LOOKBACK_MS = 7L * 24L * 60L * 60L * 1000L
    }
}

/** One activity coming to the front ([shown]) or leaving it, as usage access reports it. */
data class FrontMove(val packageName: String, val className: String, val shown: Boolean, val atMillis: Long)

/**
 * The activities in front now, latest last. A pause or stop takes out only that activity, so
 * an app moving between its own activities stays, and an app that left gives way to the one
 * that was there before it.
 */
internal class FrontStack {
    private val shown = LinkedHashSet<Pair<String, String>>()

    fun apply(moves: List<FrontMove>) {
        for (move in moves) {
            val key = move.packageName to move.className
            shown.remove(key)
            if (move.shown) shown.add(key)
        }
    }

    fun front(): String? = shown.lastOrNull()?.first
}

/**
 * The app in front of a screen Foldcade did not open an app on, as Recents, a notification or
 * an emulator's own setup leaves it. Android does not say which screen an app is on. The screen
 * beside a command center is Foldcade's, so the other app still in front is the one beside it:
 * an app opened from the command center's grid and then left drops out when it pauses. Each
 * look reads only what is new since the last one.
 */
class FrontApps(private val access: UsageAccess, private val ownPackage: String) {
    private var checkedAt = 0L
    private var stack = FrontStack()

    @Synchronized
    fun latest(nowMillis: Long = System.currentTimeMillis()): String? {
        // Without usage access nothing is read, so the next look once it is granted reads the whole day again.
        if (!access.granted()) {
            checkedAt = 0L
            stack = FrontStack()
            return null
        }
        // The window overlaps the last one a little, for moves that land after their time stamp.
        // Moving the latest few in again, in order, leaves the stack as it was.
        val since = if (checkedAt == 0L) nowMillis - FRONT_LOOKBACK_MS else checkedAt - FRONT_SLACK_MS
        stack.apply(access.frontMoves(since, nowMillis) { it == ownPackage || it == SYSTEM_UI })
        checkedAt = nowMillis
        return stack.front()
    }

    private companion object {
        const val FRONT_LOOKBACK_MS = 24L * 60L * 60L * 1000L
        /** Events can land a moment after their time stamp. */
        const val FRONT_SLACK_MS = 2_000L
        const val SYSTEM_UI = "com.android.systemui"
    }
}
