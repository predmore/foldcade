package app.foldcade

import app.foldcade.host.play.PlaySignal
import kotlin.coroutines.cancellation.CancellationException

/**
 * The external-player session on the host launch path.
 *
 * [open] records the same start a stand-in records. A start that throws ends
 * the session. A start that returns stays open until the display it launched
 * on is home again, or until that player's process has exited and Home has
 * not come back. While that display is off, as it is with the lid closed,
 * the session is asleep and its time does not count.
 */
class ExternalPlay {
    var sessionId: String? = null
        private set

    var displayId: Int? = null
        private set

    /** Package of the player that was started. Null for a stand-in in this process. */
    var packageName: String? = null
        private set

    /** Wall time of the successful start, or of the last replace-running restart. */
    var startedAtMillis: Long? = null
        private set

    fun open(
        plays: PlaySessions,
        gameId: String,
        name: String,
        displayId: Int,
        packageName: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        launch: () -> Unit,
    ) {
        sessionId?.let { plays.signal(it, PlaySignal.End) }
        forget()
        val id = plays.start(gameId, name)
        try {
            launch()
        } catch (cancelled: CancellationException) {
            plays.signal(id, PlaySignal.End)
            throw cancelled
        } catch (failure: Exception) {
            plays.signal(id, PlaySignal.End)
            throw failure
        }
        sessionId = id
        this.displayId = displayId
        this.packageName = packageName?.takeUnless { it.isBlank() }
        startedAtMillis = nowMillis
    }

    /** The launch display turned off or back on. A session already in that state is unchanged. */
    fun displayOn(plays: PlaySessions, on: Boolean) {
        val id = sessionId ?: return
        plays.signal(id, if (on) PlaySignal.Resume else PlaySignal.Sleep)
    }

    /**
     * A replace-running player quit and was started again. An exit from
     * before this restart is the switch, not a crash.
     */
    fun notePlayerRestarted(nowMillis: Long = System.currentTimeMillis()) {
        if (sessionId == null) return
        startedAtMillis = nowMillis
    }

    /** Ends the open session when [displayId] is the one the player launched on. */
    fun endIfDisplayHome(plays: PlaySessions, displayId: Int) {
        if (this.displayId != displayId) return
        val id = takeSession() ?: return
        plays.signal(id, PlaySignal.End)
    }

    /** The player process exited and Home did not resume on the launch display. */
    fun endBecausePlayerGone(plays: PlaySessions) {
        val id = takeSession() ?: return
        plays.signal(id, PlaySignal.End)
    }

    private fun takeSession(): String? {
        val id = sessionId ?: return null
        forget()
        return id
    }

    private fun forget() {
        sessionId = null
        displayId = null
        packageName = null
        startedAtMillis = null
    }
}

/** How often to look for a player process that exited while Home stayed behind. */
internal const val PLAYER_EXIT_POLL_MS = 2_000L

/** Foldcade's own process is the stand-in. Its session ends with that activity, not a watcher. */
internal fun watchPlayerPackage(playerPackage: String?, ownPackage: String): Boolean =
    !playerPackage.isNullOrBlank() && playerPackage != ownPackage

/** True when a recorded process exit is this session, not one from before it started. */
internal fun playerDiedAfterStart(startedAtMillis: Long, exitTimestamps: List<Long>): Boolean =
    exitTimestamps.any { it >= startedAtMillis }

/**
 * True while a replace-running quit may still bring Home back to start the
 * player again. An exit in that window is the switch, not a crash.
 */
internal fun restartStillExpected(nowUptime: Long, untilUptime: Long?): Boolean =
    untilUptime != null && nowUptime <= untilUptime
