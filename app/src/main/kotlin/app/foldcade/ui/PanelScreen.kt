package app.foldcade.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import app.foldcade.Displays
import app.foldcade.FoldcadeApp
import app.foldcade.FoldcadeHomeActivity
import app.foldcade.Shelf
import app.foldcade.api.Panel
import app.foldcade.api.Surface
import app.foldcade.batteryLabel
import app.foldcade.language.Chrome
import app.foldcade.language.Copy
import app.foldcade.language.DialogButton
import app.foldcade.language.DialogState
import app.foldcade.language.HintPlace
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.Metrics
import app.foldcade.language.Motion
import app.foldcade.language.Side
import app.foldcade.language.TypeRamp
import app.foldcade.language.builtInTheme
import app.foldcade.language.displayOrder
import app.foldcade.language.hintFor
import app.foldcade.language.monogram
import app.foldcade.language.panelRows
import app.foldcade.language.rowLabel
import app.foldcade.readDeviceStatus
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PanelHost(activity: FoldcadeHomeActivity, displays: Displays) {
    val app = activity.application as FoldcadeApp
    val session = app.store.session
    val panel = displays.panelFor(activity, session.defaultDisplayIsTop)
    val theme = builtInTheme()
    val scale = Motion.animatorScale(LocalContext.current.contentResolver)
    Box(Modifier.fillMaxSize().background(theme.background)) {
        if (panel == null) return@Box
        val screen = if (panel == Panel.Top) HostScreen.Top else HostScreen.Bottom
        TravelFade(target = session.surfaceOn(panel), scale = scale) { shown ->
            when (shown) {
                null -> Unit
                Surface.Hero -> Hero(app, screen, scale)
                Surface.Picker -> Picker(app, screen, scale)
            }
        }
    }
}

private object SteadyMotion : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

@Composable
private fun motionFloat(target: Float, spec: FiniteAnimationSpec<Float>): Float {
    val anim = remember { Animatable(target) }
    LaunchedEffect(target) {
        withContext(SteadyMotion) { anim.animateTo(target, spec) }
    }
    return anim.value
}

@Composable
private fun <T> TravelFade(target: T, scale: Float, content: @Composable (T) -> Unit) {
    var front by remember { mutableStateOf(target) }
    var back by remember { mutableStateOf<T?>(null) }
    val incoming = remember { Animatable(1f) }
    val outgoing = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (target == front) return@LaunchedEffect
        back = front
        incoming.snapTo(0f)
        outgoing.snapTo(1f)
        front = target
        withContext(SteadyMotion) {
            coroutineScope {
                launch { incoming.animateTo(1f, Motion.arrive(Motion.durationTravel, scale)) }
                launch { outgoing.animateTo(0f, Motion.leave(Motion.durationTravel, scale)) }
            }
        }
        back = null
    }
    Box {
        val previous = back
        if (previous != null) {
            Box(Modifier.graphicsLayer { alpha = outgoing.value }) { content(previous) }
        }
        Box(Modifier.graphicsLayer { alpha = incoming.value }) { content(front) }
    }
}

@Composable
private fun Hero(app: FoldcadeApp, screen: HostScreen, scale: Float) {
    val theme = builtInTheme()
    val game = app.shell.focusedGame()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = px(Metrics.heroInsetPx),
                    end = px(Metrics.heroInsetPx),
                    top = px(Metrics.heroInsetPx),
                ),
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(Metrics.heroArtFraction))
            TravelFade(target = game, scale = scale) { shown ->
                Column {
                    if (shown != null) {
                        BasicText(
                            text = shown.title,
                            style = text(theme.onBackground, TypeRamp.heroTitle, theme),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        BasicText(
                            text = shown.shortText,
                            style = text(theme.muted, TypeRamp.heroMeta, theme),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Panels(app, screen, scale)
    }
}

@Composable
private fun Picker(app: FoldcadeApp, screen: HostScreen, scale: Float) {
    val theme = builtInTheme()
    val shell = app.shell
    val model = shell.model
    val session = app.store.session
    val game = shell.focusedGame()
    BoxWithConstraints(Modifier.fillMaxSize().background(theme.background)) {
        val inset = maxWidth * Metrics.insetFraction
        val gap = maxWidth * Metrics.gapFraction
        val cell = (maxWidth - inset * 2 - gap * (Metrics.columns - 1)) / Metrics.columns
        Column(Modifier.fillMaxSize().padding(horizontal = inset, vertical = inset)) {
            ChromeRow(app, screen)
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(model.panel, model.dialog, model.connectOpen) {
                        if (model.panel != null || model.dialog != null || model.connectOpen) return@pointerInput
                        var dragged = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, amount -> dragged += amount },
                            onDragEnd = {
                                val meaning = when {
                                    dragged > viewConfiguration.touchSlop -> Meaning.PageTowardStart
                                    dragged < -viewConfiguration.touchSlop -> Meaning.PageTowardEnd
                                    else -> null
                                }
                                dragged = 0f
                                if (meaning != null) shell.onMeaning(meaning, screen)
                            },
                        )
                    },
            ) {
                val rows = if (cell > Dp.Hairline) {
                    ((maxHeight + gap) / (cell + gap)).toInt().coerceAtLeast(1)
                } else {
                    1
                }
                SideEffect { shell.setRowsPerPage(rows) }
                if (model.connectOpen) {
                    ConnectScreen()
                } else {
                    PagedGrid(
                        app = app,
                        screen = screen,
                        cell = cell,
                        gap = gap,
                        rows = rows,
                        scale = scale,
                        showTitle = !session.bothScreensFree(),
                        usesBoth = game?.occupiesBothDisplays == true && session.bothScreensFree(),
                    )
                }
            }
        }
        Panels(app, screen, scale)
        DialogLayer(model.dialog, screen, scale)
    }
}

@Composable
private fun Panels(app: FoldcadeApp, screen: HostScreen, scale: Float) {
    val model = app.shell.model
    if (model.dialog?.screen == screen) return
    val leftOpen = model.panel?.side == Side.Left && model.panel?.screen == screen
    val rightOpen = model.panel?.side == Side.Right && model.panel?.screen == screen
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inset = maxWidth * Metrics.insetFraction
        val openWidth = maxWidth * 0.46f
        val openHeight = maxHeight * 0.62f
        LeftPanel(app, screen, leftOpen, scale, maxWidth * 0.42f)
        Box(Modifier.align(Alignment.TopEnd).padding(top = inset, end = inset)) {
            RightCluster(
                app = app,
                screen = screen,
                open = rightOpen,
                scale = scale,
                openWidth = openWidth,
                openHeight = openHeight,
            )
        }
    }
}

@Composable
private fun LeftPanel(
    app: FoldcadeApp,
    screen: HostScreen,
    open: Boolean,
    scale: Float,
    openWidth: Dp,
) {
    val theme = builtInTheme()
    val progress = motionFloat(
        target = if (open) 1f else 0f,
        spec = if (open) Motion.arrive(Motion.durationTravel, scale) else Motion.leave(Motion.durationTravel, scale),
    )
    if (progress <= 0f) return
    Column(
        Modifier
            .fillMaxHeight()
            .width(openWidth * progress)
            .background(theme.surface)
            .padding(px(24f)),
        verticalArrangement = Arrangement.spacedBy(px(8f)),
    ) {
        if (open) PanelRows(app, screen, progress)
    }
}

@Composable
private fun RightCluster(
    app: FoldcadeApp,
    screen: HostScreen,
    open: Boolean,
    scale: Float,
    openWidth: Dp,
    openHeight: Dp,
) {
    val theme = builtInTheme()
    val progress = motionFloat(
        target = if (open) 1f else 0f,
        spec = if (open) Motion.arrive(Motion.durationTravel, scale) else Motion.leave(Motion.durationTravel, scale),
    )
    var cluster by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val grown = progress > 0f && cluster != IntSize.Zero
    val width = if (grown) lerp(with(density) { cluster.width.toDp() }, openWidth, progress) else null
    val height = if (grown) lerp(with(density) { cluster.height.toDp() }, openHeight, progress) else null
    Column(
        Modifier
            .then(if (width != null && height != null) Modifier.size(width, height) else Modifier)
            .background(if (progress > 0f) theme.surface else Color.Transparent)
            .padding(px(8f)),
    ) {
        StatusLine(
            noticeCount = app.shell.model.notices.size,
            modifier = Modifier.onSizeChanged { if (!open) cluster = it },
            onClick = { app.shell.onMeaning(Meaning.RightPanel, screen) },
            focused = app.shell.model.focus.chrome == Chrome.StatusCluster &&
                app.shell.model.panel == null &&
                app.shell.model.dialog == null,
        )
        if (progress > 0f && open) PanelRows(app, screen, progress)
    }
}

private fun lerp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction

@Composable
private fun StatusLine(
    noticeCount: Int,
    modifier: Modifier,
    onClick: () -> Unit,
    focused: Boolean,
) {
    val theme = builtInTheme()
    val context = LocalContext.current
    var status by remember { mutableStateOf(readDeviceStatus(context)) }
    LaunchedEffect(context) {
        while (true) {
            status = readDeviceStatus(context)
            delay(30_000)
        }
    }
    Row(
        modifier.focusStroke(focused).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(px(12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = status.time, style = text(theme.onBackground, TypeRamp.hint, theme))
        BasicText(text = batteryLabel(status), style = text(theme.onBackground, TypeRamp.hint, theme))
        BasicText(text = status.network, style = text(theme.onBackground, TypeRamp.hint, theme))
        if (noticeCount > 0) {
            BasicText(text = noticeCount.toString(), style = text(theme.onBackground, TypeRamp.hint, theme))
        }
    }
}

@Composable
private fun PanelRows(app: FoldcadeApp, screen: HostScreen, progress: Float) {
    val theme = builtInTheme()
    val panel = app.shell.model.panel ?: return
    val rows = panelRows(panel, app.shell.model)
    Column(Modifier.graphicsLayer { alpha = progress }, verticalArrangement = Arrangement.spacedBy(px(8f))) {
        rows.forEachIndexed { index, row ->
            val focused = panel.index == index
            BasicText(
                text = rowLabel(row, app.shell.model),
                modifier = Modifier
                    .focusStroke(focused)
                    .clickable {
                        val shell = app.shell
                        val current = shell.model.panel?.index ?: 0
                        val delta = index - current
                        val meaning = if (delta > 0) Meaning.MoveDown else Meaning.MoveUp
                        if (delta == 0) {
                            shell.onMeaning(Meaning.Activate, screen)
                        } else {
                            repeat(abs(delta)) { shell.onMeaning(meaning, screen) }
                        }
                    }
                    .padding(px(8f)),
                style = text(
                    if (focused) theme.onBackground else theme.muted,
                    TypeRamp.sideRow,
                    theme,
                ),
            )
        }
    }
}

@Composable
private fun ChromeRow(app: FoldcadeApp, screen: HostScreen) {
    val theme = builtInTheme()
    val shell = app.shell
    val model = shell.model
    val game = shell.focusedGame()
    val showLaunch = model.panel == null && model.dialog == null && !model.connectOpen &&
        app.store.session.launchTargetControlVisible(game?.occupiesBothDisplays == true)
    val place = when {
        model.dialog != null -> HintPlace.Dialog
        model.connectOpen -> HintPlace.Connect
        model.panel != null -> HintPlace.Menu
        model.atLibraryRoot -> HintPlace.RootGrid
        else -> HintPlace.InsidePlatform
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(12f))) {
            if (showLaunch && game != null) {
                val target = app.store.session.singleScreenTarget(game.id, game.platformId)
                ChromeButton(
                    label = if (target == Panel.Bottom || model.launchOnBottom) Copy.launchOnBottom else Copy.launchOnTop,
                    focused = model.focus.chrome == Chrome.LaunchTarget,
                    onClick = { shell.touchChrome(Chrome.LaunchTarget, screen) },
                )
            } else if (game?.occupiesBothDisplays == true && app.store.session.bothScreensFree() && model.panel == null) {
                BasicText(text = Copy.usesBothScreens, style = text(theme.muted, TypeRamp.hint, theme))
            }
        }
        val hint = hintFor(place)
        if (hint != null) {
            BasicText(text = hint, style = text(theme.muted, TypeRamp.hint, theme))
        }
    }
}

@Composable
private fun ChromeButton(label: String, focused: Boolean, onClick: () -> Unit) {
    val theme = builtInTheme()
    BasicText(
        text = label,
        modifier = Modifier.focusStroke(focused).clickable(onClick = onClick).padding(px(8f)),
        style = text(theme.onBackground, TypeRamp.sideRow, theme),
    )
}

@Composable
private fun PagedGrid(
    app: FoldcadeApp,
    screen: HostScreen,
    cell: Dp,
    gap: Dp,
    rows: Int,
    scale: Float,
    showTitle: Boolean,
    usesBoth: Boolean,
) {
    val focus = app.shell.model.focus
    val pageSize = (Metrics.columns * rows).coerceAtLeast(1)
    val page = focus.cellIndex / pageSize
    val position = remember { Animatable(page.toFloat()) }
    LaunchedEffect(page, scale) {
        withContext(SteadyMotion) {
            position.animateTo(page.toFloat(), Motion.arrive(Motion.durationTravel, scale))
        }
    }
    val reduced = Motion.reduced(scale)
    val alpha = if (reduced) (1f - abs(position.value - page)).coerceIn(0.35f, 1f) else 1f
    val width = cell * Metrics.columns + gap * (Metrics.columns - 1)
    Box(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }) {
        val shift = (position.value - page) * with(LocalDensity.current) { width.toPx() }
        Box(Modifier.offset { IntOffset(shift.roundToInt(), 0) }) {
            Grid(app, screen, cell, gap, rows, page, showTitle)
        }
    }
    if (usesBoth) {
        BasicText(text = Copy.usesBothScreens, style = text(builtInTheme().muted, TypeRamp.hint, builtInTheme()))
    }
}

@Composable
private fun Grid(
    app: FoldcadeApp,
    screen: HostScreen,
    cell: Dp,
    gap: Dp,
    rows: Int,
    page: Int,
    showTitle: Boolean,
) {
    val shell = app.shell
    val focus = shell.model.focus
    val pageSize = Metrics.columns * rows
    val order = displayOrder(shell.model)
    val radius = cell * builtInTheme().iconRadius
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        val start = page * pageSize
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (column in 0 until Metrics.columns) {
                    val index = start + row * Metrics.columns + column
                    if (index >= shell.model.count) {
                        Box(Modifier.size(cell))
                    } else {
                        val source = order.getOrElse(index) { index }
                        val game = Shelf.games.getOrNull(source)
                        val focused = focus.chrome == null && focus.cellIndex == index && shell.model.panel == null
                        Cell(
                            title = game?.title ?: "",
                            showTitle = showTitle,
                            focused = focused,
                            size = cell,
                            corner = radius,
                            onClick = { shell.touchCell(index, screen) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(
    title: String,
    showTitle: Boolean,
    focused: Boolean,
    size: Dp,
    corner: Dp,
    onClick: () -> Unit,
) {
    val theme = builtInTheme()
    val animatorScale = Motion.animatorScale(LocalContext.current.contentResolver)
    val drawn = motionFloat(
        target = if (focused) Motion.scaleFocus else Motion.scaleRest,
        spec = if (focused) {
            Motion.arrive(Motion.durationFocus, animatorScale)
        } else {
            Motion.leave(Motion.durationFocus, animatorScale)
        },
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.zIndex(if (focused) 1f else 0f),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(drawn)
                .focusStroke(focused)
                .clip(RoundedCornerShape(corner))
                .background(theme.background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = monogram(title),
                modifier = Modifier.scale(theme.artScale),
                style = text(theme.onBackground, TypeRamp.heroTitle, theme),
            )
        }
        if (showTitle) {
            BasicText(
                text = title,
                style = text(theme.onBackground, TypeRamp.gridLabel, theme),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DialogLayer(dialog: DialogState?, screen: HostScreen, scale: Float) {
    val visible = dialog != null && dialog.screen == screen
    var shown by remember { mutableStateOf(dialog) }
    if (visible) shown = dialog
    val alpha = motionFloat(
        target = if (visible) 1f else 0f,
        spec = if (visible) Motion.arrive(Motion.durationShort, scale) else Motion.leave(Motion.durationShort, scale),
    )
    val card = shown
    if (card != null && alpha > 0f) {
        Box(Modifier.graphicsLayer { this.alpha = alpha }) { DialogCard(card) }
    }
}

@Composable
private fun DialogCard(dialog: DialogState) {
    val theme = builtInTheme()
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = px(Metrics.dialogInsetPx))
                .drawWithContent {
                    drawContent()
                    drawRect(color = theme.muted, style = Stroke(width = Metrics.dialogBorderPx))
                }
                .background(theme.surface)
                .padding(px(Metrics.dialogInsetPx)),
            verticalArrangement = Arrangement.spacedBy(px(12f)),
        ) {
            BasicText(text = dialog.title, style = text(theme.onBackground, TypeRamp.dialogTitle, theme))
            BasicText(text = dialog.body, style = text(theme.onBackground, TypeRamp.dialogBody, theme))
            Row(horizontalArrangement = Arrangement.spacedBy(px(16f))) {
                dialog.buttons.forEachIndexed { index, button ->
                    val label = when (button) {
                        DialogButton.UseAsHome -> Copy.useAsHome
                        DialogButton.NotNow -> Copy.notNow
                        DialogButton.ContinueGrant -> Copy.continueGrant
                        DialogButton.Ok -> Copy.ok
                    }
                    BasicText(
                        text = label,
                        modifier = Modifier.focusStroke(dialog.index == index).padding(px(8f)),
                        style = text(theme.onBackground, TypeRamp.dialogBody, theme),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectScreen() {
    val theme = builtInTheme()
    BasicText(text = Copy.connectRomm, style = text(theme.onBackground, TypeRamp.dialogTitle, theme))
}

@Composable
private fun px(value: Float): Dp = with(LocalDensity.current) { value.toDp() }

@Composable
private fun text(
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
    theme: app.foldcade.language.Theme,
): TextStyle = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = theme.font,
    textAlign = TextAlign.Start,
)

@Composable
private fun Modifier.focusStroke(focused: Boolean): Modifier {
    val color = builtInTheme().focus
    return drawWithContent {
        drawContent()
        if (!focused) return@drawWithContent
        val stroke = Metrics.focusStrokePx
        drawRect(
            color = color,
            topLeft = Offset(-stroke / 2f, -stroke / 2f),
            size = Size(size.width + stroke, size.height + stroke),
            style = Stroke(width = stroke),
        )
    }
}
