package app.foldcade.ui

internal data class LauncherIconKey(val packageName: String, val version: Long, val updatedAt: Long, val size: Int)

/** Shared by both panels. Clearing never waits for a rasterization, and old loads cannot refill it. */
internal class LauncherIconCache<T : Any>(private val capacity: Int) {
    private val entries = LinkedHashMap<LauncherIconKey, T>(capacity, 0.75f, true)
    private var generation = 0L

    init { require(capacity > 0) }

    fun getOrLoad(key: LauncherIconKey, load: () -> T?): T? {
        val ticket = synchronized(entries) {
            entries[key]?.let { return it }
            generation
        }
        val value = load() ?: return null
        synchronized(entries) {
            if (ticket == generation) {
                entries[key] = value
                while (entries.size > capacity) entries.remove(entries.keys.first())
            }
        }
        return value
    }

    fun clear() = synchronized(entries) {
        generation++
        entries.clear()
    }
}
