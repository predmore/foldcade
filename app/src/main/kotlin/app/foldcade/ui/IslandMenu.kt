package app.foldcade.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.layout.onSizeChanged
import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.foldcade.FoldcadeApp
import app.foldcade.R
import app.foldcade.Panel
import app.foldcade.language.Chrome
import app.foldcade.language.SelectionCopy
import app.foldcade.language.Copy
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.heroCopy
import app.foldcade.language.PromptKey
import app.foldcade.language.Side
import app.foldcade.language.SidePanel
import app.foldcade.language.playDuration
import app.foldcade.language.playToday
import app.foldcade.millisUntilNextMinute
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Art-kit shoulder chips. Outline while the island is closed, filled while that
 * menu is open.
 * `@drawable/ic_btn_l1`, `@drawable/ic_btn_l1_filled`,
 * `@drawable/ic_btn_r1`, `@drawable/ic_btn_r1_filled`.
 */
internal object ShoulderChips {
    const val l1 = "ic_btn_l1"
    const val l1Filled = "ic_btn_l1_filled"
    const val r1 = "ic_btn_r1"
    const val r1Filled = "ic_btn_r1_filled"
}

@Composable
internal fun TopIslands(
    app: FoldcadeApp,
    screen: HostScreen,
    scale: Float,
    menuRows: @Composable (panel: SidePanel, progress: () -> Float, interactive: Boolean) -> Unit,
) {
    // Top screen only. The bottom screen keeps the grid and the hint row.
    // The L1 chip, launch target, and status cluster are not drawn there.
    if (screen != HostScreen.Top) return
    val model = app.shell.model
    val dialogCovers = model.dialog?.screen == HostScreen.Top
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zIndex(4f)
            .focusProperties { canFocus = false },
    ) {
        // A tap off the open card closes it, as its shoulder would. The cards sit above and keep their own taps.
        if (!dialogCovers && model.panel?.screen == HostScreen.Top) Box(Modifier.fillMaxSize().closesIsland(app))
        Island(
            app = app,
            side = Side.Left,
            open = !dialogCovers && model.panel?.side == Side.Left && model.panel?.screen == HostScreen.Top,
            scale = scale,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            menuRows = menuRows,
        )
        Island(
            app = app,
            side = Side.Right,
            open = !dialogCovers && model.panel?.side == Side.Right && model.panel?.screen == HostScreen.Top,
            scale = scale,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            menuRows = menuRows,
        )
    }
}

@Composable
private fun Island(
    app: FoldcadeApp,
    side: Side,
    open: Boolean,
    scale: Float,
    maxWidth: Dp,
    maxHeight: Dp,
    menuRows: @Composable (panel: SidePanel, progress: () -> Float, interactive: Boolean) -> Unit,
) {
    val design = LocalFoldTheme.current.theme.style
    val theme = LocalFoldTheme.current.theme
    val density = LocalDensity.current
    val model = app.shell.model
    val held = app.islandHold
    val mine = model.panel?.takeIf { it.side == side && it.screen == HostScreen.Top }
    val retiringHere = held == null && mine?.retiring == true
    val snap = held != null
    val target = when {
        held != null && held.side == side -> held.progress
        held != null || retiringHere -> 0f
        mine != null -> 1f
        else -> 0f
    }
    val progress = islandProgress(
        target = target,
        scale = scale,
        snap = snap,
        handoff = retiringHere,
        onHandoff = { app.shell.completeIslandRetire() },
    )
    val live = app.shell.model.panel?.takeIf { it.side == side && it.screen == HostScreen.Top }
    var shown by remember { mutableStateOf<SidePanel?>(null) }
    if (live != null) shown = live
    val panel = shown
    val inset = maxWidth * design.metrics.insetFraction
    // The closed pill fits its row. Whatever comes or goes in the row opens or closes its
    // place, and the pill follows. The status the open card adds opens with the card.
    var rowWidth by remember(side) { mutableStateOf(Dp.Unspecified) }
    val pillHeight = 44.dp
    val pillWidth = rowWidth.takeIf { it.isSpecified } ?: pillHeight
    val edge = if (side == Side.Left) Alignment.Start else Alignment.End
    val openWidth = maxWidth * 0.48f
    // The open card fits its rows, up to most of the screen. A taller menu scrolls.
    // A new level or a new notice changes the rows, and the card eases to the new height.
    var rowsHeight by remember(side) { mutableStateOf(Dp.Hairline) }
    val reach = ringReach()
    val maximumHeight = with(density) { (maxHeight * 0.88f).roundToPx() }
    val reservedHeight = with(density) { pillHeight.roundToPx() + reach.roundToPx() * 2 + MENU_BOTTOM_DP.dp.roundToPx() }
    val availableRows = with(density) { (maximumHeight - reservedHeight).toDp() }
    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf(0) }
    val fitted = if (rowsHeight > Dp.Hairline) {
        with(density) { minOf(maximumHeight, reservedHeight + rowsHeight.roundToPx()).toDp() }
    } else {
        with(density) { maximumHeight.toDp() }
    }
    // The first measured height lands at once; a change of rows eases to the next.
    var settled by remember(side) { mutableStateOf(false) }
    val openHeight = motionState(
        fitted.value,
        theme.motion.playful(theme.motion.durationSelect, scale),
        snap = !settled,
    ).value.dp
    if (rowsHeight > Dp.Hairline) SideEffect { settled = true }
    val width = lerp(pillWidth, openWidth, progress)
    val height = lerp(pillHeight, openHeight, progress)
    val radius = lerp(pillHeight * design.shape.round, design.metrics.cardCornerDp.dp, progress)
    val shape = RoundedCornerShape(radius)
    val x = if (side == Side.Left) inset else maxWidth - inset - width
    val focusedIsland = progress < 0.08f && app.shell.model.panel == null && app.shell.model.dialog == null &&
        when (side) {
            Side.Left -> app.shell.model.focus.chrome == Chrome.LeftIsland
            Side.Right -> app.shell.model.focus.chrome == Chrome.StatusCluster
        }
    val interactive = open && progress > 0.92f
    // Y's menu acts on one tile, so its header names that tile instead of today's play.
    // The two swap as the card grows and shrinks, so the closed pill is always L1's.
    val actingShown = if (side == Side.Left && panel?.level?.actsOnTile == true) progress else 0f
    val rim = remember(theme) { FocusRing(theme) }
    // The rows read the open fraction in their layers, so the morph does not recompose them.
    val opening = rememberUpdatedState(progress)
    val readOpening = remember { { opening.value } }
    Box(
        Modifier
            .offset {
                IntOffset(
                    with(density) { x.roundToPx() },
                    with(density) { inset.roundToPx() },
                )
            }
            .size(width, height)
            .zIndex(if (progress > 0f) 2f else 1f)
            // A filled card. A faint edge keeps it apart from a black backdrop, and lights
            // with the focus colors as the menu opens.
            .border(
                if (focusedIsland) design.menu.islandFocusBorderDp.dp else design.menu.islandBorderDp.dp,
                if (focusedIsland) {
                    SolidColor(theme.focus)
                } else {
                    val edge = design.menu.islandRimAlpha
                    val lit = design.menu.islandRimLitAlpha
                    Brush.linearGradient(
                        listOf(
                            rim.start.copy(alpha = edge + lit * progress),
                            theme.onBackground.copy(alpha = edge * RIM_MIDDLE),
                            rim.end.copy(alpha = edge + lit * RIM_END * progress),
                        ),
                    )
                },
                shape,
            )
            .clip(shape)
            .background(cardFill(theme, design.menu.islandLift * progress))
            // The open card holds a tap between its rows, so it never reaches the catch under it.
            .then(if (!interactive) Modifier.islandPress { app.shell.onMeaning(side.meaning(), HostScreen.Top) } else Modifier.blocksTouch()),
    ) {
        // The chip leads on both sides, so the clock is the last thing on the right.
        // The chip art carries its own margin. The text end gets a little more.
        // The row holds to the card's outer edge, so the clock stays put while the card grows.
        Row(
            Modifier
                .align(if (side == Side.Left) Alignment.TopStart else Alignment.TopEnd)
                .wrapContentWidth(edge, unbounded = true)
                .onSizeChanged { rowWidth = with(density) { it.width.toDp() } }
                .height(pillHeight)
                .padding(start = 8.dp, end = 14.dp)
                .graphicsLayer { alpha = 1f - actingShown },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (side == Side.Left) {
                ShoulderChip(R.drawable.ic_btn_l1, R.drawable.ic_btn_l1_filled, progress, "L1")
                PlayToday(app)
            } else {
                ShoulderChip(R.drawable.ic_btn_r1, R.drawable.ic_btn_r1_filled, progress, "R1")
                // The status stays while R1 is open, and says more: the date, the battery
                // percent, and Bluetooth. The bell stays too, since the notices are still there.
                StatusIcons(app, readOpening, scale)
            }
        }
        if (side == Side.Left && actingShown > 0f) {
            ActingHeader(app, Modifier.height(pillHeight).padding(start = 14.dp, end = 20.dp).graphicsLayer { alpha = actingShown })
        }
        if (panel != null && progress > 0f) {
            // The rows are laid out at the open card's width from the first frame, and the
            // growing card reveals them. Laid out at its passing width they would wrap.
            Column(
                Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(edge, unbounded = true)
                    .width(openWidth)
                    .padding(start = 6.dp, end = 6.dp, top = pillHeight, bottom = MENU_BOTTOM_DP.dp)
                    // The scroll clips. This keeps the focus ring's glow on every row inside it.
                    .onSizeChanged { viewport = it.height }
                    .menuFade(scroll)
                    .verticalScroll(scroll)
                    .padding(reach),
            ) {
                SlideSwap(
                    target = panel,
                    scale = scale,
                    distance = 40.dp,
                    vertical = false,
                    direction = { from, to -> to.level.depth.compareTo(from.level.depth) },
                    front = Modifier.onSizeChanged { rowsHeight = with(density) { it.height.toDp() } },
                    key = { it.level to it.connection },
                ) { shownPanel ->
                    // Rows on their way out take no taps: their indices belong to the old level.
                    val arriving = shownPanel.level == panel.level && shownPanel.connection == panel.connection
                    CompositionLocalProvider(LocalIslandScroll provides IslandScroll(availableRows, scroll, viewport)) {
                        menuRows(shownPanel, readOpening, interactive && arriving)
                    }
                }
            }
        }
    }
}

/** Y and the name of the tile its menu acts on. */
@Composable
private fun ActingHeader(app: FoldcadeApp, modifier: Modifier) {
    val theme = LocalFoldTheme.current.theme
    val name = app.shell.model.tileActions?.bulk?.let { SelectionCopy.count(it.size) } ?: app.shell.focusedHero()?.let { heroCopy(it).title }?.takeIf { it.isNotBlank() } ?: Copy.emptySlot
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptImage(promptGlyph(PromptKey.FaceY), 26.dp)
        BasicText(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = theme.onBackground, fontSize = theme.style.type.sideRow, fontFamily = theme.font, fontWeight = theme.style.type.strongWeight),
        )
    }
}

/** The open card's margin under its scroll. The rows keep the ring's reach inside it as well. */
private const val MENU_BOTTOM_DP = 6f

/** The island's rim, at its middle and at its end, as shares of the theme's rim at its start. */
private const val RIM_MIDDLE = 0.06f / 0.08f
private const val RIM_END = 0.26f / 0.34f

private fun Side.meaning(): Meaning = if (this == Side.Left) Meaning.LeftPanel else Meaning.RightPanel

@Composable
private fun islandProgress(
    target: Float,
    scale: Float,
    snap: Boolean,
    handoff: Boolean,
    onHandoff: () -> Unit,
): Float {
    val motion = LocalFoldTheme.current.theme.motion
    val anim = remember { androidx.compose.animation.core.Animatable(target) }
    LaunchedEffect(target, snap, handoff) {
        if (snap) {
            anim.snapTo(target)
            return@LaunchedEffect
        }
        if (abs(anim.value - target) > 0.001f) {
            val spec = if (target >= anim.value) {
                motion.arrive(motion.durationIsland, scale)
            } else {
                motion.leave(motion.durationIsland, scale)
            }
            val budget = motion.duration(
                motion.durationIsland,
                scale,
            ).toLong() + 80L
            withTimeoutOrNull(budget) {
                kotlinx.coroutines.withContext(SteadyMenu) {
                    anim.animateTo(target, spec)
                }
            }
        }
        // Every run lands on its target, so a closed island reads exactly 0.
        if (anim.value != target) anim.snapTo(target)
        if (handoff && anim.value <= 0.02f) onHandoff()
    }
    return if (snap) target else anim.value
}

/**
 * Cross-fades `@drawable/ic_btn_l1` or `@drawable/ic_btn_r1` into the `_filled` variant.
 */
@Composable
private fun ShoulderChip(outline: Int, filled: Int, openFraction: Float, label: String) {
    Box(Modifier.size(36.dp, 24.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = themeIconPainter(outline),
            contentDescription = label,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - openFraction },
            contentScale = ContentScale.Fit,
        )
        Image(
            painter = themeIconPainter(filled),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = openFraction },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun Glyph(id: Int, description: String?) {
    val icon = themeIcon(id)
    Image(
        painter = icon.painter,
        colorFilter = icon.requestedTint(LocalFoldTheme.current.theme.onBackground),
        contentDescription = description,
        modifier = Modifier.size(18.dp),
        contentScale = ContentScale.Fit,
    )
}

/** Battery, Wi-Fi, and the bell are art-kit icons. The clock stays text. */
internal fun batteryStatusIcon(percent: Int, charging: Boolean): Int {
    val bucket = ((percent.coerceIn(0, 100) + 5) / 10 * 10).coerceAtMost(100)
    return if (charging) {
        when (bucket) {
            0 -> R.drawable.ic_status_battery_0_charging
            10 -> R.drawable.ic_status_battery_10_charging
            20 -> R.drawable.ic_status_battery_20_charging
            30 -> R.drawable.ic_status_battery_30_charging
            40 -> R.drawable.ic_status_battery_40_charging
            50 -> R.drawable.ic_status_battery_50_charging
            60 -> R.drawable.ic_status_battery_60_charging
            70 -> R.drawable.ic_status_battery_70_charging
            80 -> R.drawable.ic_status_battery_80_charging
            90 -> R.drawable.ic_status_battery_90_charging
            else -> R.drawable.ic_status_battery_100_charging
        }
    } else {
        when (bucket) {
            0 -> R.drawable.ic_status_battery_0
            10 -> R.drawable.ic_status_battery_10
            20 -> R.drawable.ic_status_battery_20
            30 -> R.drawable.ic_status_battery_30
            40 -> R.drawable.ic_status_battery_40
            50 -> R.drawable.ic_status_battery_50
            60 -> R.drawable.ic_status_battery_60
            70 -> R.drawable.ic_status_battery_70
            80 -> R.drawable.ic_status_battery_80
            90 -> R.drawable.ic_status_battery_90
            else -> R.drawable.ic_status_battery_100
        }
    }
}

internal fun wifiStatusIcon(network: String, bars: Int = 4): Int = when {
    network != Copy.wifi -> R.drawable.ic_status_wifi_off
    bars <= 0 -> R.drawable.ic_status_wifi_0
    bars == 1 -> R.drawable.ic_status_wifi_1
    bars == 2 -> R.drawable.ic_status_wifi_2
    bars == 3 -> R.drawable.ic_status_wifi_3
    else -> R.drawable.ic_status_wifi_4
}

/** The bell with its count. Null with no notices: an empty bell would only be noise. */
internal fun bellStatusIcon(count: Int): Int? = when {
    count <= 0 -> null
    count == 1 -> R.drawable.ic_status_bell_1
    count == 2 -> R.drawable.ic_status_bell_2
    count == 3 -> R.drawable.ic_status_bell_3
    count == 4 -> R.drawable.ic_status_bell_4
    count == 5 -> R.drawable.ic_status_bell_5
    count == 6 -> R.drawable.ic_status_bell_6
    count == 7 -> R.drawable.ic_status_bell_7
    count == 8 -> R.drawable.ic_status_bell_8
    else -> R.drawable.ic_status_bell_9plus
}

/**
 * Time, battery, Wi-Fi, and the bell. As R1 opens ([open] toward 1) the date,
 * the battery percent, and Bluetooth open their places beside the others and fade in.
 */
@Composable
private fun StatusIcons(app: FoldcadeApp, open: () -> Float, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val status by app.deviceStatus.status.collectAsStateWithLifecycle()
    val opened by remember { derivedStateOf { open() > 0f } }
    // The bell opens its place as the first notice comes and closes it after the last,
    // still showing the count it had.
    val notices = app.shell.model.notices.size
    var bell by remember { mutableStateOf<Int?>(null) }
    bellStatusIcon(notices)?.let { bell = it }
    val ringing = eased(if (notices > 0) 1f else 0f, theme.motion.durationFocus, scale)
    val style = TextStyle(color = theme.onBackground, fontSize = LocalFoldTheme.current.theme.style.type.hint, fontFamily = theme.font)
    val muted = style.copy(color = theme.muted)
    // Read from the edge in: the clock, then the date, battery, Wi-Fi, Bluetooth, and the bell.
    // Each carries the gap after it, so a place opening or closing takes its gap along.
    Row(verticalAlignment = Alignment.CenterVertically) {
        bell?.let { art -> StatusPlace({ ringing.value }) { Glyph(art, null) } }
        if (opened && status.bluetooth) StatusPlace(open) { Glyph(R.drawable.ic_status_bluetooth, null) }
        StatusPlace { Glyph(wifiStatusIcon(status.network, status.wifiBars), null) }
        if (opened) StatusPlace(open) { BasicText(text = "${status.batteryPercent}%", style = style) }
        StatusPlace { Glyph(batteryStatusIcon(status.batteryPercent, status.charging), null) }
        if (opened && status.date.isNotEmpty()) StatusPlace(open) { BasicText(text = status.date, style = muted) }
        BasicText(text = status.time, style = style)
    }
}

/**
 * One status item and the gap after it. [shown] opens its place from nothing to its full
 * width and fades it in. The item holds to the place's end, so it stays still while its
 * place opens beside the clock, instead of sliding.
 */
@Composable
private fun StatusPlace(shown: (() -> Float)? = null, content: @Composable () -> Unit) {
    val place = if (shown == null) {
        Modifier
    } else {
        Modifier
            .graphicsLayer {
                alpha = shown()
                clip = true
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val width = (placeable.width * shown().coerceIn(0f, 1f)).roundToInt()
                layout(width, placeable.height) { placeable.place(width - placeable.width, 0) }
            }
    }
    Box(place.padding(end = 8.dp), contentAlignment = Alignment.Center) { content() }
}

/**
 * Time played today, beside L1. Midnight resets it with Foldcade still open,
 * so the day is read again each minute.
 */
@Composable
private fun PlayToday(app: FoldcadeApp) {
    val theme = LocalFoldTheme.current.theme
    var day by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilNextMinute(System.currentTimeMillis()))
            day = LocalDate.now()
        }
    }
    val stamp = app.plays.stamp
    val today = remember(stamp, day) {
        playToday(app.plays.spans(), System.currentTimeMillis(), ZoneId.systemDefault())
    }
    Glyph(R.drawable.ic_status_playtime, null)
    BasicText(
        text = playDuration(today),
        style = TextStyle(color = theme.onBackground, fontSize = LocalFoldTheme.current.theme.style.type.hint, fontFamily = theme.font),
    )
}

@Composable
private fun Modifier.islandPress(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .focusProperties { canFocus = false }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Closes the open island on a tap, the way pressing its shoulder again would. A drag is left alone. */
internal fun Modifier.closesIsland(app: FoldcadeApp): Modifier = pointerInput(app) {
    detectTapGestures { app.shell.closePanel() }
}

private fun lerp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction
