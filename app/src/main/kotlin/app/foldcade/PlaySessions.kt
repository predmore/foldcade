package app.foldcade

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.foldcade.host.play.FilePlayLog
import app.foldcade.host.play.PlayClock
import app.foldcade.host.play.PlayEvent
import app.foldcade.host.play.PlayLog
import app.foldcade.host.play.PlaySignal
import app.foldcade.host.play.PackagePlay
import app.foldcade.host.play.PlayTotals
import app.foldcade.host.play.ShownPlay
import app.foldcade.host.play.UsageSample
import app.foldcade.host.play.packagePlay
import app.foldcade.host.play.shownPlay
import app.foldcade.host.play.usageSpans
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

    /** Player package for a game id. Null for a stand-in that has no package. */
    var packageOf: (String) -> String? = { null }

    private var usageGranted = false
    private var usageByPackage = emptyMap<String, PackagePlay>()

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

    /** Foreground sample for crash recovery. It does not emit a session event. */
    fun checkpoint(sessionId: String) {
        log.checkpoint(sessionId)
    }

    fun totals(gameId: String): PlayTotals = log.totals(gameId)

    fun shown(gameId: String): ShownPlay {
        val lifecycle = log.totals(gameId)
        val usage = packageOf(gameId)?.let { usageByPackage[it] }
        return shownPlay(lifecycle.activeMillis, lifecycle.lastPlayedMillis, usage, usageGranted)
    }

    fun lastPlayedMillis(gameId: String): Long? = shown(gameId).lastPlayedMillis

    fun hasTrackedSession(): Boolean = log.events().isNotEmpty()

    /** Replaces the usage overlay. Lifecycle sessions stay in place for when access is off. */
    fun applyUsage(granted: Boolean, samples: List<UsageSample>, nowMillis: Long) {
        usageGranted = granted
        usageByPackage = if (granted) packagePlay(usageSpans(samples), nowMillis) else emptyMap()
        changed()
    }

    fun events(): List<PlayEvent> = log.events()

    fun subscribe(listener: (PlayEvent) -> Unit): AutoCloseable = log.subscribe(listener)

    private fun changed() {
        stamp++
        onChanged?.invoke()
    }

    companion object {
        fun open(file: File, clock: PlayClock = WallAndElapsedClock): PlaySessions =
            PlaySessions(PlayLog(clock, FilePlayLog(file)))
    }
}

private object WallAndElapsedClock : PlayClock {
    override fun wallNow(): Long = System.currentTimeMillis()

    override fun elapsedNow(): Long = SystemClock.elapsedRealtime()
}
