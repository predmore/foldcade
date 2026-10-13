package app.foldcade

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** New games on a server or in a folder show up within this long while Home is in front. */
internal const val RESYNC_INTERVAL_MILLIS = 15 * 60_000L

/**
 * Reads every library again with [work], one sync at a time. It runs on its own while
 * Home is shown: as Home comes forward once the last sync is [intervalMillis] old, then
 * every [intervalMillis] while it stays. A screen that sleeps or is covered stops the
 * clock. [onRunning] hears a sync start and end. Main thread only.
 */
internal class LibraryResync(
    private val scope: CoroutineScope,
    private val intervalMillis: Long,
    private val now: () -> Long,
    private val onRunning: (Boolean) -> Unit,
    private val work: suspend () -> Unit,
) {
    /** Built with the app, so the start-up read counts as the first sync. */
    private var lastMillis = now()
    private var running: Job? = null
    private var ticking: Job? = null

    /** Milliseconds until the next automatic sync. Zero means one is due. */
    fun untilDue(): Long = (lastMillis + intervalMillis - now()).coerceAtLeast(0)

    /** Starts a sync, or joins the one running. Either way the next automatic one waits a full interval. */
    fun syncNow() {
        lastMillis = now()
        if (running?.isActive == true) return
        onRunning(true)
        running = scope.launch {
            try {
                work()
            } finally {
                onRunning(false)
            }
        }
    }

    /** True while any Home screen is shown. */
    fun noteShown(shown: Boolean) {
        if (shown == (ticking != null)) return
        ticking?.cancel()
        ticking = if (shown) scope.launch { tick() } else null
    }

    private suspend fun tick() {
        while (true) {
            // A sync asked for while this waited moved the deadline, so look again before syncing.
            val wait = untilDue()
            if (wait > 0) delay(wait) else syncNow()
        }
    }
}
