// SPDX-License-Identifier: MIT

package app.foldcade.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.api.plugin.Game
import app.foldcade.language.GameAboutCopy
import app.foldcade.language.AboutFact
import app.foldcade.artwork.ArtQuery
import app.foldcade.language.GameAboutPage
import app.foldcade.language.MARK_CONSOLE
import app.foldcade.language.PromptKey
import app.foldcade.language.Theme
import app.foldcade.language.aboutFacts
import app.foldcade.language.promptKeyOf

/**
 * Y › About: one game's page, over its own art dimmed. Its cover on the left; on the right
 * the facts the libraries know, a screenshot when one is known, and the summary, cut to the
 * room left with "…". Nothing scrolls. With no facts it still shows the game and its
 * platform, and one quiet line on where the facts come from. B closes it.
 */
@Composable
internal fun GameAboutScreen(app: FoldcadeApp, page: GameAboutPage, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val game = app.shell.aboutGame(page.game)
    val record = game?.let(app.shell::artRecord)
    val query = game?.let(app.shell::artQuery)
    val details = rememberGameDetails(app, record)
    Box(Modifier.fillMaxSize().background(theme.background)) {
        SceneBackdrop(app, record, query, app.shell.model.motionSpeed, scale)
        Box(Modifier.fillMaxSize().background(design.colors.scrim.copy(alpha = design.hero.aboutDim)))
        Column(
            Modifier.fillMaxSize().padding(horizontal = EDGE_H, vertical = EDGE_V),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The pill takes what the platform leaves, and a long name ends in "…".
                Box(Modifier.weight(1f)) {
                    Row(
                        Modifier
                            .clip(design.shape.control())
                            .background(theme.surface)
                            .hostPress { app.shell.closeGameAbout() }
                            .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PromptImage(promptGlyph(promptKeyOf(app.shell.model.faceMap.backKey) ?: PromptKey.FaceB), 32.dp)
                        BasicText(
                            game?.title ?: GameAboutCopy.about,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = aboutText(theme.onBackground, theme, design.type.heroTitle, bold = true, display = true),
                        )
                    }
                }
                val platform = game?.shortText?.takeIf { it.isNotBlank() }
                if (platform != null) {
                    Spacer(Modifier.width(24.dp))
                    BasicText(platform, maxLines = 1, style = aboutText(theme.muted, theme, design.type.heroMeta))
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val coverMax = maxWidth * COVER_SHARE
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    AboutCover(app, record, query, game?.mark, Modifier.fillMaxHeight().widthIn(max = coverMax))
                    val facts = details?.let { aboutFacts(it.releaseYear, it.genres, it.developer, it.publisher, it.players) }.orEmpty()
                    AboutCard(app, record, query, facts, details?.summary, theme, Modifier.weight(1f))
                }
            }
        }
    }
}

/** The game's cover at its own shape, else its platform's object. Never an empty box. */
@Composable
private fun AboutCover(app: FoldcadeApp, record: Game?, query: ArtQuery?, mark: String?, modifier: Modifier) {
    val theme = LocalFoldTheme.current.theme
    val cover = rememberArt(app, record, query)?.cover?.let { rememberCoverPainter(app, it, COVER_PX) }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        if (cover != null) {
            Image(
                painter = cover.painter,
                contentDescription = null,
                modifier = Modifier
                    .aspectRatio(cover.ratio, matchHeightConstraintsFirst = cover.ratio < 1f)
                    .clip(RoundedCornerShape(theme.style.metrics.cardCornerDp.dp / 2)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                MarkIcon(mark ?: MARK_CONSOLE, theme.style.hero.markScale)
            }
        }
    }
}

/**
 * The words, in one card over the art: the facts, a screenshot beside them when one is
 * known, then the summary, as much of it as fits. With neither facts nor summary, the
 * one line on where they come from. Only a summary takes the card to the bottom; without
 * one it is as tall as what it holds.
 */
@Composable
private fun AboutCard(
    app: FoldcadeApp,
    record: Game?,
    query: ArtQuery?,
    facts: List<AboutFact>,
    summary: String?,
    theme: Theme,
    modifier: Modifier,
) {
    val design = theme.style
    val shot = rememberScene(app, record, query)?.screen?.let { rememberCoverPainter(app, it, SCREEN_PX, FilterQuality.None) }
    val body = aboutText(theme.onBackground, theme, design.type.dialogBody)
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp))
            .background(theme.surface.copy(alpha = design.hero.cardAlpha))
            .padding(CARD_PAD),
    ) {
        val shotMax = maxWidth * SHOT_SHARE
        val shotTall = maxHeight * SHOT_HEIGHT
        Column(
            Modifier.fillMaxWidth().then(if (summary != null) Modifier.fillMaxHeight() else Modifier),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            if (facts.isNotEmpty() || shot != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    FactList(facts, theme, Modifier.weight(1f))
                    if (shot != null) {
                        Image(
                            painter = shot.painter,
                            contentDescription = null,
                            modifier = Modifier
                                .widthIn(max = shotMax)
                                .heightIn(max = shotTall)
                                .aspectRatio(shot.ratio)
                                .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp / 2)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            when {
                summary != null -> FittedText(summary, body, Modifier.weight(1f).fillMaxWidth())
                facts.isEmpty() -> BasicText(GameAboutCopy.noInfo, style = aboutText(theme.muted, theme, design.type.dialogBody))
            }
        }
    }
}

/** Each fact's name, muted, in one column, and what it is beside it, one line each. */
@Composable
private fun FactList(facts: List<AboutFact>, theme: Theme, modifier: Modifier) {
    if (facts.isEmpty()) {
        Spacer(modifier)
        return
    }
    val size = theme.style.type.sideRow
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(FACT_GAP)) {
            facts.forEach { BasicText(it.label, maxLines = 1, style = aboutText(theme.muted, theme, size)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FACT_GAP)) {
            facts.forEach {
                BasicText(it.value, maxLines = 1, overflow = TextOverflow.Ellipsis, style = aboutText(theme.onBackground, theme, size, bold = true))
            }
        }
    }
}

/** [text] cut to the lines that fit the room it is given, the last one ending in "…". */
@Composable
private fun FittedText(text: String, style: TextStyle, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val lines = remember(text, style, width, height) {
            val laid = measurer.measure(text, style, constraints = Constraints(maxWidth = width))
            (0 until laid.lineCount).count { laid.getLineBottom(it) <= height }
        }
        if (lines > 0) BasicText(text, style = style, maxLines = lines, overflow = TextOverflow.Ellipsis)
    }
}

private fun aboutText(color: Color, theme: Theme, size: TextUnit, bold: Boolean = false, display: Boolean = false) = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = if (display) theme.displayFont else theme.font,
    fontWeight = if (bold) theme.style.type.strongWeight else theme.style.type.bodyWeight,
)

/** The top screen, about 833 × 468dp, keeps Play activity's margins. */
private val EDGE_H = 40.dp
private val EDGE_V = 24.dp
private val GAP = 20.dp
private val CARD_PAD = 24.dp
private val FACT_GAP = 6.dp
private const val COVER_SHARE = 0.3f

/** A screenshot takes up to this much of the card's width, and of its height. */
private const val SHOT_SHARE = 0.4f
private const val SHOT_HEIGHT = 0.5f
private const val COVER_PX = 1024
private const val SCREEN_PX = 1024
