package app.foldcade

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.foldcade.host.play.FilePlayLog
import app.foldcade.host.play.PlayClock
import app.foldcade.host.play.PlayEvent
import app.foldcade.host.play.PlayKind
import app.foldcade.language.PlaySpan
import app.foldcade.host.play.PlayLog
import app.foldcade.host.play.PlayNames
import app.foldcade.host.play.PlaySignal
import app.foldcade.host.play.QueuedPlaySink
import app.foldcade.host.play.PackagePlay
import app.foldcade.host.play.PlayTotals
import app.foldcade.host.play.ShownPlay
import app.foldcade.host.play.UsageSample
import app.foldcade.host.play.packagePlay
import app.foldcade.host.play.shownPlay
import app.foldcade.host.play.usageSpans
import app.foldcade.host.play.UsageSpan
import app.foldcade.host.play.withoutStretches
import app.foldcade.language.PlayCopy
import app.foldcade.language.homeGameKey
import app.foldcade.localfolder.LocalFolderBackend
import java.io.File

/**
 * Host play history for the launch path.
 *
 * [file] lives under the app's files directory. It is not under `noBackupFilesDir`.
 * Writes to it run on the play-log thread, not the caller's ([QueuedPlaySink]).
 * A session records the game id, when it started, left the foreground, resumed,
 * and ended. It holds no token, password, or save bytes, so backup rules leave
 * it in place.
 *
 * [subscribe] and [events] are the internal stream. A later companion plugin
 * and RomM sync read [PlayEvent] as stored here. This type is not on the plugin API.
 *
 * Each game's name when it was played is kept in [PlayNames], beside the log,
 * so its history still reads once the game leaves every catalog.
 */
class PlaySessions private constructor(
    private val log: PlayLog,
    private val names: PlayNames,
) {
    var stamp by mutableIntStateOf(0)
        private set

    /** Fired after a session fact is stored. The shell refreshes Recently played. */
    var onChanged: (() -> Unit)? = null

    /** Player package for a game id. Null for a stand-in that has no package. */
    var packageOf: (String) -> String? = { null }

    /**
     * Every id a tile's play is kept under. A game several libraries have is one tile, and
     * each copy kept its own history before it folded. That history stays under its id.
     */
    var historyIds: (String) -> Set<String> = { setOf(it) }

    /** The tile a game id is shown as now, so a folded game's play is one row. */
    var tileOf: (String) -> String = { it }

    private var usageGranted = false
    private var usageByPackage = emptyMap<String, PackagePlay>()
    private var usageSeen = emptyList<UsageSpan>()
    private var usageNow = 0L

    /** Opens a session. [name] is the game's name as the launch showed it. */
    fun start(gameId: String, name: String? = null): String {
        if (name != null) names.remember(gameId, name)
        val id = log.start(gameId)
        changed()
        return id
    }

    fun signal(sessionId: String, signal: PlaySignal) {
        if (log.signal(sessionId, signal)) changed()
    }

    /** Foreground sample for crash recovery. It does not emit a session event. */
    fun checkpoint(sessionId: String) {
        log.checkpoint(sessionId)
    }

    fun totals(gameId: String): PlayTotals = log.totals(gameId, historyIds(gameId))

    fun shown(gameId: String): ShownPlay {
        val lifecycle = totals(gameId)
        val usage = packageOf(gameId)?.let { usageByPackage[it] }
        return shownPlay(lifecycle.activeMillis, lifecycle.lastPlayedMillis, usage, usageGranted)
    }

    fun lastPlayedMillis(gameId: String): Long? = shown(gameId).lastPlayedMillis

    fun hasTrackedSession(): Boolean = log.events().isNotEmpty()

    /** Replaces the usage overlay. Lifecycle sessions stay in place for when access is off. */
    fun applyUsage(granted: Boolean, samples: List<UsageSample>, nowMillis: Long) {
        usageGranted = granted
        usageSeen = if (granted) usageSpans(samples) else emptyList()
        usageNow = nowMillis
        countUsage()
        changed()
    }

    fun events(): List<PlayEvent> = log.events()

    /**
     * Takes a session out of the history, as one left running overnight. The
     * schedule, the totals on tiles, and Recently played all stop counting it.
     */
    fun remove(sessionId: String) {
        if (!log.remove(sessionId)) return
        countUsage()
        changed()
    }

    /** Usage time per package, less every session the player removed, which Android also saw. */
    private fun countUsage() {
        if (!usageGranted) {
            usageByPackage = emptyMap()
            return
        }
        val cuts = HashMap<String, MutableList<LongRange>>()
        for ((gameId, stretch) in removedStretches(log.events())) {
            val pkg = packageOf(gameId) ?: continue
            cuts.getOrPut(pkg) { mutableListOf() } += stretch
        }
        usageByPackage = packagePlay(withoutStretches(usageSeen, cuts, usageNow), usageNow)
    }

    /** The name [gameId] had when it was last played or seen in a catalog. */
    fun name(gameId: String): String? =
        names.name(gameId) ?: historyIds(gameId).firstNotNullOfOrNull { names.name(it) }

    /** Keeps a catalog's name for [gameId], for when the catalog no longer has it. */
    fun rememberName(gameId: String, name: String) {
        names.remember(gameId, name)
    }

    /** Every stretch a game was in front, for the Play activity page. */
    fun spans(): List<PlaySpan> = playSpans(log.events()).map { it.copy(gameId = tileOf(it.gameId)) }

    fun subscribe(listener: (PlayEvent) -> Unit): AutoCloseable = log.subscribe(listener)

    private fun changed() {
        stamp++
        onChanged?.invoke()
    }

    companion object {
        fun open(file: File, clock: PlayClock = WallAndElapsedClock): PlaySessions =
            PlaySessions(
                PlayLog(clock, QueuedPlaySink(FilePlayLog(file))),
                PlayNames(File(file.absoluteFile.parentFile, "names.log")),
            )
    }
}

/**
 * The log's events as stretches in front. Each starts at the wall time the game
 * came to the front and lasts as long as the active time it gained, so sleep and
 * time away never count. A session still in front has no stretch until it leaves.
 * A session the player removed has none at all.
 */
internal fun playSpans(events: List<PlayEvent>): List<PlaySpan> {
    val removed = events.filter { it.kind == PlayKind.Removed }.mapTo(HashSet()) { it.sessionId }
    val front = HashMap<String, PlayEvent>()
    val spans = mutableListOf<PlaySpan>()
    for (event in events) {
        if (event.sessionId in removed) continue
        when (event.kind) {
            PlayKind.Started, PlayKind.Resumed -> front[event.sessionId] = event
            PlayKind.Backgrounded, PlayKind.Ended -> {
                val since = front.remove(event.sessionId) ?: continue
                val active = event.activeMillis - since.activeMillis
                if (active > 0L) spans += PlaySpan(event.gameId, since.atMillis, since.atMillis + active, event.sessionId)
            }
            PlayKind.Removed -> Unit
        }
    }
    return spans
}

/**
 * Each removed session's wall-clock stretch, from the time it came to the front to
 * the time it left, by game. Usage stats saw the same time, so it comes out of
 * them too ([withoutStretches]). A removed session still in front has none yet.
 */
internal fun removedStretches(events: List<PlayEvent>): List<Pair<String, LongRange>> {
    val removed = events.filter { it.kind == PlayKind.Removed }.mapTo(HashSet()) { it.sessionId }
    if (removed.isEmpty()) return emptyList()
    val front = HashMap<String, PlayEvent>()
    val stretches = mutableListOf<Pair<String, LongRange>>()
    for (event in events) {
        if (event.sessionId !in removed) continue
        when (event.kind) {
            PlayKind.Started, PlayKind.Resumed -> front[event.sessionId] = event
            PlayKind.Backgrounded, PlayKind.Ended -> {
                val since = front.remove(event.sessionId) ?: continue
                if (event.atMillis > since.atMillis) stretches += event.gameId to (since.atMillis..event.atMillis)
            }
            PlayKind.Removed -> Unit
        }
    }
    return stretches
}

/**
 * The name Play activity shows for [gameId]. A catalog that still has the game
 * names it, so a rename shows. Then the name it had when last played. Then a
 * name read from the id itself: a storage document's file name, titled as the
 * folder scan titles it. Older history kept a folder game under its bare
 * document URI, so that reads the same way. An id with nothing readable in it,
 * such as a server's number, is [PlayCopy.removedGame], never the id.
 */
internal fun playName(gameId: String, current: String?, remembered: String?): String =
    current?.trim()?.ifEmpty { null }
        ?: remembered?.trim()?.ifEmpty { null }
        ?: nameFromId(gameId)
        ?: PlayCopy.removedGame

private fun nameFromId(gameId: String): String? {
    val key = homeGameKey(gameId) ?: gameId
    if (!key.startsWith("content://") && !key.startsWith("file://")) return null
    return LocalFolderBackend.titleOf(key)
}

private object WallAndElapsedClock : PlayClock {
    override fun wallNow(): Long = System.currentTimeMillis()

    override fun elapsedNow(): Long = SystemClock.elapsedRealtime()
}
