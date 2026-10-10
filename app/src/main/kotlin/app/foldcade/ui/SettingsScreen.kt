package app.foldcade.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.R
import app.foldcade.language.Effect
import app.foldcade.language.FocusRingColors
import app.foldcade.language.HostScreen
import app.foldcade.language.Metrics
import app.foldcade.language.Row
import app.foldcade.language.SettingsCopy
import app.foldcade.language.Theme
import app.foldcade.language.TypeRamp
import app.foldcade.language.rowText
import app.foldcade.language.settingsCategories
import app.foldcade.language.settingsDetail
import app.foldcade.language.settingsRows
import app.foldcade.language.settingsTitle

/**
 * Full-screen settings, laid out like Cocoon's: a header, the categories on the left, and the
 * focused category's rows on a card to the right. It replaces the hero while it is open.
 */
@Composable
internal fun SettingsScreen(app: FoldcadeApp, screen: HostScreen, onEffect: (Effect?) -> Unit) {
    val model = app.shell.model
    val page = model.settings ?: return
    val theme = LocalFoldTheme.current.theme
    val categories = settingsCategories(model)
    if (categories.isEmpty()) return
    val categoryIndex = page.category.coerceIn(0, categories.lastIndex)
    val rows = settingsRows(categories[categoryIndex], model)
    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        Header(theme) { onEffect(app.shell.closeSettings()) }
        Row(
            Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(
                Modifier
                    .weight(0.34f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    // A scrolling column clips. This keeps the focus ring inside it.
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                categories.forEachIndexed { index, category ->
                    val selected = index == categoryIndex
                    CategoryRow(
                        title = settingsTitle(category),
                        detail = settingsDetail(category),
                        selected = selected,
                        focused = selected && !page.onRows,
                        theme = theme,
                        onClick = { onEffect(app.shell.touchSettingsCategory(index, screen)) },
                    )
                }
            }
            Column(
                Modifier
                    .weight(0.66f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Metrics.cardCornerDp.dp))
                    .background(theme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rows.forEachIndexed { index, row ->
                    val focused = page.onRows && index == page.row
                    val parts = rowText(row, model)
                    if (row == Row.MusicVolume) {
                        MusicVolumeRow(
                            label = parts.label,
                            value = parts.value.orEmpty(),
                            volume = model.music.volume,
                            focused = focused,
                            interactive = true,
                            onStep = { onEffect(app.shell.touchSettingsRow(index, screen)) },
                            onVolume = app.shell::setMusicVolume,
                            modifier = Modifier
                                .fillMaxWidth()
                                .keepVisible(focused)
                                .settingsFocus(focused, theme),
                        )
                    } else {
                        SettingRow(
                            label = parts.label,
                            value = parts.value,
                            focused = focused,
                            theme = theme,
                            onClick = { onEffect(app.shell.touchSettingsRow(index, screen)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(theme: Theme, onBack: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(theme.surface)
            .press(onBack)
            .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(painterResource(R.drawable.ic_btn_b), contentDescription = null, modifier = Modifier.size(32.dp))
        BasicText(SettingsCopy.title, style = style(theme.onBackground, TypeRamp.heroTitle, theme, bold = true))
    }
}

@Composable
private fun CategoryRow(
    title: String,
    detail: String,
    selected: Boolean,
    focused: Boolean,
    theme: Theme,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .keepVisible(focused)
            .settingsFocus(focused, theme, lifted = selected)
            .press(onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(
            title,
            style = style(if (selected) FocusRingColors.start else theme.onBackground, TypeRamp.menuRow, theme, bold = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            BasicText(
                detail,
                style = style(theme.muted, TypeRamp.hint, theme),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String?,
    focused: Boolean,
    theme: Theme,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .keepVisible(focused)
            .settingsFocus(focused, theme)
            .press(onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            label,
            modifier = Modifier.weight(1f),
            style = style(theme.onBackground, TypeRamp.menuRow, theme, bold = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!value.isNullOrEmpty()) {
            BasicText(
                value,
                modifier = Modifier.padding(start = 16.dp),
                style = style(if (focused) FocusRingColors.start else theme.muted, TypeRamp.menuRow, theme)
                    .copy(textAlign = TextAlign.End),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A lifted fill for the selected entry, and the focus ring while the D-pad is on it. */
private fun Modifier.settingsFocus(focused: Boolean, theme: Theme, lifted: Boolean = focused): Modifier =
    graphicsLayer { clip = false }.drawBehind {
        val corner = CornerRadius(22.dp.toPx(), 22.dp.toPx())
        if (lifted) drawRoundRect(color = theme.onBackground.copy(alpha = 0.06f), cornerRadius = corner)
        if (focused) with(FocusRing) { drawFocusRing(22.dp.toPx()) }
    }

/**
 * The D-pad moves focus, not the scroll, so the focused entry asks its column to show it,
 * with room for the focus ring around it.
 */
@Composable
private fun Modifier.keepVisible(focused: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(focused, size) {
        if (focused && size != IntSize.Zero) {
            requester.bringIntoView(Rect(-margin, -margin, size.width + margin, size.height + margin))
        }
    }
    return onSizeChanged { size = it }.bringIntoViewRequester(requester)
}

@Composable
private fun Modifier.press(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return focusProperties { canFocus = false }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

private fun style(color: Color, size: TextUnit, theme: Theme, bold: Boolean = false): TextStyle = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = theme.font,
    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
)
