package app.foldcade.ui

import android.view.View
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Wait for this window to submit a frame, rather than for the shared Compose frame clock.
 * Android calls this after hardware rendering has submitted the frame to the swap chain:
 * https://developer.android.com/reference/android/view/ViewTreeObserver#registerFrameCommitCallback(java.lang.Runnable)
 */
internal suspend fun View.awaitFrameCommit() {
    if (!isHardwareAccelerated) return
    suspendCancellableCoroutine { waiting ->
        val observer = viewTreeObserver
        val committed = Runnable { if (waiting.isActive) waiting.resume(Unit) }
        observer.registerFrameCommitCallback(committed)
        waiting.invokeOnCancellation { if (observer.isAlive) observer.unregisterFrameCommitCallback(committed) }
        invalidate()
    }
}
