package app.foldcade.language

import android.view.KeyEvent

/**
 * One in-app map for every Foldcade surface.
 * Null means the key is unused. Callers do not give it a second meaning.
 * Do not consult this while an external app is the focused window.
 */
enum class Meaning {
    MoveUp,
    MoveDown,
    MoveLeft,
    MoveRight,
    Activate,
    /** X on a grid. Launches the focused game on the bottom screen; anything else acts as [Activate]. */
    ActivateBottom,
    Back,
    LeftPanel,
    RightPanel,
    /** Grid swipe only. L1 and R1 do not page. */
    PageTowardStart,
    /** Grid swipe only. L1 and R1 do not page. */
    PageTowardEnd,
    /** B while a tile is moving or the icon picker is open. Puts things back as they were. */
    CancelHold,
    /** Y on a grid. Opens the actions for the focused tile. */
    Options,
    LetterForward,
    LetterBackward,

    /** Delete on a keyboard. Removes the focused notice in R1, as Y does. */
    Dismiss,

    /** Start opens Settings from a grid, runs a menu's settings row, and closes Settings again. See [chords]. */
    Menu,

    /** Select. In a menu it runs the row that shows a view (L1 then Select is Play activity). See [chords]. */
    View,

    /** X while typing: the selection, or the character before the caret. */
    Erase,

    /** Y while typing. */
    Space,

    /** L2 while typing: one capital, then caps lock, then off. */
    Shift,

    /** L1 while typing. */
    CaretBack,

    /** R1 while typing. */
    CaretForward,

    /** Start while typing: saves the text. */
    Done,
}

/** Extra keys that exist only on the home grid. Idle keeps the shared map. */
enum class HomeKeys {
    Idle,
    /** A tile picked up with Move is following the D-pad. */
    Moving,
    AllLibrary,
    /** Picker navigation repeats normally; triggers choose a game match. */
    ChoosingArt,
    /** The icon picker for a folder the user made is open. */
    PickingIcon,
    /** The controller keyboard is open. Every key types or edits; no menu or drawer opens. */
    Typing,
}

/** Keys that act once per press. Holding one does not repeat it. */
private val shoulderKeys = setOf(
    KeyEvent.KEYCODE_BUTTON_L1,
    KeyEvent.KEYCODE_BUTTON_R1,
    KeyEvent.KEYCODE_LEFT_BRACKET,
    KeyEvent.KEYCODE_RIGHT_BRACKET,
    KeyEvent.KEYCODE_BUTTON_START,
    KeyEvent.KEYCODE_BUTTON_SELECT,
    // Held, Y would open a menu, then run its Move chord, then drop, over and over.
    KeyEvent.KEYCODE_BUTTON_Y,
)

fun meaningOf(
    keyCode: Int,
    repeatCount: Int = 0,
    faces: FaceMap = FaceMap.standard(),
    homeKeys: HomeKeys = HomeKeys.Idle,
): Meaning? {
    if (homeKeys == HomeKeys.Typing) return typingMeaningOf(keyCode, repeatCount, faces)
    if (homeKeys == HomeKeys.ChoosingArt && repeatCount > 0 &&
        (keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_BUTTON_R2)) return null
    if (repeatCount > 0 && keyCode in shoulderKeys) {
        return null
    }
    // Y puts a held tile down as A does, so Y then Y lifts and Y drops without moving a thumb.
    // A held tile drops once per press.
    if (homeKeys == HomeKeys.Moving && (keyCode == KeyEvent.KEYCODE_BUTTON_Y || keyCode == faces.confirmKey)) {
        return if (repeatCount == 0) Meaning.Activate else null
    }
    return when {
        keyCode == KeyEvent.KEYCODE_DPAD_UP -> Meaning.MoveUp
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> Meaning.MoveDown
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> Meaning.MoveLeft
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> Meaning.MoveRight
        keyCode == faces.confirmKey ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER -> Meaning.Activate
        keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == faces.backKey ->
            if (homeKeys == HomeKeys.Moving || homeKeys == HomeKeys.PickingIcon) Meaning.CancelHold else Meaning.Back
        // A keyboard has no shoulders. The bracket keys sit where L1 and R1 would.
        keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_LEFT_BRACKET -> Meaning.LeftPanel
        keyCode == KeyEvent.KEYCODE_BUTTON_R1 || keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET -> Meaning.RightPanel
        keyCode == KeyEvent.KEYCODE_BUTTON_X && (homeKeys == HomeKeys.Idle || homeKeys == HomeKeys.AllLibrary || homeKeys == HomeKeys.ChoosingArt) ->
            Meaning.ActivateBottom
        keyCode == KeyEvent.KEYCODE_BUTTON_Y && (homeKeys == HomeKeys.Idle || homeKeys == HomeKeys.AllLibrary) ->
            Meaning.Options
        keyCode == KeyEvent.KEYCODE_BUTTON_START -> Meaning.Menu
        keyCode == KeyEvent.KEYCODE_BUTTON_SELECT -> Meaning.View
        keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL -> Meaning.Dismiss
        keyCode == KeyEvent.KEYCODE_BUTTON_L2 -> Meaning.LetterBackward
        keyCode == KeyEvent.KEYCODE_BUTTON_R2 -> Meaning.LetterForward
        else -> null
    }
}

/** Unused controller buttons must not reach Android's activating key fallback. */
fun consumesUnmapped(keyCode: Int): Boolean =
    keyCode in KeyEvent.KEYCODE_BUTTON_A..KeyEvent.KEYCODE_BUTTON_MODE ||
        keyCode in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16

/** These controls open UI on top, so they share ownership and launch cancellation rules. */
fun opensTopMenu(meaning: Meaning): Boolean =
    meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel || meaning == Meaning.Menu

/**
 * True when a key that opens the top screen's menus has nowhere to go: an app holds the top
 * screen, so it covers the islands. The icon editor's L1 and R1 step its colour instead, so
 * they still reach it wherever it is. The keyboard's shoulders move its caret and are never
 * menu keys (see [typingMeaningOf]).
 */
fun topMenuKeyBlocked(meaning: Meaning, homeKeys: HomeKeys, islandsOpen: Boolean): Boolean {
    if (!opensTopMenu(meaning) || islandsOpen) return false
    return !(homeKeys == HomeKeys.PickingIcon && (meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel))
}

fun cancelsPendingLaunch(meaning: Meaning, dialogOpen: Boolean, conflictOpen: Boolean): Boolean =
    !dialogOpen && !conflictOpen && (opensTopMenu(meaning) || meaning == Meaning.Back)

/**
 * Which face button does what while typing. Confirm types the focused key and back
 * cancels, wherever calibration put them. Erase keeps X and Space keeps Y when those
 * are free; a role whose button is taken goes to A, then B, so the same calibration
 * always lands the same way.
 */
data class TypingFaces(val type: Int, val cancel: Int, val erase: Int, val space: Int) {
    val eraseGlyph: PromptKey get() = promptKeyOf(erase) ?: PromptKey.FaceX
    val spaceGlyph: PromptKey get() = promptKeyOf(space) ?: PromptKey.FaceY
}

fun typingFaces(faces: FaceMap): TypingFaces {
    val x = KeyEvent.KEYCODE_BUTTON_X
    val y = KeyEvent.KEYCODE_BUTTON_Y
    val free = listOf(x, y, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B)
        .filter { it != faces.confirmKey && it != faces.backKey }
    val erase = x.takeIf { it in free }
    val space = y.takeIf { it in free }
    val rest = (free - listOfNotNull(erase, space)).iterator()
    return TypingFaces(
        type = faces.confirmKey,
        cancel = faces.backKey,
        erase = erase ?: rest.next(),
        space = space ?: rest.next(),
    )
}

/** Keys that repeat while held on the keyboard: the D-pad walks, Erase erases, L1 and R1 glide the caret. */
private val typingRepeats = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DEL,
    KeyEvent.KEYCODE_BUTTON_L1,
    KeyEvent.KEYCODE_BUTTON_R1,
)

/**
 * The keyboard's own map. The face buttons, Start and the shoulders mean something else
 * on a grid, so they are only read this way here; [typingFaces] says which face does
 * what. Typing acts once per press, so a held confirm never types a run of letters or
 * flips Shift back and forth. A keyboard's Enter saves.
 */
private fun typingMeaningOf(keyCode: Int, repeatCount: Int, faces: FaceMap): Meaning? {
    val roles = typingFaces(faces)
    if (repeatCount > 0 && keyCode !in typingRepeats && keyCode != roles.erase) return null
    return when {
        keyCode == KeyEvent.KEYCODE_DPAD_UP -> Meaning.MoveUp
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> Meaning.MoveDown
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> Meaning.MoveLeft
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> Meaning.MoveRight
        keyCode == roles.type || keyCode == KeyEvent.KEYCODE_DPAD_CENTER -> Meaning.Activate
        keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == roles.cancel -> Meaning.Back
        keyCode == roles.erase || keyCode == KeyEvent.KEYCODE_DEL -> Meaning.Erase
        keyCode == roles.space || keyCode == KeyEvent.KEYCODE_SPACE -> Meaning.Space
        keyCode == KeyEvent.KEYCODE_BUTTON_L1 -> Meaning.CaretBack
        keyCode == KeyEvent.KEYCODE_BUTTON_R1 -> Meaning.CaretForward
        keyCode == KeyEvent.KEYCODE_BUTTON_L2 -> Meaning.Shift
        keyCode == KeyEvent.KEYCODE_BUTTON_START ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER -> Meaning.Done
        else -> null
    }
}
