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
    const val durationFocus = 150
    const val durationTravel = 200
    const val scaleRest = 1f
    const val scaleFocus = 1.12f

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

    private fun spec(base: Int, animatorScale: Float, easing: Easing): FiniteAnimationSpec<Float> =
        tween(durationMillis = duration(base, animatorScale), easing = easing)
}
