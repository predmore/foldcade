package app.foldcade.gamestream

import app.foldcade.net.LanHttp
import app.foldcade.net.LanResponse
import app.foldcade.net.LanTls
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Duration
import java.util.UUID

/** The only paired requests an APK plugin may ask Foldcade to make. Never pair or unpair. */
val BROKER_PATHS: Set<String> = setOf("serverinfo", "applist", "launch", "resume", "cancel")
const val BROKER_MAX_REPLY_BYTES: Int = 1024 * 1024

/**
 * Keeps every other parameter's encoded spelling and order. Decode names before removing
 * client identity, including escaped names and repeats. Refuse ambiguous malformed escapes.
 */
fun brokerQuery(query: String?, clientId: String, requestId: String = UUID.randomUUID().toString()): String {
    val kept = brokerParameters(query).filter {
        !it.name.equals("uniqueid", ignoreCase = true) && !it.name.equals("uuid", ignoreCase = true)
    }.map { it.encoded }
    return (kept + "uniqueid=${URLEncoder.encode(clientId, "UTF-8")}" + "uuid=${URLEncoder.encode(requestId, "UTF-8")}").joinToString("&")
}

/** Every app id, including repeated and escaped names, decoded exactly as the sanitiser. */
fun brokerAppIds(query: String?): List<String> = brokerParameters(query)
    .filter { it.name.equals("appid", ignoreCase = true) }.map { it.value }

private data class BrokerParameter(val encoded: String, val name: String, val value: String)

private fun brokerParameters(query: String?): List<BrokerParameter> {
    val raw = query.orEmpty()
    require(raw.length <= 4096 && raw.none { it == '#' || it == '\n' || it == '\r' } && !raw.startsWith('?')) { "Invalid query" }
    return raw.split('&').filter { it.isNotEmpty() }.map { part ->
        BrokerParameter(part, URLDecoder.decode(part.substringBefore('='), "UTF-8"), URLDecoder.decode(part.substringAfter('=', ""), "UTF-8"))
    }
}

/** Uses the same LAN guard, keystore key manager and paired certificate as ordinary GameStream. */
suspend fun brokerRequest(host: PairedHost, identity: ClientIdentity, clientId: String, path: String, query: String?): LanResponse {
    require(path in BROKER_PATHS) { "Invalid path" }
    val sanitized = brokerQuery(query, clientId)
    LanHttp(tls = LanTls(identity.keyManager, host.certificate)).use { http ->
        return http.get(
            "https://${hostPart(host.address)}:${host.httpsPort}/$path?$sanitized",
            maxBytes = BROKER_MAX_REPLY_BYTES,
            readTimeout = Duration.ofSeconds(60).takeIf { path == "launch" || path == "resume" },
        )
    }
}
