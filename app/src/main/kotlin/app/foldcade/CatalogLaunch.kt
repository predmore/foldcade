package app.foldcade

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wait for the existing refresh (or start one), accepting an early cached route
 * as soon as it is published. A missing game ends when the refresh does; a stuck
 * server has a bounded wait. Cancelling this launch leaves the shared sync alive.
 * Called on Main; resolve only reads the shell's in-memory catalog.
 */
internal suspend fun <T : Any> awaitCatalogGame(
    resolve: () -> T?,
    sync: () -> Job,
    limitMillis: Long = 60_000,
): T? = withTimeoutOrNull(limitMillis) {
    resolve()?.let { return@withTimeoutOrNull it }
    var refresh = sync()
    while (true) {
        currentCoroutineContext().ensureActive()
        resolve()?.let { return@withTimeoutOrNull it }
        if (refresh.isCompleted) {
            if (!refresh.isCancelled) return@withTimeoutOrNull resolve()
            // Editing a connection replaces its refresh. Follow that new job.
            refresh = sync()
        }
        delay(50)
    }
    @Suppress("UNREACHABLE_CODE")
    null
}
