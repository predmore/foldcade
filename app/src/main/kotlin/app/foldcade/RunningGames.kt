package app.foldcade

import android.content.Intent
import app.foldcade.api.plugin.LaunchFlag

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
    /** Apps opened beside this run, in launch order. These associations live only in memory. */
    val companions: List<GameCompanion> = emptyList(),
    /** Explicitly tested across displays, independently of ordinary same-start resume. */
    val movesBetweenScreens: Boolean = false,
    /** The started player's folder, even if the platform's chosen player changes later. */
    val saveFolderKey: String? = null,
    val player: app.foldcade.api.plugin.Player? = null,
)

internal data class GameCompanion(val packageName: String, val title: String, val panel: Panel)

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

    /** Close keeps a replacement guard, but its player may later be opened as an app beside another game. */
    private val closedPlayers = LinkedHashSet<String>()

    /** Called with every tile id still running, after each change. */
    var onChange: (Set<String>) -> Unit = {}

    /** Called with every player that may still hold a game, after each change, to keep across restarts. */
    var onPlayersChange: (Set<String>) -> Unit = {}

    /** Players a previous process noted, read back once at start. */
    fun restore(players: Set<String>) {
        holdingOnly += players - byPackage.keys
    }

    fun note(game: RunningGame) {
        dropCompanion(game.packageName)
        closedPlayers -= game.packageName
        holdingOnly -= game.packageName
        byPackage[game.packageName] = game
        changed()
    }

    /** Keep the replacement guard without a Paused tile. [closed] lets its app be a companion after Close. */
    fun noteHolding(packageName: String, closed: Boolean = false) {
        dropCompanion(packageName)
        byPackage.remove(packageName)
        holdingOnly += packageName
        if (closed) closedPlayers += packageName else closedPlayers -= packageName
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
        closedPlayers -= packageName
        val known = byPackage.remove(packageName) != null
        if (holdingOnly.remove(packageName) || known) changed()
    }

    /** An app that returned to Home by itself no longer closes with its former game. */
    fun forgetCompanion(packageName: String) {
        if (byPackage.values.any { game -> game.companions.any { it.packageName == packageName } }) {
            dropCompanion(packageName)
            changed()
        }
    }

    private fun changed() {
        onChange(byPackage.values.map { it.tileId }.toSet())
        onPlayersChange(byPackage.keys + holdingOnly)
    }

    /** Opening an app alone releases any earlier association; another library game is never a companion. */
    fun noteApp(app: GameCompanion, besidePlayer: String?, ownPackages: Set<String>) {
        dropCompanion(app.packageName)
        val game = besidePlayer?.let(byPackage::get)
        if (game != null && !game.occupiesBoth && game.panel != app.panel &&
            ownPackages.none { app.packageName == it || app.packageName.startsWith("$it.") } &&
            (!mayHold(app.packageName) || app.packageName in closedPlayers)) {
            byPackage[game.packageName] = game.copy(companions = game.companions + app)
        }
        changed()
    }

    private fun dropCompanion(packageName: String) {
        byPackage.replaceAll { _, game -> game.copy(companions = game.companions.filterNot { it.packageName == packageName }) }
    }
}

/**
 * True when the player's front door ([frontDoor], its launcher activity) can bring
 * back a game started in [started]. When they are the same activity, the launcher
 * intent is that activity again with another intent, which Android stacks as a
 * second copy over the game rather than bringing the game's task back. A player
 * with an explicitly reusable game start does not need its launcher to resume.
 */
internal fun resumable(frontDoor: String?, started: String?, flags: Set<LaunchFlag> = emptySet()): Boolean =
    LaunchFlag.SameStartResumes in flags || LaunchFlag.MovesBetweenScreens in flags ||
        (frontDoor != null && frontDoor != started)

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
internal fun resumeStartFlags(flags: Int, launchFlags: Set<LaunchFlag> = emptySet()): Int =
    (flags and (Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_MULTIPLE_TASK).inv()) or
        Intent.FLAG_ACTIVITY_NEW_TASK or (if (LaunchFlag.MovesBetweenScreens in launchFlags) Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED else 0)

/**
 * The flags for a start in a player that still holds another game, or the same
 * one started over. Clearing its task closes that game, so the player starts
 * this one alone instead of in a second task beside it.
 */
internal fun replaceFlags(flags: Int): Int =
    (flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK.inv()) or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

/** Clear the player's task behind Home, without opening a second task beside it. */
internal const val CLOSE_FLAGS: Int = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
    Intent.FLAG_ACTIVITY_NO_ANIMATION

/**
 * RetroArch must quit through its own run loop. Clearing its NativeActivity can
 * deadlock android_app_free: it joins that loop while holding the app mutex.
 * This also applies when RetroArch was opened as a companion app, not a player.
 * https://github.com/libretro/RetroArch/blob/69a4f0ea1e8aaf442ae4858f2e7f2b31a1776576/frontend/drivers/platform_unix.c#L265
 */
internal fun canClearPlayerTask(packageName: String): Boolean = packageName !in app.foldcade.plugins.retroarch.PACKAGES

/** A shutdown may finish writes after its activities leave the task history. */
internal const val CLOSE_SETTLE_MS = 1_500L

/** Moving never displaces an app on the other panel. */
internal fun RunningGame.moveTarget(session: Session, saveAwaitsLoad: Boolean): Panel? =
    panel.flip().takeIf { movesBetweenScreens && !saveAwaitsLoad && !occupiesBoth && session.externalOn(it) == null }

/** The Thor panels change width and aspect ratio; unhandled changes would recreate the game activity. */
internal fun handlesPanelSizeChange(configChanges: Int): Boolean {
    val needed = android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE or android.content.pm.ActivityInfo.CONFIG_SCREEN_LAYOUT
    return configChanges and needed == needed
}
