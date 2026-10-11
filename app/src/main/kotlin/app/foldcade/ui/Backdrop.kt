package app.foldcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import app.foldcade.language.BackdropFrame
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.GlowFalloff
import app.foldcade.language.MarkKind
import app.foldcade.language.MotionSpeed
import app.foldcade.language.RibbonField
import app.foldcade.language.backdropClock
import app.foldcade.language.backdropFrameSeconds
import app.foldcade.language.fieldRibbons
import app.foldcade.language.layoutBackdrop
import app.foldcade.language.rgb
import kotlinx.coroutines.delay

/**
 * Background layer only. Its clock does not follow focus, paging, or other UI motion.
 * Pauses when [running] is false. Reduced motion holds a still frame. [held] stops
 * the clock on the frame already shown, for while something covers the backdrop.
 * Static is dim and creeps a few pixels every few minutes while the clock runs.
 * Frames come only as often as [backdropFrameSeconds] asks, on their own layer, so
 * a backdrop frame does not redraw the library and a library frame does not redraw it.
 */
@Composable
fun Backdrop(
    motion: BackgroundMotion,
    speed: MotionSpeed,
    animatorScale: Float,
    running: Boolean,
    held: Boolean = false,
) {
    if (motion == BackgroundMotion.Off) return
    MovingBackdrop(motion, speed, animatorScale, running, held)
}

@Composable
private fun MovingBackdrop(
    motion: BackgroundMotion,
    speed: MotionSpeed,
    animatorScale: Float,
    running: Boolean,
    held: Boolean,
) {
    val clock = if (motion == BackgroundMotion.Static) {
        if (running && animatorScale > 0f) 1f else 0f
    } else {
        backdropClock(speed, animatorScale)
    }
    val moving = motion != BackgroundMotion.Static && running && clock > 0f
    val ticking = running && !held && clock > 0f
    var designTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ticking, clock, motion) {
        if (!ticking) return@LaunchedEffect
        var last = 0L
        var wait = 0f
        while (true) {
            withFrameNanos { frame ->
                if (last != 0L) {
                    // A stall past the planned wait does not jump the ribbons ahead.
                    val dt = ((frame - last) / 1_000_000_000f).coerceIn(0f, wait + MAX_STALL_SECONDS)
                    designTime += dt * clock
                }
                last = frame
            }
            wait = backdropFrameSeconds(motion, designTime, clock)
            // Wake a little early, so the request lands on the vsync at the planned time.
            val due = last + (wait * 1_000_000_000f).toLong() - VSYNC_SLACK_NANOS
            val sleep = (due - System.nanoTime()) / 1_000_000L
            if (sleep > 0L) delay(sleep)
        }
    }
    val frame = remember { BackdropFrame() }
    val path = remember { Path() }
    val glow = remember { GlowSlots() }
    val field = remember { RibbonField() }
    val ribbons = remember { RibbonShader() }
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        layoutBackdrop(motion, designTime, size.width, size.height, moving, frame)
        // Ribbons and crossings are one shader pass. The frame's own lines are the
        // stroked version the lit-budget tests measure; only other lines are drawn here.
        val shaded = fieldRibbons(motion, designTime, size.width, size.height, moving, field)
        if (shaded) {
            ribbons.update(field, size.width, size.height)
            drawRect(brush = ribbons.brush, blendMode = BlendMode.Lighten)
        }
        for (index in 0 until frame.lineCount) {
            val line = frame.lines[index]
            if (line.count < 2 || (shaded && line.shaped)) continue
            path.rewind()
            path.moveTo(line.x[0], line.y[0])
            for (point in 1 until line.count) path.lineTo(line.x[point], line.y[point])
            drawPath(
                path = path,
                color = rgb(line.red, line.green, line.blue),
                style = Stroke(
                    width = line.radius * 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
        for (discIndex in 0 until frame.discCount) {
            val disc = frame.discs[discIndex]
            drawSoftDisc(
                slots = glow,
                cx = disc.cx,
                cy = disc.cy,
                radius = disc.radius * GlowFalloff.DISC_REACH,
                color = rgb(disc.red, disc.green, disc.blue),
                tightness = GlowFalloff.DISC_TIGHTNESS,
                rim = GlowFalloff.DISC_RIM,
                blendMode = if (disc.kind == MarkKind.Swell) BlendMode.Lighten else BlendMode.SrcOver,
            )
        }
    }
}

private const val MAX_STALL_SECONDS = 0.05f
private const val VSYNC_SLACK_NANOS = 4_000_000L

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
