package app.foldcade.ui

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.ShaderBrush
import app.foldcade.language.GlowFalloff
import app.foldcade.language.RibbonField

/**
 * Paints the ribbons of a [RibbonField] in one pass. Each pixel finds its distance to
 * every strand and takes the [GlowFalloff] curve there, with the gain and width
 * [app.foldcade.language.ribbonGain] gives at that point along the ribbon. One full-panel
 * draw replaces hundreds of stroked shells, and the glow has no steps along or across.
 * Layers combine like Lighten over black: a brighter color eases in by its cover.
 */
internal class RibbonShader {
    private val shader = RuntimeShader(SOURCE).apply {
        setFloatUniform("falloff", GlowFalloff.TIGHTNESS, GlowFalloff.RIM_START, GlowFalloff.REACH, GlowFalloff.KNOT)
        setFloatUniform("endFade", RibbonField.END_FADE)
        setFloatUniform("crossingRadius", RibbonField.CROSSING_RADIUS)
    }
    val brush: ShaderBrush = ShaderBrush(shader)

    fun update(field: RibbonField, width: Float, height: Float) {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("travel", field.travel)
        shader.setFloatUniform("shape", field.shape)
        shader.setFloatUniform("drift", field.drift)
        shader.setFloatUniform("start", field.start)
        shader.setFloatUniform("end", field.end)
        shader.setFloatUniform("swell", field.swell)
        shader.setFloatUniform("reach", field.reach)
    }
}

private val SOURCE = """
uniform float2 size;
uniform float travel;
uniform float4 shape[3];
uniform float4 drift[3];
uniform float4 start[9];
uniform float4 end[9];
uniform float4 swell;
uniform float reach;
uniform float4 falloff;
uniform float endFade;
uniform float crossingRadius;

const float PI = 3.14159265;
const float TWO_PI = 6.28318531;

float ease(float v) {
    float t = clamp(v, 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float cover(float t) {
    if (t >= 1.0) return 0.0;
    float bell = exp(-falloff.x * t * t);
    if (t <= falloff.y) return bell;
    return bell * (1.0 - ease((t - falloff.y) / (1.0 - falloff.y)));
}

float knot(float along, float phase) {
    float direct = abs(along - fract(phase));
    float distance = min(direct, 1.0 - direct);
    if (distance >= falloff.w) return 0.0;
    return 0.5 * (1.0 + cos(distance / falloff.w * PI));
}

float ribbonGain(float t, float pulse) {
    float edge = t < endFade ? ease(t / endFade) : (t > 1.0 - endFade ? ease((1.0 - t) / endFade) : 1.0);
    if (edge <= 0.0) return 0.0;
    float swelling = 0.34 + 0.66 * (0.5 + 0.5 * sin(TWO_PI * (t * 2.15 + 0.35)));
    float moving = pulse == 0.0 ? 0.22 : pulse;
    float spots = knot(t, moving) * 0.9 + knot(t, moving + 0.46) * 0.5;
    return clamp(edge * (swelling * 0.58 + spots), 0.0, 1.0);
}

float3 lighten(float3 under, float3 color, float alpha) {
    return under + max(color - under, 0.0) * alpha;
}

half4 main(float2 p) {
    float t = clamp(p.x / size.x, 0.0, 1.0);
    float s = p.x / size.x + travel;
    float3 lit = float3(0.0);
    float ys[3];
    float slopes[3];
    for (int i = 0; i < 3; i++) {
        float4 form = shape[i];
        float4 move = drift[i];
        float a = TWO_PI * s + move.x;
        float b = TWO_PI * s * form.w + move.x * 1.7;
        float y = (form.x + form.y * sin(a) + form.z * sin(b) + move.y) * size.y;
        float slope = (form.y * cos(a) + form.z * form.w * cos(b)) * TWO_PI * size.y / size.x;
        ys[i] = y;
        slopes[i] = slope;
        float distance = abs(p.y - y) * inversesqrt(1.0 + slope * slope);
        float gain = ribbonGain(t, move.z);
        float width = 0.28 + 0.92 * gain;
        for (int layer = 0; layer < 3; layer++) {
            float4 left = start[i * 3 + layer];
            float reachPx = left.w * width * falloff.z;
            if (reachPx < 0.5 || distance >= reachPx) continue;
            float3 color = mix(left.rgb, end[i * 3 + layer].rgb, t) * gain;
            lit = lighten(lit, color, cover(distance / reachPx));
        }
    }
    if (swell.w > 0.0) {
        float reachPx = crossingRadius * falloff.z;
        for (int left = 0; left < 2; left++) {
            for (int right = 1; right < 3; right++) {
                if (right <= left) continue;
                float gain = 1.0 - ease(abs(ys[left] - ys[right]) / reach);
                if (gain <= 0.0) continue;
                float mid = (ys[left] + ys[right]) * 0.5;
                float slope = (slopes[left] + slopes[right]) * 0.5;
                float distance = abs(p.y - mid) * inversesqrt(1.0 + slope * slope);
                if (distance >= reachPx) continue;
                lit = lighten(lit, swell.rgb * gain, cover(distance / reachPx));
            }
        }
    }
    return half4(half3(lit), 1.0);
}
"""
