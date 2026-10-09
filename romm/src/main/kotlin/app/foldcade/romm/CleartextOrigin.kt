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
