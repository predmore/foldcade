package app.foldcade.host.play

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Version of a play history export. A newer one is refused, not half read. */
const val PLAY_ARCHIVE_FORMAT = 1

/** What an export holds: the play log as stored, the kept names, and each played game's kept cover by [playArtName]. */
data class PlayExport(
    val events: List<PlayEvent>,
    val names: Map<String, String>,
    val art: Map<String, ByteArray>,
)

/** The file is not a Foldcade play history, or one from a newer Foldcade. */
class PlayArchiveException(message: String) : IOException(message)

/**
 * A play history export: one zip a player keeps anywhere and brings back on any device.
 *
 * It holds `foldcade-play.txt`, which names the format, then `events.log` and
 * `names.log` in the lines [FilePlayLog] and [PlayNames] already write, then each
 * kept cover as `art/<name>`. Reading takes only those entries, by those names,
 * and only so many bytes of each, so a stranger's zip cannot write anywhere or
 * fill the disk. A line it cannot read is skipped, as the log itself does.
 */
object PlayArchive {
    private const val MARKER = "foldcade-play.txt"
    private const val EVENTS = "events.log"
    private const val NAMES = "names.log"
    private const val ART = "art/"
    private const val HEADER = "foldcade-play-history"

    /** Far past any real history: ten years of play is a few megabytes. */
    private const val MAX_LOG_BYTES = 32L * 1024 * 1024

    /** A kept cover is a 384-pixel WebP, tens of kilobytes. */
    private const val MAX_ART_BYTES = 1L * 1024 * 1024

    /** Every cover together, held in memory until they are written: covers past it are left out. */
    private const val MAX_ALL_ART_BYTES = 48L * 1024 * 1024

    fun write(out: OutputStream, events: List<PlayEvent>, names: Map<String, String>, art: List<File>) {
        ZipOutputStream(out).use { zip ->
            zip.entry(MARKER) { it.write("$HEADER $PLAY_ARCHIVE_FORMAT\n".toByteArray(Charsets.UTF_8)) }
            zip.entry(EVENTS) { stream -> events.forEach { stream.write(encodePlayEvent(it).toByteArray(Charsets.UTF_8)) } }
            zip.entry(NAMES) { stream ->
                names.entries.sortedBy { it.key }.forEach { (id, name) -> stream.write(encodePlayName(id, name).toByteArray(Charsets.UTF_8)) }
            }
            art.filter { it.isFile && isPlayArtName(it.name) && it.length() in 1..MAX_ART_BYTES }
                .sortedBy { it.name }
                .forEach { file -> zip.entry(ART + file.name) { stream -> file.inputStream().use { it.copyTo(stream) } } }
        }
    }

    fun read(input: InputStream): PlayExport {
        var format: Int? = null
        var events: List<PlayEvent> = emptyList()
        val names = HashMap<String, String>()
        val art = LinkedHashMap<String, ByteArray>()
        var artBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                when {
                    name == MARKER -> format = readFormat(String(zip.bounded(1024), Charsets.UTF_8))
                    name == EVENTS -> events = String(zip.bounded(MAX_LOG_BYTES), Charsets.UTF_8).lineSequence().mapNotNull(::decodePlayEvent).toList()
                    name == NAMES -> String(zip.bounded(MAX_LOG_BYTES), Charsets.UTF_8).lineSequence().forEach { line ->
                        decodePlayName(line)?.let { (id, kept) -> names[id] = kept }
                    }
                    name.startsWith(ART) && isPlayArtName(name.removePrefix(ART)) && artBytes < MAX_ALL_ART_BYTES -> {
                        val bytes = zip.boundedOrNull(minOf(MAX_ART_BYTES, MAX_ALL_ART_BYTES - artBytes))
                        if (bytes != null && bytes.isNotEmpty()) {
                            art[name.removePrefix(ART)] = bytes
                            artBytes += bytes.size
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val read = format ?: throw PlayArchiveException("not a Foldcade play history")
        if (read > PLAY_ARCHIVE_FORMAT) throw PlayArchiveException("play history from a newer Foldcade")
        return PlayExport(events, names, art)
    }

    private fun readFormat(text: String): Int? {
        val parts = text.lineSequence().firstOrNull()?.trim()?.split(' ') ?: return null
        if (parts.size != 2 || parts[0] != HEADER) return null
        return parts[1].toIntOrNull()?.takeIf { it >= 1 }
    }

    private inline fun ZipOutputStream.entry(name: String, write: (OutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        write(this)
        closeEntry()
    }

    /** The entry's bytes, refusing the file once it passes [limit]. */
    private fun ZipInputStream.bounded(limit: Long): ByteArray =
        boundedOrNull(limit) ?: throw PlayArchiveException("play history entry is too large")

    private fun ZipInputStream.boundedOrNull(limit: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}

/**
 * The file a played game's kept cover is stored as, here and in an export: a hash of
 * its id, which can be a document URI, so the name is short and safe anywhere.
 */
fun playArtName(gameId: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(gameId.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) } + ".webp"
}

/** True for a name [playArtName] makes, and nothing else: no path, no other file. */
fun isPlayArtName(name: String): Boolean = PLAY_ART_NAME.matches(name)

private val PLAY_ART_NAME = Regex("^[0-9a-f]{40}\\.webp$")
