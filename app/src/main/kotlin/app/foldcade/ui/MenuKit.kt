package app.foldcade.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.foldcade.R
import app.foldcade.language.HOME_ANDROID_APPS
import app.foldcade.language.PromptKey
import app.foldcade.language.Row
import app.foldcade.language.RowControl
import app.foldcade.language.Theme
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The menus' shared pieces: one highlight that glides from row to row, the chord
 * each row shows on its right, a switch, a choice that rolls to its next value,
 * rows that cascade in, and content that slides aside for the next.
 */

/** Durations come from [app.foldcade.language.MotionStyle], already scaled, so the toolkit must not scale them again. */
internal object SteadyMenu : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

/**
 * [target] reached with [spec], as state. Delegate to it with `by` and read it in a
 * layer or draw block: each frame then redraws without recomposing the caller.
 */
@Composable
internal fun motionState(target: Float, spec: FiniteAnimationSpec<Float>, snap: Boolean = false): State<Float> {
    val anim = remember { Animatable(target) }
    LaunchedEffect(target, snap) {
        if (snap) anim.snapTo(target)
        else withContext(SteadyMenu) { anim.animateTo(target, spec) }
    }
    return anim.asState()
}

/** [target] eased with [MotionStyle.arrive], as state. */
@Composable
internal fun eased(target: Float, base: Int, scale: Float): State<Float> =
    motionState(target, LocalFoldTheme.current.theme.motion.arrive(base, scale))

/** [target] reached with the playful overshoot, as state. */
@Composable
internal fun bouncy(target: Float, scale: Float): State<Float> {
    val motion = LocalFoldTheme.current.theme.motion
    return motionState(target, motion.playful(motion.durationSelect, scale))
}

/**
 * Where each row of one column sits, so a single highlight can travel between them.
 * Each row calls [selectionSlot]. The rows must be the column's own children, and
 * [selectionHighlight] the last modifier on the column, so they share coordinates.
 */
internal class SelectionSlots {
    internal val rows = mutableStateMapOf<Int, Rect>()

    /** Slots that are round tiles. The highlight rounds its corners to fit them. */
    internal val round = mutableStateMapOf<Int, Boolean>()
}

@Composable
internal fun rememberSelectionSlots(): SelectionSlots = remember { SelectionSlots() }

internal fun Modifier.selectionSlot(slots: SelectionSlots, index: Int): Modifier =
    onGloballyPositioned {
        val rect = Rect(it.positionInParent(), it.size.toSize())
        if (slots.rows[index] != rect) slots.rows[index] = rect
    }

/**
 * A slot [depth] layouts below the highlighted column rather than its own child, as a tile
 * sits in a line of tiles, so one highlight can travel between tiles and the rows under them.
 */
internal fun Modifier.selectionSlot(slots: SelectionSlots, index: Int, depth: Int, round: Boolean = false): Modifier =
    onGloballyPositioned { coordinates ->
        if ((slots.round[index] == true) != round) slots.round[index] = round
        var column = coordinates.parentLayoutCoordinates
        repeat(depth - 1) { column = column?.parentLayoutCoordinates }
        val rect = column?.localBoundingBoxOf(coordinates, clipBounds = false) ?: return@onGloballyPositioned
        if (slots.rows[index] != rect) slots.rows[index] = rect
    }

/**
 * One highlight behind the column's focused row. It travels to the next row and lands
 * with a little bounce, the same size at rest wherever it is. A move made while it is
 * still travelling carries the speed on, so a held D-pad glides. [ring] fades the focus
 * ring in for the column the D-pad is in; without it the highlight is a soft fill that
 * only marks the choice. A null [index] fades it away.
 */
@Composable
internal fun Modifier.selectionHighlight(
    slots: SelectionSlots,
    index: Int?,
    ring: Boolean,
    scale: Float,
): Modifier {
    val theme = LocalFoldTheme.current.theme
    val motion = theme.motion
    val top = remember { Animatable(0f) }
    val left = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    val height = remember { Animatable(0f) }
    // 0 wears the highlight's own corner, 1 rounds it to fit a round tile.
    val roundness = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val focus = rememberUpdatedState(index)
    // The effect outlives a change of animator scale or theme, so it reads both fresh.
    val latestScale = rememberUpdatedState(scale)
    val latestMotion = rememberUpdatedState(motion)
    // Read where the rows are only here and in the draw, so rows settling do not recompose the menu.
    val present by remember(slots) { derivedStateOf { focus.value?.let(slots.rows::get) != null } }
    LaunchedEffect(slots) {
        withContext(SteadyMenu) {
            snapshotFlow { focus.value?.let(slots.rows::get) }.collect { next ->
                if (next == null) return@collect
                val round = if (focus.value?.let(slots.round::get) == true) 1f else 0f
                if (!placed) {
                    roundness.snapTo(round)
                    top.snapTo(next.top)
                    left.snapTo(next.left)
                    width.snapTo(next.width)
                    height.snapTo(next.height)
                    placed = true
                    return@collect
                }
                val now = latestMotion.value
                val by = latestScale.value
                val spec = if (top.isRunning) {
                    now.glide(now.durationSelect, by, next.top - top.value, top.velocity)
                } else {
                    now.playful(now.durationSelect, by)
                }
                val size = now.arrive(now.durationSelect, by)
                launch { top.animateTo(next.top, spec) }
                launch { left.animateTo(next.left, size) }
                launch { width.animateTo(next.width, size) }
                launch { height.animateTo(next.height, size) }
                launch { roundness.animateTo(round, size) }
            }
        }
    }
    val shown by eased(if (present) 1f else 0f, motion.durationFocus, scale)
    val ringShown by eased(if (ring) 1f else 0f, motion.durationFocus, scale)
    val focusRing = remember(theme) { FocusRing(theme) }
    val design = theme.style
    return drawBehind {
        if (!placed || shown <= 0f) return@drawBehind
        val rect = Rect(Offset(left.value, top.value), Size(width.value, height.value))
        val own = design.shape.highlightCornerDp.dp.toPx()
        val cornerPx = own + (rect.minDimension * design.shape.round - own) * roundness.value
        // A lit glass fill, brighter where the ring's green starts.
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(focusRing.highlightStart.copy(alpha = design.menu.highlightStartAlpha * shown), focusRing.highlightEnd.copy(alpha = design.menu.highlightEndAlpha * shown)),
                startX = rect.left,
                endX = rect.right,
            ),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(cornerPx, cornerPx),
        )
        if (ringShown > 0f) with(focusRing) { drawFocusRing(cornerPx, alpha = shown * ringShown, bounds = rect) }
    }
}

/** How far the focus ring and its glow reach outside a row. Scrolling leaves this much room. */
@Composable
internal fun ringReach(): Dp {
    val ring = LocalFoldTheme.current.theme.style.focusRing
    return with(LocalDensity.current) { (ring.gap + ring.stroke + ring.glowSpread).toDp() } + 4.dp
}

internal data class IslandScroll(val available: Dp, val state: ScrollState, val viewport: Int)
internal val LocalIslandScroll = staticCompositionLocalOf<IslandScroll?> { null }

/** Fade only edges with rows beyond them; the ring remains inside the scroll padding. */
internal fun Modifier.menuFade(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val edge = minOf(12.dp.toPx(), size.height / 2f)
        if (size.height <= 0f) return@drawWithContent
        val clear = androidx.compose.ui.graphics.Color.Transparent
        val solid = androidx.compose.ui.graphics.Color.Black
        drawRect(
            Brush.verticalGradient(
                0f to if (scroll.value > 0) clear else solid,
                edge / size.height to solid,
                1f - edge / size.height to solid,
                1f to if (scroll.value < scroll.maxValue) clear else solid,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** A scrolling menu column with room for the focus ring's glow inside its clip, plus [extra]. */
@Composable
internal fun Modifier.ringScroll(extra: Dp = 0.dp, state: ScrollState = rememberScrollState()): Modifier =
    verticalScroll(state).padding(ringReach() + extra)

/**
 * The D-pad moves the focus, not the scroll, so the focused row asks its scrolling
 * parent to show it with the ring's reach around it. The glow then never meets the edge.
 */
@Composable
internal fun Modifier.keepInView(focused: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val pad = with(LocalDensity.current) { ringReach().toPx() }
    LaunchedEffect(focused, size) {
        if (focused && size != IntSize.Zero) {
            requester.bringIntoView(Rect(-pad, -pad, size.width + pad, size.height + pad))
        }
    }
    return onSizeChanged { size = it }.bringIntoViewRequester(requester)
}

/**
 * Keeps [value] while it is here and while it fades away after it goes, with how far in
 * it is. A page or a card then leaves as it was rather than vanishing.
 */
internal class Presence<T>(val shown: T?, val progress: State<Float>)

@Composable
internal fun <T : Any> presence(value: T?, base: Int, scale: Float): Presence<T> {
    // A plain holder: it is only read in the same pass that writes it.
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    val progress = eased(if (value != null) 1f else 0f, base, scale)
    val fading by remember { derivedStateOf { progress.value > 0f } }
    @Suppress("UNCHECKED_CAST")
    val shown = value ?: (last[0] as T?)?.takeIf { fading }
    if (shown == null) last[0] = null
    return Presence(shown, progress)
}

/** A card or page arriving: it fades up from just under full size and a little lower. */
@Composable
internal fun Modifier.rise(progress: () -> Float): Modifier {
    val motion = LocalFoldTheme.current.theme.style.motion
    return graphicsLayer {
        val t = progress()
        alpha = t
        val grow = motion.riseFrom + (1f - motion.riseFrom) * t
        scaleX = grow
        scaleY = grow
        translationY = (1f - t) * motion.riseDp.dp.toPx()
    }
}

/** Content giving way as [progress] goes to 1: it fades and settles back, as the grid does under the keyboard. */
@Composable
internal fun Modifier.sinking(progress: () -> Float): Modifier {
    val motion = LocalFoldTheme.current.theme.style.motion
    return graphicsLayer {
        val t = progress()
        alpha = 1f - t
        val shrink = 1f - motion.sinkScale * t
        scaleX = shrink
        scaleY = shrink
        translationY = -t * motion.sinkDp.dp.toPx()
    }
}

/** A filled card's surface, lit a little at the top by [lift]. */
internal fun cardFill(theme: Theme, lift: Float): Brush =
    Brush.verticalGradient(listOf(lerp(theme.surface, theme.onBackground, lift), theme.surface))

/** Art that lifts with [lift] (springy, 0 to 1) and brightens from [rest] with [lit]. */
internal fun Modifier.lifted(lit: State<Float>, lift: State<Float>, grow: Float, rest: Float): Modifier = graphicsLayer {
    val by = 1f + grow * lift.value
    scaleX = by
    scaleY = by
    alpha = rest + (1f - rest) * lit.value
}

/**
 * Rows arrive one after another as a menu opens, each rising a little into place,
 * and leave in the same order as it closes. [progress] is the menu's own open fraction.
 */
@Composable
internal fun Modifier.cascade(index: Int, progress: () -> Float): Modifier {
    val motion = LocalFoldTheme.current.theme.style.motion
    return graphicsLayer {
        val span = motion.cascadeSpan.coerceAtMost(0.99f)
        val lag = (index * motion.cascadeStep).coerceAtMost(span)
        val local = ((progress() - lag) / (1f - span)).coerceIn(0f, 1f)
        alpha = local
        translationY = (1f - local) * motion.cascadeDp.dp.toPx()
    }
}

/** The art-kit name of a key's glyph. Select and Start wear the Thor's round caps: a square and a ▷ in a ring. */
internal fun promptGlyph(key: PromptKey): String = when (key) {
    PromptKey.FaceA -> "ic_btn_a"
    PromptKey.FaceB -> "ic_btn_b"
    PromptKey.FaceX -> "ic_btn_x"
    PromptKey.FaceY -> "ic_btn_y"
    PromptKey.Select -> "ic_btn_square"
    PromptKey.Start -> "ic_btn_triangle"
    PromptKey.Home -> "ic_btn_home"
    PromptKey.Back -> "ic_btn_back"
    PromptKey.L1 -> "ic_btn_l1"
    PromptKey.R1 -> "ic_btn_r1"
    PromptKey.L2 -> "ic_btn_l2"
    PromptKey.R2 -> "ic_btn_r2"
    PromptKey.DpadSides -> "ic_btn_dpad"
}

@Composable
internal fun PromptImage(name: String, size: Dp, modifier: Modifier = Modifier) {
    val icon = themeIconOrNull(iconSlot(name), 128) ?: return
    val theme = LocalFoldTheme.current.theme
    Image(
        painter = icon.painter,
        contentDescription = null,
        colorFilter = icon.requestedTint(theme.onBackground),
        // Each place sizes its glyph to its row; the theme scales them all alike.
        modifier = modifier.size(size * theme.style.metrics.glyphScale),
    )
}

/** The key that runs a row, on its right. It lights while it is held. */
@Composable
internal fun ChordHint(key: PromptKey?, held: Set<PromptKey>, modifier: Modifier = Modifier) {
    if (key == null) return
    val glyph = promptGlyph(key)
    PromptImage(if (key in held) "${glyph}_filled" else glyph, 26.dp, modifier)
}

/** An on/off switch. The knob springs across with a little overshoot, and the track lights. */
@Composable
internal fun ToggleSwitch(on: Boolean, focused: Boolean, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val knob by bouncy(if (on) 1f else 0f, scale)
    val lit by eased(if (on) 1f else 0f, theme.motion.durationSelect, scale)
    val ring = remember(theme) { FocusRing(theme) }
    val start = ring.onStart
    val end = ring.onEnd
    val controls = theme.style.controls
    val round = theme.style.shape.round
    Box(
        Modifier
            .size(controls.switchWidthDp.dp, controls.switchHeightDp.dp)
            .drawBehind {
                val radius = size.height * round
                drawRoundRect(
                    color = theme.onBackground.copy(alpha = if (focused) controls.trackFocusAlpha else controls.trackAlpha),
                    cornerRadius = CornerRadius(radius, radius),
                )
                if (lit > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(start, end)),
                        alpha = lit.coerceIn(0f, 1f),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
                val inset = controls.switchKnobInsetDp.dp.toPx()
                // The knob is as tall as the track inside its inset, and as round as the theme's controls.
                val knobSize = (size.height - 2f * inset).coerceAtLeast(0f)
                val x = inset + (size.width - 2f * inset - knobSize) * knob
                // A light track, as a white focus color makes it, takes a dark knob.
                val knobColor = if (start.luminance() > 0.6f) lerp(theme.onBackground, theme.background, lit.coerceIn(0f, 1f)) else theme.onBackground
                val knobCorner = knobSize * round
                drawRoundRect(
                    color = knobColor,
                    topLeft = Offset(x, inset),
                    size = Size(knobSize, knobSize),
                    cornerRadius = CornerRadius(knobCorner, knobCorner),
                )
            },
    )
}

/**
 * A check in a ring, for a choice that is in or out. Checked, the ring fills with the focus
 * colors and the tick (`control.check`, or `control.dash` for some) wipes in from the left
 * with a small pop. Cleared, it wipes back out.
 */
@Composable
internal fun CheckMark(on: Boolean, focused: Boolean, scale: Float, some: Boolean = false) {
    val theme = LocalFoldTheme.current.theme
    val tick by eased(if (on || some) 1f else 0f, theme.motion.durationSelect, scale)
    val pop by bouncy(if (on || some) 1f else 0f, scale)
    val ring = remember(theme) { FocusRing(theme) }
    val start = ring.onStart
    val end = ring.onEnd
    val controls = theme.style.controls
    val glyph = themeIconOrNull(if (some) "control.dash" else "control.check", 128)
    // A light fill, as a white focus color makes it, takes a dark tick.
    val tickColor = if (start.luminance() > 0.6f) theme.background else theme.onBackground
    val filter = remember(glyph, tickColor) { glyph?.tint(tickColor) }
    Box(
        Modifier
            .size(controls.checkSizeDp.dp)
            .graphicsLayer {
                val grow = 0.86f + 0.14f * pop
                scaleX = grow
                scaleY = grow
            }
            .drawBehind {
                val radius = size.minDimension / 2f
                val edge = controls.checkStrokeDp.dp.toPx()
                drawCircle(
                    color = theme.onBackground.copy(alpha = if (focused) controls.checkRingFocusAlpha else controls.checkRingAlpha),
                    radius = radius - edge / 2f,
                    style = Stroke(edge),
                )
                val lit = tick.coerceIn(0f, 1f)
                if (lit <= 0f || glyph == null) return@drawBehind
                drawCircle(brush = Brush.linearGradient(listOf(start, end)), radius = radius, alpha = lit)
                clipRect(right = size.width * lit) { with(glyph.painter) { draw(size, colorFilter = filter) } }
            },
    )
}

/**
 * ‹ value ›. The chevrons show while the row has focus, since left and right then change it.
 * A new value rolls up into place as the old one rolls away. At rest the value ends on the
 * row's edge, in one column with plain values, switches, and the way-in chevrons; the ›
 * makes its room as the focus arrives, nudging the value aside.
 */
@Composable
internal fun ChoiceValue(value: String, focused: Boolean, style: TextStyle, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val arrows by eased(if (focused) 1f else 0f, theme.motion.durationFocus, scale)
    val arrow = style.copy(color = theme.onBackground)
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            "‹",
            style = arrow,
            modifier = Modifier.padding(end = CHOICE_GAP).graphicsLayer { alpha = arrows; translationX = (1f - arrows) * 8.dp.toPx() },
        )
        SlideSwap(target = value, scale = scale, distance = 18.dp, vertical = true, direction = { _, _ -> 1 }) { shown ->
            BasicText(shown, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BasicText(
            "›",
            style = arrow,
            // Read in layout, so the room grows frame by frame without recomposing the row.
            modifier = Modifier.layout { measurable, constraints ->
                val glyph = measurable.measure(constraints)
                val room = ((glyph.width + CHOICE_GAP.roundToPx()) * arrows).roundToInt()
                layout(room, glyph.height) {
                    glyph.placeWithLayer(room - glyph.width, 0) { alpha = arrows }
                }
            },
        )
    }
}

/** Between a choice's chevrons and its value. */
private val CHOICE_GAP = 10.dp

/** A way into something else. It nudges toward where it leads while its row has focus. */
@Composable
internal fun OpensChevron(focused: Boolean, style: TextStyle, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val lean by eased(if (focused) 1f else 0f, theme.motion.durationFocus, scale)
    BasicText(
        "›",
        style = style.copy(color = if (focused) theme.onBackground else theme.muted),
        modifier = Modifier.graphicsLayer { translationX = lean * 4.dp.toPx() },
    )
}

/**
 * Swaps [content] for [target]'s with a slide and a fade. The old content leaves
 * [distance] the way [direction] says (1 toward the end, -1 toward the start), and the
 * new one arrives from the other side, overshooting a touch before it settles. Only a
 * new [key] swaps; any other change to [target] updates the content in place. [front]
 * applies to the incoming content alone, so a caller can measure what is arriving.
 */
@Composable
internal fun <T> SlideSwap(
    target: T,
    scale: Float,
    distance: Dp,
    vertical: Boolean,
    direction: (from: T, to: T) -> Int,
    front: Modifier = Modifier,
    key: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    val motion = LocalFoldTheme.current.theme.motion
    var shown by remember { mutableStateOf(target) }
    var back by remember { mutableStateOf<T?>(null) }
    var sign by remember { mutableStateOf(1) }
    val slide = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }
    val swapKey = key(target)
    LaunchedEffect(swapKey) {
        if (swapKey == key(shown)) return@LaunchedEffect
        sign = direction(shown, target).coerceIn(-1, 1).takeIf { it != 0 } ?: 1
        back = shown
        shown = target
        slide.snapTo(0f)
        fade.snapTo(0f)
        withContext(SteadyMenu) {
            coroutineScope {
                launch { slide.animateTo(1f, motion.playful(motion.durationSelect, scale)) }
                launch { fade.animateTo(1f, motion.dissolve(motion.durationSelect, scale)) }
            }
        }
        back = null
    }
    // The same key: the incoming content shows the target as it is now.
    val current = if (swapKey == key(shown)) target else shown
    val previous = back
    Box {
        // Each side keeps its own state under its own key, so the arriving content starts
        // fresh (its scroll at the top) instead of taking over what was leaving.
        if (previous != null) {
            Box(
                Modifier.graphicsLayer {
                    val t = fade.value
                    alpha = (1f - t * 1.5f).coerceIn(0f, 1f)
                    val shift = -sign * t * distance.toPx()
                    if (vertical) translationY = shift else translationX = shift
                },
            ) { key(key(previous)) { content(previous) } }
        }
        Box(
            front.graphicsLayer {
                if (previous == null) return@graphicsLayer
                alpha = fade.value.coerceIn(0f, 1f)
                val shift = sign * (1f - slide.value) * distance.toPx()
                if (vertical) translationY = shift else translationX = shift
            },
        ) { key(swapKey) { content(current) } }
    }
}

/**
 * The art a row leads with, or null for a row that is only words. A tile's verbs wear theirs on
 * their round tiles. Move to Games or Apps shows where it goes, which [moveTo] names.
 */
internal fun rowIcon(row: Row, moveTo: String? = null): Int? = when (row) {
    Row.Home -> R.drawable.symbol_home
    Row.AllLibrary -> R.drawable.symbol_library
    Row.AndroidGames -> R.drawable.symbol_android_games
    Row.Apps -> R.drawable.symbol_android_apps
    Row.EditHome -> R.drawable.ic_status_tools
    Row.Settings -> R.drawable.ic_status_settings
    is Row.RommConnection, is Row.SignOut -> R.drawable.symbol_cloud
    is Row.ForgetFolder -> R.drawable.symbol_folder
    Row.MoveTile -> R.drawable.symbol_move
    Row.AddToCollection -> R.drawable.symbol_add_to
    Row.ChooseArt -> R.drawable.symbol_art
    Row.Saves -> R.drawable.symbol_saves
    Row.RenameFolder -> R.drawable.symbol_rename
    Row.FolderIcon -> R.drawable.symbol_icon
    Row.NewCollection -> R.drawable.symbol_new_collection
    Row.Undo -> R.drawable.symbol_undo
    Row.ShowHidden -> R.drawable.symbol_show
    Row.ShowOnHome, Row.AddNewGames, Row.FolderOnHome -> R.drawable.symbol_home
    Row.Folders -> R.drawable.symbol_folder
    Row.MoveApp -> if (moveTo == HOME_ANDROID_APPS) R.drawable.symbol_android_apps else R.drawable.symbol_android_games
    Row.SelectTile -> R.drawable.symbol_select
    Row.Tidy -> R.drawable.symbol_tidy
    Row.RemoveTile -> R.drawable.symbol_remove
    Row.RestartGame -> R.drawable.symbol_restart
    Row.Hide, Row.Hidden -> R.drawable.symbol_hide
    Row.RemoveDownload -> R.drawable.symbol_remove_download
    Row.DeleteShortcut -> R.drawable.symbol_delete
    Row.SortFolder -> R.drawable.symbol_sort
    Row.RecentOnHome -> R.drawable.symbol_clock
    else -> null
}

/** Height of one menu row, the same for every row so the highlight keeps one size. */
private val MENU_ROW_HEIGHT = 52.dp

/**
 * One menu row: its art, its words, and on the right what it does. That is a switch,
 * a choice, a way in, a value, or the key that runs it. The words brighten and the
 * art lifts a little while the row has focus. [iconSlot] keeps the words in line
 * with rows that have art when this one has none.
 */
@Composable
internal fun MenuRow(
    label: String,
    icon: Int?,
    iconSlot: Boolean,
    focused: Boolean,
    control: RowControl,
    chord: PromptKey?,
    held: Set<PromptKey>,
    scale: Float,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle,
    height: Dp = MENU_ROW_HEIGHT,
) {
    val theme = LocalFoldTheme.current.theme
    val lit = eased(if (focused) 1f else 0f, theme.motion.durationFocus, scale)
    val lift = bouncy(if (focused) 1f else 0f, scale)
    Row(
        modifier.height(height).padding(start = 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null || iconSlot) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    // A symbol is one ink, so it follows the theme's text colour.
                    val art = themeIcon(icon)
                    Image(
                        painter = art.painter,
                        contentDescription = null,
                        colorFilter = art.tint(theme.onBackground),
                        modifier = Modifier.fillMaxSize().lifted(lit, lift, grow = theme.style.menu.artGrow, rest = theme.style.menu.artRestAlpha),
                    )
                }
            }
        }
        BasicText(
            text = label,
            modifier = Modifier.weight(1f).graphicsLayer { alpha = theme.style.menu.labelRestAlpha.let { it + (1f - it) * lit.value } },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = labelStyle.copy(color = theme.onBackground),
        )
        val valueStyle = labelStyle.copy(color = theme.muted, textAlign = TextAlign.End)
        when (control) {
            is RowControl.Toggle -> ToggleSwitch(control.on, focused, scale)
            is RowControl.Check -> CheckMark(control.on, focused, scale, control.some)
            is RowControl.Choice -> ChoiceValue(control.value, focused, valueStyle.copy(color = if (focused) theme.onBackground else theme.muted), scale)
            is RowControl.Value -> BasicText(control.text,
                style = if (control.attention) valueStyle.copy(color = theme.style.colors.attention) else valueStyle,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            RowControl.Opens -> if (chord == null) OpensChevron(focused, labelStyle, scale)
            RowControl.None -> Unit
        }
        ChordHint(chord, held)
    }
}
