package app.foldcade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import app.foldcade.language.Copy
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
    /** Whole minutes since the epoch, for anything that steps with the clock. */
    val minute: Long = 0L,
)

/**
 * The device status, read again as soon as Android reports a change: the battery and
 * charger, the default network and its signal, the Bluetooth radio, and the clock.
 * It listens only while something collects [status], and reads afresh each time it starts.
 */
class DeviceStatusWatcher(private val context: Context, scope: CoroutineScope) {
    val status: StateFlow<DeviceStatus> = changes()
        .conflate()
        .map { readDeviceStatus(context) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(), readDeviceStatus(context))

    /** One signal per change, and one at the start. */
    private fun changes(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_TIME_TICK)
                // A set clock, a new time zone, or the 12/24-hour setting.
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_LOCALE_CHANGED)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        val bluetooth = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.BLUETOOTH_ON),
            false,
            bluetooth,
        )
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        // Signal strength comes in capability changes, so the bars move with it.
        val network = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(Unit)
            }

            override fun onLost(network: Network) {
                trySend(Unit)
            }
        }
        connectivity?.registerDefaultNetworkCallback(network)
        trySend(Unit)
        awaitClose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(bluetooth)
            connectivity?.unregisterNetworkCallback(network)
        }
    }
}

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
        minute = now / 60_000L,
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
