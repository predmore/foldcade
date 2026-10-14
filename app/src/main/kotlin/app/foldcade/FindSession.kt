package app.foldcade

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.foldcade.language.FindEntry
import app.foldcade.language.FindFilter
import app.foldcade.language.FindIndex
import app.foldcade.language.FindKind
import app.foldcade.language.KeyCap
import app.foldcade.language.KeyFocus
import app.foldcade.language.KeyboardLayout
import app.foldcade.language.Meaning
import app.foldcade.language.TypingCue
import app.foldcade.language.findFilters
import app.foldcade.language.findResults

/**
 * Find, open over a grid: the controller keyboard typing a query, the filters, and the strip
 * of results above the keys. The grid under it is left as it was, so closing Find is only
 * forgetting this. [entries] is everything Find can list; the shell hands it a new list
 * whenever Home changes.
 *
 * On the keys: Up from the top row, or Start, goes to the results; L2 and R2 step the
 * filters; B clears the query, then closes; Select closes. On the results: left and right
 * walk them, A and X open the focused one, Y is its menu, and Down or B go back to the keys.
 */
class FindSession(entries: List<FindEntry>) {
    private var entries: List<FindEntry> = entries

    /** [entries] made ready to search, so a key press only matches. Built again when Home changes. */
    private var library = FindIndex(entries)

    /** The keyboard, in live mode: every change to the query searches again. */
    val typing = TypingSession(text = "", live = { search(fresh = true) }, onDone = { toResults() }, onCancel = {})

    var filters: List<FindFilter> by mutableStateOf(findFilters(entries))
        private set

    /** The chosen filter in [filters]. All is first. */
    var filter: Int by mutableIntStateOf(0)
        private set

    var results: List<FindEntry> by mutableStateOf(emptyList())
        private set

    /** True while the D-pad is on the results rather than the keys. */
    var onResults: Boolean by mutableStateOf(false)
        private set

    /** The focused result, while [onResults]. Set through [focus], which keeps [focusedId] with it. */
    var index: Int by mutableIntStateOf(0)
        private set

    /** The focused result's id, so a refresh that moves it keeps the focus on it. */
    private var focusedId: String? = null

    /**
     * The result a Y menu opened on. The menu keeps acting on it however the results move,
     * even when it leaves them, until the menu closes ([releaseMenu]).
     */
    var menuId: String? = null
        private set

    /** Counts searches that changed the results, so the strip can pop them in again. */
    var searched: Int by mutableIntStateOf(0)
        private set

    init {
        search(fresh = true)
    }

    val query: String get() = typing.text

    /** What the top screen shows: the focused result on the results, else the best one. */
    val subject: FindEntry? get() = if (onResults) results.getOrNull(index) else results.firstOrNull()

    /** The Y menu is opening on the focused result. */
    fun holdMenu() {
        menuId = subject?.id
    }

    /** The menu closed; the next one reads the focused result again. */
    fun releaseMenu() {
        menuId = null
    }

    /** Home changed under Find. The query, filter and focus stay where they can, the focus on the same result. */
    fun refresh(next: List<FindEntry>) {
        if (next == entries) return
        val chosen = filters.getOrNull(filter)
        entries = next
        library = FindIndex(next)
        filters = findFilters(next)
        filter = filters.indexOfFirst { it.kind == chosen?.kind && it.platformId == chosen.platformId }.coerceAtLeast(0)
        search(fresh = false)
    }

    /** One key. The shell does what the outcome asks; everything else happened here. */
    fun handle(meaning: Meaning): FindOutcome = if (onResults) onResult(meaning) else onKeys(meaning)

    /** A tap on a key or chip of the keyboard. A tap on Done goes to the results, as Start does. */
    fun tap(at: KeyFocus): FindOutcome {
        if (!onResults && KeyboardLayout.key(at)?.cap == KeyCap.Done) return toResults()
        onResults = false
        return FindOutcome.Stay(typing.press(at).cue)
    }

    /** A tap on result [at]: the first tap focuses it, a second opens it. */
    fun tapResult(at: Int): FindOutcome {
        val entry = results.getOrNull(at) ?: return FindOutcome.Stay(TypingCue.None)
        if (onResults && index == at) return open(entry, onBottom = false)
        onResults = true
        focus(at)
        return FindOutcome.Stay(TypingCue.Move)
    }

    private fun onKeys(meaning: Meaning): FindOutcome = when (meaning) {
        Meaning.View -> FindOutcome.Close
        Meaning.LetterBackward, Meaning.LetterForward -> step(if (meaning == Meaning.LetterForward) 1 else -1)
        Meaning.Done -> toResults()
        // Done under the ring is the results too, so A there never shakes an empty query.
        Meaning.Activate -> if (KeyboardLayout.key(typing.typing.focus)?.cap == KeyCap.Done) toResults() else typed(meaning)
        Meaning.Back, Meaning.CancelHold -> if (query.isEmpty()) {
            FindOutcome.Close
        } else {
            typing.setText("")
            FindOutcome.Stay(TypingCue.Erase)
        }
        else -> {
            val step = typing.handle(meaning)
            if (step.edge == Meaning.MoveUp) toResults() else FindOutcome.Stay(step.cue)
        }
    }

    private fun typed(meaning: Meaning): FindOutcome = FindOutcome.Stay(typing.handle(meaning).cue)

    private fun onResult(meaning: Meaning): FindOutcome {
        val entry = results.getOrNull(index)
        return when (meaning) {
            Meaning.MoveLeft, Meaning.MoveRight -> {
                val next = index + if (meaning == Meaning.MoveRight) 1 else -1
                if (next !in results.indices) return FindOutcome.Stay(TypingCue.None)
                focus(next)
                FindOutcome.Stay(TypingCue.Move)
            }
            Meaning.MoveDown, Meaning.Back, Meaning.CancelHold -> {
                onResults = false
                FindOutcome.Stay(TypingCue.Move)
            }
            Meaning.LetterBackward, Meaning.LetterForward -> step(if (meaning == Meaning.LetterForward) 1 else -1)
            Meaning.View -> FindOutcome.Close
            Meaning.Activate -> entry?.let { open(it, onBottom = false) } ?: FindOutcome.Stay(TypingCue.None)
            Meaning.ActivateBottom -> entry?.let { open(it, onBottom = true) } ?: FindOutcome.Stay(TypingCue.None)
            Meaning.Options -> if (entry != null) FindOutcome.Options else FindOutcome.Stay(TypingCue.None)
            else -> FindOutcome.Stay(TypingCue.None)
        }
    }

    private fun open(entry: FindEntry, onBottom: Boolean): FindOutcome =
        if (entry.kind == FindKind.Group) FindOutcome.OpenGroup(entry.id) else FindOutcome.Launch(onBottom)

    /** To the results, on the best one. With nothing to show it stays on the keys. */
    private fun toResults(): FindOutcome {
        if (results.isEmpty()) return FindOutcome.Stay(TypingCue.None)
        onResults = true
        focus(0)
        return FindOutcome.Stay(TypingCue.Move)
    }

    /** The next or previous filter, round the ends. The results start again from the best. */
    private fun step(by: Int): FindOutcome {
        if (filters.size < 2) return FindOutcome.Stay(TypingCue.None)
        filter = Math.floorMod(filter + by, filters.size)
        search(fresh = true)
        return FindOutcome.Stay(TypingCue.Move)
    }

    /**
     * Searches again. A [fresh] search, a new query or filter, starts the results from the
     * best and leaves them for the keys when there are none. A refresh keeps the focus.
     */
    private fun search(fresh: Boolean) {
        val next = findResults(library, query, filters.getOrElse(filter) { filters.first() })
        if (next.map { it.id } != results.map { it.id }) searched++
        results = next
        val kept = focusedId?.takeIf { !fresh }?.let { id -> next.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        focus(kept ?: if (fresh) 0 else index)
        if (next.isEmpty()) onResults = false
    }

    /** Focuses result [at], or the nearest one there is. */
    private fun focus(at: Int) {
        index = at.coerceIn(0, (results.size - 1).coerceAtLeast(0))
        focusedId = results.getOrNull(index)?.id
    }
}

/** What a key in Find asks the shell to do. [Stay] keeps Find open and makes [Stay.cue]'s sound. */
sealed interface FindOutcome {
    data class Stay(val cue: TypingCue) : FindOutcome
    data object Close : FindOutcome

    /** Open the focused game or app, on the bottom screen when [onBottom]. */
    data class Launch(val onBottom: Boolean) : FindOutcome

    /** Leave Find and open the group [id] on Home. */
    data class OpenGroup(val id: String) : FindOutcome

    /** The focused result's Y menu. */
    data object Options : FindOutcome
}
