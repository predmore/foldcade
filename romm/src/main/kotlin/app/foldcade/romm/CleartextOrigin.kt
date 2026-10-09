package app.foldcade.romm

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.URI

/**
 * Cleartext is permitted for the process so a home-LAN RomM can use http.
 * [RommClient] is the only HTTP stack. This interceptor rejects any http
 * request, including a redirect, whose scheme, host, and port are not the
 * configured origin.
 */
internal class RommCleartextInterceptor(
    private val origin: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        rejectUnlessRomOrigin(origin, request.url.toString())
        val response = chain.proceed(request)
        if (response.code !in 300..399) return response
        val location = response.header("Location") ?: return response
        val next = request.url.resolve(location)?.toString() ?: location
        if (httpCleartextAllowed(origin, next)) return response
        response.close()
        throw CleartextRejected(next)
    }
}

internal class CleartextRejected(val url: String) : IOException(
    "Cleartext request is not the configured RomM origin",
)

internal fun rejectUnlessRomOrigin(origin: String, url: String) {
    if (!httpCleartextAllowed(origin, url)) throw CleartextRejected(url)
}

/**
 * Https targets are allowed. An http target is allowed only when its scheme,
 * host, and port match [origin]. An omitted port is 80 for http and 443 for https.
 */
internal fun httpCleartextAllowed(origin: String, target: String): Boolean {
    val request = endpointOf(target) ?: return !target.trim().startsWith("http://", ignoreCase = true)
    if (request.scheme != "http") return true
    val allowed = endpointOf(origin) ?: return false
    return request == allowed
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
