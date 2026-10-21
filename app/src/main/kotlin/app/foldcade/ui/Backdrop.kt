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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** The host schedules a theme scene. No background algorithm lives in this renderer. */
@Composable
fun Backdrop(
    scene: ThemeScene?,
    speed: MotionSpeed,
    animatorScale: Float,
    running: Boolean,
    held: Boolean = false,
    panel: Float = 0f,
    previewTime: Double? = null,
    seed: Double? = null,
    /** What the shell tells the scene besides its clock, read each time it draws. */
    live: () -> SceneLive = { SceneLive() },
    onMetrics: ((SceneMetrics) -> Unit)? = null,
) {
    if (scene == null) return
    // Each surface rolls its own seed, so two panels showing one scene start apart.
    val rolled = remember(scene) { Random.nextDouble() }
    val surfaceSeed = seed ?: rolled
    var renderer by remember(scene, surfaceSeed) { mutableStateOf<SceneRenderer?>(null) }
    LaunchedEffect(scene, surfaceSeed) { renderer = withContext(Dispatchers.Default) { SceneRenderer(scene, surfaceSeed) } }
    val prepared = renderer ?: return
    val clock = if (scene.animateWhenMotionOff && animatorScale > 0f) 1f
        else backdropClock(speed, animatorScale).coerceAtMost(scene.maxClock)
    val moving = !scene.animateWhenMotionOff && running && clock > 0f
    val ticking = previewTime == null && running && !held && clock > 0f
    // Seconds of visible motion. Double keeps frame steps exact through months of uptime.
    var time by remember(scene) { mutableDoubleStateOf(scene.frozenTime) }
    var viewport by remember(scene) { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(ticking, clock, scene, moving, panel, viewport) {
        if (!ticking || viewport.width == 0 || viewport.height == 0) return@LaunchedEffect
        var last = 0L
        var wait = 0f
        while (true) {
            withFrameNanos { frame ->
                if (last != 0L) time += ((frame - last) / 1_000_000_000.0).coerceIn(0.0, wait + 0.05) * clock
                last = frame
            }
            val rate = prepared.frameRate(time, viewport.width.toFloat(), viewport.height.toFloat(), moving, panel, clock, live())
                ?: return@LaunchedEffect
            if (rate == 0f) {
                // Stopped, until an input the scene reads changes or a minute passes for `hour`.
                withTimeoutOrNull(STOPPED_RECHECK_MILLIS) { snapshotFlow { live() }.drop(1).first() }
                last = 0L
                wait = 0f
                continue
            }
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
        prepared.draw(this, previewTime ?: time, moving, panel, clock, live())
        onMetrics?.invoke(prepared.metrics)
    }
}

/** How often a stopped scene asks its frame rate again, so one that waits for an hour wakes. */
private const val STOPPED_RECHECK_MILLIS = 60_000L

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
