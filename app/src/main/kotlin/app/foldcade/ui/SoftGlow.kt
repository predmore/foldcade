package app.foldcade.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.foldcade.language.GlowFalloff

/**
 * Soft radial glow shared by discs and tiles. The curve is [GlowFalloff].
 * Ribbons and crossings use the same curve per pixel in [RibbonShader].
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
