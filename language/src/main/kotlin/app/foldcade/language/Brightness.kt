package app.foldcade.language

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The R1 brightness slider. It holds a slider position from 0 to 100. The
 * stored level, and the Thor's backlight under it, follow the position in a
 * straight line, as AYN's own brightness menu does, so a percent here reads
 * the same as a percent there.
 *
 * One slider sets both screens. Android stores the level for the default
 * display only; the other screen is set through AYN's settings service. With a
 * device plugin that sets the bottom panel's own level, the one slider writes
 * the top through Android and the bottom through the plugin, at the same
 * position. A on it splits it into Top and Bottom ([Row.BottomBrightness]) for
 * a while, and A on either links them again ([brightnessSplit]).
 */
object BrightnessCopy {
    const val row = "Brightness"
    const val promptTitle = "Change brightness from Foldcade"
    const val promptBody =
        "Turn on “Modify system settings” for Foldcade, then come back."
    const val allow = "Allow"
    const val adjust = "Brightness"

    /** A on the one slider: a slider for each screen. */
    const val split = "Split"

    /** A on Top or Bottom: one slider again, at this one's level. */
    const val link = "Link"

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

/** Two screens this many points apart or fewer read as the same level, whatever each rounded to. */
const val BRIGHTNESS_MATCH = 1

/**
 * True while R1 shows a slider for each screen: the device plugin sets the bottom's own level, and
 * the player split the slider while R1 was open ([PickerModel.brightnessUnlinked]), or the screens
 * really are at different levels. R1 never shows one slider for two screens that differ.
 */
fun PickerModel.brightnessSplit(): Boolean {
    val bottom = device?.takeIf { it.has(DeviceCapability.Brightness) }?.brightnessBottom ?: return false
    return brightnessUnlinked || abs(brightness - bottomBrightnessPosition(bottom)) > BRIGHTNESS_MATCH
}

/** True while the one slider drives both screens through the device plugin. */
fun PickerModel.brightnessLinked(): Boolean = deviceHas(DeviceCapability.Brightness) && !brightnessSplit()

/** A split lasts the R1 visit it was made in: once R1 has closed, the next one opens linked if the screens match. */
fun PickerModel.brightnessSplitSettled(): PickerModel =
    if (brightnessUnlinked && panel?.side != Side.Right) copy(brightnessUnlinked = false) else this

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
