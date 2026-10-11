package app.foldcade.language

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
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

    /** Ribbon samples kept between frames, so a layout does not allocate. */
    internal val sampleX = FloatArray(BackdropLine.POINTS)
    internal val sampleY = Array(strands.size) { FloatArray(BackdropLine.POINTS) }

    fun clear() {
        lineCount = 0
        discCount = 0
    }

    fun line(): BackdropLine? {
        if (lineCount >= lines.size) return null
        val line = lines[lineCount++]
        line.count = 0
        line.perPoint = false
        line.shaped = false
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
    var perPoint: Boolean = false
    val gain = FloatArray(POINTS)
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

    /**
     * Real seconds between painted frames. Drift moves well under a pixel a frame at
     * 30 fps, so the backdrop does not ask for every refresh. The bead crosses the
     * panel in a few seconds and gets 60. Static creeps a pixel in about 45s.
     */
    const val DRIFT_FRAME_SECONDS = 1f / 30f
    const val GLIDE_FRAME_SECONDS = 1f / 60f
    const val SLOWEST_FRAME_SECONDS = 1f / 20f
    const val STATIC_FRAME_SECONDS = 8f
}

fun rgb(red: Int, green: Int, blue: Int): Color = Color(red, green, blue)

object MarkAccent {
    val clamshell: Color = Color(0xFFFF5C7A)
    val slim: Color = Color(0xFFFF7A45)
    val handheld: Color = Color(0xFFFFBA48)
    val cartridge: Color = Color(0xFF3EE0C3)
    val disc: Color = Color(0xFFBC9CFF)
    val cloud: Color = Color(0xFF40D6FF)

    /** Platform and library marks, keyed by mark name. */
    val byMark: Map<String, Color> = mapOf(
        "nintendo-3ds" to Color(0xFFFF5C7A),
        "nintendo-ds" to Color(0xFFFF7A45),
        "game-boy" to Color(0xFFB6F25C),
        "game-boy-color" to Color(0xFFC77DFF),
        "game-boy-advance" to Color(0xFF7C8CFF),
        "nes" to Color(0xFFFF4D5A),
        "snes" to Color(0xFFB39DFF),
        "nintendo-64" to Color(0xFF3EE68A),
        "gamecube" to Color(0xFF9B7BFF),
        "wii" to Color(0xFF8FDFFF),
        "nintendo-switch" to Color(0xFFFF4F5E),
        "playstation" to Color(0xFFC3CEDF),
        "playstation-2" to Color(0xFF4C8DFF),
        "psp" to Color(0xFF6FC3FF),
        "genesis" to Color(0xFFFF3B52),
        "master-system" to Color(0xFFFF5E3A),
        "game-gear" to Color(0xFF3EE6C0),
        "saturn" to Color(0xFFFFC15A),
        "dreamcast" to Color(0xFFFF8A3D),
        "android-games" to Color(0xFF3DDC84),
        "android-apps" to Color(0xFF3DDC84),
        "pc" to Color(0xFF4CC9FF),
        "moonlight" to Color(0xFFA9BCFF),
        "folder" to Color(0xFFFFB547),
        "library" to Color(0xFF3EE6C0),
        "console" to Color(0xFFFFB547),
    )
}

fun backdropClock(speed: MotionSpeed, animatorScale: Float): Float {
    if (speed == MotionSpeed.Off || animatorScale <= 0f) return 0f
    val factor = if (speed == MotionSpeed.Slower) 0.5f else 1f
    return (factor / animatorScale).coerceAtMost(Backdrop.MAX_CLOCK)
}

/**
 * Real seconds until the next frame at design time [timeSec]. A slower [clock] moves
 * less per second, so it waits longer for the same step on screen, down to
 * [Backdrop.SLOWEST_FRAME_SECONDS].
 */
fun backdropFrameSeconds(motion: BackgroundMotion, timeSec: Float, clock: Float): Float {
    if (motion == BackgroundMotion.Static) return Backdrop.STATIC_FRAME_SECONDS
    if (clock <= 0f) return Backdrop.SLOWEST_FRAME_SECONDS
    val gliding = motion == BackgroundMotion.Ribbons && beadShowing(loopTime(timeSec))
    val step = if (gliding) Backdrop.GLIDE_FRAME_SECONDS else Backdrop.DRIFT_FRAME_SECONDS
    return (step / clock).coerceIn(Backdrop.GLIDE_FRAME_SECONDS, Backdrop.SLOWEST_FRAME_SECONDS)
}

private fun beadShowing(loop: Float): Boolean =
    loop >= BEAD_START && loop <= BEAD_START + BEAD_DURATION

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
        BackgroundMotion.Static ->
            layoutRibbons(staticTime(timeSec, width), width, height, moving = false, Backdrop.STATIC_DIM, into)
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
    var beads = 0
    var shimmers = 0
    for (index in 0 until frame.discCount) {
        val disc = frame.discs[index]
        if (max(disc.red, max(disc.green, disc.blue)) <= Backdrop.LIT_CHANNEL) continue
        when (disc.kind) {
            MarkKind.Bead -> beads++
            MarkKind.Shimmer -> shimmers++
            else -> Unit
        }
    }
    return beads + shimmers + if (crossingLit(frame)) 1 else 0
}

private fun crossingLit(frame: BackdropFrame): Boolean {
    for (index in 0 until frame.lineCount) {
        val line = frame.lines[index]
        if (line.kind != MarkKind.Swell || line.count < 2) continue
        val steps = line.count - 1
        var sampleIndex = 0
        while (sampleIndex <= steps) {
            val sample = strokeAt(line, sampleIndex.toFloat() / steps.toFloat())
            if (max(sample.red, max(sample.green, sample.blue)) > Backdrop.LIT_CHANNEL) return true
            sampleIndex += 4
        }
    }
    return false
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
    val breath = ribbonBreath(timeSec, moving, dim)
    val ys = into.sampleY
    val xs = into.sampleX
    val steps = BackdropLine.POINTS - 1
    for (step in 0..steps) {
        xs[step] = width * step / steps.toFloat()
    }
    for (index in strands.indices) {
        val strand = strands[index]
        val halo = into.line() ?: return
        val glow = into.line() ?: return
        val core = into.line() ?: return
        val pulse = ribbonPulse(timeSec, index, moving)
        for (layer in 0 until RIBBON_LAYERS) {
            val line = when (layer) {
                0 -> halo
                1 -> glow
                else -> core
            }
            prepareRibbon(line, layerRadius(strand, layer), layerStart(strand, layer), layerEnd(strand, layer), breath, pulse)
        }
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
    placeTravelingBead(timeSec, loop, xs, ys, steps + 1, breath, into)
    placeShimmer(timeSec, loop, width, height, breath, into)
    placeCrossings(xs, ys, steps + 1, height, breath, into)
}

/**
 * The ribbons of [layoutBackdrop] as numbers for a per-pixel shader: the same strands,
 * breath, pulse, and colors. The shader paints them with the [GlowFalloff] curve along
 * the whole length, where the stroked layout steps from one stretch to the next.
 * Beads and shimmers stay discs from [layoutBackdrop].
 */
class RibbonField {
    var travel: Float = 0f

    /** Per strand: anchor, sweep, wave, waves. Fractions of the panel height. */
    val shape = FloatArray(STRANDS * 4)

    /** Per strand: phase, bob as a fraction of the height, pulse, unused. */
    val drift = FloatArray(STRANDS * 4)

    /** Per strand and layer (halo, glow, core): left-end rgb 0..1 and base radius in px. */
    val start = FloatArray(STRANDS * RIBBON_LAYERS * 4)

    /** Per strand and layer: right-end rgb 0..1, unused. */
    val end = FloatArray(STRANDS * RIBBON_LAYERS * 4)

    /** Crossing rgb 0..1, and 1 when crossings are lit. */
    val swell = FloatArray(4)

    /** Gap in px at which a crossing has faded out. */
    var reach: Float = 0f

    companion object {
        const val STRANDS = 3
        const val LAYERS = RIBBON_LAYERS
        const val CROSSING_RADIUS = 30f

        /** Length at each end over which a ribbon fades in, as a fraction of it. */
        const val END_FADE = 0.16f
    }
}

/** Fills [into] for [motion] and returns true, or false when [motion] has no ribbons. */
fun fieldRibbons(
    motion: BackgroundMotion,
    timeSec: Float,
    width: Float,
    height: Float,
    moving: Boolean,
    into: RibbonField,
): Boolean {
    val time: Float
    val dim: Float
    val live: Boolean
    when (motion) {
        BackgroundMotion.Ribbons -> {
            time = timeSec
            dim = 1f
            live = moving
        }
        BackgroundMotion.Static -> {
            time = staticTime(timeSec, width)
            dim = Backdrop.STATIC_DIM
            live = false
        }
        else -> return false
    }
    val travel = time / Backdrop.DRIFT_SECONDS
    val breath = ribbonBreath(time, live, dim)
    into.travel = travel
    for (index in strands.indices) {
        val strand = strands[index]
        val at = index * 4
        into.shape[at] = strand.anchor
        into.shape[at + 1] = strand.sweep
        into.shape[at + 2] = strand.wave
        into.shape[at + 3] = strand.waves
        into.drift[at] = strand.phase
        into.drift[at + 1] = 0.04f * sin(TWO_PI * travel + strand.phase)
        into.drift[at + 2] = ribbonPulse(time, index, live)
        into.drift[at + 3] = 0f
        for (layer in 0 until RIBBON_LAYERS) {
            val slot = (index * RIBBON_LAYERS + layer) * 4
            val left = layerStart(strand, layer)
            val right = layerEnd(strand, layer)
            for (channel in 0 until 3) {
                into.start[slot + channel] = left[channel] * breath / 255f
                into.end[slot + channel] = right[channel] * breath / 255f
            }
            into.start[slot + 3] = layerRadius(strand, layer)
            into.end[slot + 3] = 0f
        }
    }
    for (channel in 0 until 3) into.swell[channel] = swellColor[channel] * breath / 255f
    into.swell[3] = if (live) 1f else 0f
    into.reach = height * CROSSING_REACH
    return true
}

private fun staticTime(timeSec: Float, width: Float): Float {
    val creep = timeSec * (Backdrop.STATIC_SHIFT_PX / width) / Backdrop.STATIC_SHIFT_SECONDS
    return creep * Backdrop.DRIFT_SECONDS
}

private fun ribbonBreath(timeSec: Float, moving: Boolean, dim: Float): Float =
    (if (moving) breath(timeSec) else 1f) * dim

private fun ribbonPulse(timeSec: Float, index: Int, moving: Boolean): Float =
    if (moving) timeSec / 36f + index * 0.33f else 0.2f + index * 0.17f

private fun layerRadius(strand: Strand, layer: Int): Float = when (layer) {
    0 -> if (strand.teal) 15f else 14f
    1 -> if (strand.teal) 6.4f else 6f
    else -> 2.5f
}

private fun layerStart(strand: Strand, layer: Int): IntArray = when (layer) {
    0 -> if (strand.teal) tealHalo else amberHalo
    1 -> if (strand.teal) tealGlow else amberGlow
    else -> if (strand.teal) tealCore else amberCore
}

private fun layerEnd(strand: Strand, layer: Int): IntArray = when (layer) {
    0 -> if (strand.teal) blueHalo else roseHalo
    1 -> if (strand.teal) blueGlow else roseGlow
    else -> if (strand.teal) blueCore else roseCore
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
            rise < EMBER_FADE -> smoothStep(rise / EMBER_FADE)
            rise > 1f - EMBER_FADE -> smoothStep((1f - rise) / EMBER_FADE)
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

/**
 * Fade in and out with smoothstep. [Backdrop.FADE_SECONDS] is 2.5s, and the clock
 * cap keeps that at least 2s, so an effect is fully transparent at both ends.
 * Outside the window the value is 0, and the derivative matches, so a loop that
 * does not cut through a window cannot pop.
 */
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

/** One soft dot. Position is arc length along the ribbon at [timeSec], not a sample index. */
private fun placeTravelingBead(
    timeSec: Float,
    loop: Float,
    xs: FloatArray,
    ys: Array<FloatArray>,
    count: Int,
    breath: Float,
    into: BackdropFrame,
) {
    val fade = window(loop, BEAD_START, BEAD_DURATION)
    if (fade <= 0f) return
    val which = Math.floorMod(floor(timeSec / Backdrop.EFFECT_LOOP).toInt(), strands.size)
    val travel = ((loop - BEAD_START) / BEAD_DURATION).coerceIn(0f, 1f)
    val point = pointOnArc(xs, ys[which], count, travel)
    val color = if (strands[which].teal) beadTeal else beadAmber
    placeDisc(into, point[0], point[1], BEAD_RADIUS, color, breath * fade, MarkKind.Bead)
}

/** A still glimmer. It fades out before the loop chooses the next point, so the hand-off is black. */
private fun placeShimmer(
    timeSec: Float,
    loop: Float,
    width: Float,
    height: Float,
    breath: Float,
    into: BackdropFrame,
) {
    val fade = window(loop, SHIMMER_START, SHIMMER_DURATION)
    if (fade <= 0f) return
    val turn = floor(timeSec / Backdrop.EFFECT_LOOP).toInt()
    val x = (0.08f + unitHash(turn, 17) * 0.84f) * width
    val y = (0.08f + unitHash(turn, 29) * 0.84f) * height
    placeDisc(into, x, y, 28f, shimmerColor, breath * fade * 0.85f, MarkKind.Shimmer)
}

/**
 * One glow between each pair of ribbons. Brightness is smoothstep of the gap, so a
 * crossing swells and recedes as the ribbons drift. There is no time window and no cutoff.
 */
private fun placeCrossings(
    xs: FloatArray,
    ys: Array<FloatArray>,
    count: Int,
    height: Float,
    breath: Float,
    into: BackdropFrame,
) {
    val reach = height * CROSSING_REACH
    val radius = RibbonField.CROSSING_RADIUS
    for (left in ys.indices) {
        for (right in left + 1 until ys.size) {
            val line = into.line() ?: return
            prepare(line, MarkKind.Swell, radius, swellColor, breath)
            line.shaped = true
            line.perPoint = true
            for (step in 0 until count) {
                val gap = abs(ys[left][step] - ys[right][step])
                line.gain[step] = 1f - smoothStep((gap / reach).coerceIn(0f, 1f))
                line.add(xs[step], (ys[left][step] + ys[right][step]) * 0.5f)
            }
        }
    }
}

/** [fraction] is 0 at the start of the polyline and 1 at the end, measured in arc length. */
internal fun pointOnArc(xs: FloatArray, ys: FloatArray, count: Int, fraction: Float): FloatArray {
    val point = floatArrayOf(xs[0], ys[0])
    if (count < 2) return point
    var total = 0f
    for (index in 1 until count) {
        total += hypot(xs[index] - xs[index - 1], ys[index] - ys[index - 1])
    }
    if (total <= 0.01f) return point
    val target = fraction.coerceIn(0f, 1f) * total
    var walked = 0f
    for (index in 1 until count) {
        val span = hypot(xs[index] - xs[index - 1], ys[index] - ys[index - 1])
        if (walked + span >= target || index == count - 1) {
            val u = if (span <= 0.0001f) 0f else ((target - walked) / span).coerceIn(0f, 1f)
            point[0] = xs[index - 1] + (xs[index] - xs[index - 1]) * u
            point[1] = ys[index - 1] + (ys[index] - ys[index - 1]) * u
            return point
        }
        walked += span
    }
    point[0] = xs[count - 1]
    point[1] = ys[count - 1]
    return point
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
    line.perPoint = false
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
 * Each knot is a cosine bump, so it ramps in and out instead of stepping.
 */
internal fun ribbonGain(along: Float, pulse: Float): Float {
    // RibbonShader repeats this curve per pixel. Change both together.
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
    val half = GlowFalloff.KNOT
    if (distance >= half) return 0f
    val u = distance / half
    return 0.5f * (1f + cos(u * PI.toFloat()))
}

/** Center color and outer radius for the piece of [line] at [along] (0 at the left). */
fun strokeAt(line: BackdropLine, along: Float): StrokeSample {
    if (line.perPoint) {
        val gain = pointGain(line, along)
        return StrokeSample(
            line.radius,
            scale(line.red, gain),
            scale(line.green, gain),
            scale(line.blue, gain),
        )
    }
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

private fun pointGain(line: BackdropLine, along: Float): Float {
    val last = line.count - 1
    if (last <= 0) return if (line.count > 0) line.gain[0] else 0f
    val scaled = along.coerceIn(0f, 1f) * last.toFloat()
    val index = scaled.toInt().coerceIn(0, last - 1)
    val fraction = scaled - index
    return line.gain[index] + (line.gain[index + 1] - line.gain[index]) * fraction
}

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
private const val END_FADE = RibbonField.END_FADE
private const val BEAD_START = 0.4f
private const val BEAD_DURATION = 8f
private const val BEAD_RADIUS = 22f
private const val SHIMMER_START = 6.8f
private const val SHIMMER_DURATION = 7f
private const val CROSSING_REACH = 0.09f
private const val EMBER_FADE = 0.12f
private const val RIBBON_LAYERS = 3

/**
 * Shared radial curve. 1 at the center, 0 at the rim, with a gaussian body and a
 * smoothstep tail so the last stop is transparent and the slope there is flat.
 * [STOPS] is the same curve sampled for the GPU gradient.
 */
object GlowFalloff {
    const val TIGHTNESS = 5.6f
    const val RIM_START = 0.55f
    const val REACH = 1.36f

    /** Ribbon layers already carry dim colors. This scales the radial curve on top. */
    const val PEAK = 1f
    const val DISC_TIGHTNESS = 2.2f
    const val DISC_RIM = 0.48f
    const val DISC_REACH = 1.55f
    const val TILE_TIGHTNESS = 0.85f
    const val TILE_RIM = 0.66f
    const val TILE_PEAK = 1f
    const val REST_TIGHTNESS = 2.4f
    const val REST_RIM = 0.42f
    const val REST_PEAK = 0.22f

    /** Half-width of a hot spot, as a fraction of the ribbon. The old knot was 0.075. */
    const val KNOT = 0.14f

    val STOPS = floatArrayOf(
        0f, 0.08f, 0.16f, 0.25f, 0.34f, 0.43f, 0.52f, 0.61f, 0.70f, 0.80f, 0.90f, 1f,
    )

    fun cover(t: Float, tightness: Float = TIGHTNESS, rimStart: Float = RIM_START): Float {
        if (t >= 1f) return 0f
        if (t <= 0f) return 1f
        val bell = exp(-tightness * t * t)
        if (t <= rimStart) return bell
        val u = ((t - rimStart) / (1f - rimStart)).coerceIn(0f, 1f)
        return bell * (1f - smoothStep(u))
    }
}

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
    val reach = if (soft) radius * GlowFalloff.REACH else radius + FRINGE
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
                    GlowFalloff.cover(dist / reach)
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
    val reach = disc.radius * GlowFalloff.DISC_REACH
    if (reach < 0.4f) return
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
                val cover = GlowFalloff.cover(dist / reach, GlowFalloff.DISC_TIGHTNESS, GlowFalloff.DISC_RIM)
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
