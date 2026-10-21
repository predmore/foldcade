package app.foldcade.language

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*

/**
 * Resolve defaults and legal token domains once; invalid values never reach native controls.
 * Each token resolves from [fields], then from [inherit] (Afterglow's style, for every other pack),
 * then from the engine's own value. An invalid token in [fields] is reported and falls through.
 */
class ThemeStyle(
    val fields: JsonObject = JsonObject(emptyMap()),
    report: (ThemeDiagnostic) -> Unit = {},
    inherit: JsonObject = JsonObject(emptyMap()),
) {
    private val reader = ThemeStyleReader(fields, inherit, report)
    val metrics = ThemeMetrics(reader)
    val glow = ThemeGlow(reader)
    val focusRing = ThemeFocusRing(reader)
    val type = ThemeTypography(reader)
    val motion = ThemeMotion(reader)
    val colors = ThemeColors(reader)
    val shape = ThemeShape(reader)
    val tile = ThemeTile(reader)
    val hero = ThemeHero(reader)
    val menu = ThemeMenu(reader)
    val controls = ThemeControls(reader)
    val keyboard = ThemeKeyboard(reader)
    val field = ThemeField(reader)
    val dialog = ThemeDialog(reader)
    val settings = ThemeSettings(reader)
    val find = ThemeFind(reader)
    val collections = ThemeCollections(reader)
    val sound = ThemeSound(reader)

    /** Every token the engine reads, as `group.name`. */
    val tokens: Set<String> get() = reader.read

    /** Tokens no layer declared, which kept the engine's own value. Afterglow declares every one. */
    val defaulted: Set<String> get() = reader.defaulted

    /** Each token's resolved value, by `group.name`. */
    val values: Map<String, Any?> get() = reader.values

    init {
        // A misspelt token would otherwise be silently ignored.
        val groups = reader.read.map { it.substringBefore('.') }.toSet()
        fields.forEach { (group, values) ->
            if (group !in groups) report(ThemeDiagnostic("theme.json:style.$group", "Unknown token group"))
            else (values as? JsonObject)?.keys?.filter { "$group.$it" !in reader.read }?.forEach { name ->
                report(ThemeDiagnostic("theme.json:style.$group.$name", "Unknown token"))
            }
        }
    }
}

internal class ThemeStyleReader(
    private val fields: JsonObject,
    private val inherit: JsonObject,
    private val report: (ThemeDiagnostic) -> Unit,
) {
    val read = linkedSetOf<String>()
    val defaulted = linkedSetOf<String>()
    val values = linkedMapOf<String, Any?>()

    private fun field(layer: JsonObject, group: String, name: String) = (layer[group] as? JsonObject)?.get(name)
    private fun invalid(group: String, name: String) = report(ThemeDiagnostic("theme.json:style.$group.$name", "Invalid token; using the default"))

    /** The pack's value, else the inherited one, else [fallback]. Only the pack's own mistakes are reported. */
    private fun <T> layered(group: String, name: String, fallback: T, parse: (JsonElement) -> T?): T {
        val token = "$group.$name"
        read += token
        val value = field(fields, group, name)?.let { raw -> parse(raw) ?: null.also { invalid(group, name) } }
            ?: field(inherit, group, name)?.let(parse)
            ?: fallback.also { defaulted += token }
        values[token] = (value as? FloatArray)?.toList() ?: value
        return value
    }

    fun number(group: String, name: String, fallback: Float, valid: (Float) -> Boolean = { it >= 0f }): Float =
        layered(group, name, fallback) { raw -> (raw as? JsonPrimitive)?.floatOrNull?.takeIf { it.isFinite() && valid(it) } }

    fun fraction(group: String, name: String, fallback: Float): Float = number(group, name, fallback) { it in 0f..1f }

    fun duration(group: String, name: String, fallback: Int): Int =
        number(group, name, fallback.toFloat()) { it >= 1f && it.toDouble() <= Int.MAX_VALUE }.toInt()

    fun color(group: String, name: String, fallback: Color): Color = optionalColor(group, name, fallback)!!

    fun optionalColor(group: String, name: String, fallback: Color? = null): Color? =
        layered(group, name, fallback) { raw -> (raw as? JsonPrimitive)?.contentOrNull?.let(::themeColor) }

    /** CSS-style weights, 1 to 1000. */
    fun weight(group: String, name: String, fallback: FontWeight): FontWeight =
        FontWeight(number(group, name, fallback.weight.toFloat()) { it >= 1f && it <= 1000f && it % 1f == 0f }.toInt())

    fun curve(name: String, fallback: FloatArray): FloatArray = layered("motion", name, fallback) { raw ->
        val values = (raw as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.floatOrNull?.takeIf(Float::isFinite) }
        if ((raw as? JsonArray)?.size == 4 && values != null && values.size == 4 && values[0] in 0f..1f && values[2] in 0f..1f) values.toFloatArray()
        else null
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
    val restPeak = reader.fraction("glow", "restPeak", .22f)
    val focusPeak = reader.fraction("glow", "focusPeak", 1f)
    val heroPeak = reader.fraction("glow", "heroPeak", .45f)

    /** The light behind a mark on its tile: its centre, from the tile's top, as a share of the tile. */
    val markLightTop = reader.number("glow", "markLightTop", .12f) { true }
    val markLightReach = reader.number("glow", "markLightReach", .85f) { it > 0f }
    val markLightPeak = reader.fraction("glow", "markLightPeak", .34f)
    val markLightFade = reader.number("glow", "markLightFade", .72f) { it > 0f && it < 1f }
}

class ThemeFocusRing internal constructor(reader: ThemeStyleReader) {
    val start = reader.optionalColor("focusRing", "start")
    val end = reader.optionalColor("focusRing", "end")
    val stroke = reader.number("focusRing", "stroke", 6f)
    val gap = reader.number("focusRing", "gap", 2f)
    val glowSpread = reader.number("focusRing", "glowSpread", 10f)
    val glowAlpha = reader.fraction("focusRing", "glowAlpha", .10f)
    val glowSteps = reader.number("focusRing", "glowSteps", 4f) { it >= 0f && it.toDouble() <= Int.MAX_VALUE && it % 1f == 0f }.toInt()
    val pronouncedStroke = reader.number("focusRing", "pronouncedStroke", 10f)
    val pronouncedSpread = reader.number("focusRing", "pronouncedSpread", 22f)
    val pronouncedAlpha = reader.fraction("focusRing", "pronouncedAlpha", .22f)
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

    /** Running text: rows, hints, values. */
    val bodyWeight = reader.weight("type", "bodyWeight", FontWeight.Normal)

    /** Titles and the words that carry a screen: the hero's name, headers, the typed name, Done. */
    val strongWeight = reader.weight("type", "strongWeight", FontWeight.SemiBold)
}

class ThemeMotion internal constructor(private val reader: ThemeStyleReader) {
    private fun ms(name: String, fallback: Int) = reader.duration("motion", name, fallback)
    val durationShort = ms("durationShort", 100)
    val durationFocus = ms("durationFocus", 180)
    val durationTravel = ms("durationTravel", 200)
    val durationIsland = ms("durationIsland", 320)
    val durationPulse = ms("durationPulse", 720)
    val durationSyncMark = ms("durationSyncMark", 2400)
    val scaleRest = reader.number("motion", "scaleRest", 1f) { it > 0f }
    val scaleFocus = reader.number("motion", "scaleFocus", 1.05f) { it > 0f }
    val heroScaleFrom = reader.number("motion", "heroScaleFrom", .96f) { it > 0f }
    val heroSlideUpPx = reader.number("motion", "heroSlideUpPx", 24f) { true }
    val heroTravelPx = reader.number("motion", "heroTravelPx", 64f) { true }
    val sceneDepth = reader.number("motion", "sceneDepth", .35f) { true }
    val sceneOverscan = reader.number("motion", "sceneOverscan", 1.05f) { it > 0f }
    val sceneSettleFrom = reader.number("motion", "sceneSettleFrom", 1.1f) { it > 0f }
    val tiltPx = reader.number("motion", "tiltPx", 18f) { it >= 0f }
    val tiltDegrees = reader.number("motion", "tiltDegrees", 6f) { it > 0f }
    val tiltArtShare = reader.fraction("motion", "tiltArtShare", .4f)
    val durationTiltRecenter = ms("durationTiltRecenter", 2400)
    val durationScene = ms("durationScene", 360)
    val durationShowcase = ms("durationShowcase", 1400)
    val durationSelect = ms("durationSelect", 280)
    val durationBob = ms("durationBob", 1800)
    val durationLetterHold = ms("durationLetterHold", 360)
    val durationLetterFade = ms("durationLetterFade", 120)
    val scaleLift = reader.number("motion", "scaleLift", 1.1f) { it > 0f }
    val scaleOver = reader.number("motion", "scaleOver", .74f) { it > 0f }
    val scaleOpen = reader.number("motion", "scaleOpen", 1.14f) { it > 0f }
    val bobPx = reader.number("motion", "bobPx", 7f) { true }
    val liftGlowPx = reader.number("motion", "liftGlowPx", 30f) { true }
    val liftGlowPeak = reader.fraction("motion", "liftGlowPeak", .7f)
    val squash = reader.number("motion", "squash", .08f) { it in 0f..0.5f }
    val keySquash = reader.number("motion", "keySquash", .07f) { it in 0f..0.5f }
    val riseFrom = reader.number("motion", "riseFrom", .96f) { it > 0f }
    val riseDp = reader.number("motion", "riseDp", 24f) { true }
    val sinkScale = reader.number("motion", "sinkScale", .06f) { it in 0f..1f }
    val sinkDp = reader.number("motion", "sinkDp", 16f) { true }
    val cascadeDp = reader.number("motion", "cascadeDp", 14f) { true }
    val cascadeStep = reader.number("motion", "cascadeStep", .05f) { it in 0f..1f }
    val cascadeSpan = reader.number("motion", "cascadeSpan", .35f) { it > 0f && it <= 1f }
    val popFrom = reader.number("motion", "popFrom", .72f) { it > 0f }
    val arrive = reader.curve("arrive", floatArrayOf(.05f, .7f, .1f, 1f))
    val leave = reader.curve("leave", floatArrayOf(.3f, 0f, .8f, .15f))
    val pulse = reader.curve("pulse", floatArrayOf(.4f, 0f, .6f, 1f))
    val dissolve = reader.curve("dissolve", floatArrayOf(.2f, 0f, 0f, 1f))
    val playful = reader.curve("playful", floatArrayOf(.34f, 1.36f, .64f, 1f))
}

/** Colours beyond the manifest's five. */
class ThemeColors internal constructor(reader: ThemeStyleReader) {
    /** What dims the screen under a menu or dialog and shades art behind text. */
    val scrim = reader.color("colors", "scrim", Color(0xFF000000))

    /** The dark end of art's own shading: a glyph pressed into a folder, a swatch's base. */
    val shade = reader.color("colors", "shade", Color(0xFF000000))

    /** The light end: the lip under a pressed glyph, a swatch's top and its chosen rim. */
    val shine = reader.color("colors", "shine", Color(0xFFFFFFFF))
}

/** Corners. A `round` of 0.5 makes a pill or a circle; 0 makes the same control square. */
class ThemeShape internal constructor(reader: ThemeStyleReader) {
    /** Small controls, as a share of their shorter side: switches, sliders, chips, pills, dots, the caret. */
    val round = reader.number("shape", "round", .5f) { it in 0f..0.5f }

    /** The highlight that glides between menu and Settings rows, and the save cards. */
    val highlightCornerDp = reader.number("shape", "highlightCornerDp", 22f)

    /** A row's highlight in the command center's lists. */
    val rowCornerPx = reader.number("shape", "rowCornerPx", 22f)

    /** The largest corner of a text chip. */
    val chipCornerPx = reader.number("shape", "chipCornerPx", 44f)

    /** A keyboard key, as a share of the key's width. */
    val keyCorner = reader.number("shape", "keyCorner", .2f) { it in 0f..0.5f }

    /** An achievement badge, a toast, and an art candidate. */
    val badgeCornerPx = reader.number("shape", "badgeCornerPx", 8f)
    val toastCornerDp = reader.number("shape", "toastCornerDp", 8f)
    val thumbCornerDp = reader.number("shape", "thumbCornerDp", 12f)
}

/** A home grid tile beyond its corner (`iconRadius`) and glow. */
class ThemeTile internal constructor(reader: ThemeStyleReader) {
    /** How bright art rests when it is not focused. */
    val restAlpha = reader.fraction("tile", "restAlpha", .82f)
    val socketAlpha = reader.fraction("tile", "socketAlpha", .16f)
    /** The thin outline of a held tile's empty socket, and of a tile while Edit home is open. */
    val outlinePx = reader.number("tile", "outlinePx", 3f)
    /** The heavy outline of the tile Edit home is on, and of a carried gap. */
    val editStrokePx = reader.number("tile", "editStrokePx", 9f)
    val editRestAlpha = reader.fraction("tile", "editRestAlpha", .45f)
    val dotPx = reader.number("tile", "dotPx", 10f)
    val statusChipPx = reader.number("tile", "statusChipPx", 36f) { it > 0f }
    val statusIconPx = reader.number("tile", "statusIconPx", 24f) { it > 0f }
    val statusBackingAlpha = reader.fraction("tile", "statusBackingAlpha", .72f)
    val downloadBarPx = reader.number("tile", "downloadBarPx", 6f)
    val downloadTrackAlpha = reader.fraction("tile", "downloadTrackAlpha", .22f)
}

/** The top screen's selected item. */
class ThemeHero internal constructor(reader: ThemeStyleReader) {
    /** A mark drawn in the hero, as a share of its box. */
    val markScale = reader.number("hero", "markScale", .86f) { it > 0f && it <= 1f }

    /** The name card over game art. */
    val cardAlpha = reader.fraction("hero", "cardAlpha", .72f)
    val pillAlpha = reader.fraction("hero", "pillAlpha", .94f)
    /** The round shade behind a game's logo. Light, now the logo's own shadow keeps a dark one apart from its art. */
    val logoShadeAlpha = reader.fraction("hero", "logoShadeAlpha", .42f)

    /** The soft shadow a logo casts in its own shape, in the scrim's colour: its strength and its blur. */
    val logoShadowAlpha = reader.fraction("hero", "logoShadowAlpha", .85f)
    val logoShadowBlurDp = reader.number("hero", "logoShadowBlurDp", 10f) { it >= 0f }

    /**
     * A logo darker than this (0 black to 1 white, over its opaque pixels) casts its
     * shadow in the shine colour instead, as a glow: a dark shadow can't lift a dark
     * logo off art of its own hue.
     */
    val logoGlowBelow = reader.fraction("hero", "logoGlowBelow", .3f)
    val sceneShadeTop = reader.fraction("hero", "sceneShadeTop", .45f)
    val sceneShadeBottom = reader.fraction("hero", "sceneShadeBottom", .8f)
    val ambientBlurDp = reader.number("hero", "ambientBlurDp", 48f)
    val ambientDim = reader.fraction("hero", "ambientDim", .55f)
}

/** The L1, R1 and Y menus and their islands. */
class ThemeMenu internal constructor(reader: ThemeStyleReader) {
    val highlightStartAlpha = reader.fraction("menu", "highlightStartAlpha", .20f)
    val highlightEndAlpha = reader.fraction("menu", "highlightEndAlpha", .08f)
    val labelRestAlpha = reader.fraction("menu", "labelRestAlpha", .72f)
    val artRestAlpha = reader.fraction("menu", "artRestAlpha", .78f)
    val artGrow = reader.number("menu", "artGrow", .12f)
    /** The dim behind an open menu, over a blur, and without one on Android before 12. */
    val scrimAlpha = reader.fraction("menu", "scrimAlpha", .32f)
    val scrimAlphaUnblurred = reader.fraction("menu", "scrimAlphaUnblurred", .55f)
    val blurPx = reader.number("menu", "blurPx", 28f)
    val islandLift = reader.number("menu", "islandLift", .04f)
    val islandBorderDp = reader.number("menu", "islandBorderDp", 1f)
    val islandFocusBorderDp = reader.number("menu", "islandFocusBorderDp", 2f)
    val islandRimAlpha = reader.fraction("menu", "islandRimAlpha", .08f)
    val islandRimLitAlpha = reader.fraction("menu", "islandRimLitAlpha", .34f)
    val quickTileAlpha = reader.fraction("menu", "quickTileAlpha", .08f)
    val quickTileLitAlpha = reader.fraction("menu", "quickTileLitAlpha", .16f)
    val quickTileOnAlpha = reader.fraction("menu", "quickTileOnAlpha", .6f)
    val quickArtGrow = reader.number("menu", "quickArtGrow", .14f)
}

/** Switches, checkmarks and sliders. */
class ThemeControls internal constructor(reader: ThemeStyleReader) {
    val switchWidthDp = reader.number("controls", "switchWidthDp", 54f) { it > 0f }
    val switchHeightDp = reader.number("controls", "switchHeightDp", 30f) { it > 0f }
    val switchKnobInsetDp = reader.number("controls", "switchKnobInsetDp", 4f)
    val trackAlpha = reader.fraction("controls", "trackAlpha", .12f)
    val trackFocusAlpha = reader.fraction("controls", "trackFocusAlpha", .20f)
    val checkSizeDp = reader.number("controls", "checkSizeDp", 30f) { it > 0f }
    val checkStrokeDp = reader.number("controls", "checkStrokeDp", 2f)
    val checkRingAlpha = reader.fraction("controls", "checkRingAlpha", .26f)
    val checkRingFocusAlpha = reader.fraction("controls", "checkRingFocusAlpha", .42f)
    val sliderTrackDp = reader.number("controls", "sliderTrackDp", 6f) { it > 0f }
    val sliderTrackAlpha = reader.fraction("controls", "sliderTrackAlpha", .28f)
    val sliderKnobDp = reader.number("controls", "sliderKnobDp", 7f) { it > 0f }
    val sliderGlow = reader.number("controls", "sliderGlow", 2.2f) { it >= 1f }
}

/** The on-screen keyboard and its name chips. */
class ThemeKeyboard internal constructor(private val reader: ThemeStyleReader) {
    private fun alpha(name: String, fallback: Float) = reader.fraction("keyboard", name, fallback)
    val highlightStartAlpha = alpha("highlightStartAlpha", .26f)
    val highlightEndAlpha = alpha("highlightEndAlpha", .10f)
    /** How strong the highlight stays while the D-pad is elsewhere. */
    val inactiveHighlight = alpha("inactiveHighlight", .3f)
    val keyAlpha = alpha("keyAlpha", .92f)
    val glassAlpha = alpha("glassAlpha", .12f)
    val modifierGlassAlpha = alpha("modifierGlassAlpha", .05f)
    val litGlassAlpha = alpha("litGlassAlpha", .06f)
    val doneAlpha = alpha("doneAlpha", .55f)
    val shiftAlpha = alpha("shiftAlpha", .38f)
    val pressAlpha = alpha("pressAlpha", .22f)
    val sheenAlpha = alpha("sheenAlpha", .10f)
    val rimTopAlpha = alpha("rimTopAlpha", .22f)
    val rimBottomAlpha = alpha("rimBottomAlpha", .04f)
    val rimDp = reader.number("keyboard", "rimDp", 1f)
    val glyphRestAlpha = alpha("glyphRestAlpha", .62f)
    val chipAlpha = alpha("chipAlpha", .08f)
    val chipFlashAlpha = alpha("chipFlashAlpha", .20f)
    val chipRimAlpha = alpha("chipRimAlpha", .12f)
    val chipLabelRestAlpha = alpha("chipLabelRestAlpha", .82f)
}

/** The text field Find, rename and connect type into. */
class ThemeField internal constructor(reader: ThemeStyleReader) {
    val ringAlpha = reader.fraction("field", "ringAlpha", .55f)
    val selectionStartAlpha = reader.fraction("field", "selectionStartAlpha", .55f)
    val selectionEndAlpha = reader.fraction("field", "selectionEndAlpha", .40f)
    val caretDp = reader.number("field", "caretDp", 4f) { it > 0f }
    val caretGlowDp = reader.number("field", "caretGlowDp", 6f)
    val caretGlowAlpha = reader.fraction("field", "caretGlowAlpha", .22f)
    /** The caret's faintest, and a selection's, as they breathe. */
    val caretLowAlpha = reader.fraction("field", "caretLowAlpha", .35f)
}

/** Dialogs, the save cards, and the chips their buttons sit in. */
class ThemeDialog internal constructor(reader: ThemeStyleReader) {
    val scrimAlpha = reader.fraction("dialog", "scrimAlpha", .72f)
    val conflictScrimAlpha = reader.fraction("dialog", "conflictScrimAlpha", .82f)
    val borderAlpha = reader.fraction("dialog", "borderAlpha", .08f)
    val chipAlpha = reader.fraction("dialog", "chipAlpha", .06f)
    val chipFocusAlpha = reader.fraction("dialog", "chipFocusAlpha", .10f)
    /** A save card's focused action, which has to read from across the bottom screen. */
    val chipStrongAlpha = reader.fraction("dialog", "chipStrongAlpha", .16f)
    val rowAlpha = reader.fraction("dialog", "rowAlpha", .06f)
    val toastAlpha = reader.fraction("dialog", "toastAlpha", .85f)
}

class ThemeSettings internal constructor(reader: ThemeStyleReader) {
    val labelRestAlpha = reader.fraction("settings", "labelRestAlpha", .62f)
    val artRestAlpha = reader.fraction("settings", "artRestAlpha", .70f)
    val artGrow = reader.number("settings", "artGrow", .14f)
    val cardLift = reader.number("settings", "cardLift", .035f)
    val borderAlpha = reader.fraction("settings", "borderAlpha", .06f)
}

class ThemeFind internal constructor(reader: ThemeStyleReader) {
    /** The faint ring on the best result before the D-pad reaches it. */
    val ringRestAlpha = reader.fraction("find", "ringRestAlpha", .38f)
    val filterAlpha = reader.fraction("find", "filterAlpha", .07f)
    val chosenAlpha = reader.fraction("find", "chosenAlpha", .30f)
    val chosenRimDp = reader.number("find", "chosenRimDp", 1.5f)
    val hiddenBackingAlpha = reader.fraction("find", "hiddenBackingAlpha", .78f)
}

/**
 * A collection's folder: its colours, and where the glyph is pressed into `mark.collection-body`,
 * in that art's 64-unit grid. A pack that redraws the folder moves the glyph with it.
 */
class ThemeCollections internal constructor(reader: ThemeStyleReader) {
    private val tints: Map<CollectionTint, Color> = CollectionTint.entries.associateWith { reader.color("collections", it.id, it.color) }
    fun color(tint: CollectionTint): Color = tints.getValue(tint)
    val glyphX = reader.number("collections", "glyphX", 32f) { it in 0f..64f }
    val glyphY = reader.number("collections", "glyphY", 39.48f) { it in 0f..64f }
    val glyphSize = reader.number("collections", "glyphSize", 29.3f) { it > 0f && it <= 64f }
    /** How far toward [ThemeColors.shade] the pressed glyph is. */
    val pressedDepth = reader.fraction("collections", "pressedDepth", .62f)
    val lipDrop = reader.number("collections", "lipDrop", .025f) { true }
    val lipAlpha = reader.fraction("collections", "lipAlpha", .32f)
    /** The swatches in the icon editor: how far their top lightens and their base darkens. */
    val swatchShine = reader.fraction("collections", "swatchShine", .35f)
    val swatchShade = reader.fraction("collections", "swatchShade", .3f)
    val swatchRimAlpha = reader.fraction("collections", "swatchRimAlpha", .85f)
}

class ThemeSound internal constructor(reader: ThemeStyleReader) {
    val volume = reader.fraction("sound", "volume", .4f)
}
