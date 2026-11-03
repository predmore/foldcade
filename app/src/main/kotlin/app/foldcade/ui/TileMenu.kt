package app.foldcade.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.foldcade.FoldcadeApp
import app.foldcade.language.HostScreen
import app.foldcade.language.Side
import app.foldcade.language.SidePanel
import app.foldcade.language.TileMenuSize
import app.foldcade.language.SelectionCopy
import app.foldcade.language.actionFaces
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

/**
 * Where the focused tile is drawn in one window, so Y's menu can grow out of it and shrink back
 * into it. The grid's and Find's focused tile place themselves here ([tileAnchor]); with neither
 * drawn on the screen there is no tile.
 */
internal class TileAnchor {
    private var coordinates: LayoutCoordinates? = null
    private var owner: Any? = null

    /** Its corners' radius, in px, so the card starts with the tile's own shape. */
    var cornerPx = 0f
        private set

    /** Bumped when a tile takes or gives up the anchor, so a card waiting for one looks again. */
    var revision by mutableIntStateOf(0)
        private set

    /**
     * How far, in px, the grid slides aside while the card is open, so the card fits beside a tile
     * in a middle column of a narrow screen. Negative is left. The grid eases there as the card grows.
     */
    var slidePx by mutableFloatStateOf(0f)

    /** How far the grid has slid now, in px, so a card opening while it slides back reads the tile at rest. */
    var slid: () -> Float = { 0f }

    /** The focused tile's bounds in the window, in px, as drawn now, or null. */
    fun bounds(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInRoot()?.takeIf { it.width > 0f && it.height > 0f }

    fun place(owner: Any, coordinates: LayoutCoordinates, cornerPx: Float) {
        val fresh = this.owner !== owner
        this.owner = owner
        this.coordinates = coordinates
        this.cornerPx = cornerPx
        if (fresh) revision++
    }

    fun clear(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        coordinates = null
        revision++
    }
}

/** This window's [TileAnchor]. Null outside a panel. */
internal val LocalTileAnchor = staticCompositionLocalOf<TileAnchor?> { null }

/** The focused tile, placing itself in this window's [TileAnchor]. */
@Composable
internal fun Modifier.tileAnchor(cornerPx: Float): Modifier {
    val anchor = LocalTileAnchor.current ?: return this
    val owner = remember { Any() }
    DisposableEffect(anchor, owner) { onDispose { anchor.clear(owner) } }
    return onPlaced { anchor.place(owner, it, cornerPx) }
}

/** True while Y's menu is open on [screen]: the grid there keeps its ring on the tile and dims around it. */
internal fun tileMenuOn(app: FoldcadeApp, screen: HostScreen): Boolean {
    val model = app.shell.model
    return model.panel?.let { it.side == Side.Tile && it.screen == screen } == true && model.dialog?.screen != screen
}

/**
 * Y's menu: a card that grows out of the focused tile and comes to rest beside it, right of it
 * when there is room, otherwise left, and as high as the tile while it fits above the grid's hint
 * row. The tile and its ring stay lit as the grid dims around them. B or Y shrinks it back in.
 * With Motion Off, or with Android's animations removed, it fades in place instead.
 *
 * Over a screen with no grid, as when a game holds the grid's screen and Foldcade shows its
 * command center here, there is no tile to grow from: the card fades up in the middle.
 */
@Composable
internal fun TileMenuLayer(
    app: FoldcadeApp,
    screen: HostScreen,
    scale: Float,
    menuRows: @Composable (panel: SidePanel, progress: () -> Float, interactive: Boolean) -> Unit,
) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val motion = theme.motion
    val model = app.shell.model
    val live = model.panel?.takeIf { it.side == Side.Tile && it.screen == screen }
    // A dialog on this screen, such as Restart's question, takes the card's place until it closes.
    val open = live != null && model.dialog?.screen != screen
    var kept by remember { mutableStateOf<SidePanel?>(null) }
    if (live != null) kept = live
    // Where the tile rested when the card opened, in this layer's dp, before the grid slid aside for it.
    // Read again each time the card opens, as when Y comes back on another tile while one still shrinks.
    var rest by remember { mutableStateOf<Rect?>(null) }
    var wasOpen by remember { mutableStateOf(false) }
    if (open != wasOpen) {
        if (open) rest = null
        wasOpen = open
    }
    val grows = motion.tileMenuGrows(model.motionSpeed, scale)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(open, grows) {
        val target = if (open) 1f else 0f
        if (progress.value != target) {
            withContext(SteadyMenu) { progress.animateTo(target, motion.tileMenu(open, model.motionSpeed, scale)) }
        }
        if (!open && app.shell.model.panel?.let { it.side == Side.Tile && it.screen == screen } != true) {
            kept = null
            rest = null
        }
    }
    val shownPanel = kept ?: return
    val visible by remember { derivedStateOf { progress.value > 0f } }
    if (!visible && !open) return
    val anchor = LocalTileAnchor.current
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .focusProperties { canFocus = false },
    ) {
        // Where this layer sits in the window, so the tile's bounds can be read in its own terms.
        val origin = remember { floatArrayOf(0f, 0f) }
        Box(Modifier.fillMaxSize().onGloballyPositioned { val at = it.positionInRoot(); origin[0] = at.x; origin[1] = at.y })
        // A tap off the card closes it, as B would. The card keeps its own taps.
        if (open) Box(Modifier.fillMaxSize().closesIsland(app))
        val width = minOf(TileMenuSize.widthDp.dp, maxWidth - TileMenuSize.marginDp.dp * 2)
        val inset = maxWidth * design.metrics.insetFraction
        val fontScale = density.fontScale
        // The card stops above the grid's hint row, which names the menu's keys.
        val bottom = maxHeight - inset - maxOf(TileMenuSize.hintLineDp.dp, (TileMenuSize.tileLabelLineDp * fontScale).dp) -
            TileMenuSize.hintGapDp.dp
        val top = TileMenuSize.marginDp.dp
        val room = (bottom - top).coerceAtLeast(0.dp)
        val reach = ringReach()
        val frame = (reach + TileMenuSize.padDp.dp) * 2
        var rowsHeight by remember { mutableStateOf(Dp.Hairline) }
        val fitted = if (rowsHeight > Dp.Hairline) minOf(room, rowsHeight + frame) else room
        // The first measured height lands at once; Add to's rows, or a row coming or going, ease to the next.
        var settled by remember { mutableStateOf(false) }
        val height = motionState(fitted.value, motion.playful(motion.durationSelect, scale), snap = !settled).value.dp
        if (rowsHeight > Dp.Hairline) androidx.compose.runtime.SideEffect { settled = true }
        // Where the tile is, in this layer's dp, read once as the card opens: the grid may slide after.
        anchor?.revision
        if (rest == null && open) {
            rest = anchor?.bounds()?.let { bounds ->
                // Less however far the grid has still slid, so this is the tile's own place.
                val left = bounds.left - origin[0] - anchor.slid()
                with(density) {
                    Rect(
                        left.toDp().value, (bounds.top - origin[1]).toDp().value,
                        (left + bounds.width).toDp().value, (bounds.bottom - origin[1]).toDp().value,
                    )
                }
            }
        }
        val placed = cardPlace(rest, width.value, height.value, maxWidth.value, top.value, bottom.value)
        val card = placed.card
        val slidePx = with(density) { placed.slideDp.dp.toPx() }
        androidx.compose.runtime.SideEffect { anchor?.slidePx = if (open) slidePx else 0f }
        val tile = rest
        // With no tile to grow from, the card grows from a little inside its own place.
        val slideDp = placed.slideDp
        val from = tile ?: card.deflate(minOf(card.width, card.height) * NO_TILE_INSET)
        val tileCorner = with(density) { (anchor?.cornerPx ?: 0f).toDp().value }.takeIf { tile != null } ?: design.metrics.cardCornerDp
        val latestFrom by rememberUpdatedState(from)
        val latestCard by rememberUpdatedState(card)
        val latestCorner by rememberUpdatedState(tileCorner)
        // A tile with no tile behind it never slides.
        val latestSlide by rememberUpdatedState(if (tile != null) slideDp else 0f)
        val grow = { t: Float -> if (grows) t.coerceIn(0f, 1f) else 1f }
        // The rows come in once the card is most of the way out, and leave first as it shrinks.
        val rowsIn = { ((progress.value - ROWS_START) / (1f - ROWS_START)).coerceIn(0f, 1f).let { if (grows) it else progress.value } }
        val ready by remember { derivedStateOf { progress.value > 0.92f } }
        val interactive = open && ready
        val scroll = rememberScrollState()
        var viewport by remember { mutableIntStateOf(0) }
        val rim = remember(theme) { FocusRing(theme) }
        Box(
            Modifier
                .layout { measurable, _ ->
                    val t = grow(progress.value)
                    val f = latestFrom.translate(latestSlide * t, 0f)
                    val c = latestCard
                    val x = lerp(f.left, c.left, t).dp.roundToPx()
                    val y = lerp(f.top, c.top, t).dp.roundToPx()
                    val w = lerp(f.width, c.width, t).dp.roundToPx().coerceAtLeast(1)
                    val h = lerp(f.height, c.height, t).dp.roundToPx().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(placeable.width, placeable.height) { placeable.place(x, y) }
                }
                .graphicsLayer {
                    // The card's fill comes up over the tile as it leaves it, so the tile is seen to open.
                    alpha = if (grows) (progress.value / FILL_IN).coerceIn(0f, 1f) else progress.value
                    val t = grow(progress.value)
                    shape = RoundedCornerShape(lerp(latestCorner, design.metrics.cardCornerDp, t).dp)
                    clip = true
                }
                .background(cardFill(theme, design.menu.islandLift))
                // An island's faint rim, lit with the focus colours, on the corners the card has now.
                .drawWithContent {
                    drawContent()
                    val stroke = design.menu.islandBorderDp.dp.toPx()
                    val corner = lerp(latestCorner, design.metrics.cardCornerDp, grow(progress.value)).dp.toPx()
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(
                                rim.start.copy(alpha = design.menu.islandRimAlpha + design.menu.islandRimLitAlpha),
                                theme.onBackground.copy(alpha = design.menu.islandRimAlpha * RIM_MIDDLE),
                                rim.end.copy(alpha = design.menu.islandRimAlpha + design.menu.islandRimLitAlpha * RIM_END),
                            ),
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(stroke),
                    )
                }
                .blocksTouch(),
        ) {
            // The rows are laid out at the card's resting size and place from the first frame. The
            // growing card reveals them where they will stay, so nothing slides or rewraps inside it.
            Box(
                Modifier.layout { measurable, constraints ->
                    val t = grow(progress.value)
                    val f = latestFrom.translate(latestSlide * t, 0f)
                    val c = latestCard
                    val placeable = measurable.measure(Constraints.fixed(c.width.dp.roundToPx(), c.height.dp.roundToPx()))
                    val dx = (c.left - lerp(f.left, c.left, t)).dp.roundToPx()
                    val dy = (c.top - lerp(f.top, c.top, t)).dp.roundToPx()
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(dx, dy) }
                },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(TileMenuSize.padDp.dp)
                        .onSizeChanged { viewport = it.height }
                        .menuFade(scroll)
                        .verticalScroll(scroll)
                        .padding(reach),
                ) {
                    // The tile says what the menu acts on. Only what it cannot show heads the card: how many
                    // are selected, or what a gap a restore left is waiting for.
                    val heading = model.tileActions?.bulk?.let { SelectionCopy.count(it.size) } ?: app.shell.restoredGapTitle()
                    var headingHeight by remember { mutableStateOf(Dp.Hairline) }
                    Column(Modifier.fillMaxWidth().onSizeChanged { rowsHeight = with(density) { it.height.toDp() } }) {
                        if (heading != null) {
                            TileMenuHeading(app, heading, Modifier.onSizeChanged { headingHeight = with(density) { it.height.toDp() } }.graphicsLayer { alpha = rowsIn() })
                        }
                        SlideSwap(
                            target = shownPanel,
                            scale = scale,
                            distance = 40.dp,
                            vertical = false,
                            direction = { from, to -> to.level.depth.compareTo(from.level.depth) },
                            front = Modifier.fillMaxWidth(),
                            key = { it.level },
                        ) { panel ->
                            // Rows on their way out take no taps: their indices belong to the old level.
                            val arriving = panel.level == shownPanel.level
                            val rowsRoom = room - frame - if (heading != null) headingHeight else Dp.Hairline
                            CompositionLocalProvider(LocalIslandScroll provides IslandScroll(rowsRoom, scroll, viewport)) {
                                menuRows(panel, rowsIn, interactive && arriving)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Y and what the card acts on, when the tile cannot say it. */
@Composable
private fun TileMenuHeading(app: FoldcadeApp, text: String, modifier: Modifier) {
    val theme = LocalFoldTheme.current.theme
    Row(
        modifier.fillMaxWidth().height(HEADING_DP.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PromptImage(promptGlyph(actionFaces(app.shell.model.faceMap).optionsGlyph), 22.dp)
        BasicText(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = theme.onBackground, fontSize = theme.style.type.sideRow, fontFamily = theme.font, fontWeight = theme.style.type.strongWeight),
        )
    }
}

/** The heading's line, a little shorter than a row. */
private const val HEADING_DP = 40f

/** Where the card rests, and how far the grid slides aside for it, in dp. */
internal data class CardPlace(val card: Rect, val slideDp: Float)

/**
 * Where the card rests beside [tile], in dp: right of it when it fits, else left. On a screen too
 * narrow for either, as the Thor's bottom one is for its middle columns, the grid slides the
 * shorter way until the card fits beside the tile, so the card never covers the tile it acts on.
 * Its top meets the tile's, unless that would push it past [bottom]. With no tile it sits in the middle.
 */
internal fun cardPlace(tile: Rect?, width: Float, height: Float, screenWidth: Float, top: Float, bottom: Float): CardPlace {
    val margin = TileMenuSize.marginDp
    val beside = TileMenuSize.besideDp
    if (tile == null) {
        val x = (screenWidth - width) / 2f
        val y = top + ((bottom - top - height) / 2f).coerceAtLeast(0f)
        return CardPlace(Rect(x, y, x + width, y + height), 0f)
    }
    val right = tile.right + beside
    val left = tile.left - beside - width
    val edge = screenWidth - margin
    val (x, slide) = when {
        right + width <= edge -> right to 0f
        left >= margin -> left to 0f
        // Sliding left by this much puts the card at the right edge; sliding right puts it at the left.
        right + width - edge <= margin - left -> (edge - width) to -(right + width - edge)
        else -> margin to (margin - left)
    }
    val y = tile.top.coerceIn(top, (bottom - height).coerceAtLeast(top))
    return CardPlace(Rect(x, y, x + width, y + height), slide)
}

private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

/** The share of the grow over which the card's fill comes up. */
private const val FILL_IN = 0.35f

/** Where in the grow the rows start to come in. */
private const val ROWS_START = 0.55f

/** With no tile, how far inside its own place the card starts, as a share of its shorter side. */
private const val NO_TILE_INSET = 0.04f

/** The card's rim, at its middle and at its end, as shares of the theme's rim at its start. As an island's. */
private const val RIM_MIDDLE = 0.06f / 0.08f
private const val RIM_END = 0.26f / 0.34f
