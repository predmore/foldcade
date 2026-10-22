package app.foldcade.net

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.time.Duration

/**
 * The app's only HTTP stack. This module is the one place an [OkHttpClient]
 * is built; `HttpStackGuardTest` fails a build that makes one anywhere else.
 *
 * Each caller states its own policy here and gets its own client, so closing
 * one never stops another:
 * - RomM's client follows its origin and its cleartext rule.
 * - [PublicHttps] reaches a fixed list of artwork hosts over HTTPS only.
 * - [LanHttp] reaches computers on the local network and follows no redirect.
 */
object HttpStack {
    /**
     * A client with these timeouts. [network] interceptors see every hop,
     * redirects included, so a guard there holds for the whole call.
     * [application] interceptors see the call once.
     */
    fun client(
        connectTimeout: Duration,
        readTimeout: Duration,
        writeTimeout: Duration,
        followSslRedirects: Boolean,
        followRedirects: Boolean = true,
        network: List<Interceptor> = emptyList(),
        application: List<Interceptor> = emptyList(),
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeout)
        .readTimeout(readTimeout)
        .writeTimeout(writeTimeout)
        .followRedirects(followRedirects)
        .followSslRedirects(followSslRedirects)
        .apply {
            network.forEach { addNetworkInterceptor(it) }
            application.forEach { addInterceptor(it) }
        }
        .build()
}
