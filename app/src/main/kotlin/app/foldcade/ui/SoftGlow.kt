package app.foldcade.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.foldcade.language.BackdropLine
import app.foldcade.language.GlowFalloff
import app.foldcade.language.strokeAt
import kotlin.math.min

/**
 * Polyline samples that share one stroke. Matches the cheap ribbon the
 * emulator already walks while it reads the accessibility tree.
 */
private const val STROKE_STRIDE = 6

/** Outside-in indexes into [GlowFalloff.STOPS]. Five shells, not all twelve. */
private val SHELLS = intArrayOf(10, 8, 6, 4, 2)

/**
 * Soft radial glow shared by ribbons, crossings, discs, and tiles.
 *
 * The curve is [GlowFalloff]. A ribbon is a few round strokes per stretch,
 * lightened so overlapping caps do not stack into dots. Twelve shells on
 * every other sample, and the earlier stamp layer, filled a frame for so
 * long that the emulator's accessibility dump stayed an empty window.
 */
internal object SoftGlow {
    fun fillStops(into: FloatArray, tightness: Float, rim: Float) {
        val stops = GlowFalloff.STOPS
        for (index in stops.indices) into[index] = GlowFalloff.cover(stops[index], tightness, rim)
    }
}

internal class GlowSlots {
    val stops: FloatArray = FloatArray(GlowFalloff.STOPS.size)
}

internal fun DrawScope.drawRibbonStrand(
    slots: GlowSlots,
    halo: BackdropLine,
    glow: BackdropLine,
    core: BackdropLine,
) {
    drawSoftLine(slots, halo, glow, core, GlowFalloff.TIGHTNESS, GlowFalloff.RIM_START, GlowFalloff.PEAK)
}

internal fun DrawScope.drawCrossingLine(slots: GlowSlots, line: BackdropLine) {
    drawSoftLine(slots, line, null, null, GlowFalloff.TIGHTNESS, GlowFalloff.RIM_START, 1f)
}

internal fun DrawScope.drawSoftDisc(
    slots: GlowSlots,
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
    tightness: Float,
    rim: Float,
    blendMode: BlendMode,
) {
    SoftGlow.fillStops(slots.stops, tightness, rim)
    drawRadialGlow(Offset(cx, cy), radius, color, slots.stops, 1f, blendMode)
}

internal fun DrawScope.drawRadialGlow(
    center: Offset,
    radius: Float,
    color: Color,
    stops: FloatArray,
    peak: Float,
    blendMode: BlendMode = BlendMode.SrcOver,
) {
    if (radius < 0.4f) return
    val gradient = Array(stops.size) { index ->
        val cover = (stops[index] * peak).coerceIn(0f, 1f)
        GlowFalloff.STOPS[index] to color.copy(alpha = cover)
    }
    drawCircle(
        brush = Brush.radialGradient(colorStops = gradient, center = center, radius = radius),
        radius = radius,
        center = center,
        blendMode = blendMode,
    )
}

private fun DrawScope.drawSoftLine(
    slots: GlowSlots,
    first: BackdropLine,
    second: BackdropLine?,
    third: BackdropLine?,
    tightness: Float,
    rim: Float,
    peak: Float,
) {
    if (first.count < 2) return
    SoftGlow.fillStops(slots.stops, tightness, rim)
    val path = Path()
    drawFalloffLine(path, first, slots.stops, GlowFalloff.REACH, peak)
    if (second != null) drawFalloffLine(path, second, slots.stops, GlowFalloff.REACH, peak)
    if (third != null) drawFalloffLine(path, third, slots.stops, GlowFalloff.REACH, peak)
}

private fun DrawScope.drawFalloffLine(
    path: Path,
    line: BackdropLine,
    stops: FloatArray,
    reach: Float,
    peak: Float,
) {
    val steps = line.count - 1
    if (steps < 1) return
    var point = 0
    while (point < steps) {
        val next = min(point + STROKE_STRIDE, steps)
        val along = (point + next) * 0.5f / steps.toFloat()
        val sample = strokeAt(line, along)
        val radius = sample.radius * reach
        if (radius >= 0.5f && sample.red + sample.green + sample.blue > 0) {
            path.rewind()
            path.moveTo(line.x[point], line.y[point])
            for (index in point + 1..next) path.lineTo(line.x[index], line.y[index])
            drawFalloffStrokes(
                path,
                rgbOf(sample.red, sample.green, sample.blue),
                radius,
                stops,
                peak,
            )
        }
        point = next
    }
}

/**
 * Wider, dimmer shells first. Each shell's alpha is the cover at that stop,
 * and [BlendMode.Lighten] keeps the brighter core without adding the shells
 * together at a joint.
 */
private fun DrawScope.drawFalloffStrokes(
    path: Path,
    color: Color,
    radius: Float,
    stops: FloatArray,
    peak: Float,
) {
    val fractions = GlowFalloff.STOPS
    for (index in SHELLS) {
        val width = radius * 2f * fractions[index]
        val cover = (stops[index] * peak).coerceIn(0f, 1f)
        if (width < 0.5f || cover <= 0.004f) continue
        drawPath(
            path = path,
            color = color.copy(alpha = cover),
            style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = BlendMode.Lighten,
        )
    }
}

private fun rgbOf(red: Int, green: Int, blue: Int): Color =
    Color.Black.copy(red = red / 255f, green = green / 255f, blue = blue / 255f)
