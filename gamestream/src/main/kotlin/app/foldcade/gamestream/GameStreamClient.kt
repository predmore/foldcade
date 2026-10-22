package app.foldcade.gamestream

import app.foldcade.net.LanHttp
import app.foldcade.net.LanHttpException
import app.foldcade.net.LanTls
import java.net.URLEncoder
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Asks GameStream hosts (Sunshine, Apollo) on the local network about
 * themselves. Every request goes through a [LanHttp], which reaches LAN hosts only.
 *
 * [clientId] is this device's GameStream id, 16 hex digits, the same shape
 * Moonlight sends. It is not Moonlight's id. [identity] is needed only to pair
 * and for what a paired host answers over https.
 */
class GameStreamClient(
    private val http: LanHttp,
    private val clientId: String,
    private val identity: ClientIdentity? = null,
) {
    init {
        require(CLIENT_ID.matches(clientId)) { "client id" }
    }

    /** One https client per paired host, each trusting only that host's certificate. */
    private val paired = ConcurrentHashMap<X509Certificate, LanHttp>()

    /**
     * The host at [address] (an IP literal), or null when it does not answer,
     * answers with an error, or is not a GameStream host.
     */
    suspend fun serverInfo(address: String, port: Int = GAMESTREAM_HTTP_PORT): ServerInfo? =
        text(http, url("http", address, port, "serverinfo", emptyList()))?.let(::parseServerInfo)

    /** The same, asked over https as this paired device, so Apollo names its running app too. */
    suspend fun serverInfo(host: PairedHost): ServerInfo? =
        text(tls(host), url("https", host.address, host.httpsPort, "serverinfo", emptyList()))?.let(::parseServerInfo)

    /** The host's apps, in its order. Null when it does not answer or refuses this device. */
    suspend fun apps(host: PairedHost): List<HostApp>? =
        text(tls(host), url("https", host.address, host.httpsPort, "applist", emptyList()))?.let(::parseAppList)

    /** The box art the host keeps for [appId], or null when it has none or does not answer. */
    suspend fun boxArt(host: PairedHost, appId: String): ByteArray? {
        require(isAppId(appId)) { "app id" }
        val query = listOf("appid" to appId, "AssetType" to "2", "AssetIdx" to "0")
        val reply = call(tls(host), url("https", host.address, host.httpsPort, "appasset", query), MAX_ART_BYTES)
        return reply?.takeIf { it.status == 200 && it.body.isNotEmpty() && !it.body.looksLikeXml() }?.body
    }

    /**
     * Pairing's requests to the host at [address]. Each pairing goes under an id
     * of its own: a host holds an abandoned one for five minutes, and a paired
     * device is known by its certificate, not by that id.
     */
    fun pairLink(address: String, httpsPort: Int, port: Int = GAMESTREAM_HTTP_PORT): PairLink {
        val attempt = newClientId()
        val base = listOf("devicename" to DEVICE_NAME, "updateState" to "1")
        return object : PairLink {
            override suspend fun pair(query: List<Pair<String, String>>, waitForUser: Boolean): String? {
                val wait = if (waitForUser) PIN_WAIT else null
                val reply = call(http, url("http", address, port, "pair", base + query, attempt), MAX_REPLY_BYTES, wait)
                return reply?.takeIf { it.status == 200 }?.body?.decodeToString()
            }

            override suspend fun pairChallenge(server: X509Certificate): String? {
                val link = tls(PairedHost(address, httpsPort, server))
                return text(link, url("https", address, httpsPort, "pair", base + ("phrase" to "pairchallenge"), attempt))
            }
        }
    }

    private fun tls(host: PairedHost): LanHttp {
        val keys = identity?.keyManager ?: throw IllegalStateException("No identity to pair with")
        return paired.getOrPut(host.certificate) { LanHttp(tls = LanTls(keys, host.certificate)) }
    }

    private suspend fun text(client: LanHttp, url: String): String? =
        call(client, url, MAX_REPLY_BYTES)?.takeIf { it.status == 200 }?.body?.decodeToString()

    private suspend fun call(client: LanHttp, url: String, maxBytes: Int, readTimeout: Duration? = null) = try {
        client.get(url, maxBytes = maxBytes, readTimeout = readTimeout)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: LanHttpException) {
        null
    }

    private fun url(
        scheme: String,
        address: String,
        port: Int,
        path: String,
        query: List<Pair<String, String>>,
        id: String = clientId,
    ): String {
        val all = query + ("uniqueid" to id) + ("uuid" to UUID.randomUUID().toString())
        val encoded = all.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
        return "$scheme://${hostPart(address)}:$port/$path?$encoded"
    }

    private fun ByteArray.looksLikeXml(): Boolean = size >= 5 && decodeToString(0, 5) == "<?xml"

    companion object {
        /** The name a host shows for this device while it waits for the code. */
        const val DEVICE_NAME: String = "Foldcade"

        /** Sunshine drops a pairing no one finished after five minutes. */
        private val PIN_WAIT: Duration = Duration.ofMinutes(5).plusSeconds(15)
        private const val MAX_REPLY_BYTES = 256 * 1024
        private const val MAX_ART_BYTES = 8 * 1024 * 1024
        private val CLIENT_ID = Regex("[0-9a-f]{16}")

        /** 16 random hex digits, the shape a GameStream client id has. */
        fun newClientId(): String {
            val random = java.security.SecureRandom()
            return buildString { repeat(16) { append("0123456789abcdef"[random.nextInt(16)]) } }
        }
    }
}

/** A host this device paired with, where it last answered. */
data class PairedHost(
    val address: String,
    val httpsPort: Int,
    val certificate: X509Certificate,
)

/** One app a host lists. [id] is the decimal id Moonlight starts it by. */
data class HostApp(val id: String, val title: String)

/** Null when [xml] is not a successful `/applist` reply. Apps without a title or a valid id are skipped. */
fun parseAppList(xml: String): List<HostApp>? {
    val root = readFlatXml(xml) ?: return null
    if (root.status != 200) return null
    return root.groups("App").mapNotNull { app ->
        val id = app.first("ID")?.trim()?.takeIf(::isAppId) ?: return@mapNotNull null
        val title = app.first("AppTitle")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        HostApp(id = id, title = title)
    }.distinctBy { it.id }
}

/** [address] as the host part of a URL: an IPv6 literal goes in brackets. */
internal fun hostPart(address: String): String =
    if (address.contains(':') && !address.startsWith("[")) "[${address.substringBefore('%')}]" else address
