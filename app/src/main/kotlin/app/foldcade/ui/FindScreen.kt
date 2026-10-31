package app.foldcade.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.foldcade.FindSession
import app.foldcade.language.FindCopy
import app.foldcade.language.FindEntry
import app.foldcade.language.KeyFocus
import app.foldcade.language.PromptKey
import app.foldcade.language.TypingFaces
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Find on the screen with the keys: one strip of results above the controller keyboard.
 * The strip shows the best result first; the ring rests faintly on it while the keys have
 * the D-pad, so Up or Start plainly lands there, and lights fully once the results have it.
 * Results pop in one after another as they arrive, and a result still there after the next
 * letter glides to its new place rather than popping again. [tile] draws one result as the
 * grid would draw it. [fieldStyle] puts the query, compact, and the filters over the strip,
 * for a screen with no top screen beside it.
 */
@Composable
internal fun FindKeys(
    session: FindSession,
    buttons: TypingFaces,
    held: Set<PromptKey>,
    scale: Float,
    gap: Dp,
    ring: Dp,
    titleBlock: Dp,
    fieldStyle: TextStyle?,
    chipStyle: TextStyle,
    emptyStyle: TextStyle,
    onTapKey: (KeyFocus) -> Unit,
    onTapResult: (Int) -> Unit,
    onTapFilter: (Int) -> Unit,
    tile: @Composable (entry: FindEntry, focused: Boolean, size: Dp, onClick: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The strip takes a share of the height and the keys the rest; four results across at most.
        val fromHeight = maxHeight * RESULTS_SHARE - ring * 2 - titleBlock
        val fromWidth = (maxWidth - ring * 2 - gap * (RESULTS_ACROSS - 1)) / RESULTS_ACROSS
        val cell = minOf(fromHeight, fromWidth).coerceAtLeast(MIN_CELL.dp)
        Column(Modifier.fillMaxSize()) {
            if (fieldStyle != null) {
                val theme = LocalFoldTheme.current.theme
                TypingField(
                    typing = session.typing.typing,
                    style = fieldStyle,
                    background = theme.surface,
                    scale = scale,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ring),
                    lead = { Magnifier(theme.muted, with(LocalDensity.current) { fieldStyle.fontSize.toDp() }) },
                    compact = true,
                )
                FindFilters(session, chipStyle, scale, Modifier.fillMaxWidth().padding(top = 8.dp), onTapFilter)
            }
            ResultStrip(
                session = session,
                cell = cell,
                gap = gap,
                pad = ring,
                titleBlock = titleBlock,
                scale = scale,
                emptyStyle = emptyStyle,
                onTap = onTapResult,
                tile = tile,
            )
            ControllerKeyboard(
                typing = session.typing.typing,
                buttons = buttons,
                held = held,
                scale = scale,
                onTap = onTapKey,
                active = !session.onResults,
                modifier = Modifier.weight(1f),
                chipRow = false,
                done = FindCopy.results,
                // L2 steps the filters here; a search has no case to shift.
                shiftButton = null,
            )
        }
    }
}

/**
 * The results, side by side, scrolled only as far as the focused one needs. Each keeps its
 * own place as the list changes, so a result that stays slides to where it now sits.
 */
@Composable
private fun ResultStrip(
    session: FindSession,
    cell: Dp,
    gap: Dp,
    pad: Dp,
    titleBlock: Dp,
    scale: Float,
    emptyStyle: TextStyle,
    onTap: (Int) -> Unit,
    tile: @Composable (entry: FindEntry, focused: Boolean, size: Dp, onClick: () -> Unit) -> Unit,
) {
    val theme = LocalFoldTheme.current.theme
    val motion = theme.motion
    val popFrom = theme.style.motion.popFrom
    val density = LocalDensity.current
    val results = session.results
    BoxWithConstraints(Modifier.fillMaxWidth().height(cell + titleBlock + pad * 2)) {
        val pitchPx = with(density) { (cell + gap).toPx() }
        val padPx = with(density) { pad.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val across = max(1, floor((widthPx - padPx * 2 + with(density) { gap.toPx() }) / pitchPx).toInt())
        val focus = if (session.onResults) session.index else 0
        // The first result on screen moves only as far as it must to keep the focus in view.
        val first = remember { IntArray(1) }
        first[0] = when {
            focus < first[0] -> focus
            focus > first[0] + across - 1 -> focus - across + 1
            else -> first[0]
        }.coerceIn(0, max(0, results.size - across))
        val shownFirst by eased(first[0].toFloat(), motion.durationTravel, scale)
        // Results centre while there are fewer than fit, and start at the left once they fill it.
        val spare = max(0f, widthPx - padPx * 2 - (results.size * pitchPx - with(density) { gap.toPx() }))
        val lead by eased(spare / 2f, motion.durationTravel, scale)
        if (results.isEmpty()) {
            BasicText(FindCopy.noMatches, Modifier.align(Alignment.Center), style = emptyStyle)
        }
        results.forEachIndexed { index, entry ->
            key(entry.id) {
                val at by eased(index.toFloat(), motion.durationTravel, scale)
                val pop = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    if (motion.reduced(scale)) {
                        pop.snapTo(1f)
                        return@LaunchedEffect
                    }
                    delay(min(index, POP_STAGGER_LIMIT) * POP_STAGGER_MS)
                    withContext(SteadyMenu) { pop.animateTo(1f, motion.playful(motion.durationSelect, scale)) }
                }
                Box(
                    Modifier
                        .offset { IntOffset((padPx + lead + (at - shownFirst) * pitchPx).roundToInt(), padPx.roundToInt()) }
                        .graphicsLayer {
                            // A result fades as it slides past either edge, so the strip has no cut ends.
                            val along = at - shownFirst
                            val outside = maxOf(-along, along - (across - 1), 0f)
                            val grown = popFrom + (1f - popFrom) * pop.value
                            alpha = (1f - outside).coerceIn(0f, 1f) * pop.value.coerceIn(0f, 1f)
                            scaleX = grown
                            scaleY = grown
                        },
                ) {
                    tile(entry, session.onResults && session.index == index, cell) { onTap(index) }
                }
            }
        }
        if (results.isNotEmpty()) {
            ResultRing(
                index = focus,
                lit = session.onResults,
                cell = cell,
                left = { padPx + lead + (focus - shownFirst) * pitchPx },
                top = padPx,
                pitchPx = pitchPx,
                scale = scale,
            )
        }
    }
}

/**
 * The strip's one ring. It glides from result to result like the grid's, full while the
 * results have the D-pad and faint on the best result while the keys do.
 */
@Composable
private fun ResultRing(index: Int, lit: Boolean, cell: Dp, left: () -> Float, top: Float, pitchPx: Float, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val motion = theme.motion
    val strength by eased(if (lit) 1f else theme.style.find.ringRestAlpha, motion.durationFocus, scale)
    val x by eased(index.toFloat(), motion.durationTravel, scale)
    val ring = remember(theme) { FocusRing(theme) }
    val path = remember { Path() }
    val radius = cell * theme.iconRadius
    Box(
        Modifier
            .size(cell)
            .graphicsLayer {
                translationX = left() + (x - index) * pitchPx
                translationY = top
                clip = false
                val grown = 1f + (motion.scaleFocus - 1f) * strength
                scaleX = grown
                scaleY = grown
            }
            .drawBehind {
                val corner = radius.toPx()
                path.reset()
                path.addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(corner, corner)))
                // Only outside the tile, so the glow never washes over the art. The ring fades
                // itself: a faded layer would cut its glow off at the tile's square edge.
                clipPath(path, ClipOp.Difference) { with(ring) { drawFocusRing(corner, alpha = strength) } }
            },
    )
}

/**
 * The top screen's Find: the focused or best result's name and where it lives, the query
 * large in its lit field behind a magnifier, and the filters under it. It sits where the
 * name card does, under the result's art.
 */
@Composable
internal fun FindCard(
    session: FindSession,
    line: FoundLine,
    fieldStyle: TextStyle,
    titleStyle: TextStyle,
    factStyle: TextStyle,
    chipStyle: TextStyle,
    background: Color,
    width: Dp,
    scale: Float,
    modifier: Modifier = Modifier,
    onTapFilter: ((Int) -> Unit)? = null,
) {
    val theme = LocalFoldTheme.current.theme
    val density = LocalDensity.current
    Column(modifier.widthIn(max = width), horizontalAlignment = Alignment.CenterHorizontally) {
        // A card under the name, as Home's name card is, so bright art never washes it out. It
        // stays put while the name in it slides.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(theme.style.metrics.cardCornerDp.dp))
                .background(background)
                .padding(horizontal = with(density) { CARD_SIDE_PX.toDp() }, vertical = with(density) { CARD_PAD_PX.toDp() }),
        ) {
            // The name slides the way the ring went along the strip.
            SlideSwap(
                target = line,
                scale = scale,
                distance = 32.dp,
                vertical = false,
                direction = { from, to -> to.order.compareTo(from.order) },
                key = { it.key },
            ) { shown ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(
                        text = shown.title ?: FindCopy.noMatches,
                        style = titleStyle.copy(fontWeight = theme.style.type.strongWeight, textAlign = TextAlign.Center),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        text = shown.facts,
                        style = factStyle.copy(textAlign = TextAlign.Center),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TypingField(
            typing = session.typing.typing,
            style = fieldStyle,
            background = background,
            scale = scale,
            modifier = Modifier.fillMaxWidth(),
            lead = { Magnifier(theme.muted, with(LocalDensity.current) { fieldStyle.fontSize.toDp() }) },
        )
        Spacer(Modifier.height(12.dp))
        // The filters sit on a pill of the same glass.
        FindFilters(
            session,
            chipStyle,
            scale,
            Modifier
                .fillMaxWidth()
                .clip(theme.style.shape.control())
                .background(background)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            onTapFilter,
        )
    }
}

/** The result the top screen names: its [key], name and facts, and its [order] along the strip. */
internal data class FoundLine(val key: String?, val title: String?, val facts: String, val order: Int)

/** Find's filters, as a [PillCarousel]. A tap on a pill chooses it. */
@Composable
internal fun FindFilters(session: FindSession, style: TextStyle, scale: Float, modifier: Modifier = Modifier, onTap: ((Int) -> Unit)? = null) =
    PillCarousel(session.filters.map { it.label }, session.filter, style, scale, modifier, onTap)

/**
 * A row of pills between L2 and R2's glyphs: Find's filters, or All games' tabs. One highlight
 * glides to the [chosen] pill with the menus' bounce, and the row slides so it stays in view.
 * With [onTap], a tap on a pill chooses it, and a tap on L2's or R2's glyph steps as the
 * trigger would, round the ends.
 */
@Composable
internal fun PillCarousel(
    labels: List<String>,
    chosen: Int,
    style: TextStyle,
    scale: Float,
    modifier: Modifier = Modifier,
    onTap: ((Int) -> Unit)? = null,
) {
    val theme = LocalFoldTheme.current.theme
    val motion = theme.motion
    val density = LocalDensity.current
    val spans = remember { mutableStateMapOf<Int, Rect>() }
    val glyph = with(density) { style.fontSize.toDp() } * GLYPH_SHARE
    val step = { by: Int -> if (onTap != null && labels.size > 1) onTap(Math.floorMod(chosen + by, labels.size)) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PromptImage(promptGlyph(PromptKey.L2), glyph, if (onTap != null) Modifier.hostPress { step(-1) } else Modifier)
        BoxWithConstraints(Modifier.weight(1f).clipToBounds()) {
            val room = with(density) { maxWidth.toPx() }
            val end = spans.values.maxOfOrNull { it.right } ?: 0f
            val target = spans[chosen]?.let { span ->
                // Centred on the chosen pill, as far as the row allows; a short row sits centred.
                if (end <= room) (end - room) / 2f else (span.center.x - room / 2f).coerceIn(0f, end - room)
            } ?: 0f
            val scroll by eased(target, motion.durationTravel, scale)
            val highlight = remember { Animatable(0f) }
            val width = remember { Animatable(0f) }
            // State, so the draw that skipped the highlight before it was placed runs again once it is.
            var placed by remember { mutableStateOf(false) }
            LaunchedEffect(chosen, spans[chosen]) {
                val span = spans[chosen] ?: return@LaunchedEffect
                if (!placed) {
                    highlight.snapTo(span.left)
                    width.snapTo(span.width)
                    placed = true
                    return@LaunchedEffect
                }
                withContext(SteadyMenu) {
                    coroutineScope {
                        launch { highlight.animateTo(span.left, motion.playful(motion.durationSelect, scale)) }
                        launch { width.animateTo(span.width, motion.arrive(motion.durationSelect, scale)) }
                    }
                }
            }
            val ring = remember(theme) { FocusRing(theme) }
            val find = theme.style.find
            val round = theme.style.shape.round
            Box(Modifier.fillMaxWidth().fadeEnds(spans, { spans[chosen] }, { scroll }, density)) {
                Row(
                    Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .graphicsLayer { translationX = -scroll }
                        .drawWithContent {
                            drawContent()
                            if (!placed) return@drawWithContent
                            // Over the pills, so the chosen one is lit glass with the ring's colours at its rim.
                            val corner = size.height * round
                            val topLeft = Offset(highlight.value, 0f)
                            val pill = Size(width.value, size.height)
                            // The rim's stroke sits inside the pill, so the row's edge never cuts half of it off.
                            val rim = find.chosenRimDp.dp.toPx()
                            val inset = Offset(rim / 2f, rim / 2f)
                            val rimSize = Size((pill.width - rim).coerceAtLeast(0f), (pill.height - rim).coerceAtLeast(0f))
                            val rimCorner = (corner - rim / 2f).coerceAtLeast(0f)
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    listOf(ring.start.copy(alpha = find.chosenAlpha), ring.end.copy(alpha = find.chosenAlpha * 0.6f)),
                                    startX = topLeft.x,
                                    endX = topLeft.x + pill.width,
                                ),
                                topLeft = topLeft,
                                size = pill,
                                cornerRadius = CornerRadius(corner, corner),
                            )
                            drawRoundRect(
                                brush = Brush.horizontalGradient(listOf(ring.start, ring.end), startX = topLeft.x, endX = topLeft.x + pill.width),
                                topLeft = topLeft + inset,
                                size = rimSize,
                                cornerRadius = CornerRadius(rimCorner, rimCorner),
                                style = Stroke(rim),
                            )
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    labels.forEachIndexed { index, label ->
                        val on by eased(if (index == chosen) 1f else 0f, motion.durationFocus, scale)
                        Box(
                            Modifier
                                .then(if (onTap != null) Modifier.hostPress { onTap(index) } else Modifier)
                                .onGloballyPositioned {
                                    val rect = Rect(it.positionInParent(), it.size.toSize())
                                    if (spans[index] != rect) spans[index] = rect
                                }
                                .clip(theme.style.shape.control())
                                .drawBehind { drawRoundRect(theme.onBackground.copy(alpha = find.filterAlpha), cornerRadius = CornerRadius(size.height * round)) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            BasicText(
                                text = label,
                                maxLines = 1,
                                style = style.copy(color = lerpInk(theme.muted, theme.onBackground, on)),
                            )
                        }
                    }
                }
            }
            // Pills that left the list are forgotten, so the row's end is where its last pill is.
            LaunchedEffect(labels.size) { spans.keys.retainAll { it < labels.size } }
        }
        PromptImage(promptGlyph(PromptKey.R2), glyph, if (onTap != null) Modifier.hostPress { step(1) } else Modifier)
    }
}

/**
 * An edge with more pills past it fades out rather than cutting one in half. Only that edge:
 * scrolled to an end, the end has nothing more and stays plain. Each fade grows in with the
 * [scroll], and shrinks away rather than reach over the [chosen] pill.
 */
private fun Modifier.fadeEnds(
    spans: Map<Int, Rect>,
    chosen: () -> Rect?,
    scroll: () -> Float,
    density: androidx.compose.ui.unit.Density,
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val width = size.width
        if (width <= 0f) return@drawWithContent
        val end = spans.values.maxOfOrNull { it.right } ?: 0f
        val shift = scroll()
        val fade = with(density) { FADE.dp.toPx() }
        val pill = chosen()
        // How far each fade may reach before it would touch the chosen pill on screen.
        val leftReach = pill?.let { min(fade, max(0f, it.left - shift)) } ?: fade
        val rightReach = pill?.let { min(fade, max(0f, width - (it.right - shift))) } ?: fade
        // How much lies past each edge, up to a fade's width, scaled down as the reach shrinks.
        val left = (shift / fade).coerceIn(0f, 1f) * (leftReach / fade)
        val right = ((end - shift - width) / fade).coerceIn(0f, 1f) * (rightReach / fade)
        if (left <= 0f && right <= 0f) return@drawWithContent
        val leftStop = max(leftReach, 1f) / width
        val rightStop = (1f - max(rightReach, 1f) / width).coerceAtLeast(leftStop)
        drawRect(
            brush = Brush.horizontalGradient(
                0f to lerpInk(Color.Black, Color.Transparent, left),
                leftStop to Color.Black,
                rightStop to Color.Black,
                1f to lerpInk(Color.Black, Color.Transparent, right),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private fun lerpInk(from: Color, to: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(from, to, t.coerceIn(0f, 1f))

/** A magnifying glass (`control.search`), in [ink] at [size], so the field reads as a search before anything is typed. */
@Composable
internal fun Magnifier(ink: Color, size: Dp) {
    val icon = themeIconOrNull("control.search", 128) ?: return
    Image(icon.painter, contentDescription = null, colorFilter = icon.tint(ink), modifier = Modifier.size(size))
}

/**
 * A small eye with a line through it (`badge.hidden`), on a dark disc, in a hidden result's
 * corner. Hidden things still show in Find, so this tells them apart at a glance.
 */
@Composable
internal fun HiddenBadge(size: Dp, modifier: Modifier = Modifier) {
    val theme = LocalFoldTheme.current.theme
    val icon = themeIconOrNull("badge.hidden", 128)
    Box(
        modifier
            .size(size)
            .clip(theme.style.shape.control())
            .background(theme.background.copy(alpha = theme.style.find.hiddenBackingAlpha)),
    ) {
        if (icon != null) Image(icon.painter, contentDescription = null, colorFilter = icon.tint(theme.onBackground), modifier = Modifier.fillMaxSize())
    }
}

/** Find's sliding layer: it rises from the bottom edge as it opens and sinks back as it closes. */
internal fun Modifier.slidingUp(progress: () -> Float): Modifier = graphicsLayer {
    val t = progress()
    alpha = t
    translationY = (1f - t) * size.height * SLIDE_SHARE
}

/** The share of the keys' screen the result strip takes, at most. */
private const val RESULTS_SHARE = 0.34f

/** At most this many results across, so each stays big enough to read. */
private const val RESULTS_ACROSS = 4.4f
private const val MIN_CELL = 48f

/** Results pop in this far apart, and only the first few wait their turn. */
private const val POP_STAGGER_MS = 28L
private const val POP_STAGGER_LIMIT = 8

/** The L2 and R2 glyphs beside the filters, as a share of their text. */
private const val GLYPH_SHARE = 1.5f
private const val FADE = 52f

/** The name card's padding, in the top screen's pixels, as Home's name card has. */
private const val CARD_SIDE_PX = 40f
private const val CARD_PAD_PX = 20f

/** How far Find's layer travels up as it opens, as a share of its height. */
private const val SLIDE_SHARE = 0.5f
