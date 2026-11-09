package app.foldcade.romm

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * Retries reads only. Never wrap a save upload or an entire save sync: a lost
 * reply can mean the write committed, and a fresh negotiation must decide next.
 * Cancellation interrupts both requests and backoff.
 *
 * A download says it [progressed] when it wrote bytes since its last failure.
 * That starts a fresh count, so a long download that keeps moving is never
 * given up on. Once it has moved, a lost route or a dropped TLS read is the
 * link, not a bad address, and it waits longer before it stops.
 */
class RommReadRetry(
    private val conversionLimitMillis: Long = 30 * 60_000L,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun <T> run(
        allowConversion: Boolean = false,
        onWait: (converting: Boolean) -> Unit = {},
        progressed: () -> Boolean = { false },
        read: suspend () -> T,
    ): T {
        var failures = 0
        var moving = false
        var conversions = 0
        var convertingSince: Long? = null
        var lastConversion: RommConversionPending? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            if (convertingSince?.let { nowMillis() - it >= conversionLimitMillis } == true) throw lastConversion!!
            try {
                return read()
            } catch (error: RommException) {
                if (progressed()) {
                    failures = 0
                    moving = true
                }
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
                    val transient = if (moving) error.transientTransferFailure() else error.transientReadFailure()
                    if (!transient || failures >= if (moving) TRANSFER_RETRIES else READ_RETRIES) throw error
                    (1_000L shl failures++).coerceAtMost(MAX_BACKOFF_MS)
                }
                onWait(converting)
                pause(wait)
            }
        }
    }
}

/** Retries of a read after its first failure. */
private const val READ_RETRIES = 3

/** Retries of a download that moved, with no new bytes between them: about a minute of waiting. */
private const val TRANSFER_RETRIES = 6

private const val MAX_BACKOFF_MS = 30_000L

internal fun retryableRommStatus(status: Int): Boolean = status in setOf(408, 429, 500, 502, 503, 504)

private fun RommException.transientReadFailure(): Boolean =
    (this is RommUnavailable && generateSequence(cause) { it.cause }.take(8).none {
        it is CleartextRejected || it is OriginRedirectRejected || it is javax.net.ssl.SSLException ||
            it is java.net.UnknownHostException || it is java.net.NoRouteToHostException
    }) ||
        (this is RommHttpException && retryableRommStatus(status))

/** A download already reached this server, so only a policy or certificate failure stops it. */
private fun RommException.transientTransferFailure(): Boolean =
    (this is RommUnavailable && generateSequence(cause) { it.cause }.take(8).none {
        it is CleartextRejected || it is OriginRedirectRejected ||
            it is javax.net.ssl.SSLHandshakeException || it is javax.net.ssl.SSLPeerUnverifiedException
    }) ||
        (this is RommHttpException && retryableRommStatus(status))
