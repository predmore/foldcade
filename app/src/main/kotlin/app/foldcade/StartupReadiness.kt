package app.foldcade

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** A failed startup attempt must not poison later launch, sync or recovery requests. */
internal suspend fun prepareStartup(
    vararg ready: CompletableDeferred<Unit>,
    onFailure: (Exception) -> Unit,
    prepare: suspend () -> Unit,
) {
    try {
        prepare()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        onFailure(failure)
    } finally {
        ready.forEach { it.complete(Unit) }
    }
}
