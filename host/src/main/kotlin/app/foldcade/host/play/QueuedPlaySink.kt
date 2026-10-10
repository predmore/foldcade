package app.foldcade.host.play

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Moves [PlaySink] writes off the caller's thread. [PlayLog] is called from
 * activity lifecycle callbacks, and [FilePlayLog] syncs every append.
 *
 * Writes run in order on one [writer] thread. [read] and [readCheckpoint] run
 * on that thread too and wait, so they see every write queued before them,
 * including writes from another sink that shares [writer]. The default writer
 * is shared by every sink in the process.
 *
 * A write still queued when the process dies is lost. [PlayLog] already closes
 * a session at its last stored event, so that costs at most the last fact.
 */
class QueuedPlaySink(
    private val delegate: PlaySink,
    private val writer: ExecutorService = SharedWriter,
) : PlaySink {
    override fun append(event: PlayEvent) {
        writer.execute { delegate.append(event) }
    }

    override fun read(): List<PlayEvent> = writer.submit(Callable { delegate.read() }).get()

    override fun writeCheckpoint(checkpoint: PlayCheckpoint) {
        writer.execute { delegate.writeCheckpoint(checkpoint) }
    }

    override fun readCheckpoint(): PlayCheckpoint? = writer.submit(Callable { delegate.readCheckpoint() }).get()

    override fun clearCheckpoint(sessionId: String) {
        writer.execute { delegate.clearCheckpoint(sessionId) }
    }

    private companion object {
        val SharedWriter: ExecutorService = Executors.newSingleThreadExecutor { task ->
            Thread(task, "foldcade-play-log").apply { isDaemon = true }
        }
    }
}
