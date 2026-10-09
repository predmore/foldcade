package app.foldcade.language

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropTest {
    @Test
    fun clockFollowsSpeedAndAnimatorScale() {
        assertEquals(0f, backdropClock(MotionSpeed.Off, 1f), 0.001f)
        assertEquals(0f, backdropClock(MotionSpeed.Slow, 0f), 0.001f)
        assertEquals(0f, backdropClock(MotionSpeed.Slow, -1f), 0.001f)
        assertEquals(1f, backdropClock(MotionSpeed.Slow, 1f), 0.001f)
        assertEquals(0.5f, backdropClock(MotionSpeed.Slower, 1f), 0.001f)
        assertEquals(0.5f, backdropClock(MotionSpeed.Slow, 2f), 0.001f)
        assertEquals(Backdrop.MAX_CLOCK, backdropClock(MotionSpeed.Slow, 0.5f), 0.001f)
        assertTrue(Backdrop.DRIFT_SECONDS / Backdrop.MAX_CLOCK >= 60f)
        assertTrue(Backdrop.BREATH_SECONDS / Backdrop.MAX_CLOCK >= 30f)
        assertTrue(Backdrop.FADE_SECONDS / Backdrop.MAX_CLOCK >= 2f)
        assertTrue(Backdrop.BREATH_AMOUNT <= 0.10f)
        assertTrue(Backdrop.DRIFT_SECONDS in 60f..120f)
    }

    @Test
    fun ribbonsStayInsideTheLitBudget() {
        val worst = measure(BackgroundMotion.Ribbons, moving = true)
        assertTrue("ribbons lit ${worst.fraction} peak ${worst.peak}", worst.fraction <= Backdrop.LIT_BUDGET)
        assertTrue(worst.peak in 13..190)
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
        assertEquals(0, litSample(frame, 320, 180).litPixels)
        layoutBackdrop(BackgroundMotion.Ribbons, 4f, 1920f, 1080f, moving = false, frame)
        assertEquals(0, visibleEffects(frame))
    }

    @Test
    fun driftRepeatsAndEffectsStaySparse() {
        val frame = BackdropFrame()
        layoutBackdrop(BackgroundMotion.Ribbons, 3f, 1240f, 1080f, moving = true, frame)
        val early = frame.lines[1]
        val xs = early.x.copyOf(early.count)
        val ys = early.y.copyOf(early.count)
        layoutBackdrop(BackgroundMotion.Ribbons, 3f + Backdrop.DRIFT_SECONDS, 1240f, 1080f, moving = true, frame)
        val again = frame.lines[1]
        for (index in xs.indices) {
            assertEquals(xs[index], again.x[index], 0.01f)
            assertEquals(ys[index], again.y[index], 0.01f)
        }
        val times = floatArrayOf(0f, 1f, 4f, 7.5f, 8f, 10f, 13.5f, 16f, 20f, 21.5f, 30f, 48f)
        for (time in times) {
            layoutBackdrop(BackgroundMotion.Ribbons, time, 1920f, 1080f, moving = true, frame)
            val effects = visibleEffects(frame)
            assertTrue("effects at $time = $effects", effects in 0..2)
        }
        layoutBackdrop(BackgroundMotion.Ribbons, 4f, 1920f, 1080f, moving = true, frame)
        assertEquals(1, visibleEffects(frame))
        layoutBackdrop(BackgroundMotion.Ribbons, 10f, 1920f, 1080f, moving = true, frame)
        assertTrue(visibleEffects(frame) >= 1)
        layoutBackdrop(BackgroundMotion.Ribbons, 16f, 1920f, 1080f, moving = true, frame)
        assertTrue(visibleEffects(frame) >= 1)
    }

    @Test
    fun eachBackgroundHasAMeasuredLitFraction() {
        val ribbons = measure(BackgroundMotion.Ribbons, moving = true)
        val embers = measure(BackgroundMotion.Embers, moving = true)
        val top = litPng("themes/afterglow/wallpaper-top.png")
        val bottom = litPng("themes/afterglow/wallpaper-bottom.png")
        println(
            "LIT_PIXELS ribbons=${pct(ribbons.fraction)} peak=${ribbons.peak} " +
                "embers=${pct(embers.fraction)} peak=${embers.peak} " +
                "staticTop=${pct(top.fraction)} staticBottom=${pct(bottom.fraction)} off=0",
        )
        assertEquals(1920, top.width)
        assertEquals(1080, top.height)
        assertEquals(1240, bottom.width)
        assertEquals(1080, bottom.height)
        assertTrue(top.fraction > 0f)
        assertTrue(bottom.fraction > 0f)
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

    private fun litPng(relative: String): PngLit {
        val image = ImageIO.read(repoFile(relative))
        var lit = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = image.getRGB(x, y)
                val red = (pixel shr 16) and 255
                val green = (pixel shr 8) and 255
                val blue = pixel and 255
                val channel = maxOf(red, green, blue)
                if (channel > Backdrop.LIT_CHANNEL) lit++
            }
        }
        val total = image.width * image.height
        return PngLit(image.width, image.height, lit.toFloat() / total.toFloat())
    }

    private fun pct(fraction: Float): String = "%.2f%%".format(fraction * 100f)

    private fun repoFile(relative: String): File {
        val cwd = File(".").canonicalFile
        val candidates = listOf(
            File(cwd, relative),
            File(cwd, "../$relative"),
            File(cwd.parentFile, relative),
        )
        return candidates.firstOrNull { it.isFile } ?: error("missing $relative from $cwd")
    }

    private data class PngLit(val width: Int, val height: Int, val fraction: Float)
}
