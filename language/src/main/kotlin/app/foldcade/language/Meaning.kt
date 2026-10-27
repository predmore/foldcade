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

    /** Delete on a keyboard. Removes the focused notice in R1, as X does. */
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
    Selecting,
    /** Picker navigation repeats normally; triggers choose a game match. */
    ChoosingArt,
    /** The icon picker for a folder the user made is open. */
    PickingIcon,
    /** The controller keyboard is open. Every key types or edits; no menu or drawer opens. */
    Typing,

    /**
     * Find's keyboard. It types as [Typing] does, except that a search has no case, so
     * L2 and R2 step the filters instead of Shift, and Select closes Find.
     */
    Finding,

    /** Find's results. They take a grid's keys: A and X open, Y is the menu. */
    FindResults,
}

/**
 * True while a held key must keep its repeats to itself until it comes up: on the controller
 * keyboard, for a name or for Find, and on Find's results, which can close under a held A.
 */
val HomeKeys.guardsHeldKeys: Boolean get() = this == HomeKeys.Selecting || this == HomeKeys.Typing || this == HomeKeys.Finding || this == HomeKeys.FindResults

/** True while a field takes text: a name on the keyboard, or Find's query, from its results too. */
val HomeKeys.takesText: Boolean get() = this == HomeKeys.Typing || this == HomeKeys.Finding || this == HomeKeys.FindResults

/**
 * What a hardware keyboard's key types while [homeKeys] take text, from its [unicodeChar]
 * (the key event's character under its meta state). Null for controller buttons and the
 * D-pad, which have none; for a dead accent waiting on its letter, which reads negative;
 * and for spaces and control characters, whose keys already mean Space, Erase, Done or Back.
 */
fun typedText(unicodeChar: Int, homeKeys: HomeKeys): String? {
    if (!homeKeys.takesText || unicodeChar <= 0 || !Character.isValidCodePoint(unicodeChar)) return null
    if (Character.isISOControl(unicodeChar) || Character.isWhitespace(unicodeChar) || Character.isSpaceChar(unicodeChar)) return null
    return String(Character.toChars(unicodeChar))
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
    if (homeKeys == HomeKeys.Finding) return findingMeaningOf(keyCode, repeatCount, faces)
    if (homeKeys == HomeKeys.ChoosingArt && repeatCount > 0 &&
        (keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_BUTTON_R2)) return null
    val roles = actionFaces(faces)
    val back = keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == faces.backKey
    // Back must not repeat into the surface it just revealed, including across activities.
    // Options must not repeat either: a held press would open a menu and run its chord.
    // Both follow calibration rather than a fixed face button.
    if (repeatCount > 0 && (keyCode in shoulderKeys || keyCode == roles.options || back)) {
        return null
    }
    // The options button puts a held tile down as confirm does, so options then options lifts
    // and options drops without moving a thumb. That is Y unless calibration took Y, and a Y
    // made the back key cancels, as the hint says. A held tile drops once per press.
    if (homeKeys == HomeKeys.Moving && (keyCode == roles.options || keyCode == faces.confirmKey)) {
        return if (repeatCount == 0) Meaning.Activate else null
    }
    val bottomScreens = homeKeys == HomeKeys.Idle || homeKeys == HomeKeys.AllLibrary ||
        homeKeys == HomeKeys.ChoosingArt || homeKeys == HomeKeys.FindResults || homeKeys == HomeKeys.Selecting
    val optionScreens = homeKeys == HomeKeys.Idle || homeKeys == HomeKeys.AllLibrary ||
        homeKeys == HomeKeys.FindResults || homeKeys == HomeKeys.Selecting
    return when {
        keyCode == KeyEvent.KEYCODE_DPAD_UP -> Meaning.MoveUp
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> Meaning.MoveDown
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> Meaning.MoveLeft
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> Meaning.MoveRight
        keyCode == faces.confirmKey ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER -> Meaning.Activate
        back ->
            if (homeKeys == HomeKeys.Moving || homeKeys == HomeKeys.PickingIcon) Meaning.CancelHold else Meaning.Back
        // A keyboard has no shoulders. The bracket keys sit where L1 and R1 would.
        keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_LEFT_BRACKET -> Meaning.LeftPanel
        keyCode == KeyEvent.KEYCODE_BUTTON_R1 || keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET -> Meaning.RightPanel
        keyCode == roles.bottom && bottomScreens -> Meaning.ActivateBottom
        keyCode == roles.options && optionScreens -> Meaning.Options
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

/** A launch still waiting to start is dropped by a key that opens something over it, Find's Select included, or by B. */
fun cancelsPendingLaunch(meaning: Meaning, dialogOpen: Boolean, conflictOpen: Boolean): Boolean =
    !dialogOpen && !conflictOpen && (opensTopMenu(meaning) || meaning == Meaning.View || meaning == Meaning.Back)

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

/**
 * Which face button launches on the bottom screen, and which opens a menu.
 * Confirm and back keep the calibrated keys. Bottom stays on X and options stays
 * on Y while those are free. A role whose button was taken moves to A, then B,
 * the same way [typingFaces] moves erase and space. The hint must name these keys.
 */
data class ActionFaces(val confirm: Int, val back: Int, val bottom: Int, val options: Int) {
    val bottomGlyph: PromptKey get() = promptKeyOf(bottom) ?: PromptKey.FaceX
    val optionsGlyph: PromptKey get() = promptKeyOf(options) ?: PromptKey.FaceY
}

fun actionFaces(faces: FaceMap): ActionFaces {
    val free = listOf(
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
    ).filter { it != faces.confirmKey && it != faces.backKey }
    val bottom = KeyEvent.KEYCODE_BUTTON_X.takeIf { it in free }
    val options = KeyEvent.KEYCODE_BUTTON_Y.takeIf { it in free }
    val rest = (free - listOfNotNull(bottom, options)).iterator()
    return ActionFaces(
        confirm = faces.confirmKey,
        back = faces.backKey,
        bottom = bottom ?: rest.next(),
        options = options ?: rest.next(),
    )
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

/** Find's keyboard: [typingMeaningOf], with the triggers stepping filters and Select closing. */
private fun findingMeaningOf(keyCode: Int, repeatCount: Int, faces: FaceMap): Meaning? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_L2 -> Meaning.LetterBackward.takeIf { repeatCount == 0 }
    KeyEvent.KEYCODE_BUTTON_R2 -> Meaning.LetterForward.takeIf { repeatCount == 0 }
    KeyEvent.KEYCODE_BUTTON_SELECT -> Meaning.View.takeIf { repeatCount == 0 }
    else -> typingMeaningOf(keyCode, repeatCount, faces)
}
