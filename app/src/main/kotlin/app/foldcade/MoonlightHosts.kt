package app.foldcade

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import app.foldcade.gamestream.GameStreamClient
import app.foldcade.gamestream.ServerInfo
import app.foldcade.net.isLanHost
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One computer Moonlight streams from, as Foldcade last saw it. */
internal data class MoonlightHost(
    val name: String,
    /** True when it answered the last ask, false when it stopped answering, null before anything reached it. */
    val online: Boolean?,
    /** The app it streams now, when it says. Apollo does not say to an unpaired ask. */
    val runningAppId: String? = null,
)

/**
 * The tiles whose game a computer is streaming now, each with that computer's
 * name. Keys are Moonlight remote keys, which are the tile ids.
 */
internal fun streamingTiles(hosts: Map<String, MoonlightHost>): Map<String, String> =
    hosts.mapNotNull { (key, host) ->
        val app = host.runningAppId?.takeIf { host.online == true } ?: return@mapNotNull null
        "$key|$app" to host.name
    }.toMap()

/**
 * Which of Moonlight's computers answer on the local network, and what each streams.
 *
 * Moonlight's own computer shortcuts give each host's UUID and name. A host is
 * found by mDNS (`_nvstream._tcp`, which Sunshine and Apollo advertise) or by
 * that name in DNS, then asked at `/serverinfo` over plain http, which needs no
 * pairing. An answer counts only when its UUID is that host's. Only LAN
 * addresses are asked.
 *
 * It runs while a Home screen is shown ([watch]) and Moonlight has a computer.
 */
internal class MoonlightHosts(
    private val context: Context,
    private val client: GameStreamClient,
    private val scope: CoroutineScope,
    private val publish: (Map<String, MoonlightHost>) -> Unit,
) {
    /** Computer names from Moonlight's shortcuts, by host key. */
    @Volatile
    private var names: Map<String, String> = emptyMap()

    /** The address each host last answered at, by host key. */
    private val addresses = ConcurrentHashMap<String, String>()

    @Volatile
    private var hosts: Map<String, MoonlightHost> = emptyMap()

    private var loop: Job? = null
    private var lastDiscovery = 0L

    /** Searches in a row that left a known host missing. */
    private var quietRounds = 0
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** The computers Moonlight's shortcuts name, keyed by lower-case UUID. Any thread. */
    fun knowNames(next: Map<String, String>) {
        val keyed = next.mapKeys { it.key.lowercase(Locale.ROOT) }
        if (keyed == names) return
        names = keyed
        wake.trySend(Unit)
    }

    /** Main thread. Asks while [shown], and once more at once when shown again. */
    fun watch(shown: Boolean) {
        if (!shown) {
            loop?.cancel()
            loop = null
            return
        }
        if (loop?.isActive == true) {
            wake.trySend(Unit)
            return
        }
        loop = scope.launch {
            while (isActive) {
                if (names.isNotEmpty()) round()
                withTimeoutOrNull(ASK_EVERY_MS) { wake.receive() }
            }
        }
    }

    private suspend fun round() {
        val wanted = names
        val seen = HashMap<String, ServerInfo>()
        // A host found before answers where it was.
        addresses.forEach { (key, address) ->
            if (key in wanted) client.serverInfo(address)?.takeIf { it.key == key }?.let { seen[key] = it }
        }
        val missing = wanted.keys - seen.keys
        val now = System.currentTimeMillis()
        // Look again soon for a host that went quiet, less often the longer it stays quiet.
        if (missing.isNotEmpty() && now - lastDiscovery > minOf(DISCOVER_EVERY_MS shl quietRounds, QUIET_MAX_MS)) {
            lastDiscovery = now
            discover().forEach { address ->
                val info = client.serverInfo(address) ?: return@forEach
                if (info.key in wanted && info.key !in seen) {
                    seen[info.key] = info
                    addresses[info.key] = address
                }
            }
            // A name Moonlight knows can be in the network's DNS, as on a router that names its clients.
            (wanted.keys - seen.keys).forEach { key ->
                val name = wanted[key] ?: return@forEach
                for (address in lanAddresses(name)) {
                    val info = client.serverInfo(address) ?: continue
                    if (info.key != key) continue
                    seen[key] = info
                    addresses[key] = address
                    break
                }
            }
            quietRounds = if ((wanted.keys - seen.keys).isEmpty()) 0 else (quietRounds + 1).coerceAtMost(3)
        }
        val before = hosts
        val next = wanted.mapValues { (key, name) ->
            val info = seen[key]
            when {
                info != null -> MoonlightHost(name = name, online = true, runningAppId = info.runningAppId)
                // Not found yet is not known to be off; found and now silent is.
                addresses.containsKey(key) || before[key]?.online != null -> MoonlightHost(name = name, online = false)
                else -> MoonlightHost(name = name, online = null)
            }
        }
        if (next != before) {
            hosts = next
            Log.i("Foldcade", "moonlight-hosts " + next.values.joinToString { "${it.name}=${it.online}/${it.runningAppId}" })
            withContext(Dispatchers.Main.immediate) { publish(next) }
        }
    }

    /** Addresses of the GameStream hosts mDNS finds in [DISCOVER_MS]. */
    private suspend fun discover(): List<String> {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return emptyList()
        val found = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                found.trySend(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                found.close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }
        val addresses = LinkedHashSet<String>()
        try {
            withTimeoutOrNull(DISCOVER_MS) {
                for (service in found) {
                    when (val answer = resolve(nsd, service)) {
                        is Resolved.Done -> answer.address?.let(addresses::add)
                        // Android 13 cannot stop a resolve, and refuses another until it ends.
                        Resolved.Stuck -> break
                    }
                }
            }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        return addresses.toList()
    }

    private sealed interface Resolved {
        data class Done(val address: String?) : Resolved

        /** No answer in time. */
        data object Stuck : Resolved
    }

    /** Android resolves one service at a time, so each waits its turn. */
    private suspend fun resolve(nsd: NsdManager, service: NsdServiceInfo): Resolved = resolving.withLock {
        withTimeoutOrNull(RESOLVE_MS) {
            suspendCancellableCoroutine<Resolved> { done ->
                val listener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        if (done.isActive) done.resume(Resolved.Done(null))
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val address = info.host?.let(::addressText)?.takeIf(::isLanHost)
                        if (done.isActive) done.resume(Resolved.Done(address))
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    done.invokeOnCancellation { runCatching { nsd.stopServiceResolution(listener) } }
                }
                @Suppress("DEPRECATION")
                nsd.resolveService(service, listener)
            }
        } ?: Resolved.Stuck
    }

    private val resolving = Mutex()

    /** [name]'s LAN addresses in DNS, IPv4 first. Empty when it has none. */
    private suspend fun lanAddresses(name: String): List<String> {
        if (!HOST_NAME.matches(name)) return emptyList()
        return withTimeoutOrNull(DNS_MS) {
            runInterruptible(Dispatchers.IO) {
                try {
                    InetAddress.getAllByName(name).toList()
                } catch (_: java.net.UnknownHostException) {
                    emptyList()
                } catch (_: SecurityException) {
                    emptyList()
                }
            }
        }.orEmpty()
            .sortedBy { it is Inet6Address }
            .mapNotNull(::addressText)
            .filter(::isLanHost)
    }

    /** An address to put in a URL. A link-local IPv6 one needs its interface, which a URL cannot carry. */
    private fun addressText(address: InetAddress): String? =
        if (address is Inet6Address && (address.isLinkLocalAddress || address.scopeId != 0)) null
        else address.hostAddress?.takeIf { it.isNotEmpty() }

    private companion object {
        const val SERVICE_TYPE = "_nvstream._tcp"
        const val ASK_EVERY_MS = 10_000L
        const val DISCOVER_EVERY_MS = 60_000L
        const val QUIET_MAX_MS = 5 * 60_000L
        const val DISCOVER_MS = 4_000L
        const val RESOLVE_MS = 3_000L
        const val DNS_MS = 3_000L

        /** A plain DNS label, as a computer name Moonlight shows usually is. */
        val HOST_NAME = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")
    }
}
