package app.foldcade

/** A Home screen that might still show a save conflict. */
internal data class SaveConflictHome(
    val displayId: Int,
    val destroyed: Boolean,
    val resumed: Boolean,
)

/**
 * Which screen refreshes a save conflict after a launch's download ends.
 * A starter that is still alive refreshes itself, so this returns null.
 * Otherwise the resumed Home, or any Home that is still up.
 */
internal fun saveConflictSurvivor(starterAlive: Boolean, homes: List<SaveConflictHome>): Int? {
    if (starterAlive) return null
    val live = homes.filter { !it.destroyed }
    return (live.firstOrNull { it.resumed } ?: live.firstOrNull())?.displayId
}
