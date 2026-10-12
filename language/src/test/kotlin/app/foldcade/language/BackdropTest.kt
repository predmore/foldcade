package app.foldcade.language

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropTest {
    @Test
    fun clockFollowsSpeedAndAnimatorScale() {
        assertEquals(0f, referenceBackdropClock(MotionSpeed.Off, 1f), 0.001f)
        assertEquals(0f, referenceBackdropClock(MotionSpeed.Slow, 0f), 0.001f)
        assertEquals(0f, referenceBackdropClock(MotionSpeed.Slow, -1f), 0.001f)
        assertEquals(1f, referenceBackdropClock(MotionSpeed.Slow, 1f), 0.001f)
        assertEquals(0.5f, referenceBackdropClock(MotionSpeed.Slower, 1f), 0.001f)
        assertEquals(0.5f, referenceBackdropClock(MotionSpeed.Slow, 2f), 0.001f)
        assertEquals(Backdrop.MAX_CLOCK, referenceBackdropClock(MotionSpeed.Slow, 0.5f), 0.001f)
        assertTrue(Backdrop.BREATH_SECONDS / Backdrop.MAX_CLOCK >= 30f)
        assertTrue(Backdrop.FADE_SECONDS / Backdrop.MAX_CLOCK >= 2f)
        assertTrue(Backdrop.BREATH_AMOUNT <= 0.10f)
    }

    @Test
    fun framesComeOnlyAsOftenAsTheMotionNeeds() {
        val width = 1920f
        val height = 1080f
        for (clock in floatArrayOf(Backdrop.MAX_CLOCK, 1f, 0.5f)) {
            var drift = 0f
            var glide = 0f
            var time = 0f
            while (time < Backdrop.EFFECT_LOOP * 3f) {
                val wait = backdropFrameSeconds(BackgroundMotion.Ribbons, time, clock)
                assertTrue(wait in Backdrop.GLIDE_FRAME_SECONDS..Backdrop.SLOWEST_FRAME_SECONDS)
                val next = time + wait * clock
                drift = maxOf(drift, ribbonStep(BackgroundMotion.Ribbons, time, next, width, height))
                val bead = beadCenter(frame(BackgroundMotion.Ribbons, time, width, height))
                val after = beadCenter(frame(BackgroundMotion.Ribbons, next, width, height))
                if (bead != null && after != null) {
                    glide = maxOf(glide, hypot(after.first - bead.first, after.second - bead.second))
                }
                time = next
            }
            assertTrue("drift ${"%.2f".format(drift)}px a frame at clock $clock", drift <= 1.25f)
            assertTrue("bead ${"%.2f".format(glide)}px a frame at clock $clock", glide <= 6f)
        }
        val still = Backdrop.STATIC_FRAME_SECONDS
        assertEquals(still, backdropFrameSeconds(BackgroundMotion.Static, 30f, 1f), 0.001f)
        val creep = ribbonStep(BackgroundMotion.Static, 30f, 30f + still * Backdrop.MAX_CLOCK, width, height)
        assertTrue("static ${"%.2f".format(creep)}px a frame", creep <= 0.5f)
    }

    @Test
    fun ribbonsSwellFadeAndShiftColor() {
        val frame = BackdropFrame()
        layoutBackdrop(BackgroundMotion.Ribbons, 12f, 1920f, 1080f, moving = true, frame)
        val teal = frame.lines[0]
        assertTrue(teal.shaped)
        assertTrue(teal.endBlue > teal.blue)
        val tip = strokeAt(teal, 0.01f)
        val mid = strokeAt(teal, 0.5f)
        assertTrue("tip ${tip.radius} mid ${mid.radius}", mid.radius > tip.radius * 1.4f)
        assertTrue(tip.red + tip.green + tip.blue < mid.red + mid.green + mid.blue)
        val amber = (0 until frame.lineCount).map { frame.lines[it] }.first { it.shaped && it.endGreen < it.green }
        assertTrue(amber.endBlue > amber.blue)
        val early = strokeAt(amber, 0.22f)
        val late = strokeAt(amber, 0.78f)
        val earlyRose = early.blue.toFloat() / (early.red + early.green + early.blue).coerceAtLeast(1)
        val lateRose = late.blue.toFloat() / (late.red + late.green + late.blue).coerceAtLeast(1)
        assertTrue("rose $earlyRose -> $lateRose", lateRose > earlyRose)
    }

    @Test
    fun ribbonsStayInsideTheLitBudget() {
        val worst = measure(BackgroundMotion.Ribbons, moving = true)
        assertTrue("ribbons lit ${worst.fraction} peak ${worst.peak}", worst.fraction <= Backdrop.LIT_BUDGET && worst.peak in 13..210)
        assertTrue(worst.fraction > 0f)
    }

    @Test
    fun embersStayInsideTheLitBudget() {
        val worst = measure(BackgroundMotion.Embers, moving = true)
        assertTrue("embers lit ${worst.fraction} peak ${worst.peak}", worst.fraction <= Backdrop.LIT_BUDGET)
        assertTrue(worst.peak in 13..190)
        assertTrue(worst.fraction > 0f)
    }

    @Test
    fun offAndAFrozenFrameLightNothingExtra() {
        val frame = BackdropFrame()
        layoutBackdrop(BackgroundMotion.Off, 10f, 1920f, 1080f, moving = true, frame)
        val off = litSample(frame, 1920, 1080)
        assertEquals(0, off.litPixels)
        assertEquals(0, off.peak)
        layoutBackdrop(BackgroundMotion.Static, 10f, 1920f, 1080f, moving = true, frame)
        assertEquals(0, visibleEffects(frame))
        assertTrue(litSample(frame, 1920, 1080).litPixels > 0)
        layoutBackdrop(BackgroundMotion.Ribbons, 4f, 1920f, 1080f, moving = false, frame)
        assertEquals(0, visibleEffects(frame))
    }

    @Test
    fun staticCreepsAFewPixelsAndStaysDimmerThanRibbons() {
        val frame = BackdropFrame()
        layoutBackdrop(BackgroundMotion.Static, 0f, 1920f, 1080f, moving = true, frame)
        val start = frame.lines[1].y.copyOf(frame.lines[1].count)
        layoutBackdrop(BackgroundMotion.Static, 30f, 1920f, 1080f, moving = true, frame)
        val soon = frame.lines[1].y
        var soonShift = 0f
        for (index in start.indices) soonShift = maxOf(soonShift, abs(soon[index] - start[index]))
        assertTrue("30s shift $soonShift", soonShift < 1.5f)
        layoutBackdrop(BackgroundMotion.Static, Backdrop.STATIC_SHIFT_SECONDS, 1920f, 1080f, moving = true, frame)
        val later = frame.lines[1].y
        var shift = 0f
        for (index in start.indices) shift = maxOf(shift, abs(later[index] - start[index]))
        assertTrue("3 min shift $shift", shift in 1f..8f)
        val ribbons = measure(BackgroundMotion.Ribbons, moving = true)
        val still = measure(BackgroundMotion.Static, moving = true)
        assertTrue("static ${still.fraction} ribbons ${ribbons.fraction}", still.fraction <= 0.04f)
        assertTrue(still.fraction < ribbons.fraction)
        assertTrue(still.peak < ribbons.peak)
    }

    @Test
    fun strandsDriftOnTheirOwnClocks() {
        // A shared clock slid the whole family sideways as one picture. Each strand now
        // flows at its own pace, some the other way, so no single shift explains a frame.
        val width = 1920f
        val before = RibbonField().also { fieldRibbons(BackgroundMotion.Ribbons, 40f, width, 1080f, true, it) }
        val after = RibbonField().also { fieldRibbons(BackgroundMotion.Ribbons, 60f, width, 1080f, true, it) }
        val shifts = (0 until RibbonField.STRANDS).map { strand ->
            // A growing sweep phase slides the curve left by phase / (2π · cycles) of the width.
            var turned = after.drift[strand * 4] - before.drift[strand * 4]
            turned -= TWO_PI * round(turned / TWO_PI)
            -turned / (TWO_PI * before.drift[strand * 4 + 3]) * width
        }
        assertTrue("strand shifts $shifts", shifts.max() - shifts.min() >= 120f)
        assertTrue("strand shifts $shifts", shifts.any { it < 0f } && shifts.any { it > 0f })
    }

    @Test
    fun crossingsWanderAcrossThePanel() {
        val width = 1920f
        val frame = BackdropFrame()
        val pairs = listOf(0 to 1, 0 to 2, 1 to 2)
        val bins = Array(pairs.size) { IntArray(5) }
        var time = 0f
        while (time < 1800f) {
            layoutBackdrop(BackgroundMotion.Ribbons, time, width, 1080f, moving = true, frame)
            pairs.forEachIndexed { pair, (one, other) ->
                val left = frame.lines[one * 3]
                val right = frame.lines[other * 3]
                for (index in 1 until left.count) {
                    if ((left.y[index - 1] - right.y[index - 1]) * (left.y[index] - right.y[index]) < 0f) {
                        bins[pair][(left.x[index] / width * 5).toInt().coerceAtMost(4)]++
                    }
                }
            }
            time += 5f
        }
        for ((pair, counts) in pairs.zip(bins)) {
            val total = counts.sum()
            assertTrue("crossings $pair ${counts.toList()}", total > 0 && counts.all { it * counts.size * 2 >= total })
        }
    }

    @Test
    fun effectsStaySparse() {
        val frame = BackdropFrame()
        val times = floatArrayOf(0f, 1f, 4f, 7.5f, 8f, 10f, 13.5f, 16f, 20f, 21.5f, 30f, 48f)
        for (time in times) {
            layoutBackdrop(BackgroundMotion.Ribbons, time, 1920f, 1080f, moving = true, frame)
            val effects = visibleEffects(frame)
            assertTrue("effects at $time = $effects", effects in 0..3)
            assertTrue("beads at $time", discsOf(frame, MarkKind.Bead) <= 1)
            assertTrue("shimmers at $time", discsOf(frame, MarkKind.Shimmer) <= 1)
        }
        layoutBackdrop(BackgroundMotion.Ribbons, 4f, 1920f, 1080f, moving = true, frame)
        assertEquals(1, discsOf(frame, MarkKind.Bead))
        layoutBackdrop(BackgroundMotion.Ribbons, 10f, 1920f, 1080f, moving = true, frame)
        assertEquals(1, discsOf(frame, MarkKind.Shimmer))
        layoutBackdrop(BackgroundMotion.Ribbons, 21.9f, 1920f, 1080f, moving = true, frame)
        assertEquals(0, discsOf(frame, MarkKind.Bead))
        assertEquals(0, discsOf(frame, MarkKind.Shimmer))
        layoutBackdrop(BackgroundMotion.Ribbons, 22.05f, 1920f, 1080f, moving = true, frame)
        assertEquals(0, discsOf(frame, MarkKind.Bead))
        assertEquals(0, discsOf(frame, MarkKind.Shimmer))
    }

    @Test
    fun radialFalloffReachesBlackWithoutACliff() {
        assertEquals(1f, GlowFalloff.cover(0f), 0.001f)
        assertEquals(0f, GlowFalloff.cover(1f), 0.001f)
        assertEquals(0f, GlowFalloff.cover(1.2f), 0.001f)
        assertTrue(GlowFalloff.STOPS.size >= 8)
        assertEquals(0f, GlowFalloff.STOPS.first(), 0.001f)
        assertEquals(1f, GlowFalloff.STOPS.last(), 0.001f)
        var previous = 1.1f
        var biggest = 0f
        for (index in GlowFalloff.STOPS.indices) {
            val cover = GlowFalloff.cover(GlowFalloff.STOPS[index])
            assertTrue("rose at ${GlowFalloff.STOPS[index]}", cover <= previous + 0.0001f)
            if (index > 0) biggest = maxOf(biggest, previous - cover)
            previous = cover
        }
        assertTrue("stop jump $biggest", biggest < 0.22f)
        assertTrue(GlowFalloff.cover(0.9f) < 0.2f)
        assertTrue(GlowFalloff.cover(0.98f) < 0.04f)
        assertTrue(GlowFalloff.KNOT >= 0.12f)
        val tileEdge = GlowFalloff.cover(0.65f, GlowFalloff.TILE_TIGHTNESS, GlowFalloff.TILE_RIM)
        assertTrue("tile shoulder $tileEdge", tileEdge in 0.45f..0.9f)
    }

    @Test
    fun hotSpotRampsInsteadOfStepping() {
        val pulse = 0.37f
        var worst = 0f
        var previous = ribbonGain(0f, pulse)
        var along = 0.005f
        while (along <= 1f) {
            val next = ribbonGain(along, pulse)
            worst = maxOf(worst, abs(next - previous))
            previous = next
            along += 0.005f
        }
        assertTrue("gain step $worst", worst < 0.08f)
    }

    @Test
    fun beadGlidesAlongArcLength() {
        val frame = BackdropFrame()
        val width = 1920f
        val dt = 1f / 60f
        layoutBackdrop(BackgroundMotion.Ribbons, 3f, width, 1080f, moving = true, frame)
        var previous = beadCenter(frame)
        assertNotNull(previous)
        val spacing = width / (BackdropLine.POINTS - 1)
        var elapsed = dt
        while (elapsed <= 0.5f) {
            layoutBackdrop(BackgroundMotion.Ribbons, 3f + elapsed, width, 1080f, moving = true, frame)
            val next = beadCenter(frame)
            assertNotNull(next)
            val moved = hypot(next!!.first - previous!!.first, next.second - previous.second)
            assertTrue("snap $moved at $elapsed (sample $spacing)", moved < spacing * 0.25f)
            assertTrue("stuck $moved at $elapsed", moved > 0.2f)
            previous = next
            elapsed += dt
        }
        layoutBackdrop(BackgroundMotion.Ribbons, 3.5f, width, 1080f, moving = true, frame)
        val direct = beadCenter(frame)
        layoutBackdrop(BackgroundMotion.Ribbons, 3.5f, width, 1080f, moving = true, frame)
        val again = beadCenter(frame)
        assertEquals(direct!!.first, again!!.first, 0.01f)
        assertEquals(direct.second, again.second, 0.01f)
    }

    @Test(timeout = 90_000)
    fun adjacentFramesDoNotFlicker() {
        val dt = 1f / 60f
        val width = 960
        val height = 540
        val frame = BackdropFrame()
        val windows = floatArrayOf(3f, 7.2f, 16f, 21.85f, 65.9f)
        for (motion in listOf(BackgroundMotion.Ribbons, BackgroundMotion.Embers)) {
            for (start in windows) {
                layoutBackdrop(motion, start, width.toFloat(), height.toFloat(), moving = true, frame)
                var previous = paintedPixels(frame, width, height)
                var elapsed = dt
                while (elapsed <= 0.2f) {
                    layoutBackdrop(motion, start + elapsed, width.toFloat(), height.toFloat(), moving = true, frame)
                    val next = paintedPixels(frame, width, height)
                    var worst = 0
                    for (index in previous.indices) {
                        val delta = abs(level(next[index]) - level(previous[index]))
                        if (delta > worst) worst = delta
                    }
                    assertTrue(
                        "$motion at ${start + elapsed} changed by $worst",
                        worst <= FLICKER_LEVELS,
                    )
                    previous = next
                    elapsed += dt
                }
            }
        }
    }

    @Test
    fun eachBackgroundHasAMeasuredLitFraction() {
        val ribbons = measure(BackgroundMotion.Ribbons, moving = true)
        val embers = measure(BackgroundMotion.Embers, moving = true)
        val still = measure(BackgroundMotion.Static, moving = true)
        println(
            "LIT_PIXELS ribbons=${pct(ribbons.fraction)} peak=${ribbons.peak} " +
                "embers=${pct(embers.fraction)} peak=${embers.peak} " +
                "static=${pct(still.fraction)} peak=${still.peak} off=0",
        )
        assertTrue(still.fraction > 0f)
        assertTrue(still.fraction <= 0.04f)
    }

    private fun measure(motion: BackgroundMotion, moving: Boolean): LitSample {
        val frame = BackdropFrame()
        val times = floatArrayOf(0f, 4f, 8f, 10f, 13.5f, 16f, 24f, 36f, 48f, 72f, 90f)
        var worst = LitSample(0f, 0, 0, 1)
        var peak = 0
        for (size in listOf(1920 to 1080, 1240 to 1080)) {
            for (time in times) {
                layoutBackdrop(motion, time, size.first.toFloat(), size.second.toFloat(), moving, frame)
                val sample = litSample(frame, size.first, size.second)
                if (sample.peak > peak) peak = sample.peak
                if (sample.fraction > worst.fraction) worst = sample
            }
        }
        return worst.copy(peak = peak)
    }

    private fun frame(motion: BackgroundMotion, time: Float, width: Float, height: Float): BackdropFrame {
        val frame = BackdropFrame()
        layoutBackdrop(motion, time, width, height, moving = true, frame)
        return frame
    }

    /** The farthest any ribbon sample moves between two design times. */
    private fun ribbonStep(motion: BackgroundMotion, from: Float, to: Float, width: Float, height: Float): Float {
        val before = frame(motion, from, width, height)
        val after = frame(motion, to, width, height)
        var step = 0f
        for (index in 0 until before.lineCount) {
            val a = before.lines[index]
            val b = after.lines[index]
            if (a.kind != MarkKind.Ribbon) continue
            for (point in 0 until minOf(a.count, b.count)) step = maxOf(step, abs(b.y[point] - a.y[point]))
        }
        return step
    }

    private fun pct(fraction: Float): String = "%.2f%%".format(fraction * 100f)

    private fun discsOf(frame: BackdropFrame, kind: MarkKind): Int {
        var count = 0
        for (index in 0 until frame.discCount) {
            if (frame.discs[index].kind == kind) count++
        }
        return count
    }

    private fun beadCenter(frame: BackdropFrame): Pair<Float, Float>? {
        for (index in 0 until frame.discCount) {
            val disc = frame.discs[index]
            if (disc.kind == MarkKind.Bead) return disc.cx to disc.cy
        }
        return null
    }

    private fun level(pixel: Int): Int = maxOf(pixel ushr 16, (pixel ushr 8) and 255, pixel and 255)

    private companion object {
        const val FLICKER_LEVELS = 24
        const val TWO_PI = (PI * 2).toFloat()
    }
}
