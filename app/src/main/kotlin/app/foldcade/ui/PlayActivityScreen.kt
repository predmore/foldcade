package app.foldcade.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.R
import app.foldcade.playName
import app.foldcade.language.MARK_CONSOLE
import app.foldcade.language.PickerModel
import app.foldcade.language.PlayActivityPage
import app.foldcade.language.PlayCopy
import app.foldcade.language.PlayWeek
import app.foldcade.language.PromptKey
import app.foldcade.language.Row as LanguageRow
import app.foldcade.language.promptKeyOf
import app.foldcade.language.Theme
import app.foldcade.language.playDaySessions
import app.foldcade.language.playDuration
import app.foldcade.language.playWeek
import java.time.ZoneId
import java.util.Date

/** The week [weeksBack] weeks ago, read again whenever a play session changes. */
@Composable
private fun rememberPlayWeek(app: FoldcadeApp, weeksBack: Int): PlayWeek {
    val stamp = app.plays.stamp
    val dateStyle = rememberDateStyle(app)
    return remember(stamp, weeksBack, dateStyle) {
        playWeek(app.plays.spans(), System.currentTimeMillis(), ZoneId.systemDefault(), dateStyle, weeksBack)
    }
}

/**
 * A time of day as the device writes it, 12- or 24-hour. The page's one clock, so it
 * can follow the launcher's date and time style wherever that is decided.
 */
@Composable
private fun rememberClockFormat(): (Long) -> String {
    val context = LocalContext.current
    return remember(context) {
        val format = DateFormat.getTimeFormat(context)
        val clock: (Long) -> String = { millis -> format.format(Date(millis)) }
        clock
    }
}

/**
 * When and what was played, seven days at a time: one row per day across the
 * hours, the day's total beside it, and the most played games beside that. Up
 * and down move a cursor over the days; A lists a day's sessions in place of the
 * most played, where a held X removes one. It covers the screen L1 opened it
 * on, as Settings does.
 */
@Composable
internal fun PlayActivityScreen(app: FoldcadeApp, page: PlayActivityPage) {
    val design = LocalFoldTheme.current.theme.style
    val theme = LocalFoldTheme.current.theme
    val motion = theme.motion
    val scale = motion.animatorScale(LocalContext.current.contentResolver)
    val week = rememberPlayWeek(app, page.weeksBack)
    val listing = presence(page.takeIf { it.sessions != null }, motion.durationIsland, scale)
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
                    .clip(theme.style.shape.control())
                    .background(theme.surface)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        app.shell.closePlayActivity()
                    }
                    .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PromptImage(
                    promptGlyph(promptKeyOf(app.shell.model.faceMap.backKey) ?: PromptKey.FaceB),
                    32.dp,
                )
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
            val days = rememberSelectionSlots()
            Column(
                Modifier
                    .weight(0.66f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp))
                    .background(theme.surface)
                    .padding(24.dp)
                    // The ring is on the days while they take the D-pad; an open list keeps its day lit.
                    .selectionHighlight(days, page.day, ring = page.sessions == null, scale = scale),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                HourScale(theme)
                week.days.forEachIndexed { index, day ->
                    Row(
                        Modifier.selectionSlot(days, index).padding(horizontal = DAY_INSET, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            day.label,
                            modifier = Modifier.width(DAY_LABEL),
                            style = playText(if (index == page.day) theme.onBackground else theme.muted, theme, LocalFoldTheme.current.theme.style.type.sideRow),
                        )
                        DayTrack(day.blocks, theme, Modifier.weight(1f).height(22.dp))
                        // Every day says its total, one without play too, so today reads like the rest.
                        BasicText(
                            playDuration(day.totalMillis),
                            modifier = Modifier.width(DAY_TOTAL),
                            style = playText(if (day.totalMillis > 0L) theme.onBackground else theme.muted, theme, LocalFoldTheme.current.theme.style.type.sideRow)
                                .copy(textAlign = TextAlign.End),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .weight(0.34f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp))
                    .background(theme.surface),
            ) {
                // The most played give way to the day's sessions, and come back after.
                Box(Modifier.fillMaxSize().sinking { listing.progress.value }) { MostPlayed(app, week, theme) }
                listing.shown?.let { shown ->
                    Box(Modifier.fillMaxSize().rise { listing.progress.value }) {
                        DaySessions(app, shown, week.days.getOrNull(shown.day)?.label.orEmpty(), theme, scale)
                    }
                }
            }
        }
    }
}

/** The games played most this week: each one's cover, name, and time. */
@Composable
private fun MostPlayed(app: FoldcadeApp, week: PlayWeek, theme: Theme) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BasicText(PlayCopy.mostPlayed, style = playText(theme.muted, theme, LocalFoldTheme.current.theme.style.type.sideRow))
        if (week.top.isEmpty()) {
            BasicText(PlayCopy.nothing, style = playText(theme.muted, theme, LocalFoldTheme.current.theme.style.type.sideRow))
        }
        val current = week.top.map { (gameId, _) -> gameId to app.shell.playTitle(gameId) }
        // A name a catalog still has is kept, so the row still reads once the game is gone.
        LaunchedEffect(current) {
            current.forEach { (gameId, title) -> if (title != null) app.plays.rememberName(gameId, title) }
        }
        week.top.forEachIndexed { index, (gameId, millis) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayCover(app, gameId, TOP_COVER)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(
                        playName(gameId, current[index].second, app.plays.name(gameId)),
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

/**
 * The open day's sessions, first started first: the game, when it ran, and its time.
 * One highlight glides over them; a held X fills it and takes the session out.
 */
@Composable
private fun DaySessions(app: FoldcadeApp, page: PlayActivityPage, dayLabel: String, theme: Theme, scale: Float) {
    val stamp = app.plays.stamp
    val known = remember(stamp, page.weeksBack, page.day) {
        playDaySessions(app.plays.spans(), System.currentTimeMillis(), ZoneId.systemDefault(), page.weeksBack, page.day)
            .associateBy { it.sessionId }
    }
    val listed = page.sessions.orEmpty().mapNotNull { known[it] }
    val clock = rememberClockFormat()
    val model = app.shell.model
    val reach = ringReach()
    val slots = rememberSelectionSlots()
    val scroll = rememberScrollState()
    val focused = page.sessions?.getOrNull(page.session)
    // The card's own margin holds the focus ring's reach, so the rows are as wide as the most played.
    Column(Modifier.fillMaxSize().padding(horizontal = (CARD_PAD - reach).coerceAtLeast(0.dp), vertical = CARD_PAD - DAY_INSET)) {
        Row(Modifier.padding(horizontal = reach, vertical = DAY_INSET), verticalAlignment = Alignment.CenterVertically) {
            BasicText(PlayCopy.sessions, modifier = Modifier.weight(1f), style = playText(theme.muted, theme, LocalFoldTheme.current.theme.style.type.sideRow))
            BasicText(dayLabel, style = playText(theme.onBackground, theme, LocalFoldTheme.current.theme.style.type.sideRow, bold = true))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .menuFade(scroll)
                .verticalScroll(scroll)
                .padding(reach)
                .selectionHighlight(slots, listed.indexOfFirst { it.sessionId == focused }.takeIf { it >= 0 }, ring = true, scale = scale),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listed.forEachIndexed { index, session ->
                key(session.sessionId) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .selectionSlot(slots, index)
                            .keepInView(session.sessionId == focused)
                            .then(removeFeedback(model, session.sessionId, theme, scale))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PlayCover(app, session.gameId, SESSION_COVER)
                            BasicText(
                                playName(session.gameId, app.shell.playTitle(session.gameId), app.plays.name(session.gameId)),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = playText(theme.onBackground, theme, LocalFoldTheme.current.theme.style.type.sideRow, bold = true),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(
                                "${clock(session.startMillis)} – ${clock(session.endMillis)}",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = playText(theme.muted, theme, LocalFoldTheme.current.theme.style.type.hint),
                            )
                            BasicText(playDuration(session.activeMillis), style = playText(theme.onBackground, theme, LocalFoldTheme.current.theme.style.type.hint, bold = true))
                        }
                    }
                }
            }
        }
    }
}

/** A held X fills the session it removes; letting go early shakes it, as Delete download does. */
@Composable
private fun removeFeedback(model: PickerModel, sessionId: String, theme: Theme, scale: Float): Modifier {
    val motion = theme.motion
    val held = model.deleteHold?.takeIf { it.row == LanguageRow.RemoveSession && it.gameId == sessionId }
    val shake = remember { Animatable(0f) }
    // The hold is gone by the time the shake is asked for, so the row remembers it was the one held.
    var wasHeld by remember { mutableStateOf(false) }
    LaunchedEffect(held != null) { if (held != null) wasHeld = true }
    LaunchedEffect(model.deleteShake) {
        if (model.deleteShake == 0 || !wasHeld) return@LaunchedEffect
        wasHeld = false
        shake.snapTo(-8f)
        shake.animateTo(8f, motion.playful(motion.durationShort, scale))
        shake.animateTo(0f, motion.playful(motion.durationShort, scale))
    }
    val progress = if (held != null) model.deleteProgress else 0f
    return Modifier.graphicsLayer { translationX = shake.value }.drawBehind {
        if (progress <= 0f) return@drawBehind
        drawRoundRect(
            theme.focus.copy(alpha = 0.3f),
            size = Size(size.width * motion.holdFill(progress), size.height),
            cornerRadius = CornerRadius(theme.style.shape.highlightCornerDp.dp.toPx()),
        )
    }
}

/**
 * A game's square cover, found as the grid finds its tile's. Without art, an app's
 * themed object or launcher icon, else its platform's mark.
 */
@Composable
private fun PlayCover(app: FoldcadeApp, gameId: String, size: Dp) {
    val theme = LocalFoldTheme.current.theme
    val shell = app.shell
    val game = shell.findGame(gameId)
    val appObject = appGlyph(game.androidPackage)
    val art = key(gameId) {
        val found = rememberArt(app, shell.artRecord(game), shell.artQuery(game), automatic = appObject == null)
        found?.let { rememberCoverPainter(app, it.square ?: it.cover, TILE_PX)?.painter }
    }
    val icon = if (art == null && appObject == null) launcherIcon(game.androidPackage) else null
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * theme.iconRadius))
            .background(theme.onBackground.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            art != null -> Image(art, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            appObject != null -> MarkIcon(appObject, MARK_FILL)
            icon != null -> LauncherImage(icon, contentDescription = null, modifier = Modifier.fillMaxSize(MARK_FILL))
            else -> MarkIcon(game.mark ?: MARK_CONSOLE, MARK_FILL)
        }
    }
}

/** Covers: four of the most played still fit the page's height. */
private val TOP_COVER = 48.dp
private val SESSION_COVER = 28.dp

/** How much of a cover's box a mark or an icon fills, so it does not touch the corners. */
private const val MARK_FILL = 0.8f

/** The cards' margin, and the room around a day's row inside its highlight. */
private val CARD_PAD = 24.dp
private val DAY_INSET = 10.dp
private val DAY_LABEL = 56.dp
private val DAY_TOTAL = 88.dp

/** 6, 12, and 18 over the day rows, where those hours fall on the track. */
@Composable
private fun HourScale(theme: Theme) {
    Row(Modifier.padding(horizontal = DAY_INSET), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(DAY_LABEL))
        Box(Modifier.weight(1f).height(20.dp)) {
            listOf(6, 12, 18).forEach { hour ->
                Box(Modifier.fillMaxWidth(hour / 24f), contentAlignment = Alignment.CenterEnd) {
                    BasicText("$hour", style = playText(theme.muted, theme, LocalFoldTheme.current.theme.style.type.hint))
                }
            }
        }
        Spacer(Modifier.width(DAY_TOTAL))
    }
}

/** One day across its hours. Each stretch played is a bar in the focus color. */
@Composable
private fun DayTrack(blocks: List<ClosedFloatingPointRange<Float>>, theme: Theme, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            // Half as round as the theme's other controls, so the sessions read as bars.
            val radius = CornerRadius(size.height * theme.style.shape.round / 2f, size.height * theme.style.shape.round / 2f)
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
    TextStyle(color = color, fontSize = size, fontFamily = theme.font, fontWeight = if (bold) theme.style.type.strongWeight else theme.style.type.bodyWeight)
