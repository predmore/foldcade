package app.foldcade.language

import java.text.BreakIterator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The on-screen keyboard, played with a controller the way a console's is: its keys,
 * how the D-pad walks them, and the text they edit. All of it is plain data here, so
 * the app only draws [Typing] and hands it [Meaning]s. It edits "a name" and knows
 * nothing about folders, so a search can use it too.
 */

/**
 * The longest name the keyboard takes, in characters as a reader counts them: an emoji
 * with its skin tone, a flag, or a family joined into one picture is one character.
 */
const val NAME_LIMIT = 32

/** Where each character of [text] ends, as a reader sees characters. */
internal fun characterBreaks(text: String): BreakIterator =
    BreakIterator.getCharacterInstance().also { it.setText(text) }

/** How many characters [text] holds, as a reader counts them. */
fun nameLength(text: String): Int {
    val breaks = characterBreaks(text)
    var count = 0
    while (breaks.next() != BreakIterator.DONE) count++
    return count
}

/** [text] cut to at most [limit] characters, at the end of a whole one, so no emoji is ever torn. */
fun clipName(text: String, limit: Int): String {
    val breaks = characterBreaks(text)
    var end = 0
    repeat(limit) {
        val next = breaks.next()
        if (next == BreakIterator.DONE) return text
        end = next
    }
    return text.substring(0, end)
}

/** One key. A [Glyph] types its character; the others edit the text or close the keyboard. */
sealed interface KeyCap {
    /** Types [lower], or [upper] while Shift is on. Digits and marks are the same both ways. */
    data class Glyph(val lower: Char, val upper: Char = lower.uppercaseChar()) : KeyCap {
        fun shown(shift: ShiftState): Char = if (shift == ShiftState.Off) lower else upper
    }

    data object Shift : KeyCap
    data object Erase : KeyCap
    data object Space : KeyCap
    data object Done : KeyCap
}

/** A key and where it sits in its row, measured in key widths from the keyboard's left edge. */
data class PlacedKey(val cap: KeyCap, val x: Float, val width: Float = 1f) {
    val center: Float get() = x + width / 2f
    val end: Float get() = x + width
}

/**
 * QWERTY under a number row, with the marks names use at the ends, Shift and Erase
 * around the bottom letters, and Space and Done along the bottom. The letter rows
 * are staggered like a real keyboard's, so rows have different lengths.
 */
object KeyboardLayout {
    const val WIDTH = 12f

    val rows: List<List<PlacedKey>> = listOf(
        glyphs(0f, "1234567890-&"),
        glyphs(0f, "qwertyuiop'!"),
        glyphs(0.5f, "asdfghjkl:?"),
        listOf(PlacedKey(KeyCap.Shift, 0f, 1.5f)) + glyphs(1.5f, "zxcvbnm,.") + PlacedKey(KeyCap.Erase, 10.5f, 1.5f),
        listOf(PlacedKey(KeyCap.Space, 0f, 9f), PlacedKey(KeyCap.Done, 9f, 3f)),
    )

    fun key(focus: KeyFocus): PlacedKey? = if (focus.onChips) null else rows.getOrNull(focus.row)?.getOrNull(focus.index)

    /** Where [cap] sits. Every cap but a glyph is on the board once. */
    fun find(cap: KeyCap): KeyFocus {
        rows.forEachIndexed { row, keys ->
            val index = keys.indexOfFirst { it.cap == cap }
            if (index >= 0) return KeyFocus(row, index, keys[index].center)
        }
        error("$cap is not on the keyboard")
    }

    /** G, in the middle of the home row: the shortest reach to anything. */
    val start: KeyFocus get() = find(KeyCap.Glyph('g'))

    private fun glyphs(from: Float, chars: String): List<PlacedKey> =
        chars.mapIndexed { index, char -> PlacedKey(KeyCap.Glyph(char), from + index) }
}

/** The row of quick-name chips above the keys. */
const val CHIP_ROW = -1

/**
 * The cursor: a key, or the chip at [index] when [row] is [CHIP_ROW]. [x] is the column
 * the cursor keeps while it goes up and down, so crossing a shorter row and coming back
 * lands where it started, as on a console keyboard.
 */
data class KeyFocus(val row: Int, val index: Int, val x: Float) {
    val onChips: Boolean get() = row == CHIP_ROW
}

/**
 * Where the D-pad takes [focus]. Null at an edge: nothing wraps. Left and right step
 * along the row and take that key's column; up and down keep the column and land on
 * the key under it. Up from the top row reaches the chips, on the one last chosen.
 */
fun moveKey(focus: KeyFocus, meaning: Meaning, chips: Int, lastChip: Int = 0): KeyFocus? {
    val rows = KeyboardLayout.rows
    return when (meaning) {
        Meaning.MoveLeft, Meaning.MoveRight -> {
            val next = focus.index + if (meaning == Meaning.MoveLeft) -1 else 1
            if (focus.onChips) {
                if (next in 0 until chips) focus.copy(index = next) else null
            } else {
                val key = rows[focus.row].getOrNull(next) ?: return null
                KeyFocus(focus.row, next, key.center)
            }
        }
        Meaning.MoveUp -> when {
            focus.onChips -> null
            focus.row == 0 -> if (chips > 0) KeyFocus(CHIP_ROW, lastChip.coerceIn(0, chips - 1), focus.x) else null
            else -> keyUnder(focus.row - 1, focus.x)
        }
        Meaning.MoveDown -> when {
            focus.onChips -> keyUnder(0, focus.x)
            focus.row == rows.lastIndex -> null
            else -> keyUnder(focus.row + 1, focus.x)
        }
        else -> null
    }
}

/** The key in [row] under column [x], or the nearest one where the row stops short. */
private fun keyUnder(row: Int, x: Float): KeyFocus {
    val keys = KeyboardLayout.rows[row]
    val index = keys.indexOfFirst { x >= it.x && x < it.end }.takeIf { it >= 0 }
        ?: keys.indices.minBy { abs(keys[it].center - x) }
    return KeyFocus(row, index, x)
}

/**
 * The text and its caret. [anchor] is the other end of the selection; equal to
 * [caret] when nothing is selected. Erase and the caret step over a whole character as
 * a reader sees it (a surrogate pair, an emoji with its skin tone, a flag), so a name
 * with an emoji in it never splits.
 */
data class TextDraft(val text: String, val caret: Int = text.length, val anchor: Int = caret) {
    val start: Int get() = min(caret, anchor)
    val end: Int get() = max(caret, anchor)
    val selecting: Boolean get() = caret != anchor

    /** [chars] in place of the selection, or at the caret. Whatever would run past [limit] is left off. */
    fun insert(chars: String, limit: Int): TextDraft {
        val room = limit - nameLength(text.removeRange(start, end))
        val typed = clipName(chars, room.coerceAtLeast(0))
        if (typed.isEmpty()) return this
        val at = start + typed.length
        return TextDraft(text.substring(0, start) + typed + text.substring(end), at)
    }

    /** The selection, or the character before the caret. */
    fun erase(): TextDraft = when {
        selecting -> TextDraft(text.removeRange(start, end), start)
        caret == 0 -> this
        else -> before(caret).let { from -> TextDraft(text.removeRange(from, caret), from) }
    }

    /** L1: a selection collapses to its start, or the caret steps back. */
    fun back(): TextDraft = TextDraft(text, if (selecting) start else before(caret))

    /** R1: a selection collapses to its end, or the caret steps on. */
    fun forward(): TextDraft = TextDraft(text, if (selecting) end else after(caret))

    /** True where the next character starts a word, which Shift then capitalises. */
    val wordStart: Boolean get() = start == 0 || text[start - 1] == ' '

    private fun before(at: Int): Int {
        if (at <= 0) return 0
        val breaks = characters()
        // The text's end is no offset to step back from, only its last boundary.
        val from = if (at >= text.length) breaks.last().let { breaks.previous() } else breaks.preceding(at)
        return from.coerceAtLeast(0)
    }

    private fun after(at: Int): Int =
        if (at >= text.length) text.length else characters().following(at.coerceAtLeast(0)).coerceAtMost(text.length)

    private fun characters(): BreakIterator = characterBreaks(text)

    companion object {
        /** The whole of [text] selected, so the first key typed replaces it. */
        fun selectAll(text: String): TextDraft = TextDraft(text, caret = text.length, anchor = 0)
    }
}

enum class ShiftState {
    Off,
    /** On by itself at the start of a word. The word's first character spends it, letter or not. */
    Word,
    /** Chosen with L2 for the next letter only. */
    Once,
    /** Caps lock. */
    Locked,
    ;

    /** L2 or the Shift key: one capital, then caps lock, then off again. A word's own capital counts as the first. */
    fun next(): ShiftState = when (this) {
        Off -> Once
        Word, Once -> Locked
        Locked -> Off
    }
}

/**
 * Shift after an edit. Caps lock stays. A word that starts with a letter starts with a
 * capital, as names usually are; one that starts with a digit or a mark has started, so
 * "2nd" and "3DS" type as they read. Typing [spent] a capital. A capital chosen with L2
 * is only spent by a letter: it waits through digits, marks, spaces, erasing and caret
 * moves for the letter it was meant for, so m, L2, 1, c types M1C.
 */
fun settleShift(shift: ShiftState, draft: TextDraft, spent: Boolean = false): ShiftState = when {
    shift == ShiftState.Locked -> ShiftState.Locked
    shift == ShiftState.Once && !spent -> ShiftState.Once
    draft.wordStart -> ShiftState.Word
    else -> ShiftState.Off
}

/**
 * An open keyboard. [original] is the text it opened with: B keeps it, and Done on an
 * empty field puts it back. [chip] is the chip the cursor returns to from the keys.
 * [refusals] counts presses the field turned down (Done on nothing, a key past [limit]);
 * the field shakes each time it grows. [pressed] counts presses, and [pressedKey] is
 * the key that acted last, so the board can flash it even when a button did the work.
 */
data class Typing(
    val draft: TextDraft,
    val original: String,
    val shift: ShiftState,
    val focus: KeyFocus,
    val chips: List<String> = emptyList(),
    val chip: Int = 0,
    val limit: Int = NAME_LIMIT,
    val refusals: Int = 0,
    val pressed: Int = 0,
    val pressedKey: KeyFocus? = null,
)

/**
 * A keyboard over [text], all of it selected. The cursor starts on the first chip when
 * [onChips] and there are chips, which suits naming something new; otherwise on G.
 */
fun openTyping(
    text: String,
    chips: List<String> = emptyList(),
    onChips: Boolean = false,
    limit: Int = NAME_LIMIT,
): Typing {
    val start = clipName(text, limit)
    val draft = TextDraft.selectAll(start)
    val home = KeyboardLayout.start
    val focus = if (onChips && chips.isNotEmpty()) KeyFocus(CHIP_ROW, 0, home.x) else home
    return Typing(draft, start, settleShift(ShiftState.Off, draft), focus, chips, limit = limit)
}

/** The sound a press makes, by theme slot. */
enum class TypingCue(val slot: String?) {
    None(null),
    Move("move"),
    Type("activate"),
    Erase("back"),
    Done("activate"),
    Cancel("back"),
}

/**
 * One press. [saved] is the trimmed text when Done took it, and [cancelled] is B.
 * [edge] is a move past the keyboard's edge, for a host with more above or below it.
 */
data class TypingStep(
    val typing: Typing,
    val cue: TypingCue,
    val saved: String? = null,
    val cancelled: Boolean = false,
    val edge: Meaning? = null,
)

/**
 * A key while the keyboard is open. A presses the focused key or chip, X erases, Y types
 * a space, L1 and R1 move the caret, L2 steps Shift, Start is Done and B cancels.
 */
fun Typing.on(meaning: Meaning): TypingStep = when (meaning) {
    Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight -> {
        val next = moveKey(focus, meaning, chips.size, chip)
        if (next == null) {
            TypingStep(this, TypingCue.None, edge = meaning)
        } else {
            TypingStep(copy(focus = next, chip = if (next.onChips) next.index else chip), TypingCue.Move)
        }
    }
    Meaning.Activate -> pressFocused()
    Meaning.Erase -> erase(KeyboardLayout.find(KeyCap.Erase))
    Meaning.Space -> type(" ", KeyboardLayout.find(KeyCap.Space))
    Meaning.Shift -> shifted(KeyboardLayout.find(KeyCap.Shift))
    Meaning.CaretBack -> caret(draft.back())
    Meaning.CaretForward -> caret(draft.forward())
    Meaning.Done -> done(KeyboardLayout.find(KeyCap.Done))
    Meaning.Back, Meaning.CancelHold -> TypingStep(this, TypingCue.Cancel, cancelled = true)
    else -> TypingStep(this, TypingCue.None)
}

/** A tap on a key or chip: the cursor goes there and presses it, as A would. */
fun Typing.press(at: KeyFocus): TypingStep {
    val valid = if (at.onChips) at.index in chips.indices else KeyboardLayout.key(at) != null
    if (!valid) return TypingStep(this, TypingCue.None)
    return copy(focus = at, chip = if (at.onChips) at.index else chip).pressFocused()
}

private fun Typing.pressFocused(): TypingStep {
    if (focus.onChips) {
        val name = chips.getOrNull(focus.index) ?: return TypingStep(this, TypingCue.None)
        return TypingStep(edited(TextDraft(clipName(name, limit))).pressing(focus), TypingCue.Type)
    }
    return when (val cap = KeyboardLayout.key(focus)?.cap) {
        is KeyCap.Glyph -> type(cap.shown(shift).toString(), focus)
        KeyCap.Shift -> shifted(focus)
        KeyCap.Erase -> erase(focus)
        KeyCap.Space -> type(" ", focus)
        KeyCap.Done -> done(focus)
        null -> TypingStep(this, TypingCue.None)
    }
}

/** Types [chars]. A letter spends any capital. A digit or a mark spends a word's own capital, not one chosen with L2. */
private fun Typing.type(chars: String, key: KeyFocus): TypingStep {
    val next = draft.insert(chars, limit)
    if (next == draft) return TypingStep(copy(refusals = refusals + 1).pressing(key), TypingCue.None)
    val typed = when {
        chars.any { it.isLetter() } -> edited(next, spent = true)
        chars.any { it == ' ' } -> edited(next)
        shift == ShiftState.Once -> copy(draft = next)
        else -> edited(next, spent = true)
    }
    return TypingStep(typed.pressing(key), TypingCue.Type)
}

private fun Typing.erase(key: KeyFocus): TypingStep {
    val next = draft.erase()
    if (next == draft) return TypingStep(pressing(key), TypingCue.None)
    return TypingStep(edited(next).pressing(key), TypingCue.Erase)
}

private fun Typing.shifted(key: KeyFocus): TypingStep =
    TypingStep(copy(shift = shift.next()).pressing(key), TypingCue.Type)

private fun Typing.caret(next: TextDraft): TypingStep =
    if (next == draft) TypingStep(this, TypingCue.None) else TypingStep(edited(next), TypingCue.Move)

/** Saves the trimmed text. Nothing to save puts the old text back, selected, and shakes the field. */
private fun Typing.done(key: KeyFocus): TypingStep {
    val name = draft.text.trim()
    if (name.isNotEmpty()) return TypingStep(pressing(key), TypingCue.Done, saved = name)
    val restored = TextDraft.selectAll(original)
    return TypingStep(edited(restored).copy(refusals = refusals + 1).pressing(key), TypingCue.None)
}

/** [text] in place of everything, caret at its end, as a host clearing a search would set it. */
fun Typing.withText(text: String): Typing = edited(TextDraft(clipName(text, limit)))

private fun Typing.edited(next: TextDraft, spent: Boolean = false): Typing =
    copy(draft = next, shift = settleShift(shift, next, spent))

private fun Typing.pressing(key: KeyFocus): Typing = copy(pressed = pressed + 1, pressedKey = key)

/**
 * One-press names for something new: the caller's own ideas first (such as the platform
 * its games share), then [KeyboardCopy.quickNames]. A name already [taken] elsewhere, a
 * repeat in any case, or one too long to type is left out.
 */
fun quickNames(context: List<String>, taken: Collection<String> = emptyList(), limit: Int = NAME_LIMIT): List<String> {
    val used = taken.map { it.trim().lowercase() }.toSet()
    return (context + KeyboardCopy.quickNames)
        .map { it.trim() }
        .filter { it.isNotEmpty() && nameLength(it) <= limit && it.lowercase() !in used }
        .distinctBy { it.lowercase() }
}

/** The keyboard's words. */
object KeyboardCopy {
    const val erase = "Erase"
    const val space = "Space"
    const val caret = "Cursor"
    const val shift = "Shift"
    const val done = "Done"

    /** Quick names, one or two words each. Favorites is left out: it is a collection of its own. */
    val quickNames = listOf(
        "Playing",
        "Backlog",
        "Finished",
        "Must play",
        "Multiplayer",
        "Co-op",
        "RPGs",
        "Platformers",
        "Racing",
        "Puzzle",
        "Retro",
        "Party",
    )
}

/**
 * Keys captured by a keyboard or confirmation hold. Until each comes up, its repeats
 * belong to that input context, even after typing closes or deletion completes. A held
 * X or A never lands on the grid as Bottom or Open.
 *
 * One guard serves both screens. A tap moves input focus to the screen it lands on,
 * and Android then hands a held key's repeats to that screen's window, after telling
 * the first window the key was cancelled. A cancelled key is still held.
 */
class HeldKeyGuard {
    private val held = HashSet<Int>()

    /** True when this key-down must go nowhere after its owning input context closes. */
    fun swallows(keyCode: Int, repeatCount: Int, capturing: Boolean): Boolean {
        if (capturing) {
            held += keyCode
            return false
        }
        if (keyCode !in held) return false
        // A fresh press means its key-up went missing, as when focus moves; it is the grid's.
        if (repeatCount == 0) {
            held -= keyCode
            return false
        }
        return true
    }

    fun capture(keyCode: Int) { held += keyCode }

    fun up(keyCode: Int, canceled: Boolean = false) {
        if (!canceled) held -= keyCode
    }
}

/** Kept for keyboard callers; both keyboards and confirmation holds use the same guard. */
typealias TypingKeyGuard = HeldKeyGuard
