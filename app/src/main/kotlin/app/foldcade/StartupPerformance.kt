package app.foldcade

import android.os.Process
import android.os.SystemClock
import android.util.Log

internal var startupTracing = false

/** Coarse debug checkpoints, measured from the start of this process. */
internal fun perfMark(stage: String) {
    if (startupTracing) Log.d("FoldcadePerf", "$stage processMs=${SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()}")
}
