package app.foldcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The host schedules a theme scene. No background algorithm lives in this renderer. */
@Composable
fun Backdrop(
    scene: ThemeScene?,
    speed: MotionSpeed,
    animatorScale: Float,
    running: Boolean,
    held: Boolean = false,
    panel: Float = 0f,
    previewTime: Float? = null,
    onMetrics: ((SceneMetrics) -> Unit)? = null,
) {
    if (scene == null) return
    var renderer by remember(scene) { mutableStateOf<SceneRenderer?>(null) }
    LaunchedEffect(scene) { renderer = withContext(Dispatchers.Default) { SceneRenderer(scene) } }
    val prepared = renderer ?: return
    val clock = if (scene.animateWhenMotionOff && animatorScale > 0f) 1f
        else backdropClock(speed, animatorScale).coerceAtMost(scene.maxClock)
    val moving = !scene.animateWhenMotionOff && running && clock > 0f
    val ticking = previewTime == null && running && !held && clock > 0f
    var time by remember(scene) { mutableFloatStateOf(scene.frozenTime) }
    var viewport by remember(scene) { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(ticking, clock, scene, moving, panel, viewport) {
        if (!ticking || viewport.width == 0 || viewport.height == 0) return@LaunchedEffect
        var last = 0L
        var wait = 0f
        while (true) {
            withFrameNanos { frame ->
                if (last != 0L) time += ((frame - last) / 1_000_000_000f).coerceIn(0f, wait + 0.05f) * clock
                last = frame
            }
            val rate = prepared.frameRate(time, viewport.width.toFloat(), viewport.height.toFloat(), moving, panel, clock)
                ?: return@LaunchedEffect
            if (rate == 0f) return@LaunchedEffect
            wait = 1f / rate
            val due = last + (wait * 1_000_000_000f).toLong() - 4_000_000L
            val sleep = (due - System.nanoTime()) / 1_000_000L
            if (sleep > 0L) delay(sleep)
        }
    }
    val resolution = scene.renderScale
    Canvas(Modifier.fillMaxSize().layout { measurable, constraints ->
        val targetWidth = (constraints.maxWidth * resolution).toInt().coerceAtLeast(1)
        val targetHeight = (constraints.maxHeight * resolution).toInt().coerceAtLeast(1)
        val surface = measurable.measure(Constraints.fixed(targetWidth, targetHeight))
        layout(constraints.maxWidth, constraints.maxHeight) { surface.place(0, 0) }
    }.onSizeChanged { viewport = it }.graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = 1f / resolution; scaleY = 1f / resolution
            if (resolution != 1f) compositingStrategy = CompositingStrategy.Offscreen
    }) {
        prepared.draw(this, previewTime ?: time, moving, panel, clock)
        onMetrics?.invoke(prepared.metrics)
    }
}

/**
 * Opaque layers over one panel's backdrop. While any is composed the backdrop is
 * held: nobody can see it move, and a frame that does not change costs nothing.
 */
internal class BackdropCover {
    private var layers by mutableIntStateOf(0)
    val covered: Boolean get() = layers > 0

    fun add() {
        layers++
    }

    fun remove() {
        layers--
    }
}

internal val LocalBackdropCover = staticCompositionLocalOf<BackdropCover?> { null }

/** Call from a layer that paints the whole panel opaque, for as long as it does. */
@Composable
internal fun CoversBackdrop() {
    val cover = LocalBackdropCover.current ?: return
    DisposableEffect(cover) {
        cover.add()
        onDispose { cover.remove() }
    }
}
