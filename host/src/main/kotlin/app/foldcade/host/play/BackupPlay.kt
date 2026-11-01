package app.foldcade.host.play

import java.io.File

/**
 * Startup restore has no live log yet. Run the existing merge into a snapshot that the caller
 * writes atomically: a process stop cannot leave half of a newly imported session present,
 * which an ordinary retry would rightly regard as a session already on this device.
 */
fun mergedBackupPlay(file: File, incoming: List<PlayEvent>): ByteArray {
    val stored = FilePlayLog(file)
    val events = mutableListOf<PlayEvent>()
    val sink = object : PlaySink {
        override fun read(): List<PlayEvent> = stored.read().also { events.addAll(it) }
        override fun append(event: PlayEvent) { events += event }
        override fun readCheckpoint(): PlayCheckpoint? = stored.readCheckpoint()
        override fun writeCheckpoint(checkpoint: PlayCheckpoint) = Unit
        override fun clearCheckpoint(sessionId: String) = Unit
    }
    PlayLog(PlayClock { System.currentTimeMillis() }, sink).merge(incoming)
    return events.joinToString("") { encodePlayEvent(it) }.encodeToByteArray()
}
