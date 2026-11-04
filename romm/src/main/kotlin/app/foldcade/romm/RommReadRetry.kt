package app.foldcade.romm

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * Retries reads only. Never wrap a save upload or an entire save sync: a lost
 * reply can mean the write committed, and a fresh negotiation must decide next.
 * Cancellation interrupts both requests and backoff.
 */
class RommReadRetry(
    private val conversionLimitMillis: Long = 30 * 60_000L,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun <T> run(
        allowConversion: Boolean = false,
        onWait: (converting: Boolean) -> Unit = {},
        read: suspend () -> T,
    ): T {
        var failures = 0
        var conversions = 0
        var convertingSince: Long? = null
        var lastConversion: RommConversionPending? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            if (convertingSince?.let { nowMillis() - it >= conversionLimitMillis } == true) throw lastConversion!!
            try {
                return read()
            } catch (error: RommException) {
                val converting = allowConversion && error is RommConversionPending
                val wait = if (converting) {
                    // RomM GET /api/roms/{id}/content/{file_name}: empty 202,
                    // Retry-After in seconds. We do not request ?format=.
                    // https://github.com/rommapp/romm/blob/d328779435e72bb9dfb6151364478619a1f34c31/backend/endpoints/roms/__init__.py#L1480-L1502
                    val started = convertingSince ?: nowMillis().also { convertingSince = it }
                    lastConversion = error as RommConversionPending
                    val remaining = conversionLimitMillis - (nowMillis() - started)
                    val seconds = error.retryAfterSeconds?.takeIf { it > 0 }
                        ?: (2L shl conversions.coerceAtMost(4)).coerceAtMost(30)
                    if (remaining <= 0 || seconds > remaining / 1_000) throw error
                    conversions++
                    seconds * 1_000
                } else {
                    if (!error.transientReadFailure() || failures >= 3) throw error
                    1_000L shl failures++
                }
                onWait(converting)
                pause(wait)
            }
        }
    }
}

internal fun retryableRommStatus(status: Int): Boolean = status in setOf(408, 429, 500, 502, 503, 504)

private fun RommException.transientReadFailure(): Boolean =
    (this is RommUnavailable && generateSequence(cause) { it.cause }.take(8).none {
        it is CleartextRejected || it is OriginRedirectRejected || it is javax.net.ssl.SSLException ||
            it is java.net.UnknownHostException || it is java.net.NoRouteToHostException
    }) ||
        (this is RommHttpException && retryableRommStatus(status))
