package app.foldcade.romm

import app.foldcade.net.LanHttp
import app.foldcade.net.isLanHost
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration

/**
 * A small hint, never an exhaustive scan: twelve likely IPv4 neighbours, one port each,
 * once per empty sign-in form. RomM does not advertise a Zeroconf service.
 * The official 5.4.1 compose maps port 80 to 8080; only that default host port is tried:
 * https://github.com/rommapp/romm/blob/5.4.1/examples/docker-compose.example.yml
 */
suspend fun findNearbyRomm(address: String): String? = withTimeoutOrNull(2_500) {
    LanHttp(connectTimeout = Duration.ofMillis(500), readTimeout = Duration.ofMillis(700)).use { http ->
        coroutineScope {
            rommCandidates(address).map { origin -> async {
                try {
                    val response = http.get("$origin/api/heartbeat", maxBytes = 64 * 1024)
                    origin.takeIf { response.status == 200 && isRommHeartbeat(response.body.toString(Charsets.UTF_8)) }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            } }.awaitAll().firstOrNull { it != null }
        }
    }
}

internal fun rommCandidates(address: String): List<String> {
    if (!isLanHost(address)) return emptyList()
    val octets = address.split('.').map { it.toIntOrNull() ?: return emptyList() }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return emptyList()
    val prefix = octets.take(3).joinToString(".")
    return listOf(1, 2, 3, 10, 20, 50, 100, 200, octets[3] - 2, octets[3] - 1, octets[3] + 1, octets[3] + 2)
        .filter { it in 1..254 && it != octets[3] }.distinct().map { "http://$prefix.$it" }
}

/**
 * This unauthenticated route identifies RomM by several independent config groups, not a 200 alone.
 * https://github.com/rommapp/romm/blob/5.4.1/backend/endpoints/heartbeat.py#L91
 */
internal fun isRommHeartbeat(body: String): Boolean = runCatching {
    val root = Json.parseToJsonElement(body).jsonObject
    val version = root["SYSTEM"]?.jsonObject?.get("VERSION")?.jsonPrimitive?.content
    !version.isNullOrBlank() && root["METADATA_SOURCES"]?.jsonObject?.containsKey("ANY_SOURCE_ENABLED") == true &&
        root["EMULATION"]?.jsonObject?.containsKey("DISABLE_EMULATOR_JS") == true
}.getOrDefault(false)
