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
    Back,
    LeftPanel,
    RightPanel,
    /** Grid swipe only. L1 and R1 do not page. */
    PageTowardStart,
    /** Grid swipe only. L1 and R1 do not page. */
    PageTowardEnd,
}

fun meaningOf(keyCode: Int, repeatCount: Int = 0, faces: FaceMap = FaceMap.standard()): Meaning? {
    if (repeatCount > 0 && (keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1)) {
        return null
    }
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> Meaning.MoveUp
        KeyEvent.KEYCODE_DPAD_DOWN -> Meaning.MoveDown
        KeyEvent.KEYCODE_DPAD_LEFT -> Meaning.MoveLeft
        KeyEvent.KEYCODE_DPAD_RIGHT -> Meaning.MoveRight
        faces.confirmKey,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> Meaning.Activate
        faces.backKey,
        KeyEvent.KEYCODE_BACK,
        -> Meaning.Back
        KeyEvent.KEYCODE_BUTTON_L1 -> Meaning.LeftPanel
        KeyEvent.KEYCODE_BUTTON_R1 -> Meaning.RightPanel
        else -> null
    }
}
