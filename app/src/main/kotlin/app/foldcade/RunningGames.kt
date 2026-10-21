package app.foldcade

import android.content.Intent

/**
 * A game Foldcade started that may still be open in its player. The Home button
 * ends the play session, but the player keeps the game where it was. A on the
 * tile then brings that task back ([RESUME_FLAGS]), rather than a second start
 * that opens another copy of the player, or makes it load the game again.
 */
internal data class RunningGame(
    val tileId: String,
    val title: String,
    val platformId: String?,
    val packageName: String,
    /** The player's activity the game opened in. A resume that brings any other to the front found the game gone. */
    val activity: String?,
    val panel: Panel,
    val occupiesBoth: Boolean,
    /** Wall time of the start. */
    val startedAtMillis: Long,
    /** What to read back when the game returns again, for a library game. */
    val saves: LibraryReturn?,
)

/** One game per player package: a player holds one game, so a new start there replaces the last. */
internal class RunningGames {
    private val byPackage = LinkedHashMap<String, RunningGame>()

    /** Called with every tile id still running, after each change. */
    var onChange: (Set<String>) -> Unit = {}

    fun note(game: RunningGame) {
        byPackage[game.packageName] = game
        changed()
    }

    /**
     * The game on tile [tileId]. [tileOf] names the tile an id plays under now: a game
     * noted under its own id may since have been folded into a tile it shares with
     * another library's copy, and it is still the same game.
     */
    fun forTile(tileId: String, tileOf: (String) -> String = { it }): RunningGame? {
        val tile = tileOf(tileId)
        return byPackage.values.firstOrNull { it.tileId == tileId || tileOf(it.tileId) == tile }
    }

    fun tiles(): Set<String> = byPackage.values.map { it.tileId }.toSet()

    fun forPackage(packageName: String?): RunningGame? = packageName?.let { byPackage[it] }

    fun packages(): Set<String> = byPackage.keys.toSet()

    fun forget(packageName: String) {
        if (byPackage.remove(packageName) != null) changed()
    }

    private fun changed() = onChange(byPackage.values.map { it.tileId }.toSet())
}

/**
 * What a resume brought to the front, from the player's activities that resumed
 * since it was sent, oldest first. The game's own [activity] means the game came
 * back. Another of the player's activities means its task was gone, as a task
 * swiped from Recents is, and the front door opened the player's own menu.
 * Nothing yet is not known.
 */
internal fun resumeFound(resumed: List<String>, activity: String?): Boolean? {
    val last = resumed.lastOrNull() ?: return null
    return activity == null || last == activity
}

/** How long after a resume to look at what came to the front. */
internal const val RESUME_CHECK_MS = 1_500L

/**
 * The flags on the player's own launcher intent that bring its open task back
 * the way a launcher does. Sending the game's start again would not: a player
 * whose game activity is single-top, as Azahar's is, takes it as a new game
 * and loads it over the one in progress. With these flags Android finds the
 * player's task, brings it to the front and drops the start, because the
 * launcher activity is not the task's root.
 */
internal const val RESUME_FLAGS: Int = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

/**
 * The flags for a start in a player that still holds another game, or the same
 * one started over. Clearing its task closes that game, so the player starts
 * this one alone instead of in a second task beside it.
 */
internal fun replaceFlags(flags: Int): Int =
    (flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK.inv()) or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
