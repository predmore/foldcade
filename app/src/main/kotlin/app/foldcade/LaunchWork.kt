package app.foldcade

import kotlinx.coroutines.Job

/** One pending launch shared by both Home activities. Called on the main thread. */
internal class LaunchWork {
    private var generation = 0
    var job: Job? = null
    private var subjectId: String? = null
    var retroArchCore: PreparedRetroArchCore? = null
    val active: Boolean get() = job?.isActive == true

    fun pending(id: String): Boolean = active && subjectId == id

    fun begin(subject: String? = null): Int {
        cancel()
        subjectId = subject
        return generation
    }

    /** A catalog wait is handing its one resolved route to the normal launch. */
    fun resolved(ticket: Int) {
        if (current(ticket)) subjectId = null
    }

    fun cancel() {
        generation += 1
        job?.cancel()
        job = null
        subjectId = null
        retroArchCore = null
    }

    fun current(ticket: Int): Boolean = ticket == generation
}

/**
 * The activity that started [ownedGeneration] is going away.
 * Cancel only while that generation is still the one in flight, so the other
 * screen being destroyed does not drop a launch it did not start.
 */
internal fun cancelLaunchIfOwned(work: LaunchWork, ownedGeneration: Int?) {
    if (ownedGeneration != null && work.current(ownedGeneration)) work.cancel()
}
