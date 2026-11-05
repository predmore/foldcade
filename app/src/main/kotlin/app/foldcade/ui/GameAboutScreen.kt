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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
                    val facts = aboutFacts(details?.releaseYear, details?.genres.orEmpty(), details?.developer, details?.publisher, details?.players,
                        sizeBytes = record?.sizeBytes, bothScreens = game?.occupiesBothDisplays == true)
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
 * one line on where they come from. A short summary lets the card hug its content.
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
    val measurer = rememberTextMeasurer()
    val bodyLine = remember(body) { measurer.measure(LINE_PROBE, body, maxLines = 1).size.height }
    val gapPx = with(LocalDensity.current) { GAP.roundToPx() }
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp))
            .background(theme.surface.copy(alpha = design.hero.cardAlpha))
            .padding(CARD_PAD),
    ) {
        val shotMax = maxWidth * SHOT_SHARE
        val shotTall = maxHeight * SHOT_HEIGHT
        // The facts may grow to what leaves the summary its first lines.
        val factRoom = constraints.maxHeight - if (summary != null) gapPx + bodyLine * SUMMARY_MIN_LINES else 0
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            if (facts.isNotEmpty() || shot != null) {
                // The screenshot sits in the top corner; facts beside it make room, and facts below it take the card's width.
                val shotWidth = shot?.let { minOf(shotMax, shotTall * it.ratio) } ?: 0.dp
                val shotHeight = shot?.let { shotWidth / it.ratio } ?: 0.dp
                Box(Modifier.fillMaxWidth().heightIn(min = shotHeight)) {
                    FactList(facts, theme, factRoom, besideWidth = if (shot != null) shotWidth + GAP else 0.dp, besideHeight = shotHeight, Modifier.fillMaxWidth())
                    if (shot != null) {
                        Image(
                            painter = shot.painter,
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(shotWidth, shotHeight)
                                .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp / 2)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            when {
                summary != null -> FittedText(summary, body, Modifier.weight(1f, fill = false).fillMaxWidth())
                facts.isEmpty() -> BasicText(GameAboutCopy.noInfo, style = aboutText(theme.muted, theme, design.type.dialogBody))
            }
        }
    }
}

/**
 * One row per fact: its name, muted, in a column as wide as the widest name, and what it is
 * beside it. A long value takes a second line while the facts stay within [room] pixels
 * tall, earlier facts first, and ends in "…" past that. A row that starts beside the
 * screenshot, the top [besideHeight] of the list, leaves [besideWidth] free for it; a row
 * below it takes the whole width.
 */
@Composable
private fun FactList(facts: List<AboutFact>, theme: Theme, room: Int, besideWidth: Dp, besideHeight: Dp, modifier: Modifier) {
    if (facts.isEmpty()) {
        Spacer(modifier)
        return
    }
    val size = theme.style.type.sideRow
    val labelStyle = aboutText(theme.muted, theme, size)
    val valueStyle = aboutText(theme.onBackground, theme, size, bold = true)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidth = remember(facts, labelStyle) {
        facts.maxOf { measurer.measure(it.label, labelStyle, maxLines = 1, softWrap = false).size.width }
    }
    BoxWithConstraints(modifier) {
        val gapPx = with(density) { FACT_GAP.roundToPx() }
        val besidePx = with(density) { besideWidth.roundToPx() }
        val besideTall = with(density) { besideHeight.roundToPx() }
        val fullWidth = (constraints.maxWidth - labelWidth - with(density) { LABEL_GAP.roundToPx() }).coerceAtLeast(0)
        // Each fact's lines, and whether it sits beside the screenshot, laid out top to bottom.
        val laid = remember(facts, valueStyle, fullWidth, besidePx, besideTall, room) {
            val line = measurer.measure(LINE_PROBE, valueStyle, maxLines = 1).size.height
            var tall = facts.size * line + (facts.size - 1) * gapPx
            var top = 0
            facts.map { fact ->
                val beside = top < besideTall
                val width = if (beside) (fullWidth - besidePx).coerceAtLeast(0) else fullWidth
                val wrap = measurer.measure(fact.value, valueStyle, constraints = Constraints(maxWidth = width), maxLines = FACT_MAX_LINES).lineCount > 1
                val lines = if (wrap && tall + line <= room) 2.also { tall += line } else 1
                top += lines * line + gapPx
                lines to beside
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(FACT_GAP)) {
            facts.forEachIndexed { index, fact ->
                val (lines, beside) = laid[index]
                Row(Modifier.padding(end = if (beside) besideWidth else 0.dp), horizontalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
                    BasicText(fact.label, Modifier.width(with(density) { labelWidth.toDp() }), maxLines = 1, softWrap = false, style = labelStyle)
                    BasicText(fact.value, Modifier.weight(1f), maxLines = lines, overflow = TextOverflow.Ellipsis, style = valueStyle)
                }
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
private val LABEL_GAP = 16.dp

/**
 * A fact's value takes at most two lines, and only while the summary keeps this many. On
 * the top screen with a screenshot that is about three two-line facts of five.
 */
private const val FACT_MAX_LINES = 2
private const val SUMMARY_MIN_LINES = 2

/** Measured for one line's height. */
private const val LINE_PROBE = "Ag"
private const val COVER_SHARE = 0.3f

/** A screenshot takes up to this much of the card's width, and of its height. */
private const val SHOT_SHARE = 0.4f
private const val SHOT_HEIGHT = 0.5f
private const val COVER_PX = 1024
private const val SCREEN_PX = 1024
