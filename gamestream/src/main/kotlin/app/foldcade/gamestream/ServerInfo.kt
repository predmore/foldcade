package app.foldcade.gamestream

import java.util.Locale

/**
 * What a GameStream host says about itself at `/serverinfo`.
 *
 * Read from Sunshine's `nvhttp.cpp` (`serverinfo`):
 * - `uniqueid` is the host's UUID, kept across restarts. Moonlight stores it
 *   as the computer's UUID, as sent, so [uniqueId] keeps its case.
 * - Over plain http, `currentgame` and `state` are real on Sunshine. Apollo
 *   blanks them to 0 and FREE there, so [runningAppId] is null on Apollo.
 * - `mac` is all zeros unless the request came over paired https.
 */
data class ServerInfo(
    val uniqueId: String,
    val hostname: String,
    val httpsPort: Int?,
    /** The app the host is streaming now, or null when it is free. */
    val runningAppId: String?,
    /** The host's MAC address, or null when it did not say. */
    val mac: String?,
) {
    /** [uniqueId] in lower case, the form Foldcade keys a host by. */
    val key: String get() = uniqueId.lowercase(Locale.ROOT)
}

/** Null when [xml] is not a successful `/serverinfo` reply with a host UUID. */
fun parseServerInfo(xml: String): ServerInfo? {
    val root = readFlatXml(xml) ?: return null
    if (root.status != 200) return null
    val uniqueId = root.first("uniqueid")?.trim()?.takeIf(::isUuid) ?: return null
    val busy = root.first("state")?.trim()?.endsWith("_SERVER_BUSY") == true
    val running = root.first("currentgame")?.trim()?.takeIf { busy && isAppId(it) && it != "0" }
    return ServerInfo(
        uniqueId = uniqueId,
        hostname = root.first("hostname")?.trim()?.takeIf { it.isNotEmpty() } ?: uniqueId,
        httpsPort = root.first("HttpsPort")?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 },
        runningAppId = running,
        mac = root.first("mac")?.trim()?.takeIf { it.isNotEmpty() && it.any { c -> c != '0' && c != ':' } },
    )
}

/** The GameStream port Sunshine and Apollo answer plain http on. */
const val GAMESTREAM_HTTP_PORT: Int = 47989

/** Their https port, when a host does not say. */
const val GAMESTREAM_HTTPS_PORT: Int = 47984

/** True for an app id a host sends: a non-negative 32-bit decimal. */
fun isAppId(value: String): Boolean =
    value.isNotEmpty() && value.length <= 10 && value.all { it in '0'..'9' } && value.toLong() <= Int.MAX_VALUE

internal fun isUuid(value: String): Boolean = UUID_SHAPE.matches(value)

private val UUID_SHAPE = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
