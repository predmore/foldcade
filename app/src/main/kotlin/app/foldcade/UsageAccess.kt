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
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            null,
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
                else -> null
            } ?: continue
            val packageName = event.packageName ?: continue
            samples += UsageSample(packageName, move, event.timeStamp)
        }
        return samples
    }

    companion object {
        const val LOOKBACK_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
