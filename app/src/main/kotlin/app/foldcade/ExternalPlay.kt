package app.foldcade

import app.foldcade.host.play.PlaySignal
import kotlin.coroutines.cancellation.CancellationException

/**
 * The external-player session on the host launch path.
 *
 * [open] records the same start a stand-in records. A start that throws ends
 * the session. A start that returns stays open until the display it launched
 * on is home again. While that display is off, as it is with the lid closed,
 * the session is asleep and its time does not count.
 */
class ExternalPlay {
    var sessionId: String? = null
        private set

    var displayId: Int? = null
        private set

    fun open(plays: PlaySessions, gameId: String, name: String, displayId: Int, launch: () -> Unit) {
        sessionId?.let { plays.signal(it, PlaySignal.End) }
        sessionId = null
        this.displayId = null
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
    }

    /** The launch display turned off or back on. A session already in that state is unchanged. */
    fun displayOn(plays: PlaySessions, on: Boolean) {
        val id = sessionId ?: return
        plays.signal(id, if (on) PlaySignal.Resume else PlaySignal.Sleep)
    }

    /** Ends the open session when [displayId] is the one the player launched on. */
    fun endIfDisplayHome(plays: PlaySessions, displayId: Int) {
        if (this.displayId != displayId) return
        val id = sessionId ?: return
        sessionId = null
        this.displayId = null
        plays.signal(id, PlaySignal.End)
    }
}
