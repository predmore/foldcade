package app.foldcade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.foldcade.language.Copy
import app.foldcade.language.Motion
import app.foldcade.language.SaveConflictFocus
import app.foldcade.language.SaveConflictPrompt
import app.foldcade.language.SaveConflictSide
import app.foldcade.language.TypeRamp
import app.foldcade.language.formatSaveSize
import app.foldcade.language.formatSaveWhen
import app.foldcade.language.previewSaveConflict

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
        BasicLine(prompt.gameLabel, theme.onBackground, TypeRamp.conflictGame.value, theme, TextAlign.Center)
        BasicLine(Copy.saveConflictTitle, theme.onBackground, TypeRamp.heroTitle.value, theme, TextAlign.Center)
        BasicLine(Copy.saveConflictTop, theme.muted, TypeRamp.heroMeta.value, theme, TextAlign.Center)
    }
}

/**
 * Both saves, side by side, with Keep this one under each and Keep both focused first.
 * The ring is the same green-to-blue stroke as every other chip, drawn heavier so a
 * controller focus reads across the bottom screen.
 */
@Composable
internal fun SaveConflictBottom(
    prompt: SaveConflictPrompt,
    onFocus: (SaveConflictFocus) -> Unit,
) {
    val theme = LocalFoldTheme.current.theme
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(28.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicLine(Copy.saveConflictTitle, theme.onBackground, TypeRamp.heroTitle.value, theme, TextAlign.Center)
            BasicLine(prompt.gameLabel, theme.onBackground, TypeRamp.heroMeta.value, theme, TextAlign.Center)
            BasicLine(Copy.saveConflictBody, theme.muted, TypeRamp.dialogBody.value, theme, TextAlign.Center)
            Row(
                Modifier.padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SaveColumn(prompt.local, prompt.focus == SaveConflictFocus.Local) { onFocus(SaveConflictFocus.Local) }
                SaveColumn(prompt.remote, prompt.focus == SaveConflictFocus.Remote) { onFocus(SaveConflictFocus.Remote) }
            }
            ConflictChip(
                label = Copy.keepBoth,
                focused = prompt.focus == SaveConflictFocus.KeepBoth,
                modifier = Modifier.padding(top = 28.dp).widthIn(min = 220.dp),
                onClick = { onFocus(SaveConflictFocus.KeepBoth) },
            )
            BasicLine(
                "B  ${Copy.decideLater}",
                theme.muted,
                TypeRamp.hint.value,
                theme,
                TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SaveColumn(side: SaveConflictSide, focused: Boolean, onKeep: () -> Unit) {
    val theme = LocalFoldTheme.current.theme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 200.dp, max = 280.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(theme.surface)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicLine(side.source, theme.onBackground, TypeRamp.menuRow.value, theme, TextAlign.Start)
            BasicLine(formatSaveWhen(side.updatedAtMillis), theme.onBackground, TypeRamp.dialogBody.value, theme, TextAlign.Start)
            BasicLine(formatSaveSize(side.sizeBytes), theme.muted, TypeRamp.dialogBody.value, theme, TextAlign.Start)
        }
        ConflictChip(
            label = Copy.keepThisOne,
            focused = focused,
            modifier = Modifier.padding(top = 16.dp),
            onClick = onKeep,
        )
    }
}

@Composable
private fun ConflictChip(
    label: String,
    focused: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalFoldTheme.current.theme
    val source = remember { MutableInteractionSource() }
    val scale = if (focused) 1.08f else Motion.scaleRest
    androidx.compose.foundation.text.BasicText(
        text = label,
        modifier = modifier
            .graphicsLayer {
                clip = false
                scaleX = scale
                scaleY = scale
            }
            .conflictChip(focused)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        style = TextStyle(
            color = if (focused) theme.onBackground else theme.muted,
            fontSize = TypeRamp.dialogBody,
            fontFamily = theme.font,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
private fun BasicLine(
    text: String,
    color: Color,
    sizeSp: Float,
    theme: app.foldcade.language.Theme,
    align: TextAlign,
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        style = TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            fontFamily = theme.font,
            textAlign = align,
        ),
    )
}

/** The shared chip ring, with a heavier stroke so the focused action is obvious. */
@Composable
private fun Modifier.conflictChip(focused: Boolean): Modifier {
    val lift = LocalFoldTheme.current.theme.onBackground
    return this.graphicsLayer { clip = false }.drawBehind {
        val corner = size.height / 2f
        drawRoundRect(
            color = lift.copy(alpha = if (focused) 0.16f else 0.06f),
            cornerRadius = CornerRadius(corner, corner),
        )
        if (focused) with(FocusRing) { drawFocusRing(corner, pronounced = true) }
    }
}

@Preview(name = "Save conflict", device = "spec:width=1240px,height=1080px,dpi=419")
@Composable
private fun SaveConflictPreview() {
    SaveConflictBottom(previewSaveConflict(), onFocus = {})
}
