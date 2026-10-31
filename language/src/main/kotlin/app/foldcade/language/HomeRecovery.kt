package app.foldcade.language

const val UNDO_MILLIS = 8_000L
const val DELETE_HOLD_MILLIS = 800L

/** The before/after delta of one user removal, kept only while no later user edit is saved. */
data class HomeUndo(
    val board: HomeBoard,
    val verb: String,
    val started: Long,
    val after: HomeBoard = board,
    val pausedAt: Long? = null,
) {
    fun available(now: Long): Boolean = pausedAt != null || (now >= started && now - started < UNDO_MILLIS)
    fun pause(now: Long): HomeUndo = if (pausedAt == null) copy(pausedAt = now) else this
    fun resume(now: Long): HomeUndo = pausedAt?.let {
        copy(started = started + (now - it).coerceAtLeast(0), pausedAt = null)
    } ?: this
    fun afterEdit(before: HomeBoard, after: HomeBoard): HomeUndo? = if (before == after) this else null

    /** Include folder tiles removed by cleanup, without recording unrelated catalog arrivals. */
    fun withFolderCleanup(current: HomeBoard): HomeUndo {
        val emptied = board.folders.values.filter { folder ->
            folder.system && folder.slots.any { it != null } &&
                after.folders[folder.id]?.slots?.all { it == null } == true &&
                folder.id in after.slots && folder.id !in current.slots
        }.map { it.id }.toSet()
        if (emptied.isEmpty()) return this
        return copy(after = after.copy(
            slots = after.slots.map { if (it in emptied) null else it },
            folders = after.folders - emptied.filter { it !in current.folders }.toSet(),
            hiddenSlots = after.hiddenSlots + current.hiddenSlots.filterKeys { it in emptied },
        ))
    }

    /** Catalog arrivals keep their slots; only fields changed by the removal are reversed. */
    /** The same Undo with copies folded since into their tiles, so it reverses the board as it is now. */
    fun adopt(sameGames: Map<String, String>): HomeUndo =
        copy(board = adoptSameGames(board, sameGames), after = adoptSameGames(after, sameGames))

    fun restore(current: HomeBoard): HomeBoard {
        val folders = current.folders.toMutableMap()
        for (id in board.folders.keys + after.folders.keys) {
            val before = board.folders[id]
            val removed = after.folders[id]
            if (before == removed) continue
            val live = folders[id]
            when {
                before == null -> if (live == removed) folders.remove(id)
                removed == null -> if (live == null) folders[id] = before
                live == null -> folders[id] = before
                else -> folders[id] = live.copy(
                    name = reverseValue(before.name, removed.name, live.name),
                    namedByUser = reverseValue(before.namedByUser, removed.namedByUser, live.namedByUser),
                    mark = reverseValue(before.mark, removed.mark, live.mark),
                    slots = reverseSlots(before.slots, removed.slots, live.slots),
                )
            }
        }
        return current.copy(
            slots = reverseSlots(board.slots, after.slots, current.slots),
            folders = folders,
            hidden = reverseSet(board.hidden, after.hidden, current.hidden),
            retired = reverseSet(board.retired, after.retired, current.retired),
            offHome = reverseSet(board.offHome, after.offHome, current.offHome),
            hiddenSlots = reverseMap(board.hiddenSlots, after.hiddenSlots, current.hiddenSlots),
            openFolderId = board.openFolderId,
            allOpen = board.allOpen,
            foldersOpen = board.foldersOpen,
            editing = false,
            hold = null,
        )
    }
}

private fun <T> reverseValue(before: T, after: T, current: T): T =
    if (before != after && current == after) before else current

private fun <T> reverseSet(before: Set<T>, after: Set<T>, current: Set<T>): Set<T> =
    (current - (after - before)) + (before - after)

private fun <K, V> reverseMap(before: Map<K, V>, after: Map<K, V>, current: Map<K, V>): Map<K, V> {
    val restored = current.toMutableMap()
    for (key in before.keys + after.keys) {
        if (before[key] == after[key] || current[key] != after[key]) continue
        val value = before[key]
        if (value == null) restored.remove(key) else restored[key] = value
    }
    return restored
}

private fun reverseSlots(before: List<String?>, after: List<String?>, current: List<String?>): List<String?> {
    val restored = current.toMutableList()
    while (restored.size < before.size) restored += null
    val changed = (0 until maxOf(before.size, after.size)).filter { before.getOrNull(it) != after.getOrNull(it) }
    // Release moved tiles first, so reversing Tidy never loses a tile still in its compacted slot.
    for (index in changed) {
        if (index < restored.size && restored[index] == after.getOrNull(index)) restored[index] = null
    }
    for (index in changed) {
        val previous = before.getOrNull(index) ?: continue
        if (previous in restored) continue
        if (restored.getOrNull(index) == null) {
            while (restored.size <= index) restored += null
            restored[index] = previous
        } else {
            // A new catalog tile has taken this gap. Keep it there and use the first free slot.
            val free = restored.indexOfFirst { it == null }
            if (free < 0) restored += previous else restored[free] = previous
        }
    }
    return restored
}

/** Time comes from the host; release never completes an unfinished hold. */
data class DeleteHold(val row: Row, val key: PromptKey, val started: Long, val gameId: String?, val meaning: Meaning = Meaning.Activate, val inputWindow: String = "", val inputKey: Int? = null) {
    fun progress(now: Long): Float = ((now - started).toFloat() / DELETE_HOLD_MILLIS).coerceIn(0f, 1f)
    fun complete(now: Long): Boolean = now >= started && now - started >= DELETE_HOLD_MILLIS
    fun release(now: Long): Boolean = complete(now)
}

fun Row.destructive(): Boolean = this == Row.RemoveDownload || this == Row.DeleteShortcut || this == Row.RemoveSession
