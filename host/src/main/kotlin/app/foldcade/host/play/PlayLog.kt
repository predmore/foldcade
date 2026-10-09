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
 * and does not subtract timestamps. Adding a field or a kind would be a
 * schema change. v1 does not need one.
 */
const val PLAY_EVENT_SCHEMA = 1

/** What the launch path and the running game tell the log. */
enum class PlaySignal {
    Background,
    Sleep,
    Resume,
    End,
}

/** The four facts on the stream. Closed. */
enum class PlayKind {
    Started,
    Backgrounded,
    Resumed,
    Ended,
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

fun interface PlayClock {
    fun now(): Long
}

/** Durable history of [PlayEvent]. The log replays [read] on startup. */
interface PlaySink {
    fun append(event: PlayEvent)

    fun read(): List<PlayEvent>
}

/**
 * Open plays are keyed by session id. The game id is the one the launch path already uses.
 *
 * [start] opens a session and returns its id. [signal] records background, sleep,
 * resume, and end for that id. Another launch of the same game is a second
 * session. A second leave while that session is already away does not add time
 * and does not emit another event. A process that died mid-session is closed
 * at the last stored event, so the gap is not counted.
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
    private val lastAt = HashMap<String, Long>()
    private val listeners = mutableListOf<(PlayEvent) -> Unit>()

    init {
        val prior = sink.read()
        synchronized(lock) {
            prior.forEach { apply(it) }
            history.addAll(prior)
            for (session in open.values.toList()) {
                commitLocked(session.ended(session.lastAt, addForeground = false))
            }
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

    fun totals(gameId: String): PlayTotals = synchronized(lock) {
        val now = clock.now()
        val running = open.values.sumOf { session ->
            if (session.gameId == gameId) session.currentActive(now) else 0L
        }
        PlayTotals(gameId, (completedActive[gameId] ?: 0L) + running, lastAt[gameId])
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
                atMillis = clock.now(),
                away = null,
                activeMillis = 0L,
            )
            commitLocked(event)
            event
        }
        dispatch(listOf(started))
        return started.sessionId
    }

    /** Records [signal] for a session [start] returned. An unknown id is ignored. */
    fun signal(sessionId: String, signal: PlaySignal) {
        val emitted = synchronized(lock) {
            val session = open[sessionId] ?: return@synchronized emptyList()
            val now = clock.now()
            val event = when (signal) {
                PlaySignal.Background -> session.leave(now, PlayAway.Background)
                PlaySignal.Sleep -> session.leave(now, PlayAway.Sleep)
                PlaySignal.Resume -> session.resume(now)
                PlaySignal.End -> session.ended(now, addForeground = true)
            } ?: return@synchronized emptyList()
            commitLocked(event)
            listOf(event)
        }
        dispatch(emitted)
    }

    private fun commitLocked(event: PlayEvent) {
        sink.append(event)
        history.add(event)
        apply(event)
    }

    private fun apply(event: PlayEvent) {
        val known = lastAt[event.gameId]
        if (known == null || event.atMillis >= known) lastAt[event.gameId] = event.atMillis
        when (event.kind) {
            PlayKind.Started -> open[event.sessionId] = OpenPlay(
                sessionId = event.sessionId,
                gameId = event.gameId,
                activeMillis = event.activeMillis,
                foregroundSince = event.atMillis,
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
                foregroundSince = event.atMillis
                away = null
                lastAt = event.atMillis
            }
            PlayKind.Ended -> {
                completedActive[event.gameId] = (completedActive[event.gameId] ?: 0L) + event.activeMillis
                open.remove(event.sessionId)
            }
        }
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

    fun leave(now: Long, reason: PlayAway): PlayEvent? {
        val since = foregroundSince ?: return null
        activeMillis += (now - since).coerceAtLeast(0L)
        foregroundSince = null
        away = reason
        lastAt = now
        return PlayEvent(sessionId, gameId, PlayKind.Backgrounded, now, reason, activeMillis)
    }

    fun resume(now: Long): PlayEvent? {
        if (foregroundSince != null) return null
        foregroundSince = now
        away = null
        lastAt = now
        return PlayEvent(sessionId, gameId, PlayKind.Resumed, now, null, activeMillis)
    }

    fun ended(now: Long, addForeground: Boolean): PlayEvent {
        if (addForeground && foregroundSince != null) {
            val since = foregroundSince ?: now
            activeMillis += (now - since).coerceAtLeast(0L)
            lastAt = now
        }
        foregroundSince = null
        away = null
        return PlayEvent(sessionId, gameId, PlayKind.Ended, lastAt, null, activeMillis)
    }
}
