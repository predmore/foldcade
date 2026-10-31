package app.foldcade.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.foldcade.LibraryView
import app.foldcade.language.FindCopy
import app.foldcade.language.PromptKey
import kotlin.math.roundToInt

/**
 * Over All games' grid: Find, then the tabs. Select opens Find and L2 and R2 step the tabs,
 * so each shows its key, and a tap does the same. The top screen stays the focused game's.
 */
@Composable
internal fun LibraryBar(
    view: LibraryView,
    style: TextStyle,
    scale: Float,
    onSearch: () -> Unit,
    onTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalFoldTheme.current.theme
    val glyph = with(LocalDensity.current) { style.fontSize.toDp() } * SEARCH_GLYPH_SHARE
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A pill as tall as the tabs, so Find reads as one more place to look.
        Row(
            Modifier
                .hostPress(onSearch)
                .clip(theme.style.shape.control())
                .drawBehind {
                    drawRoundRect(theme.onBackground.copy(alpha = theme.style.find.filterAlpha), cornerRadius = CornerRadius(size.height * theme.style.shape.round))
                }
                .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PromptImage(promptGlyph(PromptKey.Select), glyph)
            Magnifier(theme.muted, glyph * MAGNIFIER_SHARE)
            BasicText(FindCopy.find, style = style.copy(color = theme.onBackground), maxLines = 1)
        }
        PillCarousel(
            labels = view.tabs.map { it.label },
            chosen = view.tabIndex,
            style = style,
            scale = scale,
            modifier = Modifier.weight(1f),
            onTap = onTab,
        )
    }
}

/**
 * Gives up its height as [progress] goes from 0 to 1, so what is under it can take the room
 * smoothly. Read while laying out, so only layout runs again as it goes.
 */
internal fun Modifier.collapsing(progress: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * (1f - progress().coerceIn(0f, 1f))).roundToInt()
    layout(placeable.width, height) { placeable.place(0, 0) }
}

/** Select's glyph inside the Find pill: about a line of its text, so the pill is no taller than a tab. */
private const val SEARCH_GLYPH_SHARE = 1.2f

/** The magnifier sits a size down from the glyph, as the text does. */
private const val MAGNIFIER_SHARE = 0.85f
