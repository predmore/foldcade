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
open class MotionStyle(private val style: ThemeStyle = ThemeStyle()) {
    val durationLetterHold = 360L
    val durationLetterFade = 120

    val durationShort = style.motion.durationShort
    val durationFocus = style.motion.durationFocus
    val durationTravel = style.motion.durationTravel

    /** Island morph. Same curves as travel, long enough to read the shared-element grow. */
    val durationIsland = style.motion.durationIsland

    /** One breath of a downloading tile's cloud, in each direction. */
    val durationPulse = style.motion.durationPulse

    /** How long a finished sync's mark stays on its tile, fades included. */
    val durationSyncMark = style.motion.durationSyncMark
    val scaleRest = style.motion.scaleRest
    val scaleFocus = style.motion.scaleFocus

    /** Incoming hero art starts just under rest, then eases up to [scaleRest]. */
    val heroScaleFrom = style.motion.heroScaleFrom

    /** Hero art that arrives without a direction, after a jump, starts this many pixels lower, then eases up to 0. */
    val heroSlideUpPx = style.motion.heroSlideUpPx

    /**
     * Hero art arrives this far along the cursor's travel and eases to rest. The art
     * leaving goes the same distance on, so the top screen moves the way the cursor did.
     */
    val heroTravelPx = style.motion.heroTravelPx

    /** The game's wide art behind the logo travels this share of [heroTravelPx], so the logo reads in front of it. */
    val sceneDepth = style.motion.sceneDepth

    /** Wide art is drawn this much larger than the screen, so its travel never shows an edge. */
    val sceneOverscan = style.motion.sceneOverscan

    /** Wide art that has just arrived starts this much larger, then settles to [sceneOverscan]. */
    val sceneSettleFrom = style.motion.sceneSettleFrom

    /** One wide-art crossfade. Longer than [durationTravel], so the game's art dissolves rather than cuts. */
    val durationScene = style.motion.durationScene

    /** Wide art's slow settle after it arrives. */
    val durationShowcase = style.motion.durationShowcase

    /** A menu's highlight landing on a row, and a switch flipping. Long enough to read the overshoot. */
    val durationSelect = style.motion.durationSelect

    private val arriveCurve = style.motion.arrive
    val easingArrive: Easing = CubicBezierEasing(arriveCurve[0], arriveCurve[1], arriveCurve[2], arriveCurve[3])
    val easingLeave: Easing = style.motion.leave.let { CubicBezierEasing(it[0], it[1], it[2], it[3]) }
    val easingPulse: Easing = style.motion.pulse.let { CubicBezierEasing(it[0], it[1], it[2], it[3]) }

    /**
     * Opacity of art crossing over art. [easingArrive] covers most of its way in the
     * first frames, which reads as a cut when a whole screen of art changes. This
     * starts gently, then settles the same way.
     */
    val easingDissolve: Easing = style.motion.dissolve.let { CubicBezierEasing(it[0], it[1], it[2], it[3]) }

    /** Runs a touch past its target, then settles back. Menus use it so a choice lands with a little bounce. */
    val easingPlayful: Easing = style.motion.playful.let { CubicBezierEasing(it[0], it[1], it[2], it[3]) }

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

    /** [easingPlayful], or a plain [arrive] when Android removes animations, so nothing bounces then. */
    fun playful(base: Int, animatorScale: Float): FiniteAnimationSpec<Float> =
        if (reduced(animatorScale)) arrive(base, animatorScale) else spec(base, animatorScale, easingPlayful)

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
        if (slope < 1f || slope >= arriveOpeningSlope) return arrive(base, animatorScale)
        val reach = arriveCurve[1] / slope
        return tween(durationMillis = millis, easing = CubicBezierEasing(reach, arriveCurve[1], arriveCurve[2], arriveCurve[3]))
    }

    /** A held tile's size: lifted off the grid, over its neighbours. */
    val scaleLift = 1.1f

    /** A held game over a group it can go into shrinks to this, as if about to drop in. */
    val scaleOver = 0.74f

    /** A group a held game is over opens up to this, and its glow comes on. */
    val scaleOpen = 1.14f

    /** One slow rise and fall of a held tile. */
    val durationBob = 1800

    /** How far a held tile rises and falls, in panel pixels. */
    val bobPx = 7f

    /** How far below a held tile the light it casts falls, in panel pixels. */
    val liftGlowPx = 30f

    /** How bright that light is at its centre, at full lift. */
    val liftGlowPeak = 0.7f

    /** How much a tile squashes as it lands, or stretches as it lifts, at most. */
    val squash = 0.08f

    /**
     * A held tile's bob, for as long as it is held, rising and falling once per
     * [durationBob]. Slower takes twice as long. Null when Motion is Off or Android
     * removes animations, so the tile holds still.
     */
    fun bob(speed: MotionSpeed, animatorScale: Float): InfiniteRepeatableSpec<Float>? {
        if (speed == MotionSpeed.Off || reduced(animatorScale)) return null
        val base = if (speed == MotionSpeed.Slower) durationBob * 2 else durationBob
        return infiniteRepeatable(
            tween(durationMillis = duration(base, animatorScale), easing = easingPulse),
            RepeatMode.Reverse,
        )
    }

    /**
     * A squash or stretch springing back to none, with the playful overshoot so it wobbles
     * once. Null when Motion is Off or Android removes animations: the tile just lands.
     */
    fun squashBack(speed: MotionSpeed, animatorScale: Float): FiniteAnimationSpec<Float>? {
        if (speed == MotionSpeed.Off || reduced(animatorScale)) return null
        return spec(if (speed == MotionSpeed.Slower) durationSelect * 2 else durationSelect, animatorScale, easingPlayful)
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

    private val arriveOpeningSlope = if (arriveCurve[0] > 0f) arriveCurve[1] / arriveCurve[0] else Float.POSITIVE_INFINITY

    private fun spec(base: Int, animatorScale: Float, easing: Easing): FiniteAnimationSpec<Float> =
        tween(durationMillis = duration(base, animatorScale), easing = easing)
}

/** Default motion for callers that do not render a theme. */
object Motion : MotionStyle()
