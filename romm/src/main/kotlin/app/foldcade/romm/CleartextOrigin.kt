package app.foldcade.romm

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.URI

/**
 * Cleartext is an explicit LAN opt-in. [RommClient] accepts an http origin
 * only for a loopback address, a private address, a link-local address, a
 * Tailscale address, or a localhost, `.local`, `.home.arpa`, or `.ts.net` name. This interceptor rejects any
 * other http request, including a redirect. Android cannot name a dynamic
 * LAN address in the network-security config, so that file still permits
 * cleartext and this check is the opt-in.
 */
internal class RommCleartextInterceptor(
    private val origin: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        rejectUnlessRomOrigin(origin, url)
        // A follow-up OkHttp already built for another origin must not leave with the bearer.
        if (request.header("Authorization") != null && !sameOrigin(origin, url)) {
            throw OriginRedirectRejected(url)
        }
        val response = chain.proceed(request)
        if (response.code !in 300..399) return response
        val location = response.header("Location") ?: return response
        val next = request.url.resolve(location)?.toString() ?: location
        if (!httpCleartextAllowed(origin, next)) {
            response.close()
            throw CleartextRejected(next)
        }
        if (sameSchemeRedirectLeavesOrigin(origin, next)) {
            response.close()
            throw OriginRedirectRejected(next)
        }
        return response
    }
}

internal class CleartextRejected(val url: String) : IOException(
    "Cleartext request is not the configured RomM origin",
)

/** A same-scheme redirect targeted a different scheme, host, or port. */
internal class OriginRedirectRejected(val url: String) : IOException(
    "RomM redirect left the configured origin",
)

internal fun rejectUnlessRomOrigin(origin: String, url: String) {
    if (!httpCleartextAllowed(origin, url)) throw CleartextRejected(url)
}

/**
 * OkHttp follows a same-scheme redirect. That follow-up would carry the
 * bearer when the target stays on [origin], and must not when it does not.
 * A scheme change is not followed (`followSslRedirects` is false).
 */
internal fun sameSchemeRedirectLeavesOrigin(origin: String, target: String): Boolean {
    val from = endpointOf(origin) ?: return false
    val to = endpointOf(target) ?: return false
    return from.scheme == to.scheme && from != to
}

internal fun sameOrigin(origin: String, target: String): Boolean {
    val from = endpointOf(origin) ?: return false
    val to = endpointOf(target) ?: return false
    return from == to
}

/**
 * Https targets are allowed. An http target is allowed only when its host is a
 * LAN address and its scheme, host, and port match [origin]. An omitted port
 * is 80 for http and 443 for https.
 */
internal fun httpCleartextAllowed(origin: String, target: String): Boolean {
    val request = endpointOf(target) ?: return !target.trim().startsWith("http://", ignoreCase = true)
    if (request.scheme != "http") return true
    if (!isLanHost(request.host)) return false
    val allowed = endpointOf(origin) ?: return false
    return request == allowed
}

/**
 * True for a host that is unambiguously on the local network.
 * A public name is not, even when it has no public suffix.
 * This does not resolve DNS.
 *
 * A tailnet counts: `100.64.0.0/10` and `.ts.net` names. WireGuard encrypts
 * that hop. The same /10 is also carrier-grade NAT space. Off a tailnet, an
 * http origin there crosses the carrier's network in clear.
 * Tailscale's IPv6 range is unique-local and already passes as `fc00::/7`.
 */
internal fun isLanHost(raw: String): Boolean {
    var host = raw.trim().lowercase()
    if (host.startsWith("[") && host.endsWith("]") && host.length > 2) {
        host = host.substring(1, host.length - 1)
    }
    host = host.substringBefore('%')
    if (host.endsWith(".")) host = host.dropLast(1)
    if (host.isEmpty()) return false
    if (host == "localhost" || dnsSuffix(host, ".localhost")) return true
    if (dnsSuffix(host, ".local") || dnsSuffix(host, ".home.arpa")) return true
    if (dnsSuffix(host, ".ts.net")) return true
    if (host.contains(':')) return ipv6Lan(host)
    return ipv4Lan(host)
}

/** A `.ts.net` name or an address in Tailscale's 100.64.0.0/10. */
internal fun isTailnetHost(raw: String): Boolean {
    val host = raw.trim().lowercase().removeSuffix(".")
    if (dnsSuffix(host, ".ts.net")) return true
    val octets = ipv4Octets(host) ?: return false
    return octets[0] == 100 && octets[1] in 64..127
}

private fun dnsSuffix(host: String, suffix: String): Boolean =
    host.endsWith(suffix) && host.length > suffix.length

private fun ipv4Lan(host: String): Boolean {
    val octets = ipv4Octets(host) ?: return false
    val a = octets[0]
    val b = octets[1]
    if (a == 127) return true
    if (a == 10) return true
    if (a == 192 && b == 168) return true
    if (a == 169 && b == 254) return true
    if (a == 100 && b in 64..127) return true
    return a == 172 && b in 16..31
}

private fun ipv4Octets(host: String): IntArray? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    val nums = IntArray(4)
    for (index in 0..3) {
        val part = parts[index]
        if (part.isEmpty() || part.length > 3) return null
        if (part.any { it !in '0'..'9' }) return null
        if (part.length > 1 && part[0] == '0') return null
        val value = part.toInt()
        if (value > 255) return null
        nums[index] = value
    }
    return nums
}

private fun ipv6Lan(host: String): Boolean {
    val bytes = ipv6Bytes(host) ?: return false
    if (isIpv6Loopback(bytes)) return true
    val first = bytes[0].toInt() and 0xff
    val second = bytes[1].toInt() and 0xff
    if (first == 0xfe && (second and 0xc0) == 0x80) return true
    if ((first and 0xfe) == 0xfc) return true
    if (isV4Mapped(bytes)) {
        val mapped = "${bytes[12].toInt() and 0xff}.${bytes[13].toInt() and 0xff}." +
            "${bytes[14].toInt() and 0xff}.${bytes[15].toInt() and 0xff}"
        return ipv4Lan(mapped)
    }
    return false
}

private fun isIpv6Loopback(bytes: ByteArray): Boolean {
    for (index in 0..14) if (bytes[index] != 0.toByte()) return false
    return bytes[15] == 1.toByte()
}

private fun isV4Mapped(bytes: ByteArray): Boolean {
    for (index in 0..9) if (bytes[index] != 0.toByte()) return false
    return bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
}

private fun ipv6Bytes(host: String): ByteArray? {
    if (host.count { it == ':' } < 2) return null
    if (host.indexOf("::") != host.lastIndexOf("::")) return null
    val compressed = host.contains("::")
    val left = splitV6(if (compressed) host.substringBefore("::") else host) ?: return null
    val right = splitV6(if (compressed) host.substringAfter("::") else "") ?: return null
    if (!compressed && right.isNotEmpty()) return null
    val leftSlots = v6Slots(left)
    val rightSlots = v6Slots(right)
    if (leftSlots < 0 || rightSlots < 0) return null
    val missing = 8 - leftSlots - rightSlots
    if (compressed) {
        if (missing < 1) return null
    } else if (missing != 0) {
        return null
    }
    val out = ByteArray(16)
    var index = writeGroups(out, 0, left) ?: return null
    if (compressed) index += missing * 2
    if (index > 16) return null
    index = writeGroups(out, index, right) ?: return null
    if (index != 16) return null
    return out
}

private fun splitV6(part: String): List<String>? {
    if (part.isEmpty()) return emptyList()
    val groups = part.split(':')
    if (groups.any { it.isEmpty() }) return null
    return groups
}

private fun v6Slots(groups: List<String>): Int {
    if (groups.any { it.contains('.') }) {
        if (!groups.last().contains('.')) return -1
        return groups.size + 1
    }
    return groups.size
}

private fun writeGroups(out: ByteArray, start: Int, groups: List<String>): Int? {
    var index = start
    for (group in groups) {
        if (group.contains('.')) {
            val octets = ipv4Octets(group) ?: return null
            if (index + 4 > out.size) return null
            for (octet in octets) out[index++] = octet.toByte()
            continue
        }
        if (group.length > 4 || group.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        val value = group.toInt(16)
        if (index + 2 > out.size) return null
        out[index++] = (value shr 8).toByte()
        out[index++] = (value and 0xff).toByte()
    }
    return index
}

private data class Endpoint(val scheme: String, val host: String, val port: Int)

private fun endpointOf(url: String): Endpoint? {
    val uri = try {
        URI(url.trim())
    } catch (_: IllegalArgumentException) {
        return null
    }
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return null
    if (host.isEmpty()) return null
    val port = when {
        uri.port != -1 -> uri.port
        scheme == "http" -> 80
        scheme == "https" -> 443
        else -> return null
    }
    return Endpoint(scheme, host, port)
}
