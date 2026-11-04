package app.foldcade

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.foldcade.language.KeyDrop
import app.foldcade.language.KeyDropPage
import app.foldcade.net.LanDrop
import app.foldcade.net.isLanHost
import java.net.Inet4Address
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The SteamGridDB form's phone page ([KeyDrop]): a [LanDrop] that listens only while the
 * form is open on a shown screen. [want] follows that; the drop closes the moment it turns
 * false, and after the one key it takes. A key the form then refuses gets a fresh drop,
 * with a new token, from [checked]. Main thread, except where it says.
 */
internal class KeyDropHost(
    private val connectivity: ConnectivityManager?,
    private val scope: CoroutineScope,
    private val colors: () -> KeyDropPage.Colors,
    /** A key came from the phone, while the form still wants one. */
    private val onKey: (String) -> Unit,
) {
    var state by mutableStateOf<KeyDrop?>(null)
        private set

    private var wanted = false
    private var drop: LanDrop? = null
    private var opening: Job? = null

    /** The form is open on a shown screen, or no longer is. */
    fun want(on: Boolean) {
        if (on == wanted) return
        wanted = on
        if (on) arm() else disarm()
    }

    /** The form finished checking a key. One the phone sent and the form refused gets a fresh page. */
    fun checked() {
        if (wanted && state == KeyDrop.Sent) arm()
    }

    private fun disarm() {
        opening?.cancel()
        opening = null
        // Let go first: closing tells the listener, which must not find it still held.
        drop.also { drop = null }?.close()
        state = null
    }

    private fun arm() {
        drop.also { drop = null }?.close()
        opening?.cancel()
        val pageColors = colors()
        opening = scope.launch(Dispatchers.IO) {
            // Without Wi-Fi the form shows SteamGridDB's own page; it looks again now and then.
            while (true) {
                val opened = lanAddress()?.let { open(it, pageColors) }
                if (opened != null) {
                    // A drop this job opened is held or closed, even if the job is cancelled on the way.
                    var held = false
                    try {
                        withContext(Dispatchers.Main.immediate) {
                            if (wanted) {
                                drop = opened
                                state = KeyDrop.Open(opened.url)
                                held = true
                            }
                        }
                    } finally {
                        if (!held) opened.close()
                    }
                    return@launch
                }
                withContext(Dispatchers.Main.immediate) { if (wanted) state = KeyDrop.Offline }
                delay(OFFLINE_RETRY_MILLIS)
            }
        }
    }

    /** IO. */
    private fun open(address: InetAddress, pageColors: KeyDropPage.Colors): LanDrop? {
        lateinit var self: LanDrop
        val pages = object : LanDrop.Pages {
            override fun form(action: String) = KeyDropPage.form(action, pageColors)
            override fun done(received: Boolean) = KeyDropPage.done(received, pageColors)
        }
        val listener = object : LanDrop.Listener {
            override fun visited() {
                scope.launch(Dispatchers.Main.immediate) {
                    val shown = state as? KeyDrop.Open ?: return@launch
                    if (drop === self) state = shown.copy(phoneHere = true)
                }
            }

            override fun received(value: String?) {
                scope.launch(Dispatchers.Main.immediate) {
                    if (drop !== self) return@launch
                    drop = null
                    if (!wanted) return@launch
                    if (value == null) {
                        arm()
                        return@launch
                    }
                    state = KeyDrop.Sent
                    onKey(value)
                }
            }

            override fun stopped() {
                // Closed by too many strays: offer a fresh page rather than a dead code.
                scope.launch(Dispatchers.Main.immediate) {
                    if (drop === self) {
                        drop = null
                        if (wanted) arm()
                    }
                }
            }
        }
        return try {
            LanDrop.open(address, pages, listener).also { self = it }
        } catch (failure: Exception) {
            Log.w("Foldcade", "Phone page did not open", failure)
            null
        }
    }

    /** IO. This device's IPv4 address on Wi-Fi or Ethernet, the one a phone beside it can reach. */
    private fun lanAddress(): InetAddress? {
        val manager = connectivity ?: return null
        return try {
            @Suppress("DEPRECATION")
            val networks = listOfNotNull(manager.activeNetwork) + manager.allNetworks
            networks.distinct().firstNotNullOfOrNull { network ->
                val capabilities = manager.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
                val local = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                if (!local) return@firstNotNullOfOrNull null
                manager.getLinkProperties(network)?.linkAddresses?.map { it.address }?.firstOrNull { address ->
                    address is Inet4Address && !address.isLoopbackAddress && isLanHost(address.hostAddress.orEmpty())
                }
            }
        } catch (failure: SecurityException) {
            null
        }
    }

    companion object {
        private const val OFFLINE_RETRY_MILLIS = 5_000L

        fun hex(color: Color): String = "#%06x".format(color.toArgb() and 0xffffff)
    }
}
