package app.foldcade

import app.foldcade.host.play.UsageSample
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class UsageSnapshot(
    val granted: Boolean,
    val samples: List<UsageSample>,
)

/**
 * Reads a week of usage off the caller's scope and publishes only the latest
 * result that is still wanted. A screen that has left, or a newer resume,
 * drops the old query so it cannot update play time after the fact.
 */
internal class UsageReconcile {
    private var ticket = 0
    private var job: Job? = null

    fun request(
        scope: CoroutineScope,
        deliver: CoroutineContext,
        alive: () -> Boolean,
        read: suspend () -> UsageSnapshot,
        publish: (UsageSnapshot) -> Unit,
    ): Job {
        val mine = ++ticket
        job?.cancel()
        val launched = scope.launch {
            val snapshot = try {
                read()
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
            withContext(deliver) {
                if (mine != ticket || !alive()) return@withContext
                publish(snapshot)
            }
        }
        job = launched
        return launched
    }

    fun cancel() {
        ticket += 1
        job?.cancel()
        job = null
    }
}
