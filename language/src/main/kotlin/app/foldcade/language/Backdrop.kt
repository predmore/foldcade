package app.foldcade.language

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * OLED backdrop. Ribbons drift on a fixed clock. Focus changes do not move them.
 * [backdropClock] is zero when motion is off or the animator scale has reduced motion.
 */
enum class BackgroundMotion {
    Ribbons,
    Embers,
    Static,
    Off,
    ;

    fun next(): BackgroundMotion = when (this) {
        Ribbons -> Embers
        Embers -> Static
        Static -> Off
        Off -> Ribbons
    }

    val wire: String
        get() = when (this) {
            Ribbons -> "ribbons"
            Embers -> "embers"
            Static -> "static"
            Off -> "off"
        }

    companion object {
        fun fromWire(raw: String?): BackgroundMotion? = when (raw) {
            "ribbons" -> Ribbons
            "embers" -> Embers
            "static" -> Static
            "off" -> Off
            else -> null
        }
    }
}

enum class MotionSpeed {
    Slow,
    Slower,
    Off,
    ;

    fun next(): MotionSpeed = when (this) {
        Slow -> Slower
        Slower -> Off
        Off -> Slow
    }

    val wire: String
        get() = when (this) {
            Slow -> "slow"
            Slower -> "slower"
            Off -> "off"
        }

    companion object {
        fun fromWire(raw: String?): MotionSpeed? = when (raw) {
            "slow" -> Slow
            "slower" -> Slower
            "off" -> Off
            else -> null
        }
    }
}

enum class MarkKind {
    Ribbon,
    Bead,
    Swell,
    Shimmer,
    Ember,
    Horizon,
}

/**
 * One painted frame. Callers reuse the same instance so a frame does not allocate.
 * [layoutBackdrop] fills it. The rasterizer and the canvas both read it.
 */
class BackdropFrame {
    val lines = Array(16) { BackdropLine() }
    var lineCount: Int = 0
    val discs = Array(24) { BackdropDisc() }
    var discCount: Int = 0

    fun clear() {
        lineCount = 0
        discCount = 0
    }

    fun line(): BackdropLine? {
        if (lineCount >= lines.size) return null
        val line = lines[lineCount++]
        line.count = 0
        return line
    }

    fun disc(): BackdropDisc? {
        if (discCount >= discs.size) return null
        return discs[discCount++]
    }
}

class BackdropLine {
    val x = FloatArray(POINTS)
    val y = FloatArray(POINTS)
    var count: Int = 0
    var radius: Float = 0f
    var red: Int = 0
    var green: Int = 0
    var blue: Int = 0
    var endRed: Int = 0
    var endGreen: Int = 0
    var endBlue: Int = 0
    var pulse: Float = 0f
    var shaped: Boolean = false
    var kind: MarkKind = MarkKind.Ribbon

    fun add(px: Float, py: Float) {
        if (count >= POINTS) return
        x[count] = px
        y[count] = py
        count++
    }

    companion object {
        const val POINTS = 48
    }
}

/** One short piece of a ribbon. [radius] is the outer reach; color is the center. */
data class StrokeSample(val radius: Float, val red: Int, val green: Int, val blue: Int)

class BackdropDisc {
    var cx: Float = 0f
    var cy: Float = 0f
    var radius: Float = 0f
    var red: Int = 0
    var green: Int = 0
    var blue: Int = 0
    var kind: MarkKind = MarkKind.Shimmer
}

data class LitSample(
    val fraction: Float,
    val peak: Int,
    val litPixels: Int,
    val pixels: Int,
)

object Backdrop {
    const val LIT_CHANNEL = 12
    const val LIT_BUDGET = 0.08f

    /** One ribbon drift at Slow and animator scale 1. Inside the 60–120s brief. */
    const val DRIFT_SECONDS = 90f

    /** Brightness breath. Longer than 30s, within ±10%. */
    const val BREATH_SECONDS = 48f
    const val BREATH_AMOUNT = 0.08f

    /** Fade in and out. Real time stays at least 2s because [backdropClock] is capped. */
    const val FADE_SECONDS = 2.5f

    /**
     * Animator scale above 1 speeds motion up. The cap keeps a Slow drift at least 72s,
     * a breath at least 38s, and a 2.5s fade at least 2s.
     */
    const val MAX_CLOCK = 1.25f

    const val EFFECT_LOOP = 22f

    /** Static is a dimmed ribbon frame that creeps a few pixels every few minutes. */
    const val STATIC_DIM = 0.28f
    const val STATIC_SHIFT_PX = 4f
    const val STATIC_SHIFT_SECONDS = 180f
}

fun rgb(red: Int, green: Int, blue: Int): Color = Color(red, green, blue)

object MarkAccent {
    val clamshell: Color = Color(0xFFFF5C7A)
    val slim: Color = Color(0xFFFF7A45)
    val handheld: Color = Color(0xFFFFBA48)
    val cartridge: Color = Color(0xFF3EE0C3)
    val disc: Color = Color(0xFFBC9CFF)
    val cloud: Color = Color(0xFF40D6FF)
}

fun backdropClock(speed: MotionSpeed, animatorScale: Float): Float {
    if (speed == MotionSpeed.Off || animatorScale <= 0f) return 0f
    val factor = if (speed == MotionSpeed.Slower) 0.5f else 1f
    return (factor / animatorScale).coerceAtMost(Backdrop.MAX_CLOCK)
}

fun layoutBackdrop(
    motion: BackgroundMotion,
    timeSec: Float,
    width: Float,
    height: Float,
    moving: Boolean,
    into: BackdropFrame,
) {
    into.clear()
    if (width < 2f || height < 2f) return
    when (motion) {
        BackgroundMotion.Ribbons -> layoutRibbons(timeSec, width, height, moving, 1f, into)
        BackgroundMotion.Embers -> layoutEmbers(timeSec, width, height, moving, into)
        BackgroundMotion.Static -> {
            val creep = timeSec * (Backdrop.STATIC_SHIFT_PX / width) / Backdrop.STATIC_SHIFT_SECONDS
            layoutRibbons(creep * Backdrop.DRIFT_SECONDS, width, height, moving = false, Backdrop.STATIC_DIM, into)
        }
        BackgroundMotion.Off -> Unit
    }
}

/**
 * Fraction of pixels whose strongest channel is above [Backdrop.LIT_CHANNEL], on black.
 * The fringe matches a hair of canvas antialiasing so the count is not a low guess.
 */
internal fun paintedPixels(frame: BackdropFrame, width: Int, height: Int): IntArray {
    val pixels = IntArray(width * height)
    val scratch = IntArray(width * height)
    for (index in 0 until frame.lineCount) {
        val line = frame.lines[index]
        val steps = line.count - 1
        val target = if (line.shaped) scratch else pixels
        if (line.shaped) scratch.fill(0)
        var previous = 1
        while (previous <= steps) {
            val along = (previous - 0.5f) / steps.toFloat()
            val sample = strokeAt(line, along)
            paintSegment(
                target,
                width,
                height,
                line.x[previous - 1],
                line.y[previous - 1],
                line.x[previous],
                line.y[previous],
                sample.radius,
                sample.red,
                sample.green,
                sample.blue,
                line.shaped,
            )
            previous++
        }
        if (line.shaped) screenOnto(pixels, scratch)
    }
    for (index in 0 until frame.discCount) {
        val disc = frame.discs[index]
        paintDisc(pixels, width, height, disc)
    }
    return pixels
}

fun litSample(frame: BackdropFrame, width: Int, height: Int): LitSample {
    val pixels = paintedPixels(frame, width, height)
    var lit = 0
    var peak = 0
    for (pixel in pixels) {
        val channel = maxChannel(pixel)
        if (channel > peak) peak = channel
        if (channel > Backdrop.LIT_CHANNEL) lit++
    }
    val total = width * height
    return LitSample(
        fraction = if (total == 0) 0f else lit.toFloat() / total.toFloat(),
        peak = peak,
        litPixels = lit,
        pixels = total,
    )
}

fun visibleEffects(frame: BackdropFrame): Int {
    var count = 0
    for (index in 0 until frame.discCount) {
        val disc = frame.discs[index]
        if (disc.kind != MarkKind.Bead && disc.kind != MarkKind.Swell && disc.kind != MarkKind.Shimmer) continue
        if (max(disc.red, max(disc.green, disc.blue)) > Backdrop.LIT_CHANNEL) count++
    }
    return count
}

private data class Strand(
    val anchor: Float,
    val sweep: Float,
    val wave: Float,
    val waves: Float,
    val phase: Float,
    val teal: Boolean,
)

private val strands = arrayOf(
    Strand(anchor = 0.40f, sweep = 0.20f, wave = 0.030f, waves = 2f, phase = 0.6f, teal = true),
    Strand(anchor = 0.62f, sweep = 0.15f, wave = 0.026f, waves = 2f, phase = 2.5f, teal = false),
    Strand(anchor = 0.33f, sweep = 0.17f, wave = 0.024f, waves = 3f, phase = 4.3f, teal = true),
)

private fun layoutRibbons(
    timeSec: Float,
    width: Float,
    height: Float,
    moving: Boolean,
    dim: Float,
    into: BackdropFrame,
) {
    val travel = timeSec / Backdrop.DRIFT_SECONDS
    val breath = (if (moving) breath(timeSec) else 1f) * dim
    val ys = Array(strands.size) { FloatArray(BackdropLine.POINTS) }
    val xs = FloatArray(BackdropLine.POINTS)
    val steps = BackdropLine.POINTS - 1
    for (step in 0..steps) {
        xs[step] = width * step / steps.toFloat()
    }
    for (index in strands.indices) {
        val strand = strands[index]
        val halo = into.line() ?: return
        val glow = into.line() ?: return
        val core = into.line() ?: return
        val pulse = if (moving) timeSec / 36f + index * 0.33f else 0.2f + index * 0.17f
        val teal = strand.teal
        prepareRibbon(halo, if (teal) 15f else 14f, if (teal) tealHalo else amberHalo, if (teal) blueHalo else roseHalo, breath, pulse)
        prepareRibbon(glow, if (teal) 6.4f else 6f, if (teal) tealGlow else amberGlow, if (teal) blueGlow else roseGlow, breath, pulse)
        prepareRibbon(core, 2.5f, if (teal) tealCore else amberCore, if (teal) blueCore else roseCore, breath, pulse)
        for (step in 0..steps) {
            val s = xs[step] / width + travel
            val y = strandY(strand, s, travel) * height
            ys[index][step] = y
            halo.add(xs[step], y)
            glow.add(xs[step], y)
            core.add(xs[step], y)
        }
    }
    if (!moving) return
    val loop = loopTime(timeSec)
    val bead = window(loop, start = 0.4f, duration = 8f)
    if (bead > 0f) {
        val which = Math.floorMod(kotlin.math.floor(timeSec / Backdrop.EFFECT_LOOP).toInt(), strands.size)
        val progress = ((loop - 0.4f) / 8f).coerceIn(0f, 1f)
        val step = (progress * steps).toInt().coerceIn(0, steps)
        val strand = strands[which]
        val color = if (strand.teal) beadTeal else beadAmber
        placeDisc(into, xs[step], ys[which][step], 8f, color, breath * bead, MarkKind.Bead)
    }
    val shimmer = window(loop, start = 6.8f, duration = 7f)
    if (shimmer > 0f) {
        val turn = kotlin.math.floor(timeSec / Backdrop.EFFECT_LOOP).toInt()
        val x = (0.08f + unitHash(turn, 17) * 0.84f) * width
        val y = (0.08f + unitHash(turn, 29) * 0.84f) * height
        placeDisc(into, x, y, 24f, shimmerColor, breath * shimmer * 0.85f, MarkKind.Shimmer)
    }
    val swell = window(loop, start = 13.2f, duration = 8f)
    if (swell > 0f) {
        var best = Float.POSITIVE_INFINITY
        var bestX = width * 0.5f
        var bestY = height * 0.5f
        for (step in 0..steps) {
            for (left in strands.indices) {
                for (right in left + 1 until strands.size) {
                    val gap = kotlin.math.abs(ys[left][step] - ys[right][step])
                    if (gap < best) {
                        best = gap
                        bestX = xs[step]
                        bestY = (ys[left][step] + ys[right][step]) * 0.5f
                    }
                }
            }
        }
        val closeness = (1f - best / (height * 0.055f)).coerceIn(0f, 1f)
        if (closeness > 0f) {
            placeDisc(into, bestX, bestY, 36f, swellColor, breath * swell * closeness, MarkKind.Swell)
        }
    }
}

private fun layoutEmbers(
    timeSec: Float,
    width: Float,
    height: Float,
    moving: Boolean,
    into: BackdropFrame,
) {
    val breath = if (moving) breath(timeSec) else 1f
    val lift = sin(TWO_PI * timeSec / 84f) * 8f
    for (band in 0 until 5) {
        val line = into.line() ?: return
        val gain = (5 - band) / 5f * 0.5f
        prepare(line, MarkKind.Horizon, 3.1f, horizonColor, breath * gain)
        val y = height - 8f - band * 5.2f + lift
        line.add(0f, y)
        line.add(width, y)
    }
    for (index in 0 until 12) {
        val cycle = 66f + (index % 4) * 6f
        var rise = (timeSec / cycle + index * 0.137f) % 1f
        if (rise < 0f) rise += 1f
        val y = height + 10f - rise * (height + 20f)
        val x = (0.06f + (index * 73 % 88) / 100f) * width +
            sin(TWO_PI * timeSec / 96f + index) * 14f
        val edge = when {
            rise < 0.1f -> rise / 0.1f
            rise > 0.9f -> (1f - rise) / 0.1f
            else -> 1f
        }
        val color = if (index % 2 == 0) warmEmber else violetEmber
        placeDisc(into, x, y, 2.3f, color, breath * edge, MarkKind.Ember)
    }
}

private fun strandY(strand: Strand, s: Float, travel: Float): Float {
    val sweep = strand.sweep * sin(TWO_PI * s + strand.phase)
    val wave = strand.wave * sin(TWO_PI * s * strand.waves + strand.phase * 1.7f)
    val bob = 0.04f * sin(TWO_PI * travel + strand.phase)
    return strand.anchor + sweep + wave + bob
}

private fun breath(timeSec: Float): Float =
    1f + Backdrop.BREATH_AMOUNT * sin(TWO_PI * timeSec / Backdrop.BREATH_SECONDS)

private fun loopTime(timeSec: Float): Float {
    val raw = timeSec % Backdrop.EFFECT_LOOP
    return if (raw < 0f) raw + Backdrop.EFFECT_LOOP else raw
}

private fun window(loop: Float, start: Float, duration: Float): Float {
    val along = loop - start
    if (along < 0f || along > duration) return 0f
    val fade = Backdrop.FADE_SECONDS
    return when {
        along < fade -> smoothStep(along / fade)
        along > duration - fade -> smoothStep((duration - along) / fade)
        else -> 1f
    }
}

private fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun prepare(line: BackdropLine, kind: MarkKind, radius: Float, color: IntArray, gain: Float) {
    line.kind = kind
    line.radius = radius
    line.red = scale(color[0], gain)
    line.green = scale(color[1], gain)
    line.blue = scale(color[2], gain)
    line.endRed = line.red
    line.endGreen = line.green
    line.endBlue = line.blue
    line.pulse = 0f
    line.shaped = false
    line.count = 0
}

private fun prepareRibbon(
    line: BackdropLine,
    radius: Float,
    start: IntArray,
    end: IntArray,
    gain: Float,
    pulse: Float,
) {
    prepare(line, MarkKind.Ribbon, radius, start, gain)
    line.endRed = scale(end[0], gain)
    line.endGreen = scale(end[1], gain)
    line.endBlue = scale(end[2], gain)
    line.pulse = pulse
    line.shaped = true
}

/**
 * Brightness and width along one ribbon. Ends fade out. A slow swell leaves dim
 * stretches, and two hotter knots travel the length when [pulse] advances.
 */
internal fun ribbonGain(along: Float, pulse: Float): Float {
    val t = along.coerceIn(0f, 1f)
    val edge = when {
        t < END_FADE -> smoothStep(t / END_FADE)
        t > 1f - END_FADE -> smoothStep((1f - t) / END_FADE)
        else -> 1f
    }
    if (edge <= 0f) return 0f
    val swell = 0.34f + 0.66f * (0.5f + 0.5f * sin(TWO_PI * (t * 2.15f + 0.35f)))
    val travel = if (pulse == 0f) 0.22f else pulse
    val spots = knot(t, travel) * 0.9f + knot(t, travel + 0.46f) * 0.5f
    return (edge * (swell * 0.58f + spots)).coerceIn(0f, 1f)
}

private fun knot(along: Float, phase: Float): Float {
    val center = phase - floor(phase)
    val direct = abs(along - center)
    val distance = min(direct, 1f - direct)
    if (distance >= KNOT) return 0f
    val u = 1f - distance / KNOT
    return u * u
}

/** Center color and outer radius for the piece of [line] at [along] (0 at the left). */
fun strokeAt(line: BackdropLine, along: Float): StrokeSample {
    if (!line.shaped) return StrokeSample(line.radius, line.red, line.green, line.blue)
    val t = along.coerceIn(0f, 1f)
    val gain = ribbonGain(t, line.pulse)
    return StrokeSample(
        radius = line.radius * (0.28f + 0.92f * gain),
        red = mixChannel(line.red, line.endRed, t, gain),
        green = mixChannel(line.green, line.endGreen, t, gain),
        blue = mixChannel(line.blue, line.endBlue, t, gain),
    )
}

private fun mixChannel(start: Int, end: Int, along: Float, gain: Float): Int =
    ((start + (end - start) * along) * gain).toInt().coerceIn(0, 255)

private fun placeDisc(
    into: BackdropFrame,
    cx: Float,
    cy: Float,
    radius: Float,
    color: IntArray,
    gain: Float,
    kind: MarkKind,
) {
    val disc = into.disc() ?: return
    disc.cx = cx
    disc.cy = cy
    disc.radius = radius
    disc.red = scale(color[0], gain)
    disc.green = scale(color[1], gain)
    disc.blue = scale(color[2], gain)
    disc.kind = kind
}

private fun scale(channel: Int, gain: Float): Int = (channel * gain).toInt().coerceIn(0, 255)

private fun unitHash(turn: Int, salt: Int): Float {
    var mixed = turn * 374761393 + salt * 668265263
    mixed = mixed xor (mixed ushr 13)
    mixed *= 1274126177
    val positive = mixed.toLong() and 0x7fffffffL
    return (positive % 10000L) / 10000f
}

private val tealHalo = intArrayOf(14, 36, 34)
private val blueHalo = intArrayOf(12, 22, 40)
private val amberHalo = intArrayOf(36, 20, 10)
private val roseHalo = intArrayOf(34, 14, 20)
private val tealGlow = intArrayOf(20, 52, 48)
private val blueGlow = intArrayOf(16, 32, 58)
private val amberGlow = intArrayOf(52, 30, 14)
private val roseGlow = intArrayOf(50, 20, 28)
private val tealCore = intArrayOf(28, 70, 66)
private val blueCore = intArrayOf(22, 40, 78)
private val amberCore = intArrayOf(72, 44, 20)
private val roseCore = intArrayOf(70, 28, 40)
private val beadTeal = intArrayOf(78, 168, 154)
private val beadAmber = intArrayOf(168, 118, 52)
private val shimmerColor = intArrayOf(34, 38, 36)
private val swellColor = intArrayOf(44, 52, 40)
private val warmEmber = intArrayOf(140, 76, 42)
private val violetEmber = intArrayOf(108, 82, 142)
private val horizonColor = intArrayOf(32, 14, 26)

private const val TWO_PI = (PI * 2).toFloat()
private const val FRINGE = 0.75f
private const val END_FADE = 0.16f
private const val KNOT = 0.075f

private fun paintSegment(
    pixels: IntArray,
    width: Int,
    height: Int,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    radius: Float,
    red: Int,
    green: Int,
    blue: Int,
    soft: Boolean = false,
) {
    if (radius < 0.4f || red + green + blue == 0) return
    val reach = if (soft) radius else radius + FRINGE
    val minX = floor(min(x0, x1) - reach).toInt().coerceIn(0, width - 1)
    val maxX = ceil(max(x0, x1) + reach).toInt().coerceIn(0, width - 1)
    val minY = floor(min(y0, y1) - reach).toInt().coerceIn(0, height - 1)
    val maxY = ceil(max(y0, y1) + reach).toInt().coerceIn(0, height - 1)
    var y = minY
    while (y <= maxY) {
        var x = minX
        val row = y * width
        while (x <= maxX) {
            val dist = distanceToSegment(x + 0.5f, y + 0.5f, x0, y0, x1, y1)
            if (dist <= reach) {
                val cover = if (soft) {
                    val u = 1f - dist / radius
                    u * u
                } else if (dist <= radius) {
                    1f
                } else {
                    (reach - dist) / FRINGE
                }
                val index = row + x
                pixels[index] = maxBlend(
                    pixels[index],
                    scale(red, cover),
                    scale(green, cover),
                    scale(blue, cover),
                )
            }
            x++
        }
        y++
    }
}

private fun paintDisc(pixels: IntArray, width: Int, height: Int, disc: BackdropDisc) {
    val reach = disc.radius + FRINGE
    val minX = floor(disc.cx - reach).toInt().coerceIn(0, width - 1)
    val maxX = ceil(disc.cx + reach).toInt().coerceIn(0, width - 1)
    val minY = floor(disc.cy - reach).toInt().coerceIn(0, height - 1)
    val maxY = ceil(disc.cy + reach).toInt().coerceIn(0, height - 1)
    var y = minY
    while (y <= maxY) {
        var x = minX
        val row = y * width
        while (x <= maxX) {
            val dist = hypot(x + 0.5f - disc.cx, y + 0.5f - disc.cy)
            if (dist <= reach) {
                val falloff = (1f - dist / disc.radius).coerceAtLeast(0f)
                val cover = if (dist <= disc.radius) falloff else falloff * (reach - dist) / FRINGE
                val index = row + x
                pixels[index] = maxBlend(
                    pixels[index],
                    scale(disc.red, cover),
                    scale(disc.green, cover),
                    scale(disc.blue, cover),
                )
            }
            x++
        }
        y++
    }
}

private fun distanceToSegment(px: Float, py: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
    val dx = x1 - x0
    val dy = y1 - y0
    val length = dx * dx + dy * dy
    val t = if (length <= 0.01f) 0f else (((px - x0) * dx + (py - y0) * dy) / length).coerceIn(0f, 1f)
    return hypot(x0 + dx * t - px, y0 + dy * t - py)
}

private fun maxBlend(existing: Int, red: Int, green: Int, blue: Int): Int {
    val oldRed = existing shr 16
    val oldGreen = (existing shr 8) and 255
    val oldBlue = existing and 255
    return (max(oldRed, red) shl 16) or (max(oldGreen, green) shl 8) or max(oldBlue, blue)
}

private fun screenOnto(dest: IntArray, src: IntArray) {
    for (index in dest.indices) {
        val sample = src[index]
        if (sample != 0) dest[index] = screenBlend(dest[index], sample)
    }
}

/** A little extra light where strokes overlap, without climbing to white. */
private fun screenBlend(existing: Int, added: Int): Int {
    val red = screenChannel(existing shr 16, added shr 16)
    val green = screenChannel((existing shr 8) and 255, (added shr 8) and 255)
    val blue = screenChannel(existing and 255, added and 255)
    return (red shl 16) or (green shl 8) or blue
}

private fun screenChannel(base: Int, added: Int): Int =
    255 - ((255 - base) * (255 - added) / 255)

private fun maxChannel(pixel: Int): Int = max(pixel shr 16, max((pixel shr 8) and 255, pixel and 255))
