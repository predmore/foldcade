package app.foldcade.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.foldcade.Displays
import app.foldcade.FoldcadeApp
import app.foldcade.FoldcadeHomeActivity
import app.foldcade.api.Panel
import app.foldcade.api.Surface
import app.foldcade.language.ConnectField
import app.foldcade.language.Copy
import app.foldcade.language.HomeGrid
import app.foldcade.language.homeGridLabel
import app.foldcade.language.DialogButton
import app.foldcade.language.Effect
import app.foldcade.language.DialogState
import app.foldcade.language.EmptyGrid
import app.foldcade.language.GridKind
import app.foldcade.language.HeroBlend
import app.foldcade.language.HeroItem
import app.foldcade.language.HeroSubject
import app.foldcade.language.HintActions
import app.foldcade.language.HintPlace
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.Row
import app.foldcade.language.Metrics
import app.foldcade.language.MoonlightImportSheet
import app.foldcade.language.MoonlightSheetTarget
import app.foldcade.language.moonlightSheetSections
import app.foldcade.language.Motion
import app.foldcade.language.MotionSpeed
import app.foldcade.language.PanelLevel
import app.foldcade.language.Side
import app.foldcade.language.SidePanel
import app.foldcade.language.TypeRamp
import app.foldcade.language.GlowFalloff
import app.foldcade.language.builtInTheme
import app.foldcade.language.connectFields
import app.foldcade.language.connectHint
import app.foldcade.language.hintFor
import app.foldcade.language.letterOfKey
import app.foldcade.language.cursorBrush
import app.foldcade.language.displayOrder
import app.foldcade.language.heroCopy
import app.foldcade.language.heroCrossfadeActive
import app.foldcade.language.lastPlayedLine
import app.foldcade.language.monogram
import app.foldcade.language.panelRows
import app.foldcade.language.approximatePlayNote
import app.foldcade.language.playedLine
import app.foldcade.language.quickTileColumns
import app.foldcade.language.rowLabel
import app.foldcade.language.retargetHero
import app.foldcade.language.rowText
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
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
    val model = app.shell.model
    val screen = when (panel) {
        Panel.Top -> HostScreen.Top
        Panel.Bottom -> HostScreen.Bottom
        null -> null
    }
    val ownsBottom = session.surfaceOn(Panel.Bottom) != null
    val menuVisible = model.panel != null &&
        model.panel?.screen == HostScreen.Top &&
        model.dialog?.screen != HostScreen.Top
    val blurHere = screen == HostScreen.Bottom &&
        ownsBottom &&
        menuVisible &&
        model.dialog?.screen != HostScreen.Bottom
    val heldBlur = if (blurHere) app.islandHold?.progress else null
    val blurTarget = heldBlur ?: if (blurHere) 1f else 0f
    val blur = motionFloat(
        target = blurTarget,
        spec = if (blurTarget >= 1f) Motion.arrive(Motion.durationIsland, scale) else Motion.leave(Motion.durationIsland, scale),
        snap = heldBlur != null,
    )
    // A closed menu composes neither the blur nor the scrim. A graphics layer
    // left over the library, even at zero alpha, keeps the accessibility dump empty.
    CompositionLocalProvider(LocalFoldTheme provides paint) {
        Box(Modifier.fillMaxSize().background(paint.theme.background)) {
            if (blurHere) {
                Box(Modifier.fillMaxSize().menuBlur(blur).menuDim(blur)) {
                    PanelBody(activity, panel, screen, scale)
                }
            } else {
                PanelBody(activity, panel, screen, scale)
            }
            if (panel != null && screen != null) {
                DialogLayer(app, model.dialog, screen, scale, activity::dispatch)
                MoonlightImportLayer(app, model.moonlightSheet, screen, scale)
            }
        }
    }
}

@Composable
private fun PanelBody(
    activity: FoldcadeHomeActivity,
    panel: Panel?,
    screen: HostScreen?,
    scale: Float,
) {
    val app = activity.application as FoldcadeApp
    val session = app.store.session
    val model = app.shell.model
    Backdrop(
        motion = model.backgroundMotion,
        speed = model.motionSpeed,
        animatorScale = scale,
        running = activity.shellVisible && session.bothScreensFree(),
    )
    if (panel == null || screen == null) return
    val connectHere = model.connectOpen && model.connectScreen == screen
    TravelFade(target = session.surfaceOn(panel), scale = scale) { shown ->
        when (shown) {
            null -> Unit
            Surface.Hero -> if (connectHere) Unit else Hero(app, screen, scale, activity::dispatch)
            Surface.Picker -> Picker(app, screen, scale, activity::dispatch)
        }
    }
    if (connectHere) ConnectScreen(app, screen, activity::dispatch)
}

/** Dim painted with the content, not a second full-screen node on top of it. */
private fun Modifier.menuDim(progress: Float): Modifier = drawWithContent {
    drawContent()
    val dim = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.32f else 0.55f
    drawRect(Color.Black.copy(alpha = dim * progress.coerceIn(0f, 1f)))
}

/** Blur only while the radius is visible. A zero-radius graphics layer still hides nodes. */
private fun Modifier.menuBlur(progress: Float): Modifier {
    val radius = progress * 28f
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius < 0.5f) return this
    return graphicsLayer {
        renderEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
    }
}

private object SteadyMotion : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

@Composable
private fun motionFloat(target: Float, spec: FiniteAnimationSpec<Float>, snap: Boolean = false): Float {
    val anim = remember { Animatable(target) }
    LaunchedEffect(target, snap) {
        if (snap) anim.snapTo(target)
        else withContext(SteadyMotion) { anim.animateTo(target, spec) }
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
        incoming.snapTo(1f)
    }
    val previous = back
    val moving = previous != null || incoming.value < 0.999f
    if (!moving) {
        content(front)
        return
    }
    // Only while the surface is changing. Settled content has no graphics layer.
    Box(
        Modifier
            .fillMaxSize()
            .focusProperties { canFocus = false }
            .clearAndSetSemantics { },
    ) {
        if (previous != null) {
            Box(Modifier.graphicsLayer { alpha = outgoing.value }) { content(previous) }
        }
        Box(Modifier.graphicsLayer { alpha = incoming.value }) { content(front) }
    }
}

@Composable
private fun Hero(app: FoldcadeApp, screen: HostScreen, scale: Float, onEffect: (Effect?) -> Unit) {
    val theme = foldTheme()
    val model = app.shell.model
    val subject = app.shell.focusedHero()
    val cellFocused = model.dialog == null && model.panel == null && !model.connectOpen && model.focus.chrome == null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inset = px(Metrics.heroInsetPx)
        val artHeight = maxHeight * Metrics.heroArtFraction
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.48f to Color.Transparent,
                                0.62f to theme.background.copy(alpha = 0.78f),
                                0.74f to theme.background.copy(alpha = 0.96f),
                                1f to theme.background,
                            ),
                        ),
                    )
                },
        )
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    start = inset,
                    end = inset,
                    top = if (screen == HostScreen.Top) inset + 48.dp else inset,
                ),
        ) {
            HeroCrossfade(
                target = subject,
                speed = model.motionSpeed,
                animatorScale = scale,
                same = { left, right -> left?.key == right?.key },
            ) { shown ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(artHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    if (shown is HeroSubject.Item) HeroArt(shown.item)
                }
            }
            if (subject != null) {
                HeroLabel(app, subject, cellFocused)
            }
        }
        TopIslands(app, screen, scale) { panelState, progress, interactive ->
            PanelRows(app, screen, panelState, progress, interactive, onEffect)
        }
    }
}

/**
 * Crossfades the artwork onto the newest focus. A second change before this
 * finishes retargets the same two layers instead of starting another fade.
 * The name and details are not in these layers: two labels in one place
 * cannot be read. Off, and remove-animations, fade over the short duration
 * with no slide. Once the fade is done the layer is not composed at all.
 */
@Composable
private fun <T> HeroCrossfade(
    target: T?,
    speed: MotionSpeed,
    animatorScale: Float,
    same: (T?, T?) -> Boolean,
    content: @Composable (T) -> Unit,
) {
    val travelMillis = Motion.heroDuration(speed, animatorScale)
    val fadeOnly = Motion.heroFadeOnly(speed, animatorScale)
    var blend by remember {
        mutableStateOf(
            HeroBlend(
                front = target,
                back = null,
                frontAlpha = if (target == null) 0f else 1f,
                backAlpha = 0f,
            ),
        )
    }
    val incoming = remember { Animatable(blend.frontAlpha) }
    val outgoing = remember { Animatable(blend.backAlpha) }
    LaunchedEffect(target, travelMillis, fadeOnly) {
        val latest = blend.copy(frontAlpha = incoming.value, backAlpha = outgoing.value)
        val next = retargetHero(latest, target, same)
        val sameFront = same(next.front, blend.front)
        if (sameFront && blend.back == null && incoming.value >= 0.999f) {
            if (next.front != blend.front) {
                blend = next.copy(frontAlpha = 1f, back = null, backAlpha = 0f)
            }
            return@LaunchedEffect
        }
        incoming.snapTo(next.frontAlpha)
        outgoing.snapTo(next.backAlpha)
        blend = next
        val arrive = Motion.arrive(travelMillis, 1f)
        val leave = Motion.leave(travelMillis, 1f)
        withContext(SteadyMotion) {
            coroutineScope {
                launch { incoming.animateTo(if (next.front == null) 0f else 1f, arrive) }
                launch { outgoing.animateTo(0f, leave) }
            }
        }
        if (same(blend.front, next.front)) {
            incoming.snapTo(if (next.front == null) 0f else 1f)
            outgoing.snapTo(0f)
            blend = blend.copy(back = null, backAlpha = 0f, frontAlpha = incoming.value)
        }
    }
    val previous = blend.back
    val current = blend.front
    val active = heroCrossfadeActive(
        hasFront = current != null,
        hasBack = previous != null,
        frontAlpha = incoming.value,
        backAlpha = outgoing.value,
    )
    if (!active) {
        if (current != null) content(current)
        return
    }
    // The fading art sits behind the label. It takes no focus or touch, and it
    // is not in the accessibility tree. The layer is absent once the fade ends.
    Box(
        Modifier
            .fillMaxWidth()
            .zIndex(-1f)
            .focusProperties { canFocus = false }
            .clearAndSetSemantics { },
    ) {
        if (previous != null) {
            Box(Modifier.fadingHero(outgoing.value, fadeOnly)) { content(previous) }
        }
        if (current != null) {
            Box(Modifier.fadingHero(incoming.value, fadeOnly)) { content(current) }
        }
    }
}

/**
 * One name and one detail line for the focus that just arrived.
 * The artwork behind it may still be crossfading.
 */
@Composable
private fun HeroLabel(app: FoldcadeApp, shown: HeroSubject, cellFocused: Boolean) {
    val theme = foldTheme()
    val copy = heroCopy(shown)
    BasicText(
        text = copy.title,
        style = text(theme.onBackground, TypeRamp.heroTitle, theme),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (copy.detail.isNotEmpty()) {
        val hint = shown is HeroSubject.Item && shown.item.emptyShelfHint && cellFocused
        ShelfMeta(line = copy.detail, hintFocused = hint)
    }
    val availability = (shown as? HeroSubject.Item)?.item?.availability
    if (availability != null) {
        BasicText(
            text = availability,
            style = text(theme.muted, TypeRamp.availability, theme),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (shown is HeroSubject.Item) {
        PlayFacts(app, shown.item.key)
    }
}

private fun Modifier.fadingHero(alpha: Float, fadeOnly: Boolean): Modifier =
    focusProperties { canFocus = false }
        .clearAndSetSemantics { }
        .heroArrival(alpha, fadeOnly)

private fun Modifier.heroArrival(alpha: Float, fadeOnly: Boolean): Modifier = graphicsLayer {
    this.alpha = alpha
    val drawn = Motion.heroScale(alpha, fadeOnly)
    scaleX = drawn
    scaleY = drawn
    translationY = Motion.heroSlidePx(alpha, fadeOnly)
}

@Composable
private fun HeroArt(item: HeroItem) {
    val theme = foldTheme()
    val icon = launcherIcon(item.packageName)
    val accent = item.mark?.let { markGlyph(it)?.accent } ?: theme.focus
    Box(
        Modifier
            .fillMaxHeight()
            .aspectRatio(1f)
            .drawBehind {
                val reach = size.minDimension * 0.62f
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to accent.copy(alpha = 0.45f),
                            0.42f to accent.copy(alpha = 0.16f),
                            1f to Color.Transparent,
                        ),
                        center = center,
                        radius = reach,
                    ),
                    radius = reach,
                    center = center,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val mark = item.mark
        when {
            icon != null -> Image(
                bitmap = icon,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(theme.artScale),
                contentScale = ContentScale.Fit,
            )
            mark != null && markGlyph(mark) != null -> MarkIcon(mark, 0.86f)
            else -> BasicText(
                text = monogram(item.title),
                modifier = Modifier.scale(4f),
                style = text(theme.onBackground, TypeRamp.heroTitle, theme),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inset = maxWidth * Metrics.insetFraction
        val gap = maxWidth * Metrics.gapFraction
        val inner = maxWidth - inset * 2
        val rough = (inner - gap * (Metrics.columns - 1)) / Metrics.columns
        // Room outside each cell so the focus glow reaches black before the pager clip.
        val pad = focusOutset(rough) + px(FOCUS_GLOW_PAD)
        val cell = (inner - pad * 2 - gap * (Metrics.columns - 1)) / Metrics.columns
        val titlePx = rememberTextMeasurer().measure(
            text = "Ag",
            style = text(theme.onBackground, TypeRamp.gridLabel, theme),
            maxLines = 1,
        ).size.height
        val titleLine = with(LocalDensity.current) { titlePx.toDp() }
        val detailGame = game?.takeIf {
            model.panel == null && model.dialog == null && !model.connectOpen
        }
        val clearance = px(Metrics.chromeClearancePx)
        // Keeps the last tile label inside the screen, above the clip.
        val labelSafe = px(28f)
        val showingHome = !model.libraryGrid && model.homeGrid == HomeGrid.StandIns
        val bottomHome = showingHome && screen == HostScreen.Bottom
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = inset)
                .padding(top = inset, bottom = inset + labelSafe),
        ) {
            if (!bottomHome) {
                if (screen == HostScreen.Top) {
                    Spacer(Modifier.height(48.dp))
                }
                ChromeRow(app)
                Spacer(Modifier.height(clearance))
                if (detailGame != null && screen == HostScreen.Top) {
                    GameDetail(app, detailGame.id)
                }
            }
            AllBar(shell, screen, onEffect)
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(model.panel, model.dialog, model.connectOpen, model.moonlightSheet, shell.homeEditing(), showingHome) {
                        if (showingHome) return@pointerInput
                        if (model.panel != null || model.dialog != null || model.connectOpen || model.moonlightSheet != null) {
                            return@pointerInput
                        }
                        if (shell.homeEditing()) return@pointerInput
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
                val available = maxHeight.coerceAtLeast(0.dp)
                val homeRows = 3
                val titleGuess = px(20f) + titleLine
                val fromHeight = if (homeRows > 0) {
                    ((available - gap * (homeRows - 1)) / homeRows) - titleGuess
                } else {
                    cell
                }
                val fromWidth = if (maxWidth > Dp.Hairline) {
                    (maxWidth - gap * (Metrics.columns - 1)) / Metrics.columns
                } else {
                    cell
                }
                val homeCell = minOf(fromHeight, fromWidth).coerceAtLeast(px(56f))
                val gridCell = if (bottomHome) homeCell else cell
                val gridPad = if (bottomHome) px(8f) else pad
                val titleBlock = focusOutset(gridCell) + px(12f) + titleLine
                val slot = if (showTitles) gridCell + titleBlock else gridCell
                val rows = if (bottomHome) {
                    homeRows
                } else if (gridCell > Dp.Hairline && slot > Dp.Hairline) {
                    ((available - gridPad * 2 + gap) / (slot + gap)).toInt().coerceAtLeast(1)
                } else {
                    1
                }
                SideEffect { shell.setRowsPerPage(rows) }
                val libraryFailed = model.unavailable &&
                    model.panel?.level == PanelLevel.Library &&
                    model.panel?.side == Side.Left
                val emptyTitle = emptyTitle(model)
                if (libraryFailed) {
                    Unavailable()
                } else if (model.libraryGrid && emptyTitle != null) {
                    EmptyLibrary(app, screen, emptyTitle, emptyActions(model))
                } else if (!model.libraryGrid && model.homeGrid != HomeGrid.StandIns && model.count == 0) {
                    BasicText(
                        text = emptyShelf(model.homeGrid),
                        style = text(theme.muted, TypeRamp.dialogBody, theme),
                    )
                } else {
                    PagedGrid(
                        app = app,
                        screen = screen,
                        cell = gridCell,
                        gap = gap,
                        pad = gridPad,
                        rows = rows,
                        scale = scale,
                        showTitle = showTitles,
                        usesBoth = game?.occupiesBothDisplays == true && session.bothScreensFree(),
                        followDrag = bottomHome,
                    )
                }
            }
            if (bottomHome) {
                PageDots(count = model.count, rows = 3, index = model.focus.cellIndex)
                val hint = shell.homeHint()
                if (hint != null) {
                    BasicText(
                        text = hint,
                        style = text(theme.muted, TypeRamp.hint, theme),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HomeHintRow(model)
            }
        }
        TopIslands(app, screen, scale) { panelState, progress, interactive ->
            PanelRows(app, screen, panelState, progress, interactive, onEffect)
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
    onEffect: (Effect?) -> Unit,
) {
    val theme = foldTheme()
    val rows = panelRows(panel, app.shell.model)
    val tileStart = rows.indexOfFirst { it is Row.QuickTile }
    Column(Modifier.graphicsLayer { alpha = progress }, verticalArrangement = Arrangement.spacedBy(px(8f))) {
        rows.forEachIndexed { index, row ->
            if (row is Row.QuickTile) return@forEachIndexed
            val focused = panel.index == index
            val step: () -> Unit = { onEffect(app.shell.touchPanel(index, screen)) }
            val parts = rowText(row, app.shell.model)
            if (row is Row.MusicVolume) {
                MusicVolumeRow(
                    label = parts.label,
                    value = parts.value.orEmpty(),
                    volume = app.shell.model.music.volume,
                    focused = focused,
                    interactive = interactive,
                    onStep = step,
                    onVolume = app.shell::setMusicVolume,
                    modifier = Modifier.fillMaxWidth().rowHighlight(focused),
                )
            } else {
                val press = if (interactive) Modifier.hostPress(step) else Modifier
                Row(
                    Modifier
                        .fillMaxWidth()
                        .rowHighlight(focused)
                        .focusStroke(focused)
                        .then(press)
                        .padding(start = px(16f), end = px(8f), top = px(8f), bottom = px(8f)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = parts.label,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = text(
                            if (focused) theme.onBackground else theme.muted,
                            TypeRamp.sideRow,
                            theme,
                        ),
                    )
                    val value = parts.value
                    if (!value.isNullOrEmpty()) {
                        BasicText(
                            text = value,
                            modifier = Modifier.padding(start = px(16f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = text(
                                if (focused) theme.focus else theme.onBackground,
                                TypeRamp.sideRow,
                                theme,
                            ).copy(textAlign = TextAlign.End),
                        )
                    }
                }
            }
        }
        if (tileStart >= 0) {
            QuickTiles(
                app = app,
                screen = screen,
                rows = rows,
                tileStart = tileStart,
                focusedIndex = panel.index,
                interactive = interactive,
                onEffect = onEffect,
            )
        }
    }
}

@Composable
private fun QuickTiles(
    app: FoldcadeApp,
    screen: HostScreen,
    rows: List<Row>,
    tileStart: Int,
    focusedIndex: Int,
    interactive: Boolean,
    onEffect: (Effect?) -> Unit,
) {
    val theme = foldTheme()
    val tiles = rows.subList(tileStart, rows.size)
    Column(verticalArrangement = Arrangement.spacedBy(px(8f))) {
        tiles.chunked(quickTileColumns).forEachIndexed { rowIndex, chunk ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(px(8f)),
            ) {
                chunk.forEachIndexed { column, row ->
                    val index = tileStart + rowIndex * quickTileColumns + column
                    val focused = focusedIndex == index
                    val corner = 8.dp
                    val press = if (interactive) {
                        Modifier.hostPress { onEffect(app.shell.touchPanel(index, screen)) }
                    } else {
                        Modifier
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .rowHighlight(focused)
                            .focusStroke(focused, corner)
                            .tileEdge(focused = focused, corner = corner)
                            .then(press)
                            .padding(px(8f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = rowLabel(row, app.shell.model),
                            style = text(
                                if (focused) theme.onBackground else theme.muted,
                                TypeRamp.sideRow,
                                theme,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(quickTileColumns - chunk.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Modifier.tileEdge(focused: Boolean, corner: Dp): Modifier {
    val color = foldTheme().muted
    return drawWithContent {
        drawContent()
        if (focused) return@drawWithContent
        val stroke = 2f
        val radius = corner.toPx()
        drawRoundRect(
            color = color,
            style = Stroke(width = stroke),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

@Composable
private fun GameDetail(app: FoldcadeApp, gameId: String) {
    PlayFacts(app, gameId)
}

/** Total play time and last played. The hero and the game detail both use this. */
@Composable
private fun PlayFacts(app: FoldcadeApp, gameId: String) {
    val shown = app.plays.run {
        stamp
        shown(gameId)
    }
    val theme = foldTheme()
    val now = System.currentTimeMillis()
    Column {
        val note = approximatePlayNote(shown.approximate)
        if (note != null) {
            BasicText(
                text = note,
                style = text(theme.muted, TypeRamp.availability, theme),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicText(
            text = playedLine(shown.activeMillis),
            style = text(theme.muted, TypeRamp.heroMeta, theme),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            text = lastPlayedLine(shown.lastPlayedMillis, now, ZoneId.systemDefault()),
            style = text(theme.muted, TypeRamp.availability, theme),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChromeRow(app: FoldcadeApp) {
    val theme = foldTheme()
    val shell = app.shell
    val model = shell.model
    val game = shell.focusedGame()
    val shelfOpen = model.panel == null && model.dialog == null && model.moonlightSheet == null && !model.connectOpen
    Column {
        val shelf = homeGridLabel(model.homeGrid)
        if (shelf != null) {
            BasicText(text = shelf, style = text(theme.onBackground, TypeRamp.sideRow, theme))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(12f))) {
            if (shelfOpen && game?.emptyShelfHint == true) {
                ShelfMeta(
                    line = game.shortText,
                    hintFocused = model.focus.chrome == null,
                )
            } else if (game?.occupiesBothDisplays == true && app.store.session.bothScreensFree() && model.panel == null) {
                BasicText(text = Copy.usesBothScreens, style = text(theme.muted, TypeRamp.hint, theme))
            }
        }
        val actions = if (model.connectOpen) {
            connectHint(connectFields().getOrElse(model.connectIndex) { ConnectField.Origin })
        } else {
            hintFor(
                when {
                    model.moonlightSheet != null || model.dialog != null -> HintPlace.Dialog
                    model.panel != null -> HintPlace.Menu
                    !model.atLibraryRoot -> HintPlace.InsidePlatform
                    else -> HintPlace.RootGrid
                },
            )
        }
        HintRow(model, actions)
    }
}

/** Page marks under the home grid. One dot per screen of columns. */
@Composable
private fun PageDots(count: Int, rows: Int, index: Int) {
    val pageSize = (Metrics.columns * rows).coerceAtLeast(1)
    val pages = ((count + pageSize - 1) / pageSize).coerceAtLeast(1)
    val page = (index / pageSize).coerceIn(0, pages - 1)
    val theme = foldTheme()
    Row(
        Modifier.fillMaxWidth().padding(top = px(4f), bottom = px(8f)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pages) { dot ->
            Box(
                Modifier
                    .padding(horizontal = px(4f))
                    .size(if (dot == page) px(8f) else px(6f))
                    .background(
                        if (dot == page) theme.onBackground else theme.muted.copy(alpha = 0.45f),
                        CircleShape,
                    ),
            )
        }
    }
}

/** A Confirm, and Back when it does something, on its own row. */
@Composable
private fun HomeHintRow(model: app.foldcade.language.PickerModel) {
    val actions = if (model.connectOpen) {
        connectHint(connectFields().getOrElse(model.connectIndex) { ConnectField.Origin })
    } else {
        hintFor(
            when {
                model.dialog != null -> HintPlace.Dialog
                model.panel != null -> HintPlace.Menu
                !model.atLibraryRoot -> HintPlace.InsidePlatform
                else -> HintPlace.RootGrid
            },
        )
    }
    HintRow(model, actions)
}

/** One small letter glyph and a word, only for an action that currently does something. */
@Composable
private fun HintRow(
    model: app.foldcade.language.PickerModel,
    actions: HintActions?,
) {
    if (actions == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(px(16f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (actions.confirm) HintWord(model, model.faceMap.confirmKey, Copy.confirm)
        if (actions.back) HintWord(model, model.faceMap.backKey, Copy.back)
    }
}

@Composable
private fun HintWord(
    model: app.foldcade.language.PickerModel,
    keyCode: Int,
    label: String,
) {
    val letter = letterOfKey(keyCode) ?: return
    val held = when (letter) {
        app.foldcade.language.FaceLetter.A -> app.foldcade.language.PromptKey.FaceA
        app.foldcade.language.FaceLetter.B -> app.foldcade.language.PromptKey.FaceB
        app.foldcade.language.FaceLetter.X -> app.foldcade.language.PromptKey.FaceX
        app.foldcade.language.FaceLetter.Y -> app.foldcade.language.PromptKey.FaceY
    } in model.held
    val glyph = "ic_btn_${letter.name.lowercase()}"
    Row(
        horizontalArrangement = Arrangement.spacedBy(px(8f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PromptImage(if (held) "${glyph}_filled" else glyph, 24.dp)
        BasicText(text = label, style = text(foldTheme().muted, TypeRamp.hint, foldTheme()))
    }
}

@Composable
private fun PromptImage(name: String, size: Dp) {
    val context = LocalContext.current
    val id = remember(name) {
        context.resources.getIdentifier(name, "drawable", context.packageName)
    }
    if (id == 0) return
    Image(
        painter = painterResource(id),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}

/**
 * Shelf meta. An empty-shelf hint uses the focus color and the focus glow
 * while the controller is on that tile.
 */
@Composable
private fun ShelfMeta(line: String, hintFocused: Boolean) {
    val theme = foldTheme()
    BasicText(
        text = line,
        modifier = if (hintFocused) Modifier.focusStroke(true).padding(px(8f)) else Modifier,
        style = text(if (hintFocused) theme.focus else theme.muted, TypeRamp.heroMeta, theme),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AllBar(shell: app.foldcade.ShellController, screen: HostScreen, onEffect: (Effect?) -> Unit) {
    val chrome = shell.homeChrome() ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(px(12f))) {
        listOf(chrome.tab, chrome.sort, chrome.system, chrome.destination).forEachIndexed { index, label ->
            ChromeButton(
                label = label,
                focused = chrome.focused == index,
                onClick = { onEffect(shell.touchAllChrome(index, screen)) },
            )
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
    pad: Dp,
    rows: Int,
    scale: Float,
    showTitle: Boolean,
    usesBoth: Boolean,
    followDrag: Boolean = false,
) {
    val focus = app.shell.model.focus
    val pageSize = (Metrics.columns * rows).coerceAtLeast(1)
    val page = focus.cellIndex / pageSize
    val position = remember { Animatable(page.toFloat()) }
    var dragPages by remember { mutableStateOf(0f) }
    LaunchedEffect(page, scale) {
        dragPages = 0f
        withContext(SteadyMotion) {
            position.animateTo(page.toFloat(), Motion.arrive(Motion.durationTravel, scale))
        }
    }
    val reduced = Motion.reduced(scale)
    val shownPage = position.value - dragPages
    val alpha = if (reduced) (1f - abs(shownPage - page)).coerceIn(0.35f, 1f) else 1f
    val width = cell * Metrics.columns + gap * (Metrics.columns - 1)
    val widthPx = with(LocalDensity.current) { width.toPx() }
    val folderToken = app.shell.homeAnimToken()
    val folderPop = remember { Animatable(1f) }
    LaunchedEffect(folderToken, scale) {
        folderPop.snapTo(0.92f)
        withContext(SteadyMotion) {
            folderPop.animateTo(1f, Motion.arrive(Motion.durationShort, scale))
        }
    }
    val fading = reduced && abs(shownPage - page) > 0.001f
    val popping = folderPop.value != 1f
    Box(
        Modifier
            .requiredWidth(width + pad * 2)
            .then(
                if (fading || popping) {
                    Modifier.graphicsLayer {
                        if (fading) this.alpha = alpha
                        scaleX = folderPop.value
                        scaleY = folderPop.value
                    }
                } else {
                    Modifier
                },
            )
            .clipToBounds()
            .pointerInput(followDrag, app.shell.homeEditing(), widthPx) {
                if (!followDrag || app.shell.homeEditing() || widthPx <= 0f) return@pointerInput
                var walked = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount ->
                        walked += amount
                        dragPages = walked / widthPx
                    },
                    onDragEnd = {
                        val pages = dragPages
                        dragPages = 0f
                        walked = 0f
                        val meaning = when {
                            pages > 0.08f -> Meaning.PageTowardStart
                            pages < -0.08f -> Meaning.PageTowardEnd
                            else -> null
                        }
                        if (meaning != null) app.shell.onMeaning(meaning, screen)
                    },
                )
            }
            .padding(pad),
    ) {
        val low = floor(shownPage).toInt()
        val high = ceil(shownPage).toInt()
        for (drawn in low..high) {
            val dx = ((drawn - shownPage) * widthPx).roundToInt()
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
    val showingHome = !shell.model.libraryGrid && shell.model.homeGrid == HomeGrid.StandIns
    val order = displayOrder(shell.model)
    val radius = cell * foldTheme().iconRadius
    val editing = shell.homeEditing()
    val lifted = shell.homeLiftedIndex()
    val allOpen = shell.homeAllOpen()
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        val start = page * pageSize
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (column in 0 until Metrics.columns) {
                    val index = start + row * Metrics.columns + column
                    if (index >= shell.model.count) {
                        Box(Modifier.size(cell))
                    } else {
                        val source = if (showingHome) index else order.getOrElse(index) { index }
                        val face = if (showingHome) shell.homeFace(index) else null
                        val game = shell.tileFromOrder(source)
                        val focused = shell.model.dialog == null &&
                            shell.model.panel == null &&
                            shell.homeChrome()?.focused == null &&
                            focus.chrome == null &&
                            focus.cellIndex == index
                        val label = face?.section?.let { section -> "$section · ${game?.title.orEmpty()}" }
                            ?: game?.title.orEmpty()
                        val onHomeMark = face != null && allOpen && face.onGrid && !face.folder && !face.pinned
                        Cell(
                            title = label,
                            mark = game?.mark,
                            showTitle = showTitle && face?.empty != true,
                            focused = focused,
                            size = cell,
                            corner = radius,
                            icon = launcherIcon(game?.androidPackage),
                            favorite = game?.favorite == true,
                            empty = face?.empty == true,
                            lifted = lifted == index,
                            editing = editing,
                            marked = onHomeMark,
                            onClick = { shell.touchCell(index, screen) },
                            onDrag = if (editing) {
                                { meaning -> shell.dragHome(meaning) }
                            } else {
                                null
                            },
                            onPickUp = if (editing) {
                                { shell.pickUpHome(index) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

private const val FOCUS_GLOW_OVERFLOW = 64f
private const val REST_GLOW_OVERFLOW = 18f
private const val FOCUS_GLOW_PAD = 80f

@Composable
private fun emptyShelf(grid: HomeGrid): String = when (grid) {
    HomeGrid.AndroidGames -> Copy.noGames
    HomeGrid.Apps -> Copy.noApps
    HomeGrid.HiddenApps -> Copy.noHiddenApps
    HomeGrid.StandIns -> Copy.noGames
}

@Composable
private fun launcherIcon(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName == null) return@remember null
        val drawable = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: return@remember null
        runCatching { drawable.toBitmap().asImageBitmap() }.getOrNull()
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
    icon: ImageBitmap?,
    favorite: Boolean,
    empty: Boolean = false,
    lifted: Boolean = false,
    editing: Boolean = false,
    marked: Boolean = false,
    onClick: () -> Unit,
    onPickUp: (() -> Unit)? = null,
    onDrag: ((Meaning) -> Unit)? = null,
) {
    val theme = foldTheme()
    val kit = mark?.let { LocalFoldTheme.current.marks[it] }
    val animatorScale = Motion.animatorScale(LocalContext.current.contentResolver)
    val scaleTarget = when {
        lifted -> Motion.scaleFocus * Motion.scaleFocus
        focused -> Motion.scaleFocus
        else -> Motion.scaleRest
    }
    val drawn = motionFloat(
        target = scaleTarget,
        spec = if (focused || lifted) {
            Motion.arrive(Motion.durationFocus, animatorScale)
        } else {
            Motion.leave(Motion.durationFocus, animatorScale)
        },
    )
    val glowStops = remember { FloatArray(GlowFalloff.STOPS.size) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.zIndex(if (focused || lifted) 1f else 0f),
    ) {
        val accent = mark?.let { markGlyph(it)?.accent }
        // A scanned-folder tile has no line glyph. Focus fills it like the dialog pill.
        val focusCard = focused && accent == null && icon == null && !empty
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    clip = false
                    scaleX = drawn
                    scaleY = drawn
                }
                .drawBehind {
                    val glow = accent
                    val bounds = this.size
                    val cornerPx = corner.toPx()
                    if (focusCard) drawFocusCard(theme.focus, cornerPx)
                    if (glow != null) {
                        val half = min(bounds.width, bounds.height) * 0.5f
                        val overflow = if (focused) FOCUS_GLOW_OVERFLOW else REST_GLOW_OVERFLOW
                        val reach = half + overflow
                        val tightness = if (focused) GlowFalloff.TILE_TIGHTNESS else GlowFalloff.REST_TIGHTNESS
                        val rim = if (focused) GlowFalloff.TILE_RIM else GlowFalloff.REST_RIM
                        val peak = if (focused) GlowFalloff.TILE_PEAK else GlowFalloff.REST_PEAK
                        SoftGlow.fillStops(glowStops, tightness, rim)
                        drawRadialGlow(center, reach, glow, glowStops, peak)
                    }
                    if (glow != null || icon != null) {
                        drawRoundRect(
                            color = theme.background,
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                        )
                        val stroke = glow ?: theme.focus
                        val strokePx = if (focused) 9f else 2.5f
                        val inset = strokePx / 2f
                        drawRoundRect(
                            color = stroke.copy(alpha = if (focused) 1f else 0.42f),
                            topLeft = Offset(inset, inset),
                            size = Size(bounds.width - strokePx, bounds.height - strokePx),
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = Stroke(width = strokePx),
                        )
                    }
                    if (empty) {
                        drawRoundRect(
                            color = theme.muted.copy(alpha = 0.35f),
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = Stroke(width = 2.5f),
                        )
                    }
                    if (editing) {
                        val strokePx = if (focused || lifted) 9f else 3f
                        val inset = strokePx / 2f
                        drawRoundRect(
                            color = theme.focus.copy(alpha = if (focused || lifted) 1f else 0.45f),
                            topLeft = Offset(inset, inset),
                            size = Size(bounds.width - strokePx, bounds.height - strokePx),
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = Stroke(width = strokePx),
                        )
                    }
                }
                .then(
                    if (onPickUp != null && onDrag != null) {
                        Modifier.pointerInput(title) {
                            var walked = Offset.Zero
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    walked = Offset.Zero
                                    onPickUp()
                                },
                                onDrag = { _, amount ->
                                    walked += amount
                                    val step = size.toPx() * 0.45f
                                    val meaning = when {
                                        walked.x > step -> Meaning.MoveRight
                                        walked.x < -step -> Meaning.MoveLeft
                                        walked.y > step -> Meaning.MoveDown
                                        walked.y < -step -> Meaning.MoveUp
                                        else -> null
                                    }
                                    if (meaning != null) {
                                        walked = Offset.Zero
                                        onDrag(meaning)
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .hostPress(onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(theme.artScale),
                    contentScale = ContentScale.Fit,
                )
                if (favorite) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(px(8f))
                            .size(px(10f))
                            .background(theme.focus, CircleShape),
                    )
                }
            } else if (!empty) {
                val glyphAlpha = if (focused) 1f else 0.58f
                if (kit != null) {
                    Image(
                        bitmap = kit,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxSize(theme.artScale)
                            .graphicsLayer { alpha = glyphAlpha },
                        contentScale = ContentScale.Fit,
                    )
                } else if (mark != null && markGlyph(mark) != null) {
                    Box(Modifier.graphicsLayer { alpha = glyphAlpha }) {
                        MarkIcon(mark, theme.artScale)
                    }
                } else {
                    BasicText(
                        text = monogram(title),
                        modifier = Modifier.scale(theme.artScale).graphicsLayer { alpha = glyphAlpha },
                        style = text(
                            if (focusCard) theme.background else theme.onBackground,
                            TypeRamp.heroTitle,
                            theme,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            if (marked) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(px(8f))
                        .size(px(10f))
                        .background(theme.focus, CircleShape),
                )
            }
        }
        if (showTitle) {
            BasicText(
                text = title,
                modifier = Modifier
                    .padding(top = focusOutset(size) + px(12f))
                    .width(size),
                style = text(
                    if (focused) theme.onBackground else theme.muted,
                    TypeRamp.gridLabel,
                    theme,
                ).copy(textAlign = TextAlign.Center),
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
    // Drop the card with the model. A remembered card kept "GameNative is not installed"
    // on screen after the launch had already been dismissed.
    if (!visible) shown = null
    val alpha = motionFloat(
        target = if (visible) 1f else 0f,
        spec = if (visible) Motion.arrive(Motion.durationShort, scale) else Motion.leave(Motion.durationShort, scale),
        snap = !visible,
    )
    val card = shown
    if (visible && card != null && alpha > 0f) {
        Box(Modifier.graphicsLayer { this.alpha = alpha }) { DialogCard(app, card, onEffect) }
    }
}

@Composable
private fun MoonlightImportLayer(
    app: FoldcadeApp,
    sheet: MoonlightImportSheet?,
    screen: HostScreen,
    scale: Float,
) {
    val visible = sheet != null && sheet.screen == screen
    var shown by remember { mutableStateOf(sheet) }
    if (visible) shown = sheet
    val alpha = motionFloat(
        target = if (visible) 1f else 0f,
        spec = if (visible) Motion.arrive(Motion.durationShort, scale) else Motion.leave(Motion.durationShort, scale),
    )
    val card = shown
    if (card != null && alpha > 0f) {
        Box(Modifier.graphicsLayer { this.alpha = alpha }) { MoonlightImportCard(app, card, screen) }
    }
}

@Composable
private fun MoonlightImportCard(
    app: FoldcadeApp,
    sheet: MoonlightImportSheet,
    screen: HostScreen,
) {
    val theme = foldTheme()
    val scroll = rememberScrollState()
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
                .heightIn(max = 640.dp)
                .drawWithContent {
                    drawContent()
                    drawRect(color = theme.muted, style = Stroke(width = Metrics.dialogBorderPx))
                }
                .background(theme.surface)
                .padding(px(Metrics.dialogInsetPx)),
            verticalArrangement = Arrangement.spacedBy(px(12f)),
        ) {
            BasicText(text = Copy.importMoonlightTitle, style = text(theme.onBackground, TypeRamp.dialogTitle, theme))
            BasicText(text = Copy.importMoonlightBody, style = text(theme.onBackground, TypeRamp.dialogBody, theme))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(px(8f)),
            ) {
                moonlightSheetSections(sheet.apps).forEach { section ->
                    BasicText(
                        text = section.hostName,
                        modifier = Modifier.padding(top = px(4f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = text(theme.muted, TypeRamp.hint, theme),
                    )
                    section.rows.forEach { row ->
                        MoonlightAppRow(
                            label = row.app.label,
                            checked = row.app.checked,
                            focused = sheet.index == row.index,
                            onClick = { app.shell.touchMoonlight(row.index, screen) },
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(px(8f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DialogAction(
                    label = Copy.importMoonlight,
                    focused = sheet.target == MoonlightSheetTarget.Import,
                    onClick = { app.shell.touchMoonlight(sheet.apps.size, screen) },
                )
                DialogAction(
                    label = Copy.notNow,
                    focused = sheet.target == MoonlightSheetTarget.NotNow,
                    onClick = { app.shell.touchMoonlight(sheet.apps.size + 1, screen) },
                )
            }
        }
    }
}

/**
 * One discovered app. Focus uses [dialogPlate], the same filled pill as the other dialogs.
 */
@Composable
private fun MoonlightAppRow(
    label: String,
    checked: Boolean,
    focused: Boolean,
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
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) requester.bringIntoView()
    }
    val ink = if (focused) theme.background else theme.muted
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .graphicsLayer {
                clip = false
                scaleX = drawn
                scaleY = drawn
            }
            .dialogPlate(focused, theme.focus)
            .hostPress(onClick)
            .padding(horizontal = px(22f), vertical = px(12f)),
        horizontalArrangement = Arrangement.spacedBy(px(12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(px(18f))
                .drawBehind {
                    val radius = CornerRadius(4f, 4f)
                    if (checked) {
                        drawRoundRect(color = ink, cornerRadius = radius)
                    } else {
                        drawRoundRect(color = ink, cornerRadius = radius, style = Stroke(width = 2f))
                    }
                },
        )
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = text(ink, TypeRamp.dialogBody, theme),
        )
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
            val detail = dialog.detail
            if (!detail.isNullOrBlank()) {
                BasicText(text = detail, style = text(theme.muted, TypeRamp.dialogBody, theme))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(px(8f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dialog.buttons.forEachIndexed { index, button ->
                    val label = when (button) {
                        DialogButton.UseAsHome -> Copy.useAsHome
                        DialogButton.NotNow -> Copy.notNow
                        DialogButton.Allow -> Copy.allowUsage
                        DialogButton.ContinueGrant -> Copy.continueGrant
                        DialogButton.Ok -> Copy.ok
                        DialogButton.CloseIt -> Copy.closeIt
                    }
                    DialogAction(
                        label = label,
                        focused = dialog.index == index,
                        onClick = { onEffect(app.shell.touchDialog(index, dialog.screen)) },
                    )
                }
            }
        }
    }
}

private fun emptyTitle(model: app.foldcade.language.PickerModel): String? = when {
    model.gridKind == GridKind.NoLibrary -> Copy.noLibrary
    model.gridKind == GridKind.Unreachable -> Copy.libraryUnreachable
    model.emptyGrid == EmptyGrid.Loading -> Copy.loading
    model.emptyGrid == EmptyGrid.NoPlatforms -> Copy.noPlatforms
    model.emptyGrid == EmptyGrid.NoGames -> Copy.noGames
    else -> null
}

private fun emptyActions(model: app.foldcade.language.PickerModel): List<String> = when (model.gridKind) {
    GridKind.NoLibrary -> listOf(Copy.addFolder, Copy.connectRomm)
    GridKind.Unreachable -> listOf(Copy.tryAgain)
    else -> emptyList()
}

@Composable
private fun EmptyLibrary(
    app: FoldcadeApp,
    screen: HostScreen,
    title: String,
    actions: List<String>,
) {
    val theme = foldTheme()
    val focus = app.shell.model.focus
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        BasicText(text = title, style = text(theme.onBackground, TypeRamp.dialogTitle, theme))
        if (actions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = px(16f)),
                horizontalArrangement = Arrangement.spacedBy(px(16f)),
            ) {
                actions.forEachIndexed { index, label ->
                    val focused = focus.chrome == null && focus.cellIndex == index
                    BasicText(
                        text = label,
                        modifier = Modifier
                            .focusStroke(focused)
                            .hostPress { app.shell.touchCell(index, screen) }
                            .padding(px(8f)),
                        style = text(theme.onBackground, TypeRamp.dialogBody, theme),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogAction(label: String, focused: Boolean, onClick: () -> Unit) {
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
    BasicText(
        text = label,
        modifier = Modifier
            .graphicsLayer {
                clip = false
                scaleX = drawn
                scaleY = drawn
            }
            .padding(horizontal = px(16f), vertical = px(18f))
            .dialogPlate(focused, theme.focus)
            .hostPress(onClick)
            .padding(horizontal = px(22f), vertical = px(12f)),
        style = text(
            if (focused) theme.background else theme.muted,
            TypeRamp.dialogBody,
            theme,
        ).copy(textAlign = TextAlign.Center),
    )
}

/**
 * Focused library card. Same accent fill and bloom as [dialogPlate], on the tile's
 * corner, with the cell's [Motion.scaleFocus] scale-up. The bloom ends transparent.
 */
private fun DrawScope.drawFocusCard(accent: Color, cornerPx: Float) {
    val spread = 28f
    val half = min(size.width, size.height) / 2f
    val reach = half + spread
    val edge = (half / reach).coerceIn(0.5f, 0.92f)
    val mid = edge + (1f - edge) * 0.45f
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to accent,
                edge to accent.copy(alpha = 0.82f),
                mid to accent.copy(alpha = 0.18f),
                1f to Color.Transparent,
            ),
            center = center,
            radius = reach,
        ),
        radius = reach,
        center = center,
    )
    drawRoundRect(color = accent, cornerRadius = CornerRadius(cornerPx, cornerPx))
}

/**
 * Focused: one elliptical radial falloff, then the accent fill.
 * The gradient ends at transparent, so the bloom has no stroke ring.
 * Unfocused: a dim outline and no fill, so the black dialog stays black.
 *
 * Stretching the circle leaves one accent pixel on the left tip when that
 * tip sits on a pixel center (the gap beside Not now). The tips are the
 * transparent rim, so the clip drops them.
 */
@Composable
private fun Modifier.dialogPlate(focused: Boolean, accent: Color): Modifier {
    return this.graphicsLayer { clip = false }.drawBehind {
        val radius = size.height / 2f
        val corner = CornerRadius(radius, radius)
        if (!focused) {
            val stroke = 2f
            val inset = stroke / 2f
            drawRoundRect(
                color = accent.copy(alpha = 0.38f),
                topLeft = Offset(inset, inset),
                size = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
                cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f), (radius - inset).coerceAtLeast(0f)),
                style = Stroke(width = stroke),
            )
            return@drawBehind
        }
        val spread = 20f
        val reach = size.height / 2f + spread
        val wide = (size.width / 2f + spread) / reach
        val edge = (size.height / 2f / reach).coerceIn(0.35f, 0.82f)
        val mid = edge + (1f - edge) * 0.45f
        val rim = 2f
        clipRect(
            left = -spread + rim,
            top = -spread,
            right = size.width + spread - rim,
            bottom = size.height + spread,
        ) {
            scale(scaleX = wide, scaleY = 1f, pivot = center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to accent,
                            edge to accent.copy(alpha = 0.82f),
                            mid to accent.copy(alpha = 0.18f),
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
        drawRoundRect(color = accent, cornerRadius = corner)
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
    cell * ((Motion.scaleFocus - 1f) / 2f) + px(Metrics.focusStrokePx * Motion.scaleFocus)

@Composable
private fun Modifier.rowHighlight(focused: Boolean): Modifier {
    val accent = foldTheme().focus
    return this.drawBehind {
        if (!focused) return@drawBehind
        drawRoundRect(
            color = accent.copy(alpha = 0.2f),
            cornerRadius = CornerRadius(22f, 22f),
        )
        val bar = 7f
        drawRoundRect(
            color = accent,
            topLeft = Offset(10f, size.height * 0.2f),
            size = Size(bar, size.height * 0.6f),
            cornerRadius = CornerRadius(bar / 2f, bar / 2f),
        )
    }
}

@Composable
private fun Modifier.focusStroke(
    focused: Boolean,
    corner: Dp = Dp.Hairline,
    accent: Color? = null,
): Modifier {
    val color = accent ?: foldTheme().focus
    return this.graphicsLayer { clip = false }.drawBehind {
        if (!focused) return@drawBehind
        val bounds = this.size
        val reach = min(bounds.width, bounds.height) * 0.5f
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
