package app.foldcade.ui

import androidx.compose.animation.Crossfade

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.lerp
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.foldcade.language.actionFaces
import app.foldcade.language.heroPlayLine
import app.foldcade.language.openPlayerLabel
import app.foldcade.language.DateStyle
import app.foldcade.language.typingFaces
import app.foldcade.language.FindEntry
import app.foldcade.language.FindKind
import app.foldcade.language.withFindHint
import app.foldcade.language.heroFacts
import app.foldcade.R
import app.foldcade.language.QuickSetting
import app.foldcade.DeviceStatus
import app.foldcade.Displays
import app.foldcade.language.ArtCopy
import app.foldcade.language.ArtKind
import app.foldcade.FoldcadeApp
import app.foldcade.FoldcadeHomeActivity
import app.foldcade.Panel
import app.foldcade.Surface
import app.foldcade.RunningApp
import app.foldcade.CenterFocus
import app.foldcade.language.AchievementCopy
import app.foldcade.language.achievementLine
import app.foldcade.language.achievementPoints
import app.foldcade.language.PlayCopy
import app.foldcade.language.SettingsCopy
import app.foldcade.language.promptKeyOf
import app.foldcade.language.rowKeys
import app.foldcade.language.rowControl
import app.foldcade.language.tileLabel
import app.foldcade.language.tileBlock
import app.foldcade.CoverImage
import app.foldcade.artwork.ArtQuery
import app.foldcade.artwork.ArtScene
import app.foldcade.artwork.ArtSet
import app.foldcade.api.plugin.Game
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.asPainter
import coil3.memory.MemoryCache
import coil3.request.SuccessResult
import java.util.concurrent.ConcurrentHashMap
import coil3.request.ImageRequest
import coil3.request.crossfade
import app.foldcade.language.ConnectField
import app.foldcade.language.ConnectKind
import app.foldcade.language.Copy
import app.foldcade.language.HomeGrid
import app.foldcade.language.FOLDER_ICONS
import app.foldcade.language.CollectionLook
import app.foldcade.language.MARK_CONSOLE
import app.foldcade.language.SCENE_SINCE_MOVE_MAX
import app.foldcade.language.SceneLive
import app.foldcade.language.CollectionTint
import app.foldcade.language.IconPick
import app.foldcade.language.homeGridLabel
import app.foldcade.language.dialogButtonLabel
import app.foldcade.language.Effect
import app.foldcade.language.DialogState
import app.foldcade.language.EmptyGrid
import app.foldcade.language.GridKind
import app.foldcade.language.HeroBlend
import app.foldcade.language.HeroItem
import app.foldcade.language.HeroSubject
import app.foldcade.language.HeroTravel
import app.foldcade.language.heroTravel
import app.foldcade.language.HintActions
import app.foldcade.language.HintPlace
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.Row
import app.foldcade.language.FoldNotice
import app.foldcade.language.Metrics
import app.foldcade.language.GridStrip
import app.foldcade.language.shownFirstColumn
import app.foldcade.language.MotionSpeed
import app.foldcade.language.SidePanel
import app.foldcade.language.GlowFalloff
import app.foldcade.language.builtInTheme
import app.foldcade.language.connectFields
import app.foldcade.language.triggerHints
import app.foldcade.language.hintFor
import app.foldcade.language.HintKey
import app.foldcade.language.HomeKeys
import app.foldcade.language.PromptKey
import app.foldcade.language.gridHints
import app.foldcade.language.letterOfKey
import app.foldcade.language.letterbox
import app.foldcade.language.cursorBrush
import app.foldcade.language.displayOrder
import app.foldcade.language.heroCopy
import app.foldcade.language.menuFit
import app.foldcade.language.menuScrollTarget
import app.foldcade.language.heroCrossfadeActive
import app.foldcade.language.monogram
import app.foldcade.language.panelRows
import app.foldcade.language.panelHints
import app.foldcade.language.playActivityHints
import app.foldcade.language.noticeAge
import app.foldcade.language.quickTileColumns
import app.foldcade.language.rowLabel
import app.foldcade.language.retargetHero
import app.foldcade.language.Row as LanguageRow
import app.foldcade.language.rowText
import app.foldcade.language.SaveHealth
import app.foldcade.language.needsAttention
import app.foldcade.language.saveHealthLine
import app.foldcade.language.saveHealthLeads
import app.foldcade.language.TileActivity
import app.foldcade.language.PlayKey
import app.foldcade.language.activityLine
import app.foldcade.language.worked
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.hypot
import app.foldcade.HomeCarry
import app.foldcade.language.SelectionCopy
import app.foldcade.HomeFace
import app.foldcade.ShelfGame
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState

@Composable
fun PanelHost(activity: FoldcadeHomeActivity, displays: Displays) {
    val app = activity.application as FoldcadeApp
    val session = app.store.session
    val panel = displays.panelFor(activity, session.defaultDisplayIsTop)
    val paint = app.paintFor(app.shell.model.themeIndex)
    val motion = paint.theme.motion
    val scale = motion.animatorScale(LocalContext.current.contentResolver)
    val model = app.shell.model
    LaunchedEffect(activity.shellVisible, panel, model.deleteHold?.started, model.undoVerb) {
        if (panel != Panel.Top || !activity.shellVisible) {
            if (panel == Panel.Top) app.shell.cancelDeleteHold()
            return@LaunchedEffect
        }
        while (app.shell.model.deleteHold != null || app.shell.model.undoVerb != null) {
            activity.dispatch(app.shell.tickRecovery())
            kotlinx.coroutines.delay(if (app.shell.model.deleteHold != null) 16L else 100L)
        }
    }
    val screen = when (panel) {
        Panel.Top -> HostScreen.Top
        Panel.Bottom -> HostScreen.Bottom
        null -> null
    }
    androidx.compose.runtime.DisposableEffect(screen, activity.inputWindow) {
        screen?.let { app.shell.registerInputWindow(it, activity.inputWindow) }
        onDispose { app.shell.cancelDeleteHold(activity.inputWindow) }
    }
    val ownsBottom = session.surfaceOn(Panel.Bottom) != null
    // A full-screen page on the other screen leaves this one its command center, not a blurred grid.
    val besidePage = screen != null && pageBeside(model, session, screen) != null
    val menuVisible = (
        model.panel?.screen == HostScreen.Top ||
            model.settings?.screen == HostScreen.Top ||
            model.playActivity?.screen == HostScreen.Top
        ) && model.dialog?.screen != HostScreen.Top
    val blurHere = screen == HostScreen.Bottom &&
        ownsBottom &&
        !besidePage &&
        menuVisible &&
        model.dialog?.screen != HostScreen.Bottom
    val heldBlur = if (blurHere) app.islandHold?.progress else null
    val blurTarget = heldBlur ?: if (blurHere) 1f else 0f
    val blur by motionState(
        target = blurTarget,
        spec = if (blurTarget >= 1f) motion.arrive(motion.durationIsland, scale) else motion.leave(motion.durationIsland, scale),
        snap = heldBlur != null,
    )
    // Set up saves beside an emulator shows that emulator's steps, so it stays at the panel's own brightness.
    val dimmed = panel != null && app.commandCenter.dimmed(panel) && model.saveSetup == null
    // Either panel's level can move while dimmed, and a darker one still wins.
    LaunchedEffect(dimmed, app.brightness.revision) { activity.applyCommandCenterDim(dimmed) }
    // Leaving a window forgets what each graphics layer reads, and the next window
    // only lays out and draws again, so the grid's ring stopped where it was. A layer
    // runs again when its density changes: each window gets the same one, new.
    val base = LocalDensity.current
    val density = remember(base, activity.panelWindows) { WindowDensity(base) }
    // A closed menu draws neither the blur nor the scrim. A graphics layer
    // left over the library, even at zero alpha, keeps the accessibility dump empty.
    CompositionLocalProvider(LocalFoldTheme provides paint, LocalDensity provides density) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(activity.panelFocus)
                .background(paint.theme.background)
                .wakesCommandCenter(app, panel),
        ) {
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
            Box(Modifier.fillMaxSize().menuBlur { blur }.menuDim { blur }) {
                CompositionLocalProvider(LocalUnderMenu provides blurHere) {
                    val rewind = remember { androidx.compose.animation.core.Animatable(1f) }
                    LaunchedEffect(model.rewind) {
                        if (model.rewind == 0) return@LaunchedEffect
                        rewind.snapTo(paint.theme.style.motion.rewindFrom)
                        rewind.animateTo(1f, motion.playful(motion.durationTravel, scale))
                    }
                    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = rewind.value; scaleY = rewind.value }) {
                        PanelBody(activity, panel, screen, scale)
                    }
                }
            }
            if (screen == HostScreen.Top) LetterOverlay(app, scale)
            if (blurHere && session.surfaceOn(Panel.Bottom) == Surface.Picker) MenuHints(app)
            // The blurred grid takes no taps of its own. A tap on it closes the island over it.
            if (blurHere && model.panel?.screen == HostScreen.Top) Box(Modifier.fillMaxSize().closesIsland(app))
            // Saves rises over both screens as Settings does, and leaves as it was.
            val saves = presence(model.savePicker, motion.durationIsland, scale)
            saves.shown?.let { shown ->
                Box(Modifier.fillMaxSize().rise { saves.progress.value }) {
                    if (screen == HostScreen.Top) SavePickerTop(app, shown, rememberDateStyle(app), scale)
                    if (screen == HostScreen.Bottom) SavePickerBottom(app, shown, rememberDateStyle(app), scale, activity::dispatch)
                }
            }
            // Set up saves rises over both screens too. Beside a game or an emulator it takes the one screen there is.
            val setup = presence(model.saveSetup, motion.durationIsland, scale)
            setup.shown?.let { shown ->
                val split = session.bothScreensFree()
                Box(Modifier.fillMaxSize().rise { setup.progress.value }) {
                    when {
                        split && screen == HostScreen.Top -> SaveSetupTop(app, shown, scale, activity::dispatch)
                        split && screen == HostScreen.Bottom -> SaveSetupBottom(app, shown, scale, activity::dispatch)
                        !split && screen != null && panel != null && session.surfaceOn(panel) != null ->
                            SaveSetupSingle(app, shown, screen, scale, activity::dispatch)
                    }
                }
            }
            if (panel != null && screen != null) {
                DialogLayer(app, model.dialog, screen, scale, activity::dispatch)
            }
            val conflict = model.saveConflict
            if (conflict != null && screen == HostScreen.Top) SaveConflictTop(conflict)
            if (conflict != null && screen == HostScreen.Bottom) {
                SaveConflictBottom(
                    conflict,
                    dateStyle = rememberDateStyle(app),
                    onFocus = { focus -> activity.dispatch(app.shell.touchSaveConflict(focus)) },
                    onLater = { activity.dispatch(app.shell.touchSaveConflictLater()) },
                    backKey = promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB,
                )
            }
        }
    }
}

/**
 * What the shell tells a theme's scene each time it draws: the focused item's accent, eased as
 * focus moves, the time of day, the battery, how long since the cursor last moved, and where
 * [camera] says the grid has slid to.
 */
@Composable
private fun sceneLive(app: FoldcadeApp, scene: app.foldcade.language.ThemeScene?, scale: Float, camera: BackdropCamera): () -> SceneLive {
    val paint = LocalFoldTheme.current
    val motion = paint.theme.motion
    val accent = sceneAccent(app, paint)
    val spec = motion.arrive(motion.durationFocus, scale)
    val red = motionState(accent.red, spec)
    val green = motionState(accent.green, spec)
    val blue = motionState(accent.blue, spec)
    val status = app.deviceStatus.status.collectAsStateWithLifecycle()
    val model = app.shell.model
    val moves = remember { intArrayOf(0) }
    val moved = remember { mutableLongStateOf(Long.MIN_VALUE) }
    // The first focus is where the grid opened, not a move.
    LaunchedEffect(model.focus, model.panel?.index) { if (moves[0]++ > 0) moved.longValue = SystemClock.elapsedRealtime() }
    // Only what the scene reads is read while it draws, so a scene that ignores the accent is not
    // drawn again each time focus moves.
    val readsAccent = scene != null && listOf("accentR", "accentG", "accentB").any(scene::reads)
    val readsMove = scene?.reads("sinceMove") == true
    // The clock and the time zone cost more than the rest of a frame's inputs, and few scenes read them.
    val readsHour = scene?.reads("hour") == true
    // Each frame of a slide redraws a scene that follows it, and only that one.
    val readsScroll = scene?.reads("scroll") == true
    return remember(red, green, blue, status, moved, readsAccent, readsMove, readsHour, readsScroll, camera) {
        {
            val hour = if (!readsHour) UNREAD_HOUR else java.time.LocalTime.now().let { now ->
                now.hour + now.minute / 60f + now.second / 3600f
            }
            // Read only by a scene that uses it, so moving the cursor redraws no other scene.
            val since = if (!readsMove) null else moved.longValue.takeIf { it != Long.MIN_VALUE }?.let { (SystemClock.elapsedRealtime() - it) / 1000f }
            SceneLive(
                accentR = if (readsAccent) red.value else 1f,
                accentG = if (readsAccent) green.value else 1f,
                accentB = if (readsAccent) blue.value else 1f,
                hour = hour,
                battery = status.value.batteryPercent.coerceIn(0, 100) / 100f,
                charging = status.value.charging,
                sinceMove = (since ?: SCENE_SINCE_MOVE_MAX).coerceIn(0f, SCENE_SINCE_MOVE_MAX),
                scroll = if (readsScroll) camera.scroll else 0f,
            )
        }
    }
}

/** What [SceneLive.hour] holds for a scene that never reads it. */
private val UNREAD_HOUR = SceneLive().hour

/** The light behind the focused item: its app's or mark's accent, a collection's colour, else `focus`. */
private fun sceneAccent(app: FoldcadeApp, paint: FoldPaint): Color {
    val (mark, packageName) = when (val subject = app.shell.focusedHero()) {
        is app.foldcade.language.HeroSubject.Item -> subject.item.mark to subject.item.packageName
        is app.foldcade.language.HeroSubject.Folder -> subject.folder.mark to null
        null -> null to null
    }
    packageName?.let { paint.icons["app.$it"]?.firstOrNull()?.accent }?.let { return it }
    CollectionLook.parse(mark)?.let { return paint.theme.style.collections.color(it.tint) }
    mark?.let { (paint.icons["mark.$it"] ?: paint.icons["mark.$MARK_CONSOLE"])?.firstOrNull()?.accent }?.let { return it }
    return paint.theme.focus
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
    // One camera for both screens: the top's scene slides with the bottom's grid.
    val camera = app.backdropCamera
    // Settings and Play activity paint the theme's background over the whole panel.
    val pageHere = screen != null && (model.settings?.screen == screen || model.playActivity?.screen == screen)
    val settingsOver = pageHere && foldTheme().background.alpha >= 1f
    val page = screen?.let { pageBeside(model, session, it) }
    val centerHere = page != null || (panel != null && session.surfaceOn(panel) == Surface.CommandCenter)
    val motion = LocalFoldTheme.current.theme.motion
    // Settings and Play activity rise over the panel and sink away again. Each keeps its
    // last state while it leaves, so it fades out as it was rather than vanishing.
    val settingsPage = model.settings?.takeIf { it.screen == screen }
    val activityPage = model.playActivity?.takeIf { it.screen == screen }
    val settings = presence(settingsPage, motion.durationIsland, scale)
    val played = presence(activityPage, motion.durationIsland, scale)
    val beside = presence(page, motion.durationIsland, scale)
    // Only whether a page fully covers the panel, so the fades do not recompose it each frame.
    val covered by remember {
        derivedStateOf { maxOf(settings.progress.value, played.progress.value, beside.progress.value) >= 1f }
    }
    // The connection form takes the hero's place.
    val connectHere = screen != null && model.connectOpen && model.connectScreen == screen
    val running = activity.shellVisible && session.bothScreensFree()
    val backdrop = app.backgroundFor(model.backgroundMotion)
    // The icon editor hides the hero's art, so with no backdrop behind it nothing is left to slide.
    val nothingToSlide = backdrop == null && app.shell.folderIconPick() != null
    // Moving the Thor slides the hero's layers a touch, deepest most. Only while the top screen's hero shows.
    val tiltHere = panel == Panel.Top && running && session.surfaceOn(panel) == Surface.Hero && !covered && !connectHere &&
        !nothingToSlide && !motion.heroFadeOnly(model.motionSpeed, scale)
    val tilt = rememberTilt(tiltHere, motion)
    Box(if (panel == Panel.Top) Modifier.fillMaxSize().tilted(tilt, motion, 1f, cover = true) else Modifier.fillMaxSize()) {
        Backdrop(
            scene = backdrop,
            panel = if (panel == Panel.Bottom) 1f else 0f,
            speed = model.motionSpeed,
            animatorScale = scale,
            running = running,
            // Opaque game art or Settings over it, or the menu's blur, leaves nothing to watch move.
            held = cover.covered || settingsOver || LocalUnderMenu.current || centerHere,
            live = sceneLive(app, backdrop, scale, camera),
        )
    }
    if (panel == null || screen == null) return
    val artPage = presence(app.shell.artPicker, motion.durationIsland, scale)
    if (!covered) {
        CompositionLocalProvider(LocalBackdropCover provides cover, LocalBackdropCamera provides camera, LocalTilt provides tilt) {
            TravelFade(target = session.surfaceOn(panel), scale = scale) { shown ->
                when (shown) {
                    null -> Unit
                    Surface.Hero -> if (connectHere) Unit else ArtPreviewHero(app, screen, scale, activity::dispatch)
                    Surface.Picker -> Picker(
                        app,
                        screen,
                        scale,
                        activity::dispatch,
                        editorHere = session.editorPanel() == panel,
                        live = session.surfaceOn(panel) == Surface.Picker,
                    )
                    Surface.CommandCenter -> CommandCenterScreen(
                        app,
                        CenterSubject.App(panel, app.commandCenter.beside(panel)),
                        hints = false,
                    ) { app.commandCenter.browse(panel) }
                }
            }
        }
        if (connectHere) ConnectScreen(app, screen, activity::dispatch, activity::takeKeyboardFocus)
    }
    if (screen == HostScreen.Bottom) artPage.shown?.let { shown ->
        Box(Modifier.fillMaxSize().rise { artPage.progress.value }) { ArtPickerScreen(app, shown, scale) }
    }
    beside.shown?.let { subject ->
        // The other screen is still Foldcade, so it is never dimmed. Its keys still drive the page.
        // Play activity names its keys here; Settings shows its own on the page.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = beside.progress.value }) {
            CommandCenterScreen(app, subject, hints = !subject.settings) {
                if (subject.settings) app.shell.closeSettings() else app.shell.closePlayActivity()
            }
        }
    }
    settings.shown?.let { shown ->
        Box(Modifier.fillMaxSize().rise { settings.progress.value }) {
            SettingsScreen(app, screen, shown, scale, activity::dispatch)
        }
    }
    played.shown?.let { shown ->
        Box(Modifier.fillMaxSize().rise { played.progress.value }) { PlayActivityScreen(app, shown) }
    }
}

/** [base] under its own identity, so every layer under it counts it as a new density. */
private class WindowDensity(base: Density) : Density by base

/** Dim painted with the content, not a second full-screen node on top of it. */
@Composable
private fun Modifier.menuDim(progress: () -> Float): Modifier {
    val design = foldTheme().style
    val scrim = design.colors.scrim
    // Without a blur behind it, the dim alone has to quiet the grid.
    val dim = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) design.menu.scrimAlpha else design.menu.scrimAlphaUnblurred
    return drawWithContent {
        drawContent()
        val amount = progress()
        if (amount <= 0f) return@drawWithContent
        drawRect(scrim.copy(alpha = dim * amount.coerceIn(0f, 1f)))
    }
}

/** Blur only while the radius is visible. A zero-radius graphics layer still hides nodes. */
@Composable
private fun Modifier.menuBlur(progress: () -> Float): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val reach = rememberUpdatedState(foldTheme().style.menu.blurPx)
    val latest = rememberUpdatedState(progress)
    val visible by remember { derivedStateOf { latest.value() * reach.value >= 0.5f } }
    if (!visible) return this
    return graphicsLayer {
        val radius = (progress() * reach.value).coerceAtLeast(0.5f)
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

@Composable
private fun motionFloat(target: Float, spec: FiniteAnimationSpec<Float>, snap: Boolean = false): Float =
    motionState(target, spec, snap).value

/** What [glideState] follows. [jump] is a token: a new one lands at once, as [snap] does. */
private data class GlideTo(val target: Float, val snap: Boolean, val jump: Any?, val scale: Float)

/**
 * [target] reached with [motion.glide], as state. One coroutine follows every change and
 * reads the speed while the last move still runs, so a held move carries it on instead
 * of starting over at each repeat. Cancelling an animation clears its speed, so a keyed
 * effect could not. [snap], or a new [jump], lands at once. Pass [anim] to move it
 * by hand between targets: the next glide starts from wherever it was left, and a
 * glide started there to the new target is left to run.
 */
@Composable
private fun glideState(
    target: Float,
    base: Int,
    scale: Float,
    snap: Boolean = false,
    jump: Any? = null,
    anim: Animatable<Float, AnimationVector1D> = remember { Animatable(target) },
): State<Float> {
    val motion = LocalFoldTheme.current.theme.motion
    val latest = rememberUpdatedState(GlideTo(target, snap, jump, scale))
    LaunchedEffect(anim, base) {
        var landed = latest.value.jump
        withContext(SteadyMenu) {
            snapshotFlow { latest.value }.collect { next ->
                if (next.snap || next.jump != landed) {
                    landed = next.jump
                    anim.snapTo(next.target)
                    return@collect
                }
                // Already on its way there, as a strip let go by a finger is: it keeps the speed it was given.
                if (anim.isRunning && anim.targetValue == next.target) return@collect
                val velocity = anim.velocity
                val spec = motion.glide(base, next.scale, next.target - anim.value, velocity)
                launch { anim.animateTo(next.target, spec) }
            }
        }
    }
    return anim.asState()
}

@Composable
private fun <T> TravelFade(target: T, scale: Float, content: @Composable (T) -> Unit) {
    val motion = LocalFoldTheme.current.theme.motion
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
        withContext(SteadyMenu) {
            coroutineScope {
                launch { incoming.animateTo(1f, motion.arrive(motion.durationTravel, scale)) }
                launch { outgoing.animateTo(0f, motion.leave(motion.durationTravel, scale)) }
            }
        }
        back = null
        incoming.snapTo(1f)
    }
    val previous = back
    val moving by remember { derivedStateOf { back != null || incoming.value < 0.999f } }
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

/**
 * Every press on a panel that has a command center, or a grid opened from one.
 * Seen before the grid does, and never consumed.
 */
private fun Modifier.wakesCommandCenter(app: FoldcadeApp, panel: Panel?): Modifier {
    if (panel == null) return this
    return pointerInput(panel) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) app.commandCenter.touch(panel)
            }
        }
    }
}

/** What a command center stands beside: an app on the other screen, or a Foldcade page there. */
private sealed interface CenterSubject {
    data class App(val panel: Panel, val running: RunningApp?) : CenterSubject

    data class Page(val title: String, val icon: Int?, val mark: String?, val settings: Boolean) : CenterSubject
}

/**
 * Settings or Play activity filling the other screen while both are Foldcade's.
 * This screen then says where the user is, the way it would beside an app.
 */
private fun pageBeside(
    model: app.foldcade.language.PickerModel,
    session: app.foldcade.Session,
    screen: HostScreen,
): CenterSubject.Page? {
    if (!session.bothScreensFree()) return null
    val settings = model.settings
    val playActivity = model.playActivity
    return when {
        settings != null && settings.screen != screen ->
            CenterSubject.Page(SettingsCopy.title, R.drawable.ic_status_settings, null, settings = true)
        playActivity != null && playActivity.screen != screen ->
            CenterSubject.Page(PlayCopy.activity, null, "console", settings = false)
        else -> null
    }
}

/**
 * The quiet screen: true black, so an OLED panel lights only one small cluster.
 * The app's art and name, the time, the battery, and one button that opens the
 * grid here. A RomM game with RetroAchievements adds its progress and a second
 * button for the list. The cluster steps a little each minute, so a long game
 * does not leave it lit in one place.
 */
@Composable
private fun CommandCenterScreen(
    app: FoldcadeApp,
    subject: CenterSubject,
    hints: Boolean,
    onOpen: () -> Unit,
) {
    val design = LocalFoldTheme.current.theme.style
    val theme = foldTheme()
    val status by app.deviceStatus.status.collectAsStateWithLifecycle()
    // The wall clock's minute, so the cluster keeps stepping when this screen is shown again.
    val (driftX, driftY) = CENTER_DRIFT[(status.minute % CENTER_DRIFT.size).toInt()]
    val center = app.commandCenter
    val panel = (subject as? CenterSubject.App)?.panel
    val achievements = panel?.let(center::achievements)
    val title = when (subject) {
        is CenterSubject.App -> subject.running?.title?.ifEmpty { null } ?: appLabel(subject.running?.packageName)
        is CenterSubject.Page -> subject.title
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(theme.background),
    ) {
        if (panel != null && achievements != null && center.listOn == panel) {
            AchievementList(app, achievements, center.listIndex, onBack = center::closeList)
            return@BoxWithConstraints
        }
        val titleMax = maxWidth * 0.5f
        Row(
            Modifier
                .align(Alignment.Center)
                .offset(px(driftX), px(driftY)),
            horizontalArrangement = Arrangement.spacedBy(px(40f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(px(CENTER_ART_PX)), contentAlignment = Alignment.Center) {
                when (subject) {
                    is CenterSubject.App -> CenterArt(app, subject.running)
                    is CenterSubject.Page -> when {
                        subject.icon != null -> InkIcon(subject.icon, Modifier.fillMaxSize(0.62f))
                        subject.mark != null -> MarkIcon(subject.mark, theme.style.hero.markScale)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(px(8f))) {
                BasicText(text = status.time, style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.clock, theme, display = true), maxLines = 1)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(px(10f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InkIcon(batteryStatusIcon(status.batteryPercent, status.charging), Modifier.size(px(54f), px(36f)))
                    BasicText(
                        text = listOf("${status.batteryPercent}%", status.date).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = text(theme.muted, LocalFoldTheme.current.theme.style.type.heroMeta, theme),
                        maxLines = 1,
                    )
                }
                if (title != null) {
                    BasicText(
                        text = title,
                        modifier = Modifier.widthIn(max = titleMax),
                        style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.sideRow, theme),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val line = achievements?.let { found ->
                    val total = found.achievements.size
                    achievementLine(if (found.progressKnown) found.achievements.count { it.earned } else null, total)
                }
                if (line != null) {
                    BasicText(text = line, style = text(theme.focus, LocalFoldTheme.current.theme.style.type.heroMeta, theme), maxLines = 1)
                }
                Spacer(Modifier.height(px(12f)))
                // Beside an app the keys press these buttons. Beside a page they drive the page.
                val keyed = subject is CenterSubject.App
                Row(horizontalArrangement = Arrangement.spacedBy(px(16f))) {
                    val onApps = !keyed || achievements == null || center.focus == CenterFocus.OpenApp
                    CenterButton(app, Copy.openAnApp, focused = onApps, keyed = keyed, onClick = onOpen)
                    if (panel != null && achievements != null) {
                        CenterButton(
                            app,
                            AchievementCopy.title,
                            focused = !onApps,
                            keyed = keyed,
                            onClick = { center.openList(panel) },
                        )
                    }
                }
            }
        }
        if (hints) {
            val inset = maxWidth * design.metrics.insetFraction
            Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.BottomCenter) {
                GridHints(app)
            }
        }
    }
}

/**
 * A command center button. The focused one carries the confirm key's glyph when keys can press it.
 * Beside a page the keys drive the page, so no button wears the ring: they are plain touch buttons.
 */
@Composable
private fun CenterButton(app: FoldcadeApp, label: String, focused: Boolean, keyed: Boolean, onClick: () -> Unit) {
    val theme = foldTheme()
    val confirm = promptKeyOf(app.shell.model.faceMap.confirmKey)
    Row(
        Modifier.chip(focused && keyed).hostPress(onClick).padding(horizontal = px(24f), vertical = px(12f)),
        horizontalArrangement = Arrangement.spacedBy(px(10f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (keyed && focused && confirm != null) PromptImage(promptGlyph(confirm), 24.dp)
        BasicText(text = label, style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.dialogBody, theme), maxLines = 1)
    }
}

/**
 * The game's achievements, one row each: the badge, the title and what it asks,
 * and its points. Earned rows are bright; the rest are muted, and only when
 * RomM knows the user's progress. Up and down move the keys' row into view.
 */
@Composable
private fun AchievementList(
    app: FoldcadeApp,
    found: app.foldcade.api.plugin.GameAchievements,
    index: Int,
    onBack: () -> Unit,
) {
    val design = LocalFoldTheme.current.theme.style
    val theme = foldTheme()
    val inset = px(design.metrics.heroInsetPx)
    val rows = found.achievements
    Column(Modifier.fillMaxSize().padding(inset), verticalArrangement = Arrangement.spacedBy(px(16f))) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(px(16f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = AchievementCopy.back,
                modifier = Modifier.chip(false).hostPress(onBack).padding(horizontal = px(20f), vertical = px(10f)),
                style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.dialogBody, theme),
                maxLines = 1,
            )
            val earned = if (found.progressKnown) rows.count { it.earned } else null
            BasicText(
                text = achievementLine(earned, rows.size).orEmpty(),
                style = text(theme.focus, LocalFoldTheme.current.theme.style.type.heroMeta, theme),
                maxLines = 1,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // The scroll clips above and below, so the first and last rows' rings need this room.
                .padding(vertical = ringReach()),
            verticalArrangement = Arrangement.spacedBy(px(8f)),
        ) {
            rows.forEachIndexed { row, achievement ->
                val dim = found.progressKnown && !achievement.earned
                Row(
                    Modifier
                        .fillMaxWidth()
                        .rowHighlight(row == index)
                        .keepInView(row == index)
                        .padding(horizontal = px(16f), vertical = px(10f)),
                    horizontalArrangement = Arrangement.spacedBy(px(16f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(px(ACHIEVEMENT_BADGE_PX)).alpha(if (dim) 0.55f else 1f)) {
                        achievement.badge?.let { rememberCoverPainter(app, it, ACHIEVEMENT_BADGE_PX.toInt()) }?.let { badge ->
                            Image(
                                painter = badge.painter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(px(design.shape.badgeCornerPx))),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            text = achievement.title,
                            style = text(if (dim) theme.muted else theme.onBackground, LocalFoldTheme.current.theme.style.type.menuRow, theme),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        achievement.description?.let {
                            BasicText(
                                text = it,
                                style = text(theme.muted, LocalFoldTheme.current.theme.style.type.hint, theme),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    achievementPoints(achievement.points)?.let {
                        BasicText(text = it, style = text(theme.muted, LocalFoldTheme.current.theme.style.type.hint, theme), maxLines = 1)
                    }
                }
            }
        }
    }
}

private const val ACHIEVEMENT_BADGE_PX = 96f

/**
 * The app's cover, then the theme's object for it or its launcher icon, then its platform mark.
 * An app Foldcade did not open shows Android's.
 */
@Composable
private fun CenterArt(app: FoldcadeApp, running: RunningApp?) {
    val design = LocalFoldTheme.current.theme.style
    val theme = foldTheme()
    val themedApp = appGlyph(running?.packageName)
    val icon = if (themedApp == null) launcherIcon(running?.packageName) else null
    val cover = running?.let { rememberArt(app, it.record, it.query) }?.cover?.let { rememberCoverPainter(app, it, COVER_PX) }
    val mark = running?.mark ?: "android-apps"
    when {
        cover != null -> Image(
            painter = cover.painter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(cover.ratio, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp / 2)),
            contentScale = ContentScale.Crop,
        )
        icon != null -> LauncherImage(icon, contentDescription = null, modifier = Modifier.fillMaxSize(theme.artScale))
        else -> (themedApp ?: markGlyph(mark))?.let { MarkIcon(it, it.heroScale(theme)) }
    }
}

@Composable
private fun appLabel(packageName: String?): String? {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName == null) return@remember null
        runCatching {
            val manager = context.packageManager
            manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
    }
}

/** Where the command center's cluster sits each minute, in px from the centre. Small enough to read as still. */
private val CENTER_DRIFT = listOf(
    0f to 0f, 14f to 6f, -8f to 16f, 16f to -12f, -16f to -6f, 6f to -16f, -6f to 12f, 10f to 10f,
)
private const val CENTER_ART_PX = 300f

private data class ArtPreview(val gameId: String, val kind: ArtKind, val url: String)
private val LocalArtPreview = compositionLocalOf<ArtPreview?> { null }

@Composable
private fun ArtPreviewHero(app: FoldcadeApp, screen: HostScreen, scale: Float, onEffect: (Effect?) -> Unit) {
    val picker = app.shell.artPicker?.takeIf { it.preview }
    val preview = picker?.focused?.let { ArtPreview(picker.gameId, picker.kind, it.url) }
    val motion = LocalFoldTheme.current.theme.motion
    Box(Modifier.fillMaxSize()) {
        Crossfade(preview, animationSpec = motion.arrive(motion.durationScene, scale)) { shown ->
            CompositionLocalProvider(LocalArtPreview provides shown) { Hero(app, screen, scale, onEffect) }
        }
        if (screen == HostScreen.Top) {
            Crossfade(app.shell.artPicker?.match?.name,
                animationSpec = motion.arrive(motion.durationShort, scale),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp, start = 24.dp, end = 24.dp),
            ) { name ->
                if (name != null) {
                    val theme = foldTheme()
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BasicText(ArtCopy.matched(name), Modifier.clip(RoundedCornerShape(theme.style.shape.toastCornerDp.dp))
                            .background(theme.surface.copy(alpha = theme.style.dialog.toastAlpha)).padding(horizontal = 12.dp, vertical = 4.dp),
                            style = text(theme.muted, theme.style.type.hint, theme), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(app: FoldcadeApp, screen: HostScreen, scale: Float, onEffect: (Effect?) -> Unit) {
    val motion = LocalFoldTheme.current.theme.motion
    val design = LocalFoldTheme.current.theme.style
    val theme = foldTheme()
    val model = app.shell.model
    val editor = presence(app.shell.folderIconPick(), motion.durationIsland, scale)
    // While a name is typed the hero is what is being named, wherever the grid's focus is.
    val naming = app.shell.typingHero()
    // An empty collection has no tile to focus, so the top screen shows the collection.
    val subject = naming ?: app.shell.focusedHero() ?: app.shell.emptyCollectionHero()
    // Find shows its best result, or the focused one, and travels along its strip.
    val find = app.shell.find
    val cellFocused = model.dialog == null && model.panel == null && !model.connectOpen && model.focus.chrome == null
    // The record behind each subject, so a layer that is fading out keeps its own cover.
    val records = remember { mutableMapOf<String, Game>() }
    val queries = remember { mutableMapOf<String, ArtQuery>() }
    app.shell.focusedRecord()?.let { record -> if (subject != null && naming == null) records[subject.key] = record }
    app.shell.focusedGame()?.let(app.shell::artQuery)?.let { query -> if (subject != null && naming == null) queries[subject.key] = query }
    val place = subject?.let {
        if (find != null) HeroPlace("find/${find.filter}", (if (find.onResults) find.index else 0) * Metrics.rows)
        else HeroPlace(gridToken(app), model.focus.cellIndex)
    }
    // The label follows focus at once. The art follows once it is whole.
    val staged = rememberStagedHero(app, subject, place, records, queries, scale)
    val shown = staged.subject
    // The grid's neighbours, not Find's: the grid is what is under the ring.
    WarmNeighbours(app, place.takeIf { find == null })
    // The shown game's scene decides how the name card sits over it.
    val scene = shown?.let { rememberScene(app, records[it.key], queries[it.key]) }
    val sceneArt = scene != null || shown?.let { rememberArt(app, records[it.key], queries[it.key]) } != null
    val fadeOnly = motion.heroFadeOnly(model.motionSpeed, scale)
    val tilt = LocalTilt.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The collection icon editor takes the hero's place while it is open.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - editor.progress.value }) {
            // The game fills the screen behind everything: its wide art, or its box art blurred.
            // It travels less than the art in front, so the two read at different depths.
            HeroCrossfade(
                target = shown,
                travel = staged.travel,
                travelMillis = motion.sceneDuration(model.motionSpeed, scale),
                fadeOnly = fadeOnly,
                keyOf = { it.key },
                // The scene is as deep as the theme's backdrop, so moving the Thor slides it as far.
                layer = Modifier.fillMaxSize().tilted(tilt, motion, 1f, cover = true),
                depth = motion.sceneDepth,
                grows = false,
                dissolves = true,
            ) { layer ->
                SceneBackdrop(app, records[layer.key], queries[layer.key], model.motionSpeed, scale)
            }
            val inset = px(design.metrics.heroInsetPx)
            // Wide enough for a platform, both screens, and play time on one line.
            val cardMax = this@BoxWithConstraints.maxWidth * 0.72f
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
                        .fillMaxWidth()
                        // Nearer than the scene, so it slides less; the name card under it stays.
                        .tilted(tilt, motion, motion.tiltArtShare),
                    contentAlignment = Alignment.Center,
                ) {
                    HeroCrossfade(
                        target = shown,
                        travel = staged.travel,
                        travelMillis = motion.heroDuration(model.motionSpeed, scale),
                        fadeOnly = fadeOnly,
                        keyOf = { it.key },
                    ) { layer ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            when (layer) {
                                is HeroSubject.Item -> HeroArt(
                                    app = app,
                                    title = layer.item.title,
                                    mark = layer.item.mark,
                                    packageName = layer.item.packageName,
                                    record = records[layer.key],
                                    query = queries[layer.key],
                                )
                                is HeroSubject.Folder -> HeroArt(app, layer.folder.name, layer.folder.mark, null, null, null)
                            }
                        }
                    }
                }
                // A name being typed, or Find, takes the name card's place, under the art.
                val typing = presence(app.shell.typing()?.typing, motion.durationIsland, scale)
                val finding = presence(find, motion.durationIsland, scale)
                if (subject != null || finding.shown != null) {
                    Spacer(Modifier.height(px(24f)))
                    Box(contentAlignment = Alignment.Center) {
                        if (subject != null) Box(Modifier.graphicsLayer { alpha = 1f - max(typing.progress.value, finding.progress.value) }) {
                            HeroLabel(
                                app,
                                subject,
                                cellFocused,
                                cardMax,
                                overArt = sceneArt,
                                // A logo on wide art already says the name, once that art is the focused game's.
                                named = !(shown?.key == subject.key && scene?.background != null && scene.logo != null),
                            )
                        }
                        finding.shown?.let { session ->
                            val found = session.subject
                            FindCard(
                                session = session,
                                line = FoundLine(
                                    key = found?.id,
                                    title = found?.title,
                                    facts = found?.let(app.shell::findFacts)?.joinToString(" · ").orEmpty(),
                                    order = if (session.onResults) session.index else 0,
                                ),
                                fieldStyle = text(theme.onBackground, design.type.heroTitle, theme, display = true),
                                titleStyle = text(theme.onBackground, design.type.dialogTitle, theme, display = true),
                                factStyle = text(theme.muted, design.type.heroMeta, theme),
                                chipStyle = text(theme.muted, design.type.hint, theme),
                                background = if (sceneArt) theme.surface.copy(alpha = design.hero.cardAlpha) else theme.surface,
                                width = cardMax,
                                scale = scale,
                                modifier = Modifier.rise { finding.progress.value },
                                onTapFilter = { at -> onEffect(app.shell.tapFindFilter(at)) },
                            )
                        }
                        typing.shown?.let { shown ->
                            TypingField(
                                typing = shown,
                                style = text(theme.onBackground, design.type.heroTitle * TYPED_TITLE, theme, display = true),
                                background = if (sceneArt) theme.surface.copy(alpha = design.hero.cardAlpha) else theme.surface,
                                scale = scale,
                                modifier = Modifier.width(cardMax).rise { typing.progress.value },
                            )
                        }
                    }
                }
            }
        }
        val checks = app.shell.homeSelection()
        val selected = presence(checks?.checked?.size?.takeIf { model.panel == null }, motion.durationFocus, scale)
        selected.shown?.let { count ->
            BasicText(SelectionCopy.count(count),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = px(32f))
                    .graphicsLayer { alpha = selected.progress.value }
                    .clip(design.shape.control()).background(theme.surface.copy(alpha = design.hero.pillAlpha))
                    .padding(horizontal = px(24f), vertical = px(12f)),
                style = text(theme.onBackground, design.type.hint, theme))
        }
        IconEditorLayer(app, editor, screen, scale)
        TopIslands(app, screen, scale) { panelState, progress, interactive ->
            PanelRows(app, screen, panelState, progress, interactive, scale, onEffect)
        }
    }
}

/**
 * Crossfades the artwork onto the newest focus. A second change before this
 * finishes retargets the same two layers instead of starting another fade.
 * The name and details are not in these layers: two labels in one place
 * cannot be read. Art arrives from the way the cursor went, [travel], and the
 * art it replaces carries on out the other side. [depth] is the share of that
 * travel this layer moves. [grows] eases the arriving art up from just under
 * rest. [dissolves] keeps the leaving art up under the arriving art until it is
 * nearly in, so a full-screen layer never thins to the theme behind it.
 * [fadeOnly], for Off and remove-animations, fades over the short duration
 * with no travel. Once the fade is done the layer has no graphics layer.
 * Layers are keyed by [keyOf], so art that stops moving keeps its decoded images.
 */
@Composable
private fun <T> HeroCrossfade(
    target: T?,
    travel: HeroTravel,
    travelMillis: Int,
    fadeOnly: Boolean,
    keyOf: (T) -> Any,
    layer: Modifier = Modifier.fillMaxWidth(),
    depth: Float = 1f,
    grows: Boolean = true,
    dissolves: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    val motion = LocalFoldTheme.current.theme.motion
    val same: (T?, T?) -> Boolean = { left, right -> left?.let(keyOf) == right?.let(keyOf) }
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
    // Where each layer sits along the travel, in pixels before [depth].
    val inX = remember { Animatable(0f) }
    val inY = remember { Animatable(0f) }
    val outX = remember { Animatable(0f) }
    val outY = remember { Animatable(0f) }
    val way by rememberUpdatedState(travel.takeUnless { fadeOnly })
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
        val going = way
        if (!sameFront) {
            // The art that was arriving leaves from wherever it had got to.
            outX.snapTo(inX.value)
            outY.snapTo(inY.value)
            inX.snapTo(going?.let(motion::heroEntryX) ?: 0f)
            inY.snapTo(going?.let(motion::heroEntryY) ?: 0f)
        }
        incoming.snapTo(next.frontAlpha)
        outgoing.snapTo(next.backAlpha)
        blend = next
        // The art travels on the arrive curve and fades on the gentler dissolve.
        val arrive = motion.arrive(travelMillis, 1f)
        val fadeIn = motion.dissolve(travelMillis, 1f)
        val fadeOut = if (dissolves) motion.leave(travelMillis, 1f) else fadeIn
        withContext(SteadyMenu) {
            coroutineScope {
                launch { incoming.animateTo(if (next.front == null) 0f else 1f, fadeIn) }
                launch { outgoing.animateTo(0f, fadeOut) }
                launch { inX.animateTo(0f, arrive) }
                launch { inY.animateTo(0f, arrive) }
                launch { outX.animateTo(going?.let(motion::heroExitX) ?: outX.value, arrive) }
                launch { outY.animateTo(going?.let(motion::heroExitY) ?: outY.value, arrive) }
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
    // Composition only observes the start and end. Every intervening alpha belongs to the layer.
    val active by remember {
        derivedStateOf {
            heroCrossfadeActive(
                hasFront = blend.front != null,
                hasBack = blend.back != null,
                frontAlpha = incoming.value,
                backAlpha = outgoing.value,
            )
        }
    }
    // The fading art sits behind the label. It takes no focus or touch, and it
    // is not in the accessibility tree. One keyed loop holds every layer, settled
    // or fading, so art that moves from arriving to leaving, or settles, keeps its
    // decoded images; only these modifiers change.
    Box(
        if (active) {
            layer
                .zIndex(-1f)
                .focusProperties { canFocus = false }
                .clearAndSetSemantics { }
        } else {
            layer
        },
    ) {
        for (shown in listOfNotNull(previous, current)) {
            val leaving = shown === previous
            key(keyOf(shown)) {
                val fade = when {
                    leaving -> Modifier.fadingHero(outgoing.asState(), outX.asState(), outY.asState(), depth, grows, fadeOnly)
                    active -> Modifier.fadingHero(incoming.asState(), inX.asState(), inY.asState(), depth, grows, fadeOnly)
                    else -> Modifier
                }
                Box(fade) { content(shown) }
            }
        }
    }
}

/**
 * The collection icon editor rising over the hero, or over the grid when no screen has a hero.
 * Over a grid it brings the grid's hint row along, since those name its keys.
 */
@Composable
private fun IconEditorLayer(app: FoldcadeApp, editor: Presence<Pair<IconPick, String>>, screen: HostScreen, scale: Float, hints: Boolean = false) {
    val (pick, name) = editor.shown ?: return
    Column(Modifier.fillMaxSize().rise { editor.progress.value }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            FolderIconPicker(app, pick, name, top = screen == HostScreen.Top, scale = scale)
        }
        if (hints) GridHints(app, Modifier.padding(bottom = px(EDITOR_HINT_INSET)))
    }
}

/** How far the hint row sits off the bottom edge under the editor, as it does under the grid. */
private const val EDITOR_HINT_INSET = 32f

/**
 * The collection icon editor, on the top screen. The collection stands on the left as its hero
 * shows it, wearing whatever is focused; the colours and glyphs sit on the right, one colour per
 * column over the glyph grid. One ring glides between choices, and the focused one is named
 * under the grid. The bottom screen's tile wears the choice too. See [IconPick].
 */
@Composable
private fun FolderIconPicker(app: FoldcadeApp, pick: IconPick, name: String, top: Boolean, scale: Float) {
    val design = LocalFoldTheme.current.theme.style
    val motion = LocalFoldTheme.current.theme.motion
    val theme = foldTheme()
    val inset = px(design.metrics.heroInsetPx)
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = inset, end = inset, bottom = inset, top = if (top) inset + 48.dp else inset),
        horizontalArrangement = Arrangement.spacedBy(px(40f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(0.36f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(px(16f)),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Crossfade(pick.look.mark, animationSpec = motion.arrive(motion.durationFocus, scale)) { mark ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        HeroArt(app, name, mark, null, null, null)
                    }
                }
            }
            BasicText(
                text = name,
                style = text(theme.onBackground, design.type.dialogTitle, theme, display = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconChoices(pick, scale, Modifier.weight(0.64f).fillMaxHeight())
    }
}

/** The colour row over the glyph grid, with one gliding ring and the focused choice's name. */
@Composable
private fun IconChoices(pick: IconPick, scale: Float, modifier: Modifier) {
    val design = LocalFoldTheme.current.theme.style
    val motion = LocalFoldTheme.current.theme.motion
    val theme = foldTheme()
    val density = LocalDensity.current
    val columns = IconPick.COLUMNS
    val glyphRows = (FOLDER_ICONS.size + columns - 1) / columns
    val reach = ringReach()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val gap = px(12f)
            // The colours stand a little apart from the glyphs.
            val apart = px(14f)
            val cell = minOf(
                (maxWidth - reach * 2 - gap * (columns - 1)) / columns,
                (maxHeight - reach * 2 - apart - gap * glyphRows) / (glyphRows + 1),
            )
            val swatch = cell * SWATCH_SHARE
            val corner = cell * theme.iconRadius
            val width = cell * columns + gap * (columns - 1)
            val height = cell * (glyphRows + 1) + gap * glyphRows + apart
            fun glyphTop(row: Int) = (cell + gap) * (row + 1) + apart
            // The ring's target: the focused colour's circle, or the focused glyph's tile.
            val ringLeft: Dp
            val ringTop: Dp
            val ringSize: Dp
            val ringCorner: Dp
            if (pick.onTints) {
                ringLeft = (cell + gap) * pick.tint + (cell - swatch) / 2
                ringTop = (cell - swatch) / 2
                ringSize = swatch
                ringCorner = swatch / 2
            } else {
                ringLeft = (cell + gap) * (pick.glyph % columns)
                ringTop = glyphTop(pick.glyph / columns)
                ringSize = cell
                ringCorner = corner
            }
            val glide = motion.playful(motion.durationSelect, scale)
            val x by motionState(with(density) { ringLeft.toPx() }, glide)
            val y by motionState(with(density) { ringTop.toPx() }, glide)
            val side by motionState(with(density) { ringSize.toPx() }, glide)
            val round by motionState(with(density) { ringCorner.toPx() }, glide)
            val focusRing = FocusRing(LocalFoldTheme.current.theme)
            Box(
                Modifier
                    .size(width, height)
                    .drawWithContent {
                        drawContent()
                        with(focusRing) { drawFocusRing(round, bounds = Rect(Offset(x, y), Size(side, side))) }
                    },
            ) {
                val design = theme.style
                val folder = design.collections
                CollectionTint.entries.forEachIndexed { index, tint ->
                    val chosen = index == pick.tint
                    val grown by motionState(if (chosen) 1f else SWATCH_REST, glide)
                    val color = folder.color(tint)
                    Box(
                        Modifier
                            .offset(x = (cell + gap) * index + (cell - swatch) / 2, y = (cell - swatch) / 2)
                            .size(swatch)
                            .drawBehind {
                                val half = size.minDimension / 2f * grown
                                val corner = half * 2f * design.shape.round
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        listOf(lerp(color, design.colors.shine, folder.swatchShine), color, lerp(color, design.colors.shade, folder.swatchShade)),
                                        startY = center.y - half,
                                        endY = center.y + half,
                                    ),
                                    topLeft = Offset(center.x - half, center.y - half),
                                    size = Size(half * 2f, half * 2f),
                                    cornerRadius = CornerRadius(corner, corner),
                                )
                                // The colour in use keeps a light rim, so it shows while the glyphs are focused.
                                if (chosen) {
                                    val rim = half - folder.swatchRimInsetPx
                                    val rimCorner = (rim * 2f * design.shape.round).coerceAtLeast(0f)
                                    drawRoundRect(
                                        design.colors.shine,
                                        topLeft = Offset(center.x - rim, center.y - rim),
                                        size = Size(rim * 2f, rim * 2f),
                                        cornerRadius = CornerRadius(rimCorner, rimCorner),
                                        style = Stroke(width = folder.swatchRimPx),
                                        alpha = folder.swatchRimAlpha,
                                    )
                                }
                            },
                    )
                }
                FOLDER_ICONS.forEachIndexed { index, mark ->
                    val chosen = index == pick.glyph
                    val lit by motionState(if (chosen) 1f else 0f, motion.arrive(motion.durationFocus, scale))
                    Box(
                        Modifier
                            .offset(x = (cell + gap) * (index % columns), y = glyphTop(index / columns))
                            .size(cell)
                            .drawBehind {
                                val cornerPx = corner.toPx()
                                drawRoundRect(
                                    color = lerp(theme.surface, theme.onBackground, CHOSEN_LIFT * lit),
                                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        SymbolIcon(mark, PICKER_SYMBOL_SCALE, theme.onBackground) { lerp(design.tile.restAlpha, 1f, lit) }
                    }
                }
            }
        }
        Crossfade(pick.focusedName, animationSpec = motion.arrive(motion.durationFocus, scale)) { label ->
            BasicText(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                style = text(theme.muted, design.type.hint, theme).copy(textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}

/** A colour's circle in its column, and how far an unchosen one shrinks. */
private const val SWATCH_SHARE = 0.72f
private const val SWATCH_REST = 0.78f

/**
 * The device's date order and 12/24-hour clock, as the status clock reads them.
 * Only a change to the style recomposes, not every minute's tick.
 */
@Composable
internal fun rememberDateStyle(app: FoldcadeApp): DateStyle {
    val status = app.deviceStatus.status.collectAsStateWithLifecycle()
    val style by remember { derivedStateOf { status.value.dateStyle } }
    return style
}

/** How far the chosen glyph's tile lightens toward the text colour. */
private const val CHOSEN_LIFT = 0.1f

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
    val design = LocalFoldTheme.current.theme.style
    val theme = foldTheme()
    val copy = heroCopy(shown)
    val dateStyle = rememberDateStyle(app)
    val centred = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.heroTitle, theme, display = true).copy(textAlign = TextAlign.Center)
    Column(
        Modifier
            .widthIn(min = px(360f), max = maxWidth)
            .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp))
            // Over the game's art the card lets it through.
            .background(if (overArt) theme.surface.copy(alpha = design.hero.cardAlpha) else theme.surface)
            .padding(horizontal = px(40f), vertical = px(20f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(px(4f)),
    ) {
        if (named) {
            BasicText(
                text = copy.title,
                style = centred.copy(fontWeight = design.type.strongWeight),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val item = (shown as? HeroSubject.Item)?.item
        if (item?.emptyShelfHint == true) {
            ShelfMeta(line = copy.detail, hintFocused = cellFocused, centred = true)
        } else {
            val focusedGame = app.shell.focusedGame()?.takeIf { it.id == item?.key }
            val both = focusedGame?.occupiesBothDisplays == true
            // What the library is doing with this game leads the line, in the focus colour.
            val health = focusedGame?.let { app.shell.saveHealthOf(it.id, app.shell.artRecord(it)) } ?: SaveHealth.Fine
            val activity = focusedGame?.let { app.shell.activityFor(it.id) }
            val saveLine = if (saveHealthLeads(health, activity)) saveHealthLine(health) else null
            val live = activity?.takeIf { saveLine == null }?.let(::activityLine)
            val played = item?.let { app.plays.run { stamp; shown(app.shell.playId(it.key)) } }
            val facts = heroFacts(
                copy.title,
                copy.detail,
                item?.availability.takeIf { live == null },
                Copy.usesBothScreens.takeIf { both },
                played?.let {
                    heroPlayLine(it.activeMillis, it.lastPlayedMillis, System.currentTimeMillis(), ZoneId.systemDefault(), dateStyle)
                },
            )
            if (facts.isNotEmpty() || live != null || saveLine != null) {
                BasicText(
                    text = buildAnnotatedString {
                        if (live != null) {
                            withStyle(SpanStyle(color = theme.focus)) { append(live) }
                            if (facts.isNotEmpty()) append(" · ")
                        } else if (saveLine != null) {
                            withStyle(SpanStyle(color = if (health.needsAttention) theme.style.colors.attention else theme.muted)) { append(saveLine) }
                            if (facts.isNotEmpty()) append(" · ")
                        }
                        append(facts)
                    },
                    style = text(theme.muted, LocalFoldTheme.current.theme.style.type.heroMeta, theme).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Modifier.fadingHero(
    alpha: State<Float>,
    x: State<Float>,
    y: State<Float>,
    depth: Float,
    grows: Boolean,
    fadeOnly: Boolean,
): Modifier {
    val motion = LocalFoldTheme.current.theme.motion
    return     focusProperties { canFocus = false }
        .clearAndSetSemantics { }
        .graphicsLayer {
            this.alpha = alpha.value
            val drawn = if (grows) motion.heroScale(alpha.value, fadeOnly) else motion.scaleRest
            scaleX = drawn
            scaleY = drawn
            translationX = x.value * depth
            translationY = y.value * depth
        }

}

/**
 * Shows cached scene parts immediately, then fills missing ones when a lookup is due.
 * Changing artwork sources or the Game art setting asks again.
 */
@Composable
private fun rememberScene(app: FoldcadeApp, record: Game?, query: ArtQuery?): ArtScene? {
    val allowed = app.shell.model.artwork
    val connections = app.shell.model.signedIn
    val revision = app.artRevision
    val artKeySaved = app.shell.model.artKeySaved
    val held = remember(record, query, allowed, connections, artKeySaved, revision) { app.covers.cachedScene(record, query) }
    var fetched by remember(record, query, allowed, connections, artKeySaved, revision) { mutableStateOf<ArtScene?>(null) }
    LaunchedEffect(record, query, allowed, connections, artKeySaved, revision) {
        if ((record != null || query != null) && held?.complete != true) fetched = app.covers.fetchScene(record, query)
    }
    val scene = app.covers.chosenScene(fetched ?: held, query)
    val picker = LocalArtPreview.current?.takeIf { it.gameId == query?.key }
    val candidate = picker?.url ?: return scene
    return when (picker.kind) {
        ArtKind.Hero -> (scene ?: ArtScene()).copy(background = candidate)
        ArtKind.Logo -> (scene ?: ArtScene()).copy(logo = candidate)
        else -> scene
    }
}

/**
 * The whole top screen behind a game. Wide art fills it, darkened at the top
 * for the islands and at the bottom for the name card. Without wide art, the
 * box art fills it blurred and dimmed, so the screen takes the game's colours.
 * Without either, nothing is drawn and the theme's background shows.
 */
@Composable
internal fun SceneBackdrop(app: FoldcadeApp, record: Game?, query: ArtQuery?, speed: MotionSpeed, animatorScale: Float) {
    val motion = LocalFoldTheme.current.theme.motion
    val scene = rememberScene(app, record, query)
    val wide = scene?.background?.let { rememberCoverPainter(app, it, BACKDROP_PX) }
    if (wide != null) {
        CoversBackdrop()
        // Wide art lands a little close and settles back over a long breath, once per
        // game. It stays drawn just past the screen's edges, so its travel shows none.
        val still = motion.heroFadeOnly(speed, animatorScale)
        val settle = remember(scene.background) { Animatable(if (still) 0f else 1f) }
        LaunchedEffect(settle, still) {
            if (still) {
                settle.snapTo(0f)
            } else {
                withContext(SteadyMenu) { settle.animateTo(0f, motion.showcase(speed, animatorScale)) }
            }
        }
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = wide.painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val drawn = motion.sceneScale(settle.value)
                        scaleX = drawn
                        scaleY = drawn
                    },
                contentScale = ContentScale.Crop,
            )
            val hero = foldTheme().style
            val scrim = hero.colors.scrim
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to scrim.copy(alpha = hero.hero.sceneShadeTop),
                            0.22f to scrim.copy(alpha = 0f),
                            0.55f to scrim.copy(alpha = 0f),
                            1f to scrim.copy(alpha = hero.hero.sceneShadeBottom),
                        ),
                    ),
            )
        }
        return
    }
    val cover = rememberArt(app, record, query)?.cover ?: return
    val ambient = rememberCoverPainter(app, cover, AMBIENT_PX) ?: return
    val design = foldTheme().style
    CoversBackdrop()
    Image(
        painter = ambient.painter,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .blur(design.hero.ambientBlurDp.dp, BlurredEdgeTreatment.Rectangle)
            .drawWithContent {
                drawContent()
                drawRect(design.colors.scrim.copy(alpha = design.hero.ambientDim))
            },
        contentScale = ContentScale.Crop,
    )
}

/**
 * Art for a tile or the hero: at once when it is already known, otherwise after
 * one lookup. A provider's cover for [record] comes before the public sources.
 * Changing artwork sources or the Game art setting asks again. Without [automatic], only
 * the art the user chose or is choosing.
 */
@Composable
internal fun rememberArt(app: FoldcadeApp, record: Game?, query: ArtQuery?, automatic: Boolean = true): ArtSet? {
    val allowed = app.shell.model.artwork
    val connections = app.shell.model.signedIn
    val revision = app.artRevision
    val artKeySaved = app.shell.model.artKeySaved
    val held = remember(record, query, allowed, connections, artKeySaved, revision, automatic) {
        if (automatic) app.covers.cached(record, query) else null
    }
    var fetched by remember(record, query, allowed, connections, artKeySaved, revision, automatic) { mutableStateOf<ArtSet?>(null) }
    LaunchedEffect(record, query, allowed, connections, artKeySaved, revision, automatic) {
        if (automatic && (record != null || query != null) && held == null) fetched = app.covers.fetch(record, query)
    }
    val set = app.covers.chosen(held ?: fetched, query)
    val picker = LocalArtPreview.current?.takeIf { it.gameId == query?.key && it.kind == ArtKind.Tile }
    val candidate = picker?.url ?: return set
    return (set ?: ArtSet(artPreviewSource, candidate)).copy(cover = candidate, square = candidate)
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
    val design = LocalFoldTheme.current.theme.style
    val theme = foldTheme()
    val themedApp = appGlyph(packageName, 640)
    val icon = if (themedApp == null) launcherIcon(packageName) else null
    val cover = rememberArt(app, record, query)?.cover
    val scene = rememberScene(app, record, query)
    val logo = scene?.logo?.takeIf { scene.background != null }?.let { rememberCoverPainter(app, it, LOGO_PX) }
    val screenShot = scene?.screen?.takeIf { scene.background == null }
        ?.let { rememberCoverPainter(app, it, SCREEN_PX, FilterQuality.None) }
    val glyph = themedApp ?: mark?.let { markGlyph(it, 640) }
    val accent = glyph?.accent ?: theme.focus
    val loaded = cover?.let { rememberCoverPainter(app, it, COVER_PX) }
    // A logo, or a box beside its title screen, takes the width it needs. The game's own
    // art is behind them, so they draw no glow. A lone box or mark stays in its square, lit
    // from behind when the theme's glow.heroPeak asks for it (Afterglow's is 0).
    val staged = logo != null || (loaded != null && screenShot != null)
    Box(
        Modifier
            .fillMaxHeight()
            .then(if (staged) Modifier.fillMaxWidth() else Modifier.aspectRatio(1f))
            .drawBehind {
                val peak = design.glow.heroPeak
                if (staged || peak <= 0f) return@drawBehind
                val reach = size.minDimension * design.metrics.heroArtFraction
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to accent.copy(alpha = peak),
                            0.42f to accent.copy(alpha = peak * HERO_GLOW_SHOULDER),
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
        when {
            // Wide art fills the screen behind; the logo names the game over it.
            logo != null -> Box(
                modifier = Modifier
                    .fillMaxWidth(LOGO_WIDTH)
                    .fillMaxHeight(LOGO_HEIGHT)
                    // A soft shade behind the logo keeps a dark logo readable over busy art.
                    .drawBehind {
                        val reach = size.maxDimension * design.metrics.heroArtFraction
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f to design.colors.scrim.copy(alpha = design.hero.logoShadeAlpha),
                                    1f to design.colors.scrim.copy(alpha = 0f),
                                ),
                                center = center,
                                radius = reach,
                            ),
                            radius = reach,
                            center = center,
                        )
                    },
            ) {
                // The logo's own shape, blurred and in the scrim's colour, under it: a light logo
                // keeps an edge without a blot. A dark one glows in the shine colour instead, so
                // it lifts off art of its own hue.
                val shadow = design.hero.logoShadowAlpha
                val lightness = rememberLightness(logo.image)
                val glows = lightness != null && lightness < design.hero.logoGlowBelow
                if (shadow > 0f) {
                    val blur = design.hero.logoShadowBlurDp.dp
                    Image(
                        painter = logo.painter,
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .then(if (blur > 0.dp) Modifier.blur(blur, BlurredEdgeTreatment.Unbounded) else Modifier),
                        contentScale = ContentScale.Fit,
                        alpha = shadow,
                        colorFilter = ColorFilter.tint(if (glows) design.colors.shine else design.colors.scrim, BlendMode.SrcIn),
                    )
                }
                Image(
                    painter = logo.painter,
                    contentDescription = title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Fit,
                )
            }
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
                        .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp)),
                    contentScale = ContentScale.Crop,
                )
                Image(
                    painter = screenShot.painter,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(screenShot.ratio, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp / 2)),
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
                    .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp)),
                contentScale = ContentScale.Crop,
            )
            icon != null -> LauncherImage(icon, contentDescription = title, modifier = Modifier.fillMaxSize(theme.artScale))
            glyph != null -> MarkIcon(glyph, glyph.heroScale(theme))
            else -> BasicText(
                text = monogram(title),
                modifier = Modifier.scale(4f),
                style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.heroTitle, theme, display = true),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

internal class LoadedCover(val painter: Painter, val ratio: Float, val image: coil3.Image? = null)

/** How light [image] is over its opaque pixels, 0 black to 1 white. Null until read, or for an image it can't read. */
@Composable
private fun rememberLightness(image: coil3.Image?): Float? {
    val state = produceState<Float?>(null, image) {
        value = image?.let { withContext(Dispatchers.Default) { lightness(it) } }
    }
    return state.value
}

/** Samples about 48 pixels across: enough to tell a dark logo from a light one. */
private fun lightness(image: coil3.Image): Float? {
    val source = (image as? coil3.BitmapImage)?.bitmap ?: return null
    // A hardware bitmap can't be read pixel by pixel; a copy in memory can.
    val bitmap = if (source.config == android.graphics.Bitmap.Config.HARDWARE) {
        source.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        source
    }
    try {
        val step = maxOf(1, maxOf(bitmap.width, bitmap.height) / 48)
        var sum = 0.0
        var weight = 0.0
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) / 255.0
                if (alpha < 0.5) continue
                val luma = 0.2126 * ((pixel shr 16) and 0xFF) + 0.7152 * ((pixel shr 8) and 0xFF) + 0.0722 * (pixel and 0xFF)
                sum += luma / 255.0 * alpha
                weight += alpha
            }
        }
        return if (weight > 0.0) (sum / weight).toFloat() else null
    } finally {
        if (bitmap !== source) bitmap.recycle()
    }
}

/**
 * Memory-cache keys of covers already decoded, by size and address. A painter made
 * later for the same cover draws it on its first frame from these, instead of
 * drawing nothing until its own request comes back.
 */
private val decodedCovers = ConcurrentHashMap<String, MemoryCache.Key>()

private fun coverId(uri: String, px: Int): String = "$px\u0000$uri"

/**
 * The one request for [uri] at [px]. Art warmed ahead with it is the art the painter finds.
 * The size is part of the cache key: a tile and the top screen ask for the same cover
 * at different sizes, and with one entry each evicted the other, so the top screen's
 * cover reloaded and faded in late.
 */
private fun coverRequest(context: Context, uri: String, px: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(CoverImage(uri))
        .size(px)
        .memoryCacheKeyExtra(COVER_SIZE_KEY, px.toString())
        .crossfade(true)
        .build()

private const val COVER_SIZE_KEY = "foldcade.px"

/**
 * Looks up a game's scene and art, and decodes the images [SceneBackdrop] and
 * [HeroArt] draw for it into the image cache, so its art is whole when it arrives.
 */
private suspend fun prepareHeroArt(app: FoldcadeApp, context: Context, record: Game?, query: ArtQuery?) {
    if (record == null && query == null) return
    // Warm useful cached parts immediately. rememberScene refreshes missing parts after arrival.
    val scene = app.covers.cachedScene(record, query) ?: app.covers.fetchScene(record, query)
    val cover = (app.covers.cached(record, query) ?: app.covers.fetch(record, query))?.cover
    val wide = scene?.background
    val wanted = buildList {
        if (wide != null) {
            add(wide to BACKDROP_PX)
            scene.logo?.let { add(it to LOGO_PX) }
        } else {
            scene?.screen?.let { add(it to SCREEN_PX) }
            cover?.let { add(it to AMBIENT_PX) }
        }
        if (wide == null || scene.logo == null) cover?.let { add(it to COVER_PX) }
    }
    coroutineScope {
        wanted.forEach { (uri, px) -> launch { warmCover(app, context, uri, px) } }
    }
}

/** Decodes [uri] at [px] into the image cache, so the next painter for it draws it on its first frame. */
private suspend fun warmCover(app: FoldcadeApp, context: Context, uri: String, px: Int) {
    // Still in memory: nothing to do. An entry the cache has since dropped decodes again.
    decodedCovers[coverId(uri, px)]?.let { held -> if (app.images.memoryCache?.get(held) != null) return }
    val done = app.images.execute(coverRequest(context, uri, px)) as? SuccessResult
    done?.memoryCacheKey?.let { decodedCovers[coverId(uri, px)] = it }
}

/** Where a hero subject's tile sat when it had focus. [grid] tells grids and folders apart. */
private data class HeroPlace(val grid: String, val index: Int)

/** The subject the top screen's art shows, and the way the cursor went to reach it. */
private data class StagedHero(val subject: HeroSubject?, val travel: HeroTravel)

private fun gridToken(app: FoldcadeApp): String {
    val model = app.shell.model
    return listOf(model.libraryGrid, model.gridKind, model.homeGrid, app.shell.homeAnimToken()).joinToString("/")
}

private fun travelOf(from: HeroPlace?, to: HeroPlace?): HeroTravel {
    if (from == null || to == null || from.grid != to.grid) return HeroTravel.None
    return heroTravel(from.index, to.index)
}

/**
 * The subject the top screen's art shows. It follows [subject] once that
 * subject's art is decoded, or after [HERO_ART_WAIT_MS], so art crossfades in
 * whole instead of filling in after the fade. One arrival is mostly in before
 * the next starts, so a held move does not strobe the screen; the art catches
 * up when the cursor rests. New copy for the same subject shows at once.
 */
@Composable
private fun rememberStagedHero(
    app: FoldcadeApp,
    subject: HeroSubject?,
    place: HeroPlace?,
    records: Map<String, Game>,
    queries: Map<String, ArtQuery>,
    animatorScale: Float,
): StagedHero {
    val motion = LocalFoldTheme.current.theme.motion
    val context = LocalContext.current
    var staged by remember { mutableStateOf(StagedHero(subject, HeroTravel.None)) }
    var stagedPlace by remember { mutableStateOf(place) }
    var stagedAt by remember { mutableLongStateOf(0L) }
    val dwell = motion.sceneDuration(app.shell.model.motionSpeed, animatorScale) / 2
    LaunchedEffect(subject?.key) {
        if (subject?.key == staged.subject?.key) return@LaunchedEffect
        if (subject != null) {
            // Keeps warming after the wait gives up, until focus moves on.
            val ready = launch { prepareHeroArt(app, context, records[subject.key], queries[subject.key]) }
            withTimeoutOrNull(HERO_ART_WAIT_MS) { ready.join() }
        }
        delay(stagedAt + dwell - SystemClock.uptimeMillis())
        staged = StagedHero(subject, travelOf(stagedPlace, place))
        stagedPlace = place
        stagedAt = SystemClock.uptimeMillis()
    }
    val current = staged
    if (subject != null && subject.key == current.subject?.key && subject != current.subject) {
        return current.copy(subject = subject)
    }
    return current
}

/**
 * Once the cursor rests, warms the art of the tiles beside it, so the next
 * move's art is ready to arrive at once.
 */
@Composable
private fun WarmNeighbours(app: FoldcadeApp, place: HeroPlace?) {
    val context = LocalContext.current
    LaunchedEffect(place) {
        val index = place?.index ?: return@LaunchedEffect
        delay(HERO_WARM_DELAY_MS)
        val shell = app.shell
        for (near in listOf(index + 1, index - 1, index + Metrics.rows, index - Metrics.rows)) {
            val game = shell.gameAt(near) ?: continue
            prepareHeroArt(app, context, shell.artRecord(game), shell.artQuery(game))
        }
    }
}

/** How long the top screen keeps the last game's art while the next one's decodes. */
private const val HERO_ART_WAIT_MS = 400L

/**
 * Once the cursor rests, warms the tile art a screen of columns either side of
 * [first], so a column slides in with its art drawn rather than filling in behind it.
 */
@Composable
private fun WarmColumns(app: FoldcadeApp, first: Int, columns: Int) {
    val context = LocalContext.current
    // A folder opens at the start too, so the grid itself is part of the key.
    LaunchedEffect(first, columns, gridToken(app), app.shell.model.count) {
        delay(HERO_WARM_DELAY_MS)
        val shell = app.shell
        val screenful = columns * Metrics.rows
        val ahead = GridStrip.index(first + columns, 0)
        val behind = GridStrip.index(first, 0) - screenful
        for (index in (ahead until ahead + screenful) + (behind until behind + screenful)) {
            val game = shell.gameAt(index) ?: continue
            val record = shell.artRecord(game)
            val query = shell.artQuery(game)
            if (record == null && query == null) continue
            val found = app.covers.cached(record, query) ?: app.covers.fetch(record, query) ?: continue
            warmCover(app, context, found.square ?: found.cover, TILE_PX)
        }
    }
}

/** How long the cursor rests before its neighbours' art is warmed. */
private const val HERO_WARM_DELAY_MS = 300L

/**
 * The decoded cover, or null while it loads or when it cannot load. The size
 * is fixed, so the request starts before the painter is drawn.
 */
@Composable
internal fun rememberCoverPainter(
    app: FoldcadeApp,
    uri: String,
    px: Int,
    filterQuality: FilterQuality = FilterQuality.Low,
): LoadedCover? {
    val context = LocalContext.current
    val request = remember(uri, px) { coverRequest(context, uri, px) }
    val painter = rememberAsyncImagePainter(request, app.images, filterQuality = filterQuality)
    val state by painter.state.collectAsState()
    val result = (state as? AsyncImagePainter.State.Success)?.result
    if (result != null) {
        result.memoryCacheKey?.let { decodedCovers[coverId(uri, px)] = it }
        val image = result.image
        if (image.width <= 0 || image.height <= 0) return null
        return LoadedCover(painter, image.width.toFloat() / image.height, image)
    }
    // Until the painter has it, the same cover already in memory draws at once.
    val held = remember(uri, px) {
        decodedCovers[coverId(uri, px)]?.let { app.images.memoryCache?.get(it)?.image }
            ?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { LoadedCover(it.asPainter(context, filterQuality), it.width.toFloat() / it.height, it) }
    }
    return held
}

private const val artPreviewSource = "preview"

private const val COVER_PX = 1024
private const val BACKDROP_PX = 1920
private const val AMBIENT_PX = 256
private const val LOGO_PX = 800
private const val SCREEN_PX = 1024
private const val LOGO_WIDTH = 0.6f
private const val LOGO_HEIGHT = 0.7f
/** The hero glow's middle stop, as a share of its peak (`glow.heroPeak`, when a theme sets one). */
private const val HERO_GLOW_SHOULDER = 0.16f / 0.45f

/** A name being typed on the top screen reads a size up from the hero's title. */
private const val TYPED_TITLE = 1.25f
internal const val TILE_PX = 384
/** The picker's glyphs are flat symbols, drawn a little inside their cell. */
private const val PICKER_SYMBOL_SCALE = 0.7f
private const val COVER_HEIGHT = 0.92f

@Composable
private fun Picker(
    app: FoldcadeApp,
    screen: HostScreen,
    scale: Float,
    onEffect: (Effect?) -> Unit,
    /** No screen has a hero, so the collection icon editor covers this grid while it is open. */
    editorHere: Boolean = false,
    /** This panel is where the grid is, not one it is fading out of. Only it sets how many columns show. */
    live: Boolean,
) {
    val design = LocalFoldTheme.current.theme.style
    val theme = foldTheme()
    val shell = app.shell
    val model = shell.model
    val motion = LocalFoldTheme.current.theme.motion
    val editor = presence(shell.folderIconPick()?.takeIf { editorHere }, motion.durationIsland, scale)
    // The keyboard takes the grid's place: the grid sinks away as the keys rise in. Find
    // slides up from the bottom edge the same way.
    val typing = presence(shell.typing()?.typing, motion.durationIsland, scale)
    val finding = presence(shell.find, motion.durationIsland, scale)
    val covered = { max(typing.progress.value, finding.progress.value) }
    val gridGone by remember { derivedStateOf { covered() >= 1f } }
    // The hero on the other screen carries the focused name, so the grid is
    // icons only. With one screen free there is no hero, and tiles keep labels.
    val iconsOnly = app.store.session.bothScreensFree()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inset = maxWidth * design.metrics.insetFraction
        // A wide panel spaces its tiles as a 4:3 one would, and spends the rest of its width on columns.
        val gap = minOf(maxWidth, maxHeight * WIDE_SPAN) * design.metrics.gapFraction
        val titlePx = rememberTextMeasurer().measure(
            text = "Ag",
            style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.gridLabel, theme),
            maxLines = 1,
        ).size.height
        val titleLine = with(LocalDensity.current) { titlePx.toDp() }
        val showingHome = !model.libraryGrid && model.homeGrid == HomeGrid.StandIns
        Column(
            Modifier
                .fillMaxSize()
                // The grid gives way to the icon editor, which repeats its hints.
                .graphicsLayer { alpha = 1f - editor.progress.value }
                .padding(horizontal = inset)
                .padding(top = inset, bottom = inset),
        ) {
            if (screen == HostScreen.Top) {
                Spacer(Modifier.height(48.dp))
            }
            GridHeader(shell)
            shell.libraryBar()?.let { view ->
                // It sinks with the grid under Find and the keys, and gives them its room as it goes.
                LibraryBar(
                    view = view,
                    style = text(theme.muted, design.type.hint, theme),
                    scale = scale,
                    onSearch = shell::tapLibrarySearch,
                    onTab = shell::tapLibraryTab,
                    modifier = Modifier.collapsing(covered).sinking(covered).padding(bottom = px(LIBRARY_BAR_GAP)),
                )
            }
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // Rows are the same on every panel, so a Home layout reads the same on both.
                // Columns are as many as the width holds, never fewer than Metrics.columns.
                val rows = Metrics.rows
                // Room for the focused tile's scale and ring. The glow is not clipped.
                val ring = focusOutset(minOf(maxWidth / Metrics.columns, maxHeight / rows)) + px(GRID_RING_PAD)
                val titleBlock = if (iconsOnly) Dp.Hairline else px(20f) + titleLine
                // A title already parts its row from the next, so titled rows sit closer.
                val rowGap = if (iconsOnly) gap else gap * TITLED_ROW_GAP
                val fromHeight = (maxHeight - ring * 2 - rowGap * (rows - 1)) / rows - titleBlock
                val fromWidth = (maxWidth - ring * 2 - gap * (Metrics.columns - 1)) / Metrics.columns
                val cell = minOf(fromHeight, fromWidth).coerceAtLeast(px(56f))
                val span = maxWidth - ring * 2
                val columns = max(Metrics.columns, floor((span + gap) / (cell + gap)).toInt())
                // What width is left spreads into the gaps, so the strip reaches both edges. Past
                // a point it would look sparse, and the strip centres instead.
                val columnGap = ((span - cell * columns) / (columns - 1)).coerceIn(gap, gap * MAX_COLUMN_SPREAD)
                SideEffect {
                    shell.setRowsPerPage(rows)
                    if (live) shell.setColumnsShown(columns)
                }
                val emptyTitle = emptyTitle(model)
                val emptyCollection = shell.emptyCollection()
                if (gridGone) {
                    Unit
                } else if (model.libraryGrid && emptyTitle != null) {
                    EmptyLibrary(app, screen, emptyTitle, emptyActions(model))
                } else if (emptyCollection != null) {
                    EmptyCollection(app, screen, emptyCollection.name)
                } else if (!model.libraryGrid && (model.homeGrid != HomeGrid.StandIns || shell.homeFoldersOpen()) && model.count == 0) {
                    BasicText(
                        text = if (model.homeGrid == HomeGrid.StandIns) Copy.noFolders else emptyShelf(model.homeGrid),
                        style = text(theme.muted, LocalFoldTheme.current.theme.style.type.dialogBody, theme),
                    )
                } else {
                    Box(Modifier.sinking(covered)) {
                        StripGrid(
                            app = app,
                            screen = screen,
                            cell = cell,
                            rowGap = rowGap,
                            columnGap = columnGap,
                            columns = columns,
                            pad = ring,
                            scale = scale,
                            showTitle = !iconsOnly,
                            homeTiles = showingHome,
                        )
                    }
                }
                typing.shown?.let { shown ->
                    ControllerKeyboard(
                        typing = shown,
                        buttons = typingFaces(model.faceMap),
                        held = model.held,
                        scale = scale,
                        onTap = shell::tapTyping,
                        modifier = Modifier.rise { typing.progress.value },
                        // With no top screen beside it, the text shows over the keys.
                        fieldStyle = if (iconsOnly) null else text(theme.onBackground, LocalFoldTheme.current.theme.style.type.heroTitle, theme, display = true),
                    )
                }
                finding.shown?.let { session ->
                    FindKeys(
                        session = session,
                        buttons = typingFaces(model.faceMap),
                        held = model.held,
                        scale = scale,
                        gap = gap,
                        ring = ring,
                        titleBlock = titleBlock,
                        // With no top screen beside it, the query and filters show over the results, a
                        // size down so the keys keep their room.
                        fieldStyle = if (iconsOnly) null else text(theme.onBackground, design.type.dialogTitle, theme, display = true),
                        chipStyle = text(theme.muted, design.type.hint, theme),
                        emptyStyle = text(theme.muted, design.type.dialogBody, theme),
                        onTapKey = { at -> onEffect(shell.tapFind(at)) },
                        onTapResult = { at -> onEffect(shell.tapFindResult(at)) },
                        onTapFilter = { at -> onEffect(shell.tapFindFilter(at)) },
                        tile = { entry, focused, size, onClick -> FoundTile(app, entry, focused, size, !iconsOnly, onClick) },
                        modifier = Modifier.slidingUp { finding.progress.value },
                    )
                }
            }
            if (!gridGone) {
                Box(Modifier.sinking(covered)) {
                    StripBar(count = model.count, first = shownFirstColumn(model), shown = model.columnsShown, scale = scale)
                }
            }
            // Under an open menu the blur would smear these. MenuHints draws them above it.
            GridHints(app, Modifier.alpha(if (LocalUnderMenu.current) 0f else 1f))
        }
        IconEditorLayer(app, editor, screen, scale, hints = true)
        TopIslands(app, screen, scale) { panelState, progress, interactive ->
            PanelRows(app, screen, panelState, progress, interactive, scale, onEffect)
        }
    }
}

@Composable
private fun PanelRows(
    app: FoldcadeApp,
    screen: HostScreen,
    panel: SidePanel,
    progress: () -> Float,
    interactive: Boolean,
    scale: Float,
    onEffect: (Effect?) -> Unit,
) {
    val theme = foldTheme()
    val model = app.shell.model
    val rows = panelRows(panel, model)
    val island = LocalIslandScroll.current
    val density = LocalDensity.current
    val gap = px(6f)
    val fixedHeights = remember(panel.level, rows) { mutableStateMapOf<Int, Int>() }
    val block = remember(rows) { tileBlock(rows) }
    val fixedRows = rows.indices.filter { it in block || rows[it] is Row.PlayActivity ||
        rows[it] is Row.Notice || rows[it] is Row.Brightness || rows[it] is Row.MusicVolume }
    val regularRows = rows.size - fixedRows.size
    val tiled = block.count()
    val blocks = rows.size - tiled + if (tiled > 0) 1 else 0
    val fixedHeight = fixedHeights.values.sum()
    val available = with(density) { island?.available?.roundToPx() ?: Int.MAX_VALUE } - fixedHeight -
        with(density) { gap.roundToPx() } * (blocks - 1 + if (panel.connection != null) 1 else 0).coerceAtLeast(0)
    val fit = menuFit(regularRows, available, density.density)
    val heights = rows.indices.filter { it !in fixedRows }.zip(fit.rowHeights).toMap()
    val keys = remember(rows, model.faceMap) { rowKeys(rows, model.faceMap) }
    val slots = rememberSelectionSlots()
    val reachPx = with(density) { ringReach().toPx() }
    // The tiles light their own circles rather than the highlight, so they scroll into view as one block.
    var tileBounds by remember(panel.level) { mutableStateOf<Rect?>(null) }
    LaunchedEffect(panel.level, panel.index, interactive, island?.viewport, fit.rowHeights, slots.rows.toMap(), tileBounds) {
        if (island == null || !interactive || island.viewport <= 0) return@LaunchedEffect
        val bounds = { at: Int -> if (at in block) tileBounds else slots.rows[at] }
        val focused = bounds(panel.index) ?: return@LaunchedEffect
        val previous = bounds(panel.index - 1) ?: focused
        val next = bounds(panel.index + 1) ?: focused
        val target = menuScrollTarget(
            focused.top + reachPx, focused.bottom + reachPx,
            previous.top + reachPx, next.bottom + reachPx,
            island.viewport.toFloat(), island.state.value.toFloat(), island.state.maxValue.toFloat(), reachPx,
        )
        kotlinx.coroutines.withContext(SteadyMenu) {
            island.state.animateScrollTo(target.roundToInt(), theme.motion.arrive(theme.motion.durationSelect, scale))
        }
    }
    val moveTo = model.tileActions?.moveTo
    val iconSlot = rows.indices.any { it !in block && rowIcon(rows[it], moveTo) != null }
    // One highlight travels between the tiles and the rows, rounding to fit a tile.
    val listFocus = panel.index.takeIf { it in rows.indices }
    val rowStyle = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.sideRow, theme)
    Column(
        Modifier
            .selectionHighlight(slots, listFocus, ring = true, scale = scale),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        panel.connection?.let { connection ->
            Column(Modifier.onSizeChanged { fixedHeights[-1] = it.height }.padding(horizontal = px(28f), vertical = px(8f)).cascade(0, progress)) {
                BasicText(
                    text = connection.label,
                    style = rowStyle.copy(fontWeight = foldTheme().style.type.strongWeight),
                )
                app.store.rommOrigin()?.let { origin ->
                    BasicText(
                        text = origin,
                        style = text(theme.muted, LocalFoldTheme.current.theme.style.type.hint, theme),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        rows.forEachIndexed { index, row ->
            // The tiles draw as one block where the first of them sits: the top of R1, or under L1's strip.
            if (index in block) {
                if (index == block.first) {
                    QuickTiles(
                        app = app,
                        screen = screen,
                        panel = panel,
                        rows = rows,
                        keys = keys,
                        block = block,
                        interactive = interactive,
                        scale = scale,
                        slots = slots,
                        modifier = Modifier
                            .onSizeChanged { fixedHeights[index] = it.height }
                            .onGloballyPositioned { tileBounds = Rect(it.positionInParent(), it.size.toSize()) }
                            .cascade(index, progress),
                        onEffect = onEffect,
                    )
                }
                return@forEachIndexed
            }
            val focused = panel.index == index
            val step: () -> Unit = { onEffect(app.shell.touchPanel(index, screen)) }
            val destructive = row == LanguageRow.RemoveDownload || row == LanguageRow.DeleteShortcut
            val press = if (!interactive) Modifier else if (destructive) Modifier.pointerInput(row) {
                detectTapGestures(onPress = {
                    app.shell.setPromptHeld(promptKeyOf(app.shell.model.faceMap.confirmKey) ?: PromptKey.FaceA, true, app.shell.inputWindow(screen))
                    app.shell.touchPanel(index, screen)
                    try { tryAwaitRelease() } finally {
                        onEffect(app.shell.setPromptHeld(promptKeyOf(app.shell.model.faceMap.confirmKey) ?: PromptKey.FaceA, false, app.shell.inputWindow(screen)))
                    }
                })
            } else Modifier.hostPress(step)
            val feedback = recoveryRowFeedback(model, row, scale)
            val place = Modifier
                .fillMaxWidth()
                .selectionSlot(slots, index)
                .onSizeChanged { if (index in fixedRows) fixedHeights[index] = it.height }
                .then(if (island == null) Modifier.keepInView(focused) else Modifier)
                .cascade(index + 1, progress)
            val parts = rowText(row, model)
            val notice = (row as? Row.Notice)?.let { item -> model.notices.firstOrNull { it.id == item.id } }
            when {
                row is Row.PlayActivity -> PlayStrip(
                    app = app,
                    focused = focused,
                    chord = keys[index],
                    modifier = place.then(press),
                )
                notice != null -> NoticeRow(notice = notice, focused = focused, modifier = place.then(press))
                // A slider under the tiles, as in Android's quick settings: the sun, the track, the percent.
                row is Row.Brightness -> SliderRow(
                    label = parts.label,
                    value = parts.value.orEmpty(),
                    showLabel = false,
                    icon = R.drawable.ic_status_display,
                    fraction = model.brightness / 100f,
                    focused = focused,
                    interactive = interactive,
                    onStep = step,
                    onFraction = { app.shell.touchBrightness((it * 100f).roundToInt()) },
                    modifier = place,
                    scale = scale,
                )
                row is Row.MusicVolume -> SliderRow(
                    label = parts.label,
                    value = parts.value.orEmpty(),
                    fraction = model.music.volume,
                    focused = focused,
                    interactive = interactive,
                    onStep = step,
                    onFraction = app.shell::setMusicVolume,
                    modifier = place,
                    scale = scale,
                )
                else -> MenuRow(
                    label = parts.label,
                    icon = rowIcon(row, moveTo),
                    iconSlot = iconSlot,
                    focused = focused,
                    control = rowControl(row, model, parts.value),
                    chord = keys[index],
                    held = model.held,
                    scale = scale,
                    modifier = place.then(press).then(feedback),
                    labelStyle = rowStyle,
                    height = with(density) {
                        eased((heights[index] ?: (52f * density.density).roundToInt()).toFloat(),
                            theme.motion.durationSelect, scale).value.toDp()
                    },
                )
            }
        }
    }
}

/** One notice: its app's icon, its title over one line of text, and how long ago. */
@Composable
private fun NoticeRow(notice: FoldNotice, focused: Boolean, modifier: Modifier = Modifier) {
    val theme = foldTheme()
    val packageName = notice.packageName ?: LocalContext.current.packageName
    val themedApp = appGlyph(packageName, 128)
    val icon = if (themedApp == null) launcherIcon(packageName) else null
    Row(
        modifier.padding(start = px(16f), end = px(16f), top = px(10f), bottom = px(10f)),
        horizontalArrangement = Arrangement.spacedBy(px(16f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(px(48f)), contentAlignment = Alignment.Center) {
            if (themedApp != null) {
                Image(painter = themedApp.icon.painter, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else if (icon != null) {
                LauncherImage(icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(px(2f))) {
            BasicText(
                text = notice.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = text(if (focused) theme.onBackground else theme.onBackground.copy(alpha = LocalFoldTheme.current.theme.style.menu.noticeRestAlpha), LocalFoldTheme.current.theme.style.type.sideRow, theme),
            )
            if (notice.body.isNotEmpty()) {
                BasicText(
                    text = notice.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = text(theme.muted, LocalFoldTheme.current.theme.style.type.hint, theme),
                )
            }
        }
        if (notice.postedMillis > 0L) {
            BasicText(
                text = noticeAge(notice.postedMillis, System.currentTimeMillis()),
                style = text(theme.muted, LocalFoldTheme.current.theme.style.type.hint, theme),
            )
        }
    }
}

@Composable
private fun QuickTiles(
    app: FoldcadeApp,
    screen: HostScreen,
    panel: SidePanel,
    rows: List<Row>,
    keys: List<PromptKey?>,
    block: IntRange,
    interactive: Boolean,
    scale: Float,
    slots: SelectionSlots,
    modifier: Modifier,
    onEffect: (Effect?) -> Unit,
) {
    val theme = foldTheme()
    val tiles = rows.slice(block)
    val tileStart = block.first
    // R1's Wi-Fi and Bluetooth tiles light while their radio is on, as Android's own do.
    val status by app.deviceStatus.status.collectAsStateWithLifecycle()
    // Round icon buttons in one row, like Cocoon's quick settings, each named under it.
    Column(modifier.padding(vertical = px(12f)), verticalArrangement = Arrangement.spacedBy(px(16f))) {
        tiles.chunked(quickTileColumns).forEachIndexed { rowIndex, chunk ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = TileLine,
            ) {
                chunk.forEachIndexed { column, row ->
                    val index = tileStart + rowIndex * quickTileColumns + column
                    val focused = panel.index == index
                    val press = if (interactive) {
                        Modifier.hostPress { onEffect(app.shell.touchPanel(index, screen)) }
                    } else {
                        Modifier
                    }
                    val label = rowLabel(row, app.shell.model)
                    val lit = eased(if (focused) 1f else 0f, LocalFoldTheme.current.theme.motion.durationFocus, scale)
                    val lift = bouncy(if (focused) 1f else 0f, scale)
                    val ring = remember(theme) { FocusRing(theme) }
                    val words = tileLabel(row)
                    val setting = (row as? Row.QuickTile)?.setting
                    val radioOn = when (setting) {
                        QuickSetting.Wifi -> status.wifiOn
                        QuickSetting.Bluetooth -> status.bluetooth
                        else -> false
                    }
                    val on = eased(if (radioOn) 1f else 0f, LocalFoldTheme.current.theme.motion.durationSelect, scale)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(QUICK_BUTTON_DP.dp)
                            // The tile, its line, the tiles' block, and the menu's column.
                            .selectionSlot(slots, index, depth = 4, round = true)
                            .keepInView(focused)
                            .graphicsLayer { clip = false }
                            .drawBehind {
                                val menu = theme.style.menu
                                val radius = size.minDimension * theme.style.shape.round
                                drawRoundRect(
                                    theme.onBackground.copy(alpha = menu.quickTileAlpha + (menu.quickTileLitAlpha - menu.quickTileAlpha) * lit.value),
                                    cornerRadius = CornerRadius(radius, radius),
                                )
                                // A radio that is on fills its tile with the focus colours, as a switch's track lights.
                                if (on.value > 0f) {
                                    drawRoundRect(
                                        brush = Brush.linearGradient(listOf(ring.onStart, ring.onEnd)),
                                        alpha = (menu.quickTileOnAlpha * on.value).coerceIn(0f, 1f),
                                        cornerRadius = CornerRadius(radius, radius),
                                    )
                                }
                            }
                            .then(press)
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        val quick = setting?.let { quickIcon(it, status) }
                        val icon = quick ?: rowIcon(row)
                        if (icon != null) {
                            val art = themeIcon(icon)
                            Image(
                                painter = art.painter,
                                contentDescription = null,
                                colorFilter = art.requestedTint(theme.onBackground),
                                modifier = Modifier
                                    // A place's art is a whole lit disc, so it fills more of its tile.
                                    .size((QUICK_BUTTON_DP * if (quick != null) 0.46f else 0.74f).dp)
                                    .lifted(lit, lift, grow = theme.style.menu.quickArtGrow, rest = theme.style.menu.artRestAlpha),
                            )
                        }
                        // A round tile has no right side, so its chord's key sits on its rim.
                        val chord = keys[index]
                        if (chord != null) {
                            PromptImage(
                                promptGlyph(chord),
                                22.dp,
                                Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 2.dp),
                            )
                        }
                    }
                    if (words != null) {
                        val quickRest = LocalFoldTheme.current.theme.style.menu.quickLabelRestAlpha
                        BasicText(
                            text = words,
                            maxLines = 1,
                            style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.hint, theme),
                            modifier = Modifier.graphicsLayer { alpha = quickRest + (1f - quickRest) * lit.value },
                        )
                    }
                    }
                }
            }
        }
    }
}

private const val QUICK_BUTTON_DP = 56f

/**
 * Round tiles spaced evenly across their line, as [quickTileColumns] of them would be. A line
 * of fewer, such as a folder's Move and Rename, keeps that spacing and sits in the middle.
 */
private val TileLine = object : Arrangement.Horizontal {
    override fun Density.arrange(totalSize: Int, sizes: IntArray, layoutDirection: LayoutDirection, outPositions: IntArray) {
        if (sizes.isEmpty()) return
        val used = sizes.sum()
        val gap = ((totalSize - used * quickTileColumns / sizes.size) / (quickTileColumns + 1)).coerceAtLeast(0)
        var x = (totalSize - used - gap * (sizes.size - 1)) / 2
        sizes.forEachIndexed { i, size ->
            outPositions[i] = x
            x += size + gap
        }
    }
}

/** A radio's tile wears its off icon while the radio is off. */
private fun quickIcon(setting: QuickSetting, status: DeviceStatus): Int = when (setting) {
    QuickSetting.Wifi -> if (status.wifiOn) R.drawable.ic_status_wifi_4 else R.drawable.ic_status_wifi_off
    QuickSetting.Bluetooth -> if (status.bluetooth) R.drawable.ic_status_bluetooth else R.drawable.ic_status_bluetooth_off
    QuickSetting.Battery -> R.drawable.ic_status_battery_100
    QuickSetting.Settings -> R.drawable.ic_status_settings
}

/** Above the grid: the shelf name. The home grid and All have nothing here. */
@Composable
private fun GridHeader(shell: app.foldcade.ShellController) {
    val theme = foldTheme()
    val shelf = homeGridLabel(shell.model.homeGrid) ?: Copy.folders.takeIf { shell.homeFoldersOpen() } ?: return
    BasicText(text = shelf, style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.sideRow, theme))
}

/**
 * Under the grid, where the screen sits along the strip, empty columns past the
 * last tile included. None while the tiles fit on one screen.
 */
@Composable
private fun StripBar(count: Int, first: Int, shown: Int, scale: Float) {
    val columns = GridStrip.reachColumns(count)
    val motion = LocalFoldTheme.current.theme.motion
    val start by glideState(first.toFloat(), motion.durationTravel, scale)
    if (GridStrip.columns(count) <= shown) return
    val theme = foldTheme()
    val track = px(STRIP_BAR_WIDTH)
    val thumb = track * (shown.toFloat() / columns)
    Box(
        Modifier.fillMaxWidth().padding(top = px(5f), bottom = px(9f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(track)
                .height(px(theme.style.tile.stripPx))
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

private const val STRIP_BAR_WIDTH = 160f

/** Between All games' bar and the grid under it, so the focused tile's glow clears the bar as it clears the next row. */
private const val LIBRARY_BAR_GAP = 28f

/** How far ahead a flick carries the strip: its speed as the finger lifts, for this long. */
private const val STRIP_FLICK_SECONDS = 0.15f

/** How stiffly the strip gives past either end. It never gives a whole column. */
private const val STRIP_GIVE = 0.55f

/** [raw] columns along a strip that rests from 0 to [last], with the overshoot past either end damped. */
private fun stretched(raw: Float, last: Float): Float {
    fun give(over: Float) = over * STRIP_GIVE / (1f + over * STRIP_GIVE)
    return when {
        raw < 0f -> -give(-raw)
        raw > last -> last + give(raw - last)
        else -> raw
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
    val design = LocalFoldTheme.current.theme.style
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inset = maxWidth * design.metrics.insetFraction
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
    val actions = hintFor(
        when {
            model.connectOpen -> HintPlace.Connect
            model.dialog != null -> HintPlace.Dialog
            model.panel != null -> HintPlace.Menu
            !model.atLibraryRoot -> HintPlace.InsidePlatform
            else -> HintPlace.RootGrid
        },
    )
    // The grid's own keys only count while nothing covers it.
    val gridKeys = model.panel == null && model.dialog == null && model.saveConflict == null && model.savePicker == null && !model.connectOpen
    val page = model.playActivity?.takeIf { model.dialog == null }?.let { playActivityHints(model) }
    val find = shell.findHints()
    val normalKeys = find ?: ((page ?: panelHints(model) ?: gridHints(
        actions,
        if (model.connectOpen) HomeKeys.Idle else shell.homeKeys(),
        model.faceMap,
        screens = gridKeys && shell.focusedLaunchesOnOneScreen(),
        options = gridKeys && !model.connectOpen && shell.hasOptions(),
        drop = shell.homeHoldDrop(),
        play = if (gridKeys) shell.focusedPlayKey() else PlayKey.None,
    // All games' bar shows L2 and R2 beside its tabs, so only Hidden and Folders name them here.
    )) + if (gridKeys && model.settings == null && model.playActivity == null &&
        (model.homeGrid != HomeGrid.StandIns || shell.homeFoldersOpen())
    ) {
        if (model.homeGrid == HomeGrid.Hidden && model.count == 0) emptyList()
        else triggerHints(model.gridLabels.isNotEmpty())
    } else emptyList())
    // An empty collection's one button, named on A.
    val addGames = if (gridKeys && find == null && model.settings == null && model.playActivity == null && shell.emptyCollection() != null) {
        listOf(HintKey(promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, Copy.addGames))
    } else emptyList()
    val destructive = model.panel?.let { panelRows(it, model).getOrNull(it.index) }?.let { it == LanguageRow.RemoveDownload || it == LanguageRow.DeleteShortcut } == true ||
        model.playActivity?.sessions != null
    val back = promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB
    val selecting = gridKeys && shell.homeSelection() != null
    val optionsKey = actionFaces(model.faceMap).optionsGlyph
    val shown = if (!selecting && model.undoVerb != null && model.panel == null && model.dialog == null) listOf(HintKey(optionsKey, Copy.undo)) else if (!selecting && gridKeys && (shell.downloading.isNotEmpty() || shell.activity.values.any { it is TileActivity.Downloading || it == TileActivity.Checking })) {
        normalKeys.filterNot { it.key == back } + HintKey(back, "Cancel download")
    } else addGames + normalKeys
    // The root grid names Select's Find while the row has room, so the button can be found.
    val atRoot = gridKeys && find == null && shell.find == null && model.settings == null && model.playActivity == null &&
        model.homeGrid == HomeGrid.StandIns && !model.libraryGrid && model.atLibraryRoot && shell.homeKeys() == HomeKeys.Idle
    val keys = withFindHint(shown, atRoot)
    if (keys.isEmpty()) return
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(px(16f), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) BasicText(SelectionCopy.count(shell.homeSelection()!!.checked.size) + " ·", style = text(foldTheme().muted, LocalFoldTheme.current.theme.style.type.hint, foldTheme()))
        if (!selecting && model.undoVerb != null && model.panel == null && model.dialog == null) BasicText(text = model.undoVerb + " ·", style = text(foldTheme().muted, LocalFoldTheme.current.theme.style.type.hint, foldTheme()))
        if (destructive) BasicText(text = Copy.hold, style = text(foldTheme().muted, LocalFoldTheme.current.theme.style.type.hint, foldTheme()))
        keys.forEach { key -> HintWord(model, key) }
    }
}

/** One small glyph and a word. The glyph fills while that key is held. */
@Composable
internal fun HintWord(
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
        if (hint.label.isNotEmpty()) BasicText(text = hint.label, style = text(foldTheme().muted, LocalFoldTheme.current.theme.style.type.hint, foldTheme()))
    }
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
        style = text(if (hintFocused) theme.focus else theme.muted, LocalFoldTheme.current.theme.style.type.heroMeta, theme)
            .copy(textAlign = if (centred) TextAlign.Center else TextAlign.Start),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StripGrid(
    app: FoldcadeApp,
    screen: HostScreen,
    cell: Dp,
    rowGap: Dp,
    columnGap: Dp,
    /** Columns this panel fits. A panel the grid is fading out of keeps its own, not the new panel's. */
    columns: Int,
    pad: Dp,
    scale: Float,
    showTitle: Boolean,
    homeTiles: Boolean = false,
) {
    val motion = LocalFoldTheme.current.theme.motion
    val model = app.shell.model
    val first = shownFirstColumn(model, columns)
    val screenful = columns * Metrics.rows
    val titles = shownTitles(app, GridStrip.index(first, 0), screenful)
    // The emulator reads this line. A presentation-display dump is empty while that display has focus.
    LaunchedEffect(titles) {
        Log.i("Foldcade", "library-ui page " + titles.joinToString(" | "))
    }
    val strip = remember { Animatable(first.toFloat()) }
    val position = glideState(first.toFloat(), motion.durationTravel, scale, anim = strip)
    WarmColumns(app, first, columns)
    // Where a finger holds the strip, in columns. Null while no finger does.
    var held by remember { mutableStateOf<Float?>(null) }
    val reduced = motion.reduced(scale)
    // Read where it is used, in offsets and layers, so scrolling redraws without
    // recomposing the columns. Composition only hears which columns are on screen.
    val shownFirst = remember { { held ?: position.value } }
    val width = cell * columns + columnGap * (columns - 1)
    val pitchPx = with(LocalDensity.current) { (cell + columnGap).toPx() }
    // The theme's scene slides with the strip, so scrolling reads as the view moving, not the tiles.
    MovesBackdropCamera { shownFirst() * pitchPx }
    val lastColumn = GridStrip.reachColumns(model.count) - 1
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folderToken = app.shell.homeAnimToken()
    val carry = if (homeTiles) app.shell.homeCarry() else null
    val memory = rememberSlotMemory(if (homeTiles) app.shell.homeSlots() else emptyList(), folderToken)
    val swipes = app.shell.scrollsByTouch()
    // Read as the finger moves, so tiles arriving mid-drag don't restart the gesture.
    val lastFirst by rememberUpdatedState(GridStrip.lastFirst(model.count, columns))
    // The finger lets go: the strip carries on from where it was left, at the speed it was
    // going, and settles on the column nearest where that speed would take it. With
    // animations off, it lands there at once.
    val release by rememberUpdatedState<(Float) -> Unit> release@{ pixelsPerSecond ->
        val from = held ?: return@release
        val speed = if (pitchPx > 0f) -pixelsPerSecond / pitchPx else 0f
        val rest = (from + speed * STRIP_FLICK_SECONDS).roundToInt()
        scope.launch {
            app.shell.touchScroll(rest)
            val target = shownFirstColumn(app.shell.model, columns).toFloat()
            // The strip takes over from the finger in one step, so no frame shows it anywhere else.
            strip.snapTo(if (reduced) target else from)
            held = null
            if (reduced) return@launch
            withContext(SteadyMenu) {
                strip.animateTo(target, motion.glide(motion.durationTravel, scale, target - from, speed), initialVelocity = speed)
            }
        }
    }
    val folderPop = remember { Animatable(1f) }
    val folderPopFrom = LocalFoldTheme.current.theme.style.motion.folderPopFrom
    LaunchedEffect(folderToken, scale) {
        folderPop.snapTo(folderPopFrom)
        withContext(SteadyMenu) {
            folderPop.animateTo(1f, motion.arrive(motion.durationShort, scale))
        }
    }
    // A finger moves the strip itself, so it is never faded out from under it.
    val fading by remember(reduced, first) { derivedStateOf { reduced && held == null && abs(shownFirst() - first) > 0.001f } }
    val popping by remember { derivedStateOf { folderPop.value != 1f } }
    Box(
        Modifier
            .requiredWidth(width + pad * 2)
            .then(
                if (fading || popping) {
                    Modifier.graphicsLayer {
                        if (fading) this.alpha = (1f - abs(shownFirst() - first)).coerceIn(0.35f, 1f)
                        scaleX = folderPop.value
                        scaleY = folderPop.value
                    }
                } else {
                    Modifier
                },
            )
            .pointerInput(swipes, pitchPx) {
                if (!swipes || pitchPx <= 0f) return@pointerInput
                // The strip moves under the finger, column for column, and gives a little past either end.
                val tracker = VelocityTracker()
                var from = 0f
                var walked = 0f
                var at = Offset.Zero
                try {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            from = shownFirst()
                            walked = 0f
                            tracker.resetTracking()
                            held = from
                            scope.launch { strip.stop() }
                        },
                        onDragEnd = {
                            // The lift counts as a sample, so a finger that stopped before lifting lets go still.
                            tracker.addPosition(SystemClock.uptimeMillis(), at)
                            release(tracker.calculateVelocity().x)
                        },
                        onDragCancel = { release(0f) },
                        onHorizontalDrag = { change, amount ->
                            walked += amount
                            at = change.position
                            tracker.addPosition(change.uptimeMillis, at)
                            held = stretched(from - walked / pitchPx, lastFirst.toFloat())
                        },
                    )
                } finally {
                    // A grid that changes under the finger still lets the strip settle.
                    release(0f)
                }
            }
            .padding(pad),
    ) {
        val low by remember(lastColumn) {
            derivedStateOf { floor(shownFirst()).toInt().coerceIn(0, lastColumn.coerceAtLeast(0)) }
        }
        val high by remember(lastColumn, columns) {
            derivedStateOf { ceil(shownFirst() + columns - 1).toInt().coerceIn(0, lastColumn.coerceAtLeast(0)) }
        }
        var rowPx by remember { mutableStateOf(0) }
        for (drawn in low..high) {
            // Keyed, so a column keeps its decoded art as the strip scrolls. Unkeyed,
            // columns moved slots as the strip settled and every tile reloaded.
            key(drawn) {
                Box(
                    Modifier
                        .offset { IntOffset(((drawn - shownFirst()) * pitchPx).roundToInt(), 0) }
                        .graphicsLayer {
                            // A column fades as it slides past either edge, so the strip has no cut ends.
                            val along = drawn - shownFirst()
                            val outside = maxOf(-along, along - (columns - 1), 0f)
                            alpha = (1f - outside).coerceIn(0f, 1f)
                        },
                ) {
                    StripColumn(app, screen, cell, rowGap, cell + columnGap / 2, drawn, showTitle, carry, memory, pitchPx, { rowPx }) { rowPx = it }
                }
            }
        }
        if (homeTiles) {
            HomeTarget(carry, cell, width, pitchPx, shownFirst, scale)
            CarriedTile(app, carry, memory, cell, rowGap, rowPx, pitchPx, shownFirst, scale) { at ->
                (context as? FoldcadeHomeActivity)?.dispatch(app.shell.touchCell(at, screen))
            }
        }
        GridCursor(
            app = app,
            index = model.focus.cellIndex,
            cell = cell,
            gap = rowGap,
            columns = columns,
            pitchPx = pitchPx,
            rowPx = rowPx,
            shownFirst = shownFirst,
            jump = folderToken,
            scale = scale,
        )
    }
}

/**
 * The one focus ring on the grid. It slides from tile to tile instead of each tile
 * drawing its own, so moving reads as steering a cursor. Across is measured along
 * the whole strip and drawn against the columns on screen: when the strip scrolls,
 * the ring stays at the edge as the tiles slide under it, and a drag carries the
 * ring with the tiles rather than against them.
 */
@Composable
private fun GridCursor(
    app: FoldcadeApp,
    index: Int,
    cell: Dp,
    gap: Dp,
    /** Columns this panel fits. A fading panel keeps its own edge, not the panel the grid moved to. */
    columns: Int,
    pitchPx: Float,
    rowPx: Int,
    shownFirst: () -> Float,
    jump: String,
    scale: Float,
) {
    val motion = LocalFoldTheme.current.theme.motion
    val shell = app.shell
    val model = shell.model
    val shown = model.dialog == null &&
        model.panel == null &&
        !model.connectOpen &&
        model.focus.chrome == null &&
        !shell.homeEditing()
    val alpha by motionState(
        target = if (shown) 1f else 0f,
        spec = if (shown) motion.arrive(motion.durationFocus, scale) else motion.leave(motion.durationFocus, scale),
    )
    val column = GridStrip.column(index).toFloat()
    val row = GridStrip.row(index).toFloat()
    // A new folder, or a ring coming back from elsewhere, starts on its tile.
    // Held moves glide from tile to tile.
    val visible by remember { derivedStateOf { alpha > 0f } }
    val lands = !shown || !visible
    val x by glideState(column, motion.durationTravel, scale, snap = lands, jump = jump)
    val y by glideState(row, motion.durationTravel, scale, snap = lands, jump = jump)
    // Show on Home: once the ring has reached the tile, two rings burst out of it and fade.
    val burst = remember { Animatable(1f) }
    // The shell hands each landing to one ring: the one drawn as Find closes, or this one when
    // it was All that went there. A ring drawn later, as after a game, plays nothing.
    LaunchedEffect(shell.revealPulse) {
        if (!shell.takeRevealBurst()) return@LaunchedEffect
        burst.snapTo(1f)
        delay(motion.duration(motion.durationTravel, scale).toLong())
        burst.snapTo(0f)
        withContext(SteadyMenu) { burst.animateTo(1f, motion.leave(motion.durationIsland * BURST_SPAN, scale)) }
    }
    if (!visible) return
    val theme = foldTheme()
    val radius = cell * theme.iconRadius
    val ring = remember { Path() }
    Box(
        Modifier
            .size(cell)
            .then(selectionFeedback(shell.homeSelection()?.refusals?.takeIf { shell.homeSelection()?.refusedAt == index } ?: 0))
            .graphicsLayer {
                val pitchY = (if (rowPx > 0) rowPx.toFloat() else cell.toPx()) + gap.toPx()
                // Kept on screen: a finger can drag its tile away, and then the ring waits at the edge.
                translationX = (x - shownFirst()).coerceIn(0f, columns - 1f) * pitchPx
                translationY = y * pitchY
                clip = false
                scaleX = motion.scaleFocus
                scaleY = motion.scaleFocus
                this.alpha = alpha
            }
            .drawBehind {
                val cornerPx = radius.toPx()
                ring.reset()
                ring.addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(cornerPx, cornerPx)))
                // Only outside the tile, so the glow never washes over the art.
                clipPath(ring, ClipOp.Difference) {
                    with(FocusRing(theme)) {
                        drawFocusRing(cornerPx)
                        val t = burst.value
                        if (t < 1f) {
                            for (wave in 0 until BURST_WAVES) {
                                // Each wave starts a little after the one before and grows as it fades.
                                val local = ((t - wave * BURST_LAG) / (1f - BURST_LAG)).coerceIn(0f, 1f)
                                if (local <= 0f || local >= 1f) continue
                                val grow = BURST_REACH * size.minDimension * local
                                drawFocusRing(
                                    cornerPx + grow,
                                    alpha = 1f - local,
                                    bounds = Rect(-grow, -grow, size.width + grow, size.height + grow),
                                )
                            }
                        }
                    }
                }
            },
    )
}

/** Show on Home's burst: how many rings, how far apart they start, how far out they reach, and how long. */
private const val BURST_WAVES = 2
private const val BURST_LAG = 0.3f
private const val BURST_REACH = 0.32f
private const val BURST_SPAN = 3

/** Titles of [size] tiles from [start], the same labels [StripColumn] draws. */
private fun shownTitles(app: FoldcadeApp, start: Int, size: Int): List<String> {
    val shell = app.shell
    val showingHome = !shell.model.libraryGrid && shell.model.homeGrid == HomeGrid.StandIns
    val order = displayOrder(shell.model)
    val end = minOf(start + size, shell.model.count)
    return (start until end).map { index ->
        val source = if (showingHome) index else order.getOrElse(index) { index }
        shell.tileFromOrder(source)?.title.orEmpty()
    }
}

/** One column of the strip, its tiles top to bottom. */
@Composable
private fun StripColumn(
    app: FoldcadeApp,
    screen: HostScreen,
    cell: Dp,
    gap: Dp,
    /** A title may reach a little into the gaps beside its tile. */
    titleWidth: Dp,
    column: Int,
    showTitle: Boolean,
    carry: HomeCarry?,
    memory: SlotMemory,
    pitchPx: Float,
    rowPx: () -> Int,
    onRowHeight: (Int) -> Unit,
) {
    val context = LocalContext.current
    val shell = app.shell
    val focus = shell.model.focus
    val library = shell.model.libraryGrid
    val showingHome = !library && shell.model.homeGrid == HomeGrid.StandIns
    val order = displayOrder(shell.model)
    val radius = cell * foldTheme().iconRadius
    val editing = shell.homeEditing()
    val lifted = shell.homeLiftedIndex()
    val foldersOpen = shell.homeFoldersOpen()
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        for (row in 0 until Metrics.rows) {
            val index = GridStrip.index(column, row)
            // The activity runs what a second tap opens, as it does for A.
            val tap = { (context as? FoldcadeHomeActivity)?.dispatch(shell.touchCell(index, screen)) }
            // A tile's height with its title is the row pitch the cursor steps by. Past the last tile,
            // nothing is drawn but a lifted gap.
            val beyond = index >= shell.model.count
            val past = beyond && !(showingHome && lifted == index)
            val id = if (showingHome && !past) shell.homeFace(index)?.id else null
            val glide = slotGlide(memory, id, index, carry?.id)
            val placed = if (row == 0 && !past) Modifier.onSizeChanged { onRowHeight(it.height) } else Modifier
            Box(
                placed.then(selectionFeedback(shell.homeSelection()?.refusals?.takeIf { shell.homeSelection()?.refusedAt == index } ?: 0)).then(
                    if (glide == null) Modifier else Modifier.zIndex(1f).graphicsLayer {
                        val pitchY = (if (rowPx() > 0) rowPx().toFloat() else cell.toPx()) + gap.toPx()
                        translationX = glide.x.value * pitchPx
                        translationY = glide.y.value * pitchY
                    },
                ),
            ) {
                if (past) {
                    // A tap rests the cursor here, as on any empty slot.
                    Box(Modifier.size(cell).hostPress { tap() })
                } else {
                    val source = if (showingHome) index else order.getOrElse(index) { index }
                    val face = if (showingHome) shell.homeFace(index) else null
                    val game = shell.tileFromOrder(source)
                    // A tile in the hand draws itself over the grid, so its slot is only
                    // the space it left, and focus is where it is held.
                    val focused = shell.model.dialog == null &&
                        shell.model.panel == null &&
                        !shell.model.connectOpen &&
                        focus.chrome == null &&
                        focus.cellIndex == index &&
                        carry == null
                    val label = game?.title.orEmpty()
                    val libraryTile = library && face == null
                    // Folders marks the exception: a folder off Home rests dimmed. All games leaves that to
                    // its Not on Home tab, since every game is in its folder on Home.
                    val offHome = face != null && foldersOpen && face.folder && !face.onGrid
                    val appObject = appGlyph(game?.androidPackage)
                    val art = tileArt(app, game, face, automatic = appObject == null)
                    val themedApp = if (art == null) appObject else null
                    Cell(
                        title = label,
                        mark = game?.mark,
                        themedApp = themedApp,
                        art = art,
                        showTitle = showTitle,
                        focused = focused,
                        size = cell,
                        titleWidth = titleWidth,
                        corner = radius,
                        icon = if (art == null && themedApp == null) launcherIcon(game?.androidPackage) else null,
                        favorite = game?.favorite == true,
                        empty = face?.empty == true || beyond,
                        selected = face?.id in shell.homeSelection()?.checked.orEmpty(),
                        lifted = lifted == index && carry == null,
                        socket = carry?.slot == index,
                        opened = carry?.over == index,
                        editing = editing,
                        offHome = offHome,
                        onServer = face?.onServer == true,
                        downloading = shell.isDownloading(face?.id),
                        activity = shell.activityAt(source),
                        saveHealth = shell.saveHealthAt(source, game),
                        library = libraryTile,
                        settledAt = if (face?.folder == true) shell.settledAt(face.id) else 0L,
                        onClick = { tap() },
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
                        onDrop = { (context as? FoldcadeHomeActivity)?.dispatch(shell.onMeaning(Meaning.Activate, screen)) },
                    )
                }
            }
        }
    }
}

/**
 * A game tile's art, or null for a group, an empty slot, or art not found yet. An app the theme
 * draws passes [automatic] false: its object beats found art, which the top screen still shows,
 * and only art the user picked replaces it.
 */
@Composable
private fun tileArt(app: FoldcadeApp, game: ShelfGame?, face: HomeFace?, automatic: Boolean = true): Painter? {
    val shell = app.shell
    // A folder or an empty slot keeps its mark. A game asks for its art.
    val artGame = game?.takeIf { face?.folder != true && face?.empty != true }
    return key(artGame?.id) {
        val found = rememberArt(app, artGame?.let(shell::artRecord), artGame?.let(shell::artQuery), automatic)
        found?.let { rememberCoverPainter(app, it.square ?: it.cover, TILE_PX)?.painter }
    }
}

/**
 * Where each Home tile was drawn last, so one that changes slots, in a swap, a cancel or a
 * Tidy, glides there from its old slot instead of appearing in the new one. A new board, a
 * folder opened or closed, starts over.
 */
private class SlotMemory {
    var token: Any? = null
    var placed: Map<String, Int> = emptyMap()

    /** Tiles still on their way, by id, so a move made mid-glide starts from where they are. */
    val moving = HashMap<String, SlotGlide>()

    /** The grid was under a menu's blur, and when that menu last closed. */
    var under = false
    var clearedAt = 0L

    /**
     * How long a change made as a menu closes waits, so its motion is not lost under the blur
     * easing out: Tidy chosen in the Y menu, or a tile Move lifted.
     */
    fun wait(blurMillis: Long): Long = (clearedAt + blurMillis - SystemClock.uptimeMillis()).coerceAtLeast(0L)
}

/** How far a tile is from its slot, in columns and rows. Both ease to 0. */
private class SlotGlide(val x: Animatable<Float, AnimationVector1D>, val y: Animatable<Float, AnimationVector1D>)

@Composable
private fun rememberSlotMemory(slots: List<String?>, token: Any): SlotMemory {
    val memory = remember { SlotMemory() }
    val under = LocalUnderMenu.current
    if (memory.under && !under) memory.clearedAt = SystemClock.uptimeMillis()
    memory.under = under
    if (memory.token != token) {
        memory.token = token
        memory.placed = emptyMap()
        memory.moving.clear()
    }
    SideEffect {
        memory.placed = buildMap { slots.forEachIndexed { index, id -> if (id != null && id !in this) put(id, index) } }
    }
    return memory
}

/** The glide of tile [id], new in slot [index], from where it was drawn. Null for one that did not move, or [held]. */
@Composable
private fun slotGlide(memory: SlotMemory, id: String?, index: Int, held: String?): SlotGlide? {
    val motion = LocalFoldTheme.current.theme.motion
    val scale = motion.animatorScale(LocalContext.current.contentResolver)
    val glide = remember(id, index) {
        val was = id?.takeIf { it != held }?.let { memory.placed[it] }
        if (id == null || was == null || was == index) {
            null
        } else {
            val flying = memory.moving[id]
            val dx = (GridStrip.column(was) - GridStrip.column(index)) + (flying?.x?.value ?: 0f)
            val dy = (GridStrip.row(was) - GridStrip.row(index)) + (flying?.y?.value ?: 0f)
            SlotGlide(Animatable(dx), Animatable(dy)).also { memory.moving[id] = it }
        }
    }
    if (glide != null) {
        LaunchedEffect(glide) {
            // A swap is one slot and quick. A tile Tidy sends a long way takes longer, so it is seen going.
            val far = hypot(glide.x.value, glide.y.value).coerceIn(1f, SLOT_GLIDE_FAR)
            val base = (motion.durationTravel * (1f + (far - 1f) * SLOT_GLIDE_STRETCH)).toInt()
            delay(memory.wait(motion.duration(motion.durationFocus, scale).toLong()))
            withContext(SteadyMenu) {
                launch { glide.x.animateTo(0f, motion.arrive(base, scale)) }
                glide.y.animateTo(0f, motion.arrive(base, scale))
            }
            if (memory.moving[id] === glide) memory.moving.remove(id)
        }
    }
    return glide
}

/**
 * Above an open folder or collection, while a game is held on its top row: the way out.
 * It peeks in faintly there, and lights up with the focus ring once Up raises the tile to
 * it. It sits over the held tile's column and follows it along the row.
 */
@Composable
private fun HomeTarget(
    carry: HomeCarry?,
    cell: Dp,
    width: Dp,
    pitchPx: Float,
    shownFirst: () -> Float,
    scale: Float,
) {
    val motion = LocalFoldTheme.current.theme.motion
    val theme = foldTheme()
    val near = carry?.canTakeOut == true && GridStrip.row(carry.at) == 0
    val lit = carry?.overHome == true
    val presence by eased(if (near) 1f else 0f, motion.durationFocus, scale)
    val glow by bouncy(if (lit) 1f else 0f, scale)
    val column by glideState(GridStrip.column(carry?.at ?: 0).toFloat(), motion.durationTravel, scale)
    val shownNow by remember { derivedStateOf { presence > 0f } }
    if (!shownNow) return
    val pillWidth = px(HOME_TARGET_WIDTH)
    val pillHeight = px(HOME_TARGET_HEIGHT)
    val ring = remember(theme) { FocusRing(theme) }
    val cellPx = with(LocalDensity.current) { cell.toPx() }
    val widthPx = with(LocalDensity.current) { width.toPx() }
    val rise = px(HOME_TARGET_RISE)
    Box(
        Modifier
            .offset(y = -pillHeight - rise)
            .size(pillWidth, pillHeight)
            .graphicsLayer {
                val pill = pillWidth.toPx()
                val center = (column - shownFirst()) * pitchPx + cellPx / 2f
                translationX = (center - pill / 2f).coerceIn(0f, (widthPx - pill).coerceAtLeast(0f))
                alpha = presence * (HOME_TARGET_PEEK + (1f - HOME_TARGET_PEEK) * glow.coerceIn(0f, 1f))
                val grow = 0.9f + 0.1f * presence + 0.08f * glow
                scaleX = grow
                scaleY = grow
                translationY = (1f - presence) * pillHeight.toPx() * 0.5f
                clip = false
            }
            .drawBehind {
                val corner = size.height / 2f
                drawRoundRect(color = theme.surface, cornerRadius = CornerRadius(corner, corner))
                drawRoundRect(
                    color = theme.focus.copy(alpha = theme.style.tile.homeTargetAlpha * (1f - glow.coerceIn(0f, 1f))),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = theme.style.tile.homeTargetStrokePx),
                )
                if (glow > 0f) with(ring) { drawFocusRing(corner, pronounced = true, alpha = glow.coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(px(14f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val home = themeIcon(R.drawable.symbol_home)
            Image(
                painter = home.painter,
                contentDescription = null,
                colorFilter = home.tint(theme.onBackground),
                modifier = Modifier.size(px(HOME_TARGET_HEIGHT * 0.7f)),
            )
            BasicText(
                text = Copy.home,
                style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.sideRow, theme),
                maxLines = 1,
            )
        }
    }
}

/** One stretch of a tile in the hand: [held] while it is, or the drop or cancel that ended it ([from]). */
private class CarryTurn(val held: Boolean, val from: HomeCarry?)

/** Where a tile that was put down settles: in its slot, or shrinking into the group it went in. */
private data class Landing(val index: Int, val into: Boolean)

/**
 * The tile in the hand, drawn over the grid. It lifts off its slot with a little stretch,
 * a size larger with a soft shadow under it, and bobs slowly while held. It glides from slot
 * to slot with the D-pad; over a group that would take it, it shrinks a little, as if about
 * to drop in; raised to the Home target, it reaches up. Put down, it settles into its slot
 * with a small squash, or shrinks away into the group. Motion Off holds it still: no bob and
 * no squash.
 */
@Composable
private fun CarriedTile(
    app: FoldcadeApp,
    carry: HomeCarry?,
    memory: SlotMemory,
    cell: Dp,
    gap: Dp,
    rowPx: Int,
    pitchPx: Float,
    shownFirst: () -> Float,
    scale: Float,
    onTap: (Int) -> Unit,
) {
    val motion = LocalFoldTheme.current.theme.motion
    val theme = foldTheme()
    val shell = app.shell
    val speed = shell.model.motionSpeed
    val held = carry != null
    val previous = remember { arrayOfNulls<HomeCarry>(1) }
    val turn = remember(held) { CarryTurn(held, if (held) null else previous[0]) }
    SideEffect { previous[0] = carry }
    val landing = remember(turn) {
        val from = turn.from ?: return@remember null
        val at = shell.homeSlots().indexOf(from.id)
        when {
            at >= 0 -> Landing(at, into = false)
            from.over != null -> Landing(from.over, into = true)
            else -> null
        }
    }
    val shown = carry ?: turn.from?.takeIf { landing != null }
    var done by remember(turn) { mutableStateOf(false) }
    // Where it is going, in columns along the strip and rows down.
    val goalColumn = GridStrip.column(carry?.at ?: landing?.index ?: 0).toFloat()
    val goalRow = when {
        carry?.overHome == true -> -HOME_REACH
        else -> GridStrip.row(carry?.at ?: landing?.index ?: 0).toFloat()
    }
    val liftGoal = when {
        carry == null -> if (landing?.into == true) INTO_SCALE else motion.scaleFocus
        carry.overHome -> motion.scaleOver
        carry.over != null -> motion.scaleOver
        else -> motion.scaleLift
    }
    val x = remember { Animatable(goalColumn) }
    val y = remember { Animatable(goalRow) }
    val lift = remember { Animatable(motion.scaleFocus) }
    val squash = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val goal = rememberUpdatedState(Offset(goalColumn, goalRow))
    LaunchedEffect(turn) {
        if (turn.held) {
            x.snapTo(goal.value.x)
            y.snapTo(goal.value.y)
            fade.snapTo(1f)
        }
        withContext(SteadyMenu) {
            snapshotFlow { goal.value }.collect { next ->
                launch { x.animateTo(next.x, motion.glide(motion.durationTravel, scale, next.x - x.value, x.velocity)) }
                launch { y.animateTo(next.y, motion.glide(motion.durationTravel, scale, next.y - y.value, y.velocity)) }
            }
        }
    }
    val liftTo = rememberUpdatedState(liftGoal)
    LaunchedEffect(turn) {
        withContext(SteadyMenu) {
            val back = motion.squashBack(speed, scale)
            if (turn.held) {
                // Picked up: a quick stretch as it leaves the grid, then it springs to size. A Move
                // chosen in a menu waits for the menu's blur to clear, so the lift is seen.
                lift.snapTo(motion.scaleFocus)
                delay(memory.wait(motion.duration(motion.durationFocus, scale).toLong()))
                if (back != null) {
                    launch {
                        squash.animateTo(-1f, motion.arrive(motion.durationShort, scale))
                        squash.animateTo(0f, back)
                    }
                }
                // Motion Off grows it without the overshoot, as it holds the bob and squash.
                val grow = if (back != null) motion.playful(motion.durationSelect, scale) else motion.arrive(motion.durationFocus, scale)
                snapshotFlow { liftTo.value }.collect { next ->
                    launch { lift.animateTo(next, grow) }
                }
            } else if (landing != null) {
                launch { lift.animateTo(liftTo.value, motion.arrive(motion.durationTravel, scale)) }
                if (landing.into) {
                    fade.animateTo(0f, motion.leave(motion.durationTravel, scale))
                } else {
                    delay(motion.duration(motion.durationTravel, scale).toLong() / 2)
                    // Put down: it lands with a small squash and wobbles still.
                    if (back != null) {
                        squash.animateTo(1f, motion.arrive(motion.durationShort, scale))
                        squash.animateTo(0f, back)
                    } else {
                        delay(motion.duration(motion.durationTravel, scale).toLong() / 2)
                    }
                }
                done = true
            }
        }
    }
    // Lifted 0 to 1: the shadow and the bob follow it, so both fade as the tile lands.
    val raised by remember { derivedStateOf { ((lift.value - motion.scaleFocus) / (motion.scaleLift - motion.scaleFocus)).coerceIn(0f, 1f) } }
    val bobSpec = motion.bob(speed, scale)
    val bob = if (bobSpec != null && held) {
        rememberInfiniteTransition(label = "carry").animateFloat(0f, 1f, bobSpec, label = "bob")
    } else {
        null
    }
    if (shown == null || done) return
    val face = remember(shown.id, shell.model.shelfEpoch) { shell.homeFaceOf(shown.id) }
    val game = remember(shown.id, shell.model.shelfEpoch) { shell.homeTileOf(shown.id) }
    val appObject = appGlyph(game?.androidPackage)
    val art = tileArt(app, game, face, automatic = appObject == null)
    val radius = cell * theme.iconRadius
    val ring = remember(theme) { FocusRing(theme) }
    val ringPath = remember { Path() }
    val bobPx = with(LocalDensity.current) { px(motion.bobPx).toPx() }
    val glowPx = with(LocalDensity.current) { px(motion.liftGlowPx).toPx() }
    val underGlow = game.mark?.let { markGlyph(it) }?.accent ?: theme.focus
    val glowStyle = LocalFoldTheme.current.theme.style.glow
    val underStops = remember { FloatArray(GlowFalloff.STOPS.size) }
    Box(
        Modifier
            .size(cell)
            .zIndex(2f)
            .graphicsLayer {
                val pitchY = (if (rowPx > 0) rowPx.toFloat() else cell.toPx()) + gap.toPx()
                translationX = (x.value - shownFirst()) * pitchPx
                translationY = y.value * pitchY
                alpha = fade.value
                clip = false
            },
    ) {
        // A dark shadow is lost on a black grid, so the tile casts a pool of its own light on
        // the grid under it instead. It stays put as the tile bobs, and dims as the tile rises.
        // Drawn without a layer of its own: a faded layer would clip the light to the tile's box.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val rise = bob?.value ?: 0f
                    val pool = Offset(size.width / 2f, size.height + glowPx * raised + rise * bobPx * 0.5f)
                    SoftGlow.fillStops(underStops, glowStyle.focusTightness, glowStyle.focusRim)
                    scale(lift.value * (1f - 0.06f * rise), lift.value * LIFT_GLOW_FLAT, pivot = pool) {
                        val peak = motion.liftGlowPeak * raised * (1f - 0.25f * rise)
                        drawRadialGlow(pool, size.width * LIFT_GLOW_REACH, underGlow, underStops, peak)
                    }
                },
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val stretch = motion.squash * squash.value
                    val by = lift.value / motion.scaleFocus
                    scaleX = by * (1f + stretch)
                    scaleY = by * (1f - stretch)
                    translationY = -(bob?.value ?: 0f) * bobPx * raised
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    clip = false
                },
        ) {
            val themedApp = if (art == null) appObject else null
            Cell(
                title = game.title,
                mark = game.mark,
                themedApp = themedApp,
                art = art,
                showTitle = false,
                focused = true,
                size = cell,
                corner = radius,
                icon = if (art == null && themedApp == null) launcherIcon(game.androidPackage) else null,
                favorite = game.favorite,
                onServer = face.onServer,
                downloading = shell.isDownloading(face.id),
                saveHealth = shell.saveHealthOf(game.id),
                // A tap on the tile in the hand puts it down, as a tap on its slot did.
                onClick = { carry?.let { onTap(it.at) } },
            )
            // The ring hugs the drawn tile, which the cell scales to focus size itself.
            val carriedFloor = LocalFoldTheme.current.theme.style.tile.carriedRingAlpha
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = motion.scaleFocus
                        scaleY = motion.scaleFocus
                        clip = false
                    }
                    .drawBehind {
                        val cornerPx = radius.toPx()
                        ringPath.reset()
                        ringPath.addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(cornerPx, cornerPx)))
                        clipPath(ringPath, ClipOp.Difference) {
                            with(ring) { drawFocusRing(cornerPx, alpha = carriedFloor + (1f - carriedFloor) * raised) }
                        }
                    },
            )
        }
    }
}

/**
 * One of Find's results, drawn as the grid draws its tile: the game's art, an app's object or icon,
 * or a group's mark. A hidden one wears a badge, since Find shows it all the same.
 */
@Composable
private fun FoundTile(app: FoldcadeApp, entry: FindEntry, focused: Boolean, size: Dp, showTitle: Boolean, onClick: () -> Unit) {
    val shell = app.shell
    val game = if (entry.kind == FindKind.Group) null else shell.findGame(entry.id)
    val appObject = appGlyph(game?.androidPackage)
    val art = key(game?.id) {
        val found = rememberArt(app, game?.let(shell::artRecord), game?.let(shell::artQuery), automatic = appObject == null)
        found?.let { rememberCoverPainter(app, it.square ?: it.cover, TILE_PX)?.painter }
    }
    val corner = size * foldTheme().iconRadius
    val themedApp = if (art == null) appObject else null
    Box {
        Cell(
            title = entry.title,
            mark = entry.mark ?: game?.mark,
            themedApp = themedApp,
            art = art,
            showTitle = showTitle,
            focused = focused,
            size = size,
            corner = corner,
            icon = if (art == null && themedApp == null) launcherIcon(game?.androidPackage) else null,
            favorite = false,
            onServer = shell.onServer(entry.id),
            downloading = shell.isDownloading(entry.id),
            saveHealth = shell.saveHealthOf(entry.id),
            onClick = onClick,
        )
        if (entry.hidden) {
            HiddenBadge(px(HIDDEN_BADGE_PX), Modifier.padding(maxOf(px(8f), corner * CORNER_SAFE)))
        }
    }
}

/** Slots past which a gliding tile takes no longer, and how much longer each slot makes it. */
private const val SLOT_GLIDE_FAR = 4f
private const val SLOT_GLIDE_STRETCH = 0.6f

/** The light a held tile casts under it: how wide it reaches, as a share of the tile, and how flat it lies. */
private const val LIFT_GLOW_REACH = 0.62f
private const val LIFT_GLOW_FLAT = 0.42f

/** A tile put into a group shrinks to this as it goes in. */
private const val INTO_SCALE = 0.3f

/** How far up a held tile reaches toward the Home target, in rows. */
private const val HOME_REACH = 0.12f

/** The Home target, in panel pixels, and how far above the top row it sits. */
private const val HOME_TARGET_WIDTH = 220f
private const val HOME_TARGET_HEIGHT = 64f
private const val HOME_TARGET_RISE = 18f

/** How visible the Home target is while it only peeks in, before the tile reaches it. */
private const val HOME_TARGET_PEEK = 0.45f

private const val HIDDEN_BADGE_PX = 40f

private const val GRID_RING_PAD = 8f

/** The widest a panel spaces its grid for, as a share of its height. Wider panels add columns. */
private const val WIDE_SPAN = 4f / 3f

/** Titled rows' gap, as a share of the gap between columns. */
private const val TITLED_ROW_GAP = 0.5f

/** The most the gap between columns grows to spread the strip across its panel, as a multiple of the gap. */
private const val MAX_COLUMN_SPREAD = 1.5f

@Composable
private fun emptyShelf(grid: HomeGrid): String = when (grid) {
    HomeGrid.Hidden -> Copy.noHiddenApps
    HomeGrid.StandIns -> Copy.noGames
}

/**
 * A game's state on its tile, in one small chip in the tile's corner: the pause
 * mark while the game waits open in its player behind Home, the cloud
 * while it is only on the server, the cloud breathing in the focus colour while
 * it downloads, with a bar that fills beside it, the sync mark breathing while
 * saves sync, and for a moment after, a check when the sync worked.
 */
@Composable
private fun TileStatus(
    onServer: Boolean,
    focused: Boolean,
    downloading: Boolean,
    activity: TileActivity?,
    saveHealth: SaveHealth,
    animatorScale: Float,
    modifier: Modifier,
) {
    val motion = LocalFoldTheme.current.theme.motion
    val theme = foldTheme()
    val tile = theme.style.tile
    // A paused game, or one its computer is streaming, is waiting, not working, so its mark holds still.
    val waiting = activity == TileActivity.Paused || activity is TileActivity.OnHost
    val busy = downloading || (activity != null && activity !is TileActivity.Synced && !waiting)
    val breath = if (busy && !motion.reduced(animatorScale)) {
        rememberInfiniteTransition(label = "busy").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = motion.pulse(animatorScale),
            label = "breath",
        )
    } else {
        null
    }
    val loading = activity as? TileActivity.Downloading
    val showsBar = loading != null || activity == TileActivity.Checking
    val synced = activity as? TileActivity.Synced
    val shown = if (synced != null) syncMarkShown(synced, animatorScale) else null
    Row(
        modifier = modifier
            .then(if (showsBar) Modifier.fillMaxWidth() else Modifier)
            .graphicsLayer {
                val grown = shown?.value?.let { SYNC_MARK_FROM + (1f - SYNC_MARK_FROM) * it } ?: 1f
                alpha = shown?.value ?: 1f
                scaleX = grown
                scaleY = grown
                transformOrigin = TransformOrigin(1f, 0.5f)
            }
            .height(px(tile.statusChipPx))
            .clip(theme.style.shape.control())
            .background(theme.background.copy(alpha = tile.statusBackingAlpha))
            .padding(horizontal = px(((tile.statusChipPx - tile.statusIconPx) / 2f).coerceAtLeast(0f))),
        horizontalArrangement = Arrangement.spacedBy(px(8f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showsBar) {
            // Before the size is known the bar waits empty; the cloud beside it breathes.
            DownloadBar(
                fraction = if (activity == TileActivity.Checking) 1f else loading?.fraction ?: 0f,
                animatorScale = animatorScale,
                modifier = Modifier.weight(1f),
            )
        }
        val (icon, tint) = when {
            synced != null -> if (synced.mark.worked) {
                R.drawable.ic_badge_check to theme.focus
            } else {
                R.drawable.ic_badge_sync to theme.muted
            }
            activity == TileActivity.Syncing -> R.drawable.ic_badge_sync to theme.focus
            saveHealth.needsAttention && !busy -> R.drawable.ic_badge_unsynced to theme.style.colors.attention
            waiting -> R.drawable.ic_badge_paused to theme.focus
            busy -> R.drawable.ic_badge_cloud to theme.focus
            focused -> R.drawable.ic_badge_cloud to theme.onBackground
            else -> R.drawable.ic_badge_cloud to theme.muted
        }
        val badge = themeIcon(icon)
        Image(
            painter = badge.painter,
            contentDescription = null,
            colorFilter = badge.tint(tint),
            modifier = Modifier
                .size(px(tile.statusIconPx))
                // Read in the layer, so each frame redraws without recomposing the tile.
                .graphicsLayer { alpha = if (synced == null) breath?.value ?: 1f else 1f },
        )
    }
}

/** The bar beside a downloading tile's cloud. It fills with [fraction]. */
@Composable
private fun DownloadBar(fraction: Float, animatorScale: Float, modifier: Modifier) {
    val motion = LocalFoldTheme.current.theme.motion
    val theme = foldTheme()
    val filled by motionState(
        target = fraction.coerceIn(0f, 1f),
        spec = motion.arrive(motion.durationTravel, animatorScale),
    )
    val tile = theme.style.tile
    Box(
        modifier
            .height(px(tile.downloadBarPx))
            .clip(theme.style.shape.control())
            .drawBehind {
                drawRect(theme.onBackground.copy(alpha = tile.downloadTrackAlpha))
                drawRect(color = theme.style.colors.progress ?: theme.focus, size = Size(size.width * filled, size.height))
            },
    )
}

/** A finished sync's mark grows in, holds, and fades before its time is up. */
@Composable
private fun syncMarkShown(synced: TileActivity.Synced, animatorScale: Float): State<Float> {
    val motion = LocalFoldTheme.current.theme.motion
    val shown = remember(synced.untilMillis) { Animatable(0f) }
    LaunchedEffect(synced.untilMillis) {
        withContext(SteadyMenu) {
            shown.animateTo(1f, motion.arrive(motion.durationFocus, animatorScale))
            val fade = motion.duration(motion.durationTravel, animatorScale)
            delay((synced.untilMillis - System.currentTimeMillis() - fade).coerceAtLeast(0L))
            shown.animateTo(0f, motion.leave(motion.durationTravel, animatorScale))
        }
    }
    return shown.asState()
}

/** A status chip sits this share of the tile's corner radius in from each edge, clear of the curve. */
private const val CORNER_SAFE = 0.3f

/** A sync mark grows in from this scale. */
private const val SYNC_MARK_FROM = 0.6f

/** A collection settling into its new look starts this small, then overshoots back to size. */
private const val SETTLE_FROM = 0.86f

/** Only a look taken this recently settles; the tile sees it as soon as the editor closes. */
private const val SETTLE_WINDOW_MILLIS = 600L

@Composable
private fun Cell(
    title: String,
    mark: String?,
    /** The theme's object for an app ([appGlyph]), drawn in place of [mark] and its launcher [icon]. */
    themedApp: MarkGlyph? = null,
    art: Painter? = null,
    showTitle: Boolean,
    focused: Boolean,
    size: Dp,
    titleWidth: Dp = size,
    corner: Dp,
    icon: LauncherArt?,
    favorite: Boolean,
    empty: Boolean = false,
    selected: Boolean = false,
    lifted: Boolean = false,
    /** The slot a tile in the hand left. It draws only a faint outline. */
    socket: Boolean = false,
    /** A group a held game is over. It opens up and lights, ready to take it. */
    opened: Boolean = false,
    editing: Boolean = false,
    /** A folder in Folders whose tile is off Home. It rests dimmed, and lights while focused. */
    offHome: Boolean = false,
    onServer: Boolean = false,
    downloading: Boolean = false,
    activity: TileActivity? = null,
    saveHealth: SaveHealth = SaveHealth.Fine,
    library: Boolean = false,
    /** When this tile last took a new look (see [ShellController.settledAt]), or 0. */
    settledAt: Long = 0L,
    onClick: () -> Unit,
    onPickUp: (() -> Unit)? = null,
    onDrag: ((Meaning) -> Unit)? = null,
    onDrop: (() -> Unit)? = null,
) {
    val motion = LocalFoldTheme.current.theme.motion
    val theme = foldTheme()
    val tile = theme.style.tile
    val glyph = themedApp ?: mark?.let { markGlyph(it) }
    val animatorScale = motion.animatorScale(LocalContext.current.contentResolver)
    val checked by motionState(if (selected) 1f else 0f, motion.playful(motion.durationFocus, animatorScale))
    // A collection that just took a new look dips and springs back into it, once. A tile that
    // scrolls back into view later does not replay it.
    val settle = remember { Animatable(1f) }
    LaunchedEffect(settledAt) {
        if (settledAt > 0L && System.currentTimeMillis() - settledAt < SETTLE_WINDOW_MILLIS && !motion.reduced(animatorScale)) {
            settle.snapTo(SETTLE_FROM)
            withContext(SteadyMenu) { settle.animateTo(1f, motion.playful(motion.durationIsland, animatorScale)) }
        }
    }
    val scaleTarget = when {
        opened -> motion.scaleOpen
        socket -> motion.scaleRest
        lifted -> motion.scaleFocus * motion.scaleFocus
        selected -> if (focused) motion.scaleFocus * 1.02f else motion.scaleRest * 1.03f
        focused -> motion.scaleFocus
        else -> motion.scaleRest
    }
    // Settling back eases out too, so a tile shrinks as the ring leaves it, not after.
    val drawn by motionState(target = scaleTarget, spec = motion.arrive(motion.durationFocus, animatorScale))
    // How focused the tile looks, 0 at rest to 1 focused. Read in draw and layer blocks,
    // so the art, glow, and edge ease with the scale without recomposing the tile.
    val unscaledFocus = if (motion.scaleFocus == motion.scaleRest) motionFloat(
        target = if (focused || lifted || opened) 1f else 0f,
        spec = motion.arrive(motion.durationFocus, animatorScale),
    ) else 0f
    val lit = {
        if (motion.scaleFocus == motion.scaleRest) unscaledFocus
        else ((drawn - motion.scaleRest) / (motion.scaleFocus - motion.scaleRest)).coerceIn(0f, 1f)
    }
    val glowStops = remember { FloatArray(GlowFalloff.STOPS.size) }
    val strength by motionState(if (offHome && !focused) tile.offHomeAlpha else 1f, motion.arrive(motion.durationFocus, animatorScale))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.zIndex(if (focused || lifted || opened) 1f else 0f).graphicsLayer { alpha = strength },
    ) {
        val accent = glyph?.accent
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    clip = false
                    scaleX = drawn * settle.value
                    scaleY = drawn * settle.value
                    translationY = -size.toPx() * 0.025f * checked
                }
                .drawWithCache {
                    // The light behind a mark, built when the tile's size or mark changes, not each frame.
                    val markLight = theme.style.glow
                    val light = accent?.takeIf { art == null && icon == null }?.let {
                        Brush.radialGradient(
                            0f to it.copy(alpha = markLight.markLightPeak).compositeOver(theme.surface),
                            markLight.markLightFade to theme.surface,
                            center = Offset(this.size.width * 0.5f, this.size.height * markLight.markLightTop),
                            radius = this.size.width * markLight.markLightReach,
                        )
                    }
                    onDrawBehind {
                        val glow = accent
                        val bounds = this.size
                        val cornerPx = corner.toPx()
                        val focusShown = lit()
                        if (glow != null && !socket) {
                            val half = min(bounds.width, bounds.height) * 0.5f
                            val reach = half + lerp(theme.style.glow.restOverflow, theme.style.glow.focusOverflow, focusShown)
                            val tightness = lerp(theme.style.glow.restTightness, theme.style.glow.focusTightness, focusShown)
                            val rim = lerp(theme.style.glow.restRim, theme.style.glow.focusRim, focusShown)
                            val peak = lerp(theme.style.glow.restPeak, theme.style.glow.focusPeak, focusShown)
                            SoftGlow.fillStops(glowStops, tightness, rim)
                            drawRadialGlow(center, reach, glow, glowStops, peak)
                        }
                        if (socket) {
                            // The space a held tile left: a faint outline, so the eye knows where it was.
                            val strokePx = tile.outlinePx
                            val inset = strokePx / 2f
                            drawRoundRect(
                                color = theme.onBackground.copy(alpha = tile.socketAlpha),
                                topLeft = Offset(inset, inset),
                                size = Size(bounds.width - strokePx, bounds.height - strokePx),
                                cornerRadius = CornerRadius(cornerPx, cornerPx),
                                style = Stroke(width = strokePx),
                            )
                        } else if (!empty) {
                            // Every tile is a filled card, as on Cocoon's grid. A mark's glow is left
                            // as a halo around it. Focus is the grid's sliding ring, the only border it draws.
                            drawRoundRect(color = theme.surface, cornerRadius = CornerRadius(cornerPx, cornerPx))
                            // A mark sits on a soft light in its own colour, kept inside the card.
                            if (light != null) drawRoundRect(brush = light, cornerRadius = CornerRadius(cornerPx, cornerPx))
                            if (editing) {
                                val strokePx = if (focused || lifted || opened) tile.editStrokePx else tile.outlinePx
                                val inset = strokePx / 2f
                                drawRoundRect(
                                    color = theme.focus.copy(alpha = if (focused || lifted || opened) 1f else tile.editRestAlpha),
                                    topLeft = Offset(inset, inset),
                                    size = Size(bounds.width - strokePx, bounds.height - strokePx),
                                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                                    style = Stroke(width = strokePx),
                                )
                            }
                        } else if (lifted) {
                            // A lifted gap is the ring alone, so the space being carried shows where it is.
                            val strokePx = tile.editStrokePx
                            val inset = strokePx / 2f
                            drawRoundRect(
                                color = theme.focus,
                                topLeft = Offset(inset, inset),
                                size = Size(bounds.width - strokePx, bounds.height - strokePx),
                                cornerRadius = CornerRadius(cornerPx, cornerPx),
                                style = Stroke(width = strokePx),
                            )
                        }
                        // Any other empty slot draws nothing, so the grid ends where its tiles do.
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
                                // Letting go puts the tile down where it is, into a group it is over.
                                onDragEnd = { onDrop?.invoke() },
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
            if (socket) return@Box
            if (icon != null) {
                if (icon.fullBleed) {
                    // An adaptive icon fills the tile edge to edge, resting back like a cover.
                    Image(
                        bitmap = icon.image,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = lerp(tile.restAlpha, 1f, lit()) },
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Image(
                        bitmap = icon.image,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(theme.artScale)
                            .graphicsLayer { alpha = lerp(tile.restAlpha, 1f, lit()) },
                        contentScale = ContentScale.Fit,
                    )
                }
            } else if (art != null) {
                // Cover art fills the rounded tile. At rest it sits a little back, so focus reads.
                Image(
                    painter = art,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = lerp(tile.restAlpha, 1f, lit()) },
                    contentScale = ContentScale.Crop,
                )
            } else if (!empty) {
                val glyphAlpha = { lerp(tile.restAlpha, 1f, lit()) }
                if (themedApp == null && glyph != null && CollectionLook.parse(mark) != null) {
                    // A collection being dressed changes as the cursor moves, so each look fades in.
                    Crossfade(mark, animationSpec = motion.arrive(motion.durationFocus, animatorScale)) { shown ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            shown?.let { markGlyph(it) }?.let { MarkIcon(it, theme.artScale, glyphAlpha) }
                        }
                    }
                } else if (glyph != null) {
                    MarkIcon(glyph, theme.artScale, glyphAlpha)
                } else {
                    BasicText(
                        text = monogram(title),
                        modifier = Modifier.scale(theme.artScale).graphicsLayer { alpha = glyphAlpha() },
                        style = text(
                            theme.onBackground,
                            LocalFoldTheme.current.theme.style.type.heroTitle,
                            theme,
                            display = true,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            // A pinned app's dot, on its launcher icon or on the theme's object for it.
            if (favorite && (icon != null || themedApp != null)) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(px(8f))
                        .size(px(tile.dotPx))
                        .background(theme.focus, theme.style.shape.control()),
                )
            }
            val check = presence(true.takeIf { selected }, motion.durationFocus, animatorScale)
            if (check.shown != null) Box(Modifier.align(Alignment.TopStart)
                .padding(maxOf(px(8f), corner * CORNER_SAFE))
                .graphicsLayer { alpha = check.progress.value; scaleX = checked; scaleY = checked }
                .clip(theme.style.shape.control()).background(theme.background)) {
                CheckMark(true, focused, animatorScale)
            }
            // On the server, not on this device. A downloaded game has no badge at all.
            if (onServer || downloading || activity != null || saveHealth.needsAttention) {
                TileStatus(
                    onServer = onServer,
                    focused = focused,
                    downloading = downloading,
                    activity = activity,
                    saveHealth = saveHealth,
                    animatorScale = animatorScale,
                    // Far enough in that the tile's rounded corner never clips it.
                    modifier = Modifier.align(Alignment.BottomEnd).padding(maxOf(px(8f), corner * CORNER_SAFE)),
                )
            }
        }
        if (showTitle) {
            BasicText(
                text = if (socket) "" else title,
                modifier = Modifier
                    .zIndex(1f)
                    .clearAndSetSemantics { }
                    .padding(top = focusOutset(size) + px(12f))
                    // The tile's slot stays its width. A wider title spills evenly either side.
                    .width(size)
                    .requiredWidth(titleWidth),
                style = text(
                    if (focused) theme.onBackground else theme.muted,
                    LocalFoldTheme.current.theme.style.type.gridLabel,
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
    val motion = LocalFoldTheme.current.theme.motion
    val visible = dialog?.takeIf { it.screen == screen }
    // The card leaves with the model: it fades out, takes no touch while it does, and is
    // gone once it is clear. A card kept after its dialog ended once left "GameNative is
    // not installed" on screen after the launch had already been dismissed.
    val card = presence(visible, motion.durationFocus, scale)
    card.shown?.let { shown ->
        Box(Modifier.rise { card.progress.value }) {
            DialogCard(app, shown, if (visible != null) onEffect else { _ -> })
        }
    }
}

@Composable
private fun DialogCard(
    app: FoldcadeApp,
    dialog: DialogState,
    onEffect: (Effect?) -> Unit,
) {
    val design = LocalFoldTheme.current.theme.style
    val theme = foldTheme()
    Box(
        Modifier
            .fillMaxSize()
            .blocksTouch()
            .background(design.colors.scrim.copy(alpha = design.dialog.scrimAlpha))
            .padding(px(design.metrics.dialogInsetPx)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp))
                .background(theme.surface)
                .border(px(design.metrics.dialogBorderPx), theme.onBackground.copy(alpha = design.dialog.borderAlpha), RoundedCornerShape(design.metrics.cardCornerDp.dp))
                .padding(px(design.metrics.dialogInsetPx)),
            verticalArrangement = Arrangement.spacedBy(px(12f)),
        ) {
            BasicText(text = dialog.title, style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.dialogTitle, theme, display = true))
            BasicText(text = dialog.body, style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.dialogBody, theme))
            val detail = dialog.detail
            if (!detail.isNullOrBlank()) {
                BasicText(text = detail, style = text(theme.muted, LocalFoldTheme.current.theme.style.type.dialogBody, theme))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(px(8f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dialog.buttons.forEachIndexed { index, button ->
                    val label = dialogButtonLabel(button, dialog.subject)
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

/**
 * A full-screen layer that takes every touch the page under it would have had. Drawing
 * alone passes taps through: a tap on the dialog's own text once changed the Settings
 * category behind it. Nothing is consumed, so the dialog's buttons still see a whole tap.
 */
internal fun Modifier.blocksTouch(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
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
    GridKind.NoLibrary -> listOf(Copy.addFolder, Copy.addRommServer)
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
    val context = LocalContext.current
    val theme = foldTheme()
    val focus = app.shell.model.focus
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text = title, style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.dialogTitle, theme, display = true))
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
                            .hostPress { (context as? FoldcadeHomeActivity)?.dispatch(app.shell.touchCell(index, screen)) }
                            .padding(horizontal = px(20f), vertical = px(10f)),
                        style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.dialogBody, theme),
                    )
                }
            }
        }
    }
}

/**
 * An open collection with nothing in it: its name, so it is still the collection on the
 * bottom screen alone, what fills it, and Add games, which A, X or a tap presses.
 */
@Composable
private fun EmptyCollection(
    app: FoldcadeApp,
    screen: HostScreen,
    name: String,
) {
    val context = LocalContext.current
    val theme = foldTheme()
    val type = LocalFoldTheme.current.theme.style.type
    Column(
        Modifier.fillMaxSize().padding(horizontal = px(32f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = name,
            style = text(theme.onBackground, type.dialogTitle, theme, display = true).copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            text = Copy.noGamesYet,
            modifier = Modifier.padding(top = px(8f)),
            style = text(theme.onBackground, type.dialogBody, theme).copy(textAlign = TextAlign.Center),
        )
        Row(
            Modifier.padding(top = px(4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(px(8f)),
        ) {
            BasicText(
                text = Copy.addGamesHowBefore,
                style = text(theme.muted, type.dialogBody, theme).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PromptImage(promptGlyph(actionFaces(app.shell.model.faceMap).optionsGlyph), 22.dp)
            BasicText(
                text = Copy.addGamesHowAfter,
                style = text(theme.muted, type.dialogBody, theme).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicText(
            text = Copy.addGames,
            modifier = Modifier
                .padding(top = px(20f))
                .chip(app.shell.model.focus.chrome == null)
                .hostPress { (context as? FoldcadeHomeActivity)?.dispatch(app.shell.touchAddGames(screen)) }
                .padding(horizontal = px(20f), vertical = px(10f)),
            style = text(theme.onBackground, type.dialogBody, theme),
        )
    }
}

@Composable
internal fun DialogAction(label: String, focused: Boolean, onClick: () -> Unit) {
    val motion = LocalFoldTheme.current.theme.motion
    val theme = foldTheme()
    val animatorScale = motion.animatorScale(LocalContext.current.contentResolver)
    val drawn by motionState(
        target = if (focused) motion.scaleFocus else motion.scaleRest,
        spec = if (focused) {
            motion.arrive(motion.durationFocus, animatorScale)
        } else {
            motion.leave(motion.durationFocus, animatorScale)
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
            // The margin around the chip is part of the button, so a tap just off it
            // presses it rather than landing on the dimmer and doing nothing.
            .hostPress(onClick)
            .padding(horizontal = px(16f), vertical = px(18f))
            .chip(focused)
            .padding(horizontal = px(22f), vertical = px(12f)),
        style = text(
            if (focused) theme.onBackground else theme.muted,
            LocalFoldTheme.current.theme.style.type.dialogBody,
            theme,
        ).copy(textAlign = TextAlign.Center),
    )
}

@Composable
private fun ConnectScreen(
    app: FoldcadeApp,
    screen: HostScreen,
    onEffect: (Effect?) -> Unit,
    takeKeyboardFocus: suspend () -> Unit,
) {
    val design = LocalFoldTheme.current.theme.style
    val theme = foldTheme()
    val model = app.shell.model
    val artKey = model.connectKind == ConnectKind.ArtKey
    val fields = connectFields(model.connectKind, model.connectRetry)
    val ringed = fields[model.connectIndex.coerceIn(fields.indices)]
    val origin = remember { FocusRequester() }
    val token = remember { FocusRequester() }
    // Leaving a field parks focus on the form. Cleared focus would land on the first field again.
    val form = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var typingIn by remember { mutableStateOf<ConnectField?>(null) }
    // Presses from an earlier visit to the form don't count.
    val opened = remember { app.shell.typingRequest }
    // A on a text row: the cursor goes into that field and the keyboard comes up.
    // A again brings the keyboard back after it was put away.
    LaunchedEffect(app.shell.typingRequest) {
        if (app.shell.typingRequest == opened) return@LaunchedEffect
        val field = when (ringed) {
            ConnectField.Origin -> origin
            ConnectField.Token -> token
            ConnectField.Save, ConnectField.Retry -> return@LaunchedEffect
        }
        takeKeyboardFocus()
        field.requestFocus()
        keyboard?.show()
    }
    // The D-pad moved the ring off the field being typed in, so typing stops there.
    LaunchedEffect(ringed) {
        if (typingIn != null && typingIn != ringed) form.requestFocus()
    }
    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(form)
            .focusable()
            .background(theme.background)
            .padding(px(design.metrics.dialogInsetPx)),
        verticalArrangement = Arrangement.spacedBy(px(16f)),
    ) {
        BasicText(
            text = when {
                artKey -> Copy.steamGridDb
                model.connectReturnPanel != null -> Copy.editRommConnection
                app.store.rommOrigin() != null -> Copy.signInAgainTitle
                else -> Copy.addRommServer
            },
            style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.dialogTitle, theme, display = true),
        )
        if (!artKey) {
            BasicText(text = "Server", style = text(theme.muted, LocalFoldTheme.current.theme.style.type.hint, theme))
            BasicTextField(
                value = model.connectOrigin,
                onValueChange = { app.shell.editOrigin(it) },
                singleLine = true,
                textStyle = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.dialogBody, theme),
                cursorBrush = cursorBrush(theme),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(origin)
                    .chip(focused(model, ConnectField.Origin))
                    .onFocusChanged { state ->
                        typingIn = typing(typingIn, ConnectField.Origin, state.isFocused)
                        if (state.isFocused) app.shell.touchConnect(fields.indexOf(ConnectField.Origin), screen)
                    }
                    .padding(horizontal = px(20f), vertical = px(10f)),
            )
            val warning = model.connectWarning
            if (warning != null) {
                BasicText(text = warning, style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.dialogBody, theme))
            }
        }
        val hint = model.connectHint
        if (hint != null) {
            BasicText(text = hint, style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.dialogBody, theme))
        }
        BasicText(text = if (artKey) Copy.apiKey else Copy.clientApiToken, style = text(theme.muted, LocalFoldTheme.current.theme.style.type.hint, theme))
        BasicTextField(
            value = app.shell.connectToken,
            onValueChange = { app.shell.editToken(it) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.dialogBody, theme),
            cursorBrush = cursorBrush(theme),
            // A password field to the keyboard too, so it neither shows nor learns the token.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            // Done saves, as Save below does, rather than only closing the keyboard.
            keyboardActions = KeyboardActions(onDone = {
                if (!model.connectBusy) onEffect(app.shell.touchConnect(fields.indexOf(ConnectField.Save), screen))
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(token)
                .chip(focused(model, ConnectField.Token))
                .onFocusChanged { state ->
                    typingIn = typing(typingIn, ConnectField.Token, state.isFocused)
                    if (state.isFocused) app.shell.touchConnect(fields.indexOf(ConnectField.Token), screen)
                }
                .padding(horizontal = px(20f), vertical = px(10f)),
        )
        BasicText(
            text = when {
                model.connectBusy -> if (artKey) "Checking key…" else "Checking token…"
                artKey -> Copy.saveKey
                else -> Copy.saveToken
            },
            modifier = Modifier
                .chip(focused(model, ConnectField.Save))
                .hostPress { if (!model.connectBusy) onEffect(app.shell.touchConnect(fields.indexOf(ConnectField.Save), screen)) }
                .padding(horizontal = px(20f), vertical = px(10f)),
            style = text(theme.onBackground, LocalFoldTheme.current.theme.style.type.dialogBody, theme),
        )
        if (!artKey && model.connectRetry) {
            BasicText(
                text = Copy.rommNewCode,
                modifier = Modifier
                    .chip(focused(model, ConnectField.Retry))
                    .hostPress { onEffect(app.shell.touchConnect(fields.indexOf(ConnectField.Retry), screen)) }
                    .padding(horizontal = px(20f), vertical = px(10f)),
                style = text(theme.onBackground, design.type.dialogBody, theme),
            )
        }
        BasicText(
            text = if (artKey) Copy.artKeyHelp else Copy.rommTokenHelp,
            style = text(theme.muted, LocalFoldTheme.current.theme.style.type.dialogBody, theme),
        )
    }
}

/** The field taking text after [field] gains or loses focus. */
private fun typing(current: ConnectField?, field: ConnectField, focused: Boolean): ConnectField? = when {
    focused -> field
    current == field -> null
    else -> current
}

private fun focused(model: app.foldcade.language.PickerModel, field: ConnectField): Boolean {
    val fields = connectFields(model.connectKind, model.connectRetry)
    return fields[model.connectIndex.coerceIn(fields.indices)] == field
}

@Composable
internal fun Modifier.hostPress(onClick: () -> Unit): Modifier {
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
    /** Titles, the clock and the typed name wear the pack's display face. */
    display: Boolean = false,
): TextStyle = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = if (display) theme.displayFont else theme.font,
    fontWeight = theme.style.type.bodyWeight,
    textAlign = TextAlign.Start,
)

@Composable
private fun focusOutset(cell: Dp): Dp =
    (cell * ((LocalFoldTheme.current.theme.motion.scaleFocus - 1f) / 2f) +
        px(LocalFoldTheme.current.theme.style.metrics.focusStrokePx * LocalFoldTheme.current.theme.motion.scaleFocus)).coerceAtLeast(0.dp)

@Composable
private fun Modifier.rowHighlight(focused: Boolean): Modifier {
    val theme = foldTheme()
    val lift = theme.onBackground
    val corner = theme.style.shape.rowCornerPx
    return this.graphicsLayer { clip = false }.drawBehind {
        if (!focused) return@drawBehind
        drawRoundRect(
            color = lift.copy(alpha = theme.style.dialog.rowAlpha),
            cornerRadius = CornerRadius(corner, corner),
        )
        with(FocusRing(theme)) { drawFocusRing(corner) }
    }
}

/**
 * A chip: a faint filled pill, and the same focus ring as tiles and menu rows
 * while it has focus. Tabs, actions, and fields all use it.
 */
@Composable
private fun Modifier.chip(focused: Boolean): Modifier {
    val theme = foldTheme()
    val lift = theme.onBackground
    val dialog = theme.style.dialog
    val most = theme.style.shape.chipCornerPx
    val round = theme.style.shape.round
    return this.graphicsLayer { clip = false }.drawBehind {
        val corner = min(size.height * round, most)
        drawRoundRect(
            color = lift.copy(alpha = if (focused) dialog.chipFocusAlpha else dialog.chipAlpha),
            cornerRadius = CornerRadius(corner, corner),
        )
        if (focused) with(FocusRing(theme)) { drawFocusRing(corner) }
    }
}


/** The trigger's letter is brief enough to read without covering the hero for long. */
@Composable
private fun LetterOverlay(app: FoldcadeApp, scale: Float) {
    val model = app.shell.model
    val theme = LocalFoldTheme.current.theme
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(model.letterJumpEpoch) {
        if (model.letterJumpEpoch == 0) return@LaunchedEffect
        alpha.animateTo(1f, theme.motion.arrive(theme.motion.durationLetterFade, scale))
        delay(theme.motion.durationLetterHold)
        alpha.animateTo(0f, theme.motion.leave(theme.motion.durationLetterFade, scale))
    }
    val letter = model.letterJump ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(
            text = letter,
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value }
                .clip(RoundedCornerShape(theme.style.metrics.cardCornerDp.dp * 2))
                .background(theme.surface.copy(alpha = theme.style.hero.pillAlpha))
                .padding(horizontal = px(128f), vertical = px(64f)),
            style = text(theme.onBackground, theme.style.type.heroTitle * 4, theme, display = true)
                .copy(fontWeight = theme.style.type.strongWeight, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun recoveryRowFeedback(model: app.foldcade.language.PickerModel, row: app.foldcade.language.Row, scale: Float): Modifier {
    val theme = foldTheme()
    val motion = theme.motion
    if (row != LanguageRow.RemoveDownload && row != LanguageRow.DeleteShortcut) return Modifier
    val shake = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(model.deleteShake) {
        if (model.deleteShake == 0) return@LaunchedEffect
        shake.snapTo(-theme.style.motion.shakePx)
        shake.animateTo(theme.style.motion.shakePx, motion.playful(motion.durationShort, scale))
        shake.animateTo(0f, motion.playful(motion.durationShort, scale))
    }
    return Modifier.graphicsLayer { translationX = shake.value }.drawBehind {
        if (model.deleteHold?.row == row) drawRoundRect(theme.focus.copy(alpha = theme.style.menu.holdAlpha),
            size = androidx.compose.ui.geometry.Size(size.width * motion.holdFill(model.deleteProgress), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(theme.style.shape.highlightCornerDp.dp.toPx()))
    }
}

@Composable
private fun selectionFeedback(refusals: Int): Modifier {
    val motion = foldTheme().motion
    val shakePx = foldTheme().style.motion.shakePx
    val scale = motion.animatorScale(LocalContext.current.contentResolver)
    val shake = remember { Animatable(0f) }
    LaunchedEffect(refusals) {
        if (refusals == 0) return@LaunchedEffect
        withContext(SteadyMenu) {
            shake.snapTo(-shakePx)
            shake.animateTo(shakePx, motion.playful(motion.durationShort, scale))
            shake.animateTo(0f, motion.playful(motion.durationShort, scale))
        }
    }
    return Modifier.graphicsLayer { translationX = shake.value }
}
