package app.foldcade.language

import kotlinx.serialization.json.*
import kotlin.math.hypot

/** A scene is entirely authored data. Each compiled scene creates independent state per panel. */
data class ThemeScene(
    val id: String,
    val name: String,
    val source: String,
    val symbols: AnimationSymbols,
    val variables: List<Pair<Int, AnimationExpression>>,
    val layers: List<SceneLayer>,
    val frameRate: AnimationExpression,
    val maxClock: Float,
    val renderScale: Float,
    val animateWhenMotionOff: Boolean,
    val frozenTime: Double,
    val textures: Map<String, ByteArray>,
) {
    fun state(): SceneState = SceneState(this)
    /** Only variables that depend on u need reevaluating at every path sample. */
    internal val sampledVariables: List<Pair<Int, AnimationExpression>> = run {
        val dependent = mutableSetOf(symbols.index("u"))
        variables.filter { (slot, expression) ->
            expression.dependencies.any { it in dependent }.also { if (it) dependent += slot }
        }
    }
}

/** A failure in scene-wide evaluation stops this surface and produces one source-linked diagnostic. */
class SceneEvaluation(val scene: ThemeScene, private val report: (ThemeDiagnostic) -> Unit = {}) {
    val state = scene.state()
    var failed = false
        private set

    fun <T> evaluate(context: String, block: () -> T): T? {
        if (failed) return null
        return try { block() } catch (error: Exception) {
            failed = true
            report(ThemeDiagnostic("${scene.source}:$context", error.message ?: error.javaClass.simpleName))
            null
        }
    }

    fun frameRate(time: Double, width: Float, height: Float, moving: Boolean, panel: Float, clock: Float): Float? =
        evaluate("frameRate") {
            state.update(time, width, height, moving, panel, clock)
            scene.frameRate.evaluate(state.values).toFloat().also {
                require(it.isFinite() && it >= 0f) { "frameRate must be finite and nonnegative" }
            }
        }
}

class SceneState(val scene: ThemeScene) {
    val values = DoubleArray(scene.symbols.size)
    fun set(name: String, value: Double) { values[scene.symbols.index(name)] = value }
    fun update(time: Double, width: Float, height: Float, moving: Boolean, panel: Float, clock: Float = 1f) {
        set("time", time); set("width", width.toDouble()); set("height", height.toDouble())
        set("moving", if (moving) 1.0 else 0.0); set("panel", panel.toDouble())
        set("clock", clock.toDouble())
        set("index", 0.0); set("u", 0.0)
        scene.variables.forEach { (slot, expression) -> values[slot] = expression.evaluate(values) }
    }
    /** Repeat-local variables are recalculated, so each instance may use its own trajectories. */
    fun instance(index: Int) {
        set("index", index.toDouble())
        scene.variables.forEach { (slot, expression) -> values[slot] = expression.evaluate(values) }
    }
}

sealed interface SceneLayer {
    val name: String
    val repeat: Int
    val blend: String
    data class Shader(
        override val name: String, override val repeat: Int, override val blend: String,
        val source: String, val file: String,
        val uniforms: Map<String, List<AnimationExpression>>,
        val inputs: Map<String, String>,
    ) : SceneLayer
    data class Disc(
        override val name: String, override val repeat: Int, override val blend: String,
        val x: AnimationExpression, val y: AnimationExpression, val radius: AnimationExpression,
        val color: List<AnimationExpression>, val tightness: Float, val rim: Float,
        val path: ScenePath?, val along: AnimationExpression,
    ) : SceneLayer
    data class Line(
        override val name: String, override val repeat: Int, override val blend: String,
        val x: AnimationExpression, val y: AnimationExpression,
        val endX: AnimationExpression, val endY: AnimationExpression,
        val width: AnimationExpression, val color: List<AnimationExpression>,
    ) : SceneLayer
    data class Image(
        override val name: String, override val repeat: Int, override val blend: String,
        val file: String, val x: AnimationExpression, val y: AnimationExpression,
        val width: AnimationExpression, val height: AnimationExpression,
        val alpha: AnimationExpression, val rotation: AnimationExpression,
    ) : SceneLayer
}

data class ScenePath(val x: AnimationExpression, val y: AnimationExpression, val samples: Int)

/** Reusable arc-length sampler for any authored path. There are no per-frame allocations. */
class ScenePathSampler(private val path: ScenePath, scene: ThemeScene) {
    private val uSlot = scene.symbols.index("u")
    private val variables = scene.sampledVariables
    private val previousVariables = DoubleArray(variables.size)
    private val xs = FloatArray(path.samples)
    private val ys = FloatArray(path.samples)
    private val lengths = FloatArray(path.samples)
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set
    fun sample(values: DoubleArray, fraction: Float) {
        val previous = values[uSlot]
        variables.forEachIndexed { index, (slot, _) -> previousVariables[index] = values[slot] }
        var length = 0f
        try {
            for (index in xs.indices) {
                values[uSlot] = index.toDouble() / (path.samples - 1)
                variables.forEach { (slot, expression) -> values[slot] = expression.evaluate(values) }
                xs[index] = path.x.evaluate(values).toFloat(); ys[index] = path.y.evaluate(values).toFloat()
                require(xs[index].isFinite() && ys[index].isFinite()) { "Path coordinates must be finite" }
                if (index > 0) length += hypot(xs[index] - xs[index - 1], ys[index] - ys[index - 1])
                lengths[index] = length
            }
        } finally {
            values[uSlot] = previous
            variables.forEachIndexed { index, (slot, _) -> values[slot] = previousVariables[index] }
        }
        val target = fraction.coerceIn(0f, 1f) * length
        var index = 1
        while (index < path.samples - 1 && lengths[index] < target) index++
        val span = lengths[index] - lengths[index - 1]
        val u = if (span > 0.0001f) ((target - lengths[index - 1]) / span).coerceIn(0f, 1f) else 0f
        x = xs[index - 1] + (xs[index] - xs[index - 1]) * u
        y = ys[index - 1] + (ys[index] - ys[index - 1]) * u
    }
}

data class ThemeDiagnostic(val source: String, val message: String)

/** Errors identify the asset and layer; one broken scene does not discard a pack's icons. */
fun parseThemeScene(
    text: String,
    source: String,
    entries: Map<String, ByteArray>,
    report: (ThemeDiagnostic) -> Unit = {},
): ThemeScene? = try {
    val root = inheritScene(Json.parseToJsonElement(text).jsonObject, source, entries)
    require(root["version"]?.jsonPrimitive?.int == 1) { "Unsupported scene version" }
    val symbols = AnimationSymbols()
    listOf("time", "width", "height", "moving", "panel", "index", "u", "clock").forEach(symbols::define)
    fun expr(value: JsonElement?, fallback: String = "0"): AnimationExpression {
        if (value !is JsonObject) return symbols.compile(value?.jsonPrimitive?.content ?: fallback)
        val frames = value["keyframes"]!!.jsonArray.map { it.jsonArray }
        require(frames.size >= 2 && frames.all { it.size == 2 }) { "A track needs at least two [time, value] keyframes" }
        val times = frames.map { it[0].jsonPrimitive.double }.toDoubleArray()
        val points = frames.map { it[1].jsonPrimitive.double }.toDoubleArray()
        require(times.all(Double::isFinite) && points.all(Double::isFinite) && times.toList().zipWithNext().all { it.first < it.second }) { "Keyframe times must increase and values must be finite" }
        val duration = value["duration"]?.jsonPrimitive?.double ?: times.last()
        require(duration.isFinite() && duration > 0.0 && duration >= times.last()) { "Invalid track duration" }
        val time = symbols.compile(value["time"]?.jsonPrimitive?.content ?: "time")
        val looping = value["loop"]?.jsonPrimitive?.boolean ?: true
        val easing = value["easing"]?.jsonPrimitive?.content ?: "linear"
        require(easing in setOf("linear", "smooth")) { "Unknown track easing '$easing'" }
        return CompiledAnimationExpression(AnimationExpression { values ->
            val raw = time.evaluate(values)
            require(raw.isFinite()) { "Keyframe time must be finite" }
            val remainder = raw % duration
            val t = if (looping) { if (remainder < 0.0) remainder + duration else remainder } else raw
            val found = times.binarySearch(t)
            when {
                found >= 0 -> points[found]
                t <= times.first() -> points.first()
                t >= times.last() -> points.last()
                else -> {
                    val end = -found - 1
                    val u = (t - times[end - 1]) / (times[end] - times[end - 1])
                    val progress = if (easing == "smooth") u * u * (3.0 - 2.0 * u) else u
                    points[end - 1] + (points[end] - points[end - 1]) * progress
                }
            }
        }, time.dependencies)
    }
    val variables = root["variables"]?.jsonObject?.map { (name, value) ->
        val compiled = expr(value)
        symbols.define(name) to compiled
    }.orEmpty()
    fun color(value: JsonElement?): List<AnimationExpression> {
        val values = value?.jsonArray ?: error("A layer needs an RGBA color")
        require(values.size == 4) { "Color must have four components in 0..1" }
        return values.map { expr(it) }
    }
    val usedTextures = linkedMapOf<String, ByteArray>()
    fun asset(file: String): ByteArray {
        require(themeZipEntryName(file) == file) { "Invalid asset path '$file'" }
        return entries[file] ?: error("Missing asset '$file'")
    }
    val layers = root["layers"]!!.jsonArray.mapIndexed { index, value ->
        require(value is JsonObject) { "layer[$index]: only a scene that extends another can reuse a layer by name" }
        val layer = value.jsonObject
        val name = layer["name"]?.jsonPrimitive?.content ?: "layer[$index]"
        try {
            val repeat = layer["repeat"]?.jsonPrimitive?.int ?: 1
            require(repeat > 0) { "Repeat must be positive" }
            val blend = layer["blend"]?.jsonPrimitive?.content ?: "srcOver"
            require(blend in setOf("srcOver", "lighten", "plus", "screen", "multiply")) { "Unknown blend '$blend'" }
            when (layer["type"]?.jsonPrimitive?.content) {
                "shader" -> {
                    val file = layer["file"]!!.jsonPrimitive.content
                    val uniforms = layer["uniforms"]?.jsonObject?.mapValues { (_, values) ->
                        (if (values is JsonArray) values.toList() else listOf(values)).map { expr(it) }
                    }.orEmpty()
                    val inputs = layer["inputs"]?.jsonObject?.mapValues { (_, fileValue) ->
                        fileValue.jsonPrimitive.content.also { usedTextures[it] = asset(it) }
                    }.orEmpty()
                    SceneLayer.Shader(name, repeat, blend, asset(file).decodeToString(), file, uniforms, inputs)
                }
                "disc" -> {
                    val path = layer["path"]?.jsonObject?.let {
                        val count = it["samples"]?.jsonPrimitive?.int ?: 48
                        require(count >= 2) { "Path needs at least two samples" }
                        ScenePath(expr(it["x"]), expr(it["y"]), count)
                    }
                    SceneLayer.Disc(name, repeat, blend, expr(layer["x"]), expr(layer["y"]),
                        expr(layer["radius"]), color(layer["color"]),
                        layer["tightness"]?.jsonPrimitive?.float ?: 2.2f,
                        layer["rim"]?.jsonPrimitive?.float ?: 0.48f, path, expr(layer["along"]))
                }
                "line" -> SceneLayer.Line(name, repeat, blend, expr(layer["x"]), expr(layer["y"]),
                    expr(layer["endX"], "width"), expr(layer["endY"]), expr(layer["width"], "1"), color(layer["color"]))
                "image" -> {
                    val file = layer["file"]!!.jsonPrimitive.content
                    usedTextures[file] = asset(file)
                    SceneLayer.Image(name, repeat, blend, file, expr(layer["x"]), expr(layer["y"]),
                        expr(layer["width"], "width"), expr(layer["height"], "height"),
                        expr(layer["alpha"], "1"), expr(layer["rotation"]))
                }
                else -> error("Unknown layer type")
            }
        } catch (error: Exception) { throw IllegalArgumentException("$name: ${error.message}", error) }
    }
    val frameRate = expr(root["frameRate"], "30")
    val maxClock = root["maxClock"]?.jsonPrimitive?.float ?: Float.MAX_VALUE
    val renderScale = root["renderScale"]?.jsonPrimitive?.float ?: 1f
    require(maxClock.isFinite() && maxClock > 0f && renderScale.isFinite() && renderScale > 0f) { "Clock and render scale must be positive finite numbers" }
    require(root["id"]!!.jsonPrimitive.content.matches(Regex("[A-Za-z0-9._-]+"))) { "Scene id must use letters, numbers, dots, underscores or hyphens" }
    require(root["name"]!!.jsonPrimitive.content.isNotBlank()) { "Scene needs a name" }
    val frozenTime = root["frozenTime"]?.jsonPrimitive?.double ?: 0.0
    require(frozenTime.isFinite()) { "Frozen time must be finite" }
    ThemeScene(root["id"]!!.jsonPrimitive.content, root["name"]!!.jsonPrimitive.content, source,
        symbols, variables, layers, frameRate, maxClock, renderScale,
        root["animateWhenMotionOff"]?.jsonPrimitive?.boolean ?: false,
        frozenTime, usedTextures)
} catch (error: Exception) {
    report(ThemeDiagnostic(source, error.message ?: error.javaClass.simpleName))
    null
}

/**
 * Resolves `extends`, a base scene in the same archive. The scene's own keys win.
 * Its variables replace the base's same-named ones in place and append new ones, so
 * later base variables see the override. `layers` is kept from the base unless given;
 * a string in it reuses the base layer with that name.
 */
private fun inheritScene(root: JsonObject, source: String, entries: Map<String, ByteArray>, chain: List<String> = listOf(source)): JsonObject {
    val basePath = root["extends"]?.jsonPrimitive?.content ?: return root
    require(themeZipEntryName(basePath) == basePath) { "Invalid base scene path '$basePath'" }
    require(basePath !in chain) { "Scene inheritance loops through '$basePath'" }
    require(chain.size < MAX_SCENE_INHERITANCE) { "Scene inheritance is deeper than $MAX_SCENE_INHERITANCE" }
    val bytes = entries[basePath] ?: error("Missing base scene '$basePath'")
    val base = try {
        inheritScene(Json.parseToJsonElement(bytes.decodeToString()).jsonObject, basePath, entries, chain + basePath)
    } catch (error: Exception) { throw IllegalArgumentException("$basePath: ${error.message}", error) }
    val variables = LinkedHashMap(base["variables"]?.jsonObject.orEmpty())
    variables.putAll(root["variables"]?.jsonObject.orEmpty())
    val baseLayers = base["layers"]?.jsonArray.orEmpty()
    val layers = root["layers"]?.jsonArray?.map { layer ->
        if (layer !is JsonPrimitive) return@map layer
        baseLayers.firstOrNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.content == layer.content }
            ?: error("'$basePath' has no layer named '${layer.content}'")
    } ?: baseLayers
    return JsonObject(base + root - "extends" + mapOf("variables" to JsonObject(variables), "layers" to JsonArray(layers)))
}

private const val MAX_SCENE_INHERITANCE = 8
