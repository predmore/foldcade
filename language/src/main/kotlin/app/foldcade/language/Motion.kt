package app.foldcade.language

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
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

    /** One breath of a downloading tile's cloud, in each direction. */
    const val durationPulse = 720

    /** How long a finished sync's mark stays on its tile, fades included. */
    const val durationSyncMark = 2400
    const val scaleRest = 1f
    const val scaleFocus = 1.05f

    /** Incoming hero art starts just under rest, then eases up to [scaleRest]. */
    const val heroScaleFrom = 0.96f

    /** Hero art that arrives without a direction, after a jump, starts this many pixels lower, then eases up to 0. */
    const val heroSlideUpPx = 24f

    /**
     * Hero art arrives this far along the cursor's travel and eases to rest. The art
     * leaving goes the same distance on, so the top screen moves the way the cursor did.
     */
    const val heroTravelPx = 64f

    /** The game's wide art behind the logo travels this share of [heroTravelPx], so the logo reads in front of it. */
    const val sceneDepth = 0.35f

    /** Wide art is drawn this much larger than the screen, so its travel never shows an edge. */
    const val sceneOverscan = 1.05f

    /** Wide art that has just arrived starts this much larger, then settles to [sceneOverscan]. */
    const val sceneSettleFrom = 1.1f

    /** One wide-art crossfade. Longer than [durationTravel], so the game's art dissolves rather than cuts. */
    const val durationScene = 360

    /** Wide art's slow settle after it arrives. */
    const val durationShowcase = 1400

    val easingArrive: Easing = CubicBezierEasing(0.05f, ARRIVE_Y1, 0.1f, 1f)
    val easingLeave: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val easingPulse: Easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

    /**
     * Opacity of art crossing over art. [easingArrive] covers most of its way in the
     * first frames, which reads as a cut when a whole screen of art changes. This
     * starts gently, then settles the same way.
     */
    val easingDissolve: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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

    fun dissolve(base: Int, animatorScale: Float): FiniteAnimationSpec<Float> =
        spec(base, animatorScale, easingDissolve)

    /**
     * [arrive] for a move that lands while the last one is still travelling. The new
     * curve opens at the speed already reached, so held moves glide instead of
     * lurching at every repeat. [distance] is this move's length and [velocity] the
     * current speed, in the same units per second. Still, or moving the other way,
     * is a fresh [arrive].
     */
    fun glide(base: Int, animatorScale: Float, distance: Float, velocity: Float): FiniteAnimationSpec<Float> {
        if (reduced(animatorScale) || distance == 0f) return arrive(base, animatorScale)
        val millis = duration(base, animatorScale)
        // The easing's opening slope that carries the current speed over.
        val slope = velocity * millis / 1000f / distance
        if (slope < 1f || slope >= ARRIVE_OPENING_SLOPE) return arrive(base, animatorScale)
        val reach = ARRIVE_Y1 / slope
        return tween(durationMillis = millis, easing = CubicBezierEasing(reach, ARRIVE_Y1, 0.1f, 1f))
    }

    /** Fades up and back for as long as it runs. Callers hold still when [reduced]. */
    fun pulse(animatorScale: Float): InfiniteRepeatableSpec<Float> =
        infiniteRepeatable(
            tween(durationMillis = duration(durationPulse, animatorScale), easing = easingPulse),
            RepeatMode.Reverse,
        )

    /**
     * Motion Off, and Android's remove-animations scale, swap the hero with a short fade.
     * Slower takes twice [durationTravel]. Slow uses [durationTravel].
     */
    fun heroFadeOnly(speed: MotionSpeed, animatorScale: Float): Boolean =
        speed == MotionSpeed.Off || reduced(animatorScale)

    fun heroDuration(speed: MotionSpeed, animatorScale: Float): Int = heroSpan(durationTravel, speed, animatorScale)

    /** The wide art's crossfade, under the same rules as [heroDuration]. */
    fun sceneDuration(speed: MotionSpeed, animatorScale: Float): Int = heroSpan(durationScene, speed, animatorScale)

    /** The wide art's settle. Callers hold it still when [heroFadeOnly]. */
    fun showcase(speed: MotionSpeed, animatorScale: Float): FiniteAnimationSpec<Float> =
        tween(durationMillis = heroSpan(durationShowcase, speed, animatorScale), easing = easingArrive)

    fun heroScale(progress: Float, fadeOnly: Boolean): Float {
        if (fadeOnly) return scaleRest
        val t = progress.coerceIn(0f, 1f)
        return heroScaleFrom + (scaleRest - heroScaleFrom) * t
    }

    /** Where hero art arriving after [travel] starts, in pixels across. It eases to 0. */
    fun heroEntryX(travel: HeroTravel): Float = travel.x * heroTravelPx

    /** Where hero art arriving after [travel] starts, in pixels down. A jump rises a little. */
    fun heroEntryY(travel: HeroTravel): Float =
        if (travel == HeroTravel.None) heroSlideUpPx else travel.y * heroTravelPx

    /** Where hero art leaving after [travel] ends, in pixels across: on, the way the cursor went. */
    fun heroExitX(travel: HeroTravel): Float = -travel.x * heroTravelPx

    /** Where hero art leaving after [travel] ends, in pixels down. A jump fades in place. */
    fun heroExitY(travel: HeroTravel): Float = -travel.y * heroTravelPx

    /** Wide art's drawn scale. [settle] is 1 as it arrives and 0 once it has settled. */
    fun sceneScale(settle: Float): Float = sceneOverscan + (sceneSettleFrom - sceneOverscan) * settle.coerceIn(0f, 1f)

    private fun heroSpan(base: Int, speed: MotionSpeed, animatorScale: Float): Int {
        if (heroFadeOnly(speed, animatorScale)) return durationShort
        return duration(if (speed == MotionSpeed.Slower) base * 2 else base, animatorScale)
    }

    private const val ARRIVE_Y1 = 0.7f

    /** [easingArrive]'s slope at its start: a fresh move's top speed. */
    private const val ARRIVE_OPENING_SLOPE = ARRIVE_Y1 / 0.05f

    private fun spec(base: Int, animatorScale: Float, easing: Easing): FiniteAnimationSpec<Float> =
        tween(durationMillis = duration(base, animatorScale), easing = easing)
}
