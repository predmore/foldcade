package app.foldcade.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Duration

/**
 * GETs to a computer on the local network, such as a game-streaming host.
 *
 * Every hop must go to a host [isLanHost] accepts: a private, link-local,
 * loopback, or tailnet address, or a `.local`, `.home.arpa`, or `.ts.net` name.
 * Redirects are not followed. A caller that knows a host only by a bare name
 * resolves it and asks by address.
 */
class LanHttp(
    connectTimeout: Duration = Duration.ofSeconds(2),
    readTimeout: Duration = Duration.ofSeconds(4),
) : AutoCloseable {
    private val http = HttpStack.client(
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        writeTimeout = readTimeout,
        followSslRedirects = false,
        followRedirects = false,
        network = listOf(LanGuard),
    )

    /** True when [url] is http or https to a LAN host. Nothing is sent. */
    fun allows(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return isLanHost(parsed.host)
    }

    /**
     * One GET. Any status comes back. A body over [maxBytes], a refused
     * address, or an unreachable host fails with [LanHttpException].
     * Cancelling the caller cancels the call.
     */
    suspend fun get(url: String, maxBytes: Int = MAX_BODY_BYTES): LanResponse = withContext(Dispatchers.IO) {
        if (!allows(url)) throw LanHttpException("Refused $url")
        val request = Request.Builder().url(url).get().build()
        try {
            http.newCall(request).readResponse { open ->
                val body = open.body ?: return@readResponse LanResponse(open.code, ByteArray(0))
                if (body.contentLength() > maxBytes) throw LanHttpException("Body is over $maxBytes bytes")
                val bytes = body.byteStream().use { stream ->
                    val read = stream.readNBytes(maxBytes + 1)
                    if (read.size > maxBytes) throw LanHttpException("Body is over $maxBytes bytes")
                    read
                }
                LanResponse(open.code, bytes)
            }
        } catch (e: LanHttpException) {
            throw e
        } catch (e: IOException) {
            throw LanHttpException(e.message ?: "Unreachable", e)
        }
    }

    override fun close() {
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    companion object {
        const val MAX_BODY_BYTES: Int = 4 * 1024 * 1024
    }
}

class LanResponse(val status: Int, val body: ByteArray)

class LanHttpException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Refuses every hop that is not to a LAN host. */
private object LanGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!isLanHost(url.host)) throw LanHttpException("Refused ${url.scheme}://${url.host}")
        return chain.proceed(chain.request())
    }
}
