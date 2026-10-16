package app.foldcade.ui

import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import app.foldcade.language.*
import kotlin.math.*

/** One independent instance per surface. Shader programs, textures and path buffers are reused. */
internal class SceneRenderer(val scene: ThemeScene, seed: Double = 0.0) {
    val metrics = SceneMetrics()
    val diagnostics = mutableListOf<ThemeDiagnostic>()
    private val evaluation = SceneEvaluation(scene, ::report, seed)
    private val state = evaluation.state
    private val textures = scene.textures.mapNotNull { (path, bytes) ->
        try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Cannot decode image")
            bitmap.prepareToDraw()
            path to bitmap
        } catch (error: Exception) { report(path, error); null }
    }.toMap()
    init { metrics.textureBytes = textures.values.sumOf { it.allocationByteCount.toLong() } }
    private val runtimes = scene.layers.map { layer ->
        try { LayerRuntime(layer) } catch (error: Exception) {
            report((layer as? SceneLayer.Shader)?.file ?: scene.source, error)
            null
        }
    }

    fun draw(scope: DrawScope, time: Double, moving: Boolean, panel: Float, clock: Float = 1f) {
        val started = System.nanoTime()
        if (evaluation.evaluate("variables") {
            state.update(time, scope.size.width, scope.size.height, moving, panel, clock)
            true
        } != true) return
        runtimes.forEach { runtime ->
            if (runtime != null && !runtime.failed) {
                try {
                    repeat(runtime.layer.repeat) { index ->
                        state.instance(index)
                        runtime.draw(scope)
                    }
                } catch (error: Exception) {
                    if (!runtime.failed) report(scene.source + ":" + runtime.layer.name, error)
                    runtime.failed = true
                }
            }
        }
        metrics.frames++
        metrics.cpuNanos += System.nanoTime() - started
        metrics.layers = runtimes.count { it != null && !it.failed }
    }

    fun frameRate(time: Double, width: Float, height: Float, moving: Boolean, panel: Float, clock: Float): Float? = evaluation.frameRate(
        time, width, height, moving, panel, clock,
    )

    private fun report(diagnostic: ThemeDiagnostic) {
        diagnostics += diagnostic
        Log.w("FoldcadeTheme", "${diagnostic.source}: ${diagnostic.message}")
    }

    private fun report(source: String, error: Exception) {
        report(ThemeDiagnostic(source, error.message ?: error.javaClass.simpleName))
    }

    private inner class LayerRuntime(val layer: SceneLayer) {
        var failed = false
        private val shader = (layer as? SceneLayer.Shader)?.let { spec ->
            RuntimeShader(spec.source).apply {
                spec.inputs.forEach { (uniform, path) ->
                    val bitmap = textures[path] ?: error("${spec.name}: cannot decode '$path'")
                    setInputShader(uniform, BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                }
            }
        }
        private val brush = shader?.let(::ShaderBrush)
        private val uniforms = (layer as? SceneLayer.Shader)?.uniforms?.mapValues { (_, expressions) ->
            FloatArray(expressions.size)
        }.orEmpty()
        private val pathSampler = (layer as? SceneLayer.Disc)?.path?.let { ScenePathSampler(it, scene) }
        private val stops = FloatArray(GlowFalloff.STOPS.size).also { buffer ->
            (layer as? SceneLayer.Disc)?.let { SoftGlow.fillStops(buffer, it.tightness, it.rim) }
        }
        private val image = (layer as? SceneLayer.Image)?.let { textures[it.file]?.asImageBitmap() }
        private val blend = when (layer.blend) {
            "lighten" -> BlendMode.Lighten; "plus" -> BlendMode.Plus; "screen" -> BlendMode.Screen
            "multiply" -> BlendMode.Multiply; else -> BlendMode.SrcOver
        }
        private fun value(expression: AnimationExpression) = expression.evaluate(state.values).toFloat()
        private fun color(expressions: List<AnimationExpression>): Color = rgba(
            value(expressions[0]).coerceIn(0f, 1f), value(expressions[1]).coerceIn(0f, 1f),
            value(expressions[2]).coerceIn(0f, 1f), value(expressions[3]).coerceIn(0f, 1f),
        )
        fun draw(scope: DrawScope) = with(scope) {
            if (failed) return@with
            when (val spec = layer) {
                is SceneLayer.Shader -> {
                    spec.uniforms.forEach { (name, expressions) ->
                        val buffer = uniforms.getValue(name)
                        expressions.forEachIndexed { index, expression -> buffer[index] = value(expression) }
                        shader!!.setFloatUniform(name, buffer)
                    }
                    drawRect(brush = brush!!, blendMode = blend)
                }
                is SceneLayer.Disc -> {
                    val color = color(spec.color)
                    val radius = value(spec.radius)
                    if (color.alpha <= 0f || radius <= 0f) return@with
                    val center = pathSampler?.let {
                        it.sample(state.values, value(spec.along))
                        Offset(it.x, it.y)
                    } ?: Offset(value(spec.x), value(spec.y))
                    drawRadialGlow(center, radius, color, stops, color.alpha, blend)
                }
                is SceneLayer.Line -> drawLine(color(spec.color), Offset(value(spec.x), value(spec.y)),
                    Offset(value(spec.endX), value(spec.endY)), value(spec.width), blendMode = blend)
                is SceneLayer.Image -> {
                    val bitmap = image ?: return@with
                    val x = value(spec.x); val y = value(spec.y)
                    val w = value(spec.width); val h = value(spec.height)
                    if (w <= 0f || h <= 0f) return@with
                    rotate(value(spec.rotation), Offset(x + w / 2f, y + h / 2f)) {
                        drawImage(bitmap, dstOffset = androidx.compose.ui.unit.IntOffset(x.roundToInt(), y.roundToInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                            alpha = value(spec.alpha).coerceIn(0f, 1f), blendMode = blend)
                    }
                }
            }
        }
    }
}

/** CPU submission timing is distinct from GPU/frame timing collected by Android's profiler. */
class SceneMetrics {
    var frames: Long = 0
        internal set
    var cpuNanos: Long = 0
        internal set
    var layers: Int = 0
        internal set
    var textureBytes: Long = 0
        internal set
}
