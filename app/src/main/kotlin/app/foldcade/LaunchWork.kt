package app.foldcade

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One pending launch shared by both Home activities. Called on the main thread. */
internal class LaunchWork {
    private var generation = 0
    private var finished by androidx.compose.runtime.mutableIntStateOf(0)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var currentJob: Job? by androidx.compose.runtime.mutableStateOf(null)
    var job: Job?
        get() = currentJob
        set(value) { currentJob = value; value?.invokeOnCompletion { main.post { finished++ } } }
    private var subjectId: String? = null
    var retroArchCore: PreparedRetroArchCore? = null
    val active: Boolean get() { finished; return job?.isActive == true }

    val pendingSubject: String? get() = subjectId.takeIf { active }

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

    /**
     * A launch waiting for its panel to light (a device plugin's one-screen layout), until it
     * starts or is dropped. Set and cleared on the main thread, by identity.
     */
    private var currentLighting: Job? by androidx.compose.runtime.mutableStateOf(null)
    var lighting: Job?
        get() = currentLighting
        set(value) { currentLighting = value; value?.invokeOnCompletion { main.post { finished++ } } }

    /** True while a launch waits for its panel: a start that needs none supersedes it, as any newer launch does. */
    val waitingToLight: Boolean get() { finished; return lighting?.isActive == true }

    fun cancel() {
        generation += 1
        job?.cancel()
        job = null
        subjectId = null
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
