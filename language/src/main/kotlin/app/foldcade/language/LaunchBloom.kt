package app.foldcade.language

/**
 * A game's art growing to fill both screens as it opens. The game starts in the same
 * moment, so this never holds the launch back: it plays while the player loads, and the
 * player's window opens over art that already fills its screen.
 *
 * Times are milliseconds since the start went in. A screen the game takes keeps its art
 * until the game covers it, or [WAIT_MILLIS] at most. The other screen keeps it [holdMillis]
 * once grown, then gives way to what is under it, the command center beside the game.
 */
class LaunchBloom internal constructor(
    /** The art growing from where it sat to the whole screen. */
    val growMillis: Int,
    /** How long a screen the game does not take keeps the grown art. */
    val holdMillis: Int,
    /** The art giving way to what is under it. */
    val leaveMillis: Int,
    /** The slow push into the art while it waits, from the start. */
    val pushMillis: Int,
    /** How much larger the art is drawn by the end of [pushMillis]. */
    val push: Float,
    private val motion: MotionStyle,
) {
    /**
     * How far the art has grown, 0 where it sat to 1 filling the screen. It leaves the tile
     * gently, so a frame the start holds up costs little distance, then sweeps out and settles.
     */
    fun grow(elapsed: Long): Float = motion.easingDissolve.transform(share(elapsed, growMillis))

    /** Art that had no place on this screen fades in as it grows, so it never cuts in. */
    fun arrive(elapsed: Long): Float = motion.easingDissolve.transform(share(elapsed, growMillis))

    /** The scale over the grown art, easing from 1 to [push] and resting there. */
    fun pushScale(elapsed: Long): Float {
        val t = share(elapsed, pushMillis)
        // Quick at first, then barely moving, so the art is never quite still while the game loads.
        return 1f + (push - 1f) * t * (2f - t)
    }

    /** When the art starts to give way on a screen. A screen the game takes waits for it. */
    fun leavesAt(gameHere: Boolean): Long =
        if (gameHere) WAIT_MILLIS else (growMillis + holdMillis).toLong()

    /** How much of the art is left, 1 until [leaveFrom] and 0 once it has given way. */
    fun presence(elapsed: Long, leaveFrom: Long): Float =
        1f - motion.easingDissolve.transform(share(elapsed - leaveFrom, leaveMillis))

    /** The art has given way. */
    fun done(elapsed: Long, leaveFrom: Long): Boolean = elapsed >= leaveFrom + leaveMillis

    private fun share(elapsed: Long, span: Int): Float =
        if (span <= 0) 1f else (elapsed.toFloat() / span).coerceIn(0f, 1f)

    companion object {
        /** The longest a screen holds its art for a game that has not covered it yet. */
        const val WAIT_MILLIS = 6000L
    }
}
