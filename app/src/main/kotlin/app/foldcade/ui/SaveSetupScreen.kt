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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.R
import app.foldcade.language.Effect
import app.foldcade.language.HintKey
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.PromptKey
import app.foldcade.language.SaveSetupCopy
import app.foldcade.language.SaveSetupPage
import app.foldcade.language.SetupStatus
import app.foldcade.language.SetupStep
import app.foldcade.language.SetupTile
import app.foldcade.language.Theme
import app.foldcade.language.actionFaces
import app.foldcade.language.promptKeyOf
import app.foldcade.language.saveSetupActions
import app.foldcade.language.saveSetupAllSet
import app.foldcade.language.saveSetupTiles
import app.foldcade.language.setupStatus
import kotlin.math.floor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Set up saves across both screens: the top shows the focused emulator large with its one step
 * and the buttons for it; the bottom is the strip of tiles under the page's head. [page] may be
 * the page as it was while it fades away; the tiles are always read from the model as it is.
 */
@Composable
internal fun SaveSetupTop(app: FoldcadeApp, page: SaveSetupPage, scale: Float, dispatch: (Effect?) -> Unit) {
    val theme = LocalFoldTheme.current.theme
    Box(Modifier.fillMaxSize().background(theme.background).padding(horizontal = TOP_EDGE_H, vertical = TOP_EDGE_V)) {
        Showcase(app, page, HostScreen.Top, compact = false, scale = scale, dispatch = dispatch, modifier = Modifier.fillMaxSize())
    }
}

@Composable
internal fun SaveSetupBottom(app: FoldcadeApp, page: SaveSetupPage, scale: Float, dispatch: (Effect?) -> Unit) {
    val theme = LocalFoldTheme.current.theme
    AdvanceAfterCheck(app, scale)
    Column(Modifier.fillMaxSize().background(theme.background).padding(horizontal = EDGE_H, vertical = EDGE_V)) {
        Head(app, page, HostScreen.Bottom, scale, dispatch)
        Spacer(Modifier.weight(1f))
        Strip(app, page, TILE_ART, scale)
        Spacer(Modifier.weight(1f))
    }
}

/** A game holds the other screen: the showcase over the strip, on the one screen there is. */
@Composable
internal fun SaveSetupSingle(app: FoldcadeApp, page: SaveSetupPage, screen: HostScreen, scale: Float, dispatch: (Effect?) -> Unit) {
    val theme = LocalFoldTheme.current.theme
    AdvanceAfterCheck(app, scale)
    Column(
        Modifier.fillMaxSize().background(theme.background).padding(horizontal = EDGE_H, vertical = EDGE_V),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Head(app, page, screen, scale, dispatch)
        Showcase(app, page, screen, compact = true, scale = scale, dispatch = dispatch, modifier = Modifier.weight(1f).fillMaxWidth())
        Strip(app, page, COMPACT_ART, scale)
    }
}

/**
 * A tile that has just started syncing keeps the focus while its check lands, then the focus
 * glides on to the next that needs you. Drawn by the screen with the strip, so it runs once.
 */
@Composable
private fun AdvanceAfterCheck(app: FoldcadeApp, scale: Float) {
    val motion = LocalFoldTheme.current.theme.motion
    val celebrating = app.shell.model.saveSetup?.celebrating
    val checking = app.shell.model.checkingSaves
    LaunchedEffect(celebrating, checking) {
        val key = celebrating ?: return@LaunchedEffect
        if (checking) return@LaunchedEffect
        delay((motion.durationSetupPause * scale).toLong())
        app.shell.advanceSaveSetup(key)
    }
}

/** The page's name with the Back glyph, and how many tiles still need you. */
@Composable
private fun Head(app: FoldcadeApp, page: SaveSetupPage, screen: HostScreen, scale: Float, dispatch: (Effect?) -> Unit) {
    val theme = LocalFoldTheme.current.theme
    val model = app.shell.model
    val back = promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB
    val tiles = saveSetupTiles(model, page)
    val left = tiles.count { !it.findSaves && setupStatus(it, page) == SetupStatus.NeedsYou }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .clip(theme.style.shape.control())
                .background(theme.surface)
                .hostPress { dispatch(app.shell.touchSaveSetup(Meaning.Back, screen)) }
                .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PromptImage(promptGlyph(back), 32.dp)
            BasicText(SaveSetupCopy.title, style = setupStyle(theme.onBackground, theme.style.type.heroTitle, theme, bold = true, display = true), maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (tiles.isNotEmpty()) {
            val word = if (left == 0) SaveSetupCopy.allSet else SaveSetupCopy.toGo(left)
            SlideSwap(target = word, scale = scale, distance = 12.dp, vertical = true, direction = { _, _ -> 1 }) { shown ->
                Chip(shown, if (left == 0) StatusLook.Syncing else StatusLook.NeedsYou, theme)
            }
        }
    }
}

/**
 * The tiles side by side under one gliding highlight. The strip never pages: it slides only as
 * far as the focus needs, as the home grid does, and sits centred while every tile fits.
 */
@Composable
private fun Strip(app: FoldcadeApp, page: SaveSetupPage, art: Dp, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val model = app.shell.model
    val tiles = saveSetupTiles(model, page)
    val focus = tiles.indexOfFirst { it.key == page.focus }
    val delays = flipDelays(tiles, (theme.motion.durationSetupStagger * scale).toLong())
    BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds()) {
        val reach = ringReach()
        val column = art * TILE_WIDTH
        val pitch = column + TILE_GAP
        val fits = floor(((maxWidth - reach * 2 + TILE_GAP) / pitch).toDouble()).toInt().coerceAtLeast(1)
        // A plain holder: the first tile shown follows the focus, which already recomposes the strip.
        val first = remember { intArrayOf(0) }
        if (focus >= 0) {
            if (focus < first[0]) first[0] = focus
            if (focus >= first[0] + fits) first[0] = focus - fits + 1
        }
        first[0] = first[0].coerceIn(0, (tiles.size - fits).coerceAtLeast(0))
        val pitchPx = with(LocalDensity.current) { pitch.toPx() }
        val slide = eased(first[0] * pitchPx, theme.motion.durationTravel, scale)
        val slots = rememberSelectionSlots()
        Box(Modifier.fillMaxWidth(), contentAlignment = if (tiles.size <= fits) Alignment.Center else Alignment.CenterStart) {
            Row(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = -slide.value }
                    .padding(reach)
                    .selectionHighlight(slots, focus.takeIf { it >= 0 }, ring = true, scale = scale),
                horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
            ) {
                tiles.forEachIndexed { index, tile ->
                    StripTile(
                        tile = tile,
                        status = setupStatus(tile, page),
                        focused = index == focus,
                        flipDelay = delays[tile.key] ?: 0L,
                        art = art,
                        scale = scale,
                        artModifier = Modifier.selectionSlot(slots, index, depth = 2),
                        modifier = Modifier.width(column).hostPress { app.shell.touchSaveSetupTile(tile.key) },
                    )
                }
            }
        }
    }
}

/**
 * How long each tile that has just started syncing waits before its badge flips, so checks that
 * land together, as after all-files access finds several folders, flip one after another.
 */
@Composable
private fun flipDelays(tiles: List<SetupTile>, stagger: Long): Map<String, Long> {
    val done = tiles.filter { it.done }.map { it.key }.toSet()
    // Plain holders: read and written only in the pass that draws the strip.
    val seen = remember { arrayOf(done) }
    val delays = remember { mutableMapOf<String, Long>() }
    if (done != seen[0]) {
        tiles.filter { it.done && it.key !in seen[0] }.forEachIndexed { rank, tile -> delays[tile.key] = rank * stagger }
        seen[0] = done
    }
    return delays
}

/** One tile: its lit object on a soft square, its badge, and its name. A skipped one rests dimmer; its badge does not. */
@Composable
private fun StripTile(
    tile: SetupTile,
    status: SetupStatus,
    focused: Boolean,
    flipDelay: Long,
    art: Dp,
    scale: Float,
    artModifier: Modifier,
    modifier: Modifier,
) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val lit = eased(if (focused) 1f else 0f, theme.motion.durationFocus, scale)
    val lift = bouncy(if (focused) 1f else 0f, scale)
    val dim = eased(if (status == SetupStatus.Skipped) design.setup.skippedAlpha else 1f, theme.motion.durationFocus, scale)
    Column(
        modifier.semantics { contentDescription = tile.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TILE_LABEL_GAP),
    ) {
        Box(artModifier.size(art), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(design.shape.highlightCornerDp.dp)).background(theme.onBackground.copy(alpha = design.menu.quickTileAlpha)))
            Box(
                Modifier.fillMaxSize().lifted(lit, lift, grow = design.settings.artGrow, rest = design.settings.artRestAlpha).graphicsLayer { alpha = dim.value },
                contentAlignment = Alignment.Center,
            ) { SetupArt(tile, ART_FILL) }
            FlipBadge(tile, status, flipDelay, scale, art * BADGE_SHARE, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
        }
        BasicText(
            tile.label,
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                alpha = dim.value * design.settings.labelRestAlpha.let { it + (1f - it) * lit.value }
            },
            style = setupStyle(theme.onBackground, design.type.hint, theme, bold = true).copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The tile's lit object: the emulator's own, else its launcher icon, and the folder for Find saves. */
@Composable
private fun SetupArt(tile: SetupTile, fill: Float) {
    val theme = LocalFoldTheme.current.theme
    if (tile.findSaves) {
        markGlyph("folder")?.let { MarkIcon(it, fill) }
        return
    }
    val themed = appGlyph(tile.packageName)
    if (themed != null) {
        MarkIcon(themed, fill)
        return
    }
    val icon = launcherIcon(tile.packageName)
    if (icon != null) LauncherImage(icon, contentDescription = null, modifier = Modifier.fillMaxSize(fill))
    else SymbolIcon("emulators", fill * SYMBOL_FILL, theme.onBackground)
}

/** What a status wears: the check in focus once it syncs, the not-syncing cloud in amber while it doesn't. */
private enum class StatusLook { Syncing, NeedsYou, Quiet }

private fun statusLook(tile: SetupTile, status: SetupStatus): StatusLook = when {
    status == SetupStatus.Syncing -> StatusLook.Syncing
    // Find saves is a shortcut, not a save that fails to sync, so it never wears the amber mark.
    tile.findSaves -> StatusLook.Quiet
    else -> StatusLook.NeedsYou
}

private fun badgeOf(look: StatusLook, theme: Theme): Pair<Int, Color>? = when (look) {
    StatusLook.Syncing -> R.drawable.ic_badge_check to theme.focus
    StatusLook.NeedsYou -> R.drawable.ic_badge_unsynced to theme.style.colors.attention
    StatusLook.Quiet -> null
}

/** The corner badge. Once the tile starts syncing it turns over like a coin and lands on the check. */
@Composable
private fun FlipBadge(tile: SetupTile, status: SetupStatus, flipDelay: Long, scale: Float, size: Dp, modifier: Modifier) {
    val theme = LocalFoldTheme.current.theme
    val motion = theme.motion
    val look = statusLook(tile, status)
    val shown = remember { arrayOf(look) }
    val turn = remember { Animatable(1f) }
    var face by remember { mutableIntStateOf(look.ordinal) }
    LaunchedEffect(look) {
        if (shown[0] == look) return@LaunchedEffect
        val toCheck = look == StatusLook.Syncing
        shown[0] = look
        withContext(SteadyMenu) {
            if (toCheck) delay(flipDelay)
            turn.animateTo(0f, motion.leave(motion.durationFocus, scale))
            face = look.ordinal
            turn.animateTo(1f, motion.playful(motion.durationSelect, scale))
        }
    }
    val (icon, tint) = badgeOf(StatusLook.entries[face], theme) ?: return
    val badge = themeIcon(icon)
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = turn.value
                scaleY = 0.7f + 0.3f * turn.value
            }
            .clip(theme.style.shape.control())
            .background(theme.background.copy(alpha = theme.style.tile.statusBackingAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painter = badge.painter, contentDescription = null, colorFilter = badge.tint(tint), modifier = Modifier.fillMaxSize(BADGE_INK))
    }
}

/** What the top screen shows, and where it sits on the strip, so it slides the way the focus went. */
private data class Subject(val key: String?, val order: Int)

@Composable
private fun Showcase(
    app: FoldcadeApp,
    page: SaveSetupPage,
    screen: HostScreen,
    compact: Boolean,
    scale: Float,
    dispatch: (Effect?) -> Unit,
    modifier: Modifier,
) {
    val model = app.shell.model
    val tiles = saveSetupTiles(model, page)
    val index = tiles.indexOfFirst { it.key == page.focus }
    Box(modifier) {
        SlideSwap(
            target = Subject(page.focus.takeIf { index >= 0 }, if (index >= 0) index else tiles.size),
            scale = scale,
            distance = 40.dp,
            vertical = false,
            direction = { from, to -> to.order.compareTo(from.order) },
            key = { it.key },
        ) { subject ->
            val tile = tiles.firstOrNull { it.key == subject.key }
            when {
                tile != null -> TileShowcase(app, page, tile, screen, compact, scale, dispatch)
                saveSetupAllSet(tiles, page) -> AllSet(app, page, tiles, screen, compact, scale, dispatch)
                // The emulators are still loading, as when the page comes back with a new process.
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

/** The focused tile large: its art and name, how it is doing, its one step, step dots, and the buttons. */
@Composable
private fun TileShowcase(
    app: FoldcadeApp,
    page: SaveSetupPage,
    tile: SetupTile,
    screen: HostScreen,
    compact: Boolean,
    scale: Float,
    dispatch: (Effect?) -> Unit,
) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val status = setupStatus(tile, page)
    val celebrating = page.celebrating == tile.key
    val pop = bouncy(if (celebrating) 1f else 0f, scale)
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else TOP_GAP)) {
        Box(
            Modifier
                .fillMaxHeight(if (compact) COMPACT_ART_SHARE else design.setup.artShare)
                .aspectRatio(1f)
                .graphicsLayer {
                    val grow = 1f + design.settings.artGrow * pop.value
                    scaleX = grow
                    scaleY = grow
                },
            contentAlignment = Alignment.Center,
        ) {
            SetupArt(tile, 1f)
            FlipBadge(tile, status, 0L, scale, if (compact) 36.dp else 56.dp, Modifier.align(Alignment.TopEnd))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            BasicText(
                tile.label,
                style = setupStyle(theme.onBackground, design.type.heroTitle, theme, bold = true, display = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SlideSwap(target = SaveSetupCopy.status(tile, page), scale = scale, distance = 12.dp, vertical = true, direction = { _, _ -> 1 }) { word ->
                Chip(word, statusLook(tile, status), theme)
            }
            // A new step rises in under the last, as the check for the one before lands.
            SlideSwap(target = tile.step, scale = scale, distance = 24.dp, vertical = true, direction = { _, _ -> 1 }, key = { it?.let(::stepKey) }) { step ->
                StepWords(tile, step, page, compact, scale)
            }
            if (tile.steps.size > 1) StepDots(tile, scale)
            Actions(app, page, screen, scale, dispatch)
        }
    }
}

/** A step's identity, so the words slide only when the step itself changes, not when its how-to is read again. */
private fun stepKey(step: SetupStep): String = when (step) {
    is SetupStep.Folder -> "folder:${step.playerId}"
    is SetupStep.EdenFolder -> "eden"
    is SetupStep.Cores -> "cores"
    is SetupStep.InApp -> "app:${step.playerId}:${step.kind}"
    is SetupStep.AllFiles -> "files"
}

@Composable
private fun StepWords(tile: SetupTile, step: SetupStep?, page: SaveSetupPage, compact: Boolean, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(
            SaveSetupCopy.stepTitle(step),
            style = setupStyle(theme.onBackground, design.type.dialogTitle, theme, bold = true, display = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The words of the step on its way out stay its own, not the next one's.
        SaveSetupCopy.stepBody(tile.copy(steps = listOfNotNull(step)))?.let { body ->
            BasicText(
                body,
                style = setupStyle(theme.muted, design.type.dialogBody, theme),
                maxLines = if (compact) COMPACT_BODY_LINES else BODY_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val note = page.note?.takeIf { it.key == tile.key }?.text
        val shown = presence(note, theme.motion.durationFocus, scale)
        shown.shown?.let { text ->
            BasicText(
                text,
                modifier = Modifier.rise { shown.progress.value },
                style = setupStyle(design.colors.attention, design.type.dialogBody, theme, bold = true),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One dot a step: lit once done, ringed for the one to do now. */
@Composable
private fun StepDots(tile: SetupTile, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val current = tile.steps.indexOfFirst { !it.done }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        tile.steps.forEachIndexed { index, step ->
            val done = eased(if (step.done) 1f else 0f, theme.motion.durationFocus, scale)
            val now = bouncy(if (index == current) 1f else 0f, scale)
            Box(
                Modifier
                    .size(DOT)
                    .graphicsLayer {
                        val grow = 1f + 0.4f * now.value
                        scaleX = grow
                        scaleY = grow
                    }
                    .clip(theme.style.shape.control())
                    .background(androidx.compose.ui.graphics.lerp(theme.muted.copy(alpha = theme.style.settings.labelRestAlpha), theme.focus, done.value)),
            )
        }
    }
}

/** The step's verb with the user's confirm key, then Skip with the bottom key. A tap on either does what its key does. */
@Composable
private fun Actions(app: FoldcadeApp, page: SaveSetupPage, screen: HostScreen, scale: Float, dispatch: (Effect?) -> Unit) {
    val model = app.shell.model
    val skipKey = actionFaces(model.faceMap).bottomGlyph
    val hints = saveSetupActions(model, page)
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        hints.forEachIndexed { index, hint ->
            val meaning = if (hint.key == skipKey && index > 0) Meaning.ActivateBottom else Meaning.Activate
            ActionChip(model, hint, primary = index == 0, scale = scale) { dispatch(app.shell.touchSaveSetup(meaning, screen)) }
        }
    }
}

@Composable
private fun ActionChip(model: app.foldcade.language.PickerModel, hint: HintKey, primary: Boolean, scale: Float, onClick: () -> Unit) {
    val theme = LocalFoldTheme.current.theme
    val glyph = promptGlyph(hint.key)
    val held = eased(if (hint.key in model.held) 1f else 0f, theme.motion.durationShort, scale)
    Row(
        Modifier
            .graphicsLayer {
                val press = 1f - theme.style.motion.keySquash * held.value
                scaleX = press
                scaleY = press
            }
            .clip(theme.style.shape.control())
            .then(if (primary) Modifier.background(theme.surface) else Modifier)
            .hostPress(onClick)
            .padding(start = 10.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PromptImage(if (hint.key in model.held) "${glyph}_filled" else glyph, 28.dp)
        BasicText(
            hint.label,
            style = setupStyle(if (primary) theme.onBackground else theme.muted, theme.style.type.menuRow, theme, bold = primary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A word on a soft pill, led by the status badge: Syncing, Needs you, Skipped, 3 to go. */
@Composable
private fun Chip(word: String, look: StatusLook, theme: Theme) {
    val design = theme.style
    val attention = look == StatusLook.NeedsYou
    Row(
        Modifier
            .clip(design.shape.control())
            .background(if (attention) design.colors.attention.copy(alpha = design.dialog.chipStrongAlpha) else theme.surface)
            .padding(start = 10.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        badgeOf(look, theme)?.let { (icon, tint) ->
            val badge = themeIcon(icon)
            Image(badge.painter, contentDescription = null, colorFilter = badge.tint(tint), modifier = Modifier.size(22.dp))
        }
        BasicText(word, style = setupStyle(if (attention) design.colors.attention else theme.onBackground, design.type.hint, theme, bold = true), maxLines = 1)
    }
}

/** Nothing needs you: a large check, what was skipped, and Done. */
@Composable
private fun AllSet(
    app: FoldcadeApp,
    page: SaveSetupPage,
    tiles: List<SetupTile>,
    screen: HostScreen,
    compact: Boolean,
    scale: Float,
    dispatch: (Effect?) -> Unit,
) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val landed = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        withContext(SteadyMenu) { launch { landed.animateTo(1f, theme.motion.playful(theme.motion.durationSelect, scale)) } }
    }
    val skipped = tiles.count { setupStatus(it, page) == SetupStatus.Skipped && !it.findSaves }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else TOP_GAP)) {
        Box(
            Modifier
                .fillMaxHeight((if (compact) COMPACT_ART_SHARE else design.setup.artShare) * ALL_SET_SHARE)
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = landed.value
                    scaleY = landed.value
                }
                .clip(design.shape.control())
                .background(theme.background.copy(alpha = design.tile.statusBackingAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            val check = themeIcon(R.drawable.ic_badge_check)
            Image(check.painter, contentDescription = null, colorFilter = check.tint(theme.focus), modifier = Modifier.fillMaxSize(BADGE_INK))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(SaveSetupCopy.allSet, style = setupStyle(theme.onBackground, design.type.heroTitle, theme, bold = true, display = true), maxLines = 1)
            SaveSetupCopy.allSetBody(skipped)?.let {
                BasicText(it, style = setupStyle(theme.muted, design.type.dialogBody, theme), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Actions(app, page, screen, scale, dispatch)
        }
    }
}

private fun setupStyle(color: Color, size: TextUnit, theme: Theme, bold: Boolean = false, display: Boolean = false): TextStyle = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = if (display) theme.displayFont else theme.font,
    fontWeight = if (bold) theme.style.type.strongWeight else theme.style.type.bodyWeight,
)

/**
 * The bottom screen is about 538 × 468dp: four tiles of 92dp fit across it with their names,
 * and the strip slides for a fifth. One screen beside a game keeps the strip short so the step
 * above it still reads.
 */
private val EDGE_H = 24.dp
private val EDGE_V = 16.dp
private val TILE_ART = 92.dp
private val COMPACT_ART = 60.dp
private val TILE_GAP = 8.dp
private val TILE_LABEL_GAP = 4.dp

/** A tile's column is wider than its art, so a name like DuckStation fits under it. */
private const val TILE_WIDTH = 1.22f
private const val BADGE_SHARE = 0.3f
private const val BADGE_INK = 0.74f
private const val ART_FILL = 0.84f
private const val SYMBOL_FILL = 0.62f
private val DOT = 10.dp

/** The top screen, about 833 × 468dp, keeps the Saves page's margins. Nothing on it scrolls. */
private val TOP_EDGE_H = 40.dp
private val TOP_EDGE_V = 24.dp
private val TOP_GAP = 32.dp
private const val BODY_LINES = 6
private const val COMPACT_BODY_LINES = 3
private const val COMPACT_ART_SHARE = 0.62f
private const val ALL_SET_SHARE = 0.72f
