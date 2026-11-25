package app.foldcade

/** A hold survives until Foldcade can see the stop or the player confirms an unobservable close. */
internal class RepairPlayerHolds(
    private val store: SessionStore,
    private val bootAtMillis: () -> Long,
    private val packageStopped: (String) -> Boolean = { false },
    private val exitTimestamps: (String) -> List<Long>,
) {
    fun mayHold(packageName: String, fallbackAtMillis: Long?, hasRunningTask: Boolean = false): Boolean {
        if (hasRunningTask) return !packageStopped(packageName)
        val started = startedAt(packageName, fallbackAtMillis) ?: return false
        if (packageStopped(packageName)) return false
        val exits = readableExits(packageName)
        val current = startedAt(packageName, fallbackAtMillis) ?: return false
        return current != started || exits == null || !playerDiedAfterStart(started, exits)
    }

    /** Called on Main after the player explicitly confirms that they quit. */
    fun confirmClosed(packageName: String, fallbackAtMillis: Long?, confirmedAtMillis: Long, hasRunningTask: Boolean) {
        if (hasRunningTask) return
        val started = startedAt(packageName, fallbackAtMillis) ?: return
        if (started > confirmedAtMillis || readableExits(packageName) != null) return
        if (startedAt(packageName, fallbackAtMillis) == started) store.noteRepairPlayerClosed(packageName, confirmedAtMillis)
    }

    private fun startedAt(packageName: String, fallbackAtMillis: Long?): Long? {
        val started = store.repairPlayerOpenedAt(packageName) ?: fallbackAtMillis ?: return null
        val closed = store.repairPlayerClosedAt(packageName)
        return started.takeIf { it >= bootAtMillis() && (closed == null || closed < it) }
    }

    private fun readableExits(packageName: String): List<Long>? =
        try { exitTimestamps(packageName) } catch (_: SecurityException) { null }
}
