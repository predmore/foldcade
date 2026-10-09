package app.foldcade.ui

import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.foldcade.language.BackdropLine
import app.foldcade.language.GlowFalloff
import app.foldcade.language.strokeAt
import kotlin.math.max
import kotlin.math.min

/**
 * One radial glow, shared by ribbons, crossings, discs, and the focused tile.
 * The curve is [GlowFalloff] sampled at [GlowFalloff.STOPS]. A stable Bayer dither
 * keeps the shallow tail from banding, and pixels outside the radius stay true black.
 *
 * Each draw uses its own [RuntimeShader]. Uniforms are captured with the draw, so
 * segments in one frame do not share an instance.
 */
internal object SoftGlow {
    const val STRIDE = 2
    const val SEGMENTS = 24
    private const val LINES = 6
    const val LINE_SHADERS = LINES * SEGMENTS
    const val DISC_SHADERS = 24

    val source: String = """
        uniform float2 p0;
        uniform float2 p1;
        uniform float3 rad0;
        uniform float3 rad1;
        uniform float3 c0a;
        uniform float3 c0b;
        uniform float3 c0c;
        uniform float3 c1a;
        uniform float3 c1b;
        uniform float3 c1c;
        uniform float peak;
        uniform float stopT[12];
        uniform float stopA[12];

        float bayer(float2 p) {
            float x = mod(floor(p.x), 4.0);
            float y = mod(floor(p.y), 4.0);
            float v = 5.0;
            if (y < 0.5) {
                if (x < 0.5) v = 0.0;
                else if (x < 1.5) v = 8.0;
                else if (x < 2.5) v = 2.0;
                else v = 10.0;
            } else if (y < 1.5) {
                if (x < 0.5) v = 12.0;
                else if (x < 1.5) v = 4.0;
                else if (x < 2.5) v = 14.0;
                else v = 6.0;
            } else if (y < 2.5) {
                if (x < 0.5) v = 3.0;
                else if (x < 1.5) v = 11.0;
                else if (x < 2.5) v = 1.0;
                else v = 9.0;
            } else {
                if (x < 0.5) v = 15.0;
                else if (x < 1.5) v = 7.0;
                else if (x < 2.5) v = 13.0;
                else v = 5.0;
            }
            return v / 16.0;
        }

        float cover(float dist, float radius) {
            if (radius < 0.4) return 0.0;
            float u = dist / radius;
            if (u >= 1.0) return 0.0;
            float prevT = stopT[0];
            float prevA = stopA[0];
            float a = prevA;
            for (int i = 1; i < 12; i++) {
                float t = stopT[i];
                float s = stopA[i];
                if (u <= t) {
                    float span = t - prevT;
                    float f = span > 0.0001 ? (u - prevT) / span : 0.0;
                    a = mix(prevA, s, f);
                    break;
                }
                prevT = t;
                prevA = s;
            }
            return a * peak;
        }

        float3 screen3(float3 dst, float3 src) {
            return dst + src - dst * src;
        }

        half4 main(float2 frag) {
            float2 ab = p1 - p0;
            float len2 = dot(ab, ab);
            float t = len2 < 0.01 ? 0.0 : clamp(dot(frag - p0, ab) / len2, 0.0, 1.0);
            float dist = length(frag - (p0 + ab * t));
            float3 rad = mix(rad0, rad1, t);
            float maxR = max(rad.x, max(rad.y, rad.z));
            if (maxR < 0.4 || dist >= maxR) {
                return half4(0.0, 0.0, 0.0, 0.0);
            }
            float3 rgb = float3(0.0, 0.0, 0.0);
            rgb = screen3(rgb, mix(c0a, c1a, t) * cover(dist, rad.x));
            rgb = screen3(rgb, mix(c0b, c1b, t) * cover(dist, rad.y));
            rgb = screen3(rgb, mix(c0c, c1c, t) * cover(dist, rad.z));
            float energy = max(rgb.x, max(rgb.y, rgb.z));
            if (energy <= 0.0) {
                return half4(0.0, 0.0, 0.0, 0.0);
            }
            float n = (bayer(frag) - 0.5) / 255.0;
            rgb = clamp(rgb + float3(n, n, n), 0.0, 1.0);
            float a = max(rgb.x, max(rgb.y, rgb.z));
            return half4(half3(rgb), half(a));
        }
    """.trimIndent()

    fun shaders(count: Int): List<RuntimeShader> = List(count) { RuntimeShader(source) }

    fun fillStops(into: FloatArray, tightness: Float, rim: Float) {
        val stops = GlowFalloff.STOPS
        for (index in stops.indices) into[index] = GlowFalloff.cover(stops[index], tightness, rim)
    }
}

internal class GlowSlots {
    val lineShaders: List<RuntimeShader> = SoftGlow.shaders(SoftGlow.LINE_SHADERS)
    val discShaders: List<RuntimeShader> = SoftGlow.shaders(SoftGlow.DISC_SHADERS)
    val tileShader: RuntimeShader = RuntimeShader(SoftGlow.source)
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
    drawSoftLine(slots, slotLine, halo, glow, core, GlowFalloff.TIGHTNESS, GlowFalloff.RIM_START, GlowFalloff.PEAK)
}

internal fun DrawScope.drawCrossingLine(slots: GlowSlots, slotLine: Int, line: BackdropLine) {
    drawSoftLine(slots, slotLine, line, null, null, GlowFalloff.TIGHTNESS, GlowFalloff.RIM_START, 1f)
}

internal fun DrawScope.drawSoftDisc(
    shader: RuntimeShader,
    slots: GlowSlots,
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
    tightness: Float,
    rim: Float,
    blendMode: BlendMode,
) {
    if (radius < 0.4f) return
    SoftGlow.fillStops(slots.stops, tightness, rim)
    shader.prepareGlow(
        x0 = cx,
        y0 = cy,
        x1 = cx,
        y1 = cy,
        r0 = radius,
        g0 = 0f,
        b0 = 0f,
        r1 = radius,
        g1 = 0f,
        b1 = 0f,
        c0a = color,
        c0b = Color.Black,
        c0c = Color.Black,
        c1a = color,
        c1b = Color.Black,
        c1c = Color.Black,
        peak = 1f,
        stops = slots.stops,
    )
    val pad = radius + 1f
    drawRect(
        brush = ShaderBrush(shader),
        topLeft = Offset(cx - pad, cy - pad),
        size = Size(pad * 2f, pad * 2f),
        blendMode = blendMode,
    )
}

private fun DrawScope.drawSoftLine(
    slots: GlowSlots,
    slotLine: Int,
    first: BackdropLine,
    second: BackdropLine?,
    third: BackdropLine?,
    tightness: Float,
    rim: Float,
    peak: Float,
) {
    val steps = first.count - 1
    if (steps < 1) return
    SoftGlow.fillStops(slots.stops, tightness, rim)
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxRadius = 1f
    val reach = GlowFalloff.REACH
    for (index in 0 until first.count) {
        minX = min(minX, first.x[index])
        maxX = max(maxX, first.x[index])
        minY = min(minY, first.y[index])
        maxY = max(maxY, first.y[index])
    }
    for (index in 0 until steps) {
        val along = index.toFloat() / steps.toFloat()
        maxRadius = max(maxRadius, strokeAt(first, along).radius * reach)
        if (second != null) maxRadius = max(maxRadius, strokeAt(second, along).radius * reach)
        if (third != null) maxRadius = max(maxRadius, strokeAt(third, along).radius * reach)
    }
    val pad = maxRadius + 2f
    drawContext.canvas.saveLayer(
        Rect(minX - pad, minY - pad, maxX + pad, maxY + pad),
        slots.layer,
    )
    var point = 0
    var segment = 0
    while (point < steps && segment < SoftGlow.SEGMENTS) {
        val next = min(point + SoftGlow.STRIDE, steps)
        val along0 = point.toFloat() / steps.toFloat()
        val along1 = next.toFloat() / steps.toFloat()
        val shader = slots.lineShaders[slotLine * SoftGlow.SEGMENTS + segment]
        val a0 = strokeAt(first, along0)
        val a1 = strokeAt(first, along1)
        val b0 = if (second != null) strokeAt(second, along0) else null
        val b1 = if (second != null) strokeAt(second, along1) else null
        val c0 = if (third != null) strokeAt(third, along0) else null
        val c1 = if (third != null) strokeAt(third, along1) else null
        shader.prepareGlow(
            x0 = first.x[point],
            y0 = first.y[point],
            x1 = first.x[next],
            y1 = first.y[next],
            r0 = a0.radius * reach,
            g0 = (b0?.radius ?: 0f) * reach,
            b0 = (c0?.radius ?: 0f) * reach,
            r1 = a1.radius * reach,
            g1 = (b1?.radius ?: 0f) * reach,
            b1 = (c1?.radius ?: 0f) * reach,
            c0a = colorOf(a0.red, a0.green, a0.blue),
            c0b = colorOf(b0?.red ?: 0, b0?.green ?: 0, b0?.blue ?: 0),
            c0c = colorOf(c0?.red ?: 0, c0?.green ?: 0, c0?.blue ?: 0),
            c1a = colorOf(a1.red, a1.green, a1.blue),
            c1b = colorOf(b1?.red ?: 0, b1?.green ?: 0, b1?.blue ?: 0),
            c1c = colorOf(c1?.red ?: 0, c1?.green ?: 0, c1?.blue ?: 0),
            peak = peak,
            stops = slots.stops,
        )
        val radius = max(a0.radius, max(a1.radius, max(b0?.radius ?: 0f, max(b1?.radius ?: 0f, max(c0?.radius ?: 0f, c1?.radius ?: 0f))))) * reach
        val left = min(first.x[point], first.x[next]) - radius - 1f
        val top = min(first.y[point], first.y[next]) - radius - 1f
        val right = max(first.x[point], first.x[next]) + radius + 1f
        val bottom = max(first.y[point], first.y[next]) + radius + 1f
        drawRect(
            brush = ShaderBrush(shader),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            blendMode = BlendMode.Lighten,
        )
        point = next
        segment++
    }
    drawContext.canvas.restore()
}

internal fun RuntimeShader.prepareTile(
    center: Offset,
    reach: Float,
    color: Color,
    peak: Float,
    stops: FloatArray,
) {
    prepareGlow(
        x0 = center.x,
        y0 = center.y,
        x1 = center.x,
        y1 = center.y,
        r0 = reach,
        g0 = 0f,
        b0 = 0f,
        r1 = reach,
        g1 = 0f,
        b1 = 0f,
        c0a = color,
        c0b = Color.Black,
        c0c = Color.Black,
        c1a = color,
        c1b = Color.Black,
        c1c = Color.Black,
        peak = peak,
        stops = stops,
    )
}

private fun colorOf(red: Int, green: Int, blue: Int): Color =
    Color.Black.copy(red = red / 255f, green = green / 255f, blue = blue / 255f)

private fun RuntimeShader.prepareGlow(
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    r0: Float,
    g0: Float,
    b0: Float,
    r1: Float,
    g1: Float,
    b1: Float,
    c0a: Color,
    c0b: Color,
    c0c: Color,
    c1a: Color,
    c1b: Color,
    c1c: Color,
    peak: Float,
    stops: FloatArray,
) {
    setFloatUniform("p0", x0, y0)
    setFloatUniform("p1", x1, y1)
    setFloatUniform("rad0", r0, g0, b0)
    setFloatUniform("rad1", r1, g1, b1)
    setFloatUniform("c0a", c0a.red, c0a.green, c0a.blue)
    setFloatUniform("c0b", c0b.red, c0b.green, c0b.blue)
    setFloatUniform("c0c", c0c.red, c0c.green, c0c.blue)
    setFloatUniform("c1a", c1a.red, c1a.green, c1a.blue)
    setFloatUniform("c1b", c1b.red, c1b.green, c1b.blue)
    setFloatUniform("c1c", c1c.red, c1c.green, c1c.blue)
    setFloatUniform("peak", peak)
    setFloatUniform("stopT", GlowFalloff.STOPS)
    setFloatUniform("stopA", stops)
}
