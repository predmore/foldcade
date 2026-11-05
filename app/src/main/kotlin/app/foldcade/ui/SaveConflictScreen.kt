package app.foldcade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import app.foldcade.language.newerSide

/**
 * The top screen names the game. The choice itself is on the bottom screen.
 */
@Composable
internal fun SaveConflictTop(prompt: SaveConflictPrompt) {
    val theme = LocalFoldTheme.current.theme
    Column(
        Modifier
            .fillMaxSize()
            .blocksTouch()
            // Opaque, so the focused tile's hero and name never show through the choice.
            .background(theme.background)
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicLine(prompt.gameLabel, theme.onBackground, LocalFoldTheme.current.theme.style.type.conflictGame.value, theme, TextAlign.Center, display = true)
        BasicLine(Copy.saveConflictTitle, theme.onBackground, LocalFoldTheme.current.theme.style.type.heroTitle.value, theme, TextAlign.Center, display = true)
        BasicLine(Copy.saveConflictTop, theme.muted, LocalFoldTheme.current.theme.style.type.heroMeta.value, theme, TextAlign.Center)
    }
}

/**
 * The choice on one opaque card, as every dialog is, over the scrim, so no tile reads through it.
 * Both saves sit side by side at one width, and each card is the thing you press: A plays the
 * focused one. Leave as is starts focused, with Decide later, the back button, beside it. The
 * ring is the same green-to-blue stroke as every other chip, drawn heavier so a controller focus
 * reads across the bottom screen. A short panel tightens the spacing. Anything that still does
 * not fit scrolls, and the focused action brings itself into view.
 */
@Composable
internal fun SaveConflictBottom(
    prompt: SaveConflictPrompt,
    dateStyle: DateStyle = DateStyle(),
    onFocus: (SaveConflictFocus) -> Unit,
    onLater: () -> Unit,
    backKey: PromptKey = PromptKey.FaceB,
    confirmKey: PromptKey = PromptKey.FaceA,
) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val type = design.type
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .blocksTouch()
            .background(design.colors.scrim.copy(alpha = design.dialog.conflictScrimAlpha)),
    ) {
        val fit = saveConflictFit(maxWidth.value, maxHeight.value)
        val edge = fit.edge.dp
        val gap = fit.gap.dp
        val corner = RoundedCornerShape(design.metrics.cardCornerDp.dp)
        val border = with(LocalDensity.current) { design.metrics.dialogBorderPx.toDp() }
        // Inside the card, room for the heavier ring's glow and the focused chip's grow, so the
        // focused action never meets the card's edge.
        val ring = design.focusRing
        val reach = with(LocalDensity.current) { (ring.gap + ring.pronouncedStroke + ring.pronouncedSpread).toDp() } + FOCUS_GROW_ROOM
        val inset = maxOf(edge, reach)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(edge)
                .heightIn(min = (maxHeight - edge * 2).coerceAtLeast(0.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    // The cards keep their one width on a wide screen; a narrow one fits the card to it.
                    .widthIn(max = fit.cardWidth.dp * 2 + gap + inset * 2)
                    .fillMaxWidth()
                    .clip(corner)
                    .background(theme.surface)
                    .border(border, theme.onBackground.copy(alpha = design.dialog.borderAlpha), corner)
                    .padding(inset),
                verticalArrangement = Arrangement.spacedBy(if (fit.compact) 2.dp else 6.dp),
            ) {
                BasicLine(Copy.saveConflictTitle, theme.onBackground, type.dialogTitle.value, theme, TextAlign.Start, maxLines = 1, display = true, weight = type.strongWeight)
                BasicLine(prompt.gameLabel, theme.onBackground, type.dialogBody.value, theme, TextAlign.Start, maxLines = 1)
                BasicLine(Copy.saveConflictBody, theme.muted, type.dialogBody.value, theme, TextAlign.Start)
                Row(
                    Modifier
                        .padding(top = gap)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.Top,
                ) {
                    val newer = newerSide(prompt)
                    SaveCard(prompt.local, dateStyle, prompt.focus == SaveConflictFocus.Local, fit.compact, newer == SaveConflictFocus.Local, confirmKey, Modifier.weight(1f)) {
                        onFocus(SaveConflictFocus.Local)
                    }
                    SaveCard(prompt.remote, dateStyle, prompt.focus == SaveConflictFocus.Remote, fit.compact, newer == SaveConflictFocus.Remote, confirmKey, Modifier.weight(1f)) {
                        onFocus(SaveConflictFocus.Remote)
                    }
                }
                Row(
                    Modifier.padding(top = gap),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConflictChip(
                        label = Copy.keepBoth,
                        focused = prompt.focus == SaveConflictFocus.KeepBoth,
                        compact = fit.compact,
                        modifier = Modifier,
                        onClick = { onFocus(SaveConflictFocus.KeepBoth) },
                    )
                    LaterChip(compact = fit.compact, backKey = backKey, modifier = Modifier, onClick = onLater)
                }
            }
        }
    }
}

/** One save, pressed as a whole: who made it, when, its size, Newer on the later one, and A Play this one. */
@Composable
private fun SaveCard(
    side: SaveConflictSide,
    dateStyle: DateStyle,
    focused: Boolean,
    compact: Boolean,
    newer: Boolean,
    confirmKey: PromptKey,
    modifier: Modifier,
    onKeep: () -> Unit,
) {
    val theme = LocalFoldTheme.current.theme
    val type = theme.style.type
    val source = remember { MutableInteractionSource() }
    val grow = if (focused) CARD_GROW else LocalFoldTheme.current.theme.motion.scaleRest
    Column(
        modifier
            .fillMaxHeight()
            .keepInView(focused)
            .graphicsLayer {
                clip = false
                scaleX = grow
                scaleY = grow
            }
            .conflictCard(focused)
            .clickable(interactionSource = source, indication = null, onClick = onKeep)
            .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
    ) {
        BasicLine(side.source, theme.onBackground, type.menuRow.value, theme, TextAlign.Start, maxLines = 2)
        BasicLine(formatSaveWhen(side.updatedAtMillis, dateStyle), theme.onBackground, type.dialogBody.value, theme, TextAlign.Start, maxLines = 2)
        // The newer copy says so in the focus colour, so the two times need no comparing.
        val size = formatSaveSize(side.sizeBytes)
        BasicLine(if (newer) "$size · ${Copy.newerSave}" else size, if (newer) theme.focus else theme.muted, type.dialogBody.value, theme, TextAlign.Start, maxLines = 1)
        // What A does here, at full ink on the focused card, so the card reads as its own button.
        // It sits at the card's foot, so the two line up whatever their names' lengths.
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.padding(top = if (compact) 6.dp else 10.dp).graphicsLayer { alpha = if (focused) 1f else theme.style.menu.labelRestAlpha },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PromptImage(promptGlyph(confirmKey), 22.dp)
            BasicLine(Copy.keepThisOne, if (focused) theme.onBackground else theme.muted, type.hint.value, theme, TextAlign.Start, maxLines = 1, weight = if (focused) type.strongWeight else null)
        }
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
    weight: FontWeight? = null,
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        style = TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            fontFamily = if (display) theme.displayFont else theme.font,
            fontWeight = weight,
            textAlign = align,
        ),
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
}

/** A save card's soft fill on the dialog card, ringed like the chips while it has the focus. */
@Composable
private fun Modifier.conflictCard(focused: Boolean): Modifier {
    val theme = LocalFoldTheme.current.theme
    val lift = theme.onBackground
    val dialog = theme.style.dialog
    val cornerDp = theme.style.shape.highlightCornerDp.dp
    return this.graphicsLayer { clip = false }.drawBehind {
        val corner = cornerDp.toPx()
        drawRoundRect(
            color = lift.copy(alpha = if (focused) dialog.chipStrongAlpha else dialog.chipAlpha),
            cornerRadius = CornerRadius(corner, corner),
        )
        if (focused) with(FocusRing(theme)) { drawFocusRing(corner, pronounced = true) }
    }
}

/** How far a focused chip's 8% grow reaches past its edge, about a tenth of its width. */
private val FOCUS_GROW_ROOM = 10.dp

/** A focused save card grows a little, less than a chip, as it is so much larger. */
private const val CARD_GROW = 1.03f

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
