package app.foldcade.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.R
import app.foldcade.language.Copy
import app.foldcade.language.Effect
import app.foldcade.language.HostScreen
import app.foldcade.language.PickerModel
import app.foldcade.language.PromptKey
import app.foldcade.language.Row
import app.foldcade.language.SettingsCategory
import app.foldcade.language.SettingsCopy
import app.foldcade.language.SettingsInner
import app.foldcade.language.SettingsPage
import app.foldcade.language.SettingsShelf
import app.foldcade.language.Theme
import app.foldcade.language.TileStatus
import app.foldcade.language.destructive
import app.foldcade.language.emulatorShelf
import app.foldcade.language.menuFit
import app.foldcade.language.promptKeyOf
import app.foldcade.language.rowControl
import app.foldcade.language.rowText
import app.foldcade.language.settingsCategories
import app.foldcade.language.settingsInnerDetail
import app.foldcade.language.settingsInnerRows
import app.foldcade.language.settingsInnerTitle
import app.foldcade.language.settingsRows
import app.foldcade.language.settingsShown
import app.foldcade.language.shownShelfFirst
import app.foldcade.language.settingsTile
import app.foldcade.language.settingsTileStatus
import app.foldcade.language.settingsTitle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Full-screen settings, laid out like Cocoon's: the header over the categories on the left,
 * and the focused category's card on the right, as tall as the page. It replaces the hero
 * while it is open. Each side has one highlight that glides between its rows. The side the
 * D-pad is in wears the focus ring; the other keeps a soft fill on its choice.
 */
@Composable
internal fun SettingsScreen(app: FoldcadeApp, screen: HostScreen, page: SettingsPage, scale: Float, onEffect: (Effect?) -> Unit) {
    val design = LocalFoldTheme.current.theme.style
    val model = app.shell.model
    val theme = LocalFoldTheme.current.theme
    val categories = settingsCategories(model)
    if (categories.isEmpty()) return
    val categoryIndex = page.category.coerceIn(0, categories.lastIndex)
    // The bottom screen is a third narrower. Settings opens there only when a game holds the
    // top, and then the categories are their art alone and the header its button, so the
    // card keeps the width its rows need.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < COMPACT_BELOW
        Row(
            Modifier
                .fillMaxSize()
                .background(theme.background)
                .padding(horizontal = if (compact) 20.dp else SETTINGS_PAGE_H, vertical = SETTINGS_PAGE_V),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp),
        ) {
            // The categories scroll inside the ring's reach on each side, so the art's column adds it.
            Column((if (compact) Modifier.width(COMPACT_COLUMN + ringReach() * 2) else Modifier.weight(0.32f)).fillMaxHeight()) {
                val back = if (model.capturingConfirm) PromptKey.Start else promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB
                Header(theme, back, titled = !compact) { onEffect(app.shell.backSettings(screen)) }
                val master = rememberSelectionSlots()
                // All six fit at rest on the Thor's top screen. Should they not, as at a large font
                // size, the edge with more beyond it fades, so a hidden category still shows.
                val categoryScroll = rememberScrollState()
                Column(
                    Modifier
                        .padding(top = 20.dp)
                        .fillMaxSize()
                        .menuFade(categoryScroll)
                        .ringScroll(state = categoryScroll)
                        .selectionHighlight(master, categoryIndex, ring = !page.onRows, scale = scale),
                    verticalArrangement = Arrangement.spacedBy(ROW_GAP),
                ) {
                    categories.forEachIndexed { index, category ->
                        val selected = index == categoryIndex
                        CategoryRow(
                            title = settingsTitle(category),
                            icon = categoryIcon(category),
                            labelled = !compact,
                            selected = selected,
                            focused = selected && !page.onRows,
                            scale = scale,
                            modifier = Modifier.selectionSlot(master, index),
                            onClick = { onEffect(app.shell.touchSettingsCategory(index, screen)) },
                        )
                    }
                }
            }
            Box(
                Modifier
                    .weight(0.68f)
                    .fillMaxHeight()
                    .settingsCard(theme),
            ) {
                // A new category's card rises in from the way the focus went.
                SlideSwap(
                    target = categories[categoryIndex],
                    scale = scale,
                    distance = 36.dp,
                    vertical = true,
                    direction = { from, to -> to.ordinal.compareTo(from.ordinal) },
                ) { category ->
                    CategoryCard(app, screen, page, category, category == categories[categoryIndex], scale, onEffect)
                }
            }
        }
    }
}

/**
 * A category's card: its shelf of tiles and its rows, or the page one of its tiles opened.
 * A page slides in from the right as a menu level does, and slides back out on the way to its tile.
 */
@Composable
private fun CategoryCard(
    app: FoldcadeApp,
    screen: HostScreen,
    page: SettingsPage,
    category: SettingsCategory,
    current: Boolean,
    scale: Float,
    onEffect: (Effect?) -> Unit,
) {
    val model = app.shell.model
    val inner = if (current) settingsShown(page, model).first.inner else null
    SlideSwap(
        target = inner,
        scale = scale,
        distance = 40.dp,
        vertical = false,
        direction = { from, to -> (if (to != null) 1 else 0).compareTo(if (from != null) 1 else 0) },
    ) { shownInner ->
        val live = current && shownInner == inner
        val rows = shownInner?.let { settingsInnerRows(it, model) } ?: settingsRows(category, model)
        CardRows(
            app = app,
            screen = screen,
            rows = rows,
            inner = shownInner,
            fitRows = category == SettingsCategory.Screens && shownInner == null,
            focus = page.row.takeIf { live && page.onRows && it in rows.indices },
            // A card on its way out keeps the strip where it was.
            shelfFirst = if (live) shownShelfFirst(page, model) else 0,
            // The card on its way out takes no taps: its rows are not the page's now.
            interactive = live,
            hint = live && category == SettingsCategory.Personalization && model.capturingConfirm,
            scale = scale,
            onEffect = onEffect,
        )
    }
}

@Composable
private fun CardRows(
    app: FoldcadeApp,
    screen: HostScreen,
    rows: List<Row>,
    inner: SettingsInner?,
    fitRows: Boolean,
    focus: Int?,
    shelfFirst: Int,
    interactive: Boolean,
    hint: Boolean,
    scale: Float,
    onEffect: (Effect?) -> Unit,
) {
    val model = app.shell.model
    val theme = LocalFoldTheme.current.theme
    val slots = rememberSelectionSlots()
    val iconSlot = rows.any { !it.settingsTile() && settingsRowIcon(it) != null }
    val labelStyle = style(theme.onBackground, theme.style.type.menuRow, theme, bold = true)
    val tiles = rows.takeWhile { it.settingsTile() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // System contracts row padding like the island menus, keeping Layout, Play on and
        // every pending Android permission visible without consuming the focus ring's room.
        val fit = if (fitRows) with(density) {
            menuFit(rows.size, constraints.maxHeight - (ringReach() + CARD_EXTRA).roundToPx() * 2,
                density.density, ROW_GAP.roundToPx())
        } else null
        // A card should fit at rest. One that cannot, as Systems with many systems to choose,
        // fades at the edge with more beyond it, as the categories do, rather than cut a row off.
        val cardScroll = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .menuFade(cardScroll)
                .ringScroll(extra = CARD_EXTRA, state = cardScroll)
                .selectionHighlight(slots, focus, ring = true, scale = scale),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP),
        ) {
            if (inner != null) InnerHeader(model, inner)
            // The tiles are one strip, a child of this column, which tells the one highlight where each rests.
            if (tiles.isNotEmpty()) {
                ShelfStrip(
                    tiles = tiles,
                    model = model,
                    focus = focus?.takeIf { it < tiles.size },
                    first = shelfFirst,
                    slots = slots,
                    interactive = interactive,
                    scale = scale,
                    onTap = { index -> onEffect(app.shell.touchSettingsRow(index, screen)) },
                    onScroll = { app.shell.scrollSettingsShelf(it, screen) },
                    modifier = Modifier.padding(bottom = if (SettingsShelf.scrolls(tiles.size)) 0.dp else TILE_BLOCK_GAP),
                )
                if (SettingsShelf.scrolls(tiles.size)) ShelfBar(tiles.size, shelfFirst, scale)
            }
            rows.forEachIndexed { index, row ->
                if (index < tiles.size) return@forEachIndexed
                val focused = focus == index
                val parts = rowText(row, model)
                val place = Modifier
                    .fillMaxWidth()
                    .selectionSlot(slots, index)
                    .keepInView(focused)
                val tap = { onEffect(app.shell.touchSettingsRow(index, screen)) }
                if (row == Row.MusicVolume) {
                    SliderRow(
                        label = parts.label,
                        value = parts.value.orEmpty(),
                        fraction = model.music.volume,
                        focused = focused,
                        interactive = interactive,
                        onStep = tap,
                        onFraction = app.shell::setMusicVolume,
                        modifier = place,
                        scale = scale,
                        labelStyle = labelStyle,
                        icon = settingsRowIcon(row),
                    )
                } else {
                    MenuRow(
                        label = parts.label,
                        icon = settingsRowIcon(row),
                        iconSlot = iconSlot,
                        focused = focused,
                        control = rowControl(row, model, parts.value),
                        chord = null,
                        held = model.held,
                        scale = scale,
                        // Remove fills while held, and shakes when let go early, as a download's does.
                        modifier = (if (!interactive) place else if (row.destructive()) place.holdPress(app, index, screen) else place.press(tap))
                            .then(holdFeedback(model, row, scale)),
                        labelStyle = labelStyle,
                        height = fit?.rowHeights?.getOrNull(index)?.let { with(density) { it.toDp() } } ?: ROW_HEIGHT,
                    )
                }
            }
            // Calibrating Confirm: what to press, under the row that asked.
            val help = presence(Copy.buttonCalibrationHelp.takeIf { hint }, theme.motion.durationFocus, scale)
            help.shown?.let {
                BasicText(
                    it,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).rise { help.progress.value },
                    style = style(theme.muted, theme.style.type.hint, theme),
                )
            }
        }
    }
}

/** A page's head: what it is about, its art larger, and its name. Not a row: the D-pad passes it by. */
@Composable
private fun InnerHeader(model: PickerModel, inner: SettingsInner) {
    val theme = LocalFoldTheme.current.theme
    val off = inner is SettingsInner.Host && model.streamHosts.none { it.key == inner.key && it.paired }
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(HEADER_ART), contentAlignment = Alignment.Center) {
            TileArt(innerArt(inner, model), lit = 1f, off = off)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                settingsInnerTitle(inner, model),
                style = style(theme.onBackground, theme.style.type.dialogTitle, theme, bold = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // What the page is about, in a word or two each: a folder's path, its games, its art and info.
            settingsInnerDetail(inner, model)?.let {
                BasicText(it, style = style(theme.muted, theme.style.type.hint, theme), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * One of a shelf's tiles: its art on a soft square, its name under it, and a badge when it
 * has one. The card's highlight lands on the square; the name brightens with it.
 */
@Composable
private fun ShelfTile(
    row: Row,
    model: PickerModel,
    focused: Boolean,
    scale: Float,
    modifier: Modifier,
) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val lit = eased(if (focused) 1f else 0f, theme.motion.durationFocus, scale)
    val lift = bouncy(if (focused) 1f else 0f, scale)
    val label = rowText(row, model).label
    val inner = (row as? Row.SettingsTile)?.page
    val off = inner is SettingsInner.Host && model.streamHosts.none { it.key == inner.key && it.paired }
    val dim = if (off) design.settings.tileOffAlpha else 1f
    val corner = RoundedCornerShape(design.shape.highlightCornerDp.dp)
    Column(
        modifier.semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TILE_LABEL_GAP),
    ) {
        Box(Modifier.size(TILE_ART), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(corner)
                    .background(theme.onBackground.copy(alpha = design.menu.quickTileAlpha)),
            )
            Box(
                Modifier.fillMaxSize().lifted(lit, lift, grow = design.settings.artGrow, rest = design.settings.artRestAlpha),
                contentAlignment = Alignment.Center,
            ) {
                // RomM's tile is its connection, which wears the cloud as its row did.
                TileArt(inner?.let { innerArt(it, model) } ?: ShelfArt.Mark("cloud"), lit = 1f, off = off)
            }
            settingsTileStatus(row, model)?.let { status -> TileBadge(status, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)) }
        }
        BasicText(
            label,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).graphicsLayer {
                alpha = dim * design.settings.labelRestAlpha.let { it + (1f - it) * lit.value }
            },
            style = style(theme.onBackground, design.type.hint, theme, bold = true).copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A category's tiles as one strip, as the grid is ([SettingsShelf]): columns of one or two
 * tiles, four on screen. The strip glides a column at a time to follow the focus and runs
 * out to the card's own edge, fading there while more is beyond it. A tile that changes
 * place, as one whose saves start syncing leaves the front, travels to its new one.
 *
 * The card's one highlight lands where the focused tile's square rests once the strip
 * has, so the highlight and the strip arrive together rather than the highlight chasing.
 */
@Composable
private fun ShelfStrip(
    tiles: List<Row>,
    model: PickerModel,
    focus: Int?,
    first: Int,
    slots: SelectionSlots,
    interactive: Boolean,
    scale: Float,
    onTap: (Int) -> Unit,
    onScroll: (Int) -> Unit,
    modifier: Modifier,
) {
    val motion = LocalFoldTheme.current.theme.motion
    val density = LocalDensity.current
    val count = tiles.size
    val lines = SettingsShelf.lines(count)
    val last = SettingsShelf.lastFirst(count)
    val scrolls = SettingsShelf.scrolls(count)
    val reach = ringReach()
    val reachPx = with(density) { reach.toPx() }
    val roomPx = with(density) { BADGE_ROOM.toPx() }
    val gapPx = with(density) { SHELF_LINE_GAP.toPx() }
    val artPx = with(density) { TILE_ART.toPx() }
    val fadePx = with(density) { (reach + SHELF_FADE).toPx() }
    val anim = remember { Animatable(first.toFloat()) }
    val shift by glideState(first.toFloat(), motion.durationTravel, scale, anim = anim)
    // The strip's place in the card's column, which the highlight draws in.
    var frame by remember { mutableStateOf<Rect?>(null) }
    // Each tile's column and line, gliding when the tiles reorder, read only while placing.
    val places = tiles.mapIndexed { index, row ->
        key(row) {
            eased(SettingsShelf.column(index, count).toFloat(), motion.durationTravel, scale) to
                eased(SettingsShelf.line(index, count).toFloat(), motion.durationTravel, scale)
        }
    }
    frame?.let { strip ->
        val column = strip.width / SettingsShelf.shown
        val tile = (strip.height - roomPx - gapPx * (lines - 1)) / lines
        SideEffect {
            for (index in 0 until count) {
                val at = SettingsShelf.column(index, count)
                if (at !in first until first + SettingsShelf.shown) {
                    slots.rows.remove(index)
                    continue
                }
                val rect = Rect(
                    Offset(strip.left + (at - first) * column + (column - artPx) / 2f, strip.top + roomPx + SettingsShelf.line(index, count) * (tile + gapPx)),
                    Size(artPx, artPx),
                )
                if (slots.round[index] == true) slots.round[index] = false
                if (slots.rows[index] != rect) slots.rows[index] = rect
            }
        }
    }
    val swipes = rememberCoroutineScope()
    val drag = rememberDraggableState { delta ->
        val column = (frame?.width ?: 0f) / SettingsShelf.shown
        if (column > 0f) swipes.launch { anim.snapTo((anim.value - delta / column).coerceIn(-SHELF_GIVE, last + SHELF_GIVE)) }
    }
    Box(
        modifier
            .fillMaxWidth()
            .keepInView(focus != null)
            .onGloballyPositioned { coordinates ->
                val column = coordinates.parentLayoutCoordinates ?: return@onGloballyPositioned
                val rect = column.localBoundingBoxOf(coordinates, clipBounds = false)
                if (frame != rect) frame = rect
            }
            // Out over the card's margin on each side, so tiles slide off at the card's edge.
            .layout { measurable, constraints ->
                val wide = constraints.maxWidth + reachPx.roundToInt() * 2
                val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-reachPx.roundToInt(), 0) }
            }
            .then(if (scrolls) Modifier.shelfFade({ shift }, last, fadePx) else Modifier)
            .clipToBounds()
            .draggable(
                state = drag,
                orientation = Orientation.Horizontal,
                enabled = interactive && scrolls,
                onDragStopped = { velocity ->
                    val column = (frame?.width ?: 0f) / SettingsShelf.shown
                    if (column <= 0f) return@draggable
                    val target = (anim.value - velocity / column * SHELF_FLICK_SECONDS).roundToInt().coerceIn(0, last)
                    onScroll(target)
                    anim.animateTo(target.toFloat(), motion.glide(motion.durationTravel, scale, target - anim.value, anim.velocity))
                },
            ),
    ) {
        Layout(
            content = {
                tiles.forEachIndexed { index, row ->
                    key(row) {
                        ShelfTile(
                            row = row,
                            model = model,
                            focused = focus == index,
                            scale = scale,
                            modifier = if (interactive) Modifier.press { onTap(index) } else Modifier,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val column = (constraints.maxWidth - reachPx * 2) / SettingsShelf.shown
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(column.roundToInt().coerceAtLeast(0))) }
            val tile = placeables.maxOfOrNull { it.height } ?: 0
            val height = (roomPx + tile * lines + gapPx * (lines - 1)).roundToInt()
            layout(constraints.maxWidth, height) {
                placeables.forEachIndexed { index, placeable ->
                    val (at, line) = places.getOrNull(index) ?: return@forEachIndexed
                    placeable.place(
                        (reachPx + (at.value - shift) * column).roundToInt(),
                        (roomPx + line.value * (tile + gapPx)).roundToInt(),
                    )
                }
            }
        }
    }
}

/** Fades the strip's edge while a column is beyond it, in as the strip leaves its end. */
private fun Modifier.shelfFade(shift: () -> Float, last: Int, band: Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        if (size.width <= 0f) return@drawWithContent
        val edge = (band / size.width).coerceAtMost(0.5f)
        // A mask: only its alpha counts.
        val solid = androidx.compose.ui.graphics.Color.Black
        val clear = androidx.compose.ui.graphics.Color.Transparent
        val before = shift().coerceIn(0f, 1f)
        val after = (last - shift()).coerceIn(0f, 1f)
        drawRect(
            Brush.horizontalGradient(
                0f to lerp(solid, clear, before),
                edge to solid,
                1f - edge to solid,
                1f to lerp(solid, clear, after),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Under a strip with tiles off screen: where the screen sits along it, as the grid's bar does. */
@Composable
private fun ShelfBar(count: Int, first: Int, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val start by glideState(first.toFloat(), theme.motion.durationTravel, scale)
    val columns = SettingsShelf.columns(count).toFloat()
    val density = LocalDensity.current
    val track = with(density) { SHELF_BAR_PX.toDp() }
    val thumb = track * (SettingsShelf.shown / columns)
    Box(Modifier.fillMaxWidth().padding(bottom = TILE_BLOCK_GAP), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(track)
                .height(with(density) { theme.style.tile.stripPx.toDp() })
                .background(theme.muted.copy(alpha = theme.style.tile.stripAlpha), theme.style.shape.control()),
        ) {
            Box(
                Modifier
                    .graphicsLayer { translationX = (track * (start / columns)).toPx() }
                    .width(thumb)
                    .fillMaxHeight()
                    .background(theme.onBackground, theme.style.shape.control()),
            )
        }
    }
}

/** The check while everything is fine, or the not-syncing cloud in the attention colour, on a dark disc. */
@Composable
private fun TileBadge(status: TileStatus, modifier: Modifier) {
    val theme = LocalFoldTheme.current.theme
    val (icon, tint) = when (status) {
        TileStatus.Fine -> R.drawable.ic_badge_check to theme.focus
        TileStatus.Attention -> R.drawable.ic_badge_unsynced to theme.style.colors.attention
    }
    val badge = themeIcon(icon)
    Box(
        modifier
            .size(BADGE_SIZE)
            .clip(theme.style.shape.control())
            .background(theme.background.copy(alpha = theme.style.tile.statusBackingAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painter = badge.painter, contentDescription = null, colorFilter = badge.tint(tint), modifier = Modifier.size(BADGE_SIZE - 6.dp))
    }
}

/** What a tile or a page's head wears. */
private sealed interface ShelfArt {
    /** A place's lit object, by its `mark.` slot. */
    data class Mark(val mark: String) : ShelfArt

    /** An installed app's object, or its launcher icon when no pack draws one. */
    data class App(val packageName: String?) : ShelfArt

    /** A one-ink control glyph, by its slot. */
    data class Control(val slot: String) : ShelfArt
}

private fun innerArt(inner: SettingsInner, model: PickerModel): ShelfArt = when (inner) {
    is SettingsInner.Emulator -> ShelfArt.App(emulatorShelf(model).firstOrNull { it.key == inner.key }?.packageName)
    is SettingsInner.Folder -> ShelfArt.Mark("folder")
    is SettingsInner.Host -> ShelfArt.Mark("moonlight")
    is SettingsInner.Backend -> ShelfArt.Mark("cloud")
    SettingsInner.Systems -> ShelfArt.Mark("emulators")
    SettingsInner.Add -> ShelfArt.Control("control.add")
}

/** [off] dims it, as a computer Foldcade is not paired with. [lit] is for art that fades in with focus. */
@Composable
private fun TileArt(art: ShelfArt, lit: Float, off: Boolean) {
    val theme = LocalFoldTheme.current.theme
    val alpha = lit * if (off) theme.style.settings.tileOffAlpha else 1f
    when (art) {
        is ShelfArt.Mark -> MarkIcon(markGlyph(art.mark) ?: return, ART_FILL) { alpha }
        is ShelfArt.App -> {
            val themed = appGlyph(art.packageName)
            if (themed != null) {
                MarkIcon(themed, ART_FILL) { alpha }
                return
            }
            // An app no pack draws keeps its own launcher icon, as its grid tile does.
            val icon = launcherIcon(art.packageName)
            if (icon != null) {
                LauncherImage(icon, contentDescription = null, modifier = Modifier.fillMaxSize(ART_FILL).graphicsLayer { this.alpha = alpha })
            } else {
                SymbolIcon("emulators", ART_FILL * SYMBOL_FILL, theme.onBackground) { alpha }
            }
        }
        is ShelfArt.Control -> {
            val icon = themeIconOrNull(art.slot, 128) ?: return
            Image(
                painter = icon.painter,
                contentDescription = null,
                colorFilter = icon.tint(theme.onBackground),
                modifier = Modifier.fillMaxSize(ART_FILL * SYMBOL_FILL).graphicsLayer { this.alpha = alpha },
            )
        }
    }
}

/**
 * Categories and rows normally share one height and one gap. System contracts padding
 * with menuFit when its pending permissions need more room. Six categories take 6 × 52 + 5 × 4 = 332dp, inside the
 * Thor's 468dp top screen less the page's margins, header, and the ring's reach. The card
 * beside them has the page's full height, about 410dp inside the ring's reach.
 */
private val ROW_HEIGHT = 52.dp
private val ROW_GAP = 4.dp

/**
 * A shelf's tiles. Two lines of them over three rows, with the strip's bar when it
 * scrolls, fit the card on the Thor's top screen without the card scrolling.
 */
private val TILE_ART = 56.dp
private val TILE_LABEL_GAP = 2.dp
private val TILE_LINE_GAP = 6.dp
private val TILE_BLOCK_GAP = 6.dp
private val BADGE_SIZE = 22.dp

/** Between the strip's two lines: a line's gap and a row's, as when each line was a row of the card. */
private val SHELF_LINE_GAP = TILE_LINE_GAP + ROW_GAP

/** Above the strip's first line, for the badge that sits over a tile's corner. */
private val BADGE_ROOM = 8.dp

/** How far past the ring's reach the strip's edge fades. */
private val SHELF_FADE = 8.dp

/** The strip's bar: as long as the grid's, in pixels as the grid's is. */
private const val SHELF_BAR_PX = 160f

/** How far a flick carries the strip: its speed as the finger lifts, for this long. */
private const val SHELF_FLICK_SECONDS = 0.15f

/** How far past either end a finger can pull the strip, in columns. */
private const val SHELF_GIVE = 0.3f
private val HEADER_ART = 56.dp

/** Beyond the ring's reach inside the card's scroll. */
private val CARD_EXTRA = 0.dp

/** How much of its square a tile's object fills, and a flat glyph within that. */
private const val ART_FILL = 0.84f
private const val SYMBOL_FILL = 0.62f

/** The art beside each category, from the theme's own set. */
private fun categoryIcon(category: SettingsCategory): Int = when (category) {
    SettingsCategory.Personalization -> R.drawable.ic_status_display
    SettingsCategory.Sound -> R.drawable.ic_status_volume_3
    SettingsCategory.Library -> R.drawable.symbol_library
    SettingsCategory.Players -> R.drawable.symbol_emulators
    SettingsCategory.Screens -> R.drawable.ic_status_settings
    SettingsCategory.About -> R.drawable.ic_status_info
}

/**
 * A settings row's art. Every row on every page leads with some, so each card reads as one
 * list rather than art here and gaps there. The menus keep [rowIcon] alone.
 */
private fun settingsRowIcon(row: Row): Int? = when (row) {
    // On a page that already shows the connection or the folder, the row wears the act.
    is Row.SignOut, is Row.ForgetFolder -> R.drawable.symbol_remove
    is Row.EditRomm -> R.drawable.symbol_rename
    is Row.PickFolderAgain -> R.drawable.symbol_folder
    // Under the Emulators shelf, so the rows share Systems' icon column.
    Row.SaveSetup -> R.drawable.symbol_saves
    Row.FindSaves, Row.FindGames -> R.drawable.ic_control_search
    Row.SyncLibrary -> R.drawable.ic_badge_sync
    Row.AddFolder -> R.drawable.symbol_folder
    Row.AddRomm -> R.drawable.symbol_cloud
    Row.Artwork -> R.drawable.symbol_art
    Row.ArtKey -> R.drawable.ic_status_wrench
    is Row.StreamHost -> R.drawable.symbol_pc
    // Look: the pack is a gem, its icons a framed mark, the scene's glow, how fast things move, the buttons.
    Row.Theme -> R.drawable.symbol_gem
    Row.IconTheme -> R.drawable.symbol_icon
    Row.Background -> R.drawable.symbol_flame
    Row.MotionSpeed -> R.drawable.symbol_rocket
    Row.ButtonLabels -> R.drawable.symbol_console
    Row.Music -> R.drawable.symbol_music
    Row.MusicTrack -> R.drawable.symbol_disc
    Row.MusicVolume -> R.drawable.ic_status_volume_3
    // System: the two screens, where games open, Home, Android's permissions, Backup.
    Row.ScreenLayout -> R.drawable.symbol_clamshell
    Row.PlayOn -> R.drawable.symbol_play_badge
    Row.SetAsHome, Row.DefaultHomeApp -> R.drawable.symbol_home
    Row.UsageAccess -> R.drawable.ic_status_playtime
    Row.NoticeAccess -> R.drawable.ic_status_bell
    Row.Backup -> R.drawable.symbol_saves
    Row.AndroidSettings -> R.drawable.symbol_android_apps
    Row.Version -> R.drawable.ic_status_info
    Row.Licenses -> R.drawable.symbol_shield
    else -> rowIcon(row)
}

@Composable
private fun Header(theme: Theme, back: PromptKey, titled: Boolean = true, onBack: () -> Unit) =
    PageHeader(theme, back, SettingsCopy.title.takeIf { titled }, onBack)

/**
 * A Settings page's head: Back's key and the page's [title] on one pill, which a tap also
 * closes. Settings, Themes and Licenses wear it. Without a title it is the key alone.
 */
@Composable
internal fun PageHeader(theme: Theme, back: PromptKey, title: String?, onBack: () -> Unit) {
    Row(
        Modifier
            .clip(theme.style.shape.control())
            .background(theme.surface)
            .press(onBack)
            .padding(start = 12.dp, end = if (title != null) 24.dp else 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PromptImage(promptGlyph(back), 32.dp)
        if (title != null) BasicText(title, style = style(theme.onBackground, theme.style.type.heroTitle, theme, bold = true, display = true))
    }
}

/** A Settings page's card: the lifted fill and the faint border, as tall as the page. */
internal fun Modifier.settingsCard(theme: Theme): Modifier {
    val design = theme.style
    val card = RoundedCornerShape(design.metrics.cardCornerDp.dp)
    return clip(card)
        .background(cardFill(theme, design.settings.cardLift))
        .border(design.settings.borderDp.dp, theme.onBackground.copy(alpha = design.settings.borderAlpha), card)
}

/** The margins round a Settings page on the top screen. */
internal val SETTINGS_PAGE_H = 40.dp
internal val SETTINGS_PAGE_V = 16.dp

/** A category: its art and one word, always the same height so the highlight never changes size. */
@Composable
private fun CategoryRow(
    title: String,
    icon: Int,
    labelled: Boolean,
    selected: Boolean,
    focused: Boolean,
    scale: Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalFoldTheme.current.theme
    val chosen = eased(if (selected) 1f else 0f, theme.motion.durationFocus, scale)
    val lift = bouncy(if (focused) 1f else 0f, scale)
    Row(
        modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .keepInView(focused)
            .press(onClick)
            // Art alone still says which category it is.
            .then(if (labelled) Modifier else Modifier.semantics { contentDescription = title })
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val art = themeIcon(icon)
        Image(
            painter = art.painter,
            contentDescription = null,
            colorFilter = art.tint(theme.onBackground),
            modifier = Modifier.size(34.dp).lifted(chosen, lift, grow = theme.style.settings.artGrow, rest = theme.style.settings.artRestAlpha),
        )
        if (labelled) BasicText(
            title,
            modifier = Modifier.graphicsLayer {
                alpha = theme.style.settings.labelRestAlpha.let { it + (1f - it) * chosen.value }
                translationX = chosen.value * 4.dp.toPx()
            },
            style = style(theme.onBackground, theme.style.type.menuRow, theme, bold = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A held row's press: A is held while the finger is down, and letting go early leaves the row as it was. */
private fun Modifier.holdPress(app: FoldcadeApp, index: Int, screen: HostScreen): Modifier = pointerInput(index) {
    detectTapGestures(onPress = {
        val key = promptKeyOf(app.shell.model.faceMap.confirmKey) ?: PromptKey.FaceA
        app.shell.setPromptHeld(key, true, app.shell.inputWindow(screen))
        app.shell.touchSettingsRow(index, screen)
        try { tryAwaitRelease() } finally { app.shell.setPromptHeld(key, false, app.shell.inputWindow(screen)) }
    })
}

/** The fill a held row grows while it is held, and the shake when it is let go early. */
@Composable
private fun holdFeedback(model: PickerModel, row: Row, scale: Float): Modifier {
    if (!row.destructive()) return Modifier
    val theme = LocalFoldTheme.current.theme
    val motion = theme.motion
    val shake = remember { Animatable(0f) }
    LaunchedEffect(model.deleteShake) {
        if (model.deleteShake == 0) return@LaunchedEffect
        shake.snapTo(-theme.style.motion.shakePx)
        shake.animateTo(theme.style.motion.shakePx, motion.playful(motion.durationShort, scale))
        shake.animateTo(0f, motion.playful(motion.durationShort, scale))
    }
    return Modifier.graphicsLayer { translationX = shake.value }.drawBehind {
        if (model.deleteHold?.row == row) drawRoundRect(
            theme.focus.copy(alpha = theme.style.menu.holdAlpha),
            size = Size(size.width * motion.holdFill(model.deleteProgress), size.height),
            cornerRadius = CornerRadius(theme.style.shape.highlightCornerDp.dp.toPx()),
        )
    }
}

@Composable
private fun Modifier.press(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return focusProperties { canFocus = false }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

private fun style(color: Color, size: TextUnit, theme: Theme, bold: Boolean = false, display: Boolean = false): TextStyle = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = if (display) theme.displayFont else theme.font,
    fontWeight = if (bold) theme.style.type.strongWeight else theme.style.type.bodyWeight,
)

/** Narrower than this, as the Thor's bottom screen, Settings drops its words beside the card. */
private val COMPACT_BELOW = 640.dp

/** The categories' column with art alone: the art and the row's padding on each side. */
private val COMPACT_COLUMN = 66.dp
