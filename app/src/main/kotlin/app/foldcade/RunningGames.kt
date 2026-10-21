package app.foldcade

import android.content.Intent

/**
 * A game Foldcade started that may still be open in its player. Home coming back
 * ends the play session, but the player keeps the game where it was. A on the
 * tile then sends [intent] again so Android brings that task back, rather than a
 * second start that opens another copy of the player beside the first.
 */
internal class RunningGame(
    val tileId: String,
    val title: String,
    val platformId: String?,
    val packageName: String,
    /** The start as it was sent, with its flags. */
    val intent: Intent,
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
 * The flags that bring a running game's task back. Android reuses a task whose
 * root intent matches this one, so the player is handed nothing new: without
 * [Intent.FLAG_ACTIVITY_MULTIPLE_TASK] no second task opens, and without the
 * clear flags the game is not closed and started over.
 */
internal fun resumeFlags(flags: Int): Int =
    (flags and (Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).inv()) or
        Intent.FLAG_ACTIVITY_NEW_TASK

/**
 * The flags for a start in a player that still holds another game, or the same
 * one started over. Clearing its task closes that game, so the player starts
 * this one alone instead of in a second task beside it.
 */
internal fun replaceFlags(flags: Int): Int =
    (flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK.inv()) or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
