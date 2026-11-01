package app.foldcade.host.play

import java.util.UUID

/**
 * Schema of one play-session fact.
 *
 * This is the record a later companion plugin and RomM sync subscribe to.
 * It is not a plugin API. The fields below are the whole contract: session,
 * game, the four lifecycle kinds, when it happened, why a background happened,
 * and the active time in that session after the event. Active time already
 * excludes background and sleep, so a subscriber uses [PlayEvent.activeMillis]
 * and does not subtract timestamps. Adding a field would be a schema change.
 * [PlayKind.Removed] and [PlayKind.Moved] were added within v1: a reader that
 * does not know a kind skips that line, so an older build still reads the rest,
 * and only counts the removed session again, or under the game it was played as.
 */
const val PLAY_EVENT_SCHEMA = 1

/** What the launch path and the running game tell the log. */
enum class PlaySignal {
    Background,
    Sleep,
    Resume,
    End,
}

/** The lifecycle facts on the stream, and the player taking a session back out. */
enum class PlayKind {
    Started,
    Backgrounded,
    Resumed,
    Ended,

    /**
     * The player took this session out of their history, as a game left running
     * overnight. Its time no longer counts anywhere. The session's own events stay
     * in the log, which is append-only; readers skip a session with this event.
     */
    Removed,

    /**
     * The player moved this session's time to another game, as after renaming or
     * replacing a ROM. [PlayEvent.gameId] is the game it counts for from now on;
     * the session's own events keep the game it was played as. A later move of
     * the same session wins, so moving it back is one more line.
     */
    Moved,
}

/**
 * Why the game left the foreground.
 * [Sleep] is the device sleeping, including the screen turning off.
 * [Background] is every other time the game is not the foreground.
 */
enum class PlayAway {
    Background,
    Sleep,
}

data class PlayEvent(
    val sessionId: String,
    val gameId: String,
    val kind: PlayKind,
    val atMillis: Long,
    val away: PlayAway?,
    val activeMillis: Long,
)

data class PlayTotals(
    val gameId: String,
    val activeMillis: Long,
    val lastPlayedMillis: Long?,
)

/** One session [PlayLog.move] gave to [to]. [from] is the game it counted for before, so it can go back. */
data class PlayMove(val sessionId: String, val from: String, val to: String)

/** What [PlayLog.merge] took in: [sessions] new to this log, and [changes] removals and moves of sessions. */
data class PlayMerge(val sessions: Int, val changes: Int) {
    val empty: Boolean get() = sessions == 0 && changes == 0
}

/**
 * [wallNow] stamps start and last played. [elapsedNow] measures active time.
 * The app uses the wall clock for those stamps and elapsedRealtime for the deltas.
 */
interface PlayClock {
    fun wallNow(): Long

    fun elapsedNow(): Long

    companion object {
        /** One clock for both, so a test can advance them together. */
        operator fun invoke(now: () -> Long): PlayClock = object : PlayClock {
            override fun wallNow(): Long = now()
            override fun elapsedNow(): Long = now()
        }
    }
}

/**
 * Latest foreground sample for one open session.
 * Not a [PlayEvent]. Recovery uses it once, then drops it.
 */
data class PlayCheckpoint(
    val sessionId: String,
    val activeMillis: Long,
    val atMillis: Long,
)

/** How often a foreground game asks the log to [PlayLog.checkpoint]. */
const val PLAY_CHECKPOINT_INTERVAL_MS = 60_000L

/** Durable history of [PlayEvent]. The log replays [read] on startup. */
interface PlaySink {
    fun append(event: PlayEvent)

    fun read(): List<PlayEvent>

    fun writeCheckpoint(checkpoint: PlayCheckpoint)

    fun readCheckpoint(): PlayCheckpoint?

    fun clearCheckpoint(sessionId: String)
}

/**
 * Open plays are keyed by session id. The game id is the one the launch path already uses.
 *
 * [start] opens a session and returns its id. [signal] records background, sleep,
 * resume, and end for that id. Another launch of the same game is a second
 * session. A second leave while that session is already away does not add time
 * and does not emit another event. Active time is an [PlayClock.elapsedNow]
 * delta, so a wall-clock change does not add or remove it. A negative delta
 * counts as zero. A process that died mid-session is closed at the last stored
 * event, plus a [checkpoint] if one was written while that session was in the
 * foreground. Time after that checkpoint is not invented. A checkpoint is not
 * a [PlayEvent].
 */
class PlayLog(
    private val clock: PlayClock,
    private val sink: PlaySink,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private val history = mutableListOf<PlayEvent>()
    private val open = LinkedHashMap<String, OpenPlay>()
    private val completedActive = HashMap<String, Long>()

    /** The game each session was started for, as its own events name it. */
    private val playedAs = HashMap<String, String>()

    /** The game a moved session counts for now ([PlayKind.Moved]). */
    private val movedTo = HashMap<String, String>()

    /** Each ended session's active time, so a removal or a move can take it back out. */
    private val ended = HashMap<String, Long>()
    private val removed = HashSet<String>()
    private val listeners = mutableListOf<(PlayEvent) -> Unit>()

    init {
        val prior = sink.read()
        synchronized(lock) {
            prior.forEach { apply(it) }
            history.addAll(prior)
            val checkpoint = sink.readCheckpoint()
            for (session in open.values.toList()) {
                session.credit(checkpoint)
                commitLocked(session.ended(session.lastAt, elapsed = clock.elapsedNow(), addForeground = false))
            }
            if (checkpoint != null) sink.clearCheckpoint(checkpoint.sessionId)
        }
    }

    /** New events after this call. History is [events]. */
    fun subscribe(listener: (PlayEvent) -> Unit): AutoCloseable {
        synchronized(lock) { listeners.add(listener) }
        return AutoCloseable {
            synchronized(lock) { listeners.remove(listener) }
        }
    }

    fun events(): List<PlayEvent> = synchronized(lock) { history.toList() }

    /**
     * [events] for an export: everything but a session still open, which another device
     * would have to close early. Its removal or move still goes, and the next export
     * carries the session whole once it has ended.
     */
    fun finished(): List<PlayEvent> = synchronized(lock) {
        history.filter { it.sessionId !in open || it.kind == PlayKind.Removed || it.kind == PlayKind.Moved }
    }

    /** True before the first session, without copying [events]. */
    fun isEmpty(): Boolean = synchronized(lock) { history.isEmpty() }

    fun totals(gameId: String): PlayTotals = totals(gameId, setOf(gameId))

    /**
     * One game's play recorded under several ids, as [gameId]: the active time of
     * all [gameIds] added up, and the latest of their last plays.
     */
    fun totals(gameId: String, gameIds: Set<String>): PlayTotals = synchronized(lock) {
        val elapsed = clock.elapsedNow()
        val running = open.values.sumOf { session ->
            if (gameOf(session.sessionId, session.gameId) in gameIds && session.sessionId !in removed) session.currentActive(elapsed) else 0L
        }
        val completed = gameIds.sumOf { completedActive[it] ?: 0L }
        val now = clock.wallNow()
        // A stamp written while the clock was ahead must not stay "last played" after it is corrected.
        // A removed session was never played, as far as the player is concerned.
        val last = history.asSequence()
            .filter { it.played() && it.atMillis <= now && gameOf(it.sessionId, it.gameId) in gameIds }
            .maxOfOrNull { it.atMillis }
        PlayTotals(gameId, completed + running, last)
    }

    /**
     * Every game with play that still counts, by the game it counts for now: its time,
     * running sessions included, and its last play. A game whose sessions were all
     * removed or moved away is not here.
     */
    fun played(): Map<String, PlayTotals> = synchronized(lock) {
        val elapsed = clock.elapsedNow()
        val now = clock.wallNow()
        val active = HashMap<String, Long>()
        val last = HashMap<String, Long>()
        for (event in history) {
            if (!event.played()) continue
            val game = gameOf(event.sessionId, event.gameId)
            active.putIfAbsent(game, 0L)
            if (event.atMillis <= now && event.atMillis > (last[game] ?: Long.MIN_VALUE)) last[game] = event.atMillis
        }
        for ((sessionId, millis) in ended) {
            if (sessionId in removed) continue
            val game = ownerOf(sessionId) ?: continue
            active[game] = (active[game] ?: 0L) + millis
        }
        for (session in open.values) {
            if (session.sessionId in removed) continue
            val game = gameOf(session.sessionId, session.gameId)
            active[game] = (active[game] ?: 0L) + session.currentActive(elapsed)
        }
        active.mapValues { (game, millis) -> PlayTotals(game, millis, last[game]) }
    }

    /**
     * [events] as readers count them: each lifecycle event names the game its session
     * counts for now, and the moves themselves are left out. Removals stay, as in [events].
     */
    fun counted(): List<PlayEvent> = synchronized(lock) {
        history.mapNotNull { event ->
            when {
                event.kind == PlayKind.Moved -> null
                event.kind == PlayKind.Removed -> event
                else -> gameOf(event.sessionId, event.gameId).let { game -> if (game == event.gameId) event else event.copy(gameId = game) }
            }
        }
    }

    /**
     * Starts a play for [gameId]. Returns the session id the activity passes
     * back to [signal]. Does not end a play that is already open for this game.
     */
    fun start(gameId: String): String {
        require(gameId.isNotBlank()) { "game id is empty" }
        val started = synchronized(lock) {
            val id = newId()
            val event = PlayEvent(
                sessionId = id,
                gameId = gameId,
                kind = PlayKind.Started,
                atMillis = clock.wallNow(),
                away = null,
                activeMillis = 0L,
            )
            commitLocked(event)
            event
        }
        dispatch(listOf(started))
        return started.sessionId
    }

    /**
     * Records [signal] for a session [start] returned. An unknown id is ignored.
     * Returns true when this stored an event.
     */
    fun signal(sessionId: String, signal: PlaySignal): Boolean {
        val emitted = synchronized(lock) {
            val session = open[sessionId] ?: return@synchronized emptyList()
            val wall = clock.wallNow()
            val elapsed = clock.elapsedNow()
            val event = when (signal) {
                PlaySignal.Background -> session.leave(wall, elapsed, PlayAway.Background)
                PlaySignal.Sleep -> session.leave(wall, elapsed, PlayAway.Sleep)
                PlaySignal.Resume -> session.resume(wall, elapsed)
                PlaySignal.End -> session.ended(wall, elapsed, addForeground = true)
            } ?: return@synchronized emptyList()
            commitLocked(event)
            if (event.kind == PlayKind.Backgrounded || event.kind == PlayKind.Ended) {
                sink.clearCheckpoint(sessionId)
            }
            listOf(event)
        }
        dispatch(emitted)
        return emitted.isNotEmpty()
    }

    /**
     * Stores the active time of a foreground session without emitting an event.
     * The running game does this on a [PLAY_CHECKPOINT_INTERVAL_MS] cadence and
     * when the screen turns off. An away session is left unchanged.
     */
    fun checkpoint(sessionId: String) {
        synchronized(lock) {
            val session = open[sessionId] ?: return
            val since = session.foregroundSince ?: return
            val active = session.activeMillis + (clock.elapsedNow() - since).coerceAtLeast(0L)
            sink.writeCheckpoint(PlayCheckpoint(sessionId, active, clock.wallNow()))
        }
    }

    /** Sessions the player removed. Their events stay in [events]; readers leave them out. */
    fun removedSessions(): Set<String> = synchronized(lock) { removed.toSet() }

    /**
     * Takes session [sessionId] out of the history: its time leaves the totals and
     * its stretches leave the schedule. A session still open keeps logging, but
     * still counts for nothing. Returns false for an unknown or already removed id.
     */
    fun remove(sessionId: String): Boolean {
        val event = synchronized(lock) {
            if (sessionId in removed) return@synchronized null
            if (sessionId !in open && sessionId !in ended) return@synchronized null
            val gameId = ownerOf(sessionId) ?: return@synchronized null
            PlayEvent(sessionId, gameId, PlayKind.Removed, clock.wallNow(), null, 0L).also(::commitLocked)
        } ?: return false
        dispatch(listOf(event))
        return true
    }

    /**
     * Gives every ended session that counts for one of [fromGameIds] to [toGameId], as
     * after a ROM was renamed or replaced: its time and its last play count there from
     * now on. A session still open stays, and so does a removed one, which counts for
     * nothing anywhere. Returns what moved, for [undo]; empty when nothing did.
     */
    fun move(fromGameIds: Set<String>, toGameId: String): List<PlayMove> {
        require(toGameId.isNotBlank()) { "game id is empty" }
        val moved = synchronized(lock) {
            val sessions = ended.keys
                .filter { it !in removed && it !in open }
                .mapNotNull { id -> ownerOf(id)?.takeIf { it in fromGameIds && it != toGameId }?.let { id to it } }
            // The log's order, so the lines read as the sessions were played.
            val order = history.asSequence().filter { it.kind == PlayKind.Started }.map { it.sessionId }.withIndex()
                .associate { (index, id) -> id to index }
            sessions.sortedBy { order[it.first] ?: Int.MAX_VALUE }.map { (sessionId, from) ->
                PlayEvent(sessionId, toGameId, PlayKind.Moved, clock.wallNow(), null, 0L).also(::commitLocked) to
                    PlayMove(sessionId, from, toGameId)
            }
        }
        dispatch(moved.map { it.first })
        return moved.map { it.second }
    }

    /**
     * Puts back what [move] did: each session that still counts for its [PlayMove.to]
     * goes back to [PlayMove.from]. One moved again since stays where it went.
     * Returns how many went back.
     */
    fun undo(moves: List<PlayMove>): Int {
        val back = synchronized(lock) {
            moves.mapNotNull { move ->
                if (move.sessionId in removed || move.sessionId !in ended) return@mapNotNull null
                if (ownerOf(move.sessionId) != move.to || move.from == move.to) return@mapNotNull null
                PlayEvent(move.sessionId, move.from, PlayKind.Moved, clock.wallNow(), null, 0L).also(::commitLocked)
            }
        }
        dispatch(back)
        return back.size
    }

    /**
     * Takes in another log's [events], as an imported export: each session this log does
     * not have yet, whole, then the removals and moves it has not seen. Merging the same
     * export again adds nothing. A session that never ended there is closed at its last
     * event, as a session open when the process died is.
     */
    fun merge(events: List<PlayEvent>): PlayMerge {
        val added = synchronized(lock) {
            val present = HashSet(history)
            val known = history.mapTo(HashSet()) { it.sessionId }
            val fresh = LinkedHashMap<String, MutableList<PlayEvent>>()
            val changes = mutableListOf<PlayEvent>()
            for (event in events) {
                if (event in present || event.sessionId.isBlank() || event.gameId.isBlank()) continue
                when (event.kind) {
                    PlayKind.Removed, PlayKind.Moved -> changes += event
                    else -> if (event.sessionId !in known) fresh.getOrPut(event.sessionId) { mutableListOf() } += event
                }
            }
            val committed = mutableListOf<PlayEvent>()
            var sessions = 0
            for (stretch in fresh.values) {
                val played = whole(stretch) ?: continue
                played.forEach { commitLocked(it); committed += it }
                sessions++
            }
            var changed = 0
            for (event in changes.distinct()) {
                val session = event.sessionId
                if (session !in ended && session !in open) continue
                if (event.kind == PlayKind.Removed) {
                    if (session in removed) continue
                    commitLocked(event)
                    committed += event
                    changed++
                    continue
                }
                // A move is kept even when the session already counts there: a later line in
                // the same file may move it on, and the next import must find both already here.
                val effective = ownerOf(session) != event.gameId
                commitLocked(event)
                committed += event
                if (effective) changed++
            }
            committed to PlayMerge(sessions, changed)
        }
        dispatch(added.first)
        return added.second
    }

    private fun commitLocked(event: PlayEvent) {
        sink.append(event)
        history.add(event)
        apply(event)
    }

    /** The game session [sessionId] counts for now: where it was moved, else what it was played as. */
    private fun ownerOf(sessionId: String): String? = movedTo[sessionId] ?: playedAs[sessionId]

    private fun gameOf(sessionId: String, fallback: String): String = ownerOf(sessionId) ?: fallback

    /** A lifecycle fact of a session that still counts. */
    private fun PlayEvent.played(): Boolean = kind != PlayKind.Removed && kind != PlayKind.Moved && sessionId !in removed

    private fun apply(event: PlayEvent) {
        if (event.kind == PlayKind.Removed) {
            if (!removed.add(event.sessionId)) return
            val active = ended[event.sessionId] ?: return
            val gameId = gameOf(event.sessionId, event.gameId)
            completedActive[gameId] = ((completedActive[gameId] ?: 0L) - active).coerceAtLeast(0L)
            return
        }
        if (event.kind == PlayKind.Moved) {
            val before = ownerOf(event.sessionId)
            movedTo[event.sessionId] = event.gameId
            val active = ended[event.sessionId]
            if (active != null && before != null && event.sessionId !in removed && before != event.gameId) {
                completedActive[before] = ((completedActive[before] ?: 0L) - active).coerceAtLeast(0L)
                completedActive[event.gameId] = (completedActive[event.gameId] ?: 0L) + active
            }
            return
        }
        playedAs.putIfAbsent(event.sessionId, event.gameId)
        when (event.kind) {
            PlayKind.Started -> open[event.sessionId] = OpenPlay(
                sessionId = event.sessionId,
                gameId = event.gameId,
                activeMillis = event.activeMillis,
                foregroundSince = clock.elapsedNow(),
                away = null,
                lastAt = event.atMillis,
            )
            PlayKind.Backgrounded -> open[event.sessionId]?.apply {
                activeMillis = event.activeMillis
                foregroundSince = null
                away = event.away
                lastAt = event.atMillis
            }
            PlayKind.Resumed -> open[event.sessionId]?.apply {
                activeMillis = event.activeMillis
                foregroundSince = clock.elapsedNow()
                away = null
                lastAt = event.atMillis
            }
            PlayKind.Ended -> {
                // A second end of one session would count it twice. The log never writes one.
                if (event.sessionId in ended) return
                if (event.sessionId !in removed) {
                    val gameId = gameOf(event.sessionId, event.gameId)
                    completedActive[gameId] = (completedActive[gameId] ?: 0L) + event.activeMillis
                }
                ended[event.sessionId] = event.activeMillis
                open.remove(event.sessionId)
            }
            PlayKind.Removed, PlayKind.Moved -> Unit
        }
    }

    /**
     * One imported session's lifecycle, from its start, ended. Null when it has no start,
     * so its time cannot be read. One that never ended is closed at its last event.
     */
    private fun whole(stretch: List<PlayEvent>): List<PlayEvent>? {
        val start = stretch.indexOfFirst { it.kind == PlayKind.Started }
        if (start < 0) return null
        val end = stretch.indexOfFirst { it.kind == PlayKind.Ended }
        if (end in 0 until start) return null
        if (end >= 0) return stretch.subList(start, end + 1)
        val played = stretch.subList(start, stretch.size)
        val last = played.last()
        return played + PlayEvent(last.sessionId, last.gameId, PlayKind.Ended, last.atMillis, null, played.maxOf { it.activeMillis })
    }

    private fun dispatch(events: List<PlayEvent>) {
        if (events.isEmpty()) return
        val current = synchronized(lock) { listeners.toList() }
        for (event in events) {
            for (listener in current) listener(event)
        }
    }
}

/**
 * Indices of [ids] with the most recently played first.
 * An id [lastPlayedMillis] does not know stays after the ones it does, in input order.
 */
fun recentlyPlayedIndices(ids: List<String>, lastPlayedMillis: (String) -> Long?): List<Int> =
    ids.indices.sortedWith(
        compareByDescending<Int> { lastPlayedMillis(ids[it]) ?: Long.MIN_VALUE }.thenBy { it },
    )

private class OpenPlay(
    val sessionId: String,
    val gameId: String,
    var activeMillis: Long,
    var foregroundSince: Long?,
    var away: PlayAway?,
    var lastAt: Long,
) {
    fun currentActive(now: Long): Long {
        val since = foregroundSince ?: return activeMillis
        return activeMillis + (now - since).coerceAtLeast(0L)
    }

    fun credit(checkpoint: PlayCheckpoint?) {
        if (checkpoint == null || checkpoint.sessionId != sessionId || foregroundSince == null) return
        if (checkpoint.activeMillis > activeMillis) activeMillis = checkpoint.activeMillis
        if (checkpoint.atMillis > lastAt) lastAt = checkpoint.atMillis
    }

    fun leave(wall: Long, elapsed: Long, reason: PlayAway): PlayEvent? {
        val since = foregroundSince ?: return null
        activeMillis += (elapsed - since).coerceAtLeast(0L)
        foregroundSince = null
        away = reason
        lastAt = wall
        return PlayEvent(sessionId, gameId, PlayKind.Backgrounded, wall, reason, activeMillis)
    }

    fun resume(wall: Long, elapsed: Long): PlayEvent? {
        if (foregroundSince != null) return null
        foregroundSince = elapsed
        away = null
        lastAt = wall
        return PlayEvent(sessionId, gameId, PlayKind.Resumed, wall, null, activeMillis)
    }

    fun ended(wall: Long, elapsed: Long, addForeground: Boolean): PlayEvent {
        if (addForeground && foregroundSince != null) {
            val since = foregroundSince ?: elapsed
            activeMillis += (elapsed - since).coerceAtLeast(0L)
            lastAt = wall
        }
        foregroundSince = null
        away = null
        return PlayEvent(sessionId, gameId, PlayKind.Ended, lastAt, null, activeMillis)
    }
}
