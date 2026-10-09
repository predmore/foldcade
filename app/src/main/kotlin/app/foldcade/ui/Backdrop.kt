package app.foldcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.foldcade.language.BackdropFrame
import app.foldcade.language.BackdropLine
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.MotionSpeed
import app.foldcade.language.backdropClock
import app.foldcade.language.layoutBackdrop
import app.foldcade.language.rgb
import app.foldcade.language.strokeAt
import kotlin.math.min

/**
 * Background layer only. Its clock does not follow focus, paging, or other UI motion.
 * Pauses when [running] is false. Reduced motion holds a still frame.
 * Static is dim and creeps a few pixels every few minutes while the clock runs.
 */
@Composable
fun Backdrop(
    motion: BackgroundMotion,
    speed: MotionSpeed,
    animatorScale: Float,
    running: Boolean,
) {
    if (motion == BackgroundMotion.Off) return
    MovingBackdrop(motion, speed, animatorScale, running)
}

@Composable
private fun MovingBackdrop(
    motion: BackgroundMotion,
    speed: MotionSpeed,
    animatorScale: Float,
    running: Boolean,
) {
    val clock = if (motion == BackgroundMotion.Static) {
        if (running && animatorScale > 0f) 1f else 0f
    } else {
        backdropClock(speed, animatorScale)
    }
    val moving = motion != BackgroundMotion.Static && running && clock > 0f
    val ticking = running && clock > 0f
    var designTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ticking, clock) {
        if (!ticking) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { frame ->
                if (last != 0L) {
                    val dt = ((frame - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    designTime += dt * clock
                }
                last = frame
            }
        }
    }
    val frame = remember { BackdropFrame() }
    val path = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        layoutBackdrop(motion, designTime, size.width, size.height, moving, frame)
        for (index in 0 until frame.lineCount) {
            val line = frame.lines[index]
            if (line.count < 2) continue
            if (line.shaped) {
                drawShapedRibbon(line)
            } else {
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
        }
        for (index in 0 until frame.discCount) {
            val disc = frame.discs[index]
            val color = rgb(disc.red, disc.green, disc.blue)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(disc.cx, disc.cy),
                    radius = disc.radius,
                ),
                radius = disc.radius,
                center = Offset(disc.cx, disc.cy),
            )
        }
    }
}

/**
 * Layered round strokes stand in for a blur. The outer ring is a wide, faint halo.
 * Inner rings carry the core. Screen blending adds a little light where strokes cross.
 * Width and color come from [strokeAt], so the trail swells, tapers, and shifts hue.
 */
private fun DrawScope.drawShapedRibbon(line: BackdropLine) {
    val steps = line.count - 1
    val path = Path()
    var point = 0
    val stride = 6
    while (point < steps) {
        val next = min(point + stride, steps)
        val along = (point + next) * 0.5f / steps.toFloat()
        val sample = strokeAt(line, along)
        val energy = sample.red + sample.green + sample.blue
        if (sample.radius >= 0.5f && energy > 0) {
            path.rewind()
            path.moveTo(line.x[point], line.y[point])
            for (index in point + 1..next) path.lineTo(line.x[index], line.y[index])
            val color = rgb(sample.red, sample.green, sample.blue)
            drawGlowStroke(path, color, sample.radius * 2.8f, 0.14f)
            drawGlowStroke(path, color, sample.radius * 1.65f, 0.22f)
            drawGlowStroke(path, color, sample.radius * 0.9f, 0.34f)
            drawGlowStroke(path, color, sample.radius * 0.4f, 0.4f)
        }
        point = next
    }
}

private fun DrawScope.drawGlowStroke(path: Path, color: Color, width: Float, alpha: Float) {
    drawPath(
        path = path,
        color = color.copy(alpha = alpha),
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = BlendMode.Screen,
    )
}
