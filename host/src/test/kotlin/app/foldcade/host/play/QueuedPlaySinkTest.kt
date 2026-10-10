package app.foldcade.host.play

import java.nio.file.Files
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuedPlaySinkTest {
    private var now = 0L

    @Test
    fun writesRunInOrderOffTheCallersThread() {
        val recording = RecordingSink()
        val sink = QueuedPlaySink(recording)
        val log = PlayLog(PlayClock { now }, sink) { "session-1" }
        now = 1_000
        val id = log.start("one")
        now = 2_000
        log.checkpoint(id)
        log.signal(id, PlaySignal.Background)
        now = 3_000
        log.signal(id, PlaySignal.End)
        sink.read()
        assertEquals(
            listOf("append Started", "checkpoint", "append Backgrounded", "clear", "append Ended", "clear"),
            recording.calls,
        )
        assertTrue(recording.threads.isNotEmpty())
        assertFalse(Thread.currentThread().name in recording.threads)
    }

    @Test
    fun aReloadSeesWritesStillQueuedByTheLastLog() {
        val file = Files.createTempDirectory("queued-play").resolve("events.log").toFile()
        val first = PlayLog(PlayClock { now }, QueuedPlaySink(FilePlayLog(file))) { "session-1" }
        now = 1_000
        val id = first.start("one")
        now = 4_000
        first.signal(id, PlaySignal.End)
        val second = PlayLog(PlayClock { now }, QueuedPlaySink(FilePlayLog(file))) { "session-2" }
        assertEquals(listOf(PlayKind.Started, PlayKind.Ended), second.events().map { it.kind })
        assertEquals(3_000L, second.totals("one").activeMillis)
    }

    @Test
    fun signalSaysWhetherItStoredAnEvent() {
        val log = PlayLog(PlayClock { now }, QueuedPlaySink(RecordingSink())) { "session-1" }
        val id = log.start("one")
        assertTrue(log.signal(id, PlaySignal.Background))
        assertFalse(log.signal(id, PlaySignal.Sleep))
        assertFalse(log.signal("unknown", PlaySignal.End))
        assertTrue(log.signal(id, PlaySignal.End))
    }

    private class RecordingSink : PlaySink {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val threads: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

        override fun append(event: PlayEvent) = record("append ${event.kind}")

        override fun read(): List<PlayEvent> = emptyList()

        override fun writeCheckpoint(checkpoint: PlayCheckpoint) = record("checkpoint")

        override fun readCheckpoint(): PlayCheckpoint? = null

        override fun clearCheckpoint(sessionId: String) = record("clear")

        private fun record(call: String) {
            calls += call
            threads += Thread.currentThread().name
        }
    }
}
