package app.foldcade.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.foldcade.language.CHIP_ROW
import app.foldcade.language.KeyCap
import app.foldcade.language.KeyFocus
import app.foldcade.language.KeyboardCopy
import app.foldcade.language.KeyboardLayout
import app.foldcade.language.PlacedKey
import app.foldcade.language.PromptKey
import app.foldcade.language.ShiftState
import app.foldcade.language.Typing
import app.foldcade.language.TypingFaces
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The controller keyboard, drawn where the grid was. Glossy keys with a neon rim, the
 * quick-name chips above them, and one highlight that glides from key to key and up to
 * the chips, landing with the menus' little bounce. A key flashes when it acts, even
 * when a button did the work (X flashes Erase, Start flashes Done), and each of those
 * keys wears its button's glyph. Letters roll between cases with Shift. [field] draws
 * the text above the chips, for a screen with no top screen beside it. [buttons] says
 * which face buttons erase and type a space under the player's calibration, so the keys
 * show the right glyphs. A tap presses a key through [onTap]; nothing here raises
 * Android's keyboard.
 */
@Composable
internal fun ControllerKeyboard(
    typing: Typing,
    buttons: TypingFaces,
    held: Set<PromptKey>,
    scale: Float,
    onTap: (KeyFocus) -> Unit,
    modifier: Modifier = Modifier,
    field: (@Composable (Modifier) -> Unit)? = null,
) {
    val theme = LocalFoldTheme.current.theme
    val motion = theme.motion
    val density = LocalDensity.current
    val reach = ringReach()
    val ring = remember(theme) { FocusRing(theme) }
    val caps by eased(if (typing.shift == ShiftState.Off) 0f else 1f, motion.durationFocus, scale)
    val flash = remember { Animatable(0f) }
    LaunchedEffect(typing.pressed) {
        if (typing.pressed == 0) return@LaunchedEffect
        flash.snapTo(1f)
        withContext(SteadyMenu) { flash.animateTo(0f, motion.leave(motion.durationSelect, scale)) }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val rows = KeyboardLayout.rows.size
        val fieldUnits = if (field != null) FIELD_HEIGHT + FIELD_GAP else 0f
        val tallUnits = fieldUnits + CHIP_HEIGHT + CHIP_GAP + rows * ROW_PITCH - KEY_GAP
        val unit = minOf((maxWidth - reach * 2) / KeyboardLayout.WIDTH, (maxHeight - reach * 2) / tallUnits)
        val blockWidth = unit * KeyboardLayout.WIDTH
        val left = (maxWidth - blockWidth) / 2
        val top = (maxHeight - unit * tallUnits) / 2
        val chipsTop = top + unit * fieldUnits
        val keysTop = chipsTop + unit * (CHIP_HEIGHT + CHIP_GAP)
        val unitPx = with(density) { unit.toPx() }
        val leftPx = with(density) { left.toPx() }
        val chipsTopPx = with(density) { chipsTop.toPx() }
        val keysTopPx = with(density) { keysTop.toPx() }
        val blockWidthPx = with(density) { blockWidth.toPx() }

        // Chips are as wide as their words; each reports where it sits along the strip.
        val chipSpans = remember { mutableStateMapOf<Int, Rect>() }
        // The strip scrolls only as far as it must to show the chip the cursor is on or returns
        // to, with room past it for the next one to peek in, faded, so more chips read as more.
        val scrolled = remember { FloatArray(1) }
        val focusedChip = typing.focus.takeIf { it.onChips }?.index ?: typing.chip
        chipSpans[focusedChip]?.let { span ->
            val edge = unitPx * CHIP_PEEK
            val furthest = ((chipSpans.values.maxOfOrNull { it.right } ?: 0f) - blockWidthPx).coerceAtLeast(0f)
            scrolled[0] = when {
                span.right + edge - scrolled[0] > blockWidthPx -> span.right + edge - blockWidthPx
                span.left - edge < scrolled[0] -> span.left - edge
                else -> scrolled[0]
            }.coerceIn(0f, furthest)
        }
        val scrollTarget = scrolled[0]
        val scroll by eased(scrollTarget, motion.durationTravel, scale)

        // Where the highlight goes, worked out from the layout rather than measured, so it
        // can set off before the strip has scrolled.
        val lift = motion.scaleFocus
        val target by rememberUpdatedState(
            if (typing.focus.onChips) {
                chipSpans[typing.focus.index]?.let { span ->
                    Rect(Offset(leftPx + span.left - scrollTarget, chipsTopPx), span.size).grown(lift)
                }
            } else {
                KeyboardLayout.key(typing.focus)?.let { key ->
                    keyRect(key, typing.focus.row, unitPx, leftPx, keysTopPx).grown(lift)
                }
            },
        )
        val highlight = rememberGlidingRect({ target }, scale)

        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val rect = highlight.rect() ?: return@drawWithContent
                    val corner = unitPx * KEY_CORNER * lift
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(ring.start.copy(alpha = 0.26f), ring.end.copy(alpha = 0.10f)),
                            startY = rect.top,
                            endY = rect.bottom,
                        ),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = CornerRadius(corner, corner),
                    )
                    with(ring) { drawFocusRing(corner, bounds = rect) }
                },
        ) {
            if (field != null) {
                field(
                    Modifier
                        .offset(left, top)
                        .size(blockWidth, unit * FIELD_HEIGHT),
                )
            }
            ChipStrip(
                typing = typing,
                unit = unit,
                scroll = { scroll },
                spans = chipSpans,
                flash = { flash.value },
                onTap = onTap,
                modifier = Modifier
                    .offset(left, chipsTop)
                    .size(blockWidth, unit * CHIP_HEIGHT),
            )
            KeyboardLayout.rows.forEachIndexed { row, keys ->
                keys.forEachIndexed { index, placed ->
                    val at = KeyFocus(row, index, placed.center)
                    val rect = keyRect(placed, row, unitPx, leftPx, keysTopPx)
                    key(row, index) {
                        Key(
                            placed = placed,
                            typing = typing,
                            focused = !typing.focus.onChips && typing.focus.row == row && typing.focus.index == index,
                            flashed = typing.pressedKey == at,
                            flash = { flash.value },
                            caps = { caps },
                            buttons = buttons,
                            held = held,
                            unit = unit,
                            scale = scale,
                            modifier = Modifier
                                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                                .tap { onTap(at) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The text being typed, large, in a lit card: a selection glows behind its letters, and
 * the caret breathes and glides to where it goes next. A press the field turns down
 * shakes it. [background] is the card's fill, so it can let art through.
 */
@Composable
internal fun TypingField(
    typing: Typing,
    style: TextStyle,
    background: Color,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val theme = LocalFoldTheme.current.theme
    val motion = theme.motion
    val ring = remember(theme) { FocusRing(theme) }
    val shake = remember { Animatable(1f) }
    LaunchedEffect(typing.refusals) {
        if (typing.refusals == 0) return@LaunchedEffect
        shake.snapTo(0f)
        withContext(SteadyMenu) { shake.animateTo(1f, motion.arrive(motion.durationIsland * 2, scale)) }
    }
    val breath = if (motion.reduced(scale)) {
        remember { mutableStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "caret").animateFloat(CARET_DIM, 1f, motion.pulse(scale), label = "caret")
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val draft = typing.draft
    val caretTarget = layout?.takeIf { draft.caret <= it.layoutInput.text.length }?.getHorizontalPosition(draft.caret, true)
    val caretX = remember { Animatable(caretTarget ?: 0f) }
    var caretPlaced by remember { mutableStateOf(false) }
    LaunchedEffect(caretTarget) {
        val to = caretTarget ?: return@LaunchedEffect
        if (!caretPlaced) {
            caretX.snapTo(to)
            caretPlaced = true
            return@LaunchedEffect
        }
        withContext(SteadyMenu) {
            caretX.animateTo(to, motion.glide(motion.durationFocus, scale, to - caretX.value, caretX.velocity))
        }
    }
    val corner = LocalFoldTheme.current.theme.style.metrics.cardCornerDp.dp
    Box(
        modifier
            .graphicsLayer {
                val t = shake.value
                translationX = sin(t * PI.toFloat() * SHAKE_SWINGS * 2f) * (1f - t) * SHAKE_REACH.dp.toPx()
            }
            .drawBehind {
                val radius = corner.toPx()
                drawRoundRect(color = background, cornerRadius = CornerRadius(radius, radius))
                with(ring) { drawFocusRing(radius, alpha = 0.55f) }
            }
            .padding(horizontal = 32.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val room = constraints.maxWidth.toFloat()
            val density = LocalDensity.current
            val base = style.copy(textAlign = TextAlign.Start, fontWeight = FontWeight.SemiBold)
            // A long name first shrinks, down to a floor, so more of it stays readable at once.
            val measurer = rememberTextMeasurer()
            val natural = remember(draft.text, base, density) {
                measurer.measure(draft.text, base, maxLines = 1, softWrap = false).size.width
            }
            val fit = if (natural <= 0) 1f else (room / natural).coerceIn(TYPED_FLOOR, 1f)
            val grow by eased(fit, motion.durationFocus, scale)
            val shown = base.copy(fontSize = base.fontSize * grow)
            // Past the floor it scrolls: only as far as it must to keep the caret, with
            // some of the name either side of it, in view. A short name sits centred.
            val wide = layout?.size?.width?.toFloat() ?: 0f
            val edge = with(density) { CARET_ROOM.dp.toPx() }
            // The caret at either end of a long name keeps a little room, so its glow is never cut.
            val ends = with(density) { CARET_END.dp.toPx() }
            val scrolled = remember { FloatArray(1) }
            val caretAt = caretTarget ?: 0f
            val over = wide - room
            scrolled[0] = if (over <= 0f) {
                0f
            } else {
                when {
                    caretAt - scrolled[0] > room - edge -> caretAt - room + edge
                    caretAt - scrolled[0] < edge -> caretAt - edge
                    else -> scrolled[0]
                }.coerceIn(-ends, over + ends)
            }
            val along by eased(if (over <= 0f) (room - wide) / 2f else -scrolled[0], motion.durationFocus, scale)
            val fade = with(density) { FIELD_FADE.dp.toPx() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // An end with more of the name past it fades out rather than cutting a letter.
                        val left = if (along < -1f) fade / size.width else 0f
                        val right = if (along + wide > size.width + 1f) 1f - fade / size.width else 1f
                        if (left == 0f && right == 1f) return@drawWithContent
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to if (left > 0f) Color.Transparent else Color.Black,
                                left to Color.Black,
                                right to Color.Black,
                                1f to if (right < 1f) Color.Transparent else Color.Black,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .padding(vertical = 6.dp),
            ) {
                BasicText(
                    text = draft.text,
                    style = shown,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = { layout = it },
                    modifier = Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .graphicsLayer { translationX = along }
                        .drawWithContent {
                            val laid = layout ?: return@drawWithContent drawContent()
                            val lineTop = laid.getLineTop(0)
                            val lineBottom = laid.getLineBottom(0)
                            if (draft.selecting) {
                                val from = laid.getHorizontalPosition(draft.start, true)
                                val to = laid.getHorizontalPosition(draft.end, true)
                                val pad = 6.dp.toPx()
                                val radius = 10.dp.toPx()
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(
                                            ring.start.copy(alpha = 0.55f * (0.7f + 0.3f * breath.value)),
                                            ring.end.copy(alpha = 0.40f * (0.7f + 0.3f * breath.value)),
                                        ),
                                    ),
                                    topLeft = Offset(from - pad, lineTop),
                                    size = Size(to - from + pad * 2f, lineBottom - lineTop),
                                    cornerRadius = CornerRadius(radius, radius),
                                )
                            }
                            drawContent()
                            if (!draft.selecting) {
                                val width = 4.dp.toPx()
                                val inset = (lineBottom - lineTop) * 0.1f
                                val glow = 6.dp.toPx()
                                drawRoundRect(
                                    brush = Brush.verticalGradient(listOf(ring.start, ring.end), startY = lineTop, endY = lineBottom),
                                    topLeft = Offset(caretX.value - width / 2f - glow, lineTop + inset - glow / 2f),
                                    size = Size(width + glow * 2f, lineBottom - lineTop - inset * 2f + glow),
                                    cornerRadius = CornerRadius(width / 2f + glow, width / 2f + glow),
                                    alpha = 0.22f * breath.value,
                                )
                                drawRoundRect(
                                    brush = Brush.verticalGradient(listOf(ring.start, ring.end), startY = lineTop, endY = lineBottom),
                                    topLeft = Offset(caretX.value - width / 2f, lineTop + inset),
                                    size = Size(width, lineBottom - lineTop - inset * 2f),
                                    cornerRadius = CornerRadius(width / 2f, width / 2f),
                                    alpha = breath.value,
                                )
                            }
                        },
                )
            }
        }
    }
}

/** The quick names, scrolled so the focused one is in view. Each pill is as wide as its word. */
@Composable
private fun ChipStrip(
    typing: Typing,
    unit: Dp,
    scroll: () -> Float,
    spans: MutableMap<Int, Rect>,
    flash: () -> Float,
    onTap: (KeyFocus) -> Unit,
    modifier: Modifier,
) {
    val theme = LocalFoldTheme.current.theme
    val density = LocalDensity.current
    val labelSize = with(density) { (unit * CHIP_LABEL).toSp() }
    val fadePx = with(density) { (unit * CHIP_FADE).toPx() }
    // Only the pills are clipped; the highlight draws above the strip, so its glow is whole.
    // An edge with more chips past it fades out rather than cutting a pill in half.
    Box(
        modifier
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val width = size.width
                val shift = scroll()
                val end = spans.values.maxOfOrNull { it.right } ?: 0f
                val left = if (shift > 1f) fadePx / width else 0f
                val right = if (end - shift > width + 1f) 1f - fadePx / width else 1f
                if (left == 0f && right == 1f) return@drawWithContent
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to if (left > 0f) Color.Transparent else Color.Black,
                        left to Color.Black,
                        right to Color.Black,
                        1f to if (right < 1f) Color.Transparent else Color.Black,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Row(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -scroll() },
            horizontalArrangement = Arrangement.spacedBy(unit * CHIP_SPACING),
        ) {
            typing.chips.forEachIndexed { index, name ->
                val focused = typing.focus.onChips && typing.focus.index == index
                val flashed = typing.pressedKey?.let { it.onChips && it.index == index } == true
                Box(
                    Modifier
                        .height(unit * CHIP_HEIGHT)
                        .onGloballyPositioned {
                            val rect = Rect(it.positionInParent(), it.size.toSize())
                            if (spans[index] != rect) spans[index] = rect
                        }
                        .drawBehind {
                            val radius = size.height / 2f
                            drawRoundRect(
                                color = theme.onBackground.copy(alpha = 0.08f + 0.20f * if (flashed) flash() else 0f),
                                cornerRadius = CornerRadius(radius, radius),
                            )
                            drawRoundRect(
                                color = theme.onBackground.copy(alpha = 0.12f),
                                cornerRadius = CornerRadius(radius, radius),
                                style = Stroke(1.dp.toPx()),
                            )
                        }
                        .tap { onTap(KeyFocus(CHIP_ROW, index, typing.focus.x)) }
                        .padding(horizontal = unit * CHIP_PAD),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = name,
                        maxLines = 1,
                        style = TextStyle(
                            color = theme.onBackground.copy(alpha = if (focused) 1f else 0.82f),
                            fontSize = labelSize,
                            fontFamily = theme.font,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * One key. It lifts with a bounce under the highlight and squashes as it acts. A letter
 * shows both cases and rolls between them with [caps]. Shift lights while it is on and
 * shows a lamp for caps lock.
 */
@Composable
private fun Key(
    placed: PlacedKey,
    typing: Typing,
    focused: Boolean,
    flashed: Boolean,
    flash: () -> Float,
    caps: () -> Float,
    buttons: TypingFaces,
    held: Set<PromptKey>,
    unit: Dp,
    scale: Float,
    modifier: Modifier,
) {
    val theme = LocalFoldTheme.current.theme
    val ring = remember(theme) { FocusRing(theme) }
    val lift by bouncy(if (focused) 1f else 0f, scale)
    val lit by eased(if (focused) 1f else 0f, theme.motion.durationFocus, scale)
    val cap = placed.cap
    val shiftOn = cap == KeyCap.Shift && typing.shift != ShiftState.Off
    val shiftLit by eased(if (shiftOn) 1f else 0f, theme.motion.durationFocus, scale)
    val lamp by eased(if (cap == KeyCap.Shift && typing.shift == ShiftState.Locked) 1f else 0f, theme.motion.durationFocus, scale)
    val focusScale = theme.motion.scaleFocus
    val density = LocalDensity.current
    val labelSize = with(density) { (unit * KEY_LABEL).toSp() }
    val wordSize = with(density) { (unit * KEY_WORD).toSp() }
    Box(
        modifier
            .graphicsLayer {
                clip = false
                val pressed = if (flashed) flash() else 0f
                val grown = 1f + (focusScale - 1f) * lift - KEY_SQUASH * pressed
                scaleX = grown
                scaleY = grown
            }
            .drawBehind {
                val corner = unit.toPx() * KEY_CORNER
                val radius = CornerRadius(corner, corner)
                val modifierKey = cap !is KeyCap.Glyph
                val body = if (modifierKey) 0.05f else 0.12f
                // Glass: lit at the top, the theme's surface below, the backdrop faintly through it.
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            androidx.compose.ui.graphics.lerp(theme.surface, theme.onBackground, body + 0.06f * lit),
                            theme.surface,
                        ),
                    ),
                    cornerRadius = radius,
                    alpha = KEY_ALPHA,
                )
                if (cap == KeyCap.Done || shiftLit > 0f) {
                    drawRoundRect(
                        brush = Brush.linearGradient(listOf(ring.start, ring.end), Offset.Zero, Offset(size.width, size.height)),
                        cornerRadius = radius,
                        alpha = if (cap == KeyCap.Done) DONE_ALPHA else SHIFT_ALPHA * shiftLit,
                    )
                }
                val pressed = if (flashed) flash() else 0f
                if (pressed > 0f) {
                    drawRoundRect(color = theme.onBackground, cornerRadius = radius, alpha = 0.22f * pressed)
                }
                // A sheen across the top half, and a rim that catches the light at the top edge.
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(theme.onBackground.copy(alpha = 0.10f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.5f,
                    ),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(theme.onBackground.copy(alpha = 0.22f), theme.onBackground.copy(alpha = 0.04f)),
                    ),
                    cornerRadius = radius,
                    style = Stroke(1.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val ink = theme.onBackground
        val glyphSize = unit * KEY_GLYPH
        when (cap) {
            is KeyCap.Glyph -> {
                val style = TextStyle(color = ink, fontSize = labelSize, fontFamily = theme.font, textAlign = TextAlign.Center)
                if (cap.lower == cap.upper) {
                    BasicText(cap.lower.toString(), style = style)
                } else {
                    val roll = with(density) { (unit * 0.18f).toPx() }
                    BasicText(
                        cap.lower.toString(),
                        style = style,
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - caps()
                            translationY = caps() * roll
                        },
                    )
                    BasicText(
                        cap.upper.toString(),
                        style = style,
                        modifier = Modifier.graphicsLayer {
                            alpha = caps()
                            translationY = (caps() - 1f) * roll
                        },
                    )
                }
            }
            KeyCap.Shift -> KeyArt(PromptKey.L2, held, glyphSize) { drawShift(ink, shiftLit, lamp, ring) }
            KeyCap.Erase -> KeyArt(buttons.eraseGlyph, held, glyphSize) { drawErase(ink) }
            KeyCap.Space -> KeyArt(buttons.spaceGlyph, held, glyphSize, artWidth = 2.4f) { drawSpace(ink) }
            KeyCap.Done -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(unit * 0.16f),
            ) {
                BasicText(
                    KeyboardCopy.done,
                    style = TextStyle(color = ink, fontSize = wordSize, fontFamily = theme.font, fontWeight = FontWeight.SemiBold),
                )
                ButtonGlyph(PromptKey.Start, held, glyphSize)
            }
        }
    }
}

/** A key's drawn symbol beside the button that also does its job. */
@Composable
private fun KeyArt(button: PromptKey, held: Set<PromptKey>, glyph: Dp, artWidth: Float = 1f, art: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(glyph * 0.3f)) {
        Canvas(Modifier.size(glyph * artWidth, glyph), onDraw = art)
        ButtonGlyph(button, held, glyph)
    }
}

/** A button's glyph, small and quiet, lit while that button is held. */
@Composable
private fun ButtonGlyph(button: PromptKey, held: Set<PromptKey>, size: Dp) {
    val glyph = promptGlyph(button)
    val down = button in held
    PromptImage(if (down) "${glyph}_filled" else glyph, size, Modifier.graphicsLayer { alpha = if (down) 1f else 0.62f })
}

/** An upward arrow: outlined while Shift is off, filled as it lights, with a lamp under it for caps lock. */
private fun DrawScope.drawShift(ink: Color, lit: Float, lamp: Float, ring: FocusRing) {
    val w = size.width
    val h = size.height
    val arrow = Path().apply {
        moveTo(w * 0.5f, h * 0.08f)
        lineTo(w * 0.94f, h * 0.52f)
        lineTo(w * 0.70f, h * 0.52f)
        lineTo(w * 0.70f, h * 0.76f)
        lineTo(w * 0.30f, h * 0.76f)
        lineTo(w * 0.30f, h * 0.52f)
        lineTo(w * 0.06f, h * 0.52f)
        close()
    }
    if (lit > 0f) drawPath(arrow, ink, alpha = lit)
    drawPath(arrow, ink, style = Stroke(width = h * 0.09f, join = StrokeJoin.Round))
    if (lamp > 0f) {
        val brush = Brush.horizontalGradient(listOf(ring.start, ring.end))
        drawRoundRect(
            brush = brush,
            topLeft = Offset(w * 0.02f, h * 0.80f),
            size = Size(w * 0.96f, h * 0.26f),
            cornerRadius = CornerRadius(h * 0.13f, h * 0.13f),
            alpha = 0.3f * lamp,
        )
        drawRoundRect(
            brush = brush,
            topLeft = Offset(w * 0.12f, h * 0.86f),
            size = Size(w * 0.76f, h * 0.14f),
            cornerRadius = CornerRadius(h * 0.07f, h * 0.07f),
            alpha = lamp,
        )
    }
}

/** A tag pointing left with a cross in it. */
private fun DrawScope.drawErase(ink: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = h * 0.09f, join = StrokeJoin.Round, cap = StrokeCap.Round)
    val tag = Path().apply {
        moveTo(w * 0.04f, h * 0.5f)
        lineTo(w * 0.32f, h * 0.18f)
        lineTo(w * 0.96f, h * 0.18f)
        lineTo(w * 0.96f, h * 0.82f)
        lineTo(w * 0.32f, h * 0.82f)
        close()
    }
    drawPath(tag, ink, style = stroke)
    val cx = w * 0.62f
    val cy = h * 0.5f
    val arm = h * 0.15f
    drawLine(ink, Offset(cx - arm, cy - arm), Offset(cx + arm, cy + arm), stroke.width, StrokeCap.Round)
    drawLine(ink, Offset(cx - arm, cy + arm), Offset(cx + arm, cy - arm), stroke.width, StrokeCap.Round)
}

/** The space bar's open bracket. */
private fun DrawScope.drawSpace(ink: Color) {
    val w = size.width
    val h = size.height
    val bracket = Path().apply {
        moveTo(w * 0.04f, h * 0.36f)
        lineTo(w * 0.04f, h * 0.66f)
        lineTo(w * 0.96f, h * 0.66f)
        lineTo(w * 0.96f, h * 0.36f)
    }
    drawPath(bracket, ink, style = Stroke(width = h * 0.09f, join = StrokeJoin.Round, cap = StrokeCap.Round))
}

/** Where [key] in [row] sits, in pixels, with the gap between keys taken off its edges. */
private fun keyRect(key: PlacedKey, row: Int, unit: Float, left: Float, top: Float): Rect {
    val gap = unit * KEY_GAP
    return Rect(
        Offset(left + key.x * unit + gap / 2f, top + row * unit * ROW_PITCH),
        Size(key.width * unit - gap, unit * (ROW_PITCH - KEY_GAP)),
    )
}

/** This rect grown about its centre by [by], to match a lifted key. */
private fun Rect.grown(by: Float): Rect {
    val dx = width * (by - 1f) / 2f
    val dy = height * (by - 1f) / 2f
    return Rect(left - dx, top - dy, right + dx, bottom + dy)
}

/** A highlight's rect that glides to each new [target], carrying its speed through held moves. */
private class GlidingRect(
    val left: Animatable<Float, AnimationVector1D>,
    val top: Animatable<Float, AnimationVector1D>,
    val width: Animatable<Float, AnimationVector1D>,
    val height: Animatable<Float, AnimationVector1D>,
    val placed: State<Boolean>,
) {
    fun rect(): Rect? = if (!placed.value) null else Rect(Offset(left.value, top.value), Size(width.value, height.value))
}

@Composable
private fun rememberGlidingRect(target: () -> Rect?, scale: Float): GlidingRect {
    val motion = LocalFoldTheme.current.theme.motion
    val left = remember { Animatable(0f) }
    val top = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    val height = remember { Animatable(0f) }
    val placed = remember { mutableStateOf(false) }
    val latestScale = rememberUpdatedState(scale)
    val next = remember { derivedStateOf(target) }
    LaunchedEffect(Unit) {
        withContext(SteadyMenu) {
            snapshotFlow { next.value }.collect { rect ->
                if (rect == null) return@collect
                if (!placed.value) {
                    left.snapTo(rect.left)
                    top.snapTo(rect.top)
                    width.snapTo(rect.width)
                    height.snapTo(rect.height)
                    placed.value = true
                    return@collect
                }
                val by = latestScale.value
                // A move while the last is still travelling glides on; a fresh one lands with a bounce.
                fun along(anim: Animatable<Float, AnimationVector1D>, to: Float) =
                    if (anim.isRunning) motion.glide(motion.durationSelect, by, to - anim.value, anim.velocity)
                    else motion.playful(motion.durationSelect, by)
                val size = motion.arrive(motion.durationSelect, by)
                launch { left.animateTo(rect.left, along(left, rect.left)) }
                launch { top.animateTo(rect.top, along(top, rect.top)) }
                launch { width.animateTo(rect.width, size) }
                launch { height.animateTo(rect.height, size) }
            }
        }
    }
    return remember { GlidingRect(left, top, width, height, placed) }
}

/** A tap that takes no focus and draws no ripple: the highlight already shows where it landed. */
@Composable
private fun Modifier.tap(onTap: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return focusProperties { canFocus = false }.clickable(interactionSource = source, indication = null, onClick = onTap)
}

// The keyboard's proportions, in key widths. A key is a little taller than it is wide.
private const val ROW_PITCH = 1.12f
private const val KEY_GAP = 0.14f
private const val KEY_CORNER = 0.2f
private const val KEY_LABEL = 0.44f
private const val KEY_WORD = 0.36f
private const val KEY_GLYPH = 0.46f
private const val KEY_ALPHA = 0.92f
private const val KEY_SQUASH = 0.07f
private const val DONE_ALPHA = 0.55f
private const val SHIFT_ALPHA = 0.38f
private const val CHIP_HEIGHT = 0.84f
private const val CHIP_GAP = 0.42f
private const val CHIP_SPACING = 0.2f
private const val CHIP_PAD = 0.42f
private const val CHIP_LABEL = 0.34f
private const val CHIP_FADE = 0.9f
private const val CHIP_PEEK = 1.4f
private const val FIELD_HEIGHT = 1.5f
private const val FIELD_GAP = 0.3f

/** The smallest a long name shrinks to in the top field, as a share of its size. */
private const val TYPED_FLOOR = 0.7f

/** Room kept beside the caret when a long name scrolls, and the fade at a scrolled edge, in dp. */
private const val CARET_ROOM = 72f
private const val FIELD_FADE = 28f
private const val CARET_END = 12f

/** How far down the caret and a selection breathe between beats. */
private const val CARET_DIM = 0.35f
private const val SHAKE_SWINGS = 3f
private const val SHAKE_REACH = 12f
