package app.foldcade

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import android.text.format.DateFormat
import app.foldcade.language.Copy
import java.util.Date
import java.util.Locale

data class DeviceStatus(
    val time: String,
    val batteryPercent: Int,
    val charging: Boolean,
    val network: String,
    /** The weekday and date, for the open R1 card. */
    val date: String = "",
    /** Wi-Fi bars from 0 to 4. Zero off Wi-Fi. */
    val wifiBars: Int = 0,
    val bluetooth: Boolean = false,
)

fun readDeviceStatus(context: Context): DeviceStatus {
    val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 0
    val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    val now = System.currentTimeMillis()
    val network = networkOf(context)
    return DeviceStatus(
        time = clockText(context, now),
        batteryPercent = percent,
        charging = charging,
        network = network.name,
        date = dateText(now),
        wifiBars = network.bars,
        bluetooth = bluetoothOn(context),
    )
}

/** Locale order and the device 12/24-hour setting. Does not force a pattern. */
internal fun clockText(context: Context, nowMillis: Long): String =
    DateFormat.getTimeFormat(context).format(Date(nowMillis))

/** Short weekday, month, and day, in the locale's order. */
internal fun dateText(nowMillis: Long, locale: Locale = Locale.getDefault()): String =
    DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), nowMillis).toString()

/** Delay until the next minute boundary. Exactly on the boundary waits a full minute. */
internal fun millisUntilNextMinute(nowMillis: Long): Long {
    val elapsed = nowMillis.mod(60_000L)
    return if (elapsed == 0L) 60_000L else 60_000L - elapsed
}

private data class NetworkState(val name: String, val bars: Int)

private fun networkOf(context: Context): NetworkState {
    val offline = NetworkState(Copy.offline, 0)
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return offline
    val network = manager.activeNetwork ?: return offline
    val caps = manager.getNetworkCapabilities(network) ?: return offline
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState(Copy.wifi, wifiBars(context, caps.signalStrength))
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkState(Copy.cellular, 0)
        else -> offline
    }
}

/**
 * The RSSI in [rssi] as 0 to 4 bars, on Android's own scale for this device.
 * An unknown strength reads as full: the network is up, and an empty icon would say it is not.
 */
private fun wifiBars(context: Context, rssi: Int): Int {
    if (rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) return 4
    val wifi = context.getSystemService(WifiManager::class.java) ?: return 4
    return scaledBars(wifi.calculateSignalLevel(rssi), wifi.maxSignalLevel)
}

/** Android's [level] out of [max] as 0 to 4 bars. */
internal fun scaledBars(level: Int, max: Int): Int {
    if (max <= 0) return 4
    return ((level.coerceIn(0, max) * 4f) / max + 0.5f).toInt().coerceIn(0, 4)
}

/** Bluetooth radio on or off. Reading the global setting needs no Bluetooth permission. */
private fun bluetoothOn(context: Context): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.BLUETOOTH_ON, 0) != 0

fun batteryLabel(status: DeviceStatus): String =
    if (status.charging) "${status.batteryPercent}% +" else "${status.batteryPercent}%"
