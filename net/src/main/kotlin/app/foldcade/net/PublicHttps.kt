package app.foldcade.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * GETs from a fixed list of public hosts, over HTTPS only.
 *
 * A host in [hosts] matches exactly. An entry that starts with a dot, such as
 * `.steamgriddb.com`, matches that domain's subdomains. A request, or any
 * redirect hop, to another host or to plain http fails before it is sent.
 * OkHttp drops an `Authorization` header when a redirect changes host, so a
 * key sent to one API does not follow a redirect onto an image CDN.
 */
class PublicHttps(
    private val hosts: Set<String>,
    connectTimeout: Duration = Duration.ofSeconds(10),
    readTimeout: Duration = Duration.ofSeconds(20),
) : AutoCloseable {
    private val http = HttpStack.client(
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        writeTimeout = readTimeout,
        followSslRedirects = false,
        network = listOf(HostGuard(hosts)),
    )

    /** True when [url] is HTTPS to one of [hosts]. Nothing is sent. */
    fun allows(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.isHttps && hostAllowed(hosts, parsed.host)
    }

    /**
     * One GET. Any status comes back, so a caller can read a 404 as a miss.
     * A body over [maxBytes] fails with [PublicHttpsException], as does a
     * refused address or an unreachable host. Cancelling the caller cancels the call.
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Int = MAX_BODY_BYTES,
    ): PublicResponse = withContext(Dispatchers.IO) {
        call(url, "GET", headers).use { open ->
            val body = open.body ?: return@use PublicResponse(open.code, ByteArray(0))
            val declared = body.contentLength()
            if (declared > maxBytes) throw PublicHttpsException("Body is over $maxBytes bytes")
            val bytes = body.byteStream().use { stream ->
                val read = stream.readNBytes(maxBytes + 1)
                if (read.size > maxBytes) throw PublicHttpsException("Body is over $maxBytes bytes")
                read
            }
            PublicResponse(open.code, bytes)
        }
    }

    /** One HEAD request's status, so a caller can check an image exists without its bytes. */
    suspend fun status(url: String, headers: Map<String, String> = emptyMap()): Int = withContext(Dispatchers.IO) {
        call(url, "HEAD", headers).use { it.code }
    }

    private suspend fun call(url: String, method: String, headers: Map<String, String>): Response {
        if (!allows(url)) throw PublicHttpsException("Refused $url")
        val request = Request.Builder().url(url).method(method, null).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        return try {
            http.newCall(request).await()
        } catch (e: IOException) {
            throw PublicHttpsException(e.message ?: "Unreachable", e)
        }
    }

    override fun close() {
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    companion object {
        const val MAX_BODY_BYTES: Int = 8 * 1024 * 1024
    }
}

class PublicResponse(val status: Int, val body: ByteArray)

class PublicHttpsException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal fun hostAllowed(hosts: Set<String>, host: String): Boolean {
    val name = host.lowercase().removeSuffix(".")
    return hosts.any { entry ->
        val allowed = entry.lowercase()
        if (allowed.startsWith(".")) name.endsWith(allowed) && name.length > allowed.length else name == allowed
    }
}

/** Refuses every hop that is not HTTPS to an allowed host, redirects included. */
private class HostGuard(private val hosts: Set<String>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!url.isHttps || !hostAllowed(hosts, url.host)) {
            throw PublicHttpsException("Refused ${url.scheme}://${url.host}")
        }
        return chain.proceed(chain.request())
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, _, _ -> response.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    })
}
