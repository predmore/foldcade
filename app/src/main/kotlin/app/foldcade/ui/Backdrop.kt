package app.foldcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import app.foldcade.language.BackdropFrame
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.MotionSpeed
import app.foldcade.language.backdropClock
import app.foldcade.language.layoutBackdrop
import app.foldcade.language.rgb

/**
 * Background layer only. Its clock does not follow focus, paging, or other UI motion.
 * Pauses when [running] is false. Speed Off and reduced motion hold a still frame.
 */
@Composable
fun Backdrop(
    motion: BackgroundMotion,
    speed: MotionSpeed,
    animatorScale: Float,
    running: Boolean,
    wallpaper: ImageBitmap?,
) {
    if (motion == BackgroundMotion.Static) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        return
    }
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
    val clock = backdropClock(speed, animatorScale)
    val moving = running && clock > 0f
    var designTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(moving, clock) {
        if (!moving) return@LaunchedEffect
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
