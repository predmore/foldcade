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
    /** The name field for a folder is open. */
    Renaming,
}

/** Keys that act once per press. Holding one does not repeat it. */
private val shoulderKeys = setOf(
    KeyEvent.KEYCODE_BUTTON_L1,
    KeyEvent.KEYCODE_BUTTON_R1,
    KeyEvent.KEYCODE_LEFT_BRACKET,
    KeyEvent.KEYCODE_RIGHT_BRACKET,
    KeyEvent.KEYCODE_BUTTON_START,
    KeyEvent.KEYCODE_BUTTON_SELECT,
)

fun meaningOf(
    keyCode: Int,
    repeatCount: Int = 0,
    faces: FaceMap = FaceMap.standard(),
    homeKeys: HomeKeys = HomeKeys.Idle,
): Meaning? {
    if (homeKeys == HomeKeys.ChoosingArt && repeatCount > 0 &&
        (keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_BUTTON_R2)) return null
    if (repeatCount > 0 && keyCode in shoulderKeys) {
        return null
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
        keyCode == KeyEvent.KEYCODE_BUTTON_START && homeKeys != HomeKeys.Renaming -> Meaning.Menu
        keyCode == KeyEvent.KEYCODE_BUTTON_SELECT && homeKeys != HomeKeys.Renaming -> Meaning.View
        (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) &&
            homeKeys != HomeKeys.Renaming -> Meaning.Dismiss
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

fun cancelsPendingLaunch(meaning: Meaning, dialogOpen: Boolean, conflictOpen: Boolean): Boolean =
    !dialogOpen && !conflictOpen && (opensTopMenu(meaning) || meaning == Meaning.Back)
