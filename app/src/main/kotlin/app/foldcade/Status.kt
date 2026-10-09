package app.foldcade

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.text.format.DateFormat
import app.foldcade.language.Copy
import java.util.Date

data class DeviceStatus(
    val time: String,
    val batteryPercent: Int,
    val charging: Boolean,
    val network: String,
)

fun readDeviceStatus(context: Context): DeviceStatus {
    val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 0
    val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return DeviceStatus(
        time = clockText(context, System.currentTimeMillis()),
        batteryPercent = percent,
        charging = charging,
        network = networkName(context),
    )
}

/** Locale order and the device 12/24-hour setting. Does not force a pattern. */
internal fun clockText(context: Context, nowMillis: Long): String =
    DateFormat.getTimeFormat(context).format(Date(nowMillis))

/** Delay until the next minute boundary. Exactly on the boundary waits a full minute. */
internal fun millisUntilNextMinute(nowMillis: Long): Long {
    val elapsed = nowMillis.mod(60_000L)
    return if (elapsed == 0L) 60_000L else 60_000L - elapsed
}

private fun networkName(context: Context): String {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return Copy.offline
    val network = manager.activeNetwork ?: return Copy.offline
    val caps = manager.getNetworkCapabilities(network) ?: return Copy.offline
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Copy.wifi
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Copy.cellular
        else -> Copy.offline
    }
}

fun batteryLabel(status: DeviceStatus): String =
    if (status.charging) "${status.batteryPercent}% +" else "${status.batteryPercent}%"
