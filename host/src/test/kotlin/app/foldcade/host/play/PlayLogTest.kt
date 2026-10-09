package app.foldcade.host.play

import java.nio.file.Files
import org.junit.Assert.assertEquals
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

    private fun memoryLog(): PlayLog = PlayLog(PlayClock { now }, MemoryPlaySink()) { "session-${nextId++}" }

    private fun tempLog() = Files.createTempDirectory("play-log").resolve("events.log").toFile()
}

private class MemoryPlaySink : PlaySink {
    private val rows = mutableListOf<PlayEvent>()

    override fun append(event: PlayEvent) {
        rows.add(event)
    }

    override fun read(): List<PlayEvent> = rows.toList()
}
