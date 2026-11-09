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
import androidx.compose.foundation.layout.widthIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.R
import app.foldcade.playName
import app.foldcade.language.DateStyle
import app.foldcade.language.MARK_CONSOLE
import app.foldcade.language.PLAY_WEEK_DAYS
import app.foldcade.language.PickerModel
import app.foldcade.language.PlayActivityPage
import app.foldcade.language.PlayCopy
import app.foldcade.language.PlayHistory
import app.foldcade.language.PlayWeek
import app.foldcade.language.PlayZoom
import app.foldcade.language.PromptKey
import app.foldcade.language.Row as LanguageRow
import app.foldcade.language.promptKeyOf
import app.foldcade.language.Theme
import app.foldcade.language.actionFaces
import app.foldcade.language.playDayReadout
import app.foldcade.language.playDuration
import app.foldcade.language.playDayTotal
import app.foldcade.language.playMarkRange
import app.foldcade.language.playMonthGrid
import app.foldcade.language.playMonthReadout
import app.foldcade.language.playPeriodEmpty
import app.foldcade.language.playPeriodLabel
import app.foldcade.language.playPeriodReach
import app.foldcade.language.playYearWindow
import app.foldcade.language.playStreak
import app.foldcade.language.playWeekStart
import app.foldcade.language.playWeekdays
import app.foldcade.language.playWhen
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateText
import java.util.Date

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
 * What the chart shows: a zoom and the first day of its period. A change of either slides or zooms it.
 * Immutable, so the cards that read it skip while a held D-pad moves the cursor inside one period.
 */
@Immutable
private data class ChartKey(val zoom: PlayZoom, val start: LocalDate)

private fun chartKey(zoom: PlayZoom, day: LocalDate, style: DateStyle): ChartKey = ChartKey(
    zoom,
    when (zoom) {
        PlayZoom.Week -> playWeekStart(day, style.firstDayOfWeek)
        PlayZoom.Month -> day.withDayOfMonth(1)
        PlayZoom.Year -> day.withDayOfYear(1)
    },
)

/** The last day of [key]'s period. */
private fun ChartKey.end(): LocalDate = when (zoom) {
    PlayZoom.Week -> start.plusDays(PLAY_WEEK_DAYS - 1L)
    PlayZoom.Month -> start.withDayOfMonth(start.lengthOfMonth())
    PlayZoom.Year -> start.withDayOfYear(start.lengthOfYear())
}

/**
 * When and what was played, at three zooms over one cursor date: a week of day rows across
 * the hours, a month of day cells shaded by time, and a year of month bars. L2 and R2 zoom;
 * the D-pad moves the date and the chart slides to the next period at its edge. Beside it,
 * the period's most played, or on a game's page its lifetime facts. A lists a day's sessions
 * there, where a held X removes one; Y on a game lists the games its time can come from. It
 * covers the screen L1 opened it on, as Settings does.
 */
@Composable
internal fun PlayActivityScreen(app: FoldcadeApp, page: PlayActivityPage) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val motion = theme.motion
    val scale = motion.animatorScale(LocalContext.current.contentResolver)
    // The tile the game shows as now: copies folding while the page is open give it a new id.
    val game = page.game?.let(app.shell::tileOf)
    val history = app.playHistory(game)
    val style = rememberDateStyle(app)
    val today = page.calendar?.today ?: app.playToday()
    val day = page.cursor
    val chart = chartKey(page.zoom, day, style)
    val listing = presence(page.takeIf { it.sessions != null }, motion.durationIsland, scale)
    val moving = presence(page.takeIf { it.moving && it.sessions == null }, motion.durationIsland, scale)
    // Each minute, a set clock, or a new time zone: today may be another day, so the bounds follow.
    val status by app.deviceStatus.status.collectAsStateWithLifecycle()
    LaunchedEffect(status.minute, status.time) { app.shell.notePlayClock() }
    val first = page.calendar?.first ?: today
    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 40.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The pill takes what the zooms leave, and a long game name ends in "…".
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .clip(design.shape.control())
                        .background(theme.surface)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            app.shell.closePlayActivity()
                        }
                        .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PromptImage(promptGlyph(promptKeyOf(app.shell.model.faceMap.backKey) ?: PromptKey.FaceB), 32.dp)
                    // A game's page is named for the game, as its Y menu was.
                    BasicText(
                        if (game == null) PlayCopy.activity else playName(game, app.shell.playTitle(game), app.plays.name(game)),
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = playText(theme.onBackground, theme, design.type.heroTitle, bold = true, display = true),
                    )
                }
            }
            Spacer(Modifier.width(24.dp))
            // All games' tabs, as L2 and R2 step them there.
            PillCarousel(
                labels = remember { PlayZoom.entries.map(PlayCopy::zoom) },
                chosen = page.zoom.ordinal,
                style = playText(theme.muted, theme, design.type.sideRow),
                scale = scale,
                onTap = app.shell::tapPlayZoom,
                fills = false,
                wraps = false,
            )
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                Modifier
                    .weight(CHART_SHARE)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp))
                    .background(theme.surface)
                    .padding(horizontal = CARD_PAD, vertical = CHART_PAD),
                // Room for the focus ring's glow between the last row and the readout.
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // The period and its total crossfade as the chart moves.
                ChartSwap(chart, scale, Modifier.fillMaxWidth(), travel = 0f) { shown ->
                    PeriodLine(history, shown, page.calendar, today, style, theme)
                }
                // The ring is on the chart while it takes the D-pad; an open list keeps its day lit.
                val ring = page.sessions == null && !page.moving
                ChartSwap(chart, scale, Modifier.weight(1f).fillMaxWidth()) { shown ->
                    when (shown.zoom) {
                        PlayZoom.Week -> WeekChart(history, shown.start, day, first, today, ring, style, theme, scale)
                        PlayZoom.Month -> MonthChart(history, shown.start, day, first, today, ring, style, theme, scale)
                        PlayZoom.Year -> YearChart(history, shown.start.year, day, first, today, ring, style, theme, scale)
                    }
                }
                Readout(history, chart, day, today, quietPeriod = game != null, style, theme)
            }
            Box(
                Modifier
                    .weight(1f - CHART_SHARE)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(design.metrics.cardCornerDp.dp))
                    .background(theme.surface),
            ) {
                // The card's own content gives way to a day's sessions or to Move time from, and comes back after.
                // One after the other, never both at once: the card leaving is gone by halfway, and the one arriving starts there.
                Box(Modifier.fillMaxSize().sinking { firstHalf(maxOf(listing.progress.value, moving.progress.value)) }) {
                    if (game != null) GameFacts(app, history, game, today, style, theme, page.sources.isNotEmpty())
                    else MostPlayed(app, history, chart, today, style, theme)
                }
                listing.shown?.let { shown ->
                    Box(Modifier.fillMaxSize().rise { secondHalf(listing.progress.value) }) {
                        DaySessions(app, history, shown, style, theme, scale)
                    }
                }
                moving.shown?.let { shown ->
                    if (game != null) Box(Modifier.fillMaxSize().rise { secondHalf(moving.progress.value) }) { GameSources(app, shown, game, theme, scale) }
                }
            }
        }
    }
}

/** A swap's first half: what leaves is gone by the middle. */
private fun firstHalf(progress: Float): Float = (progress * 2f).coerceIn(0f, 1f)

/** A swap's second half: what arrives starts at the middle. */
private fun secondHalf(progress: Float): Float = (progress * 2f - 1f).coerceIn(0f, 1f)

/**
 * [content] for [key], sliding and fading to the next key's as it changes: along the timeline
 * for a new period, or growing or shrinking a little for a new zoom, in going closer. With
 * [travel] 0 it only crossfades. The key it leaves keeps drawing until it is gone.
 */
@Composable
private fun ChartSwap(key: ChartKey, scale: Float, modifier: Modifier, travel: Float = 1f, content: @Composable (ChartKey) -> Unit) {
    val theme = LocalFoldTheme.current.theme
    val motion = theme.motion
    val play = theme.style.play
    // Plain holders: written and read in the same pass, as presence does.
    val shown = remember { arrayOf(key) }
    val leaving = remember { arrayOfNulls<ChartKey>(1) }
    if (shown[0] != key) {
        leaving[0] = shown[0]
        shown[0] = key
    }
    val first = remember { arrayOf(true) }
    val progress = remember(key) { Animatable(if (first[0]) 1f else 0f) }
    first[0] = false
    LaunchedEffect(key) { progress.animateTo(1f, motion.arrive(motion.durationIsland, scale)) }
    val running by remember(progress) { derivedStateOf { progress.value < 1f } }
    val old = leaving[0]?.takeIf { running }
    if (!running) leaving[0] = null
    // Forward in time comes in from the right; a closer zoom grows in from a little smaller.
    val sign = old?.let { from ->
        if (from.zoom != key.zoom) (if (key.zoom < from.zoom) 1f else -1f) else (if (key.start > from.start) 1f else -1f)
    } ?: 0f
    val zooming = old != null && old.zoom != key.zoom
    Box(modifier) {
        if (old != null) {
            key(old) {
                Box(
                    // The leaving copy takes the room the arriving one sizes.
                    Modifier.matchParentSize().graphicsLayer {
                        val t = progress.value
                        alpha = (1f - t * 2f).coerceIn(0f, 1f)
                        if (zooming) {
                            val by = 1f + sign * play.zoomScale * t * travel
                            scaleX = by
                            scaleY = by
                        } else {
                            translationX = -sign * t * play.slideDp.dp.toPx() * travel
                        }
                    },
                ) { content(old) }
            }
        }
        key(key) {
            Box(
                Modifier.fillMaxWidth().graphicsLayer {
                    val t = progress.value
                    alpha = t
                    if (zooming) {
                        val by = 1f - sign * play.zoomScale * (1f - t) * travel
                        scaleX = by
                        scaleY = by
                    } else {
                        translationX = sign * (1f - t) * play.slideDp.dp.toPx() * travel
                    }
                },
            ) { content(key) }
        }
    }
}

/**
 * The chart card's top line: the period between ‹ and ›, each lit while the D-pad can reach a
 * period that way and dim at the first day played and at today, then its time on the right.
 * Year shows every year played instead, each with its time, the shown one lit.
 */
@Composable
private fun PeriodLine(history: PlayHistory, key: ChartKey, calendar: app.foldcade.language.PlayCalendar?, today: LocalDate, style: DateStyle, theme: Theme) {
    val type = theme.style.type
    if (key.zoom == PlayZoom.Year) {
        YearStrip(history, key.start.year, calendar, today, theme)
        return
    }
    val total = remember(history, key) { history.total(key.start, key.end()) }
    val (earlier, later) = playPeriodReach(calendar, key.start, key.end())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Chevron("‹", earlier, theme)
        BasicText(
            playPeriodLabel(key.zoom, key.start, today, style),
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = CHEVRON_GAP),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = playText(theme.muted, theme, type.heroMeta),
        )
        Chevron("›", later, theme)
        Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
        BasicText(playDuration(total), maxLines = 1, style = playText(theme.onBackground, theme, type.heroMeta, bold = true))
    }
}

/** ‹ or ›: lit when the D-pad can reach a period that way, dim at the bounds. */
@Composable
private fun Chevron(glyph: String, lit: Boolean, theme: Theme) {
    BasicText(
        glyph,
        maxLines = 1,
        style = playText(if (lit) theme.onBackground else theme.muted.copy(alpha = theme.style.play.futureAlpha), theme, theme.style.type.heroMeta, bold = true),
    )
}

/**
 * Year's top line: each year since the first one played with its time, the shown year lit.
 * It follows the cursor and never takes focus. With more years than fit, it shows those around
 * the shown one, and ‹ › say there are more, dim at the first year played and this one.
 */
@Composable
private fun YearStrip(history: PlayHistory, year: Int, calendar: app.foldcade.language.PlayCalendar?, today: LocalDate, theme: Theme) {
    val type = theme.style.type
    val first = calendar?.first?.year ?: today.year
    val last = today.year
    val measurer = rememberTextMeasurer()
    val label = playText(theme.muted, theme, type.hint)
    val lit = playText(theme.onBackground, theme, type.hint, bold = true)
    val texts = remember(history, first, last) { (first..last).associateWith { "$it · ${yearDuration(history.yearTotal(it))}" } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chevrons = with(density) { measurer.measure("‹›", playText(theme.muted, theme, type.heroMeta, bold = true)).size.width.toDp() + CHEVRON_GAP * 2 }
        val padPx = with(density) { YEAR_CHIP_PAD.toPx() }
        val gapPx = with(density) { YEAR_CHIP_GAP.toPx() }
        val room = with(density) { (maxWidth - chevrons).toPx() }
        val window = remember(texts, year, room) {
            playYearWindow(first, last, year, room, gapPx) { measurer.measure(texts.getValue(it), lit).size.width + padPx * 2 }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Chevron("‹", year > first, theme)
            Row(
                Modifier.weight(1f).padding(horizontal = CHEVRON_GAP),
                horizontalArrangement = Arrangement.spacedBy(YEAR_CHIP_GAP, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                window.forEach { shown ->
                    val on = shown == year
                    BasicText(
                        texts.getValue(shown),
                        maxLines = 1,
                        style = if (on) lit else label,
                        modifier = Modifier
                            .clip(theme.style.shape.control())
                            .drawBehind {
                                drawRoundRect(
                                    if (on) (theme.style.colors.progress ?: theme.focus).copy(alpha = theme.style.play.yearChipAlpha)
                                    else theme.onBackground.copy(alpha = theme.style.play.trackAlpha),
                                    cornerRadius = CornerRadius(size.height * theme.style.shape.round),
                                )
                            }
                            .padding(horizontal = YEAR_CHIP_PAD, vertical = 4.dp),
                    )
                }
            }
            Chevron("›", year < last, theme)
        }
    }
}

/** A year's time on its chip: whole hours once there are any, "–" for none. */
private fun yearDuration(millis: Long): String = if (millis <= 0L) PlayCopy.none else shortDuration(millis)

/**
 * What a hover would say about the cursor: its day or month, the time, and how many sessions or
 * days. On a game's page, whose side card keeps the lifetime facts, a period with no play says
 * so here instead.
 */
@Composable
private fun Readout(history: PlayHistory, key: ChartKey, day: LocalDate, today: LocalDate, quietPeriod: Boolean, style: DateStyle, theme: Theme) {
    val zoom = key.zoom
    val text = remember(history, key, day, style, quietPeriod, today) {
        if (quietPeriod && history.total(key.start, key.end()) <= 0L) {
            playPeriodEmpty(zoom, day, today, style)
        } else if (zoom == PlayZoom.Year) {
            val month = YearMonth.from(day)
            playMonthReadout(month.month, history.monthTotal(month), history.monthDaysPlayed(month), style)
        } else {
            playDayReadout(day, history.dayTotal(day), history.daySessions(day).size, style)
        }
    }
    BasicText(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = playText(theme.onBackground, theme, theme.style.type.sideRow))
}

/**
 * The week from [start]: one row per day across the hours, its total beside it. Days still to
 * come are drawn dim. The cursor's row wears the highlight.
 */
@Composable
private fun WeekChart(history: PlayHistory, start: LocalDate, day: LocalDate, first: LocalDate, today: LocalDate, ring: Boolean, style: DateStyle, theme: Theme, scale: Float) {
    val type = theme.style.type
    val week = remember(history, start, style) { history.week(start, style) }
    val slots = rememberSelectionSlots()
    val grown = rememberGrowIn(scale)
    Column(
        Modifier
            .fillMaxSize()
            .selectionHighlight(slots, week.days.indexOfFirst { it.date == day }.takeIf { it >= 0 }, ring = ring, scale = scale),
    ) {
        HourScale(theme)
        // The focus ring's reach, so a larger font's first row never wears its ring across the hours.
        Spacer(Modifier.height(ringReach() / 2))
        week.days.forEachIndexed { index, row ->
            // Days still to come and days before the first play are drawn dim: the cursor reaches neither.
            val future = row.date > today || row.date < first
            // The seven rows share what the card has, so a larger font still fits all of them.
            Row(
                Modifier
                    .weight(1f)
                    .selectionSlot(slots, index)
                    .padding(horizontal = DAY_INSET)
                    .graphicsLayer { if (future) alpha = theme.style.play.futureAlpha },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    row.label,
                    modifier = Modifier.width(DAY_LABEL),
                    style = playText(if (row.date == day) theme.onBackground else theme.muted, theme, type.sideRow),
                )
                DayTrack(row.blocks, theme, Modifier.weight(1f).height(DAY_TRACK), grown)
                // Empty days stay quiet beside the days with play.
                BasicText(
                    playDayTotal(row.totalMillis),
                    modifier = Modifier.width(DAY_TOTAL),
                    style = playText(if (row.totalMillis > 0L) theme.onBackground else theme.muted, theme, type.sideRow).copy(textAlign = TextAlign.End),
                )
            }
        }
    }
}

/** 0 to 1 once, as a chart first draws, so its bars and cells ease in. */
@Composable
private fun rememberGrowIn(scale: Float): () -> Float {
    val motion = LocalFoldTheme.current.theme.motion
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, motion.arrive(motion.durationIsland, scale)) }
    return { grow.value }
}

/** One day's cell on the month calendar, as the canvas draws it. */
private class MonthCell(val date: LocalDate, val inMonth: Boolean, val level: Int, val numeral: String)

/**
 * The month from [start] as a calendar: weekday initials in the device's week order, then one
 * cell per day, shaded in four steps by its time against the player's own days. Today wears a
 * quiet ring; days still to come are dim and never focused. Drawn on one canvas, so a held
 * D-pad moves only the highlight.
 */
@Composable
private fun MonthChart(history: PlayHistory, start: LocalDate, day: LocalDate, first: LocalDate, today: LocalDate, ring: Boolean, style: DateStyle, theme: Theme, scale: Float) {
    val play = theme.style.play
    val type = theme.style.type
    val month = YearMonth.from(start)
    val cells = remember(history, month, style) {
        playMonthGrid(month, style.firstDayOfWeek).map { date ->
            MonthCell(date, YearMonth.from(date) == month, history.heat.level(history.dayTotal(date)), date.dayOfMonth.toString())
        }
    }
    val weekdays = remember(style) {
        playWeekdays(style.firstDayOfWeek).map { it.getDisplayName(DateText.NARROW_STANDALONE, style.locale) }
    }
    val slots = rememberSelectionSlots()
    val grown = rememberGrowIn(scale)
    val measurer = rememberTextMeasurer()
    var area by remember { mutableStateOf(IntSize.Zero) }
    val fill = theme.style.colors.progress ?: theme.focus
    val label = playText(theme.muted, theme, type.hint).copy(textAlign = TextAlign.Center)
    val numeral = playText(theme.onBackground, theme, type.hint)
    val strong = playText(theme.background, theme, type.hint, bold = true)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            weekdays.forEach { BasicText(it, modifier = Modifier.weight(1f), style = label) }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { area = it }
                .selectionHighlight(slots, cells.indexOfFirst { it.date == day && it.inMonth }.takeIf { it >= 0 }, ring = ring, scale = scale)
                .drawBehind {
                    val gap = CELL_GAP.toPx()
                    // Always six rows tall, so every month's cells are one size and the slide reads as one calendar.
                    val cellW = (size.width - gap * (PLAY_WEEK_DAYS - 1)) / PLAY_WEEK_DAYS
                    val cellH = (size.height - gap * (MONTH_ROWS - 1)) / MONTH_ROWS
                    val corner = CornerRadius(play.cellCornerDp.dp.toPx())
                    val t = grown()
                    cells.forEachIndexed { index, cell ->
                        if (!cell.inMonth) return@forEachIndexed
                        val col = index % PLAY_WEEK_DAYS
                        val row = index / PLAY_WEEK_DAYS
                        val at = Offset(col * (cellW + gap), row * (cellH + gap))
                        val box = Size(cellW, cellH)
                        val future = cell.date > today || cell.date < first
                        drawRoundRect(theme.onBackground.copy(alpha = play.cellAlpha * if (future) play.futureAlpha else 1f), at, box, corner)
                        if (cell.level > 0) drawRoundRect(fill.copy(alpha = play.heatAlpha(cell.level) * t), at, box, corner)
                        // A list open on this day, which has the D-pad instead: the cell keeps a soft mark, as Week's row does.
                        if (!ring && cell.date == day) drawRoundRect(theme.focus.copy(alpha = play.openedAlpha), at, box, corner, style = Stroke(play.todayDp.dp.toPx() * 2f))
                        if (cell.date == today) {
                            val stroke = play.todayDp.dp.toPx()
                            drawRoundRect(
                                theme.onBackground.copy(alpha = play.todayAlpha),
                                at + Offset(stroke / 2f, stroke / 2f),
                                Size(cellW - stroke, cellH - stroke),
                                CornerRadius((corner.x - stroke / 2f).coerceAtLeast(0f)),
                                style = Stroke(stroke),
                            )
                        }
                        val ink = when {
                            cell.level >= DARK_INK_LEVEL -> strong
                            future -> numeral.copy(color = theme.onBackground.copy(alpha = play.futureAlpha))
                            cell.level == 0 -> numeral.copy(color = theme.muted)
                            else -> numeral
                        }
                        val measured = measurer.measure(cell.numeral, ink)
                        drawText(measured, topLeft = at + Offset((cellW - measured.size.width) / 2f, (cellH - measured.size.height) / 2f))
                    }
                },
        )
    }
    // The highlight finds each day's cell where the canvas puts it.
    MonthSlots(slots, cells, area)
}

/** Each in-month day's cell rectangle, for the gliding highlight. */
@Composable
private fun MonthSlots(slots: SelectionSlots, cells: List<MonthCell>, size: IntSize) {
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { CELL_GAP.toPx() }
    LaunchedEffect(size, cells, gapPx) {
        if (size == IntSize.Zero) return@LaunchedEffect
        val cellW = (size.width - gapPx * (PLAY_WEEK_DAYS - 1)) / PLAY_WEEK_DAYS
        val cellH = (size.height - gapPx * (MONTH_ROWS - 1)) / MONTH_ROWS
        slots.rows.clear()
        cells.forEachIndexed { index, cell ->
            if (!cell.inMonth) return@forEachIndexed
            val col = index % PLAY_WEEK_DAYS
            val row = index / PLAY_WEEK_DAYS
            slots.rows[index] = Rect(Offset(col * (cellW + gapPx), row * (cellH + gapPx)), Size(cellW, cellH))
        }
    }
}

/**
 * The year as twelve month bars, each as tall as its time against the year's biggest month,
 * with its initial under it. The focused bar says its time over it. Months still to come
 * are dim stubs. Drawn on one canvas; the bars grow in as a year arrives.
 */
@Composable
private fun YearChart(history: PlayHistory, year: Int, day: LocalDate, first: LocalDate, today: LocalDate, ring: Boolean, style: DateStyle, theme: Theme, scale: Float) {
    val play = theme.style.play
    val type = theme.style.type
    val months = remember(history, year) { (1..12).map { history.monthTotal(YearMonth.of(year, it)) } }
    val initials = remember(style) { java.time.Month.entries.map { it.getDisplayName(DateText.NARROW_STANDALONE, style.locale) } }
    val most = months.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val focused = if (day.year == year) day.monthValue - 1 else null
    val slots = rememberSelectionSlots()
    val grown = rememberGrowIn(scale)
    val measurer = rememberTextMeasurer()
    var area by remember { mutableStateOf(IntSize.Zero) }
    val fill = theme.style.colors.progress ?: theme.focus
    val label = playText(theme.muted, theme, type.hint)
    val strongLabel = playText(theme.onBackground, theme, type.hint, bold = true)
    val value = playText(theme.onBackground, theme, type.hint, bold = true)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val valueHeight = with(density) { measurer.measure("0", value).size.height.toFloat() }
    val labelHeight = with(density) { measurer.measure("J", label).size.height.toFloat() }
    val gapPx = with(density) { BAR_GAP.toPx() }
    val padPx = with(density) { BAR_PAD.toPx() }
    LaunchedEffect(area, gapPx) {
        if (area == IntSize.Zero) return@LaunchedEffect
        val barW = (area.width - gapPx * 11) / 12f
        slots.rows.clear()
        (0 until 12).forEach { slots.rows[it] = Rect(Offset(it * (barW + gapPx), 0f), Size(barW, area.height.toFloat())) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { area = it }
            .selectionHighlight(slots, focused, ring = ring, scale = scale)
            .drawBehind {
                val barW = (size.width - gapPx * 11) / 12f
                val floor = size.height - padPx - labelHeight - padPx
                val roof = padPx + valueHeight + padPx
                val track = (floor - roof).coerceAtLeast(1f)
                val corner = CornerRadius(play.monthBarCornerDp.dp.toPx())
                val t = grown()
                months.forEachIndexed { index, millis ->
                    val left = index * (barW + gapPx)
                    val inset = barW * BAR_INSET
                    val month = YearMonth.of(year, index + 1)
                    val future = month > YearMonth.from(today) || month < YearMonth.from(first)
                    val stub = 3.dp.toPx()
                    if (millis > 0L) {
                        val height = (track * millis / most * t).coerceAtLeast(stub)
                        drawRoundRect(fill, Offset(left + inset, floor - height), Size(barW - inset * 2, height), corner)
                    } else {
                        drawRoundRect(
                            theme.onBackground.copy(alpha = play.emptyBarAlpha * if (future) play.futureAlpha else 1f),
                            Offset(left + inset, floor - stub), Size(barW - inset * 2, stub), CornerRadius(stub / 2f),
                        )
                    }
                    val initial = measurer.measure(initials[index], if (index == focused) strongLabel else label.copy(color = if (future) theme.muted.copy(alpha = play.futureAlpha) else theme.muted))
                    drawText(initial, topLeft = Offset(left + (barW - initial.size.width) / 2f, size.height - padPx - initial.size.height))
                    if (index == focused && millis > 0L) {
                        val height = (track * millis / most * t).coerceAtLeast(stub)
                        val text = measurer.measure(shortDuration(millis), value)
                        val x = (left + (barW - text.size.width) / 2f).coerceIn(0f, size.width - text.size.width)
                        drawText(text, topLeft = Offset(x, floor - height - padPx - text.size.height))
                    }
                }
            },
    )
}

/** A month's time over its bar, short enough for a narrow bar: whole hours once there are any. The readout has it exactly. */
private fun shortDuration(millis: Long): String {
    val hours = millis / 3_600_000L
    return if (hours >= 1L) "${hours}h" else playDuration(millis)
}

/**
 * The games played most in the chart's period: each one's cover, name, time, and a thin bar
 * of its share. With nothing in the period, it says so; with nothing ever, where play comes from.
 */
@Composable
private fun MostPlayed(app: FoldcadeApp, history: PlayHistory, key: ChartKey, today: LocalDate, style: DateStyle, theme: Theme) {
    val type = theme.style.type
    val play = theme.style.play
    val top = remember(history, key) { history.top(key.start, key.end()) }
    val total = remember(history, key) { history.total(key.start, key.end()).coerceAtLeast(1L) }
    Column(Modifier.fillMaxSize().padding(CARD_PAD), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BasicText(PlayCopy.mostPlayed, style = playText(theme.muted, theme, type.sideRow))
        if (top.isEmpty()) {
            if (history.first == null) {
                BasicText(PlayCopy.nothingYet, style = playText(theme.onBackground, theme, type.sideRow, bold = true))
                BasicText(PlayCopy.playSomething, style = playText(theme.muted, theme, type.hint))
            } else {
                BasicText(playPeriodEmpty(key.zoom, key.start, today, style), style = playText(theme.muted, theme, type.sideRow))
            }
        }
        val current = top.map { (gameId, _) -> gameId to app.shell.playTitle(gameId) }
        // A name a catalog still has is kept, so the row still reads once the game is gone.
        LaunchedEffect(current) {
            current.forEach { (gameId, title) -> if (title != null) app.plays.rememberName(gameId, title) }
        }
        val fill = theme.style.colors.progress ?: theme.focus
        // Two lines, so two games of one series still read apart; the card holds three that way.
        val nameLines = 2
        top.forEachIndexed { index, (gameId, millis) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayCover(app, gameId, TOP_COVER)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(
                        playName(gameId, current[index].second, app.plays.name(gameId)),
                        maxLines = nameLines,
                        overflow = TextOverflow.Ellipsis,
                        style = playText(theme.onBackground, theme, type.sideRow, bold = true),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val share = millis.toFloat() / total
                        Box(
                            Modifier
                                .weight(1f)
                                .height(play.shareBarDp.dp)
                                .drawBehind {
                                    val radius = CornerRadius(size.height / 2f)
                                    drawRoundRect(theme.onBackground.copy(alpha = play.trackAlpha), cornerRadius = radius)
                                    drawRoundRect(fill, size = Size((size.width * share).coerceAtLeast(size.height), size.height), cornerRadius = radius)
                                },
                        )
                        BasicText(playDuration(millis), maxLines = 1, style = playText(theme.muted, theme, type.hint))
                    }
                }
            }
        }
    }
}

/**
 * A game's lifetime on its page: its cover and all its time, as its tile and hero say it, then
 * first and last played, days played, sessions, the longest, and the best streak.
 */
@Composable
private fun GameFacts(app: FoldcadeApp, history: PlayHistory, game: String, today: LocalDate, style: DateStyle, theme: Theme, hasSources: Boolean) {
    val type = theme.style.type
    app.plays.stamp // Its time follows removals and moves.
    val shown = app.plays.shown(game)
    if (shown.activeMillis <= 0L && history.first == null) {
        NoPlayYet(app, game, theme, hasSources)
        return
    }
    val last = shown.lastPlayedMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(history.zone).toLocalDate() } ?: history.last
    val facts = listOf(
        PlayCopy.firstPlayed to playWhen(history.first, today, style),
        PlayCopy.lastPlayed to playWhen(last, today, style),
        PlayCopy.daysPlayed to if (history.daysPlayed > 0) history.daysPlayed.toString() else PlayCopy.none,
        PlayCopy.sessionsPlayed to if (history.sessions > 0) history.sessions.toString() else PlayCopy.none,
        PlayCopy.longest to if (history.longestMillis > 0L) playDuration(history.longestMillis) else PlayCopy.none,
        PlayCopy.bestStreak to playStreak(history.bestStreak),
    )
    Column(Modifier.fillMaxSize().padding(horizontal = FACT_PAD, vertical = CARD_PAD), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PlayCover(app, game, FACT_COVER)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(PlayCopy.timePlayed, maxLines = 1, style = playText(theme.muted, theme, type.hint))
                BasicText(
                    if (shown.activeMillis > 0L) playDuration(shown.activeMillis) else PlayCopy.nothingYet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = playText(theme.onBackground, theme, type.heroTitle, bold = true, display = true),
                )
            }
        }
        // Quiet labels and strong values, so a long date still fits beside its label at a larger font.
        facts.forEach { (name, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = playText(theme.muted, theme, type.hint))
                Spacer(Modifier.width(8.dp))
                BasicText(value, maxLines = 1, style = playText(theme.onBackground, theme, type.sideRow, bold = true))
            }
        }
    }
}

/**
 * A game's page before any play: its cover large, No play yet, and what comes next, with Y's
 * Move time when another game's time could be this one's.
 */
@Composable
private fun NoPlayYet(app: FoldcadeApp, game: String, theme: Theme, hasSources: Boolean) {
    val type = theme.style.type
    Column(
        Modifier.fillMaxSize().padding(horizontal = FACT_PAD, vertical = CARD_PAD),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlayCover(app, game, NO_PLAY_COVER)
        BasicText(PlayCopy.nothingYet, maxLines = 1, style = playText(theme.onBackground, theme, type.heroTitle, bold = true, display = true))
        BasicText(PlayCopy.firstSession, style = playText(theme.muted, theme, type.sideRow).copy(textAlign = TextAlign.Center))
        if (hasSources) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PromptImage(promptGlyph(actionFaces(app.shell.model.faceMap).optionsGlyph), 28.dp)
                BasicText(PlayCopy.moveTime, maxLines = 1, style = playText(theme.onBackground, theme, type.sideRow))
            }
        }
    }
}

/**
 * The open day's sessions, first started first: the game, when it ran, and its time.
 * One highlight glides over them; a held X fills it and takes the session out.
 */
@Composable
private fun DaySessions(app: FoldcadeApp, history: PlayHistory, page: PlayActivityPage, style: DateStyle, theme: Theme, scale: Float) {
    val type = theme.style.type
    val known = remember(history, page.cursor) { history.daySessions(page.cursor).associateBy { it.sessionId } }
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
            BasicText(PlayCopy.sessions, modifier = Modifier.weight(1f), style = playText(theme.muted, theme, type.sideRow))
            BasicText(style.weekdayMonthDay(page.cursor), maxLines = 1, style = playText(theme.onBackground, theme, type.sideRow, bold = true))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .menuFade(scroll, LIST_FADE)
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
                            .then(holdFeedback(model, LanguageRow.RemoveSession, session.sessionId, theme, scale))
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
                                style = playText(theme.onBackground, theme, type.sideRow, bold = true),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(
                                "${clock(session.startMillis)} – ${clock(session.endMillis)}",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = playText(theme.muted, theme, type.hint),
                            )
                            BasicText(playDuration(session.activeMillis), style = playText(theme.onBackground, theme, type.hint, bold = true))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Move time from, on a game's page: the game's own time on top, counting up as time moves onto
 * it, then the other games with play, likeliest first, each with its time, and Gone when no
 * library has it now. A held A fills the one in focus and moves its play here; it then reads
 * Moved here with what it moved, and A puts it back.
 */
@Composable
private fun GameSources(app: FoldcadeApp, page: PlayActivityPage, game: String, theme: Theme, scale: Float) {
    val stamp = app.plays.stamp
    val type = theme.style.type
    val motion = theme.motion
    val played = remember(stamp) { app.plays.played() }
    val model = app.shell.model
    val reach = ringReach()
    val slots = rememberSelectionSlots()
    val scroll = rememberScrollState()
    val focused = page.sources.getOrNull(page.source)
    val total = app.plays.shown(game).activeMillis
    val counted = remember { Animatable(total.toFloat()) }
    LaunchedEffect(total) { counted.animateTo(total.toFloat(), motion.arrive(motion.durationIsland * COUNT_SPAN, scale)) }
    // What each source had before it moved, so its row can say how much came here.
    val before = remember { mutableMapOf<String, Long>() }
    page.sources.forEach { source -> if (source !in page.moved) played[source]?.activeMillis?.takeIf { it > 0L }?.let { before[source] = it } }
    Column(Modifier.fillMaxSize().padding(horizontal = (CARD_PAD - reach).coerceAtLeast(0.dp), vertical = CARD_PAD - DAY_INSET)) {
        Row(
            Modifier.padding(horizontal = reach, vertical = DAY_INSET),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayCover(app, game, TOP_COVER)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(PlayCopy.timePlayed, maxLines = 1, style = playText(theme.muted, theme, type.hint))
                BasicText(
                    if (counted.value > 0f) playDuration(counted.value.toLong()) else PlayCopy.nothingYet,
                    maxLines = 1,
                    style = playText(theme.onBackground, theme, type.heroMeta, bold = true),
                )
            }
        }
        BasicText(
            PlayCopy.moveFrom,
            modifier = Modifier.padding(horizontal = reach, vertical = 4.dp),
            style = playText(theme.muted, theme, type.sideRow),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .menuFade(scroll, LIST_FADE)
                .verticalScroll(scroll)
                .padding(reach)
                .selectionHighlight(slots, page.source.takeIf { page.sources.isNotEmpty() }, ring = true, scale = scale),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            page.sources.forEachIndexed { index, source ->
                key(source) {
                    val moved = source in page.moved
                    val current = app.shell.playTitle(source)
                    val gone = app.shell.libraryTitle(source) == null
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectionSlot(slots, index)
                            .keepInView(source == focused)
                            .then(holdFeedback(model, LanguageRow.MovePlay, source, theme, scale))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PlayCover(app, source, SESSION_COVER)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Two lines, so the end of a long name, where copies differ, still shows.
                            BasicText(
                                playName(source, current, app.plays.name(source)),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = playText(theme.onBackground, theme, type.sideRow, bold = true),
                            )
                            BasicText(
                                when {
                                    moved -> before[source]?.let(PlayCopy::movedAmount) ?: PlayCopy.moved
                                    gone -> "${playDuration(played[source]?.activeMillis ?: 0L)} · ${PlayCopy.gone}"
                                    else -> playDuration(played[source]?.activeMillis ?: 0L)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = playText(if (moved) theme.focus else theme.muted, theme, type.hint, bold = moved),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A held key fills the [row] for [id] it acts on; letting go early shakes it. */
@Composable
private fun holdFeedback(model: PickerModel, row: LanguageRow, id: String, theme: Theme, scale: Float): Modifier {
    val motion = theme.motion
    val held = model.deleteHold?.takeIf { it.row == row && it.gameId == id }
    val shake = remember { Animatable(0f) }
    // The hold is gone by the time the shake is asked for, so the row remembers it was the one held.
    var wasHeld by remember { mutableStateOf(false) }
    LaunchedEffect(held != null) { if (held != null) wasHeld = true }
    LaunchedEffect(model.deleteShake) {
        if (model.deleteShake == 0 || !wasHeld) return@LaunchedEffect
        wasHeld = false
        shake.snapTo(-theme.style.motion.shakePx)
        shake.animateTo(theme.style.motion.shakePx, motion.playful(motion.durationShort, scale))
        shake.animateTo(0f, motion.playful(motion.durationShort, scale))
    }
    val progress = if (held != null) model.deleteProgress else 0f
    return Modifier.graphicsLayer { translationX = shake.value }.drawBehind {
        if (progress <= 0f) return@drawBehind
        drawRoundRect(
            theme.focus.copy(alpha = theme.style.menu.holdAlpha),
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
    // A game no library has now shows the cover kept when it was played.
    val gone = shell.libraryTitle(gameId) == null
    val art = key(gameId) {
        // Without one kept, as for history older than kept covers, the game's art is still looked for.
        val kept = if (gone) rememberKeptCover(app, gameId) else null
        if (kept != null) {
            kept
        } else {
            val found = rememberArt(app, shell.artRecord(game), shell.artQuery(game), automatic = appObject == null)
            val uri = found?.let { it.square ?: it.cover }
            val loaded = uri?.let { rememberCoverPainter(app, it, TILE_PX) }
            // History drawn before this release has no kept cover yet: this one is it.
            LaunchedEffect(uri, loaded != null) {
                if (uri != null && loaded != null && app.plays.keptCover(gameId) == null) app.keepPlayArt(gameId, uri)
            }
            loaded?.painter
        }
    }
    val icon = if (art == null && appObject == null) launcherIcon(game.androidPackage) else null
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * theme.iconRadius))
            .background(theme.onBackground.copy(alpha = theme.style.play.backingAlpha)),
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

/** The cover [PlayArt] kept for [gameId], or null when none was. */
@Composable
private fun rememberKeptCover(app: FoldcadeApp, gameId: String): androidx.compose.ui.graphics.painter.Painter? {
    val stamp = app.plays.stamp
    val file = remember(gameId, stamp) { app.plays.keptCover(gameId) } ?: return null
    val painter = coil3.compose.rememberAsyncImagePainter(file, app.images)
    val state by painter.state.collectAsState()
    // A kept file is the cover while it decodes too, so the page does not look for it elsewhere meanwhile.
    return painter.takeIf { state !is coil3.compose.AsyncImagePainter.State.Error }
}

/** Covers: four of the most played still fit the page's height. */
private val TOP_COVER = 44.dp
private val FACT_COVER = 64.dp
private val NO_PLAY_COVER = 112.dp

/** A list's fading edge: tall enough that a cut row reads as more below. */
private val LIST_FADE = 36.dp

/** The Move list's total counts up over this many island spans, slow enough to see. */
private const val COUNT_SPAN = 2

/** Year's strip: the chips' padding and gap, and the room either side of ‹ and ›. */
private val YEAR_CHIP_PAD = 10.dp
private val YEAR_CHIP_GAP = 6.dp
private val CHEVRON_GAP = 8.dp
private val SESSION_COVER = 28.dp

/** How much of a cover's box a mark or an icon fills, so it does not touch the corners. */
private const val MARK_FILL = 0.8f

/** The chart's share of the page's width; the card beside it has the rest. */
private const val CHART_SHARE = 0.62f

/** The cards' margin, and the room around a day's row inside its highlight. */
private val CARD_PAD = 24.dp
private val FACT_PAD = 20.dp
private val CHART_PAD = 18.dp
private val DAY_INSET = 10.dp
private val DAY_LABEL = 56.dp
private val DAY_TOTAL = 88.dp
private val DAY_TRACK = 20.dp

/** The month calendar: rows it is always sized for, and the gap between cells. */
private const val MONTH_ROWS = 6
private val CELL_GAP = 6.dp

/** From this heat level a day's numeral is drawn dark on its fill. */
private const val DARK_INK_LEVEL = 3

/** The year's bars: the gap between them, the room above and below, and the share of each column a bar leaves on each side. */
private val BAR_GAP = 6.dp
private val BAR_PAD = 6.dp
private const val BAR_INSET = 0.16f

/** 6, 12, and 18 over the day rows, where those hours fall on the track. */
@Composable
private fun HourScale(theme: Theme) {
    Row(Modifier.padding(horizontal = DAY_INSET), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(DAY_LABEL))
        Box(Modifier.weight(1f).height(18.dp)) {
            listOf(6, 12, 18).forEach { hour ->
                Box(Modifier.fillMaxWidth(hour / 24f), contentAlignment = Alignment.CenterEnd) {
                    BasicText("$hour", style = playText(theme.muted, theme, theme.style.type.hint))
                }
            }
        }
        Spacer(Modifier.width(DAY_TOTAL))
    }
}

/** One day across its hours. Each stretch played is a bar in the focus color, growing in with [grown]. */
@Composable
private fun DayTrack(blocks: List<ClosedFloatingPointRange<Float>>, theme: Theme, modifier: Modifier, grown: () -> Float = { 1f }) {
    Box(
        modifier.drawBehind {
            // Half as round as the theme's other controls, so the sessions read as bars.
            val radius = CornerRadius(size.height * theme.style.shape.round / 2f, size.height * theme.style.shape.round / 2f)
            val play = theme.style.play
            drawRoundRect(color = theme.onBackground.copy(alpha = play.trackAlpha), cornerRadius = radius)
            listOf(0.25f, 0.5f, 0.75f).forEach { mark ->
                val x = size.width * mark
                drawLine(theme.onBackground.copy(alpha = play.hourAlpha), Offset(x, 0f), Offset(x, size.height), strokeWidth = play.hourDp.dp.toPx())
            }
            val t = grown()
            blocks.forEach { block ->
                val mark = playMarkRange(block, size.width, play.markMinWidthDp.dp.toPx())
                drawRoundRect(
                    color = (theme.style.colors.progress ?: theme.focus).copy(alpha = t),
                    topLeft = Offset(mark.start, 0f),
                    size = Size(mark.endInclusive - mark.start, size.height),
                    cornerRadius = radius,
                )
            }
        },
    )
}

/** L1's first row: this week as seven bars, its total, and the page's name. */
@Composable
internal fun PlayStrip(app: FoldcadeApp, focused: Boolean, chord: PromptKey? = null, modifier: Modifier = Modifier) {
    val theme = LocalFoldTheme.current.theme
    val style = rememberDateStyle(app)
    val history = app.playHistory(null)
    val today = app.playToday()
    val week: PlayWeek = remember(history, today, style) { history.week(playWeekStart(today, style.firstDayOfWeek), style) }
    val most = week.days.maxOfOrNull { it.totalMillis }?.coerceAtLeast(1L) ?: 1L
    Column(modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InkIcon(R.drawable.ic_status_playtime, Modifier.size(24.dp))
            BasicText(
                PlayCopy.activity,
                modifier = Modifier.weight(1f),
                style = playText(if (focused) theme.onBackground else theme.muted, theme, theme.style.type.sideRow),
            )
            BasicText(playDuration(week.totalMillis), style = playText(if (focused) theme.focus else theme.onBackground, theme, theme.style.type.sideRow))
            ChordHint(chord, app.shell.model.held)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            week.days.forEach { day ->
                val share = day.totalMillis.toFloat() / most
                val future = day.date > today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .drawBehind {
                                val height = if (day.totalMillis > 0L) (size.height * share).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
                                val empty = theme.onBackground.copy(alpha = theme.style.play.emptyBarAlpha * if (future) theme.style.play.futureAlpha else 1f)
                                drawRoundRect(
                                    color = if (day.totalMillis > 0L) theme.style.colors.progress ?: theme.focus else empty,
                                    topLeft = Offset(0f, size.height - height),
                                    size = Size(size.width, height),
                                    cornerRadius = CornerRadius(theme.style.play.barCornerDp.dp.toPx(), theme.style.play.barCornerDp.dp.toPx()),
                                )
                            },
                    )
                    // Today's initial is lit, as the calendar marks today.
                    BasicText(day.label.take(1), style = playText(if (day.date == today) theme.onBackground else theme.muted, theme, theme.style.type.hint, bold = day.date == today))
                }
            }
        }
    }
}

private fun playText(color: Color, theme: Theme, size: androidx.compose.ui.unit.TextUnit, bold: Boolean = false, display: Boolean = false) =
    TextStyle(color = color, fontSize = size, fontFamily = if (display) theme.displayFont else theme.font, fontWeight = if (bold) theme.style.type.strongWeight else theme.style.type.bodyWeight)
