package app.foldcade.romm

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Logs the request line and headers. Authorization and cookie headers are
 * replaced. Bodies are not logged. A bearer token in the remaining text is
 * replaced as well.
 */
class RedactingLoggingInterceptor(
    private val log: (String) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        log(redactSensitive(format(request.method, request.url.toString(), request.headers)))
        val response = chain.proceed(request)
        log(redactSensitive(format("HTTP ${response.code}", response.request.url.toString(), response.headers)))
        return response
    }

    private fun format(start: String, url: String, headers: okhttp3.Headers): String = buildString {
        append(start)
        append(' ')
        append(url)
        for (i in 0 until headers.size) {
            append('\n')
            append(headers.name(i))
            append(": ")
            append(headers.value(i))
        }
    }
}

private val sensitiveHeaders = setOf(
    "authorization",
    "proxy-authorization",
    "cookie",
    "set-cookie",
)

private val bearer = Regex("(?i)\\bBearer\\s+\\S+")

fun redactSensitive(message: String): String {
    val lines = message.lineSequence().map { line ->
        val split = line.indexOf(':')
        if (split <= 0) return@map line
        val name = line.substring(0, split).trim()
        if (name.lowercase() !in sensitiveHeaders) line else "$name: ***"
    }
    return bearer.replace(lines.joinToString("\n")) { "Bearer ***" }
}
