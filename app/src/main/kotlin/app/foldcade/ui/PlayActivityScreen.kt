package app.foldcade.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.R
import app.foldcade.language.HostScreen
import app.foldcade.language.PlayActivityPage
import app.foldcade.language.PlayCopy
import app.foldcade.language.PlayWeek
import app.foldcade.language.PromptKey
import app.foldcade.language.Theme
import app.foldcade.language.playDuration
import app.foldcade.language.playWeek
import java.time.ZoneId

/** The week [weeksBack] weeks ago, read again whenever a play session changes. */
@Composable
private fun rememberPlayWeek(app: FoldcadeApp, weeksBack: Int): PlayWeek {
    val stamp = app.plays.stamp
    return remember(stamp, weeksBack) {
        playWeek(app.plays.spans(), System.currentTimeMillis(), ZoneId.systemDefault(), weeksBack)
    }
}

/**
 * When and what was played, seven days at a time: one row per day across the
 * hours, the day's total beside it, and the most played games under it. It
 * covers the screen L1 opened it on, as Settings does.
 */
@Composable
internal fun PlayActivityScreen(app: FoldcadeApp, page: PlayActivityPage) {
    val design = LocalFoldTheme.current.theme.style
    val theme = LocalFoldTheme.current.theme
    val week = rememberPlayWeek(app, page.weeksBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(theme.surface)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        app.shell.closePlayActivity()
                    }
                    .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(themeIconPainter(R.drawable.ic_btn_b), contentDescription = null, modifier = Modifier.size(32.dp))
                BasicText(PlayCopy.activity, style = playText(theme.onBackground, theme, LocalFoldTheme.current.theme.style.type.heroTitle, bold = true))
            }
            Spacer(Modifier.weight(1f))
            BasicText(week.label, style = playText(theme.muted, theme, LocalFoldTheme.current.theme.style.type.heroMeta))
            Spacer(Modifier.width(16.dp))
            BasicText(playDuration(week.totalMillis), style = playText(theme.onBackground, theme, LocalFoldTheme.current.theme.style.type.heroMeta, bold = true))
        }
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                Modifier
                    .weight(0.66f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp))
                    .background(theme.surface)
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                HourScale(theme)
                week.days.forEach { day ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            day.label,
                            modifier = Modifier.width(56.dp),
                            style = playText(theme.muted, theme, LocalFoldTheme.current.theme.style.type.sideRow),
                        )
                        DayTrack(day.blocks, theme, Modifier.weight(1f).height(22.dp))
                        BasicText(
                            if (day.totalMillis > 0L) playDuration(day.totalMillis) else "",
                            modifier = Modifier.width(88.dp),
                            style = playText(theme.onBackground, theme, LocalFoldTheme.current.theme.style.type.sideRow).copy(textAlign = TextAlign.End),
                        )
                    }
                }
            }
            Column(
                Modifier
                    .weight(0.34f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp))
                    .background(theme.surface)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BasicText(PlayCopy.mostPlayed, style = playText(theme.muted, theme, LocalFoldTheme.current.theme.style.type.sideRow))
                if (week.top.isEmpty()) {
                    BasicText(PlayCopy.nothing, style = playText(theme.muted, theme, LocalFoldTheme.current.theme.style.type.sideRow))
                }
                week.top.forEach { (gameId, millis) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(
                            app.shell.playTitle(gameId) ?: gameId,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = playText(theme.onBackground, theme, LocalFoldTheme.current.theme.style.type.sideRow, bold = true),
                        )
                        BasicText(playDuration(millis), style = playText(theme.muted, theme, LocalFoldTheme.current.theme.style.type.hint))
                    }
                }
            }
        }
    }
}

/** 6, 12, and 18 over the day rows, where those hours fall on the track. */
@Composable
private fun HourScale(theme: Theme) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(56.dp))
        Box(Modifier.weight(1f).height(20.dp)) {
            listOf(6, 12, 18).forEach { hour ->
                Box(Modifier.fillMaxWidth(hour / 24f), contentAlignment = Alignment.CenterEnd) {
                    BasicText("$hour", style = playText(theme.muted, theme, LocalFoldTheme.current.theme.style.type.hint))
                }
            }
        }
        Spacer(Modifier.width(88.dp))
    }
}

/** One day across its hours. Each stretch played is a bar in the focus color. */
@Composable
private fun DayTrack(blocks: List<ClosedFloatingPointRange<Float>>, theme: Theme, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            val radius = CornerRadius(size.height / 4f, size.height / 4f)
            drawRoundRect(color = theme.onBackground.copy(alpha = 0.06f), cornerRadius = radius)
            listOf(0.25f, 0.5f, 0.75f).forEach { mark ->
                val x = size.width * mark
                drawLine(theme.onBackground.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            blocks.forEach { block ->
                val start = size.width * block.start
                // A short session still shows as a sliver.
                val width = (size.width * (block.endInclusive - block.start)).coerceAtLeast(2.dp.toPx())
                drawRoundRect(color = theme.focus, topLeft = Offset(start, 0f), size = Size(width, size.height), cornerRadius = radius)
            }
        },
    )
}

/** L1's first row: the past week as seven bars, the total, and the page's name. */
@Composable
internal fun PlayStrip(app: FoldcadeApp, focused: Boolean, chord: PromptKey? = null, modifier: Modifier = Modifier) {
    val theme = LocalFoldTheme.current.theme
    val week = rememberPlayWeek(app, 0)
    val most = week.days.maxOfOrNull { it.totalMillis }?.coerceAtLeast(1L) ?: 1L
    Column(modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = themeIconPainter(R.drawable.ic_status_playtime),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            BasicText(
                PlayCopy.activity,
                modifier = Modifier.weight(1f),
                style = playText(if (focused) theme.onBackground else theme.muted, theme, LocalFoldTheme.current.theme.style.type.sideRow),
            )
            BasicText(playDuration(week.totalMillis), style = playText(if (focused) theme.focus else theme.onBackground, theme, LocalFoldTheme.current.theme.style.type.sideRow))
            ChordHint(chord, app.shell.model.held)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            week.days.forEach { day ->
                val share = day.totalMillis.toFloat() / most
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .drawBehind {
                                val height = if (day.totalMillis > 0L) (size.height * share).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
                                drawRoundRect(
                                    color = if (day.totalMillis > 0L) theme.focus else theme.onBackground.copy(alpha = 0.12f),
                                    topLeft = Offset(0f, size.height - height),
                                    size = Size(size.width, height),
                                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                )
                            },
                    )
                    BasicText(day.label.take(1), style = playText(theme.muted, theme, LocalFoldTheme.current.theme.style.type.hint))
                }
            }
        }
    }
}

private fun playText(color: androidx.compose.ui.graphics.Color, theme: Theme, size: androidx.compose.ui.unit.TextUnit, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size, fontFamily = theme.font, fontWeight = if (bold) FontWeight.SemiBold else null)
