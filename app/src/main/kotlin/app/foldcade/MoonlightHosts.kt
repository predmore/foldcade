package app.foldcade

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import app.foldcade.gamestream.GAMESTREAM_HTTPS_PORT
import app.foldcade.gamestream.GameStreamClient
import app.foldcade.gamestream.HostApp
import app.foldcade.gamestream.PairedHost
import app.foldcade.gamestream.ServerInfo
import app.foldcade.net.isLanHost
import java.net.Inet6Address
import java.net.InetAddress
import java.security.cert.X509Certificate
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

/** A computer Foldcade paired with itself. [uuid] keeps the case the host sent, as Moonlight stores it. */
internal data class PairedStreamHost(
    val uuid: String,
    val name: String,
    val httpsPort: Int,
    val certificate: X509Certificate,
) {
    val key: String get() = uuid.lowercase(Locale.ROOT)
}

/** Where a computer answered, for a pairing to start there. */
internal data class LocatedHost(val uuid: String, val address: String, val httpsPort: Int)

/**
 * Which of Moonlight's computers answer on the local network, and what each streams.
 *
 * Moonlight's own computer shortcuts give each host's UUID and name, when
 * Foldcade is the home app and may read them. Hosts are also found by mDNS
 * (`_nvstream._tcp`, which Sunshine and Apollo advertise), so one can be paired
 * without that. A named host missing from mDNS is looked up by its name in
 * DNS. Each is asked at `/serverinfo` over plain http, which needs no pairing.
 * An answer counts only when its UUID is that host's. Only LAN addresses are asked.
 *
 * A computer Foldcade paired with ([knowPaired]) is also asked over https for
 * its apps, which go to [onApps], and for what it streams, which Apollo tells
 * only a paired device.
 *
 * It runs while a Home screen is shown ([watch]) and [active] says Moonlight is installed.
 */
internal class MoonlightHosts(
    private val context: Context,
    private val client: () -> GameStreamClient,
    private val scope: CoroutineScope,
    private val active: () -> Boolean,
    private val publish: (Map<String, MoonlightHost>) -> Unit,
    /** Called off the main thread. */
    private val onApps: suspend (PairedStreamHost, List<HostApp>) -> Unit = { _, _ -> },
) {
    /** Hosts mDNS found that no Moonlight shortcut names, with the name each gave. */
    private val discovered = ConcurrentHashMap<String, String>()

    /** Computer names from Moonlight's shortcuts, by host key. */
    @Volatile
    private var names: Map<String, String> = emptyMap()

    @Volatile
    private var paired: Map<String, PairedStreamHost> = emptyMap()

    /** Where each host last answered, by host key. */
    private val found = ConcurrentHashMap<String, LocatedHost>()

    /** When each paired host last listed its apps, by host key. */
    private val listedAt = ConcurrentHashMap<String, Long>()

    @Volatile
    private var hosts: Map<String, MoonlightHost> = emptyMap()

    private var loop: Job? = null
    private var lastDiscovery = 0L

    /** Searches in a row that left a known host missing. */
    private var quietRounds = 0
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** One round, or one [locate], at a time. */
    private val asking = Mutex()

    /** The computers Moonlight's shortcuts name, keyed by lower-case UUID. Any thread. */
    fun knowNames(next: Map<String, String>) {
        val keyed = next.mapKeys { it.key.lowercase(Locale.ROOT) }
        if (keyed == names) return
        names = keyed
        wake.trySend(Unit)
    }

    /** The computers Foldcade paired with. A new one lists its apps at the next round. Any thread. */
    fun knowPaired(next: List<PairedStreamHost>) {
        val keyed = next.associateBy { it.key }
        if (keyed == paired) return
        (keyed.keys - paired.keys).forEach(listedAt::remove)
        paired = keyed
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
                if (active()) asking.withLock { round() }
                withTimeoutOrNull(ASK_EVERY_MS) { wake.receive() }
            }
        }
    }

    /** Where [key] last answered, without asking again. */
    fun lastAt(key: String): LocatedHost? = found[key]

    /** Where the computer [key] answers now, looking for it at once if need be. Null when it does not. */
    suspend fun locate(key: String): LocatedHost? = asking.withLock {
        val last = found[key]
        if (last != null && client().serverInfo(last.address)?.key == key) return@withLock last
        val answers = search(known().filterKeys { it == key })
        answers.forEach { (one, answer) -> remember(answer.first, answer.second, one) }
        if (key in answers) found[key] else null
    }

    /** Every host by key, with its name: Moonlight's first, then the one it was paired under, then its own. */
    private fun known(): Map<String, String> = discovered + paired.mapValues { it.value.name } + names

    private suspend fun round() {
        val seen = HashMap<String, ServerInfo>()
        // A host found before answers where it was.
        found.forEach { (key, at) ->
            client().serverInfo(at.address)?.takeIf { it.key == key }?.let { seen[key] = it }
        }
        val since = System.currentTimeMillis() - lastDiscovery
        val missing = known().keys - seen.keys
        // Look again soon for a host that went quiet, less often the longer it stays quiet,
        // and now and then for a new one.
        val quietWait = minOf(DISCOVER_EVERY_MS shl quietRounds, LOOK_AROUND_MS)
        if ((missing.isNotEmpty() && since > quietWait) || since > LOOK_AROUND_MS) {
            search(known().filterKeys { it in missing }).forEach { (key, answer) ->
                if (key !in seen) seen[key] = answer.first
                remember(answer.first, answer.second, key)
            }
            quietRounds = if ((known().keys - seen.keys).isEmpty()) 0 else (quietRounds + 1).coerceAtMost(3)
        }
        val wanted = known()
        val running = seen.mapValues { (key, info) -> info.runningAppId ?: pairedRunning(key) }
        val before = hosts
        val next = wanted.mapValues { (key, name) ->
            when {
                key in seen -> MoonlightHost(name = name, online = true, runningAppId = running[key])
                // Not found yet is not known to be off; found and now silent is.
                found.containsKey(key) || before[key]?.online != null -> MoonlightHost(name = name, online = false)
                else -> MoonlightHost(name = name, online = null)
            }
        }
        if (next != before) {
            hosts = next
            Log.i("Foldcade", "moonlight-hosts " + next.values.joinToString { "${it.name}=${it.online}/${it.runningAppId}" })
            withContext(Dispatchers.Main.immediate) { publish(next) }
        }
        seen.keys.forEach { key -> listApps(key) }
    }

    /** Apollo names the running app only to a paired device over https. */
    private suspend fun pairedRunning(key: String): String? {
        val host = paired[key] ?: return null
        val at = found[key] ?: return null
        return client().serverInfo(PairedHost(at.address, host.httpsPort, host.certificate))
            ?.takeIf { it.key == key }?.runningAppId
    }

    /** A paired host that answered lists its apps when it never has, or not for a while. */
    private suspend fun listApps(key: String) {
        val host = paired[key] ?: return
        val at = found[key] ?: return
        val now = System.currentTimeMillis()
        if (now - (listedAt[key] ?: 0L) < LIST_EVERY_MS) return
        val apps = client().apps(PairedHost(at.address, host.httpsPort, host.certificate)) ?: return
        listedAt[key] = now
        Log.i("Foldcade", "moonlight-apps ${host.name} ${apps.size}")
        onApps(host, apps)
    }

    /**
     * Every host mDNS finds, then each of [missing] DNS finds by its name, with
     * the address each answered at.
     */
    private suspend fun search(missing: Map<String, String>): Map<String, Pair<ServerInfo, String>> {
        lastDiscovery = System.currentTimeMillis()
        val answers = HashMap<String, Pair<ServerInfo, String>>()
        discover().forEach { address ->
            val info = client().serverInfo(address) ?: return@forEach
            if (info.key !in answers) answers[info.key] = info to address
        }
        // A name Moonlight knows can be in the network's DNS, as on a router that names its clients.
        (missing.keys - answers.keys).forEach { key ->
            val name = missing[key] ?: return@forEach
            for (address in lanAddresses(name)) {
                val info = client().serverInfo(address) ?: continue
                if (info.key != key) continue
                answers[key] = info to address
                break
            }
        }
        return answers
    }

    private fun remember(info: ServerInfo, address: String, key: String) {
        found[key] = LocatedHost(uuid = info.uniqueId, address = address, httpsPort = info.httpsPort ?: GAMESTREAM_HTTPS_PORT)
        if (key !in names && key !in paired) discovered[key] = info.hostname
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
        const val LIST_EVERY_MS = 5 * 60_000L
        const val DISCOVER_EVERY_MS = 60_000L
        const val LOOK_AROUND_MS = 5 * 60_000L
        const val DISCOVER_MS = 4_000L
        const val RESOLVE_MS = 3_000L
        const val DNS_MS = 3_000L

        /** A plain DNS label, as a computer name Moonlight shows usually is. */
        val HOST_NAME = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")
    }
}
