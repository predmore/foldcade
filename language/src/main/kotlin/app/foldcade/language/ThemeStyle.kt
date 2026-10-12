package app.foldcade.language

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*

/** Resolve defaults and legal token domains once; invalid values never reach native controls. */
class ThemeStyle(val fields: JsonObject = JsonObject(emptyMap()), report: (ThemeDiagnostic) -> Unit = {}) {
    private val reader = ThemeStyleReader(fields, report)
    val metrics = ThemeMetrics(reader)
    val glow = ThemeGlow(reader)
    val focusRing = ThemeFocusRing(reader)
    val type = ThemeTypography(reader)
    val motion = ThemeMotion(reader)
}

internal class ThemeStyleReader(private val fields: JsonObject, private val report: (ThemeDiagnostic) -> Unit) {
    private fun field(group: String, name: String) = (fields[group] as? JsonObject)?.get(name)
    private fun invalid(group: String, name: String) = report(ThemeDiagnostic("theme.json:style.$group.$name", "Invalid token; using the default"))
    fun number(group: String, name: String, fallback: Float, valid: (Float) -> Boolean = { it >= 0f }): Float {
        val raw = field(group, name) ?: return fallback
        val value = (raw as? JsonPrimitive)?.floatOrNull
        if (value != null && value.isFinite() && valid(value)) return value
        invalid(group, name)
        return fallback
    }
    fun color(group: String, name: String): Color? {
        val raw = field(group, name) ?: return null
        val color = (raw as? JsonPrimitive)?.contentOrNull?.let(::themeColor)
        if (color == null) invalid(group, name)
        return color
    }
    fun curve(name: String, fallback: FloatArray): FloatArray {
        val raw = field("motion", name) ?: return fallback
        val values = (raw as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.floatOrNull?.takeIf(Float::isFinite) }
        if ((raw as? JsonArray)?.size == 4 && values != null && values.size == 4 && values[0] in 0f..1f && values[2] in 0f..1f) return values.toFloatArray()
        invalid("motion", name)
        return fallback
    }
}

class ThemeMetrics internal constructor(reader: ThemeStyleReader) {
    val insetFraction = reader.number("metrics", "insetFraction", Metrics.insetFraction) { it in 0f..0.5f }
    val gapFraction = reader.number("metrics", "gapFraction", Metrics.gapFraction)
    val focusStrokePx = reader.number("metrics", "focusStrokePx", Metrics.focusStrokePx)
    val heroInsetPx = reader.number("metrics", "heroInsetPx", Metrics.heroInsetPx)
    val dialogInsetPx = reader.number("metrics", "dialogInsetPx", Metrics.dialogInsetPx)
    val dialogBorderPx = reader.number("metrics", "dialogBorderPx", Metrics.dialogBorderPx)
    val cardCornerDp = reader.number("metrics", "cardCornerDp", Metrics.cardCornerDp)
    val heroArtFraction = reader.number("metrics", "heroArtFraction", Metrics.heroArtFraction) { it > 0f && it <= 1f }
    val chromeClearancePx = reader.number("metrics", "chromeClearancePx", Metrics.chromeClearancePx)
}

class ThemeGlow internal constructor(reader: ThemeStyleReader) {
    val focusOverflow = reader.number("glow", "focusOverflow", 64f)
    val restOverflow = reader.number("glow", "restOverflow", 18f)
    val restTightness = reader.number("glow", "restTightness", 2.4f)
    val focusTightness = reader.number("glow", "focusTightness", .85f)
    val restRim = reader.number("glow", "restRim", .42f) { it >= 0f && it < 1f }
    val focusRim = reader.number("glow", "focusRim", .66f) { it >= 0f && it < 1f }
    val restPeak = reader.number("glow", "restPeak", .22f) { it in 0f..1f }
    val focusPeak = reader.number("glow", "focusPeak", 1f) { it in 0f..1f }
    val heroPeak = reader.number("glow", "heroPeak", .45f) { it in 0f..1f }
}

class ThemeFocusRing internal constructor(reader: ThemeStyleReader) {
    val start = reader.color("focusRing", "start")
    val end = reader.color("focusRing", "end")
    val stroke = reader.number("focusRing", "stroke", 6f)
    val gap = reader.number("focusRing", "gap", 2f)
    val glowSpread = reader.number("focusRing", "glowSpread", 10f)
    val glowAlpha = reader.number("focusRing", "glowAlpha", .10f) { it in 0f..1f }
    val glowSteps = reader.number("focusRing", "glowSteps", 4f) { it >= 0f && it.toDouble() <= Int.MAX_VALUE && it % 1f == 0f }.toInt()
    val pronouncedStroke = reader.number("focusRing", "pronouncedStroke", 10f)
    val pronouncedSpread = reader.number("focusRing", "pronouncedSpread", 22f)
    val pronouncedAlpha = reader.number("focusRing", "pronouncedAlpha", .22f) { it in 0f..1f }
}

class ThemeTypography internal constructor(reader: ThemeStyleReader) {
    val heroTitle = reader.number("type", "heroTitle", TypeRamp.heroTitle.value) { it > 0f }.sp
    val conflictGame = reader.number("type", "conflictGame", TypeRamp.conflictGame.value) { it > 0f }.sp
    val heroMeta = reader.number("type", "heroMeta", TypeRamp.heroMeta.value) { it > 0f }.sp
    val availability = reader.number("type", "availability", TypeRamp.availability.value) { it > 0f }.sp
    val gridLabel = reader.number("type", "gridLabel", TypeRamp.gridLabel.value) { it > 0f }.sp
    val dialogTitle = reader.number("type", "dialogTitle", TypeRamp.dialogTitle.value) { it > 0f }.sp
    val dialogBody = reader.number("type", "dialogBody", TypeRamp.dialogBody.value) { it > 0f }.sp
    val menuRow = reader.number("type", "menuRow", TypeRamp.menuRow.value) { it > 0f }.sp
    val sideRow = reader.number("type", "sideRow", TypeRamp.sideRow.value) { it > 0f }.sp
    val hint = reader.number("type", "hint", TypeRamp.hint.value) { it > 0f }.sp
    val clock = reader.number("type", "clock", TypeRamp.clock.value) { it > 0f }.sp
}

class ThemeMotion internal constructor(reader: ThemeStyleReader) {
    val durationShort = reader.number("motion", "durationShort", 100f) { it >= 1f && it.toDouble() <= Int.MAX_VALUE }.toInt()
    val durationFocus = reader.number("motion", "durationFocus", 180f) { it >= 1f && it.toDouble() <= Int.MAX_VALUE }.toInt()
    val durationTravel = reader.number("motion", "durationTravel", 200f) { it >= 1f && it.toDouble() <= Int.MAX_VALUE }.toInt()
    val durationIsland = reader.number("motion", "durationIsland", 320f) { it >= 1f && it.toDouble() <= Int.MAX_VALUE }.toInt()
    val durationPulse = reader.number("motion", "durationPulse", 720f) { it >= 1f && it.toDouble() <= Int.MAX_VALUE }.toInt()
    val durationSyncMark = reader.number("motion", "durationSyncMark", 2400f) { it >= 1f && it.toDouble() <= Int.MAX_VALUE }.toInt()
    val scaleRest = reader.number("motion", "scaleRest", 1f) { it > 0f }
    val scaleFocus = reader.number("motion", "scaleFocus", 1.05f) { it > 0f }
    val heroScaleFrom = reader.number("motion", "heroScaleFrom", .96f) { it > 0f }
    val heroSlideUpPx = reader.number("motion", "heroSlideUpPx", 24f) { true }
    val heroTravelPx = reader.number("motion", "heroTravelPx", 64f) { true }
    val sceneDepth = reader.number("motion", "sceneDepth", .35f) { true }
    val sceneOverscan = reader.number("motion", "sceneOverscan", 1.05f) { it > 0f }
    val sceneSettleFrom = reader.number("motion", "sceneSettleFrom", 1.1f) { it > 0f }
    val durationScene = reader.number("motion", "durationScene", 360f) { it >= 1f && it.toDouble() <= Int.MAX_VALUE }.toInt()
    val durationShowcase = reader.number("motion", "durationShowcase", 1400f) { it >= 1f && it.toDouble() <= Int.MAX_VALUE }.toInt()
    val durationSelect = reader.number("motion", "durationSelect", 280f) { it >= 1f && it.toDouble() <= Int.MAX_VALUE }.toInt()
    val arrive = reader.curve("arrive", floatArrayOf(.05f, .7f, .1f, 1f))
    val leave = reader.curve("leave", floatArrayOf(.3f, 0f, .8f, .15f))
    val pulse = reader.curve("pulse", floatArrayOf(.4f, 0f, .6f, 1f))
    val dissolve = reader.curve("dissolve", floatArrayOf(.2f, 0f, 0f, 1f))
    val playful = reader.curve("playful", floatArrayOf(.34f, 1.36f, .64f, 1f))
}
