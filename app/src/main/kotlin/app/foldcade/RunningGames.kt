package app.foldcade

import android.content.Intent

/**
 * A game Foldcade started that may still be open in its player. The Home button
 * ends the play session, but the player keeps the game where it was. A on the
 * tile then brings that task back ([RESUME_FLAGS]), rather than a second start
 * that opens another copy of the player, or makes it load the game again.
 */
internal class RunningGame(
    val tileId: String,
    val title: String,
    val platformId: String?,
    val packageName: String,
    val panel: Panel,
    val occupiesBoth: Boolean,
    /** Wall time of the start. A process exit after it means the game is gone. */
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

    fun forTile(tileId: String): RunningGame? = byPackage.values.firstOrNull { it.tileId == tileId }

    fun forPackage(packageName: String?): RunningGame? = packageName?.let { byPackage[it] }

    fun packages(): Set<String> = byPackage.keys.toSet()

    fun forget(packageName: String) {
        if (byPackage.remove(packageName) != null) changed()
    }

    /** Drops each game whose player exited since it started. [exits] is null when it can't be read. */
    fun dropExited(exits: (String) -> List<Long>?) {
        val gone = byPackage.values.filter { game ->
            exits(game.packageName)?.let { playerDiedAfterStart(game.startedAtMillis, it) } == true
        }
        if (gone.isEmpty()) return
        gone.forEach { byPackage.remove(it.packageName) }
        changed()
    }

    private fun changed() = onChange(byPackage.values.map { it.tileId }.toSet())
}

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
