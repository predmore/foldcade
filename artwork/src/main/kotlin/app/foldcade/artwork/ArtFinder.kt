package app.foldcade.artwork

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Asks each [ArtSource] in order and keeps the first cover found.
 *
 * Results are remembered in [index], hits and misses both, so a game is not
 * looked up again on the next launch. A miss is asked again after
 * [MISS_MAX_AGE_MS], or at once after [forgetMisses]. A source that throws
 * (offline, server error) does not record a miss. At most [PARALLEL] lookups
 * run at once, two asks for one key share a lookup, and nothing is sent while
 * [enabled] is false.
 */
class ArtFinder(
    private val sources: () -> List<ArtSource>,
    private val index: ArtIndex,
    private val enabled: () -> Boolean = { true },
) {
    private val gate = Semaphore(PARALLEL)
    private val perKey = ConcurrentHashMap<String, Mutex>()

    /** What is already known for [query], with no lookup. */
    fun cached(query: ArtQuery): ArtSet? = if (enabled()) index.hit(query.key) else null

    suspend fun find(query: ArtQuery): ArtSet? {
        if (!enabled()) return null
        when (val known = index.lookup(query.key)) {
            is ArtIndex.Known.Hit -> return known.set
            ArtIndex.Known.Miss -> return null
            null -> Unit
        }
        // The hero and its tile ask at once. The second waits, then reads what the first found.
        return perKey.computeIfAbsent(query.key) { Mutex() }.withLock {
            when (val known = index.lookup(query.key)) {
                is ArtIndex.Known.Hit -> return@withLock known.set
                ArtIndex.Known.Miss -> return@withLock null
                null -> gate.withPermit { ask(query) }
            }
        }
    }

    private suspend fun ask(query: ArtQuery): ArtSet? {
        var failed = false
        for (source in sources()) {
            if (!source.handles(query)) continue
            val found = try {
                source.find(query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                null
            }
            if (found != null) {
                index.put(query.key, found)
                return found
            }
        }
        if (!failed) index.put(query.key, null)
        return null
    }

    /** Misses are asked again, for when a source is added. */
    fun forgetMisses() = index.forgetMisses()

    companion object {
        const val PARALLEL: Int = 3
        const val MISS_MAX_AGE_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}

/**
 * Art found per tile key, kept in [file] across launches. Each change is one
 * appended line, and the file is rewritten when old lines outnumber live ones.
 */
class ArtIndex(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface Known {
        data class Hit(val set: ArtSet) : Known
        data object Miss : Known
    }

    private class Entry(val set: ArtSet?, val savedAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()
    private var lines = 0

    init {
        if (file.isFile) {
            file.forEachLine { line ->
                lines++
                val parts = line.split('\t')
                if (parts.size < 6) return@forEachLine
                val key = unescape(parts[0])
                val savedAt = parts[1].toLongOrNull() ?: return@forEachLine
                val source = unescape(parts[2])
                entries[key] = if (source.isEmpty()) {
                    Entry(null, savedAt)
                } else {
                    Entry(
                        ArtSet(
                            source = source,
                            cover = unescape(parts[3]),
                            square = unescape(parts[4]).ifEmpty { null },
                            background = unescape(parts[5]).ifEmpty { null },
                        ),
                        savedAt,
                    )
                }
            }
        }
    }

    fun hit(key: String): ArtSet? = entries[key]?.set

    fun lookup(key: String): Known? {
        val entry = entries[key] ?: return null
        val set = entry.set
        if (set != null) return Known.Hit(set)
        return if (clock() - entry.savedAt < ArtFinder.MISS_MAX_AGE_MS) Known.Miss else null
    }

    @Synchronized
    fun put(key: String, set: ArtSet?) {
        val entry = Entry(set, clock())
        entries[key] = entry
        file.parentFile?.mkdirs()
        file.appendText(lineOf(key, entry))
        lines++
        if (lines > COMPACT_FLOOR && lines > entries.size * 2) rewrite()
    }

    @Synchronized
    fun forgetMisses() {
        entries.entries.removeIf { it.value.set == null }
        rewrite()
    }

    private fun rewrite() {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(entries.entries.joinToString("") { (key, entry) -> lineOf(key, entry) })
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        lines = entries.size
    }

    private fun lineOf(key: String, entry: Entry): String {
        val set = entry.set
        return listOf(
            escape(key),
            entry.savedAt.toString(),
            escape(set?.source.orEmpty()),
            escape(set?.cover.orEmpty()),
            escape(set?.square.orEmpty()),
            escape(set?.background.orEmpty()),
        ).joinToString("\t", postfix = "\n")
    }

    private companion object {
        const val COMPACT_FLOOR = 64

        fun escape(text: String): String =
            text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

        fun unescape(text: String): String {
            val out = StringBuilder(text.length)
            var index = 0
            while (index < text.length) {
                val char = text[index]
                if (char == '\\' && index + 1 < text.length) {
                    when (text[index + 1]) {
                        't' -> out.append('\t')
                        'n' -> out.append('\n')
                        else -> out.append(text[index + 1])
                    }
                    index += 2
                } else {
                    out.append(char)
                    index++
                }
            }
            return out.toString()
        }
    }
}
