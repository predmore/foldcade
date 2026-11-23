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
import app.foldcade.host.play.PlayArchive
import app.foldcade.host.play.PlayExport
import app.foldcade.host.play.PlayLog
import app.foldcade.host.play.PlayMerge
import app.foldcade.host.play.PlayMove
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
import java.io.OutputStream

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
    /** Each played game's cover, kept so its history still shows it once no library has it. */
    val art: PlayArt,
) {
    var stamp by mutableIntStateOf(0)
        private set

    /** Fired after a session fact is stored. The shell refreshes Recently played. */
    var onChanged: (() -> Unit)? = null

    /** Package usage belongs only to a tile that represents the whole app, never one of its ROMs. */
    var packageOf: (String) -> String? = { null }

    /** Game tiles whose Android usage counts in Play activity. Non-game apps keep their launch history. */
    var usageGames: () -> List<String> = { emptyList() }

    /**
     * Every id a tile's play is kept under. A game several libraries have is one tile, and
     * each copy kept its own history before it folded. That history stays under its id.
     */
    var historyIds: (String) -> Set<String> = { setOf(it) }

    /** The tile a game id is shown as now, so a folded game's play is one row. */
    var tileOf: (String) -> String = { it }

    /** A session started for the tile a game id is shown as. The app keeps its cover then. */
    var onStarted: ((String) -> Unit)? = null

    private var usageGranted = false
    private var usageByPackage = emptyMap<String, PackagePlay>()
    private var usageSeen = emptyList<UsageSpan>()
    private var usageCounted = emptyList<UsageSpan>()
    private var usageNow = 0L
    private var usageFrom = 0L

    /** Opens a session. [name] is the game's name as the launch showed it. */
    fun start(gameId: String, name: String? = null): String {
        if (name != null) names.remember(gameId, name)
        val id = log.start(gameId)
        changed()
        onStarted?.invoke(tileOf(gameId))
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
        val pkg = packageOf(gameId)
        val usage = pkg?.takeIf { name -> usageGames().any { packageOf(it) == name } }?.let { usageByPackage[it] }
        if (usageGranted && usage != null) {
            // Android retains only a short event window. Older launch history must stay visible.
            val ids = historyIds(gameId)
            val older = beforeUsage(playSpans(log.counted()).filter { it.gameId in ids })
            return ShownPlay(usage.activeMillis + older.sumOf { it.endMillis - it.startMillis },
                listOfNotNull(usage.lastPlayedMillis, lifecycle.lastPlayedMillis?.takeIf { it < usageFrom }).maxOrNull(), false)
        }
        return shownPlay(lifecycle.activeMillis, lifecycle.lastPlayedMillis, usage, usageGranted)
    }

    fun lastPlayedMillis(gameId: String): Long? = shown(gameId).lastPlayedMillis

    fun hasTrackedSession(): Boolean = !log.isEmpty()

    /** A Play time page is useful for usage-only games too, even before the first launch here. */
    fun hasPlay(): Boolean = hasTrackedSession() || usageGames().any { game ->
        packageOf(game)?.let { usageByPackage[it]?.activeMillis ?: 0L }?.let { it > 0L } == true
    }

    /** Replaces the usage overlay. Lifecycle sessions stay in place for when access is off. */
    fun applyUsage(granted: Boolean, samples: List<UsageSample>, nowMillis: Long,
        fromMillis: Long = nowMillis - UsageAccess.LOOKBACK_MS) {
        usageGranted = granted
        usageSeen = if (granted) usageSpans(samples) else emptyList()
        usageNow = nowMillis
        usageFrom = fromMillis
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
            usageCounted = emptyList()
            return
        }
        val cuts = HashMap<String, MutableList<LongRange>>()
        for ((gameId, stretch) in removedStretches(log.events())) {
            val pkg = packageOf(gameId) ?: continue
            cuts.getOrPut(pkg) { mutableListOf() } += stretch
        }
        usageCounted = withoutStretches(usageSeen, cuts, usageNow)
        usageByPackage = packagePlay(usageCounted, usageNow)
    }

    /** The name [gameId] had when it was last played or seen in a catalog. */
    fun name(gameId: String): String? =
        names.name(gameId) ?: historyIds(gameId).firstNotNullOfOrNull { names.name(it) }

    /** Keeps a catalog's name for [gameId], for when the catalog no longer has it. */
    fun rememberName(gameId: String, name: String) {
        names.remember(gameId, name)
    }

    /**
     * The cover kept for [tileId], or for one of its copies: a cover kept before copies
     * folded into one tile stays under the copy's own id.
     */
    fun keptCover(tileId: String): File? = art.file(tileId) ?: historyIds(tileId).firstNotNullOfOrNull(art::file)

    /**
     * For game tiles, the same source as [shown]: usage replaces lifecycle time, never a ROM’s.
     * A package appears once even if both its Android tile and an emulator stand-in name it.
     * Usage outside a Foldcade launch has no removable session id; Android owns that history.
     * Android events are package/activity observations, not Foldcade launches:
     * https://developer.android.com/reference/android/app/usage/UsageEvents.Event#ACTIVITY_RESUMED
     */
    fun spans(): List<PlaySpan> {
        val lifecycle = playSpans(log.counted()).map { it.copy(gameId = tileOf(it.gameId)) }
        if (!usageGranted) return lifecycle
        val games = usageGames().distinct()
        val byPackage = games.mapNotNull { id -> packageOf(id)?.let { it to tileOf(id) } }
            .distinctBy { it.first }.toMap()
        val overlaid = usageCounted.flatMap { span ->
            val game = byPackage[span.packageName] ?: return@flatMap emptyList()
            // Share the clock-jump cap and future clamping used by the hero's package totals.
            val duration = packagePlay(listOf(span), usageNow)[span.packageName]?.activeMillis ?: 0L
            val end = (span.pausedAt ?: usageNow).coerceAtMost(usageNow)
            val start = end - duration
            if (end <= start) return@flatMap emptyList()
            val launches = lifecycle.filter { packageOf(it.gameId) == span.packageName && it.endMillis > start && it.startMillis < end }
            val edges = (listOf(start, end) + launches.flatMap { listOf(it.startMillis.coerceIn(start, end), it.endMillis.coerceIn(start, end)) }).distinct().sorted()
            edges.zipWithNext { from, to ->
                val launch = launches.firstOrNull { it.startMillis <= from && it.endMillis >= to }
                PlaySpan(game, from, to, launch?.sessionId.orEmpty())
            }
        }
        return lifecycle.flatMap {
            if (packageOf(it.gameId) in byPackage && packageOf(it.gameId) in usageByPackage) beforeUsage(listOf(it)) else listOf(it)
        } + overlaid
    }

    private fun beforeUsage(spans: List<PlaySpan>): List<PlaySpan> = spans.mapNotNull {
        if (it.startMillis < usageFrom) it.copy(endMillis = minOf(it.endMillis, usageFrom)) else null
    }

    /**
     * Every tile with play that still counts, its time and last play: each copy's and each
     * moved session's, as the tile's own totals add them up.
     */
    fun played(): Map<String, PlayTotals> {
        val byTile = HashMap<String, PlayTotals>()
        for ((gameId, totals) in log.played()) {
            val tile = tileOf(gameId)
            val known = byTile[tile]
            byTile[tile] = if (known == null) totals.copy(gameId = tile) else PlayTotals(
                tile,
                known.activeMillis + totals.activeMillis,
                listOfNotNull(known.lastPlayedMillis, totals.lastPlayedMillis).maxOrNull(),
            )
        }
        return byTile
    }

    /**
     * Gives [toTile] every finished session of [fromTile] and of each copy it folds, as
     * after a ROM was renamed or replaced. Returns what moved, for [undo].
     */
    fun move(fromTile: String, toTile: String): List<PlayMove> {
        val target = tileOf(toTile)
        val from = historyIds(fromTile) - historyIds(target)
        if (from.isEmpty()) return emptyList()
        val moved = log.move(from, target)
        if (moved.isNotEmpty()) changed()
        return moved
    }

    fun undo(moves: List<PlayMove>) {
        if (log.undo(moves) > 0) changed()
    }

    /** The whole history to [out]: every finished session as stored, every kept name, and every kept cover. */
    fun export(out: OutputStream) {
        PlayArchive.write(out, log.finished(), names.all(), art.files())
    }

    /**
     * Adds a read [export] to this history, never replacing anything here: sessions it
     * lacks, then removals and moves, then names. Covers go in with [PlayArt.adopt] first,
     * off the main thread. Call this on the main thread, as every other change is.
     */
    fun merge(read: PlayExport): PlayMerge {
        val merged = log.merge(read.events)
        val named = names.merge(read.names)
        if (!merged.empty || named > 0) {
            countUsage()
            changed()
        }
        return merged
    }

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
                PlayArt(File(file.absoluteFile.parentFile, "art")),
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
            PlayKind.Removed, PlayKind.Moved -> Unit
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
            PlayKind.Removed, PlayKind.Moved -> Unit
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

/** A package total belongs to an app tile, never to one ROM or streamed game within it. */
internal fun playTimePackage(gameId: String, shelfPackage: (String) -> String?): String? = when {
    gameId.startsWith("android:") -> gameId.removePrefix("android:").takeIf { it.isNotBlank() }
    gameId in Shelf.APP_TILES -> shelfPackage(gameId)
    else -> null
}
