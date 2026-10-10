package app.foldcade.artwork

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Asks each [ArtSource] in order and keeps the first cover found. For the
 * focused game, [findScene] asks every source for wide art, a logo, and a
 * title screen, and keeps the best of each.
 *
 * Results are remembered in [index] and [scenes], hits and misses both, so a
 * game is not looked up again on the next launch. A miss is asked again after
 * [MISS_MAX_AGE_MS], or at once after [forgetMisses]. A source that throws
 * (offline, server error) does not record a miss. At most [PARALLEL] lookups
 * run at once, two asks for one key share a lookup, and nothing is sent while
 * [enabled] is false.
 */
class ArtFinder(
    private val sources: () -> List<ArtSource>,
    private val index: ArtIndex,
    private val scenes: ArtIndex = ArtIndex(File(index.file.parentFile, "scenes-${index.file.name}")),
    private val enabled: () -> Boolean = { true },
) {
    private val gate = Semaphore(PARALLEL)
    private val perKey = ConcurrentHashMap<String, Mutex>()

    /** What is already known for [query], with no lookup. */
    fun cached(query: ArtQuery): ArtSet? = if (enabled()) index.hit(query.key)?.let(::artSetOf) else null

    suspend fun find(query: ArtQuery): ArtSet? = lookUp(index, query) {
        var failed = false
        for (source in sources()) {
            if (!source.handles(query)) continue
            val found = asking(source) { find(query) }
            if (found.failed) failed = true
            val set = found.value
            if (set != null) return@lookUp Answer(fieldsOf(set), failed = false)
        }
        Answer(null, failed)
    }?.let(::artSetOf)

    /** The scene already known for [query], with no lookup. */
    fun cachedScene(query: ArtQuery): ArtScene? = if (enabled()) scenes.hit(query.key)?.let(::sceneOf) else null

    /**
     * Wide art, a logo, and a title screen for [query]. Each part comes from the
     * first source that has it. Asking stops once there is wide art and a logo.
     */
    suspend fun findScene(query: ArtQuery): ArtScene? = lookUp(scenes, query) {
        var scene = ArtScene()
        var failed = false
        for (source in sources()) {
            if (scene.complete) break
            if (!source.handles(query)) continue
            val found = asking(source) { scene(query) }
            if (found.failed) failed = true
            scene = scene.or(found.value)
        }
        // A part found before an outage is kept. Only a scene with nothing in it waits to be asked again.
        if (scene.isEmpty) Answer(null, failed) else Answer(fieldsOf(scene), failed = false)
    }?.let(::sceneOf)

    /** Misses are asked again, for when a source is added. */
    fun forgetMisses() {
        index.forgetMisses()
        scenes.forgetMisses()
    }

    private class Answer(val fields: List<String>?, val failed: Boolean)

    private class Asked<T>(val value: T?, val failed: Boolean)

    private suspend fun <T> asking(source: ArtSource, ask: suspend ArtSource.() -> T?): Asked<T> = try {
        Asked(source.ask(), failed = false)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Asked(null, failed = true)
    }

    private suspend fun lookUp(store: ArtIndex, query: ArtQuery, ask: suspend () -> Answer): List<String>? {
        if (!enabled()) return null
        when (val known = store.lookup(query.key)) {
            is ArtIndex.Known.Hit -> return known.fields
            ArtIndex.Known.Miss -> return null
            null -> Unit
        }
        // The hero and its tile ask at once. The second waits, then reads what the first found.
        return perKey.computeIfAbsent("${store.file.name}\u0000${query.key}") { Mutex() }.withLock {
            when (val known = store.lookup(query.key)) {
                is ArtIndex.Known.Hit -> known.fields
                ArtIndex.Known.Miss -> null
                null -> gate.withPermit {
                    val answer = ask()
                    if (answer.fields != null || !answer.failed) store.put(query.key, answer.fields)
                    answer.fields
                }
            }
        }
    }

    private fun fieldsOf(set: ArtSet): List<String> =
        listOf(set.source, set.cover, set.square.orEmpty(), set.background.orEmpty())

    private fun artSetOf(fields: List<String>): ArtSet? {
        if (fields.size < 4 || fields[1].isEmpty()) return null
        return ArtSet(fields[0], fields[1], fields[2].ifEmpty { null }, fields[3].ifEmpty { null })
    }

    private fun fieldsOf(scene: ArtScene): List<String> =
        listOf(scene.background.orEmpty(), scene.logo.orEmpty(), scene.screen.orEmpty())

    private fun sceneOf(fields: List<String>): ArtScene? {
        if (fields.size < 3) return null
        return ArtScene(fields[0].ifEmpty { null }, fields[1].ifEmpty { null }, fields[2].ifEmpty { null })
    }

    companion object {
        const val PARALLEL: Int = 3
        const val MISS_MAX_AGE_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}

/**
 * What was found per tile key, kept in [file] across launches: a list of
 * fields for a hit, nothing for a miss. Each change is one appended line, and
 * the file is rewritten when old lines outnumber live ones.
 */
class ArtIndex(
    val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface Known {
        data class Hit(val fields: List<String>) : Known
        data object Miss : Known
    }

    private class Entry(val fields: List<String>?, val savedAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()
    private var lines = 0

    init {
        if (file.isFile) {
            file.forEachLine { line ->
                lines++
                val parts = line.split('\t')
                if (parts.size < 3) return@forEachLine
                val key = unescape(parts[0])
                val savedAt = parts[1].toLongOrNull() ?: return@forEachLine
                entries[key] = when (parts[2]) {
                    HIT -> Entry(parts.drop(3).map(::unescape), savedAt)
                    MISS -> Entry(null, savedAt)
                    // A line from an older layout is dropped and looked up again.
                    else -> return@forEachLine
                }
            }
        }
    }

    fun hit(key: String): List<String>? = entries[key]?.fields

    fun lookup(key: String): Known? {
        val entry = entries[key] ?: return null
        val fields = entry.fields
        if (fields != null) return Known.Hit(fields)
        return if (clock() - entry.savedAt < ArtFinder.MISS_MAX_AGE_MS) Known.Miss else null
    }

    @Synchronized
    fun put(key: String, fields: List<String>?) {
        val entry = Entry(fields, clock())
        entries[key] = entry
        file.parentFile?.mkdirs()
        file.appendText(lineOf(key, entry))
        lines++
        if (lines > COMPACT_FLOOR && lines > entries.size * 2) rewrite()
    }

    @Synchronized
    fun forgetMisses() {
        entries.entries.removeIf { it.value.fields == null }
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
        val fields = entry.fields
        val head = listOf(escape(key), entry.savedAt.toString(), if (fields == null) MISS else HIT)
        return (head + fields.orEmpty().map(::escape)).joinToString("\t", postfix = "\n")
    }

    private companion object {
        const val COMPACT_FLOOR = 64
        const val HIT = "h"
        const val MISS = "m"

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
