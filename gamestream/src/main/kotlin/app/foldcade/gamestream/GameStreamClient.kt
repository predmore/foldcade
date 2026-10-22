package app.foldcade.gamestream

import app.foldcade.net.LanHttp
import app.foldcade.net.LanHttpException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Asks GameStream hosts (Sunshine, Apollo) on the local network about
 * themselves. Every request goes through [http], which reaches LAN hosts only.
 *
 * [clientId] is this device's GameStream id, 16 hex digits, the same shape
 * Moonlight sends. It is not Moonlight's id.
 */
class GameStreamClient(
    private val http: LanHttp,
    private val clientId: String,
) {
    init {
        require(CLIENT_ID.matches(clientId)) { "client id" }
    }

    /**
     * The host at [address] (an IP literal), or null when it does not answer,
     * answers with an error, or is not a GameStream host.
     */
    suspend fun serverInfo(address: String, port: Int = GAMESTREAM_HTTP_PORT): ServerInfo? {
        val url = "http://${hostPart(address)}:$port/serverinfo?uniqueid=$clientId"
        val reply = try {
            http.get(url, maxBytes = MAX_REPLY_BYTES)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LanHttpException) {
            return null
        }
        if (reply.status != 200) return null
        return parseServerInfo(reply.body.decodeToString())
    }

    private companion object {
        const val MAX_REPLY_BYTES = 64 * 1024
        val CLIENT_ID = Regex("[0-9a-f]{16}")
    }
}

/** [address] as the host part of a URL: an IPv6 literal goes in brackets. */
internal fun hostPart(address: String): String =
    if (address.contains(':') && !address.startsWith("[")) "[${address.substringBefore('%')}]" else address
