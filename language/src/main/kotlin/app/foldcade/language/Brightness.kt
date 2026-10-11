package app.foldcade.language

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The R1 brightness slider. It holds a slider position from 0 to 100, not the
 * raw level, so equal steps look equally bright. The curve is Android's own
 * slider curve (`BrightnessUtils`), so the system slider and this one agree.
 *
 * Android stores the level for the default display only. On the Thor that is
 * the top screen. The bottom screen takes a privileged call, so it is not here.
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

private const val R = 0.5f
private const val A = 0.17883277f
private const val B = 0.28466892f
private const val C = 0.55991073f

/** The `Settings.System.SCREEN_BRIGHTNESS` level for a slider [position]. */
fun brightnessLevel(position: Int): Int {
    val ratio = position.coerceIn(0, 100) / 100f
    val linear = if (ratio <= R) (ratio / R) * (ratio / R) else exp((ratio - C) / A) + B
    val fraction = (linear / 12f).coerceIn(0f, 1f)
    return (BRIGHTNESS_MIN_LEVEL + fraction * (BRIGHTNESS_MAX_LEVEL - BRIGHTNESS_MIN_LEVEL)).roundToInt()
}

/** The slider position that shows a stored [level]. */
fun brightnessPosition(level: Int): Int {
    val span = (BRIGHTNESS_MAX_LEVEL - BRIGHTNESS_MIN_LEVEL).toFloat()
    val fraction = ((level - BRIGHTNESS_MIN_LEVEL) / span).coerceIn(0f, 1f)
    val normal = fraction * 12f
    val ratio = if (normal <= 1f) R * sqrt(normal) else A * ln(normal - B) + C
    return (ratio * 100f).roundToInt().coerceIn(0, 100)
}

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
