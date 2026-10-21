package app.foldcade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.foldcade.language.Copy
import app.foldcade.language.DateStyle
import app.foldcade.language.PromptKey
import app.foldcade.language.SaveConflictFocus
import app.foldcade.language.SaveConflictPrompt
import app.foldcade.language.SaveConflictSide
import app.foldcade.language.formatSaveSize
import app.foldcade.language.formatSaveWhen
import app.foldcade.language.previewSaveConflict
import app.foldcade.language.saveConflictFit

/**
 * The top screen names the game. The choice itself is on the bottom screen.
 */
@Composable
internal fun SaveConflictTop(prompt: SaveConflictPrompt) {
    val theme = LocalFoldTheme.current.theme
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicLine(prompt.gameLabel, theme.onBackground, LocalFoldTheme.current.theme.style.type.conflictGame.value, theme, TextAlign.Center, display = true)
        BasicLine(Copy.saveConflictTitle, theme.onBackground, LocalFoldTheme.current.theme.style.type.heroTitle.value, theme, TextAlign.Center, display = true)
        BasicLine(Copy.saveConflictTop, theme.muted, LocalFoldTheme.current.theme.style.type.heroMeta.value, theme, TextAlign.Center)
    }
}

/**
 * Both saves, side by side at one width, with Keep this one under each and Keep both focused first.
 * The ring is the same green-to-blue stroke as every other chip, drawn heavier so a
 * controller focus reads across the bottom screen. Decide later is B, or a touch.
 * A short panel tightens the spacing. Anything that still does not fit scrolls, and the
 * focused action brings itself into view. Decide later follows the calibrated back button.
 */
@Composable
internal fun SaveConflictBottom(
    prompt: SaveConflictPrompt,
    dateStyle: DateStyle = DateStyle(),
    onFocus: (SaveConflictFocus) -> Unit,
    onLater: () -> Unit,
    backKey: PromptKey = PromptKey.FaceB,
) {
    val theme = LocalFoldTheme.current.theme
    val type = theme.style.type
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(theme.style.colors.scrim.copy(alpha = theme.style.dialog.conflictScrimAlpha)),
    ) {
        val fit = saveConflictFit(maxWidth.value, maxHeight.value)
        val edge = fit.edge.dp
        val gap = fit.gap.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(edge)
                .heightIn(min = (maxHeight - edge * 2).coerceAtLeast(0.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicLine(Copy.saveConflictTitle, theme.onBackground, type.heroTitle.value, theme, TextAlign.Center, maxLines = 1, display = true)
            BasicLine(prompt.gameLabel, theme.onBackground, type.heroMeta.value, theme, TextAlign.Center, maxLines = 2)
            BasicLine(Copy.saveConflictBody, theme.muted, type.dialogBody.value, theme, TextAlign.Center)
            Row(
                Modifier
                    .padding(top = gap)
                    .width(fit.cardWidth.dp * 2 + gap)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Top,
            ) {
                SaveColumn(prompt.local, dateStyle, prompt.focus == SaveConflictFocus.Local, fit.compact, Modifier.weight(1f)) {
                    onFocus(SaveConflictFocus.Local)
                }
                SaveColumn(prompt.remote, dateStyle, prompt.focus == SaveConflictFocus.Remote, fit.compact, Modifier.weight(1f)) {
                    onFocus(SaveConflictFocus.Remote)
                }
            }
            ConflictChip(
                label = Copy.keepBoth,
                focused = prompt.focus == SaveConflictFocus.KeepBoth,
                compact = fit.compact,
                modifier = Modifier.padding(top = gap).widthIn(min = 220.dp),
                onClick = { onFocus(SaveConflictFocus.KeepBoth) },
            )
            LaterChip(
                compact = fit.compact,
                backKey = backKey,
                modifier = Modifier.padding(top = if (fit.compact) 8.dp else 16.dp),
                onClick = onLater,
            )
        }
    }
}

@Composable
private fun SaveColumn(
    side: SaveConflictSide,
    dateStyle: DateStyle,
    focused: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onKeep: () -> Unit,
) {
    val theme = LocalFoldTheme.current.theme
    val type = theme.style.type
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(theme.style.shape.highlightCornerDp.dp))
                .background(theme.surface)
                .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 10.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
        ) {
            BasicLine(side.source, theme.onBackground, type.menuRow.value, theme, TextAlign.Start, maxLines = 2)
            BasicLine(formatSaveWhen(side.updatedAtMillis, dateStyle), theme.onBackground, type.dialogBody.value, theme, TextAlign.Start, maxLines = 2)
            BasicLine(formatSaveSize(side.sizeBytes), theme.muted, type.dialogBody.value, theme, TextAlign.Start, maxLines = 1)
        }
        ConflictChip(
            label = Copy.keepThisOne,
            focused = focused,
            compact = compact,
            modifier = Modifier.padding(top = if (compact) 10.dp else 16.dp),
            onClick = onKeep,
        )
    }
}

@Composable
private fun ConflictChip(
    label: String,
    focused: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val motion = LocalFoldTheme.current.theme.motion
    val theme = LocalFoldTheme.current.theme
    val source = remember { MutableInteractionSource() }
    val scale = if (focused) 1.08f else motion.scaleRest
    androidx.compose.foundation.text.BasicText(
        text = label,
        modifier = modifier
            .keepInView(focused)
            .graphicsLayer {
                clip = false
                scaleX = scale
                scaleY = scale
            }
            .conflictChip(focused)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = if (compact) 10.dp else 14.dp),
        style = TextStyle(
            color = if (focused) theme.onBackground else theme.muted,
            fontSize = LocalFoldTheme.current.theme.style.type.dialogBody,
            fontFamily = theme.font,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
    )
}

/** The back button postpones the choice. On touch the chip is its own control, never focused. */
@Composable
private fun LaterChip(compact: Boolean, backKey: PromptKey, modifier: Modifier, onClick: () -> Unit) {
    val theme = LocalFoldTheme.current.theme
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .conflictChip(focused = false)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PromptImage(promptGlyph(backKey), 18.dp)
        androidx.compose.foundation.text.BasicText(
            text = Copy.decideLater,
            style = TextStyle(
                color = theme.muted,
                fontSize = theme.style.type.hint,
                fontFamily = theme.font,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun BasicLine(
    text: String,
    color: Color,
    sizeSp: Float,
    theme: app.foldcade.language.Theme,
    align: TextAlign,
    maxLines: Int = Int.MAX_VALUE,
    display: Boolean = false,
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        style = TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            fontFamily = if (display) theme.displayFont else theme.font,
            textAlign = align,
        ),
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
}

/** The shared chip ring, with a heavier stroke so the focused action is obvious. */
@Composable
private fun Modifier.conflictChip(focused: Boolean): Modifier {
    val theme = LocalFoldTheme.current.theme
    val lift = theme.onBackground
    val dialog = theme.style.dialog
    val round = theme.style.shape.round
    return this.graphicsLayer { clip = false }.drawBehind {
        val corner = size.height * round
        drawRoundRect(
            color = lift.copy(alpha = if (focused) dialog.chipStrongAlpha else dialog.chipAlpha),
            cornerRadius = CornerRadius(corner, corner),
        )
        if (focused) with(FocusRing(theme)) { drawFocusRing(corner, pronounced = true) }
    }
}

@Preview(name = "Save conflict, Thor bottom", device = "spec:width=1240px,height=1080px,dpi=419")
@Composable
private fun SaveConflictPreview() {
    SaveConflictBottom(previewSaveConflict(), onFocus = {}, onLater = {})
}

@Preview(name = "Save conflict, 16:9", device = "spec:width=1920px,height=1080px,dpi=419")
@Composable
private fun SaveConflictWidePreview() {
    SaveConflictBottom(previewSaveConflict(), onFocus = {}, onLater = {})
}

@Preview(name = "Save conflict, long names", device = "spec:width=1240px,height=1080px,dpi=419")
@Composable
private fun SaveConflictLongPreview() {
    val prompt = previewSaveConflict()
    SaveConflictBottom(
        prompt.copy(
            gameLabel = "The Legend of Zelda: Spirit Tracks and the Very Long Subtitle Edition",
            local = prompt.local.copy(source = "This device · Azahar Nightly (com.azahar.debug)"),
            remote = prompt.remote.copy(source = "Living room Steam Deck OLED · Azahar"),
        ),
        onFocus = {},
        onLater = {},
    )
}
