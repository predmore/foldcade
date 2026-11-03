package app.foldcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
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
    /** An explicit visual hold, such as a blurred menu, freezes the clock and live inputs. */
    held: Boolean = false,
    /** Art dissolves keep their original clock pause while their visible camera stays live. */
    pauseClock: Boolean = false,
    /** Fully opaque content covers this surface; keep its clock/seed but submit no drawing. */
    occluded: Boolean = false,
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
    val ticking = previewTime == null && running && !held && !pauseClock && !occluded && clock > 0f
    // Seconds of visible motion. Double keeps frame steps exact through months of uptime.
    var time by remember(scene) { mutableDoubleStateOf(scene.frozenTime) }
    var viewport by remember(scene) { mutableStateOf(IntSize.Zero) }
    val resolution = scene.renderScale
    // The scene works at its render size, so the grid's slide reaches it in the same px.
    val inputs = remember(scene) { BackdropInputs() }
    inputs.visibility(occluded)
    val told = {
        inputs.read(held) { live() }.let {
            if (resolution == 1f || it.scroll == 0f) it else it.copy(scroll = it.scroll * resolution)
        }
    }
    LaunchedEffect(ticking, clock, scene, moving, panel, viewport) {
        if (!ticking || viewport.width == 0 || viewport.height == 0) return@LaunchedEffect
        var last = 0L
        var wait = 0f
        while (true) {
            withFrameNanos { frame ->
                if (last != 0L) time += ((frame - last) / 1_000_000_000.0).coerceIn(0.0, wait + 0.05) * clock
                last = frame
            }
            val rate = prepared.frameRate(time, viewport.width.toFloat(), viewport.height.toFloat(), moving, panel, clock, told())
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
    // Leave the remembered scene, renderer and time above this boundary. Recreating them on
    // uncover would roll a new seed and reset the ribbons. No Canvas means no live subscriptions.
    if (occluded) return
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
        prepared.draw(this, previewTime ?: time, moving, panel, clock, told())
        onMetrics?.invoke(prepared.metrics)
    }
}

/** A held scene keeps its last inputs, including scroll, without observing their live sources. */
internal class BackdropInputs {
    private var last: SceneLive? = null
    private var hidden = false

    fun visibility(occluded: Boolean) {
        // The camera can travel while hidden. Sample its current position before the first
        // pixel is revealed, even if a blur or an art dissolve still holds the clock.
        if (hidden && !occluded) last = null
        hidden = occluded
    }

    fun read(held: Boolean, live: () -> SceneLive): SceneLive {
        if (held) return last ?: Snapshot.withoutReadObservation(live).also { last = it }
        return live().also { last = it }
    }
}

/** A late image only occludes after an actual draw at the end of Coil's crossfade. */
internal class BackdropArtReveal(crossfadeMillis: Int) {
    private val duration = crossfadeMillis * 1_000_000L
    var firstDraw by mutableStateOf<Long?>(null)
        private set
    var complete by mutableStateOf(crossfadeMillis == 0)
        private set

    fun drew(started: Long, finished: Long = started) {
        if (complete) return
        val first = firstDraw
        if (first == null) firstDraw = finished
        else if (started - first >= duration) complete = true
    }
}

/** How often a stopped scene asks its frame rate again, so one that waits for an hour wakes. */
private const val STOPPED_RECHECK_MILLIS = 60_000L

/**
 * Opaque layers over one panel's backdrop. While any is composed the backdrop is
 * held. Only layers whose parents are also opaque can omit its drawing entirely.
 */
internal class BackdropCover {
    private val layers = mutableStateListOf<() -> Boolean>()
    val covered: Boolean get() = layers.isNotEmpty()
    val occluded by derivedStateOf { layers.any { it() } }

    fun add(opaque: () -> Boolean) {
        layers.add(opaque)
    }

    fun remove(opaque: () -> Boolean) {
        layers.remove(opaque)
    }
}

internal val LocalBackdropCover = staticCompositionLocalOf<BackdropCover?> { null }
internal val LocalBackdropOpaque = compositionLocalOf { true }

/** Call from a layer that paints the whole panel opaque, for as long as it does. */
@Composable
internal fun CoversBackdrop(opaque: Boolean = true) {
    val cover = LocalBackdropCover.current ?: return
    val whole = rememberUpdatedState(opaque && LocalBackdropOpaque.current)
    val read = remember { { whole.value } }
    DisposableEffect(cover) {
        cover.add(read)
        onDispose { cover.remove(read) }
    }
}

/**
 * Where the grid has slid to, in px, for the scenes on both screens to follow as a
 * camera would, as the 3DS's top screen slides with the grid below it. The grid on
 * screen tells it; once that grid leaves, the scenes stay where it left them rather
 * than jumping back.
 */
internal class BackdropCamera {
    private var source by mutableStateOf<() -> Float>({ 0f })

    /** Read in the scene's draw, so a slide redraws it each frame without a new tick. */
    val scroll: Float get() = source()

    fun follow(where: () -> Float) {
        source = where
    }

    fun leave(where: () -> Float) {
        if (source !== where) return
        val last = where()
        source = { last }
    }
}

internal val LocalBackdropCamera = staticCompositionLocalOf<BackdropCamera?> { null }

/** The grid's slide, px, while it is on screen. See [BackdropCamera]. */
@Composable
internal fun MovesBackdropCamera(scroll: () -> Float) {
    val camera = LocalBackdropCamera.current ?: return
    val where = rememberUpdatedState(scroll)
    val read = remember { { where.value() } }
    DisposableEffect(camera) {
        camera.follow(read)
        onDispose { camera.leave(read) }
    }
}
