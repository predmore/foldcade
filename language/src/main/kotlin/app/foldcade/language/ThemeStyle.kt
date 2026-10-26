package app.foldcade.language

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*

/**
 * Resolve defaults and legal token domains once; invalid values never reach native controls.
 * Each token resolves from [fields], then from [inherit] (Afterglow's style, for every other pack),
 * then from the engine's own value. An invalid token in [fields] is reported and falls through.
 * A value the pack inherits is multiplied by the pack's `base` scale for that token's [TokenRole],
 * so a token added after the pack was made follows the pack's own proportions.
 */
class ThemeStyle(
    val fields: JsonObject = JsonObject(emptyMap()),
    report: (ThemeDiagnostic) -> Unit = {},
    inherit: JsonObject = JsonObject(emptyMap()),
    /** Old token names and what each became; [ThemeContract.renamedTokens] outside tests. */
    renamed: Map<String, String> = ThemeContract.renamedTokens,
    retired: Set<String> = ThemeContract.retiredTokens,
) {
    private val reader = ThemeStyleReader(fields, inherit, report, renamed)
    val base = ThemeBase(reader)
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
    val picker = ThemePicker(reader)
    val saves = ThemeSaves(reader)
    val play = ThemePlay(reader)
    val collections = ThemeCollections(reader)
    val sound = ThemeSound(reader)

    /** Every token the engine reads, as `group.name`. */
    val tokens: Set<String> get() = reader.read

    /** Tokens no layer declared, which kept the engine's own value. Afterglow declares every one. */
    val defaulted: Set<String> get() = reader.defaulted

    /** Each token's resolved value, by `group.name`. */
    val values: Map<String, Any?> get() = reader.values

    /** What each token accepts, by `group.name`: the public contract a pack is written against. */
    val specs: Map<String, TokenSpec> get() = reader.specs

    /**
     * Every resolved token as a style document, for a pack that builds on this one to inherit.
     * A colour left to the engine is `null`, so it is derived again from the inheriting pack's own.
     */
    fun resolvedJson(): JsonObject {
        val groups = linkedMapOf<String, MutableMap<String, JsonElement>>()
        reader.values.forEach { (token, value) ->
            val element = when (value) {
                null -> JsonNull
                is Color -> JsonPrimitive(themeColorText(value))
                is Number -> JsonPrimitive(value)
                is List<*> -> JsonArray(value.map { JsonPrimitive(it as Number) })
                else -> return@forEach
            }
            groups.getOrPut(token.substringBefore('.')) { linkedMapOf() }[token.substringAfter('.')] = element
        }
        return JsonObject(groups.mapValues { JsonObject(it.value) })
    }

    init {
        // A misspelt token would otherwise be silently ignored, and a renamed one silently lost.
        val groups = reader.read.map { it.substringBefore('.') }.toSet()
        fields.forEach { (group, values) ->
            val names = (values as? JsonObject)?.keys.orEmpty().map { "$group.$it" }
            val known = names.any { it in renamed || it in retired }
            if (group !in groups && !known) report(ThemeDiagnostic("theme.json:style.$group", "Unknown token group"))
            else names.forEach { token ->
                val now = renamed[token]
                when {
                    now != null -> report(ThemeDiagnostic("theme.json:style.$token", "Renamed to $now; this name still works"))
                    token in retired -> report(ThemeDiagnostic("theme.json:style.$token", "Retired; this token no longer does anything"))
                    token !in reader.read -> report(ThemeDiagnostic("theme.json:style.$token", "Unknown token"))
                }
            }
        }
    }
}

/** What a token holds. */
enum class TokenKind(val wire: String) { Number("number"), Duration("duration"), Color("color"), Weight("weight"), Curve("curve") }

/**
 * The `base` scale a token follows when a pack inherits it. [None] tokens are inherited as they are.
 * The scales are proportions, not values, so they apply across px, dp, sp and fractions alike.
 */
enum class TokenRole(val wire: String, val scale: String?) {
    None("none", null),
    Attention("attention", null),
    Corner("corner", "cornerScale"),
    Stroke("stroke", "strokeScale"),
    Glow("glow", "glowScale"),
    Fill("fill", "fillScale"),
    Type("type", "typeScale"),
    Duration("duration", "durationScale"),
}

/** The numbers a token accepts. An open end excludes its bound. [whole] asks for an integer. */
data class TokenDomain(
    val min: Float = Float.NEGATIVE_INFINITY,
    val max: Float = Float.POSITIVE_INFINITY,
    val minOpen: Boolean = false,
    val maxOpen: Boolean = false,
    val whole: Boolean = false,
) {
    fun accepts(value: Float): Boolean = value.isFinite() &&
        (if (minOpen) value > min else value >= min) &&
        (if (maxOpen) value < max else value <= max) &&
        (!whole || value % 1f == 0f)

    companion object {
        val Any = TokenDomain()
        val NonNegative = TokenDomain(min = 0f)
        val Positive = TokenDomain(min = 0f, minOpen = true)
        val Fraction = TokenDomain(0f, 1f)
        val Half = TokenDomain(0f, .5f)
        /** A falloff's start: 1 would leave no room for the falloff. */
        val Rim = TokenDomain(0f, 1f, maxOpen = true)
        /** A share of something that has to stay visible. */
        val Share = TokenDomain(0f, 1f, minOpen = true)
        val Inside = TokenDomain(0f, 1f, minOpen = true, maxOpen = true)
        val Millis = TokenDomain(1f, Int.MAX_VALUE.toFloat())
        val Steps = TokenDomain(0f, Int.MAX_VALUE.toFloat(), whole = true)
        val CssWeight = TokenDomain(1f, 1000f, whole = true)
        val Grid64 = TokenDomain(0f, 64f)
    }
}

/**
 * One token's contract: its kind, accepted [domain], unit, and the `base` scale it follows.
 * A [nullable] colour takes `null`, which leaves it to the engine to derive.
 */
data class TokenSpec(val kind: TokenKind, val domain: TokenDomain?, val unit: String?, val role: TokenRole, val nullable: Boolean = false)

/** A token's unit, read from its name the way the guide writes it. */
internal fun tokenUnit(group: String, name: String, kind: TokenKind): String? = when {
    kind == TokenKind.Duration -> "ms"
    kind == TokenKind.Weight -> "weight"
    kind != TokenKind.Number -> null
    group == "type" -> "sp"
    name.endsWith("Px") -> "px"
    name.endsWith("Dp") -> "dp"
    else -> null
}

internal class ThemeStyleReader(
    private val fields: JsonObject,
    private val inherit: JsonObject,
    private val report: (ThemeDiagnostic) -> Unit,
    private val renamed: Map<String, String> = ThemeContract.renamedTokens,
) {
    val read = linkedSetOf<String>()
    val defaulted = linkedSetOf<String>()
    val values = linkedMapOf<String, Any?>()
    val specs = linkedMapOf<String, TokenSpec>()

    /** A pack's own value for [group].[name], under its current name or any name it had before. */
    private fun own(group: String, name: String): Pair<String, JsonElement>? {
        val token = "$group.$name"
        ownField(fields, token)?.let { return token to it }
        return renamed.filterValues { it == token }.keys.firstNotNullOfOrNull { old -> ownField(fields, old)?.let { old to it } }
    }

    private fun ownField(layer: JsonObject, token: String) =
        (layer[token.substringBefore('.')] as? JsonObject)?.get(token.substringAfter('.'))

    private fun inherited(group: String, name: String): JsonElement? {
        val token = "$group.$name"
        return ownField(inherit, token)
            ?: renamed.filterValues { it == token }.keys.firstNotNullOfOrNull { ownField(inherit, it) }
    }

    private fun invalid(token: String) = report(ThemeDiagnostic("theme.json:style.$token", "Invalid token; using the default"))

    /**
     * The pack's value, else the inherited one, else [fallback]. Only the pack's own mistakes are
     * reported. [scaled] adjusts a value the pack did not set itself.
     */
    private fun <T> layered(
        group: String,
        name: String,
        fallback: T,
        spec: TokenSpec,
        scaled: (T) -> T = { it },
        parse: (JsonElement) -> T?,
    ): T {
        val token = "$group.$name"
        read += token
        specs[token] = spec
        val own = own(group, name)?.let { (written, raw) -> parse(raw) ?: null.also { invalid(written) } }
        val value = own
            ?: (inherited(group, name)?.let(parse) ?: fallback.also { defaulted += token }).let(scaled)
        values[token] = (value as? FloatArray)?.toList() ?: value
        return value
    }

    /** The pack's `base` scales, read before any token that follows one. */
    private val scales: Map<TokenRole, Float> by lazy {
        TokenRole.entries.mapNotNull { role ->
            val scale = role.scale ?: return@mapNotNull null
            // A stroke, a type size or a duration scaled to nothing would leave nothing to draw.
            val domain = if (role in setOf(TokenRole.Stroke, TokenRole.Type, TokenRole.Duration)) TokenDomain.Positive else TokenDomain.NonNegative
            val parse = { raw: JsonElement -> (raw as? JsonPrimitive)?.floatOrNull?.takeIf(domain::accepts) }
            layered("base", scale, 1f, TokenSpec(TokenKind.Number, domain, null, TokenRole.None), parse = parse)
            // Only the pack's own scale applies. One it inherits is already in the values it inherits.
            role to (own("base", scale)?.second?.let(parse) ?: 1f)
        }.toMap()
    }

    fun readScales() { scales }

    /** The pack's own scale for [role]; 1 when it inherits one or sets none. */
    fun ownScale(role: TokenRole): Float = scales[role] ?: 1f

    /** [value] times [role]'s scale, kept inside [domain]. A product that falls outside keeps [value]. */
    private fun scale(value: Float, role: TokenRole, domain: TokenDomain): Float {
        val factor = scales[role] ?: return value
        if (factor == 1f) return value
        val product = (value * factor).coerceIn(domain.min, domain.max)
        return if (domain.accepts(product)) product else value
    }

    fun number(group: String, name: String, fallback: Float, domain: TokenDomain = TokenDomain.NonNegative, role: TokenRole = TokenRole.None): Float =
        layered(group, name, fallback, TokenSpec(TokenKind.Number, domain, tokenUnit(group, name, TokenKind.Number), role), { scale(it, role, domain) }) { raw ->
            (raw as? JsonPrimitive)?.floatOrNull?.takeIf(domain::accepts)
        }

    fun fraction(group: String, name: String, fallback: Float, role: TokenRole = TokenRole.None): Float = number(group, name, fallback, TokenDomain.Fraction, role)

    fun duration(group: String, name: String, fallback: Int): Int {
        val domain = TokenDomain.Millis
        return layered(group, name, fallback.toFloat(), TokenSpec(TokenKind.Duration, domain, "ms", TokenRole.Duration),
            { scale(it, TokenRole.Duration, domain).let { scaled -> if (scaled == it) it else maxOf(1f, kotlin.math.round(scaled)) } }) { raw ->
            (raw as? JsonPrimitive)?.floatOrNull?.takeIf(domain::accepts)
        }.toInt()
    }

    fun color(group: String, name: String, fallback: Color, role: TokenRole = TokenRole.None): Color =
        layered(group, name, fallback, TokenSpec(TokenKind.Color, null, null, role)) { raw ->
            (raw as? JsonPrimitive)?.contentOrNull?.let(::themeColor)
        }

    /**
     * A colour that may be left to the engine to derive. JSON `null` asks for the derived colour
     * explicitly, which is how Afterglow declares it and how a pack undoes an inherited one.
     */
    fun optionalColor(group: String, name: String): Color? {
        val derived = Color.Unspecified
        return layered<Color>(group, name, derived, TokenSpec(TokenKind.Color, null, null, TokenRole.None, nullable = true)) { raw ->
            if (raw is JsonNull) derived else (raw as? JsonPrimitive)?.contentOrNull?.let(::themeColor)
        }.takeIf { it != derived }.also { values["$group.$name"] = it }
    }

    /** CSS-style weights, 1 to 1000. */
    fun weight(group: String, name: String, fallback: FontWeight): FontWeight {
        val domain = TokenDomain.CssWeight
        return FontWeight(layered(group, name, fallback.weight.toFloat(), TokenSpec(TokenKind.Weight, domain, "weight", TokenRole.None)) { raw ->
            (raw as? JsonPrimitive)?.floatOrNull?.takeIf(domain::accepts)
        }.toInt())
    }

    fun curve(name: String, fallback: FloatArray): FloatArray =
        layered("motion", name, fallback, TokenSpec(TokenKind.Curve, null, null, TokenRole.None)) { raw ->
            val values = (raw as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.floatOrNull?.takeIf(Float::isFinite) }
            if ((raw as? JsonArray)?.size == 4 && values != null && values.size == 4 && values[0] in 0f..1f && values[2] in 0f..1f) values.toFloatArray()
            else null
        }
}

/**
 * Proportions a pack sets once for everything it inherits, including tokens added to Foldcade
 * after the pack was made. 1 keeps Afterglow's. A value the pack sets itself is never scaled.
 */
class ThemeBase internal constructor(reader: ThemeStyleReader) {
    init { reader.readScales() }

    /** The pack's own corner scale, which also rounds the tiles (`iconRadius`) it inherits. */
    val cornerScale: Float = reader.ownScale(TokenRole.Corner)
}

class ThemeMetrics internal constructor(reader: ThemeStyleReader) {
    val insetFraction = reader.number("metrics", "insetFraction", Metrics.insetFraction, TokenDomain.Half)
    val gapFraction = reader.number("metrics", "gapFraction", Metrics.gapFraction)
    val focusStrokePx = reader.number("metrics", "focusStrokePx", Metrics.focusStrokePx)
    val heroInsetPx = reader.number("metrics", "heroInsetPx", Metrics.heroInsetPx)
    val dialogInsetPx = reader.number("metrics", "dialogInsetPx", Metrics.dialogInsetPx)
    val dialogBorderPx = reader.number("metrics", "dialogBorderPx", Metrics.dialogBorderPx, role = TokenRole.Stroke)
    val cardCornerDp = reader.number("metrics", "cardCornerDp", Metrics.cardCornerDp, role = TokenRole.Corner)
    val heroArtFraction = reader.number("metrics", "heroArtFraction", Metrics.heroArtFraction, TokenDomain.Share)
    val chromeClearancePx = reader.number("metrics", "chromeClearancePx", Metrics.chromeClearancePx)

    /** Controller glyphs beside rows, hints and chips, as a share of the size each place draws them. */
    val glyphScale = reader.number("metrics", "glyphScale", 1f, TokenDomain.Positive)
}

class ThemeGlow internal constructor(reader: ThemeStyleReader) {
    val focusOverflow = reader.number("glow", "focusOverflow", 64f)
    val restOverflow = reader.number("glow", "restOverflow", 18f)
    val restTightness = reader.number("glow", "restTightness", 2.4f)
    val focusTightness = reader.number("glow", "focusTightness", .85f)
    val restRim = reader.number("glow", "restRim", .42f, TokenDomain.Rim)
    val focusRim = reader.number("glow", "focusRim", .66f, TokenDomain.Rim)
    val restPeak = reader.fraction("glow", "restPeak", .22f, TokenRole.Glow)
    val focusPeak = reader.fraction("glow", "focusPeak", 1f, TokenRole.Glow)
    val heroPeak = reader.fraction("glow", "heroPeak", 0f, TokenRole.Glow)

    /** The light behind a mark on its tile: its centre, from the tile's top, as a share of the tile. */
    val markLightTop = reader.number("glow", "markLightTop", .12f, TokenDomain.Any)
    val markLightReach = reader.number("glow", "markLightReach", .85f, TokenDomain.Positive)
    val markLightPeak = reader.fraction("glow", "markLightPeak", .34f, TokenRole.Glow)
    val markLightFade = reader.number("glow", "markLightFade", .72f, TokenDomain.Inside)
}

class ThemeFocusRing internal constructor(reader: ThemeStyleReader) {
    val start = reader.optionalColor("focusRing", "start")
    val end = reader.optionalColor("focusRing", "end")
    val stroke = reader.number("focusRing", "stroke", 6f, role = TokenRole.Stroke)
    val gap = reader.number("focusRing", "gap", 2f)
    val glowSpread = reader.number("focusRing", "glowSpread", 10f)
    val glowAlpha = reader.fraction("focusRing", "glowAlpha", .10f, TokenRole.Glow)
    val glowSteps = reader.number("focusRing", "glowSteps", 4f, TokenDomain.Steps).toInt()
    val pronouncedStroke = reader.number("focusRing", "pronouncedStroke", 10f, role = TokenRole.Stroke)
    val pronouncedSpread = reader.number("focusRing", "pronouncedSpread", 22f)
    val pronouncedAlpha = reader.fraction("focusRing", "pronouncedAlpha", .22f, TokenRole.Glow)
}

class ThemeTypography internal constructor(private val reader: ThemeStyleReader) {
    private fun size(name: String, fallback: Float) = reader.number("type", name, fallback, TokenDomain.Positive, TokenRole.Type).sp
    val heroTitle = size("heroTitle", TypeRamp.heroTitle.value)
    val conflictGame = size("conflictGame", TypeRamp.conflictGame.value)
    val heroMeta = size("heroMeta", TypeRamp.heroMeta.value)
    val availability = size("availability", TypeRamp.availability.value)
    val gridLabel = size("gridLabel", TypeRamp.gridLabel.value)
    val dialogTitle = size("dialogTitle", TypeRamp.dialogTitle.value)
    val dialogBody = size("dialogBody", TypeRamp.dialogBody.value)
    val menuRow = size("menuRow", TypeRamp.menuRow.value)
    val sideRow = size("sideRow", TypeRamp.sideRow.value)
    val hint = size("hint", TypeRamp.hint.value)
    val clock = size("clock", TypeRamp.clock.value)

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
    val scaleRest = reader.number("motion", "scaleRest", 1f, TokenDomain.Positive)
    val scaleFocus = reader.number("motion", "scaleFocus", 1.05f, TokenDomain.Positive)
    val heroScaleFrom = reader.number("motion", "heroScaleFrom", .96f, TokenDomain.Positive)
    val heroSlideUpPx = reader.number("motion", "heroSlideUpPx", 24f, TokenDomain.Any)
    val heroTravelPx = reader.number("motion", "heroTravelPx", 64f, TokenDomain.Any)
    val sceneDepth = reader.number("motion", "sceneDepth", .35f, TokenDomain.Any)
    val sceneOverscan = reader.number("motion", "sceneOverscan", 1.05f, TokenDomain.Positive)
    val sceneSettleFrom = reader.number("motion", "sceneSettleFrom", 1.1f, TokenDomain.Positive)
    val tiltPx = reader.number("motion", "tiltPx", 18f)
    val tiltDegrees = reader.number("motion", "tiltDegrees", 3f, TokenDomain.Positive)
    val tiltArtShare = reader.fraction("motion", "tiltArtShare", .4f)
    val durationTiltRecenter = ms("durationTiltRecenter", 2400)
    val durationScene = ms("durationScene", 360)
    val durationShowcase = ms("durationShowcase", 1400)
    val durationSelect = ms("durationSelect", 280)
    val durationBob = ms("durationBob", 1800)
    val durationLetterHold = ms("durationLetterHold", 360)
    val durationLetterFade = ms("durationLetterFade", 120)
    val scaleLift = reader.number("motion", "scaleLift", 1.1f, TokenDomain.Positive)
    val scaleOver = reader.number("motion", "scaleOver", .74f, TokenDomain.Positive)
    val scaleOpen = reader.number("motion", "scaleOpen", 1.14f, TokenDomain.Positive)
    val bobPx = reader.number("motion", "bobPx", 7f, TokenDomain.Any)
    val liftGlowPx = reader.number("motion", "liftGlowPx", 30f, TokenDomain.Any)
    val liftGlowPeak = reader.fraction("motion", "liftGlowPeak", .7f, TokenRole.Glow)
    val squash = reader.number("motion", "squash", .08f, TokenDomain.Half)
    val keySquash = reader.number("motion", "keySquash", .07f, TokenDomain.Half)
    val riseFrom = reader.number("motion", "riseFrom", .96f, TokenDomain.Positive)
    val riseDp = reader.number("motion", "riseDp", 24f, TokenDomain.Any)
    val sinkScale = reader.fraction("motion", "sinkScale", .06f)
    val sinkDp = reader.number("motion", "sinkDp", 16f, TokenDomain.Any)
    val cascadeDp = reader.number("motion", "cascadeDp", 14f, TokenDomain.Any)
    val cascadeStep = reader.fraction("motion", "cascadeStep", .05f)
    val cascadeSpan = reader.number("motion", "cascadeSpan", .35f, TokenDomain.Share)
    val popFrom = reader.number("motion", "popFrom", .72f, TokenDomain.Positive)

    /** How far a refused or cancelled row shakes each way. */
    val shakePx = reader.number("motion", "shakePx", 8f)

    /** The board's starting scale as an undo or a tidy puts it back. */
    val rewindFrom = reader.number("motion", "rewindFrom", .94f, TokenDomain.Positive)

    /** A folder's grid's starting scale as it opens. */
    val folderPopFrom = reader.number("motion", "folderPopFrom", .92f, TokenDomain.Positive)
    val arrive = reader.curve("arrive", floatArrayOf(.05f, .7f, .1f, 1f))
    val leave = reader.curve("leave", floatArrayOf(.3f, 0f, .8f, .15f))
    val pulse = reader.curve("pulse", floatArrayOf(.4f, 0f, .6f, 1f))
    val dissolve = reader.curve("dissolve", floatArrayOf(.2f, 0f, 0f, 1f))
    val playful = reader.curve("playful", floatArrayOf(.34f, 1.36f, .64f, 1f))
}

/** Colours beyond the manifest's five. */
class ThemeColors internal constructor(reader: ThemeStyleReader) {
    /** Progress that needs protection, such as a save that is not reaching RomM. */
    val attention = reader.color("colors", "attention", Color(0xFFFFC48A), TokenRole.Attention)

    /** What dims the screen under a menu or dialog and shades art behind text. */
    val scrim = reader.color("colors", "scrim", Color(0xFF000000))

    /** The dark end of art's own shading: a glyph pressed into a folder, a swatch's base. */
    val shade = reader.color("colors", "shade", Color(0xFF000000))

    /** The light end: the lip under a pressed glyph, a swatch's top and its chosen rim. */
    val shine = reader.color("colors", "shine", Color(0xFFFFFFFF))

    /** The gliding highlights of menus, keys and a text selection. Null: the focus ring's colours. */
    val highlight = reader.optionalColor("colors", "highlight")

    /** A switch, a check or a quick tile that is on. Null: the focus ring's colours. */
    val on = reader.optionalColor("colors", "on")

    /** What fills as something progresses: a download, a slider, the play-time bars. Null: `focus`. */
    val progress = reader.optionalColor("colors", "progress")
}

/** Corners. A `round` of 0.5 makes a pill or a circle; 0 makes the same control square. */
class ThemeShape internal constructor(reader: ThemeStyleReader) {
    /** Small controls, as a share of their shorter side: switches, sliders, chips, pills, dots, the caret. */
    val round = reader.number("shape", "round", .5f, TokenDomain.Half, TokenRole.Corner)

    /** The highlight that glides between menu and Settings rows, and the save cards. */
    val highlightCornerDp = reader.number("shape", "highlightCornerDp", 22f, role = TokenRole.Corner)

    /** A row's highlight in the command center's lists. */
    val rowCornerPx = reader.number("shape", "rowCornerPx", 22f, role = TokenRole.Corner)

    /** The largest corner of a text chip. */
    val chipCornerPx = reader.number("shape", "chipCornerPx", 44f, role = TokenRole.Corner)

    /** A keyboard key, as a share of the key's width. */
    val keyCorner = reader.number("shape", "keyCorner", .2f, TokenDomain.Half, TokenRole.Corner)

    /** An achievement badge, a toast, and an art candidate. */
    val badgeCornerPx = reader.number("shape", "badgeCornerPx", 8f, role = TokenRole.Corner)
    val toastCornerDp = reader.number("shape", "toastCornerDp", 8f, role = TokenRole.Corner)
    val thumbCornerDp = reader.number("shape", "thumbCornerDp", 12f, role = TokenRole.Corner)
}

/** A home grid tile beyond its corner (`iconRadius`) and glow. */
class ThemeTile internal constructor(reader: ThemeStyleReader) {
    /** How bright art rests when it is not focused. */
    val restAlpha = reader.fraction("tile", "restAlpha", .82f)
    val socketAlpha = reader.fraction("tile", "socketAlpha", .16f, TokenRole.Fill)
    /** The thin outline of a held tile's empty socket, and of a tile while Edit home is open. */
    val outlinePx = reader.number("tile", "outlinePx", 3f, role = TokenRole.Stroke)
    /** The heavy outline of the tile Edit home is on, and of a carried gap. */
    val editStrokePx = reader.number("tile", "editStrokePx", 9f, role = TokenRole.Stroke)
    val editRestAlpha = reader.fraction("tile", "editRestAlpha", .45f)
    val dotPx = reader.number("tile", "dotPx", 10f)
    val statusChipPx = reader.number("tile", "statusChipPx", 36f, TokenDomain.Positive)
    val statusIconPx = reader.number("tile", "statusIconPx", 24f, TokenDomain.Positive)
    val statusBackingAlpha = reader.fraction("tile", "statusBackingAlpha", .72f, TokenRole.Fill)
    val downloadBarPx = reader.number("tile", "downloadBarPx", 6f, role = TokenRole.Stroke)
    val downloadTrackAlpha = reader.fraction("tile", "downloadTrackAlpha", .22f, TokenRole.Fill)

    /** The bar under a wide grid that shows where the screen is, and its track's strength over `muted`. */
    val stripPx = reader.number("tile", "stripPx", 6f, TokenDomain.Positive, TokenRole.Stroke)
    val stripAlpha = reader.fraction("tile", "stripAlpha", .45f, TokenRole.Fill)

    /** The Home pill above a group's grid while a game is held: its outline and that outline's strength. */
    val homeTargetStrokePx = reader.number("tile", "homeTargetStrokePx", 3f, role = TokenRole.Stroke)
    val homeTargetAlpha = reader.fraction("tile", "homeTargetAlpha", .35f)

    /** The faintest a carried tile's ring gets before it is raised. */
    val carriedRingAlpha = reader.fraction("tile", "carriedRingAlpha", .4f)
}

/** The top screen's selected item. */
class ThemeHero internal constructor(reader: ThemeStyleReader) {
    /** A mark drawn in the hero, as a share of its box. */
    val markScale = reader.number("hero", "markScale", .86f, TokenDomain.Share)

    /** The name card over game art. */
    val cardAlpha = reader.fraction("hero", "cardAlpha", .72f)
    val pillAlpha = reader.fraction("hero", "pillAlpha", .94f)
    /** The round shade behind a game's logo. Light, now the logo's own shadow keeps a dark one apart from its art. */
    val logoShadeAlpha = reader.fraction("hero", "logoShadeAlpha", .42f)

    /** The soft shadow a logo casts in its own shape, in the scrim's colour: its strength and its blur. */
    val logoShadowAlpha = reader.fraction("hero", "logoShadowAlpha", .85f)
    val logoShadowBlurDp = reader.number("hero", "logoShadowBlurDp", 10f)

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
    val highlightStartAlpha = reader.fraction("menu", "highlightStartAlpha", .20f, TokenRole.Fill)
    val highlightEndAlpha = reader.fraction("menu", "highlightEndAlpha", .08f, TokenRole.Fill)
    val labelRestAlpha = reader.fraction("menu", "labelRestAlpha", .72f)
    val artRestAlpha = reader.fraction("menu", "artRestAlpha", .78f)
    val artGrow = reader.number("menu", "artGrow", .12f)
    /** The dim behind an open menu, over a blur, and without one on Android before 12. */
    val scrimAlpha = reader.fraction("menu", "scrimAlpha", .32f)
    val scrimAlphaUnblurred = reader.fraction("menu", "scrimAlphaUnblurred", .55f)
    val blurPx = reader.number("menu", "blurPx", 28f)
    val islandLift = reader.number("menu", "islandLift", .04f, role = TokenRole.Fill)
    val islandBorderDp = reader.number("menu", "islandBorderDp", 1f, role = TokenRole.Stroke)
    val islandFocusBorderDp = reader.number("menu", "islandFocusBorderDp", 2f, role = TokenRole.Stroke)
    val islandRimAlpha = reader.fraction("menu", "islandRimAlpha", .08f, TokenRole.Fill)
    val islandRimLitAlpha = reader.fraction("menu", "islandRimLitAlpha", .34f, TokenRole.Fill)
    val quickTileAlpha = reader.fraction("menu", "quickTileAlpha", .08f, TokenRole.Fill)
    val quickTileLitAlpha = reader.fraction("menu", "quickTileLitAlpha", .16f, TokenRole.Fill)
    val quickTileOnAlpha = reader.fraction("menu", "quickTileOnAlpha", .6f, TokenRole.Fill)
    val quickArtGrow = reader.number("menu", "quickArtGrow", .14f)

    /** A quick tile's word while it is not focused; focused, it is full strength. */
    val quickLabelRestAlpha = reader.fraction("menu", "quickLabelRestAlpha", .6f)

    /** A notice's title in R1 while it is not focused. */
    val noticeRestAlpha = reader.fraction("menu", "noticeRestAlpha", .86f)

    /** The fill that grows across a row while a delete is held. */
    val holdAlpha = reader.fraction("menu", "holdAlpha", .3f, TokenRole.Fill)
}

/** Switches, checkmarks and sliders. */
class ThemeControls internal constructor(reader: ThemeStyleReader) {
    val switchWidthDp = reader.number("controls", "switchWidthDp", 54f, TokenDomain.Positive)
    val switchHeightDp = reader.number("controls", "switchHeightDp", 30f, TokenDomain.Positive)
    val switchKnobInsetDp = reader.number("controls", "switchKnobInsetDp", 4f)
    val trackAlpha = reader.fraction("controls", "trackAlpha", .12f, TokenRole.Fill)
    val trackFocusAlpha = reader.fraction("controls", "trackFocusAlpha", .20f, TokenRole.Fill)
    val checkSizeDp = reader.number("controls", "checkSizeDp", 30f, TokenDomain.Positive)
    val checkStrokeDp = reader.number("controls", "checkStrokeDp", 2f, role = TokenRole.Stroke)
    val checkRingAlpha = reader.fraction("controls", "checkRingAlpha", .26f, TokenRole.Fill)
    val checkRingFocusAlpha = reader.fraction("controls", "checkRingFocusAlpha", .42f, TokenRole.Fill)
    val sliderTrackDp = reader.number("controls", "sliderTrackDp", 6f, TokenDomain.Positive)
    val sliderTrackAlpha = reader.fraction("controls", "sliderTrackAlpha", .28f, TokenRole.Fill)
    val sliderKnobDp = reader.number("controls", "sliderKnobDp", 7f, TokenDomain.Positive)
    val sliderGlow = reader.number("controls", "sliderGlow", 2.2f, TokenDomain(min = 1f))

    /** The knob's glow at its centre and at [sliderGlowMid], over the slider's colour. */
    val sliderGlowAlpha = reader.fraction("controls", "sliderGlowAlpha", .9f, TokenRole.Glow)
    val sliderGlowMidAlpha = reader.fraction("controls", "sliderGlowMidAlpha", .34f, TokenRole.Glow)
    val sliderGlowMid = reader.number("controls", "sliderGlowMid", .42f, TokenDomain.Inside)
}

/** The on-screen keyboard and its name chips. */
class ThemeKeyboard internal constructor(private val reader: ThemeStyleReader) {
    private fun alpha(name: String, fallback: Float, role: TokenRole = TokenRole.None) = reader.fraction("keyboard", name, fallback, role)
    val highlightStartAlpha = alpha("highlightStartAlpha", .26f, TokenRole.Fill)
    val highlightEndAlpha = alpha("highlightEndAlpha", .10f, TokenRole.Fill)
    /** How strong the highlight stays while the D-pad is elsewhere. */
    val inactiveHighlight = alpha("inactiveHighlight", .3f)
    val keyAlpha = alpha("keyAlpha", .92f)
    val glassAlpha = alpha("glassAlpha", .12f, TokenRole.Fill)
    val modifierGlassAlpha = alpha("modifierGlassAlpha", .05f, TokenRole.Fill)
    val litGlassAlpha = alpha("litGlassAlpha", .06f, TokenRole.Fill)
    val doneAlpha = alpha("doneAlpha", .55f)
    val shiftAlpha = alpha("shiftAlpha", .38f)
    val pressAlpha = alpha("pressAlpha", .22f, TokenRole.Fill)
    val sheenAlpha = alpha("sheenAlpha", .10f, TokenRole.Fill)
    val rimTopAlpha = alpha("rimTopAlpha", .22f, TokenRole.Fill)
    val rimBottomAlpha = alpha("rimBottomAlpha", .04f, TokenRole.Fill)
    val rimDp = reader.number("keyboard", "rimDp", 1f, role = TokenRole.Stroke)
    val glyphRestAlpha = alpha("glyphRestAlpha", .62f)
    val chipAlpha = alpha("chipAlpha", .08f, TokenRole.Fill)
    val chipFlashAlpha = alpha("chipFlashAlpha", .20f, TokenRole.Fill)
    val chipRimAlpha = alpha("chipRimAlpha", .12f, TokenRole.Fill)
    val chipLabelRestAlpha = alpha("chipLabelRestAlpha", .82f)

    /** The halo of the lamp under Shift while it is on. */
    val lampHaloAlpha = alpha("lampHaloAlpha", .3f, TokenRole.Glow)
}

/** The text field Find, rename and connect type into. */
class ThemeField internal constructor(reader: ThemeStyleReader) {
    val ringAlpha = reader.fraction("field", "ringAlpha", .55f)
    val selectionStartAlpha = reader.fraction("field", "selectionStartAlpha", .55f, TokenRole.Fill)
    val selectionEndAlpha = reader.fraction("field", "selectionEndAlpha", .40f, TokenRole.Fill)
    /** A selection's faintest as it breathes, as a share of its strength. */
    val selectionLowAlpha = reader.fraction("field", "selectionLowAlpha", .7f)
    val selectionCornerDp = reader.number("field", "selectionCornerDp", 10f, role = TokenRole.Corner)
    val caretDp = reader.number("field", "caretDp", 4f, TokenDomain.Positive, TokenRole.Stroke)
    val caretGlowDp = reader.number("field", "caretGlowDp", 6f)
    val caretGlowAlpha = reader.fraction("field", "caretGlowAlpha", .22f, TokenRole.Glow)
    /** The caret's faintest as it breathes. */
    val caretLowAlpha = reader.fraction("field", "caretLowAlpha", .35f)
}

/** Dialogs, the save cards, and the chips their buttons sit in. */
class ThemeDialog internal constructor(reader: ThemeStyleReader) {
    val scrimAlpha = reader.fraction("dialog", "scrimAlpha", .72f)
    val conflictScrimAlpha = reader.fraction("dialog", "conflictScrimAlpha", .82f)
    val borderAlpha = reader.fraction("dialog", "borderAlpha", .08f, TokenRole.Fill)
    val chipAlpha = reader.fraction("dialog", "chipAlpha", .06f, TokenRole.Fill)
    val chipFocusAlpha = reader.fraction("dialog", "chipFocusAlpha", .10f, TokenRole.Fill)
    /** A save card's focused action, which has to read from across the bottom screen. */
    val chipStrongAlpha = reader.fraction("dialog", "chipStrongAlpha", .16f, TokenRole.Fill)
    val rowAlpha = reader.fraction("dialog", "rowAlpha", .06f, TokenRole.Fill)
    val toastAlpha = reader.fraction("dialog", "toastAlpha", .85f)
}

class ThemeSettings internal constructor(reader: ThemeStyleReader) {
    val labelRestAlpha = reader.fraction("settings", "labelRestAlpha", .62f)
    val artRestAlpha = reader.fraction("settings", "artRestAlpha", .70f)
    val artGrow = reader.number("settings", "artGrow", .14f)
    val cardLift = reader.number("settings", "cardLift", .035f, role = TokenRole.Fill)
    val borderAlpha = reader.fraction("settings", "borderAlpha", .06f, TokenRole.Fill)
    val borderDp = reader.number("settings", "borderDp", 1f, role = TokenRole.Stroke)
    /** A source tile that is off, as a Moonlight computer Foldcade is not paired with: its art and name. */
    val tileOffAlpha = reader.fraction("settings", "tileOffAlpha", .45f)
}

class ThemeFind internal constructor(reader: ThemeStyleReader) {
    /** The faint ring on the best result before the D-pad reaches it. */
    val ringRestAlpha = reader.fraction("find", "ringRestAlpha", .38f)
    val filterAlpha = reader.fraction("find", "filterAlpha", .07f, TokenRole.Fill)
    val chosenAlpha = reader.fraction("find", "chosenAlpha", .30f, TokenRole.Fill)
    val chosenRimDp = reader.number("find", "chosenRimDp", 1.5f, role = TokenRole.Stroke)
    val hiddenBackingAlpha = reader.fraction("find", "hiddenBackingAlpha", .78f)
}

/** The art picker. */
class ThemePicker internal constructor(reader: ThemeStyleReader) {
    /** The shimmer across a candidate still loading, in the focus colour over `surface`. */
    val shimmerAlpha = reader.fraction("picker", "shimmerAlpha", .18f, TokenRole.Fill)
}

/** Play activity: the week, the days and their sessions. */
class ThemePlay internal constructor(reader: ThemeStyleReader) {
    /** A game's backing while its art loads, or when it has none. */
    val backingAlpha = reader.fraction("play", "backingAlpha", .06f, TokenRole.Fill)
    /** A day's track, and the hour lines across it. */
    val trackAlpha = reader.fraction("play", "trackAlpha", .06f, TokenRole.Fill)
    val hourAlpha = reader.fraction("play", "hourAlpha", .10f, TokenRole.Fill)
    val hourDp = reader.number("play", "hourDp", 1f, role = TokenRole.Stroke)
    /** A day of the week with no play. */
    val emptyBarAlpha = reader.fraction("play", "emptyBarAlpha", .12f, TokenRole.Fill)
    val barCornerDp = reader.number("play", "barCornerDp", 3f, role = TokenRole.Corner)
}

/**
 * A collection's folder: its colours, and where the glyph is pressed into `mark.collection-body`,
 * in that art's 64-unit grid. A pack that redraws the folder moves the glyph with it.
 */
class ThemeCollections internal constructor(reader: ThemeStyleReader) {
    private val tints: Map<CollectionTint, Color> = CollectionTint.entries.associateWith { reader.color("collections", it.id, it.color) }
    fun color(tint: CollectionTint): Color = tints.getValue(tint)
    val glyphX = reader.number("collections", "glyphX", 32f, TokenDomain.Grid64)
    val glyphY = reader.number("collections", "glyphY", 39.48f, TokenDomain.Grid64)
    val glyphSize = reader.number("collections", "glyphSize", 29.3f, TokenDomain(0f, 64f, minOpen = true))
    /** How far toward [ThemeColors.shade] the pressed glyph is. */
    val pressedDepth = reader.fraction("collections", "pressedDepth", .62f)
    val lipDrop = reader.number("collections", "lipDrop", .025f, TokenDomain.Any)
    val lipAlpha = reader.fraction("collections", "lipAlpha", .32f)
    /** The swatches in the icon editor: how far their top lightens and their base darkens. */
    val swatchShine = reader.fraction("collections", "swatchShine", .35f)
    val swatchShade = reader.fraction("collections", "swatchShade", .3f)
    val swatchRimAlpha = reader.fraction("collections", "swatchRimAlpha", .85f)
    /** The chosen swatch's rim, and how far inside the swatch's edge it sits. */
    val swatchRimPx = reader.number("collections", "swatchRimPx", 3f, role = TokenRole.Stroke)
    val swatchRimInsetPx = reader.number("collections", "swatchRimInsetPx", 2.5f)
}

class ThemeSound internal constructor(reader: ThemeStyleReader) {
    val volume = reader.fraction("sound", "volume", .4f)
}

/** The two-screen save chooser. Its list and header share one row height. */
class ThemeSaves internal constructor(reader: ThemeStyleReader) {
    val rowHeightDp = reader.number("saves", "rowHeightDp", 52f, TokenDomain.Positive)
    val edgeDp = reader.number("saves", "edgeDp", 24f)
    val gapDp = reader.number("saves", "gapDp", 8f)
    val screenshotShare = reader.number("saves", "screenshotShare", .5f, TokenDomain.Share)
    /** The scrim over the game's art behind the top screen, so the focused save reads over it. */
    val backdropDim = reader.fraction("saves", "backdropDim", .5f)
    /** A Backup row at rest, quieter than the saves it keeps safe. */
    val backupAlpha = reader.fraction("saves", "backupAlpha", .6f)
}
