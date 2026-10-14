package app.foldcade.language

const val UNDO_MILLIS = 8_000L
const val DELETE_HOLD_MILLIS = 800L

/** A single in-memory snapshot. An unrelated edit invalidates it. */
data class HomeUndo(val board: HomeBoard, val shelves: AppShelfState, val verb: String, val started: Long) {
    fun available(now: Long): Boolean = now >= started && now - started < UNDO_MILLIS
    fun afterEdit(before: HomeBoard, after: HomeBoard): HomeUndo? = if (before == after) this else null
}

/** Time comes from the host; release never completes an unfinished hold. */
data class DeleteHold(val row: Row, val key: PromptKey, val started: Long, val gameId: String?, val meaning: Meaning = Meaning.Activate) {
    fun progress(now: Long): Float = ((now - started).toFloat() / DELETE_HOLD_MILLIS).coerceIn(0f, 1f)
    fun complete(now: Long): Boolean = now >= started && now - started >= DELETE_HOLD_MILLIS
    fun release(now: Long): Boolean = complete(now)
}

fun Row.destructive(): Boolean = this == Row.RemoveDownload || this == Row.DeleteShortcut
