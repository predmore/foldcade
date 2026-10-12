package app.foldcade.language

import kotlin.math.exp

/** A stable selection id, including arbitrary scenes supplied by themes. */
data class BackgroundMotion(val wire: String) {
    companion object {
        val Off = BackgroundMotion("off")
        // v1 settings aliases, retained for migration and old callers.
        val Ribbons = BackgroundMotion("ribbons")
        val Embers = BackgroundMotion("embers")
        val Static = BackgroundMotion("static")
        fun fromWire(raw: String?): BackgroundMotion? = raw?.takeIf {
            it in setOf("off", "ribbons", "embers", "static") || it.matches(Regex("[A-Za-z0-9._-]+:[A-Za-z0-9._-]+"))
        }?.let(::BackgroundMotion)
    }
    fun next(): BackgroundMotion = when (this) {
        Ribbons -> Embers
        Embers -> Static
        Static -> Off
        else -> Ribbons
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

fun backdropClock(speed: MotionSpeed, animatorScale: Float): Float {
    if (speed == MotionSpeed.Off || animatorScale <= 0f) return 0f
    return (if (speed == MotionSpeed.Slower) 0.5f else 1f) / animatorScale
}

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
        return bell * (1f - (u * u * (3f - 2f * u)))
    }
}
