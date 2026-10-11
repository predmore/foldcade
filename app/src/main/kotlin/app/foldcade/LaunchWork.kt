package app.foldcade

import kotlinx.coroutines.Job

/** One pending launch shared by both Home activities. Called on the main thread. */
internal class LaunchWork {
    private var generation = 0
    var job: Job? = null
    val active: Boolean get() = job?.isActive == true

    fun begin(): Int {
        cancel()
        return generation
    }

    fun cancel() {
        generation += 1
        job?.cancel()
        job = null
    }

    fun current(ticket: Int): Boolean = ticket == generation
}
