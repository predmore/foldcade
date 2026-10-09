package app.foldcade.host.play

import java.io.File
import java.io.FileOutputStream

/**
 * Append-only play history. Each line is one [PlayEvent] at [PLAY_EVENT_SCHEMA].
 * A torn last line is skipped. The directory is ordinary app-private storage.
 * The caller passes a file under `filesDir`, not `noBackupFilesDir`: a play
 * session holds no secret, so backup rules must not exclude it.
 */
class FilePlayLog(private val file: File) : PlaySink {
    override fun append(event: PlayEvent) {
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).use { out ->
            out.write(encodePlayEvent(event).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
    }

    override fun read(): List<PlayEvent> {
        if (!file.isFile) return emptyList()
        return file.readLines(Charsets.UTF_8).mapNotNull { decodePlayEvent(it) }
    }

    override fun writeCheckpoint(checkpoint: PlayCheckpoint) {
        val target = checkpointFile()
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(encodeCheckpoint(checkpoint).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    override fun readCheckpoint(): PlayCheckpoint? {
        val target = checkpointFile()
        if (!target.isFile) return null
        return target.readLines(Charsets.UTF_8).firstNotNullOfOrNull { decodeCheckpoint(it) }
    }

    override fun clearCheckpoint(sessionId: String) {
        val target = checkpointFile()
        val stored = readCheckpoint() ?: return
        if (stored.sessionId == sessionId) target.delete()
    }

    private fun checkpointFile(): File = File(file.parentFile, "${file.name}.checkpoint")
}

internal fun encodePlayEvent(event: PlayEvent): String =
    listOf(
        PLAY_EVENT_SCHEMA.toString(),
        escape(event.sessionId),
        escape(event.gameId),
        event.kind.name,
        event.atMillis.toString(),
        event.away?.name ?: "-",
        event.activeMillis.toString(),
    ).joinToString("\t") + "\n"

internal fun decodePlayEvent(line: String): PlayEvent? {
    if (line.isBlank()) return null
    val parts = splitFields(line)
    if (parts.size != 7) return null
    if (parts[0] != PLAY_EVENT_SCHEMA.toString()) return null
    val kind = runCatching { PlayKind.valueOf(parts[3]) }.getOrNull() ?: return null
    val at = parts[4].toLongOrNull() ?: return null
    val away = when (parts[5]) {
        "-" -> null
        else -> runCatching { PlayAway.valueOf(parts[5]) }.getOrNull() ?: return null
    }
    if (kind == PlayKind.Backgrounded && away == null) return null
    if (kind != PlayKind.Backgrounded && away != null) return null
    val active = parts[6].toLongOrNull() ?: return null
    if (active < 0L || at < 0L) return null
    val sessionId = parts[1]
    val gameId = parts[2]
    if (sessionId.isBlank() || gameId.isBlank()) return null
    return PlayEvent(sessionId, gameId, kind, at, away, active)
}

internal fun encodeCheckpoint(checkpoint: PlayCheckpoint): String =
    listOf(
        escape(checkpoint.sessionId),
        checkpoint.activeMillis.toString(),
        checkpoint.atMillis.toString(),
    ).joinToString("\t") + "\n"

internal fun decodeCheckpoint(line: String): PlayCheckpoint? {
    if (line.isBlank()) return null
    val parts = splitFields(line)
    if (parts.size != 3) return null
    val active = parts[1].toLongOrNull() ?: return null
    val at = parts[2].toLongOrNull() ?: return null
    if (active < 0L || at < 0L || parts[0].isBlank()) return null
    return PlayCheckpoint(parts[0], active, at)
}

private fun escape(value: String): String = buildString(value.length) {
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(ch)
        }
    }
}

private fun splitFields(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    for (ch in line) {
        if (escaped) {
            when (ch) {
                '\\' -> current.append('\\')
                't' -> current.append('\t')
                'n' -> current.append('\n')
                'r' -> current.append('\r')
                else -> current.append(ch)
            }
            escaped = false
        } else if (ch == '\\') {
            escaped = true
        } else if (ch == '\t') {
            fields.add(current.toString())
            current.clear()
        } else {
            current.append(ch)
        }
    }
    if (escaped) return emptyList()
    fields.add(current.toString())
    return fields
}
