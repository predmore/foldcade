package app.foldcade.ui

import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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
 * Soft radial glow shared by ribbons, crossings, discs, and tiles.
 *
 * A ribbon is one shader draw, not one shader per segment. Compiling a
 * [RuntimeShader] for every segment kept the first frame from finishing
 * inside the emulator launch wait, and the pile of them segfaulted SwiftShader.
 * Tiles and discs are ordinary radial gradients of the same [GlowFalloff] stops.
 *
 * The shader stays inside the AGSL subset: no bitwise operators, and every
 * array index is a constant or the loop variable.
 */
internal object SoftGlow {
    const val SAMPLES = 16
    const val LINES = 6

    val lineSource: String = """
        uniform float px[$SAMPLES];
        uniform float py[$SAMPLES];
        uniform float radA[$SAMPLES];
        uniform float radB[$SAMPLES];
        uniform float radC[$SAMPLES];
        uniform float ar[$SAMPLES];
        uniform float ag[$SAMPLES];
        uniform float ab[$SAMPLES];
        uniform float br[$SAMPLES];
        uniform float bg[$SAMPLES];
        uniform float bb[$SAMPLES];
        uniform float cr[$SAMPLES];
        uniform float cg[$SAMPLES];
        uniform float cb[$SAMPLES];
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
            float2 prev = float2(px[0], py[0]);
            float prevRa = radA[0];
            float prevRb = radB[0];
            float prevRc = radC[0];
            float prevAr = ar[0];
            float prevAg = ag[0];
            float prevAb = ab[0];
            float prevBr = br[0];
            float prevBg = bg[0];
            float prevBb = bb[0];
            float prevCr = cr[0];
            float prevCg = cg[0];
            float prevCb = cb[0];
            float best = 1000000.0;
            float bestRa = prevRa;
            float bestRb = prevRb;
            float bestRc = prevRc;
            float bestAr = prevAr;
            float bestAg = prevAg;
            float bestAb = prevAb;
            float bestBr = prevBr;
            float bestBg = prevBg;
            float bestBb = prevBb;
            float bestCr = prevCr;
            float bestCg = prevCg;
            float bestCb = prevCb;
            for (int i = 1; i < $SAMPLES; i++) {
                float2 cur = float2(px[i], py[i]);
                float2 abv = cur - prev;
                float len2 = dot(abv, abv);
                float t = len2 < 0.01 ? 0.0 : clamp(dot(frag - prev, abv) / len2, 0.0, 1.0);
                float dist = length(frag - (prev + abv * t));
                if (dist < best) {
                    best = dist;
                    bestRa = mix(prevRa, radA[i], t);
                    bestRb = mix(prevRb, radB[i], t);
                    bestRc = mix(prevRc, radC[i], t);
                    bestAr = mix(prevAr, ar[i], t);
                    bestAg = mix(prevAg, ag[i], t);
                    bestAb = mix(prevAb, ab[i], t);
                    bestBr = mix(prevBr, br[i], t);
                    bestBg = mix(prevBg, bg[i], t);
                    bestBb = mix(prevBb, bb[i], t);
                    bestCr = mix(prevCr, cr[i], t);
                    bestCg = mix(prevCg, cg[i], t);
                    bestCb = mix(prevCb, cb[i], t);
                }
                prev = cur;
                prevRa = radA[i];
                prevRb = radB[i];
                prevRc = radC[i];
                prevAr = ar[i];
                prevAg = ag[i];
                prevAb = ab[i];
                prevBr = br[i];
                prevBg = bg[i];
                prevBb = bb[i];
                prevCr = cr[i];
                prevCg = cg[i];
                prevCb = cb[i];
            }
            float maxR = max(bestRa, max(bestRb, bestRc));
            if (maxR < 0.4 || best >= maxR) {
                return half4(0.0, 0.0, 0.0, 0.0);
            }
            float3 rgb = float3(0.0, 0.0, 0.0);
            rgb = screen3(rgb, float3(bestAr, bestAg, bestAb) * cover(best, bestRa));
            rgb = screen3(rgb, float3(bestBr, bestBg, bestBb) * cover(best, bestRb));
            rgb = screen3(rgb, float3(bestCr, bestCg, bestCb) * cover(best, bestRc));
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

    fun fillStops(into: FloatArray, tightness: Float, rim: Float) {
        val stops = GlowFalloff.STOPS
        for (index in stops.indices) into[index] = GlowFalloff.cover(stops[index], tightness, rim)
    }
}

internal class GlowSlots {
    val lineShaders: List<RuntimeShader> = List(SoftGlow.LINES) { RuntimeShader(SoftGlow.lineSource) }
    val stops: FloatArray = FloatArray(GlowFalloff.STOPS.size)
    val layer: Paint = Paint().apply { blendMode = BlendMode.Screen }
    val px: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val py: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val radA: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val radB: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val radC: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val ar: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val ag: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val ab: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val br: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val bg: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val bb: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val cr: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val cg: FloatArray = FloatArray(SoftGlow.SAMPLES)
    val cb: FloatArray = FloatArray(SoftGlow.SAMPLES)
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
    slotLine: Int,
    first: BackdropLine,
    second: BackdropLine?,
    third: BackdropLine?,
    tightness: Float,
    rim: Float,
    peak: Float,
) {
    if (slotLine !in slots.lineShaders.indices || first.count < 2) return
    SoftGlow.fillStops(slots.stops, tightness, rim)
    val reach = GlowFalloff.REACH
    var maxRadius = 1f
    val last = first.count - 1
    for (sample in 0 until SoftGlow.SAMPLES) {
        val along = sample.toFloat() / (SoftGlow.SAMPLES - 1).toFloat()
        val scaled = along * last
        val index = min(scaled.toInt(), last - 1)
        val span = scaled - index
        slots.px[sample] = first.x[index] + (first.x[index + 1] - first.x[index]) * span
        slots.py[sample] = first.y[index] + (first.y[index + 1] - first.y[index]) * span
        val a = strokeAt(first, along)
        val b = if (second != null) strokeAt(second, along) else null
        val c = if (third != null) strokeAt(third, along) else null
        val ra = a.radius * reach
        val rb = (b?.radius ?: 0f) * reach
        val rc = (c?.radius ?: 0f) * reach
        slots.radA[sample] = ra
        slots.radB[sample] = rb
        slots.radC[sample] = rc
        writeRgb(slots.ar, slots.ag, slots.ab, sample, a.red, a.green, a.blue)
        writeRgb(slots.br, slots.bg, slots.bb, sample, b?.red ?: 0, b?.green ?: 0, b?.blue ?: 0)
        writeRgb(slots.cr, slots.cg, slots.cb, sample, c?.red ?: 0, c?.green ?: 0, c?.blue ?: 0)
        maxRadius = max(maxRadius, max(ra, max(rb, rc)))
    }
    if (maxRadius < 0.4f) return
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (sample in 0 until SoftGlow.SAMPLES) {
        minX = min(minX, slots.px[sample])
        maxX = max(maxX, slots.px[sample])
        minY = min(minY, slots.py[sample])
        maxY = max(maxY, slots.py[sample])
    }
    val shader = slots.lineShaders[slotLine]
    shader.setFloatUniform("px", slots.px)
    shader.setFloatUniform("py", slots.py)
    shader.setFloatUniform("radA", slots.radA)
    shader.setFloatUniform("radB", slots.radB)
    shader.setFloatUniform("radC", slots.radC)
    shader.setFloatUniform("ar", slots.ar)
    shader.setFloatUniform("ag", slots.ag)
    shader.setFloatUniform("ab", slots.ab)
    shader.setFloatUniform("br", slots.br)
    shader.setFloatUniform("bg", slots.bg)
    shader.setFloatUniform("bb", slots.bb)
    shader.setFloatUniform("cr", slots.cr)
    shader.setFloatUniform("cg", slots.cg)
    shader.setFloatUniform("cb", slots.cb)
    shader.setFloatUniform("peak", peak)
    shader.setFloatUniform("stopT", GlowFalloff.STOPS)
    shader.setFloatUniform("stopA", slots.stops)
    val pad = maxRadius + 2f
    drawContext.canvas.saveLayer(
        Rect(minX - pad, minY - pad, maxX + pad, maxY + pad),
        slots.layer,
    )
    drawRect(
        brush = ShaderBrush(shader),
        topLeft = Offset(minX - pad, minY - pad),
        size = Size((maxX - minX) + pad * 2f, (maxY - minY) + pad * 2f),
    )
    drawContext.canvas.restore()
}

private fun writeRgb(red: FloatArray, green: FloatArray, blue: FloatArray, index: Int, r: Int, g: Int, b: Int) {
    red[index] = r / 255f
    green[index] = g / 255f
    blue[index] = b / 255f
}
