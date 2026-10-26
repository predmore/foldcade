package app.foldcade.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.R
import app.foldcade.RunningApp
import app.foldcade.language.Copy
import app.foldcade.language.DateStyle
import app.foldcade.language.Effect
import app.foldcade.language.HostScreen
import app.foldcade.language.MARK_CONSOLE
import app.foldcade.language.Meaning
import app.foldcade.language.PromptKey
import app.foldcade.language.SaveDestination
import app.foldcade.language.SaveHealth
import app.foldcade.language.SavePickerChoice
import app.foldcade.language.SavePickerCopy
import app.foldcade.language.SavePickerSave
import app.foldcade.language.SavePickerState
import app.foldcade.language.Theme
import app.foldcade.language.actionFaces
import app.foldcade.language.formatSaveSize
import app.foldcade.language.formatSaveWhen
import app.foldcade.language.needsAttention
import app.foldcade.language.promptKeyOf
import app.foldcade.language.relativeSaveWhen
import app.foldcade.language.savePickerHints

/**
 * The top screen shows what the bottom has focused, large, over the game's own hero art:
 * the focused save's picture, slot, date, maker and size, or, on the status chip and
 * while there is no save to show, the game and how its saves are doing.
 */
@Composable
internal fun SavePickerTop(app: FoldcadeApp, state: SavePickerState, dates: DateStyle, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val art = app.shell.savePickerArt(state.target)
    Box(Modifier.fillMaxSize().background(theme.background)) {
        SceneBackdrop(app, art?.record, art?.query, app.shell.model.motionSpeed, scale)
        Box(Modifier.fillMaxSize().background(design.colors.scrim.copy(alpha = design.saves.backdropDim)))
        val save = state.focused
        val title = eased(if (save != null) 1f else 0f, theme.motion.durationFocus, scale)
        Column(Modifier.fillMaxSize().padding(horizontal = TOP_EDGE_H, vertical = TOP_EDGE_V)) {
            // Over a save, the game's name steps up to the corner; the overview says it large.
            BasicText(
                state.gameLabel,
                modifier = Modifier.graphicsLayer { alpha = title.value },
                style = saveStyle(theme.onBackground, design.type.heroMeta, theme, bold = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A new save rises in from the way the cursor went, as Settings' cards do.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SlideSwap(
                    target = TopSubject(save, if (save == null) 0 else state.index),
                    scale = scale,
                    distance = 28.dp,
                    vertical = true,
                    direction = { from, to -> to.order.compareTo(from.order) },
                    key = { it.save?.id },
                ) { subject ->
                    BoxWithConstraints(Modifier.fillMaxSize().padding(top = 12.dp)) {
                        // The art takes its own shape, up to its share of the width; the words take the rest.
                        val artMax = maxWidth * design.saves.screenshotShare
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TOP_GAP)) {
                            TopArt(app, art, subject.save?.screenshotUri, Modifier.fillMaxHeight().widthIn(max = artMax))
                            val shown = subject.save
                            if (shown != null) SaveFacts(shown, dates, theme, Modifier.weight(1f))
                            else Overview(state, theme, scale, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** What the top screen is showing, and where it sits in the list, so it can slide the way focus went. */
private data class TopSubject(val save: SavePickerSave?, val order: Int)

/** The save's screenshot, else the game's cover, else its platform's object. Never an empty box. */
@Composable
private fun TopArt(app: FoldcadeApp, art: RunningApp?, screenshot: String?, modifier: Modifier) {
    val theme = LocalFoldTheme.current.theme
    val cover = rememberArt(app, art?.record, art?.query)?.cover
    val picture = (screenshot ?: cover)?.let { rememberCoverPainter(app, it, COVER_PX) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (picture != null) {
            Image(
                painter = picture.painter,
                contentDescription = null,
                modifier = Modifier.aspectRatio(picture.ratio, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(theme.style.metrics.cardCornerDp.dp / 2)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxHeight(OBJECT_FILL).aspectRatio(1f), contentAlignment = Alignment.Center) {
                MarkIcon(art?.mark ?: MARK_CONSOLE, theme.style.hero.markScale)
            }
        }
    }
}

/** The focused save, large: its slot, then when, who made it, and how big, each led by its symbol. */
@Composable
private fun SaveFacts(save: SavePickerSave, dates: DateStyle, theme: Theme, modifier: Modifier) {
    val design = theme.style
    Column(
        modifier
            .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp))
            .background(theme.surface.copy(alpha = design.hero.cardAlpha))
            .padding(CARD_PAD),
        verticalArrangement = Arrangement.spacedBy(design.saves.gapDp.dp),
    ) {
        if (save.playing) Tag(SavePickerCopy.playing, theme.focus)
        else if (save.backup) Tag(SavePickerCopy.backup, theme.muted)
        BasicText(
            if (save.backup) save.slot?.takeIf { it.isNotBlank() } ?: SavePickerCopy.noSlot else SavePickerCopy.slot(save),
            style = saveStyle(theme.onBackground, design.type.heroTitle, theme, bold = true, display = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Fact("clock", formatSaveWhen(save.updatedAtMillis, dates), theme)
        Fact("handheld", SavePickerCopy.sourceDetail(save), theme, lines = 2)
        Fact("cartridge", formatSaveSize(save.sizeBytes), theme)
    }
}

/** The game and its save state, large, with one line under them: how many saves, or why there are none yet. */
@Composable
private fun Overview(state: SavePickerState, theme: Theme, scale: Float, modifier: Modifier) {
    val design = theme.style
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BasicText(
            state.gameLabel,
            style = saveStyle(theme.onBackground, design.type.heroTitle, theme, bold = true, display = true),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val (badge, tint) = healthBadge(state.health, theme)
            val icon = themeIcon(badge)
            Image(icon.painter, contentDescription = null, colorFilter = icon.tint(tint), modifier = Modifier.size(28.dp))
            BasicText(
                SavePickerCopy.header(state.health),
                style = saveStyle(if (state.health.needsAttention) design.colors.attention else theme.onBackground, design.type.heroMeta, theme, bold = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            state.loading -> Pulsing(SavePickerCopy.loading, theme, design.type.heroMeta, scale)
            state.error -> BasicText(SavePickerCopy.error, style = saveStyle(theme.muted, design.type.heroMeta, theme))
            else -> BasicText(SavePickerCopy.count(state.saves.size), style = saveStyle(theme.muted, design.type.heroMeta, theme))
        }
    }
}

@Composable
private fun Fact(symbol: String, value: String, theme: Theme, lines: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(FACT_ICON), contentAlignment = Alignment.Center) { SymbolIcon(symbol, 1f, theme.muted) }
        BasicText(value, style = saveStyle(theme.onBackground, theme.style.type.heroMeta, theme), maxLines = lines, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The bottom screen acts, in Settings' clothes: the header pill with Back and the status chip
 * beside it, then one card of saves under a single gliding highlight. Y opens the slots to copy
 * into in the same card, and a confirmation rises over it as every dialog does.
 */
@Composable
internal fun SavePickerBottom(app: FoldcadeApp, state: SavePickerState, dates: DateStyle, scale: Float, dispatch: (Effect?) -> Unit) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val model = app.shell.model
    val back = promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB
    val art = app.shell.savePickerArt(state.target)
    val press = { meaning: Meaning -> dispatch(app.shell.onMeaning(meaning, HostScreen.Bottom)) }
    Box(Modifier.fillMaxSize().background(theme.background)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = design.saves.edgeDp.dp, vertical = BOTTOM_EDGE_V),
            verticalArrangement = Arrangement.spacedBy(BOTTOM_GAP),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderPill(if (state.copying == null) SavePickerCopy.title else SavePickerCopy.copyTo, back, enabled = !state.busy, scale = scale) { press(Meaning.Back) }
                Spacer(Modifier.weight(1f))
                StatusChip(
                    state.health,
                    focused = state.index == 0 && state.statusActs && state.copying == null && !state.loading && !state.error && !state.busy,
                    lit = state.confirmation == null,
                    scale = scale,
                ) { dispatch(app.shell.touchSavePickerHeader(state.target)) }
            }
            val card = RoundedCornerShape(design.metrics.cardCornerDp.dp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(card)
                    .background(cardFill(theme, design.settings.cardLift))
                    .border(design.settings.borderDp.dp, theme.onBackground.copy(alpha = design.settings.borderAlpha), card),
            ) {
                val mode = when {
                    state.busy -> CardMode.Busy
                    state.loading -> CardMode.Loading
                    state.error -> CardMode.Error
                    state.copying != null -> CardMode.Destinations
                    state.saves.isEmpty() -> CardMode.Empty
                    else -> CardMode.Saves
                }
                // The save being copied stays named while its slots slide away after the copy.
                var copying by remember { mutableStateOf(state.copying) }
                if (state.copying != null) copying = state.copying
                SlideSwap(
                    target = mode,
                    scale = scale,
                    distance = 40.dp,
                    vertical = false,
                    direction = { from, to -> to.depth.compareTo(from.depth) },
                ) { shown ->
                    when (shown) {
                        CardMode.Loading -> CardNote("cloud", SavePickerCopy.loading, pulse = true, scale = scale)
                        CardMode.Busy -> CardNote("cloud", SavePickerCopy.working, pulse = true, scale = scale)
                        CardMode.Error -> CardNote("cloud", SavePickerCopy.error, pulse = false, scale = scale, modifier = Modifier.hostPress { press(Meaning.Activate) })
                        CardMode.Empty -> CardNote("cloud", SavePickerCopy.empty, pulse = false, scale = scale)
                        CardMode.Saves -> SaveList(app, state, art, dates, scale, dispatch)
                        CardMode.Destinations -> DestinationList(app, state.copy(copying = state.copying ?: copying), art, scale, dispatch)
                    }
                }
                val message = presence(state.message, theme.motion.durationFocus, scale)
                message.shown?.let { text ->
                    Box(Modifier.fillMaxSize().padding(CARD_PAD), contentAlignment = Alignment.BottomCenter) {
                        BasicText(
                            text,
                            modifier = Modifier
                                .rise { message.progress.value }
                                .clip(RoundedCornerShape(design.shape.toastCornerDp.dp))
                                .background(theme.surface.copy(alpha = design.dialog.toastAlpha))
                                .border(design.settings.borderDp.dp, theme.onBackground.copy(alpha = design.dialog.borderAlpha), RoundedCornerShape(design.shape.toastCornerDp.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            style = saveStyle(theme.onBackground, design.type.dialogBody, theme).copy(textAlign = TextAlign.Center),
                            maxLines = 3,
                        )
                    }
                }
            }
            val hints = savePickerHints(state, model.faceMap)
            Row(
                Modifier.fillMaxWidth().height(HINT_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val options = actionFaces(model.faceMap).optionsGlyph
                hints.forEach { hint ->
                    Row(Modifier.hostPress { press(if (hint.key == options) Meaning.Options else Meaning.Activate) }) { HintWord(model, hint) }
                }
            }
        }
        val confirm = presence(state.confirmation, theme.motion.durationFocus, scale)
        confirm.shown?.let { choice ->
            val live = state.confirmation == choice
            Confirmation(app, state, choice, dates, Modifier.rise { confirm.progress.value }) { index ->
                if (live) dispatch(app.shell.touchSavePickerConfirm(state.target, choice, index))
            }
        }
    }
}

/** What the card holds. Choosing a slot is a level in, so it slides in from the right and back out. */
private enum class CardMode(val depth: Int) { Loading(0), Busy(0), Error(0), Empty(0), Saves(0), Destinations(1) }

@Composable
private fun SaveList(app: FoldcadeApp, state: SavePickerState, art: RunningApp?, dates: DateStyle, scale: Float, dispatch: (Effect?) -> Unit) {
    val slots = rememberSelectionSlots()
    val scroll = rememberScrollState()
    val now = System.currentTimeMillis()
    val focus = (state.index - 1).takeIf { it >= 0 }
    Column(
        Modifier
            .fillMaxSize()
            .menuFade(scroll)
            .verticalScroll(scroll)
            .padding(ringReach() + CARD_INSET)
            .selectionHighlight(slots, focus, ring = state.confirmation == null, scale = scale),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        state.saves.forEachIndexed { index, save ->
            val focused = focus == index
            SaveRow(
                app, save, art, relativeSaveWhen(save.updatedAtMillis, now, dates), focused, scale,
                Modifier.selectionSlot(slots, index).keepInView(focused).hostPress { dispatch(app.shell.touchSavePickerSave(state.target, save.id)) },
            )
        }
    }
}

@Composable
private fun DestinationList(app: FoldcadeApp, state: SavePickerState, art: RunningApp?, scale: Float, dispatch: (Effect?) -> Unit) {
    val destinations = state.destinations()
    if (destinations.isEmpty()) {
        CardNote("folder", SavePickerCopy.noDestinations, pulse = false, scale = scale)
        return
    }
    val slots = rememberSelectionSlots()
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .menuFade(scroll)
            .verticalScroll(scroll)
            .padding(ringReach() + CARD_INSET)
            .selectionHighlight(slots, state.destinationIndex, ring = state.confirmation == null, scale = scale),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        destinations.forEachIndexed { index, destination ->
            val focused = state.destinationIndex == index
            DestinationRow(
                destination, art, focused, scale,
                Modifier.selectionSlot(slots, index).keepInView(focused).hostPress { dispatch(app.shell.touchSavePickerDestination(state.target, destination)) },
            )
        }
    }
}

/**
 * One save at the menus' one row height: its picture, its slot, where it came from when that
 * is not this device, how long ago, and Playing on the one Foldcade plays. A Backup rests quieter.
 */
@Composable
private fun SaveRow(app: FoldcadeApp, save: SavePickerSave, art: RunningApp?, whenText: String, focused: Boolean, scale: Float, modifier: Modifier) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val lit = eased(if (focused) 1f else 0f, theme.motion.durationFocus, scale)
    val lift = bouncy(if (focused) 1f else 0f, scale)
    val rest = if (save.backup) design.saves.backupAlpha else design.menu.labelRestAlpha
    Row(
        modifier.fillMaxWidth().height(design.saves.rowHeightDp.dp).padding(horizontal = ROW_PAD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumb(app, save.screenshotUri, if (save.backup) "shield" else art?.mark, lit, lift, rest)
        BasicText(
            SavePickerCopy.slot(save),
            modifier = Modifier.weight(1f).graphicsLayer { alpha = rest + (1f - rest) * lit.value },
            style = saveStyle(theme.onBackground, design.type.menuRow, theme, bold = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = Modifier.graphicsLayer { alpha = if (save.backup) rest + (1f - rest) * lit.value else 1f }
        if (save.origin?.thisDevice != true) {
            Row(meta.widthIn(max = SOURCE_MAX), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(MARK_SIZE), contentAlignment = Alignment.Center) { SymbolIcon("handheld", 1f, theme.muted) }
                BasicText(SavePickerCopy.source(save), style = saveStyle(theme.muted, design.type.hint, theme), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        BasicText(whenText, modifier = meta, style = saveStyle(theme.muted, design.type.hint, theme), maxLines = 1, softWrap = false)
        if (save.playing) Tag(SavePickerCopy.playing, theme.focus)
    }
}

/** A slot to copy into: its slot, and whose it is. */
@Composable
private fun DestinationRow(destination: SaveDestination, art: RunningApp?, focused: Boolean, scale: Float, modifier: Modifier) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val lit = eased(if (focused) 1f else 0f, theme.motion.durationFocus, scale)
    val lift = bouncy(if (focused) 1f else 0f, scale)
    val rest = design.menu.labelRestAlpha
    Row(
        modifier.fillMaxWidth().height(design.saves.rowHeightDp.dp).padding(horizontal = ROW_PAD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumb(null, null, art?.mark, lit, lift, rest)
        BasicText(
            destination.slot,
            modifier = Modifier.weight(1f).graphicsLayer { alpha = rest + (1f - rest) * lit.value },
            style = saveStyle(theme.onBackground, design.type.menuRow, theme, bold = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(destination.source, modifier = Modifier.widthIn(max = SOURCE_MAX * 1.5f), style = saveStyle(theme.muted, design.type.hint, theme),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A row's picture on a soft square: the save's screenshot, else [symbol] in one ink, as Settings' tiles wear theirs. */
@Composable
private fun Thumb(app: FoldcadeApp?, screenshot: String?, symbol: String?, lit: androidx.compose.runtime.State<Float>, lift: androidx.compose.runtime.State<Float>, rest: Float) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val shot = if (app != null && screenshot != null) rememberCoverPainter(app, screenshot, THUMB_PX) else null
    Box(
        Modifier
            .size(THUMB)
            .lifted(lit, lift, grow = design.menu.artGrow, rest = rest)
            .clip(RoundedCornerShape(design.shape.thumbCornerDp.dp / 2))
            .background(theme.onBackground.copy(alpha = design.menu.quickTileAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        if (shot != null) {
            Image(shot.painter, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val mark = symbol?.takeIf { symbolIcon(it) != null } ?: MARK_CONSOLE
            SymbolIcon(mark, SYMBOL_FILL, theme.onBackground)
        }
    }
}

/** The card's one line when there is no list: a symbol over a few words, the symbol breathing while it waits. */
@Composable
private fun CardNote(symbol: String, text: String, pulse: Boolean, scale: Float, modifier: Modifier = Modifier) {
    val theme = LocalFoldTheme.current.theme
    val breath = if (pulse) {
        val transition = rememberInfiniteTransition()
        transition.animateFloat(theme.style.menu.labelRestAlpha, 1f, theme.motion.pulse(scale))
    } else null
    Column(
        modifier.fillMaxSize().padding(CARD_PAD),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(NOTE_ART).graphicsLayer { alpha = breath?.value ?: theme.style.menu.artRestAlpha }, contentAlignment = Alignment.Center) {
            SymbolIcon(symbol, 1f, theme.onBackground)
        }
        BasicText(text, style = saveStyle(theme.onBackground, theme.style.type.menuRow, theme).copy(textAlign = TextAlign.Center))
    }
}

/** The page's name with the Back glyph, as Settings and Play activity wear it. A tap goes back. */
@Composable
private fun HeaderPill(title: String, back: PromptKey, enabled: Boolean, scale: Float, onBack: () -> Unit) {
    val theme = LocalFoldTheme.current.theme
    val ready = eased(if (enabled) 1f else 0f, theme.motion.durationFocus, scale)
    Row(
        Modifier
            .clip(theme.style.shape.control())
            .background(theme.surface)
            .hostPress { if (enabled) onBack() }
            .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PromptImage(promptGlyph(back), 32.dp, Modifier.graphicsLayer { alpha = theme.style.menu.labelRestAlpha.let { it + (1f - it) * ready.value } })
        SlideSwap(target = title, scale = scale, distance = 16.dp, vertical = true, direction = { _, to -> if (to == SavePickerCopy.title) -1 else 1 }) { shown ->
            BasicText(shown, style = saveStyle(theme.onBackground, theme.style.type.heroTitle, theme, bold = true, display = true), maxLines = 1)
        }
    }
}

/**
 * How this game's saves are doing, in a chip at the page's head. It is only read while all is well;
 * while setup or an upload is waiting it is the first thing focused, and wears the ring A acts on.
 */
@Composable
private fun StatusChip(health: SaveHealth, focused: Boolean, lit: Boolean, scale: Float, onClick: () -> Unit) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val ring = eased(if (focused) 1f else 0f, theme.motion.durationFocus, scale)
    val ringLit = eased(if (lit) 1f else 0f, theme.motion.durationFocus, scale)
    val pop = bouncy(if (focused) 1f else 0f, scale)
    val focusRing = remember(theme) { FocusRing(theme) }
    val attention = health.needsAttention
    val (badge, tint) = healthBadge(health, theme)
    Row(
        Modifier
            .graphicsLayer {
                val grow = 1f + design.menu.artGrow / 3f * pop.value
                scaleX = grow
                scaleY = grow
            }
            .drawBehind {
                val corner = size.height / 2f * design.shape.round
                drawRoundRect(
                    color = if (attention) design.colors.attention.copy(alpha = design.dialog.chipStrongAlpha) else theme.surface,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                )
                val shown = ring.value * ringLit.value
                if (shown > 0f) with(focusRing) { drawFocusRing(corner, alpha = shown) }
            }
            .hostPress(onClick)
            .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val icon = themeIcon(badge)
        Image(icon.painter, contentDescription = null, colorFilter = icon.tint(tint), modifier = Modifier.size(22.dp))
        BasicText(
            SavePickerCopy.header(health),
            style = saveStyle(if (attention) design.colors.attention else theme.onBackground, design.type.hint, theme, bold = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The sync badge in focus while saves reach RomM, the unsynced cloud in amber while they can't. */
private fun healthBadge(health: SaveHealth, theme: Theme): Pair<Int, Color> = when (health) {
    SaveHealth.Fine -> R.drawable.ic_badge_sync to theme.focus
    is SaveHealth.NotSyncing, SaveHealth.Waiting -> R.drawable.ic_badge_unsynced to theme.style.colors.attention
    SaveHealth.DeviceOnly -> R.drawable.ic_badge_cloud to theme.muted
}

/** Play or copy, asked once with Cancel focused, in the dialog card every other question uses. */
@Composable
private fun Confirmation(app: FoldcadeApp, state: SavePickerState, choice: SavePickerChoice, dates: DateStyle, modifier: Modifier, onButton: (Int) -> Unit) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val card = RoundedCornerShape(design.metrics.cardCornerDp.dp)
    Box(
        modifier
            .fillMaxSize()
            .blocksTouch()
            .background(design.colors.scrim.copy(alpha = design.dialog.scrimAlpha))
            .padding(design.saves.edgeDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(card)
                .background(theme.surface)
                .border(design.settings.borderDp.dp, theme.onBackground.copy(alpha = design.dialog.borderAlpha), card)
                .padding(CARD_PAD),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(SavePickerCopy.confirmationTitle(choice, state.playerName), style = saveStyle(theme.onBackground, design.type.dialogTitle, theme, bold = true, display = true))
            BasicText(
                SavePickerCopy.confirmationBody(choice, state.target.playerId, state.playerName, dates, state.restartsOpenGame, state.ownSlots),
                style = saveStyle(theme.onBackground, design.type.dialogBody, theme),
            )
            val action = when (choice) {
                is SavePickerChoice.Play -> SavePickerCopy.play
                is SavePickerChoice.Copy -> SavePickerCopy.copy
                SavePickerChoice.Setup -> Copy.setUp
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                listOf(action, SavePickerCopy.cancel).forEachIndexed { index, label ->
                    DialogAction(label, focused = state.confirmIndex == index) { onButton(index) }
                }
            }
        }
    }
}

/** A small word in its own colour on a soft pill of it: Playing, or Backup. */
@Composable
private fun Tag(label: String, color: Color) {
    val theme = LocalFoldTheme.current.theme
    BasicText(
        label,
        Modifier.clip(theme.style.shape.control()).background(color.copy(alpha = theme.style.dialog.chipStrongAlpha)).padding(horizontal = 10.dp, vertical = 2.dp),
        maxLines = 1,
        style = saveStyle(color, theme.style.type.hint, theme, bold = true),
    )
}

@Composable
private fun Pulsing(text: String, theme: Theme, size: TextUnit, scale: Float) {
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(theme.style.menu.labelRestAlpha, 1f, theme.motion.pulse(scale))
    BasicText(text, modifier = Modifier.graphicsLayer { alpha = pulse }, style = saveStyle(theme.muted, size, theme))
}

private fun saveStyle(color: Color, size: TextUnit, theme: Theme, bold: Boolean = false, display: Boolean = false): TextStyle = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = if (display) theme.displayFont else theme.font,
    fontWeight = if (bold) theme.style.type.strongWeight else theme.style.type.bodyWeight,
)

/**
 * The bottom screen is about 538 × 468dp. Its margins, header, and hints leave the card
 * about 330dp, five rows and a half, so a sixth save shows there is more.
 */
private val BOTTOM_EDGE_V = 16.dp
private val BOTTOM_GAP = 12.dp
private val HINT_HEIGHT = 28.dp
private val ROW_GAP = 4.dp
private val ROW_PAD = 12.dp
private val CARD_INSET = 4.dp
private val CARD_PAD = 24.dp
private val THUMB = 36.dp
private const val THUMB_PX = 144
private val MARK_SIZE = 14.dp
private val SOURCE_MAX = 120.dp
private val NOTE_ART = 56.dp
private const val SYMBOL_FILL = 0.72f

/** The top screen, about 833 × 468dp, keeps Play activity's margins. */
private val TOP_EDGE_H = 40.dp
private val TOP_EDGE_V = 24.dp
private val TOP_GAP = 32.dp
private val FACT_ICON = 24.dp
private const val COVER_PX = 1024
private const val OBJECT_FILL = 0.8f
