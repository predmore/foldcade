package app.foldcade.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import app.foldcade.language.Theme
import app.foldcade.language.TileStatus
import app.foldcade.language.emulatorShelf
import app.foldcade.language.promptKeyOf
import app.foldcade.language.quickTileColumns
import app.foldcade.language.rowControl
import app.foldcade.language.rowText
import app.foldcade.language.settingsCategories
import app.foldcade.language.settingsInnerRows
import app.foldcade.language.settingsInnerTitle
import app.foldcade.language.settingsRows
import app.foldcade.language.settingsShown
import app.foldcade.language.settingsTile
import app.foldcade.language.settingsTileStatus
import app.foldcade.language.settingsTitle

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
    Row(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 40.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(0.32f).fillMaxHeight()) {
            val back = if (model.capturingConfirm) PromptKey.Start else promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB
            Header(theme, back) { onEffect(app.shell.backSettings(screen)) }
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
                        selected = selected,
                        focused = selected && !page.onRows,
                        scale = scale,
                        modifier = Modifier.selectionSlot(master, index),
                        onClick = { onEffect(app.shell.touchSettingsCategory(index, screen)) },
                    )
                }
            }
        }
        val card = RoundedCornerShape(design.metrics.cardCornerDp.dp)
        Box(
            Modifier
                .weight(0.68f)
                .fillMaxHeight()
                .clip(card)
                .background(cardFill(theme, design.settings.cardLift))
                .border(design.settings.borderDp.dp, theme.onBackground.copy(alpha = design.settings.borderAlpha), card),
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
            focus = page.row.takeIf { live && page.onRows && it in rows.indices },
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
    focus: Int?,
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
    Column(
        Modifier
            .fillMaxSize()
            .ringScroll(extra = CARD_EXTRA)
            .selectionHighlight(slots, focus, ring = true, scale = scale),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        if (inner != null) InnerHeader(model, inner)
        // Tiles sit in lines of four, each a child of this column, so the one highlight reaches them too.
        tiles.chunked(quickTileColumns).forEachIndexed { line, chunk ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (line == (tiles.size - 1) / quickTileColumns) TILE_BLOCK_GAP else TILE_LINE_GAP),
            ) {
                chunk.forEachIndexed { column, row ->
                    val index = line * quickTileColumns + column
                    ShelfTile(
                        row = row,
                        model = model,
                        focused = focus == index,
                        scale = scale,
                        art = Modifier.selectionSlot(slots, index, depth = 3),
                        modifier = Modifier.weight(1f).then(
                            if (interactive) Modifier.press { onEffect(app.shell.touchSettingsRow(index, screen)) } else Modifier,
                        ),
                    )
                }
                // A short last line keeps the columns of the lines above it.
                repeat(quickTileColumns - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
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
                    modifier = if (interactive) place.press(tap) else place,
                    labelStyle = labelStyle,
                    height = ROW_HEIGHT,
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
        BasicText(
            settingsInnerTitle(inner, model),
            style = style(theme.onBackground, theme.style.type.dialogTitle, theme, bold = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    art: Modifier,
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
        Box(art.size(TILE_ART), contentAlignment = Alignment.Center) {
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
 * Categories and rows share one height and one gap, so both highlights are the same size
 * and both columns keep one rhythm. Six categories take 6 × 52 + 5 × 4 = 332dp, inside the
 * Thor's 468dp top screen less the page's margins, header, and the ring's reach. The card
 * beside them has the page's full height, about 410dp inside the ring's reach.
 */
private val ROW_HEIGHT = 52.dp
private val ROW_GAP = 4.dp

/**
 * A shelf's tiles. Library with RomM, two folders, a computer, and Add is two lines of
 * tiles over four rows, and fits the card on the Thor's top screen without scrolling.
 */
private val TILE_ART = 56.dp
private val TILE_LABEL_GAP = 2.dp
private val TILE_LINE_GAP = 6.dp
private val TILE_BLOCK_GAP = 6.dp
private val BADGE_SIZE = 22.dp
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
 * A settings row's art. Every Library row leads with some, so that column reads as one
 * list rather than art here and gaps there. The menus keep [rowIcon] alone.
 */
private fun settingsRowIcon(row: Row): Int? = when (row) {
    // On a page that already shows the folder or the connection.
    is Row.ForgetFolder, is Row.SignOut -> null
    Row.SyncLibrary -> R.drawable.ic_badge_sync
    Row.AddFolder -> R.drawable.symbol_folder
    Row.AddRomm -> R.drawable.symbol_cloud
    Row.Order -> R.drawable.symbol_library
    // A cartridge's label is its art.
    Row.Artwork -> R.drawable.symbol_cartridge
    Row.ArtKey -> R.drawable.ic_status_wrench
    else -> rowIcon(row)
}

@Composable
private fun Header(theme: Theme, back: PromptKey, onBack: () -> Unit) {
    Row(
        Modifier
            .clip(theme.style.shape.control())
            .background(theme.surface)
            .press(onBack)
            .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PromptImage(promptGlyph(back), 32.dp)
        BasicText(SettingsCopy.title, style = style(theme.onBackground, theme.style.type.heroTitle, theme, bold = true, display = true))
    }
}

/** A category: its art and one word, always the same height so the highlight never changes size. */
@Composable
private fun CategoryRow(
    title: String,
    icon: Int,
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
        BasicText(
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
