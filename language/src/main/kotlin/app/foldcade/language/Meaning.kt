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
    /** B while editing home. Puts the held tile back. */
    CancelHold,
    /** Start or Back leaves edit mode. */
    LeaveEdit,
    /** X while editing. Hides a tile or spills a folder. The game stays in All. */
    RemoveFromHome,
    /** Y while editing. New folder, or rename when a folder is focused. */
    MakeFolder,
    /** Select while editing a folder the user made. Opens its icon picker. */
    FolderIcon,
    /** Y in All. First free slot, or the chosen folder. */
    AddToHome,
    /** X in All. Cycles the folder an add will use. */
    CycleDestination,
    LetterForward,
    LetterBackward,
}

/** Extra keys that exist only on the home grid. Idle keeps the shared map. */
enum class HomeKeys {
    Idle,
    Editing,
    AllLibrary,
    /** The icon picker for a folder the user made is open. */
    PickingIcon,
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
        keyCode == KeyEvent.KEYCODE_BACK -> when (homeKeys) {
            HomeKeys.Editing -> Meaning.LeaveEdit
            HomeKeys.PickingIcon -> Meaning.CancelHold
            else -> Meaning.Back
        }
        keyCode == faces.backKey ->
            if (homeKeys == HomeKeys.Editing || homeKeys == HomeKeys.PickingIcon) Meaning.CancelHold else Meaning.Back
        keyCode == KeyEvent.KEYCODE_BUTTON_L1 -> Meaning.LeftPanel
        keyCode == KeyEvent.KEYCODE_BUTTON_R1 -> Meaning.RightPanel
        keyCode == KeyEvent.KEYCODE_BUTTON_START && homeKeys == HomeKeys.Editing -> Meaning.LeaveEdit
        keyCode == KeyEvent.KEYCODE_BUTTON_X && homeKeys == HomeKeys.Editing -> Meaning.RemoveFromHome
        keyCode == KeyEvent.KEYCODE_BUTTON_Y && homeKeys == HomeKeys.Editing -> Meaning.MakeFolder
        keyCode == KeyEvent.KEYCODE_BUTTON_SELECT && homeKeys == HomeKeys.Editing -> Meaning.FolderIcon
        keyCode == KeyEvent.KEYCODE_BUTTON_X && homeKeys == HomeKeys.AllLibrary -> Meaning.CycleDestination
        keyCode == KeyEvent.KEYCODE_BUTTON_Y && homeKeys == HomeKeys.AllLibrary -> Meaning.AddToHome
        keyCode == KeyEvent.KEYCODE_BUTTON_L2 && homeKeys == HomeKeys.AllLibrary -> Meaning.LetterBackward
        keyCode == KeyEvent.KEYCODE_BUTTON_R2 && homeKeys == HomeKeys.AllLibrary -> Meaning.LetterForward
        else -> null
    }
}
