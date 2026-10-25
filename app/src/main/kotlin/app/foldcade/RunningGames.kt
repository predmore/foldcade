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
    /** The player's activity the game's start named. Some players hand the game on to another one. */
    val activity: String?,
    val panel: Panel,
    val occupiesBoth: Boolean,
    /** Wall time of the start. */
    val startedAtMillis: Long,
    /** What to read back when the game returns again, for a library game. */
    val saves: LibraryReturn?,
    /**
     * The game's own start, for a player that takes it again as a resume
     * ([app.foldcade.api.plugin.LaunchFlag.SameStartResumes]), with [resumeStartFlags].
     * Null resumes through the player's launcher.
     */
    val resumeStart: Intent? = null,
)

/** One game per player package: a player holds one game, so a new start there replaces the last. */
internal class RunningGames {
    private val byPackage = LinkedHashMap<String, RunningGame>()

    /**
     * Players that may hold a game no tile can bring back: one an earlier Foldcade
     * process started, or one in a player whose front door is where its games run
     * ([noteHolding]). No tile reads Paused for them, but a start there still
     * replaces the task instead of opening a second one beside it.
     */
    private val holdingOnly = LinkedHashSet<String>()

    /** Called with every tile id still running, after each change. */
    var onChange: (Set<String>) -> Unit = {}

    /** Called with every player that may still hold a game, after each change, to keep across restarts. */
    var onPlayersChange: (Set<String>) -> Unit = {}

    /** Players a previous process noted, read back once at start. */
    fun restore(players: Set<String>) {
        holdingOnly += players - byPackage.keys
    }

    fun note(game: RunningGame) {
        holdingOnly -= game.packageName
        byPackage[game.packageName] = game
        changed()
    }

    /** [packageName] holds a game now that its front door would not bring back, see [resumable]. */
    fun noteHolding(packageName: String) {
        byPackage.remove(packageName)
        holdingOnly += packageName
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

    /** True when [packageName] may still hold a game Foldcade started, in this process or an earlier one. */
    fun mayHold(packageName: String?): Boolean = packageName != null && (packageName in byPackage || packageName in holdingOnly)

    fun forget(packageName: String) {
        val known = byPackage.remove(packageName) != null
        if (holdingOnly.remove(packageName) || known) changed()
    }

    private fun changed() {
        onChange(byPackage.values.map { it.tileId }.toSet())
        onPlayersChange(byPackage.keys + holdingOnly)
    }
}

/**
 * True when the player's front door ([frontDoor], its launcher activity) can bring
 * back a game started in [started]. When they are the same activity, the launcher
 * intent is that activity again with another intent, which Android stacks as a
 * second copy over the game rather than bringing the game's task back.
 */
internal fun resumable(frontDoor: String?, started: String?): Boolean = frontDoor != null && frontDoor != started

/**
 * What a resume brought to the front, from the player's activities that resumed
 * since it was sent, oldest first. A task still there comes back without the
 * player's [frontDoor] (its launcher activity), so the front door coming forward
 * says the game was gone, as after a swipe from Recents or a crash: the resume
 * opened the player itself. That holds when the front door hands on to the
 * player's own menu at once, as aPS3e's QuickStartActivity opens its
 * MainActivity, unless the activity the game started in ([started]) came last:
 * the player opened the game again itself. Any other activity is the game,
 * whichever one the player runs it in. A front door that is also where the
 * game's start went can't tell the two apart, so it never says gone. Nothing
 * seen yet is not known.
 */
internal fun resumeFound(resumed: List<String>, frontDoor: String?, started: String?): Boolean? {
    val last = resumed.lastOrNull() ?: return null
    return frontDoor == null || frontDoor == started || frontDoor !in resumed || last == started
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
 * The flags for a game's own start sent again as a resume
 * ([app.foldcade.api.plugin.LaunchFlag.SameStartResumes]): the start's own, without
 * the ones that would clear the game's task or open another beside it.
 */
internal fun resumeStartFlags(flags: Int): Int =
    (flags and (Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_MULTIPLE_TASK).inv()) or
        Intent.FLAG_ACTIVITY_NEW_TASK

/**
 * The flags for a start in a player that still holds another game, or the same
 * one started over. Clearing its task closes that game, so the player starts
 * this one alone instead of in a second task beside it.
 */
internal fun replaceFlags(flags: Int): Int =
    (flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK.inv()) or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
