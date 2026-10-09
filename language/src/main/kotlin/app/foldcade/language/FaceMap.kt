package app.foldcade.language

import android.view.KeyEvent

/** Physical letter on a face cap. Not a hardcoded prompt string. */
enum class FaceLetter { A, B, X, Y }

/** Spot on the face diamond. The Thor manual puts X on top, Y on the left, A on the right, B on the bottom. */
enum class DiamondPoint { Top, Left, Right, Bottom }

/** Which supplied diamond art matches the positions. */
enum class FaceSet { N, X, Neither }

enum class PromptKey {
    FaceA,
    FaceB,
    FaceX,
    FaceY,
    Select,
    Start,
    Home,
    Back,
    L1,
    R1,
    ;

    val face: Boolean
        get() = this == FaceA || this == FaceB || this == FaceX || this == FaceY
}

/** Readable Thor controller style. An unreadable value stays null. */
enum class ThorStyle { Standard, Xbox }

enum class ClusterRole { Select, Start, Home, Back }

/**
 * Which key confirms, which key goes back, and where each letter sits.
 * The default is the Thor manual: X top, Y left, A right, B bottom, confirm on A, back on B.
 */
data class FaceMap(
    val confirmKey: Int,
    val backKey: Int,
    val positions: Map<FaceLetter, DiamondPoint>,
) {
    val set: FaceSet get() = classifyFaceSet(positions)

    companion object {
        fun standard(): FaceMap = FaceMap(
            confirmKey = KeyEvent.KEYCODE_BUTTON_A,
            backKey = KeyEvent.KEYCODE_BUTTON_B,
            positions = N_POSITIONS,
        )
    }
}

/** Thor default. `ic_btn_face_diamond_n_*`. */
val N_POSITIONS: Map<FaceLetter, DiamondPoint> = mapOf(
    FaceLetter.X to DiamondPoint.Top,
    FaceLetter.Y to DiamondPoint.Left,
    FaceLetter.A to DiamondPoint.Right,
    FaceLetter.B to DiamondPoint.Bottom,
)

/** Alternate order. `ic_btn_face_diamond_x` is this set's base, not a lit X. */
val X_POSITIONS: Map<FaceLetter, DiamondPoint> = mapOf(
    FaceLetter.Y to DiamondPoint.Top,
    FaceLetter.X to DiamondPoint.Left,
    FaceLetter.B to DiamondPoint.Right,
    FaceLetter.A to DiamondPoint.Bottom,
)

fun classifyFaceSet(positions: Map<FaceLetter, DiamondPoint>): FaceSet = when {
    positions == N_POSITIONS -> FaceSet.N
    positions == X_POSITIONS -> FaceSet.X
    else -> FaceSet.Neither
}

fun interpretThorStyle(raw: String?): ThorStyle? {
    val text = raw?.trim()?.lowercase() ?: return null
    return when (text) {
        "standard", "thor", "nintendo" -> ThorStyle.Standard
        "xbox", "swap" -> ThorStyle.Xbox
        else -> null
    }
}

fun letterOfKey(keyCode: Int): FaceLetter? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A -> FaceLetter.A
    KeyEvent.KEYCODE_BUTTON_B -> FaceLetter.B
    KeyEvent.KEYCODE_BUTTON_X -> FaceLetter.X
    KeyEvent.KEYCODE_BUTTON_Y -> FaceLetter.Y
    else -> null
}

fun promptKeyOf(keyCode: Int): PromptKey? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A -> PromptKey.FaceA
    KeyEvent.KEYCODE_BUTTON_B -> PromptKey.FaceB
    KeyEvent.KEYCODE_BUTTON_X -> PromptKey.FaceX
    KeyEvent.KEYCODE_BUTTON_Y -> PromptKey.FaceY
    KeyEvent.KEYCODE_BUTTON_SELECT -> PromptKey.Select
    KeyEvent.KEYCODE_BUTTON_START -> PromptKey.Start
    KeyEvent.KEYCODE_HOME -> PromptKey.Home
    KeyEvent.KEYCODE_BACK -> PromptKey.Back
    KeyEvent.KEYCODE_BUTTON_L1 -> PromptKey.L1
    KeyEvent.KEYCODE_BUTTON_R1 -> PromptKey.R1
    else -> null
}

fun keyOfPrompt(key: PromptKey): Int = when (key) {
    PromptKey.FaceA -> KeyEvent.KEYCODE_BUTTON_A
    PromptKey.FaceB -> KeyEvent.KEYCODE_BUTTON_B
    PromptKey.FaceX -> KeyEvent.KEYCODE_BUTTON_X
    PromptKey.FaceY -> KeyEvent.KEYCODE_BUTTON_Y
    PromptKey.Select -> KeyEvent.KEYCODE_BUTTON_SELECT
    PromptKey.Start -> KeyEvent.KEYCODE_BUTTON_START
    PromptKey.Home -> KeyEvent.KEYCODE_HOME
    PromptKey.Back -> KeyEvent.KEYCODE_BACK
    PromptKey.L1 -> KeyEvent.KEYCODE_BUTTON_L1
    PromptKey.R1 -> KeyEvent.KEYCODE_BUTTON_R1
}

fun backKeyFor(confirmKey: Int): Int = when (confirmKey) {
    KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
    KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
    KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
    KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
    else -> KeyEvent.KEYCODE_BUTTON_B
}

/** Style picks n or x. A saved confirm press remaps which key is confirm and keeps those positions. */
fun resolveFaceMap(style: ThorStyle?, confirm: PromptKey?): FaceMap {
    val positions = if (style == ThorStyle.Xbox) X_POSITIONS else N_POSITIONS
    val confirmKey = confirm?.let(::keyOfPrompt)?.takeIf { letterOfKey(it) != null }
        ?: KeyEvent.KEYCODE_BUTTON_A
    return FaceMap(confirmKey, backKeyFor(confirmKey), positions)
}

fun faceMapWithConfirm(current: FaceMap, confirm: PromptKey): FaceMap {
    val key = keyOfPrompt(confirm)
    if (letterOfKey(key) == null) return current
    return current.copy(confirmKey = key, backKey = backKeyFor(key))
}

data class FaceArt(val diamond: String, val letter: String?)

/**
 * Diamond for [letter] on [map].
 * N and X sets use that set's lit glyph. Any other layout uses the blank diamond at the letter's
 * position plus the letter glyph. [filled] selects the pressed twin.
 */
fun faceArt(letter: FaceLetter, map: FaceMap, filled: Boolean): FaceArt {
    val mark = letter.name.lowercase()
    val point = map.positions[letter] ?: DiamondPoint.Right
    val diamond = when (map.set) {
        FaceSet.N -> "ic_btn_face_diamond_n_$mark"
        FaceSet.X -> "ic_btn_face_diamond_x_$mark"
        FaceSet.Neither -> "ic_btn_face_diamond_blank_${point.name.lowercase()}"
    }
    val letterGlyph = if (map.set == FaceSet.Neither) "ic_btn_$mark" else null
    return FaceArt(
        diamond = if (filled) "${diamond}_filled" else diamond,
        letter = letterGlyph?.let { if (filled) "${it}_filled" else it },
    )
}

fun promptedLetter(confirm: Boolean, back: Boolean, map: FaceMap): FaceLetter? {
    val key = when {
        confirm -> map.confirmKey
        back -> map.backKey
        else -> return null
    }
    return letterOfKey(key)
}

fun clusterGlyph(role: ClusterRole, thor: Boolean, filled: Boolean): String {
    val base = when (role) {
        ClusterRole.Select -> if (thor) "ic_btn_square" else "ic_btn_select"
        ClusterRole.Start -> if (thor) "ic_btn_triangle" else "ic_btn_start"
        ClusterRole.Home -> "ic_btn_home"
        ClusterRole.Back -> "ic_btn_back"
    }
    return if (filled) "${base}_filled" else base
}

fun clusterFilled(role: ClusterRole, held: Set<PromptKey>, map: FaceMap): Boolean = when (role) {
    ClusterRole.Select -> PromptKey.Select in held
    ClusterRole.Start -> PromptKey.Start in held
    ClusterRole.Home -> PromptKey.Home in held
    ClusterRole.Back -> PromptKey.Back in held || promptKeyOf(map.backKey)?.let { it in held } == true
}

/**
 * One-time L1 row. Never on the first launch, never when the style setting was readable,
 * and never again for this device after a press or a dismiss.
 */
fun offerButtonCalibration(
    firstSession: Boolean,
    styleKnown: Boolean,
    dismissed: Boolean,
    calibrated: Boolean,
): Boolean = !firstSession && !styleKnown && !dismissed && !calibrated

/**
 * Closed-island chips. The menu-morph pull request cross-fades [LEFT] to [LEFT_FILLED]
 * and [RIGHT] to [RIGHT_FILLED] while that menu is open. This screen does not hard-swap them.
 * A held shoulder uses the filled twin for the press itself.
 */
object IslandChip {
    const val LEFT = "ic_btn_l1"
    const val LEFT_FILLED = "ic_btn_l1_filled"
    const val RIGHT = "ic_btn_r1"
    const val RIGHT_FILLED = "ic_btn_r1_filled"
}

fun islandGlyph(left: Boolean, held: Set<PromptKey>): String {
    val down = if (left) PromptKey.L1 in held else PromptKey.R1 in held
    return when {
        left && down -> IslandChip.LEFT_FILLED
        left -> IslandChip.LEFT
        down -> IslandChip.RIGHT_FILLED
        else -> IslandChip.RIGHT
    }
}
