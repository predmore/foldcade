package app.foldcade.host.play

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

/** Version of one [PlayNames] line. */
const val PLAY_NAME_SCHEMA = 1

/**
 * The name each game had when it was last played, kept beside the play log.
 *
 * A [PlayEvent] holds only the game id, which can be a storage document URI or
 * a server's number. Once the game leaves every catalog, its history still
 * needs a name. This file is not part of [PLAY_EVENT_SCHEMA], so the event
 * stream and the logs already written stay as they are.
 *
 * Each line is the schema, the game id, and the name. A later line for the
 * same id wins. An unknown line is skipped, and so is a last line with no line
 * break, which a write cut short left; the next write cuts it off first.
 * [remember] updates memory at once and appends on [writer], so a
 * lifecycle callback does not wait on the disk. Only a changed name is written.
 */
class PlayNames(
    private val file: File,
    private val writer: ExecutorService = PlayLogWriter,
) {
    private val names = ConcurrentHashMap<String, String>()

    /** Bytes up to the last line break, when a line cut short follows them. Only [writer] uses it. */
    private var whole: Long? = null

    init {
        names.putAll(writer.submit(Callable { read() }).get())
    }

    fun name(gameId: String): String? = names[gameId]

    /**
     * Keeps [name] for [gameId]. A blank name, the id itself, or a URI is not
     * a name and is ignored. Returns true when this stored a new name.
     */
    fun remember(gameId: String, name: String): Boolean {
        val clean = name.trim()
        if (gameId.isBlank() || !isPlayName(gameId, clean)) return false
        if (names.put(gameId, clean) == clean) return false
        writer.execute { append(gameId, clean) }
        return true
    }

    private fun read(): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val bytes = file.readBytes()
        val end = bytes.lastIndexOf('\n'.code.toByte()) + 1
        whole = if (end < bytes.size) end.toLong() else null
        val read = HashMap<String, String>()
        String(bytes, 0, end, Charsets.UTF_8).lineSequence().forEach { line ->
            decodePlayName(line)?.let { (id, name) -> read[id] = name }
        }
        return read
    }

    private fun append(gameId: String, name: String) {
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).use { out ->
            whole?.let { out.channel.truncate(it) }
            out.write(encodePlayName(gameId, name).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        whole = null
    }
}

private fun isPlayName(gameId: String, name: String): Boolean =
    name.isNotEmpty() && name != gameId && !URI_SCHEME.containsMatchIn(name)

/** A name that starts like `content://` is an id that leaked through, not a name. */
private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

internal fun encodePlayName(gameId: String, name: String): String =
    listOf(PLAY_NAME_SCHEMA.toString(), escape(gameId), escape(name)).joinToString("\t") + "\n"

internal fun decodePlayName(line: String): Pair<String, String>? {
    if (line.isBlank()) return null
    val parts = splitFields(line)
    if (parts.size != 3) return null
    if (parts[0] != PLAY_NAME_SCHEMA.toString()) return null
    val gameId = parts[1]
    val name = parts[2].trim()
    if (gameId.isBlank() || !isPlayName(gameId, name)) return null
    return gameId to name
}
