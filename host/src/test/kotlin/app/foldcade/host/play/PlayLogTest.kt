package app.foldcade.host.play

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayLogTest {
    private var now = 0L
    private var nextId = 0

    @Test
    fun activeTimeExcludesBackground() {
        val log = memoryLog()
        val id = log.start("one")
        now = 3_000
        log.signal(id, PlaySignal.Background)
        now = 9_000
        log.signal(id, PlaySignal.End)
        assertEquals(3_000L, log.totals("one").activeMillis)
        assertEquals(3_000L, log.events().last().activeMillis)
        assertEquals(PlayKind.Ended, log.events().last().kind)
    }

    @Test
    fun activeTimeExcludesSleep() {
        val log = memoryLog()
        val id = log.start("one")
        now = 2_000
        log.signal(id, PlaySignal.Sleep)
        now = 8_000
        log.signal(id, PlaySignal.Resume)
        now = 9_000
        log.signal(id, PlaySignal.End)
        val events = log.events()
        assertEquals(PlayAway.Sleep, events[1].away)
        assertEquals(3_000L, log.totals("one").activeMillis)
        assertEquals(listOf(PlayKind.Started, PlayKind.Backgrounded, PlayKind.Resumed, PlayKind.Ended), events.map { it.kind })
    }

    @Test
    fun secondLeaveDoesNotAddTimeOrAnotherEvent() {
        val log = memoryLog()
        val id = log.start("one")
        now = 1_000
        log.signal(id, PlaySignal.Background)
        now = 5_000
        log.signal(id, PlaySignal.Sleep)
        now = 9_000
        log.signal(id, PlaySignal.End)
        assertEquals(1_000L, log.totals("one").activeMillis)
        assertEquals(3, log.events().size)
        assertEquals(1_000L, log.totals("one").lastPlayedMillis)
    }

    @Test
    fun resumeWhileForegroundIsIgnored() {
        val log = memoryLog()
        val id = log.start("one")
        now = 500
        log.signal(id, PlaySignal.Resume)
        assertEquals(1, log.events().size)
    }

    @Test
    fun twoSessionsOfOneGameBothCount() {
        val log = memoryLog()
        val first = log.start("one")
        now = 1_000
        val second = log.start("one")
        now = 2_000
        log.signal(first, PlaySignal.End)
        now = 3_000
        log.signal(second, PlaySignal.End)
        assertEquals(4_000L, log.totals("one").activeMillis)
    }

    @Test
    fun oneGameUnderSeveralIdsAddsUp() {
        val log = memoryLog()
        val local = log.start("local")
        now = 4_000
        log.signal(local, PlaySignal.End)
        val server = log.start("server")
        now = 5_000
        log.signal(server, PlaySignal.End)
        val tile = log.start("tile")
        now = 7_000
        val both = log.totals("tile", setOf("tile", "local", "server"))
        assertEquals("tile", both.gameId)
        assertEquals(7_000L, both.activeMillis)
        assertEquals(5_000L, both.lastPlayedMillis)
        log.signal(tile, PlaySignal.End)
        assertEquals(4_000L, log.totals("local").activeMillis)
        assertNull(log.totals("tile", setOf("other")).lastPlayedMillis)
    }

    @Test
    fun unknownSessionIsIgnored() {
        val log = memoryLog()
        log.signal("missing", PlaySignal.End)
        assertTrue(log.events().isEmpty())
        assertNull(log.totals("missing").lastPlayedMillis)
    }

    @Test
    fun subscriberSeesTheSameRecordTheLogStores() {
        val log = memoryLog()
        val seen = mutableListOf<PlayEvent>()
        log.subscribe { seen.add(it) }
        val id = log.start("one")
        now = 1_500
        log.signal(id, PlaySignal.Background)
        assertEquals(log.events(), seen)
        assertEquals(PLAY_EVENT_SCHEMA, 1)
        assertEquals(setOf("sessionId", "gameId", "kind", "atMillis", "away", "activeMillis"), PlayEvent::class.java.declaredFields.map { it.name }.toSet())
    }

    @Test
    fun reloadClosesAForegroundSessionWithoutInventingTheGap() {
        val file = tempLog()
        val first = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-1" }
        now = 1_000
        first.start("one")
        now = 9_000
        val second = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-2" }
        assertEquals(0L, second.totals("one").activeMillis)
        val ended = second.events().last()
        assertEquals(PlayKind.Ended, ended.kind)
        assertEquals(1_000L, ended.atMillis)
        assertEquals(0L, ended.activeMillis)
    }

    @Test
    fun reloadKeepsTimeThatWasAlreadyBackgrounded() {
        val file = tempLog()
        val first = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-1" }
        now = 1_000
        val id = first.start("one")
        now = 4_000
        first.signal(id, PlaySignal.Background)
        now = 50_000
        val second = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-2" }
        assertEquals(3_000L, second.totals("one").activeMillis)
        assertEquals(4_000L, second.events().last().atMillis)
        assertEquals(PlayKind.Ended, second.events().last().kind)
    }

    @Test
    fun reloadDoesNotMoveLastPlayedBackward() {
        val file = tempLog()
        val first = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-${nextId++}" }
        now = 1_000
        val older = first.start("one")
        now = 2_000
        first.signal(older, PlaySignal.Background)
        now = 3_000
        val newer = first.start("one")
        now = 8_000
        first.signal(newer, PlaySignal.End)
        now = 90_000
        val second = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-${nextId++}" }
        assertEquals(6_000L, second.totals("one").activeMillis)
        assertEquals(8_000L, second.totals("one").lastPlayedMillis)
    }

    @Test
    fun fileRoundTripsAGameIdThatContainsSeparators() {
        val file = tempLog()
        val log = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-1" }
        val id = log.start("game\twith\nparts")
        now = 2_000
        log.signal(id, PlaySignal.End)
        file.appendText("not a play event\n")
        val loaded = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-2" }
        assertEquals(2_000L, loaded.totals("game\twith\nparts").activeMillis)
        assertEquals("game\twith\nparts", loaded.events().first().gameId)
    }

    @Test
    fun wallClockJumpDoesNotChangeActiveTime() {
        var wall = 1_000L
        var elapsed = 10_000L
        val log = PlayLog(splitClock({ wall }, { elapsed }), MemoryPlaySink()) { "session-1" }
        val id = log.start("one")
        wall = 5_000_000L
        elapsed = 12_000L
        log.signal(id, PlaySignal.End)
        assertEquals(2_000L, log.totals("one").activeMillis)
        assertEquals(5_000_000L, log.totals("one").lastPlayedMillis)
        wall = 2_000L
        assertEquals(1_000L, log.totals("one").lastPlayedMillis)
    }

    @Test
    fun elapsedGoingBackwardAddsNoActiveTime() {
        var wall = 1_000L
        var elapsed = 50_000L
        val log = PlayLog(splitClock({ wall }, { elapsed }), MemoryPlaySink()) { "session-1" }
        val id = log.start("one")
        wall = 4_000L
        elapsed = 1_000L
        log.signal(id, PlaySignal.Background)
        assertEquals(0L, log.totals("one").activeMillis)
        log.signal(id, PlaySignal.Resume)
        wall = 9_000L
        elapsed = 4_000L
        log.signal(id, PlaySignal.End)
        assertEquals(3_000L, log.totals("one").activeMillis)
        assertTrue(log.totals("one").activeMillis >= 0L)
    }

    @Test
    fun reloadCreditsTimeUpToTheLastCheckpoint() {
        val file = tempLog()
        var wall = 1_000L
        var elapsed = 0L
        val clock = splitClock({ wall }, { elapsed })
        val first = PlayLog(clock, FilePlayLog(file)) { "session-1" }
        val id = first.start("one")
        wall = 31_000L
        elapsed = 30_000L
        first.checkpoint(id)
        assertEquals(listOf(PlayKind.Started), first.events().map { it.kind })
        wall = 120_000L
        elapsed = 90_000L
        val second = PlayLog(clock, FilePlayLog(file)) { "session-2" }
        assertEquals(30_000L, second.totals("one").activeMillis)
        assertEquals(31_000L, second.totals("one").lastPlayedMillis)
        assertEquals(listOf(PlayKind.Started, PlayKind.Ended), second.events().map { it.kind })
        assertEquals(30_000L, second.events().last().activeMillis)
    }

    @Test
    fun checkpointWhileAwayDoesNotIncreaseActiveTime() {
        var wall = 0L
        var elapsed = 0L
        val log = PlayLog(splitClock({ wall }, { elapsed }), MemoryPlaySink()) { "session-1" }
        val id = log.start("one")
        wall = 1_000L
        elapsed = 1_000L
        log.signal(id, PlaySignal.Background)
        wall = 9_000L
        elapsed = 9_000L
        log.checkpoint(id)
        log.signal(id, PlaySignal.End)
        assertEquals(1_000L, log.totals("one").activeMillis)
        assertEquals(1, log.events().count { it.kind == PlayKind.Backgrounded })
    }

    @Test
    fun recentlyPlayedPutsTheLatestFirst() {
        val order = recentlyPlayedIndices(listOf("a", "b", "c", "d")) { id ->
            when (id) {
                "b" -> 5L
                "a" -> 1L
                else -> null
            }
        }
        assertEquals(listOf(1, 0, 2, 3), order)
    }

    @Test
    fun aRemovedSessionLeavesTheTotalsAndLastPlayed() {
        val log = memoryLog()
        val kept = log.start("one")
        now = 2_000
        log.signal(kept, PlaySignal.End)
        now = 10_000
        val night = log.start("one")
        now = 64_000
        log.signal(night, PlaySignal.End)
        assertEquals(56_000L, log.totals("one").activeMillis)
        now = 70_000
        assertTrue(log.remove(night))
        assertEquals(2_000L, log.totals("one").activeMillis)
        // The removal is not a play, and the removed session's own stamps no longer count.
        assertEquals(2_000L, log.totals("one").lastPlayedMillis)
        assertEquals(setOf(night), log.removedSessions())
        val last = log.events().last()
        assertEquals(PlayKind.Removed, last.kind)
        assertEquals(night, last.sessionId)
        assertEquals("one", last.gameId)
        // Twice, or an id the log never had, does nothing.
        assertFalse(log.remove(night))
        assertFalse(log.remove("missing"))
        assertEquals(1, log.events().count { it.kind == PlayKind.Removed })
    }

    @Test
    fun aSessionRemovedWhileOpenNeverCounts() {
        val log = memoryLog()
        val id = log.start("one")
        now = 5_000
        log.signal(id, PlaySignal.Background)
        assertTrue(log.remove(id))
        assertEquals(0L, log.totals("one").activeMillis)
        log.signal(id, PlaySignal.Resume)
        now = 9_000
        assertEquals(0L, log.totals("one").activeMillis)
        log.signal(id, PlaySignal.End)
        assertEquals(0L, log.totals("one").activeMillis)
        assertNull(log.totals("one").lastPlayedMillis)
    }

    @Test
    fun aRemovalIsKeptThroughAReload() {
        val file = tempLog()
        val first = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-${nextId++}" }
        val kept = first.start("one")
        now = 3_000
        first.signal(kept, PlaySignal.End)
        val night = first.start("one")
        now = 50_000
        first.signal(night, PlaySignal.End)
        first.remove(night)
        now = 90_000
        val second = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-${nextId++}" }
        assertEquals(3_000L, second.totals("one").activeMillis)
        assertEquals(setOf(night), second.removedSessions())
        assertEquals(first.events(), second.events())
    }

    @Test
    fun aKindThisBuildDoesNotKnowIsSkippedAndTheRestReads() {
        val file = tempLog()
        val first = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-1" }
        val id = first.start("one")
        now = 4_000
        first.signal(id, PlaySignal.End)
        // A later build's fact, as an older build meets Removed.
        file.appendText("$PLAY_EVENT_SCHEMA\tsession-1\tone\tRenamed\t5000\t-\t0\n")
        assertNull(decodePlayEvent("$PLAY_EVENT_SCHEMA\tsession-1\tone\tRenamed\t5000\t-\t0"))
        val second = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-2" }
        assertEquals(4_000L, second.totals("one").activeMillis)
        assertEquals(listOf(PlayKind.Started, PlayKind.Ended), second.events().map { it.kind })
        // And Removed itself round-trips.
        val removed = PlayEvent("session-1", "one", PlayKind.Removed, 6_000L, null, 0L)
        assertEquals(removed, decodePlayEvent(encodePlayEvent(removed).trimEnd('\n')))
    }

    private fun memoryLog(): PlayLog = PlayLog(PlayClock { now }, MemoryPlaySink()) { "session-${nextId++}" }

    private fun splitClock(wall: () -> Long, elapsed: () -> Long): PlayClock = object : PlayClock {
        override fun wallNow(): Long = wall()
        override fun elapsedNow(): Long = elapsed()
    }

    private fun tempLog() = Files.createTempDirectory("play-log").resolve("events.log").toFile()
}

private class MemoryPlaySink : PlaySink {
    private val rows = mutableListOf<PlayEvent>()

    override fun append(event: PlayEvent) {
        rows.add(event)
    }

    override fun read(): List<PlayEvent> = rows.toList()

    private var checkpoint: PlayCheckpoint? = null

    override fun writeCheckpoint(checkpoint: PlayCheckpoint) {
        this.checkpoint = checkpoint
    }

    override fun readCheckpoint(): PlayCheckpoint? = checkpoint

    override fun clearCheckpoint(sessionId: String) {
        if (checkpoint?.sessionId == sessionId) checkpoint = null
    }
}
