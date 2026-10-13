package app.foldcade.language

import kotlin.math.roundToInt

/**
 * The R1 brightness slider. It holds a slider position from 0 to 100. The
 * stored level, and the Thor's backlight under it, follow the position in a
 * straight line, as AYN's own brightness menu does, so a percent here reads
 * the same as a percent there.
 *
 * One slider sets both screens. Android stores the level for the default
 * display only; the other screen is set through AYN's settings service.
 */
object BrightnessCopy {
    const val row = "Brightness"
    const val promptTitle = "Change brightness from Foldcade"
    const val promptBody =
        "Android asks once. Turn on \"Modify system settings\" for Foldcade, then come back."
    const val allow = "Allow"
    const val adjust = "Brightness"

    fun percent(position: Int): String = "${position.coerceIn(0, 100)}%"
}

/** One D-pad press moves the slider this far. */
const val BRIGHTNESS_STEP = 5

/** Android's lowest manual level. Zero can switch some panels off. */
const val BRIGHTNESS_MIN_LEVEL = 1
const val BRIGHTNESS_MAX_LEVEL = 255

/** The `Settings.System.SCREEN_BRIGHTNESS` level for a slider [position]. */
fun brightnessLevel(position: Int): Int {
    val fraction = position.coerceIn(0, 100) / 100f
    return (BRIGHTNESS_MIN_LEVEL + fraction * (BRIGHTNESS_MAX_LEVEL - BRIGHTNESS_MIN_LEVEL)).roundToInt()
}

/** A stored [level] as Android's brightness, 0 to 1. */
fun brightnessFraction(level: Int): Float {
    val span = (BRIGHTNESS_MAX_LEVEL - BRIGHTNESS_MIN_LEVEL).toFloat()
    return ((level - BRIGHTNESS_MIN_LEVEL) / span).coerceIn(0f, 1f)
}

/** The slider position that shows a stored [level]. */
fun brightnessPosition(level: Int): Int = (brightnessFraction(level) * 100f).roundToInt()

/** One step along the slider. The ends hold. */
fun steppedBrightness(position: Int, direction: Int): Int =
    (position + direction * BRIGHTNESS_STEP).coerceIn(0, 100)

/** Asked when the row is used before Android lets Foldcade write the setting. */
fun brightnessPrompt(screen: HostScreen = HostScreen.Top): DialogState = DialogState(
    kind = DialogKind.Brightness,
    title = BrightnessCopy.promptTitle,
    body = BrightnessCopy.promptBody,
    buttons = listOf(DialogButton.Allow, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
    screen = screen,
)
