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
}

/** Extra keys that exist only on the home grid. Idle keeps the shared map. */
enum class HomeKeys {
    Idle,
    /** A tile picked up with Move is following the D-pad. */
    Moving,
    AllLibrary,
    /** The icon picker for a folder the user made is open. */
    PickingIcon,
    /** The name field for a folder is open. */
    Renaming,
}

fun meaningOf(
    keyCode: Int,
    repeatCount: Int = 0,
    faces: FaceMap = FaceMap.standard(),
    homeKeys: HomeKeys = HomeKeys.Idle,
): Meaning? {
    if (repeatCount > 0 && (keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1)) {
        return null
    }
    if (homeKeys == HomeKeys.AllLibrary && repeatCount > 0) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return Meaning.LetterForward
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return Meaning.LetterBackward
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
        keyCode == KeyEvent.KEYCODE_BACK || keyCode == faces.backKey ->
            if (homeKeys == HomeKeys.Moving || homeKeys == HomeKeys.PickingIcon) Meaning.CancelHold else Meaning.Back
        keyCode == KeyEvent.KEYCODE_BUTTON_L1 -> Meaning.LeftPanel
        keyCode == KeyEvent.KEYCODE_BUTTON_R1 -> Meaning.RightPanel
        keyCode == KeyEvent.KEYCODE_BUTTON_X && (homeKeys == HomeKeys.Idle || homeKeys == HomeKeys.AllLibrary) ->
            Meaning.ActivateBottom
        keyCode == KeyEvent.KEYCODE_BUTTON_Y && (homeKeys == HomeKeys.Idle || homeKeys == HomeKeys.AllLibrary) ->
            Meaning.Options
        keyCode == KeyEvent.KEYCODE_BUTTON_L2 && homeKeys == HomeKeys.AllLibrary -> Meaning.LetterBackward
        keyCode == KeyEvent.KEYCODE_BUTTON_R2 && homeKeys == HomeKeys.AllLibrary -> Meaning.LetterForward
        else -> null
    }
}
