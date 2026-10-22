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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.R
import app.foldcade.language.Copy
import app.foldcade.language.Effect
import app.foldcade.language.HostScreen
import app.foldcade.language.PromptKey
import app.foldcade.language.Row
import app.foldcade.language.SettingsCategory
import app.foldcade.language.SettingsCopy
import app.foldcade.language.SettingsPage
import app.foldcade.language.Theme
import app.foldcade.language.promptKeyOf
import app.foldcade.language.rowControl
import app.foldcade.language.rowText
import app.foldcade.language.settingsCategories
import app.foldcade.language.settingsRows
import app.foldcade.language.settingsTitle

/**
 * Full-screen settings, laid out like Cocoon's: a header, the categories on the left, and the
 * focused category's rows on a card to the right. It replaces the hero while it is open.
 * Each side has one highlight that glides between its rows. The side the D-pad is in wears
 * the focus ring; the other keeps a soft fill on its choice.
 */
@Composable
internal fun SettingsScreen(app: FoldcadeApp, screen: HostScreen, page: SettingsPage, scale: Float, onEffect: (Effect?) -> Unit) {
    val design = LocalFoldTheme.current.theme.style
    val model = app.shell.model
    val theme = LocalFoldTheme.current.theme
    val categories = settingsCategories(model)
    if (categories.isEmpty()) return
    val categoryIndex = page.category.coerceIn(0, categories.lastIndex)
    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        val back = if (model.capturingConfirm) PromptKey.Start else promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Header(theme, back) { onEffect(app.shell.closeSettings()) }
            if (model.capturingConfirm) {
                BasicText(
                    Copy.buttonCalibrationHelp,
                    modifier = Modifier.weight(1f),
                    style = style(theme.muted, theme.style.type.hint, theme),
                )
            }
        }
        Row(
            Modifier
                .fillMaxSize()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val master = rememberSelectionSlots()
            // All six fit at rest on the Thor's top screen. Should they not, as at a large font
            // size, the edge with more beyond it fades, so a hidden category still shows.
            val categoryScroll = rememberScrollState()
            Column(
                Modifier
                    .weight(0.32f)
                    .fillMaxHeight()
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
            val card = RoundedCornerShape(design.metrics.cardCornerDp.dp)
            Box(
                Modifier
                    .weight(0.68f)
                    .fillMaxHeight()
                    .clip(card)
                    .background(cardFill(theme, design.settings.cardLift))
                    .border(design.settings.borderDp.dp, theme.onBackground.copy(alpha = design.settings.borderAlpha), card),
            ) {
                // A new category's rows rise in from the way the focus went.
                SlideSwap(
                    target = categories[categoryIndex],
                    scale = scale,
                    distance = 36.dp,
                    vertical = true,
                    direction = { from, to -> to.ordinal.compareTo(from.ordinal) },
                ) { category ->
                    DetailRows(app, screen, page, category, category == categories[categoryIndex], scale, onEffect)
                }
            }
        }
    }
}

@Composable
private fun DetailRows(
    app: FoldcadeApp,
    screen: HostScreen,
    page: SettingsPage,
    category: SettingsCategory,
    current: Boolean,
    scale: Float,
    onEffect: (Effect?) -> Unit,
) {
    val model = app.shell.model
    val theme = LocalFoldTheme.current.theme
    val rows = settingsRows(category, model)
    val slots = rememberSelectionSlots()
    val iconSlot = rows.any { settingsRowIcon(it) != null }
    val focus = page.row.takeIf { current && page.onRows && it in rows.indices }
    val labelStyle = style(theme.onBackground, theme.style.type.menuRow, theme, bold = true)
    Column(
        Modifier
            .fillMaxSize()
            .ringScroll(extra = 6.dp)
            .selectionHighlight(slots, focus, ring = true, scale = scale),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        rows.forEachIndexed { index, row ->
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
                    interactive = current,
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
                    // The category on its way out takes no taps: its rows are not the page's now.
                    modifier = if (current) place.press(tap) else place,
                    labelStyle = labelStyle,
                    height = ROW_HEIGHT,
                )
            }
        }
    }
}

/**
 * Categories and rows share one height and one gap, so both highlights are the same size
 * and both columns keep one rhythm. Six categories take 6 × 52 + 5 × 4 = 332dp, inside the
 * Thor's 480dp top screen less the page's margins, header, and the ring's reach.
 */
private val ROW_HEIGHT = 52.dp
private val ROW_GAP = 4.dp

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
private fun settingsRowIcon(row: Row): Int? = rowIcon(row) ?: when (row) {
    Row.SyncLibrary -> R.drawable.ic_badge_sync
    Row.AddFolder -> R.drawable.symbol_folder
    Row.AddRomm -> R.drawable.symbol_cloud
    Row.Order -> R.drawable.symbol_library
    Row.AddNewGames -> R.drawable.symbol_home
    // A cartridge's label is its art.
    Row.Artwork -> R.drawable.symbol_cartridge
    Row.ArtKey -> R.drawable.ic_status_wrench
    is Row.StreamHost -> R.drawable.symbol_moonlight
    Row.UsageAccess -> R.drawable.ic_status_playtime
    else -> null
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
