package app.foldcade.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.foldcade.language.BackdropLine
import app.foldcade.language.GlowFalloff
import app.foldcade.language.strokeAt
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val STAMP_FRACTION = 0.4f

/**
 * Soft radial glow shared by ribbons, crossings, discs, and tiles.
 *
 * The curve is [GlowFalloff] sampled at [GlowFalloff.STOPS]. Ribbons are
 * overlapping radial stamps, lightened inside one screen layer, so the halo
 * has no hard rim. There is no [android.graphics.RuntimeShader]: compiling one
 * per segment, and even one per ribbon, kept the first frame from finishing
 * inside the emulator's launch wait.
 */
internal object SoftGlow {
    fun fillStops(into: FloatArray, tightness: Float, rim: Float) {
        val stops = GlowFalloff.STOPS
        for (index in stops.indices) into[index] = GlowFalloff.cover(stops[index], tightness, rim)
    }
}

internal class GlowSlots {
    val stops: FloatArray = FloatArray(GlowFalloff.STOPS.size)
    val layer: Paint = Paint().apply { blendMode = BlendMode.Screen }
}

internal fun DrawScope.drawRibbonStrand(
    slots: GlowSlots,
    slotLine: Int,
    halo: BackdropLine,
    glow: BackdropLine,
    core: BackdropLine,
) {
    drawSoftLine(slots, halo, glow, core, GlowFalloff.TIGHTNESS, GlowFalloff.RIM_START, GlowFalloff.PEAK)
}

internal fun DrawScope.drawCrossingLine(slots: GlowSlots, slotLine: Int, line: BackdropLine) {
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
    val reach = GlowFalloff.REACH
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxRadius = 1f
    for (index in 0 until first.count) {
        minX = min(minX, first.x[index])
        maxX = max(maxX, first.x[index])
        minY = min(minY, first.y[index])
        maxY = max(maxY, first.y[index])
    }
    val steps = first.count - 1
    for (index in 0 until steps) {
        val along = index.toFloat() / steps.toFloat()
        maxRadius = max(maxRadius, strokeAt(first, along).radius * reach)
        if (second != null) maxRadius = max(maxRadius, strokeAt(second, along).radius * reach)
        if (third != null) maxRadius = max(maxRadius, strokeAt(third, along).radius * reach)
    }
    if (maxRadius < 0.4f) return
    val pad = maxRadius + 2f
    drawContext.canvas.saveLayer(
        Rect(minX - pad, minY - pad, maxX + pad, maxY + pad),
        slots.layer,
    )
    stampLine(first, slots.stops, reach, peak)
    if (second != null) stampLine(second, slots.stops, reach, peak)
    if (third != null) stampLine(third, slots.stops, reach, peak)
    drawContext.canvas.restore()
}

private fun DrawScope.stampLine(
    line: BackdropLine,
    stops: FloatArray,
    reach: Float,
    peak: Float,
) {
    val steps = line.count - 1
    if (steps < 1) return
    for (index in 0 until steps) {
        val along0 = index.toFloat() / steps.toFloat()
        val along1 = (index + 1).toFloat() / steps.toFloat()
        val a0 = strokeAt(line, along0)
        val a1 = strokeAt(line, along1)
        val x0 = line.x[index]
        val y0 = line.y[index]
        val x1 = line.x[index + 1]
        val y1 = line.y[index + 1]
        val span = hypot(x1 - x0, y1 - y0)
        val radius0 = a0.radius * reach
        val radius1 = a1.radius * reach
        val spacing = max(3f, min(radius0, radius1).coerceAtLeast(1f) * STAMP_FRACTION)
        val stamps = max(1, ceil(span / spacing).toInt())
        for (stamp in 0..stamps) {
            val t = stamp.toFloat() / stamps.toFloat()
            val radius = radius0 + (radius1 - radius0) * t
            val color = rgbOf(
                mixChannel(a0.red, a1.red, t),
                mixChannel(a0.green, a1.green, t),
                mixChannel(a0.blue, a1.blue, t),
            )
            if (color.red == 0f && color.green == 0f && color.blue == 0f) continue
            drawRadialGlow(
                center = Offset(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t),
                radius = radius,
                color = color,
                stops = stops,
                peak = peak,
                blendMode = BlendMode.Lighten,
            )
        }
    }
}

private fun mixChannel(start: Int, end: Int, t: Float): Int =
    (start + (end - start) * t).toInt().coerceIn(0, 255)

private fun rgbOf(red: Int, green: Int, blue: Int): Color =
    Color.Black.copy(red = red / 255f, green = green / 255f, blue = blue / 255f)
