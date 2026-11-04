package app.foldcade

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One pending launch shared by both Home activities. Called on the main thread. */
internal class LaunchWork {
    private var generation = 0
    var job: Job? = null
    var retroArchCore: PreparedRetroArchCore? = null
    val active: Boolean get() = job?.isActive == true

    fun begin(): Int {
        cancel()
        return generation
    }

    fun cancel() {
        generation += 1
        job?.cancel()
        job = null
        retroArchCore = null
    }

    fun current(ticket: Int): Boolean = ticket == generation

    /** Core preparation and BIOS placement share this launch and its cancellation boundary. */
    suspend fun prepare(ticket: Int, step: suspend () -> Boolean): Boolean {
        currentCoroutineContext().ensureActive()
        if (!current(ticket)) return false
        val ready = step()
        currentCoroutineContext().ensureActive()
        return ready && current(ticket)
    }
}

/**
 * The activity that started [ownedGeneration] is going away.
 * Cancel only while that generation is still the one in flight, so the other
 * screen being destroyed does not drop a launch it did not start.
 */
internal fun cancelLaunchIfOwned(work: LaunchWork, ownedGeneration: Int?) {
    if (ownedGeneration != null && work.current(ownedGeneration)) work.cancel()
}
