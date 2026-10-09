package app.foldcade

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.foldcade.host.play.FilePlayLog
import app.foldcade.host.play.PlayClock
import app.foldcade.host.play.PlayEvent
import app.foldcade.host.play.PlayLog
import app.foldcade.host.play.PlaySignal
import app.foldcade.host.play.PlayTotals
import java.io.File

/**
 * Host play history for the launch path.
 *
 * [file] lives under the app's files directory. It is not under `noBackupFilesDir`.
 * A session records the game id, when it started, left the foreground, resumed,
 * and ended. It holds no token, password, or save bytes, so backup rules leave
 * it in place.
 *
 * [subscribe] and [events] are the internal stream. A later companion plugin
 * and RomM sync read [PlayEvent] as stored here. This type is not on the plugin API.
 */
class PlaySessions private constructor(
    private val log: PlayLog,
) {
    var stamp by mutableIntStateOf(0)
        private set

    /** Fired after a session fact is stored. The shell refreshes Recently played. */
    var onChanged: (() -> Unit)? = null

    fun start(gameId: String): String {
        val id = log.start(gameId)
        changed()
        return id
    }

    fun signal(sessionId: String, signal: PlaySignal) {
        val before = log.events().size
        log.signal(sessionId, signal)
        if (log.events().size != before) changed()
    }

    fun totals(gameId: String): PlayTotals = log.totals(gameId)

    fun lastPlayedMillis(gameId: String): Long? = log.totals(gameId).lastPlayedMillis

    fun events(): List<PlayEvent> = log.events()

    fun subscribe(listener: (PlayEvent) -> Unit): AutoCloseable = log.subscribe(listener)

    private fun changed() {
        stamp++
        onChanged?.invoke()
    }

    companion object {
        fun open(file: File, clock: PlayClock = PlayClock { System.currentTimeMillis() }): PlaySessions =
            PlaySessions(PlayLog(clock, FilePlayLog(file)))
    }
}
