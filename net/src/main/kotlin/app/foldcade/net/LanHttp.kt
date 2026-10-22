package app.foldcade.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

/**
 * GETs to a computer on the local network, such as a game-streaming host.
 *
 * Every hop must go to a host [isLanHost] accepts: a private, link-local,
 * loopback, or tailnet address, or a `.local`, `.home.arpa`, or `.ts.net` name.
 * Redirects are not followed. A caller that knows a host only by a bare name
 * resolves it and asks by address.
 *
 * With [tls], https goes to one host only: the one holding [LanTls.server].
 * Without it, https trusts the platform's authorities, which a LAN host's
 * self-signed certificate never passes.
 */
class LanHttp(
    connectTimeout: Duration = Duration.ofSeconds(2),
    readTimeout: Duration = Duration.ofSeconds(4),
    tls: LanTls? = null,
) : AutoCloseable {
    private val http = HttpStack.client(
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        writeTimeout = readTimeout,
        followSslRedirects = false,
        followRedirects = false,
        network = listOf(LanGuard),
        tls = tls?.stack(),
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
    suspend fun get(
        url: String,
        maxBytes: Int = MAX_BODY_BYTES,
        readTimeout: Duration? = null,
    ): LanResponse = withContext(Dispatchers.IO) {
        if (!allows(url)) throw LanHttpException("Refused $url")
        val request = Request.Builder().url(url).get().build()
        // A longer wait shares this client's connections; only the timeout differs.
        val client = readTimeout?.let { http.newBuilder().readTimeout(it).build() } ?: http
        try {
            client.newCall(request).readResponse { open ->
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

/**
 * https to one paired LAN host: [keyManager] presents this device's certificate,
 * and only [server], the certificate the host gave when it paired, is trusted.
 * The host is known by that certificate, not by a name, so no name is checked.
 */
class LanTls(
    val keyManager: X509KeyManager,
    val server: X509Certificate,
) {
    internal fun stack(): HttpStack.Tls {
        val trust = PinnedTrust(server)
        val context = SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), arrayOf(trust), null) }
        val verifier = HostnameVerifier { _, session ->
            (session.peerCertificates.firstOrNull() as? X509Certificate) == server
        }
        return HttpStack.Tls(context.socketFactory, trust, verifier)
    }
}

/** Trusts [server] and nothing else, whatever signed it. */
private class PinnedTrust(private val server: X509Certificate) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty() || chain[0] != server) throw CertificateException("Not the paired host")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Not a server")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

class LanHttpException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Refuses every hop that is not to a LAN host. */
private object LanGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!isLanHost(url.host)) throw LanHttpException("Refused ${url.scheme}://${url.host}")
        return chain.proceed(chain.request())
    }
}
