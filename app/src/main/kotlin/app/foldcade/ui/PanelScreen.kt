package app.foldcade.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.foldcade.language.heroPlayLine
import app.foldcade.language.heroFacts
import app.foldcade.R
import app.foldcade.language.QuickSetting
import app.foldcade.Displays
import app.foldcade.FoldcadeApp
import app.foldcade.FoldcadeHomeActivity
import app.foldcade.Panel
import app.foldcade.FolderIconPick
import app.foldcade.Surface
import app.foldcade.CoverImage
import app.foldcade.artwork.ArtQuery
import app.foldcade.artwork.ArtScene
import app.foldcade.artwork.ArtSet
import app.foldcade.api.plugin.Game
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import app.foldcade.language.ConnectField
import app.foldcade.language.ConnectKind
import app.foldcade.language.Copy
import app.foldcade.language.HomeGrid
import app.foldcade.language.FOLDER_ICONS
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
import app.foldcade.language.Motion
import app.foldcade.language.MotionSpeed
import app.foldcade.language.SidePanel
import app.foldcade.language.TypeRamp
import app.foldcade.language.GlowFalloff
import app.foldcade.language.builtInTheme
import app.foldcade.language.connectFields
import app.foldcade.language.connectHint
import app.foldcade.language.hintFor
import app.foldcade.language.HintKey
import app.foldcade.language.PromptKey
import app.foldcade.language.gridHints
import app.foldcade.language.letterOfKey
import app.foldcade.language.letterbox
import app.foldcade.language.cursorBrush
import app.foldcade.language.displayOrder
import app.foldcade.language.heroCopy
import app.foldcade.language.heroCrossfadeActive
import app.foldcade.language.monogram
import app.foldcade.language.panelRows
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
    val menuVisible = (model.panel?.screen == HostScreen.Top || model.settings?.screen == HostScreen.Top) &&
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
    // A closed menu draws neither the blur nor the scrim. A graphics layer
    // left over the library, even at zero alpha, keeps the accessibility dump empty.
    CompositionLocalProvider(LocalFoldTheme provides paint) {
        Box(Modifier.fillMaxSize().background(paint.theme.background)) {
            val wallpaper = when (panel) {
                Panel.Top -> paint.wallpaperTop
                Panel.Bottom -> paint.wallpaperBottom
                null -> null
            }
            // No wallpaper node when this panel has none. A full-screen layer left
            // in the tree keeps the accessibility dump empty, so the library title
            // is not in the window the folder check reads.
            if (wallpaper != null) PanelWallpaper(wallpaper)
            // The body has one place in the tree, open menu or not. Opening a menu
            // keeps the backdrop's clock and the grid's state, and the blur eases out.
            Box(Modifier.fillMaxSize().menuBlur(blur).menuDim(blur)) {
                CompositionLocalProvider(LocalUnderMenu provides blurHere) {
                    PanelBody(activity, panel, screen, scale)
                }
            }
            if (blurHere && session.surfaceOn(Panel.Bottom) == Surface.Picker) MenuHints(app)
            if (panel != null && screen != null) {
                DialogLayer(app, model.dialog, screen, scale, activity::dispatch)
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
    val cover = remember { BackdropCover() }
    // Settings paints the theme's background over the whole panel.
    val settingsOver = screen != null && model.settings?.screen == screen && foldTheme().background.alpha >= 1f
    Backdrop(
        motion = model.backgroundMotion,
        speed = model.motionSpeed,
        animatorScale = scale,
        running = activity.shellVisible && session.bothScreensFree(),
        // Opaque game art or Settings over it, or the menu's blur, leaves nothing to watch move.
        held = cover.covered || settingsOver || LocalUnderMenu.current,
    )
    if (panel == null || screen == null) return
    val connectHere = model.connectOpen && model.connectScreen == screen
    if (model.settings?.screen == screen) {
        SettingsScreen(app, screen, activity::dispatch)
        return
    }
    CompositionLocalProvider(LocalBackdropCover provides cover) {
        TravelFade(target = session.surfaceOn(panel), scale = scale) { shown ->
            when (shown) {
                null -> Unit
                Surface.Hero -> if (connectHere) Unit else Hero(app, screen, scale, activity::dispatch)
                Surface.Picker -> Picker(app, screen, scale, activity::dispatch)
            }
        }
    }
    if (connectHere) ConnectScreen(app, screen, activity::dispatch)
}

/** Dim painted with the content, not a second full-screen node on top of it. */
private fun Modifier.menuDim(progress: Float): Modifier = drawWithContent {
    drawContent()
    if (progress <= 0f) return@drawWithContent
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

/**
 * Static wallpaper for this panel. Letterboxed, not cropped, and not animated.
 * Drawn behind the library, with no focus, touch, or semantics. The caller
 * leaves this out of the tree when the panel has no wallpaper.
 */
@Composable
private fun PanelWallpaper(image: ImageBitmap) {
    Canvas(
        Modifier
            .fillMaxSize()
            .zIndex(-1f)
            .focusProperties { canFocus = false }
            .clearAndSetSemantics { },
    ) {
        val box = letterbox(
            imageWidth = image.width.toFloat(),
            imageHeight = image.height.toFloat(),
            panelWidth = size.width,
            panelHeight = size.height,
        )
        val width = box.width.roundToInt()
        val height = box.height.roundToInt()
        if (width <= 0 || height <= 0) return@Canvas
        drawImage(
            image = image,
            dstOffset = IntOffset(box.left.roundToInt(), box.top.roundToInt()),
            dstSize = IntSize(width, height),
            filterQuality = FilterQuality.Medium,
        )
    }
}

private object SteadyMotion : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

@Composable
private fun motionFloat(target: Float, spec: FiniteAnimationSpec<Float>, snap: Boolean = false): Float =
    motionState(target, spec, snap).value

/**
 * The same motion as [motionFloat], as state. Delegate to it with `by` and read it in a
 * layer or draw block: each frame then redraws without recomposing the caller.
 */
@Composable
private fun motionState(target: Float, spec: FiniteAnimationSpec<Float>, snap: Boolean = false): State<Float> {
    val anim = remember { Animatable(target) }
    LaunchedEffect(target, snap) {
        if (snap) anim.snapTo(target)
        else withContext(SteadyMotion) { anim.animateTo(target, spec) }
    }
    return anim.asState()
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
    app.shell.folderIconPick()?.let { (pick, name) ->
        Box(Modifier.fillMaxSize()) {
            FolderIconPicker(pick, name, top = screen == HostScreen.Top)
            TopIslands(app, screen, scale) { panelState, progress, interactive ->
                PanelRows(app, screen, panelState, progress, interactive, onEffect)
            }
        }
        return
    }
    val subject = app.shell.focusedHero()
    val cellFocused = model.dialog == null && model.panel == null && !model.connectOpen && model.focus.chrome == null
    // The record behind each subject, so a layer that is fading out keeps its own cover.
    val records = remember { mutableMapOf<String, Game>() }
    val queries = remember { mutableMapOf<String, ArtQuery>() }
    app.shell.focusedRecord()?.let { record -> if (subject != null) records[subject.key] = record }
    app.shell.focusedGame()?.let(app.shell::artQuery)?.let { query -> if (subject != null) queries[subject.key] = query }
    // The focused game's scene decides how the name card sits over it.
    val scene = subject?.let { rememberScene(app, records[it.key], queries[it.key]) }
    val sceneArt = scene != null || subject?.let { rememberArt(app, records[it.key], queries[it.key]) } != null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The game fills the screen behind everything: its wide art, or its box art blurred.
        HeroCrossfade(
            target = subject,
            speed = model.motionSpeed,
            animatorScale = scale,
            same = { left, right -> left?.key == right?.key },
            layer = Modifier.fillMaxSize(),
            fadeOnly = true,
        ) { shown ->
            SceneBackdrop(app, records[shown.key], queries[shown.key])
        }
        val inset = px(Metrics.heroInsetPx)
        // Wide enough for a platform, both screens, and play time on one line.
        val cardMax = maxWidth * 0.72f
        // Cocoon's hero: the art centred between the islands, a name card centred under it.
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = inset,
                    end = inset,
                    top = if (screen == HostScreen.Top) inset + 48.dp else inset,
                    bottom = inset,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                HeroCrossfade(
                    target = subject,
                    speed = model.motionSpeed,
                    animatorScale = scale,
                    same = { left, right -> left?.key == right?.key },
                ) { shown ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (shown) {
                            is HeroSubject.Item -> HeroArt(
                                app = app,
                                title = shown.item.title,
                                mark = shown.item.mark,
                                packageName = shown.item.packageName,
                                record = records[shown.key],
                                query = queries[shown.key],
                            )
                            is HeroSubject.Folder -> HeroArt(app, shown.folder.name, shown.folder.mark, null, null, null)
                        }
                    }
                }
            }
            if (subject != null) {
                Spacer(Modifier.height(px(24f)))
                HeroLabel(
                    app,
                    subject,
                    cellFocused,
                    cardMax,
                    overArt = sceneArt,
                    // A logo on wide art already says the name.
                    named = !(scene?.background != null && scene.logo != null),
                )
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
    layer: Modifier = Modifier.fillMaxWidth(),
    fadeOnly: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    val travelMillis = Motion.heroDuration(speed, animatorScale)
    val fadeOnly = fadeOnly || Motion.heroFadeOnly(speed, animatorScale)
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
        layer
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
 * The icons a folder the user made can wear, in rows on the top screen. The
 * focused icon wears the focus ring. The bottom screen names A and B.
 */
@Composable
private fun FolderIconPicker(pick: FolderIconPick, name: String, top: Boolean) {
    val theme = foldTheme()
    val columns = FolderIconPick.COLUMNS
    val rows = FOLDER_ICONS.chunked(columns)
    val inset = px(Metrics.heroInsetPx)
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = inset, end = inset, bottom = inset, top = if (top) inset + 48.dp else inset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(px(24f)),
    ) {
        BasicText(
            text = "${Copy.folderIcon} · $name",
            style = text(theme.onBackground, TypeRamp.dialogTitle, theme),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val gap = px(20f)
            val cell = minOf(
                (maxWidth - gap * (columns - 1)) / columns,
                (maxHeight - gap * (rows.size - 1)) / rows.size,
            )
            val corner = cell * theme.iconRadius
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEachIndexed { columnIndex, mark ->
                            val focused = rowIndex * columns + columnIndex == pick.index
                            Box(
                                Modifier
                                    .size(cell)
                                    .graphicsLayer { clip = false }
                                    .drawBehind {
                                        val cornerPx = corner.toPx()
                                        drawRoundRect(color = theme.surface, cornerRadius = CornerRadius(cornerPx, cornerPx))
                                        if (focused) with(FocusRing) { drawFocusRing(cornerPx) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.graphicsLayer { alpha = if (focused) 1f else 0.7f }) {
                                    MarkIcon(mark, theme.artScale)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One name and one line of facts for the focus that just arrived.
 * The artwork behind it may still be crossfading.
 */
@Composable
private fun HeroLabel(
    app: FoldcadeApp,
    shown: HeroSubject,
    cellFocused: Boolean,
    maxWidth: Dp,
    overArt: Boolean = false,
    named: Boolean = true,
) {
    val theme = foldTheme()
    val copy = heroCopy(shown)
    val centred = text(theme.onBackground, TypeRamp.heroTitle, theme).copy(textAlign = TextAlign.Center)
    Column(
        Modifier
            .widthIn(min = px(360f), max = maxWidth)
            .clip(RoundedCornerShape(Metrics.cardCornerDp.dp))
            // Over the game's art the card lets it through.
            .background(if (overArt) theme.surface.copy(alpha = SCENE_CARD_ALPHA) else theme.surface)
            .padding(horizontal = px(40f), vertical = px(20f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(px(4f)),
    ) {
        if (named) {
            BasicText(
                text = copy.title,
                style = centred.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val item = (shown as? HeroSubject.Item)?.item
        if (item?.emptyShelfHint == true) {
            ShelfMeta(line = copy.detail, hintFocused = cellFocused, centred = true)
        } else {
            val both = app.shell.focusedGame()?.takeIf { it.id == item?.key }?.occupiesBothDisplays == true
            val played = item?.let { app.plays.run { stamp; shown(app.shell.playId(it.key)) } }
            val facts = heroFacts(
                copy.title,
                copy.detail,
                item?.availability,
                Copy.usesBothScreens.takeIf { both },
                played?.let {
                    heroPlayLine(it.activeMillis, it.lastPlayedMillis, System.currentTimeMillis(), ZoneId.systemDefault())
                },
            )
            if (facts.isNotEmpty()) {
                BasicText(
                    text = facts,
                    style = text(theme.muted, TypeRamp.heroMeta, theme).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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

/**
 * The top screen's scene for a game: at once when it is already known,
 * otherwise after one lookup. Turning Game art off or on asks again.
 */
@Composable
private fun rememberScene(app: FoldcadeApp, record: Game?, query: ArtQuery?): ArtScene? {
    val allowed = app.shell.model.artwork
    val held = remember(record, query, allowed) { app.covers.cachedScene(record, query) }
    var fetched by remember(record, query, allowed) { mutableStateOf<ArtScene?>(null) }
    LaunchedEffect(record, query, allowed) {
        if ((record != null || query != null) && held == null) fetched = app.covers.fetchScene(record, query)
    }
    return held ?: fetched
}

/**
 * The whole top screen behind a game. Wide art fills it, darkened at the top
 * for the islands and at the bottom for the name card. Without wide art, the
 * box art fills it blurred and dimmed, so the screen takes the game's colours.
 * Without either, nothing is drawn and the theme's background shows.
 */
@Composable
private fun SceneBackdrop(app: FoldcadeApp, record: Game?, query: ArtQuery?) {
    val scene = rememberScene(app, record, query)
    val wide = scene?.background?.let { rememberCoverPainter(app, it, BACKDROP_PX) }
    if (wide != null) {
        CoversBackdrop()
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = wide.painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.22f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.8f),
                        ),
                    ),
            )
        }
        return
    }
    if (scene?.background != null) return
    val cover = rememberArt(app, record, query)?.cover ?: return
    val ambient = rememberCoverPainter(app, cover, AMBIENT_PX) ?: return
    CoversBackdrop()
    Image(
        painter = ambient.painter,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .blur(AMBIENT_BLUR, BlurredEdgeTreatment.Rectangle)
            .drawWithContent {
                drawContent()
                drawRect(Color.Black.copy(alpha = AMBIENT_DIM))
            },
        contentScale = ContentScale.Crop,
    )
}

/**
 * Art for a tile or the hero: at once when it is already known, otherwise after
 * one lookup. A provider's cover for [record] comes before the public sources.
 * Turning Game art off or on asks again.
 */
@Composable
private fun rememberArt(app: FoldcadeApp, record: Game?, query: ArtQuery?): ArtSet? {
    val allowed = app.shell.model.artwork
    val held = remember(record, query, allowed) { app.covers.cached(record, query) }
    var fetched by remember(record, query, allowed) { mutableStateOf<ArtSet?>(null) }
    LaunchedEffect(record, query, allowed) {
        if ((record != null || query != null) && held == null) fetched = app.covers.fetch(record, query)
    }
    return held ?: fetched
}

@Composable
private fun HeroArt(
    app: FoldcadeApp,
    title: String,
    mark: String?,
    packageName: String?,
    record: Game?,
    query: ArtQuery?,
) {
    val theme = foldTheme()
    val icon = launcherIcon(packageName)
    val cover = rememberArt(app, record, query)?.cover
    val scene = rememberScene(app, record, query)
    val logo = scene?.logo?.takeIf { scene.background != null }?.let { rememberCoverPainter(app, it, LOGO_PX) }
    val screenShot = scene?.screen?.takeIf { scene.background == null }
        ?.let { rememberCoverPainter(app, it, SCREEN_PX, FilterQuality.None) }
    val accent = mark?.let { markGlyph(it)?.accent } ?: theme.focus
    val loaded = cover?.let { rememberCoverPainter(app, it, COVER_PX) }
    // A logo, or a box beside its title screen, takes the width it needs. The game's own
    // art is behind them, so they draw no glow. A lone box or mark stays in its glowing square.
    val staged = logo != null || (loaded != null && screenShot != null)
    Box(
        Modifier
            .fillMaxHeight()
            .then(if (staged) Modifier.fillMaxWidth() else Modifier.aspectRatio(1f))
            .drawBehind {
                if (staged) return@drawBehind
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
        // The cover, then the same art the focused tile shows. A letter is the last resort.
        val kit = mark?.let { LocalFoldTheme.current.marks[it] }
        when {
            // Wide art fills the screen behind; the logo names the game over it.
            logo != null -> Image(
                painter = logo.painter,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth(LOGO_WIDTH)
                    .fillMaxHeight(LOGO_HEIGHT)
                    // A soft shade behind the logo keeps a dark logo readable over busy art.
                    .drawBehind {
                        val reach = size.maxDimension * 0.62f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f to Color.Black.copy(alpha = 0.62f),
                                    1f to Color.Transparent,
                                ),
                                center = center,
                                radius = reach,
                            ),
                            radius = reach,
                            center = center,
                        )
                    },
                contentScale = ContentScale.Fit,
            )
            scene?.background != null && scene.logo != null -> Unit
            // A real title screen beside the box, drawn crisp.
            loaded != null && screenShot != null -> Row(
                Modifier.fillMaxHeight(COVER_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(px(40f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = loaded.painter,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(loaded.ratio, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(Metrics.cardCornerDp.dp)),
                    contentScale = ContentScale.Crop,
                )
                Image(
                    painter = screenShot.painter,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(screenShot.ratio, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(Metrics.cardCornerDp.dp / 2)),
                    // The painter was asked for nearest-neighbour scaling, so pixel art stays sharp.
                    contentScale = ContentScale.Fit,
                )
            }
            loaded != null -> Image(
                painter = loaded.painter,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxHeight(COVER_HEIGHT)
                    .aspectRatio(loaded.ratio, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(Metrics.cardCornerDp.dp)),
                contentScale = ContentScale.Crop,
            )
            icon != null -> Image(
                bitmap = icon,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(theme.artScale),
                contentScale = ContentScale.Fit,
            )
            kit != null -> KitMark(kit, theme.artScale, alpha = 1f)
            mark != null && markGlyph(mark) != null -> MarkIcon(mark, 0.86f)
            else -> BasicText(
                text = monogram(title),
                modifier = Modifier.scale(4f),
                style = text(theme.onBackground, TypeRamp.heroTitle, theme),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private class LoadedCover(val painter: Painter, val ratio: Float)

/**
 * The decoded cover, or null while it loads or when it cannot load. The size
 * is fixed, so the request starts before the painter is drawn.
 */
@Composable
private fun rememberCoverPainter(
    app: FoldcadeApp,
    uri: String,
    px: Int,
    filterQuality: FilterQuality = FilterQuality.Low,
): LoadedCover? {
    val context = LocalContext.current
    val request = remember(uri, px) {
        ImageRequest.Builder(context)
            .data(CoverImage(uri))
            .size(px)
            .crossfade(true)
            .build()
    }
    val painter = rememberAsyncImagePainter(request, app.images, filterQuality = filterQuality)
    val state by painter.state.collectAsState()
    val image = (state as? AsyncImagePainter.State.Success)?.result?.image ?: return null
    if (image.width <= 0 || image.height <= 0) return null
    return LoadedCover(painter, image.width.toFloat() / image.height)
}

private const val COVER_PX = 1024
private const val BACKDROP_PX = 1920
private const val AMBIENT_PX = 256
private const val LOGO_PX = 800
private const val SCREEN_PX = 1024
private const val LOGO_WIDTH = 0.6f
private const val LOGO_HEIGHT = 0.7f
private const val SCENE_CARD_ALPHA = 0.72f
private const val AMBIENT_DIM = 0.55f
private val AMBIENT_BLUR = 48.dp
private const val TILE_PX = 384
private const val TILE_ART_REST_ALPHA = 0.82f
private const val COVER_HEIGHT = 0.92f

/**
 * A theme mark. Theme marks are painted on opaque black. Screen drops the black,
 * so the glow sits on the card behind instead of a black square inside it.
 */
@Composable
private fun KitMark(kit: ImageBitmap, fraction: Float, alpha: Float) {
    Canvas(Modifier.fillMaxSize(fraction)) {
        val area = this.size
        val side = min(area.width, area.height)
        drawImage(
            image = kit,
            dstOffset = IntOffset(((area.width - side) / 2f).roundToInt(), ((area.height - side) / 2f).roundToInt()),
            dstSize = IntSize(side.roundToInt(), side.roundToInt()),
            alpha = alpha,
            blendMode = BlendMode.Screen,
        )
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
    // The hero on the other screen carries the focused name, so the grid is
    // icons only. With one screen free there is no hero, and tiles keep labels.
    val iconsOnly = app.store.session.bothScreensFree()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val stride = maxWidth
        val inset = maxWidth * Metrics.insetFraction
        val gap = maxWidth * Metrics.gapFraction
        val titlePx = rememberTextMeasurer().measure(
            text = "Ag",
            style = text(theme.onBackground, TypeRamp.gridLabel, theme),
            maxLines = 1,
        ).size.height
        val titleLine = with(LocalDensity.current) { titlePx.toDp() }
        val showingHome = !model.libraryGrid && model.homeGrid == HomeGrid.StandIns
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = inset)
                .padding(top = inset, bottom = inset),
        ) {
            if (screen == HostScreen.Top) {
                Spacer(Modifier.height(48.dp))
            }
            GridHeader(app, shell, screen, onEffect)
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(model.panel, model.dialog, model.connectOpen, shell.homeEditing(), showingHome) {
                        if (showingHome) return@pointerInput
                        if (model.panel != null || model.dialog != null || model.connectOpen) {
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
                contentAlignment = Alignment.Center,
            ) {
                val rows = Metrics.rows
                val columns = Metrics.columns
                // Room for the focused tile's scale and ring. The glow is not clipped.
                val ring = focusOutset(maxWidth / columns) + px(GRID_RING_PAD)
                val titleBlock = if (iconsOnly) Dp.Hairline else px(20f) + titleLine
                val fromHeight = (maxHeight - ring * 2 - gap * (rows - 1)) / rows - titleBlock
                val fromWidth = (maxWidth - ring * 2 - gap * (columns - 1)) / columns
                val cell = minOf(fromHeight, fromWidth).coerceAtLeast(px(56f))
                SideEffect { shell.setRowsPerPage(rows) }
                val emptyTitle = emptyTitle(model)
                if (model.libraryGrid && emptyTitle != null) {
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
                        cell = cell,
                        gap = gap,
                        pad = ring,
                        rows = rows,
                        stride = stride,
                        scale = scale,
                        showTitle = !iconsOnly,
                        followDrag = showingHome,
                    )
                }
            }
            PageDots(count = model.count, rows = Metrics.rows, index = model.focus.cellIndex)
            val draft = shell.renameDraft()
            if (draft != null) RenameField(app, screen, draft)
            // Under an open menu the blur would smear these. MenuHints draws them above it.
            GridHints(app, Modifier.alpha(if (LocalUnderMenu.current) 0f else 1f))
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
                    modifier = Modifier.fillMaxWidth().keepInView(focused).rowHighlight(focused),
                )
            } else {
                val press = if (interactive) Modifier.hostPress(step) else Modifier
                Row(
                    Modifier
                        .fillMaxWidth()
                        .keepInView(focused)
                        .rowHighlight(focused)
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

/**
 * A menu taller than its island scrolls. The D-pad moves the focus, not the
 * scroll, so the focused row asks its scrolling parent to show it.
 */
@Composable
private fun Modifier.keepInView(focused: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) requester.bringIntoView()
    }
    return bringIntoViewRequester(requester)
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
    // Round icon buttons in one row, like Cocoon's quick settings. The label is for touch
    // readers and the emulator's UI dump.
    Column(Modifier.padding(vertical = px(12f)), verticalArrangement = Arrangement.spacedBy(px(16f))) {
        tiles.chunked(quickTileColumns).forEachIndexed { rowIndex, chunk ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                chunk.forEachIndexed { column, row ->
                    val index = tileStart + rowIndex * quickTileColumns + column
                    val focused = focusedIndex == index
                    val press = if (interactive) {
                        Modifier.hostPress { onEffect(app.shell.touchPanel(index, screen)) }
                    } else {
                        Modifier
                    }
                    val label = rowLabel(row, app.shell.model)
                    Box(
                        Modifier
                            .size(QUICK_BUTTON_DP.dp)
                            .keepInView(focused)
                            .graphicsLayer { clip = false }
                            .drawBehind {
                                val radius = size.minDimension / 2f
                                drawCircle(theme.onBackground.copy(alpha = if (focused) 0.16f else 0.08f))
                                if (focused) with(FocusRing) { drawFocusRing(radius) }
                            }
                            .then(press)
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        val icon = (row as? Row.QuickTile)?.setting?.let(::quickIcon)
                        if (icon != null) {
                            Image(
                                painter = painterResource(icon),
                                contentDescription = null,
                                modifier = Modifier
                                    .size((QUICK_BUTTON_DP * 0.46f).dp)
                                    .graphicsLayer { alpha = if (focused) 1f else 0.78f },
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val QUICK_BUTTON_DP = 52f

private fun quickIcon(setting: QuickSetting): Int = when (setting) {
    QuickSetting.Wifi -> R.drawable.ic_status_wifi_4
    QuickSetting.Bluetooth -> R.drawable.ic_status_bluetooth
    QuickSetting.Display -> R.drawable.ic_status_display
    QuickSetting.Sound -> R.drawable.ic_status_volume_3
    QuickSetting.Battery -> R.drawable.ic_status_battery_100
    QuickSetting.AppInfo -> R.drawable.ic_status_info
    QuickSetting.Settings -> R.drawable.ic_status_settings
}

/**
 * Above the grid: the shelf name, or the All library's tab, sort, system, and
 * destination. The home grid has nothing here.
 */
@Composable
private fun GridHeader(
    app: FoldcadeApp,
    shell: app.foldcade.ShellController,
    screen: HostScreen,
    onEffect: (Effect?) -> Unit,
) {
    val theme = foldTheme()
    val chrome = shell.homeChrome()
    if (chrome != null) {
        Row(
            Modifier.padding(bottom = px(12f)),
            horizontalArrangement = Arrangement.spacedBy(px(12f)),
        ) {
            listOf(chrome.tab, chrome.sort, chrome.system, chrome.destination).forEachIndexed { index, label ->
                ChromeButton(
                    label = label,
                    focused = chrome.focused == index,
                    onClick = { onEffect(shell.touchAllChrome(index, screen)) },
                )
            }
        }
        return
    }
    val shelf = homeGridLabel(shell.model.homeGrid) ?: return
    BasicText(text = shelf, style = text(theme.onBackground, TypeRamp.sideRow, theme))
}

/** Page marks under the grid. One dot per page, and none for a single page. */
@Composable
private fun PageDots(count: Int, rows: Int, index: Int) {
    val pageSize = (Metrics.columns * rows).coerceAtLeast(1)
    val pages = ((count + pageSize - 1) / pageSize).coerceAtLeast(1)
    if (pages < 2) return
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

/** True inside the blurred layer under an open menu. */
private val LocalUnderMenu = compositionLocalOf { false }

/**
 * The picker's hint row, drawn above the blur while a menu is open. It sits where
 * the picker's own row would, at the bottom of the same inset.
 */
@Composable
private fun MenuHints(app: FoldcadeApp) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inset = maxWidth * Metrics.insetFraction
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.BottomCenter) {
            GridHints(app)
        }
    }
}

/** The keys under the grid, centred. Nothing when every key here is obvious. */
@Composable
private fun GridHints(app: FoldcadeApp, modifier: Modifier = Modifier) {
    val shell = app.shell
    val model = shell.model
    val actions = if (model.connectOpen) {
        connectHint(connectFields(model.connectKind).getOrElse(model.connectIndex) { ConnectField.Token })
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
    // The grid's own keys only count while nothing covers it.
    val gridKeys = model.panel == null && model.dialog == null
    val keys = gridHints(
        actions,
        shell.homeKeys(),
        model.faceMap,
        screens = gridKeys && shell.focusedLaunchesOnOneScreen(),
        options = gridKeys && !model.connectOpen && shell.hasOptions(),
    )
    if (keys.isEmpty()) return
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(px(16f), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEach { key -> HintWord(model, key) }
    }
}

/** One small glyph and a word. The glyph fills while that key is held. */
@Composable
private fun HintWord(
    model: app.foldcade.language.PickerModel,
    hint: HintKey,
) {
    val glyph = promptGlyph(hint.key)
    val held = hint.key in model.held
    Row(
        horizontalArrangement = Arrangement.spacedBy(px(8f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PromptImage(if (held) "${glyph}_filled" else glyph, 24.dp)
        BasicText(text = hint.label, style = text(foldTheme().muted, TypeRamp.hint, foldTheme()))
    }
}

private fun promptGlyph(key: PromptKey): String = when (key) {
    PromptKey.FaceA -> "ic_btn_a"
    PromptKey.FaceB -> "ic_btn_b"
    PromptKey.FaceX -> "ic_btn_x"
    PromptKey.FaceY -> "ic_btn_y"
    PromptKey.Select -> "ic_btn_select"
    PromptKey.Start -> "ic_btn_start"
    PromptKey.Home -> "ic_btn_home"
    PromptKey.Back -> "ic_btn_back"
    PromptKey.L1 -> "ic_btn_l1"
    PromptKey.R1 -> "ic_btn_r1"
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
private fun ShelfMeta(line: String, hintFocused: Boolean, centred: Boolean = false) {
    val theme = foldTheme()
    BasicText(
        text = line,
        modifier = if (hintFocused) Modifier.chip(true).padding(horizontal = px(20f), vertical = px(10f)) else Modifier,
        style = text(if (hintFocused) theme.focus else theme.muted, TypeRamp.heroMeta, theme)
            .copy(textAlign = if (centred) TextAlign.Center else TextAlign.Start),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ChromeButton(label: String, focused: Boolean, onClick: () -> Unit) {
    val theme = foldTheme()
    BasicText(
        text = label,
        modifier = Modifier.chip(focused).hostPress(onClick).padding(horizontal = px(20f), vertical = px(10f)),
        style = text(theme.onBackground, TypeRamp.dialogBody, theme),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
    stride: Dp,
    scale: Float,
    showTitle: Boolean,
    followDrag: Boolean = false,
) {
    val focus = app.shell.model.focus
    val pageSize = (Metrics.columns * rows).coerceAtLeast(1)
    val page = focus.cellIndex / pageSize
    val titles = pageTitles(app, page, pageSize)
    // The emulator reads this line. A presentation-display dump is empty while that display has focus.
    LaunchedEffect(titles) {
        Log.i("Foldcade", "library-ui page " + titles.joinToString(" | "))
    }
    val position = remember { Animatable(page.toFloat()) }
    var dragPages by remember { mutableStateOf(0f) }
    LaunchedEffect(page, scale) {
        dragPages = 0f
        withContext(SteadyMotion) {
            position.animateTo(page.toFloat(), Motion.arrive(Motion.durationTravel, scale))
        }
    }
    val reduced = Motion.reduced(scale)
    // Read where it is used, in offsets and layers, so travel redraws without recomposing
    // the pages. Composition only hears which pages are on screen.
    val shownPage = { position.value - dragPages }
    val width = cell * Metrics.columns + gap * (Metrics.columns - 1)
    // A neighbouring page sits one panel width away, so it is off screen and
    // the focus glow does not need a clip.
    val widthPx = with(LocalDensity.current) { stride.toPx() }
    val folderToken = app.shell.homeAnimToken()
    val folderPop = remember { Animatable(1f) }
    LaunchedEffect(folderToken, scale) {
        folderPop.snapTo(0.92f)
        withContext(SteadyMotion) {
            folderPop.animateTo(1f, Motion.arrive(Motion.durationShort, scale))
        }
    }
    val fading by remember(reduced, page) { derivedStateOf { reduced && abs(shownPage() - page) > 0.001f } }
    val popping by remember { derivedStateOf { folderPop.value != 1f } }
    Box(
        Modifier
            .requiredWidth(width + pad * 2)
            .then(
                if (fading || popping) {
                    Modifier.graphicsLayer {
                        if (reduced) this.alpha = (1f - abs(shownPage() - page)).coerceIn(0.35f, 1f)
                        scaleX = folderPop.value
                        scaleY = folderPop.value
                    }
                } else {
                    Modifier
                },
            )
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
        val low by remember { derivedStateOf { floor(shownPage()).toInt() } }
        val high by remember { derivedStateOf { ceil(shownPage()).toInt() } }
        var rowPx by remember { mutableStateOf(0) }
        for (drawn in low..high) {
            Box(Modifier.offset { IntOffset(((drawn - shownPage()) * widthPx).roundToInt(), 0) }) {
                Grid(app, screen, cell, gap, rows, drawn, showTitle) { rowPx = it }
            }
        }
        GridCursor(
            app = app,
            slot = focus.cellIndex % pageSize,
            cell = cell,
            gap = gap,
            rowPx = rowPx,
            dragPx = { dragPages * widthPx },
            jump = folderToken,
            scale = scale,
        )
    }
}

/**
 * The one focus ring on the grid. It slides from tile to tile instead of each tile
 * drawing its own, so moving reads as steering a cursor. Across a page it travels with
 * the page, so it lands on the new tile as the page settles.
 */
@Composable
private fun GridCursor(
    app: FoldcadeApp,
    slot: Int,
    cell: Dp,
    gap: Dp,
    rowPx: Int,
    dragPx: () -> Float,
    jump: String,
    scale: Float,
) {
    val shell = app.shell
    val model = shell.model
    val shown = model.dialog == null &&
        model.panel == null &&
        shell.homeChrome()?.focused == null &&
        model.focus.chrome == null &&
        !shell.homeEditing() &&
        model.focus.cellIndex < model.count
    val alpha = motionFloat(
        target = if (shown) 1f else 0f,
        spec = if (shown) Motion.arrive(Motion.durationFocus, scale) else Motion.leave(Motion.durationFocus, scale),
    )
    val column = (slot % Metrics.columns).toFloat()
    val row = (slot / Metrics.columns).toFloat()
    val x = remember { Animatable(column) }
    val y = remember { Animatable(row) }
    var landed by remember { mutableStateOf(jump) }
    LaunchedEffect(column, row, jump, shown, scale) {
        // A new folder, or a ring coming back from elsewhere, starts on its tile.
        if (jump != landed || !shown || alpha == 0f) {
            landed = jump
            x.snapTo(column)
            y.snapTo(row)
            return@LaunchedEffect
        }
        withContext(SteadyMotion) {
            coroutineScope {
                launch { x.animateTo(column, Motion.arrive(Motion.durationTravel, scale)) }
                launch { y.animateTo(row, Motion.arrive(Motion.durationTravel, scale)) }
            }
        }
    }
    if (alpha == 0f) return
    val radius = cell * foldTheme().iconRadius
    val ring = remember { Path() }
    Box(
        Modifier
            .offset {
                val pitchX = (cell + gap).toPx()
                val pitchY = (if (rowPx > 0) rowPx.toFloat() else cell.toPx()) + gap.toPx()
                IntOffset((x.value * pitchX - dragPx()).roundToInt(), (y.value * pitchY).roundToInt())
            }
            .size(cell)
            .graphicsLayer {
                clip = false
                scaleX = Motion.scaleFocus
                scaleY = Motion.scaleFocus
                this.alpha = alpha
            }
            .drawBehind {
                val cornerPx = radius.toPx()
                ring.reset()
                ring.addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(cornerPx, cornerPx)))
                // Only outside the tile, so the glow never washes over the art.
                clipPath(ring, ClipOp.Difference) {
                    with(FocusRing) { drawFocusRing(cornerPx) }
                }
            },
    )
}

/** Titles on [page], the same labels [Grid] draws, home sections included. */
private fun pageTitles(app: FoldcadeApp, page: Int, pageSize: Int): List<String> {
    val shell = app.shell
    val showingHome = !shell.model.libraryGrid && shell.model.homeGrid == HomeGrid.StandIns
    val order = displayOrder(shell.model)
    val start = page * pageSize
    val end = minOf(start + pageSize, shell.model.count)
    return (start until end).map { index ->
        val source = if (showingHome) index else order.getOrElse(index) { index }
        val face = if (showingHome) shell.homeFace(index) else null
        val title = shell.tileFromOrder(source)?.title.orEmpty()
        face?.section?.let { section -> "$section · $title" } ?: title
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
    onRowHeight: (Int) -> Unit,
) {
    val shell = app.shell
    val focus = shell.model.focus
    val pageSize = Metrics.columns * rows
    val library = shell.model.libraryGrid
    val showingHome = !library && shell.model.homeGrid == HomeGrid.StandIns
    val order = displayOrder(shell.model)
    val radius = cell * foldTheme().iconRadius
    val editing = shell.homeEditing()
    val lifted = shell.homeLiftedIndex()
    val allOpen = shell.homeAllOpen()
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        val start = page * pageSize
        for (row in 0 until rows) {
            // The first row always holds a tile, so its height is the row pitch the cursor steps by.
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                modifier = if (row == 0) Modifier.onSizeChanged { onRowHeight(it.height) } else Modifier,
            ) {
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
                        val libraryTile = library && face == null
                        val onHomeMark = face != null && allOpen && face.onGrid && !face.folder && !face.pinned
                        // A folder or an empty slot keeps its mark. A game asks for its art.
                        val artGame = game?.takeIf { face?.folder != true && face?.empty != true }
                        val art = key(artGame?.id) {
                            val found = rememberArt(app, artGame?.let(shell::artRecord), artGame?.let(shell::artQuery))
                            found?.let { rememberCoverPainter(app, it.square ?: it.cover, TILE_PX)?.painter }
                        }
                        Cell(
                            title = label,
                            mark = game?.mark,
                            art = art,
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
                            onServer = face?.onServer == true,
                            downloading = face?.id != null && face.id in shell.downloading,
                            library = libraryTile,
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
private const val GRID_RING_PAD = 8f

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
    art: Painter? = null,
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
    onServer: Boolean = false,
    downloading: Boolean = false,
    library: Boolean = false,
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
    val drawn by motionState(
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
                    if (!empty) {
                        // Every tile is a filled card, as on Cocoon's grid. A mark's glow is left
                        // as a halo around it. Focus is the grid's sliding ring; at rest a quiet edge.
                        drawRoundRect(color = theme.surface, cornerRadius = CornerRadius(cornerPx, cornerPx))
                        if (!focused && !editing) {
                            val strokePx = 2.5f
                            val inset = strokePx / 2f
                            drawRoundRect(
                                color = glow?.copy(alpha = 0.42f) ?: theme.onBackground.copy(alpha = 0.10f),
                                topLeft = Offset(inset, inset),
                                size = Size(bounds.width - strokePx, bounds.height - strokePx),
                                cornerRadius = CornerRadius(cornerPx, cornerPx),
                                style = Stroke(width = strokePx),
                            )
                        }
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
                // The ring and glows above draw outside. The art itself stays inside the
                // rounded tile, so a mark's square backing does not show at the corners.
                .clip(RoundedCornerShape(corner))
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
                .hostPress(onClick)
                // The title is on the tile, so the dump keeps it without a visible label.
                .clearAndSetSemantics { this[SemanticsProperties.Text] = listOf(AnnotatedString(title)) },
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
            } else if (art != null) {
                // Cover art fills the rounded tile. At rest it sits a little back, so focus reads.
                Image(
                    painter = art,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (focused || lifted) 1f else TILE_ART_REST_ALPHA },
                    contentScale = ContentScale.Crop,
                )
            } else if (!empty) {
                val glyphAlpha = if (focused) 1f else 0.58f
                if (kit != null) {
                    KitMark(kit, theme.artScale, glyphAlpha)
                } else if (mark != null && markGlyph(mark) != null) {
                    Box(Modifier.graphicsLayer { alpha = glyphAlpha }) {
                        MarkIcon(mark, theme.artScale)
                    }
                } else {
                    BasicText(
                        text = monogram(title),
                        modifier = Modifier.scale(theme.artScale).graphicsLayer { alpha = glyphAlpha },
                        style = text(
                            theme.onBackground,
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
            // On the server, not on this device. A downloaded game has no badge at all.
            // While it downloads the cloud takes the focus colour and breathes.
            if (onServer || downloading) {
                val breath = if (downloading && !Motion.reduced(animatorScale)) {
                    rememberInfiniteTransition(label = "download").animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = Motion.pulse(animatorScale),
                        label = "cloud",
                    )
                } else {
                    null
                }
                val tint = when {
                    downloading -> theme.focus
                    focused -> theme.onBackground
                    else -> theme.muted
                }
                Image(
                    painter = painterResource(R.drawable.ic_badge_cloud),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(px(8f))
                        .size(px(22f))
                        // Read in the layer, so each frame redraws without recomposing the tile.
                        .graphicsLayer { alpha = breath?.value ?: 1f },
                )
            }
        }
        if (showTitle) {
            BasicText(
                text = title,
                modifier = Modifier
                    .zIndex(1f)
                    .clearAndSetSemantics { }
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
                .clip(RoundedCornerShape(Metrics.cardCornerDp.dp))
                .background(theme.surface)
                .border(1.dp, theme.onBackground.copy(alpha = 0.08f), RoundedCornerShape(Metrics.cardCornerDp.dp))
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
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
                            .chip(focused)
                            .hostPress { app.shell.touchCell(index, screen) }
                            .padding(horizontal = px(20f), vertical = px(10f)),
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
    val drawn by motionState(
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
            .chip(focused)
            .hostPress(onClick)
            .padding(horizontal = px(22f), vertical = px(12f)),
        style = text(
            if (focused) theme.onBackground else theme.muted,
            TypeRamp.dialogBody,
            theme,
        ).copy(textAlign = TextAlign.Center),
    )
}

/**
 * The name field for a folder, under the grid. It takes focus and raises the
 * keyboard as it opens. The keyboard's Done saves like A; B keeps the old name.
 */
@Composable
private fun RenameField(app: FoldcadeApp, screen: HostScreen, draft: String) {
    val theme = foldTheme()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // The draft starts as the old name. The cursor goes after it so typing adds on, and select-all would hide it.
    var field by remember { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Column(Modifier.fillMaxWidth().padding(top = px(8f)), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(text = Copy.folderName, style = text(theme.muted, TypeRamp.hint, theme))
        BasicTextField(
            value = field,
            onValueChange = { next ->
                field = next
                app.shell.editRename(next.text)
            },
            singleLine = true,
            textStyle = text(theme.onBackground, TypeRamp.dialogBody, theme).copy(textAlign = TextAlign.Center),
            cursorBrush = cursorBrush(theme),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { app.shell.onMeaning(Meaning.Activate, screen) }),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .focusRequester(focus)
                .chip(true)
                .padding(horizontal = px(20f), vertical = px(10f)),
        )
    }
}

@Composable
private fun ConnectScreen(
    app: FoldcadeApp,
    screen: HostScreen,
    onEffect: (Effect?) -> Unit,
) {
    val theme = foldTheme()
    val model = app.shell.model
    val artKey = model.connectKind == ConnectKind.ArtKey
    val fields = connectFields(model.connectKind)
    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(px(Metrics.dialogInsetPx)),
        verticalArrangement = Arrangement.spacedBy(px(16f)),
    ) {
        BasicText(
            text = if (artKey) Copy.steamGridDb else Copy.connectRomm,
            style = text(theme.onBackground, TypeRamp.dialogTitle, theme),
        )
        if (!artKey) {
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
                    .chip(focused(model, ConnectField.Origin))
                    .onFocusChanged { state ->
                        if (state.isFocused) app.shell.touchConnect(fields.indexOf(ConnectField.Origin), screen)
                    }
                    .padding(horizontal = px(20f), vertical = px(10f)),
            )
            val warning = model.connectWarning
            if (warning != null) {
                BasicText(text = warning, style = text(theme.onBackground, TypeRamp.dialogBody, theme))
            }
        }
        val hint = model.connectHint
        if (hint != null) {
            BasicText(text = hint, style = text(theme.onBackground, TypeRamp.dialogBody, theme))
        }
        BasicText(text = if (artKey) Copy.apiKey else Copy.clientApiToken, style = text(theme.muted, TypeRamp.hint, theme))
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
                .chip(focused(model, ConnectField.Token))
                .onFocusChanged { state ->
                    if (state.isFocused) app.shell.touchConnect(fields.indexOf(ConnectField.Token), screen)
                }
                .padding(horizontal = px(20f), vertical = px(10f)),
        )
        BasicText(
            text = if (artKey) Copy.saveKey else Copy.saveToken,
            modifier = Modifier
                .chip(focused(model, ConnectField.Save))
                .hostPress { onEffect(app.shell.touchConnect(fields.indexOf(ConnectField.Save), screen)) }
                .padding(horizontal = px(20f), vertical = px(10f)),
            style = text(theme.onBackground, TypeRamp.dialogBody, theme),
        )
        BasicText(
            text = if (artKey) Copy.artKeyHelp else Copy.rommTokenHelp,
            style = text(theme.muted, TypeRamp.dialogBody, theme),
        )
    }
}

private fun focused(model: app.foldcade.language.PickerModel, field: ConnectField): Boolean {
    val fields = connectFields(model.connectKind)
    return fields[model.connectIndex.coerceIn(fields.indices)] == field
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
    val lift = foldTheme().onBackground
    return this.graphicsLayer { clip = false }.drawBehind {
        if (!focused) return@drawBehind
        drawRoundRect(
            color = lift.copy(alpha = 0.06f),
            cornerRadius = CornerRadius(22f, 22f),
        )
        with(FocusRing) { drawFocusRing(22f) }
    }
}

/**
 * A chip: a faint filled pill, and the same focus ring as tiles and menu rows
 * while it has focus. Tabs, actions, and fields all use it.
 */
@Composable
private fun Modifier.chip(focused: Boolean): Modifier {
    val lift = foldTheme().onBackground
    return this.graphicsLayer { clip = false }.drawBehind {
        val corner = min(size.height / 2f, CHIP_CORNER_PX)
        drawRoundRect(
            color = lift.copy(alpha = if (focused) 0.10f else 0.06f),
            cornerRadius = CornerRadius(corner, corner),
        )
        if (focused) with(FocusRing) { drawFocusRing(corner) }
    }
}

private const val CHIP_CORNER_PX = 44f
