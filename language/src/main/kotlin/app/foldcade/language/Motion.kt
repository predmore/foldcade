package app.foldcade.language

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * The only motion specs Foldcade uses.
 * Call sites ask this object for a spec. They do not write a second cubic.
 * Durations are never 0. The toolkit must not apply [Settings.Global.ANIMATOR_DURATION_SCALE] again.
 */
object Motion {
    const val durationShort = 100
    const val durationFocus = 180
    const val durationTravel = 200

    /** Island morph. Same curves as travel, long enough to read the shared-element grow. */
    const val durationIsland = 320
    const val scaleRest = 1f
    const val scaleFocus = 1.05f

    /** Incoming hero art starts just under rest, then eases up to [scaleRest]. */
    const val heroScaleFrom = 0.96f

    /** Incoming hero art starts this many pixels lower, then eases up to 0. */
    const val heroSlideUpPx = 24f

    val easingArrive: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val easingLeave: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun animatorScale(resolver: ContentResolver): Float =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    fun reduced(animatorScale: Float): Boolean = animatorScale <= 0f

    fun duration(base: Int, animatorScale: Float): Int {
        if (animatorScale <= 0f) return durationShort
        return (base * animatorScale).toInt().coerceAtLeast(1)
    }

    fun arrive(base: Int, animatorScale: Float): FiniteAnimationSpec<Float> =
        spec(base, animatorScale, easingArrive)

    fun leave(base: Int, animatorScale: Float): FiniteAnimationSpec<Float> =
        spec(base, animatorScale, easingLeave)

    /**
     * Motion Off, and Android's remove-animations scale, swap the hero with a short fade.
     * Slower takes twice [durationTravel]. Slow uses [durationTravel].
     */
    fun heroFadeOnly(speed: MotionSpeed, animatorScale: Float): Boolean =
        speed == MotionSpeed.Off || reduced(animatorScale)

    fun heroDuration(speed: MotionSpeed, animatorScale: Float): Int {
        if (heroFadeOnly(speed, animatorScale)) return durationShort
        val base = if (speed == MotionSpeed.Slower) durationTravel * 2 else durationTravel
        return duration(base, animatorScale)
    }

    fun heroScale(progress: Float, fadeOnly: Boolean): Float {
        if (fadeOnly) return scaleRest
        val t = progress.coerceIn(0f, 1f)
        return heroScaleFrom + (scaleRest - heroScaleFrom) * t
    }

    fun heroSlidePx(progress: Float, fadeOnly: Boolean): Float {
        if (fadeOnly) return 0f
        return heroSlideUpPx * (1f - progress.coerceIn(0f, 1f))
    }

    private fun spec(base: Int, animatorScale: Float, easing: Easing): FiniteAnimationSpec<Float> =
        tween(durationMillis = duration(base, animatorScale), easing = easing)
}
