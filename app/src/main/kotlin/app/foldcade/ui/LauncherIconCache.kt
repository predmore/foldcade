package app.foldcade.ui

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class LauncherIconKey(val packageName: String, val version: Long, val updatedAt: Long, val size: Int)

/** Shared by both panels. Clearing never waits for a rasterization, and old loads cannot refill it. */
internal class LauncherIconCache<T : Any>(private val capacity: Int) {
    private val entries = LinkedHashMap<LauncherIconKey, T>(capacity, 0.75f, true)
    private val loading = Mutex()
    private var generation = 0L

    init { require(capacity > 0) }

    suspend fun getOrLoad(key: LauncherIconKey, load: () -> T?): T? = loading.withLock {
        val ticket = synchronized(entries) {
            entries[key]?.let { return@withLock it }
            generation
        }
        val value = load() ?: return@withLock null
        synchronized(entries) {
            if (ticket == generation) {
                entries[key] = value
                while (entries.size > capacity) entries.remove(entries.keys.first())
            }
        }
        value
    }

    fun clear() = synchronized(entries) {
        generation++
        entries.clear()
    }
}
