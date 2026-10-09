package app.foldcade.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.foldcade.Displays
import app.foldcade.FoldcadeApp
import app.foldcade.FoldcadeHomeActivity
import app.foldcade.Shelf
import app.foldcade.api.Panel
import app.foldcade.api.Surface
import app.foldcade.batteryLabel
import app.foldcade.millisUntilNextMinute
import app.foldcade.language.Chrome
import app.foldcade.language.ConnectField
import app.foldcade.language.Copy
import app.foldcade.language.DialogButton
import app.foldcade.language.Effect
import app.foldcade.language.DialogState
import app.foldcade.language.HintPlace
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.Row
import app.foldcade.language.Metrics
import app.foldcade.language.Motion
import app.foldcade.language.PanelLevel
import app.foldcade.language.Side
import app.foldcade.language.SidePanel
import app.foldcade.language.TypeRamp
import app.foldcade.language.connectFields
import app.foldcade.language.connectHint
import app.foldcade.language.cursorBrush
import app.foldcade.language.displayOrder
import app.foldcade.language.hintFor
import app.foldcade.language.monogram
import app.foldcade.language.panelRows
import app.foldcade.language.rowLabel
import app.foldcade.readDeviceStatus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
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
    val paint = app.paintFor(app.shell.model.themeIndex)
    val scale = Motion.animatorScale(LocalContext.current.contentResolver)
    CompositionLocalProvider(LocalFoldTheme provides paint) {
        Box(Modifier.fillMaxSize().background(paint.theme.background)) {
            Backdrop(
                motion = app.shell.model.backgroundMotion,
                speed = app.shell.model.motionSpeed,
                animatorScale = scale,
                running = activity.shellVisible && session.bothScreensFree(),
            )
            if (panel == null) return@Box
            val screen = if (panel == Panel.Top) HostScreen.Top else HostScreen.Bottom
            val model = app.shell.model
            val connectHere = model.connectOpen && model.connectScreen == screen
            TravelFade(target = session.surfaceOn(panel), scale = scale) { shown ->
                when (shown) {
                    null -> Unit
                    Surface.Hero -> if (connectHere) Unit else Hero(app, screen, scale)
                    Surface.Picker -> Picker(app, screen, scale, activity::dispatch)
                }
            }
            if (connectHere) ConnectScreen(app, screen, activity::dispatch)
            DialogLayer(app, model.dialog, screen, scale, activity::dispatch)
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
    val theme = foldTheme()
    val game = app.shell.focusedGame()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inset = px(Metrics.heroInsetPx)
        val artHeight = maxHeight * Metrics.heroArtFraction
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = inset, end = inset, top = inset),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(artHeight),
            )
            TravelFade(target = game, scale = scale) { shown ->
                if (shown != null) {
                    Column {
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
private fun Picker(
    app: FoldcadeApp,
    screen: HostScreen,
    scale: Float,
    onEffect: (Effect?) -> Unit,
) {
    val theme = foldTheme()
    val shell = app.shell
    val model = shell.model
    val session = app.store.session
    val game = shell.focusedGame()
    var clusterHeight by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inset = maxWidth * Metrics.insetFraction
        val gap = maxWidth * Metrics.gapFraction
        val inner = maxWidth - inset * 2
        val cell = (inner - gap * (Metrics.columns - 1)) / Metrics.columns
        val focusOutset = focusOutset(cell)
        val titlePx = rememberTextMeasurer().measure(
            text = "Ag",
            style = text(theme.onBackground, TypeRamp.gridLabel, theme),
            maxLines = 1,
        ).size.height
        val titleLine = with(LocalDensity.current) { titlePx.toDp() }
        val clearance = px(Metrics.chromeClearancePx)
        Column(Modifier.fillMaxSize().padding(horizontal = inset, vertical = inset)) {
            Box(Modifier.fillMaxWidth().heightIn(min = clusterHeight)) {
                Column(Modifier.align(Alignment.BottomStart)) { ChromeRow(app, screen) }
            }
            Spacer(Modifier.height(clearance))
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
                val showTitles = true
                val slot = if (showTitles) cell + focusOutset + titleLine else cell
                val available = (maxHeight - focusOutset * 2).coerceAtLeast(slot)
                val rows = if (cell > Dp.Hairline) {
                    ((available + gap) / (slot + gap)).toInt().coerceAtLeast(1)
                } else {
                    1
                }
                SideEffect { shell.setRowsPerPage(rows) }
                val libraryFailed = model.unavailable &&
                    model.panel?.level == PanelLevel.Library &&
                    model.panel?.side == Side.Left
                if (libraryFailed) {
                    Unavailable()
                } else {
                    PagedGrid(
                        app = app,
                        screen = screen,
                        cell = cell,
                        gap = gap,
                        rows = rows,
                        scale = scale,
                        showTitle = showTitles,
                        usesBoth = game?.occupiesBothDisplays == true && session.bothScreensFree(),
                    )
                }
            }
        }
        Panels(app, screen, scale) { clusterHeight = it }
    }
}

@Composable
private fun Panels(
    app: FoldcadeApp,
    screen: HostScreen,
    scale: Float,
    onClusterHeight: (Dp) -> Unit = {},
) {
    val model = app.shell.model
    val dialogHere = model.dialog?.screen == screen
    val leftOpen = !dialogHere && model.panel?.side == Side.Left && model.panel?.screen == screen
    val rightOpen = !dialogHere && model.panel?.side == Side.Right && model.panel?.screen == screen
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
                onClosedHeight = onClusterHeight,
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
    val theme = foldTheme()
    val progress = motionFloat(
        target = if (open) 1f else 0f,
        spec = if (open) Motion.arrive(Motion.durationTravel, scale) else Motion.leave(Motion.durationTravel, scale),
    )
    val live = app.shell.model.panel?.takeIf { it.side == Side.Left && it.screen == screen }
    var shown by remember { mutableStateOf<SidePanel?>(null) }
    if (live != null) shown = live
    val panel = shown
    if (progress <= 0f || panel == null) return
    Column(
        Modifier
            .fillMaxHeight()
            .width(openWidth * progress)
            .background(theme.surface)
            .padding(px(24f)),
        verticalArrangement = Arrangement.spacedBy(px(8f)),
    ) {
        val libraryFailed = panel.level == PanelLevel.Library && app.shell.model.unavailable
        if (libraryFailed) {
            Unavailable()
        } else {
            PanelRows(app, screen, panel, progress, interactive = open)
        }
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
    onClosedHeight: (Dp) -> Unit,
) {
    val theme = foldTheme()
    val progress = motionFloat(
        target = if (open) 1f else 0f,
        spec = if (open) Motion.arrive(Motion.durationTravel, scale) else Motion.leave(Motion.durationTravel, scale),
    )
    var cluster by remember { mutableStateOf(IntSize.Zero) }
    val live = app.shell.model.panel?.takeIf { it.side == Side.Right && it.screen == screen }
    var shown by remember { mutableStateOf<SidePanel?>(null) }
    if (live != null) shown = live
    val density = LocalDensity.current
    val grown = progress > 0f && cluster != IntSize.Zero
    val width = if (grown) lerp(with(density) { cluster.width.toDp() }, openWidth, progress) else null
    val height = if (grown) lerp(with(density) { cluster.height.toDp() }, openHeight, progress) else null
    Column(
        Modifier
            .onSizeChanged { size ->
                if (!open && progress == 0f) onClosedHeight(with(density) { size.height.toDp() })
            }
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
        val panel = shown
        if (progress > 0f && panel != null) {
            PanelRows(app, screen, panel, progress, interactive = open)
        }
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
    val theme = foldTheme()
    val context = LocalContext.current
    var status by remember { mutableStateOf(readDeviceStatus(context)) }
    LaunchedEffect(context) {
        while (true) {
            status = readDeviceStatus(context)
            delay(millisUntilNextMinute(System.currentTimeMillis()))
        }
    }
    Row(
        modifier.focusStroke(focused).hostPress(onClick),
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
private fun PanelRows(
    app: FoldcadeApp,
    screen: HostScreen,
    panel: SidePanel,
    progress: Float,
    interactive: Boolean,
) {
    val theme = foldTheme()
    val rows = panelRows(panel, app.shell.model)
    Column(Modifier.graphicsLayer { alpha = progress }, verticalArrangement = Arrangement.spacedBy(px(8f))) {
        rows.forEachIndexed { index, row ->
            val focused = panel.index == index
            val step: () -> Unit = {
                val shell = app.shell
                val current = shell.model.panel?.index ?: panel.index
                val delta = index - current
                val meaning = if (delta > 0) Meaning.MoveDown else Meaning.MoveUp
                if (delta == 0) {
                    shell.onMeaning(Meaning.Activate, screen)
                } else {
                    repeat(abs(delta)) { shell.onMeaning(meaning, screen) }
                }
                Unit
            }
            if (row is Row.MusicVolume) {
                MusicVolumeRow(
                    label = rowLabel(row, app.shell.model),
                    volume = app.shell.model.music.volume,
                    focused = focused,
                    interactive = interactive,
                    onStep = step,
                    onVolume = app.shell::setMusicVolume,
                )
            } else {
                val press = if (interactive) Modifier.hostPress(step) else Modifier
                BasicText(
                    text = rowLabel(row, app.shell.model),
                    modifier = Modifier
                        .focusStroke(focused)
                        .then(press)
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
}

@Composable
private fun ChromeRow(app: FoldcadeApp, screen: HostScreen) {
    val theme = foldTheme()
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
        val hint = if (model.connectOpen) {
            connectHint(connectFields().getOrElse(model.connectIndex) { ConnectField.Origin })
        } else {
            hintFor(place)
        }
        if (hint != null) {
            BasicText(text = hint, style = text(theme.muted, TypeRamp.hint, theme))
        }
    }
}

@Composable
private fun ChromeButton(label: String, focused: Boolean, onClick: () -> Unit) {
    val theme = foldTheme()
    BasicText(
        text = label,
        modifier = Modifier.focusStroke(focused).hostPress(onClick).padding(px(8f)),
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
    val widthPx = with(LocalDensity.current) { width.toPx() }
    val outset = focusOutset(cell)
    Box(
        Modifier
            .requiredWidth(width + outset * 2)
            .graphicsLayer { this.alpha = alpha }
            .clipToBounds()
            .padding(outset),
    ) {
        val low = floor(position.value).toInt()
        val high = ceil(position.value).toInt()
        for (drawn in low..high) {
            val dx = ((drawn - position.value) * widthPx).roundToInt()
            Box(Modifier.offset { IntOffset(dx, 0) }) {
                Grid(app, screen, cell, gap, rows, drawn, showTitle)
            }
        }
    }
    if (usesBoth) {
        BasicText(text = Copy.usesBothScreens, style = text(foldTheme().muted, TypeRamp.hint, foldTheme()))
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
    val radius = cell * foldTheme().iconRadius
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
                        val focused = shell.model.dialog == null &&
                            shell.model.panel == null &&
                            focus.chrome == null &&
                            focus.cellIndex == index
                        Cell(
                            title = game?.title ?: "",
                            mark = game?.mark,
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
    mark: String?,
    showTitle: Boolean,
    focused: Boolean,
    size: Dp,
    corner: Dp,
    onClick: () -> Unit,
) {
    val theme = foldTheme()
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
        val accent = mark?.let { markGlyph(it)?.accent }
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    clip = false
                    scaleX = drawn
                    scaleY = drawn
                }
                .focusStroke(focused, corner, accent)
                .hostPress(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(corner))
                    .background(theme.background),
            )
            if (mark != null && markGlyph(mark) != null) {
                MarkIcon(mark, theme.artScale)
            } else {
                BasicText(
                    text = monogram(title),
                    modifier = Modifier.scale(theme.artScale),
                    style = text(theme.onBackground, TypeRamp.heroTitle, theme),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        if (showTitle) {
            BasicText(
                text = title,
                modifier = Modifier
                    .padding(top = focusOutset(size))
                    .width(size),
                style = text(theme.onBackground, TypeRamp.gridLabel, theme).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DialogLayer(
    app: FoldcadeApp,
    dialog: DialogState?,
    screen: HostScreen,
    scale: Float,
    onEffect: (Effect?) -> Unit,
) {
    val visible = dialog != null && dialog.screen == screen
    var shown by remember { mutableStateOf(dialog) }
    if (visible) shown = dialog
    val alpha = motionFloat(
        target = if (visible) 1f else 0f,
        spec = if (visible) Motion.arrive(Motion.durationShort, scale) else Motion.leave(Motion.durationShort, scale),
    )
    val card = shown
    if (card != null && alpha > 0f) {
        Box(Modifier.graphicsLayer { this.alpha = alpha }) { DialogCard(app, card, onEffect) }
    }
}

@Composable
private fun DialogCard(
    app: FoldcadeApp,
    dialog: DialogState,
    onEffect: (Effect?) -> Unit,
) {
    val theme = foldTheme()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(px(Metrics.dialogInsetPx)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
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
                        modifier = Modifier
                            .focusStroke(dialog.index == index)
                            .hostPress {
                                onEffect(app.shell.touchDialog(index, dialog.screen))
                            }
                            .padding(px(8f)),
                        style = text(theme.onBackground, TypeRamp.dialogBody, theme),
                    )
                }
            }
        }
    }
}

@Composable
private fun Unavailable() {
    val theme = builtInTheme()
    BasicText(text = Copy.unavailable, style = text(theme.onBackground, TypeRamp.dialogTitle, theme))
}

@Composable
private fun ConnectScreen(
    app: FoldcadeApp,
    screen: HostScreen,
    onEffect: (Effect?) -> Unit,
) {
    val theme = foldTheme()
    val model = app.shell.model
    val fields = connectFields()
    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(px(Metrics.dialogInsetPx)),
        verticalArrangement = Arrangement.spacedBy(px(16f)),
    ) {
        BasicText(text = Copy.connectRomm, style = text(theme.onBackground, TypeRamp.dialogTitle, theme))
        BasicText(text = "Server", style = text(theme.muted, TypeRamp.hint, theme))
        BasicTextField(
            value = model.connectOrigin,
            onValueChange = { app.shell.editOrigin(it) },
            singleLine = true,
            textStyle = text(theme.onBackground, TypeRamp.dialogBody, theme),
            cursorBrush = cursorBrush(theme),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .focusStroke(focused(model.connectIndex, ConnectField.Origin))
                .onFocusChanged { state ->
                    if (state.isFocused) app.shell.touchConnect(fields.indexOf(ConnectField.Origin), screen)
                }
                .padding(px(8f)),
        )
        val warning = model.connectWarning
        if (warning != null) {
            BasicText(text = warning, style = text(theme.onBackground, TypeRamp.dialogBody, theme))
        }
        val hint = model.connectHint
        if (hint != null) {
            BasicText(text = hint, style = text(theme.onBackground, TypeRamp.dialogBody, theme))
        }
        BasicText(text = Copy.clientApiToken, style = text(theme.muted, TypeRamp.hint, theme))
        BasicTextField(
            value = app.shell.connectToken,
            onValueChange = { app.shell.editToken(it) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = text(theme.onBackground, TypeRamp.dialogBody, theme),
            cursorBrush = cursorBrush(theme),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .focusStroke(focused(model.connectIndex, ConnectField.Token))
                .onFocusChanged { state ->
                    if (state.isFocused) app.shell.touchConnect(fields.indexOf(ConnectField.Token), screen)
                }
                .padding(px(8f)),
        )
        BasicText(
            text = Copy.saveToken,
            modifier = Modifier
                .focusStroke(focused(model.connectIndex, ConnectField.Save))
                .hostPress { onEffect(app.shell.touchConnect(fields.indexOf(ConnectField.Save), screen)) }
                .padding(px(8f)),
            style = text(theme.onBackground, TypeRamp.dialogBody, theme),
        )
        BasicText(text = Copy.rommTokenHelp, style = text(theme.muted, TypeRamp.dialogBody, theme))
    }
}

private fun focused(index: Int, field: ConnectField): Boolean {
    val fields = connectFields()
    return fields[index.coerceIn(fields.indices)] == field
}

@Composable
private fun Modifier.hostPress(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .focusProperties { canFocus = false }
        .clickable(
            interactionSource = source,
            indication = null,
            onClick = onClick,
        )
}

@Composable
private fun foldTheme(): app.foldcade.language.Theme = LocalFoldTheme.current.theme

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
private fun focusOutset(cell: Dp): Dp =
    cell * ((Motion.scaleFocus - 1f) / 2f) + px(36f)

@Composable
private fun Modifier.focusStroke(
    focused: Boolean,
    corner: Dp = Dp.Hairline,
    accent: Color? = null,
): Modifier {
    val color = accent ?: foldTheme().focus
    return this.graphicsLayer { clip = false }.drawBehind {
        if (!focused) return@drawBehind
        val reach = size.maxDimension * 0.78f
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to color.copy(alpha = 0.7f),
                    0.38f to color.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                ),
                center = center,
                radius = reach,
            ),
            radius = reach,
            center = center,
        )
    }
}
