package app.foldcade

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async

/** Shares a recent or running folder walk between both Home activities. Reload bypasses the limit. */
internal class FolderRefreshes(private val intervalMillis: Long, private val now: () -> Long) {
    private var lastStarted: Long? = null
    private var running: Job? = null

    @Synchronized
    fun quietly(force: Boolean = false, start: () -> Job): Job {
        running?.let { job ->
            if (!job.isCompleted || (!force && now() - (lastStarted ?: 0) < intervalMillis)) return job
        }
        return record(start())
    }

    @Synchronized
    fun record(job: Job): Job {
        lastStarted = now()
        running = job
        return job
    }
}

/**
 * One discovery and health read for both screens. Main thread only. A changed grant or
 * configuration requests another pass after the current one; ordinary duplicate resumes join it.
 * Checking stays true through the last pass, including while plugin entries are still loading.
 */
internal class SaveRefreshes(
    private val scope: CoroutineScope,
    private val checking: (Boolean) -> Unit,
    private val read: suspend (discover: Boolean) -> Unit,
) {
    private var running: Deferred<Unit>? = null
    private var pending = false
    private var discovery = false
    private var discovering = false

    fun request(discover: Boolean, changed: Boolean = true): Deferred<Unit> {
        val current = running?.takeIf { it.isActive }
        if (current != null) {
            if (changed || (discover && !discovering)) {
                pending = true
                discovery = discovery || discover
            }
            return current
        }
        discovery = discover
        checking(true)
        return scope.async(start = CoroutineStart.LAZY) {
            try {
                do {
                    pending = false
                    discovering = discovery
                    discovery = false
                    read(discovering)
                } while (pending)
            } finally {
                checking(false)
            }
        }.also { running = it; it.start() }
    }
}
