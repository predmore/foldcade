package app.foldcade.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A surface pair published together, with fresh clocks so a cancelled fade holds no mutex we need. */
internal class SurfaceFrame<T>(val front: T, val back: T? = null) {
    val incoming = Animatable(if (back == null) 1f else 0f)
    val outgoing = Animatable(if (back == null) 0f else 1f)
}

internal class SurfaceTravel<T>(initial: T) {
    var frame by mutableStateOf(SurfaceFrame(initial))
        private set

    suspend fun change(target: T, awaitDraw: suspend () -> Unit, animate: suspend (SurfaceFrame<T>) -> Unit) {
        if (target == frame.front) return
        // A covered window keeps only its settled surface until it can submit a frame.
        awaitDraw()
        val previous = frame.front
        if (target == previous) return
        val next = SurfaceFrame(target, previous)
        frame = next
        try {
            animate(next)
        } finally {
            // Cancellation, including a rapid return Home, must not strand a split surface.
            if (frame === next) frame = SurfaceFrame(target)
        }
    }
}
