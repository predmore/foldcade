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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import app.foldcade.language.BackdropFrame
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.GlowFalloff
import app.foldcade.language.MarkKind
import app.foldcade.language.MotionSpeed
import app.foldcade.language.backdropClock
import app.foldcade.language.layoutBackdrop
import app.foldcade.language.rgb

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
    val glow = remember { GlowSlots() }
    Canvas(Modifier.fillMaxSize()) {
        layoutBackdrop(motion, designTime, size.width, size.height, moving, frame)
        var index = 0
        var slotLine = 0
        while (index < frame.lineCount) {
            val line = frame.lines[index]
            if (line.count < 2) {
                index++
                continue
            }
            if (
                line.kind == MarkKind.Ribbon &&
                index + 2 < frame.lineCount &&
                frame.lines[index + 1].kind == MarkKind.Ribbon &&
                frame.lines[index + 2].kind == MarkKind.Ribbon
            ) {
                drawRibbonStrand(
                    glow,
                    slotLine,
                    frame.lines[index],
                    frame.lines[index + 1],
                    frame.lines[index + 2],
                )
                slotLine++
                index += 3
            } else if (line.shaped) {
                drawCrossingLine(glow, slotLine, line)
                slotLine++
                index++
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
                index++
            }
        }
        for (discIndex in 0 until frame.discCount) {
            val disc = frame.discs[discIndex]
            val shader = glow.discShaders[discIndex]
            drawSoftDisc(
                shader = shader,
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
