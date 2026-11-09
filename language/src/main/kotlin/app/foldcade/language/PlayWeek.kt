package app.foldcade.language

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle

/**
 * One stretch a game was in front, from launch history or usage access. Wall-clock milliseconds.
 * [sessionId] is its Foldcade launch, if any; one session can be several stretches.
 */
data class PlaySpan(val gameId: String, val startMillis: Long, val endMillis: Long, val sessionId: String = "")

/** One session on a day's list: from its first stretch's start to its last one's end, and the time in front. */
data class DaySession(
    val sessionId: String,
    val gameId: String,
    val startMillis: Long,
    val endMillis: Long,
    val activeMillis: Long,
)

/** One day's row on the schedule. [blocks] are fractions of the day, 0 to 1. */
data class PlayDay(
    val date: LocalDate,
    val label: String,
    val blocks: List<ClosedFloatingPointRange<Float>>,
    val totalMillis: Long,
)

/** Seven days, oldest first, with the games played most in them. */
data class PlayWeek(
    val days: List<PlayDay>,
    val totalMillis: Long,
    /** Game ids and their time in this week, most first. */
    val top: List<Pair<String, Long>>,
)

/**
 * The Play activity page: everything played, or one game's ([game]) history. Its cursor is
 * one date, [day], shared by the three zooms: a day row in Week, a day cell in Month, and
 * that day's month bar in Year. The shell fills [calendar] and [day] as the page opens and
 * keeps the calendar fresh as play changes; until then the D-pad waits.
 */
data class PlayActivityPage(
    val screen: HostScreen,
    /** Grid focus to restore when the page closes. */
    val grid: GridFocus = GridFocus(),
    val zoom: PlayZoom = PlayZoom.Week,
    /** The date in focus. Null until the shell opens the page on one. */
    val day: LocalDate? = null,
    /** How far the cursor may go. Null until the shell reads the history. */
    val calendar: PlayCalendar? = null,
    /** The open day's session ids, in the order listed. Null while the chart has the D-pad. */
    val sessions: List<String>? = null,
    /** The session in focus on the open day's list. */
    val session: Int = 0,
    /**
     * The game the page is about, opened from its Y menu's Play time: the chart shows only
     * its play, and Y lists [sources]. Null for everything played.
     */
    val game: String? = null,
    /** On a game's page, the other games with play that can move onto it, likeliest first. */
    val sources: List<String> = emptyList(),
    /** The source in focus. */
    val source: Int = 0,
    /** Sources moved onto [game] while the page is open. A on one again puts it back. */
    val moved: Set<String> = emptySet(),
    /** Y opened Move time from on a game's page: the D-pad is on [sources]. */
    val moving: Boolean = false,
) {
    /** The date in focus, today before the shell has opened the page on one. */
    val cursor: LocalDate get() = day ?: calendar?.today ?: LocalDate.EPOCH
}

/**
 * Opens [page] on its history: everything played opens on this week's today; a game opens on
 * the month it was last played, on that day, or on today when it never was.
 */
fun PlayActivityPage.opened(calendar: PlayCalendar): PlayActivityPage = copy(
    calendar = calendar,
    zoom = if (game != null) PlayZoom.Month else PlayZoom.Week,
    day = if (game != null) calendar.lastPlayed ?: calendar.today else calendar.today,
)

/** The history changed under the open page: its bounds move and the cursor stays inside them. */
fun PlayActivityPage.refreshed(calendar: PlayCalendar): PlayActivityPage =
    if (calendar == this.calendar) this else copy(calendar = calendar, day = calendar.clamp(cursor))

object PlayCopy {
    const val activity = "Play activity"
    const val mostPlayed = "Most played"
    const val nothing = "No play this week"
    const val nothingThen = "No play that week"
    const val nothingYet = "No play yet"
    /** Under [nothingYet]: where play comes from. */
    const val playSomething = "Play a game and its time shows here."
    fun empty(hasPlay: Boolean): String = if (hasPlay) nothing else nothingYet

    /** A month or a year with no play: "No play in November", "No play in 2024". */
    fun nothingIn(period: String): String = "No play in $period"

    /** The zooms, as their tabs name them. */
    const val week = "Week"
    const val month = "Month"
    const val year = "Year"
    fun zoom(zoom: PlayZoom): String = when (zoom) {
        PlayZoom.Week -> week
        PlayZoom.Month -> month
        PlayZoom.Year -> year
    }
    const val thisWeek = "This week"

    const val sessions = "Sessions"
    const val remove = "Remove"
    /** A on a month bar: that month's calendar. */
    const val open = "Open"
    /** Y on a game's page: the games its time can come from. */
    const val moveTime = "Move time"

    /** A game's Y menu row, and the top of its own Play activity. */
    const val playTime = "Play time"

    /** A game's page: its lifetime facts. */
    const val timePlayed = "Time played"
    const val firstPlayed = "First played"
    const val lastPlayed = "Last played"
    const val daysPlayed = "Days played"
    const val sessionsPlayed = "Sessions"
    const val longest = "Longest"
    const val bestStreak = "Best streak"
    /** A fact with nothing to say yet. */
    const val none = "–"
    const val today = "Today"
    const val yesterday = "Yesterday"
    const val noPlay = "No play"

    fun sessionCount(count: Int): String = if (count == 1) "1 session" else "$count sessions"
    fun dayCount(count: Int): String = if (count == 1) "1 day" else "$count days"

    /** Over the other games on a game's page. */
    const val moveFrom = "Move time from"

    /** Held A on one of them: the hint says to hold, as a tap does nothing. */
    const val holdToMove = "Hold to move"
    const val moveHere = "Move here"
    const val moved = "Moved here"
    const val undo = Copy.undo

    /** A game with play that no library has any more: its file was renamed, replaced, or removed. */
    const val gone = "Gone"

    /** Settings › System. Its dialog offers both ways. */
    const val history = "Play history"
    const val historyBody = "Export your play time to a file, or import one. Importing only adds to what’s here."
    const val export = "Export"
    const val import = "Import"
    const val exported = "Saved."
    fun exported(folder: String?, name: String?): String = when {
        !folder.isNullOrBlank() && !name.isNullOrBlank() -> "Saved $name to $folder."
        !folder.isNullOrBlank() -> "Saved to $folder."
        !name.isNullOrBlank() -> "Saved as $name."
        else -> exported
    }
    const val notSaved = "The file couldn’t be saved."
    const val updated = "Updated"

    /** A game's Play time row when it has none, but a gone game of its name has: its time, waiting. */
    fun found(millis: Long): String = "Found ${playDuration(millis)}"
    const val nothingNew = "Nothing new to add"
    const val notHistory = "That file isn’t a Foldcade play history."
    const val unreadable = "That file couldn’t be read."

    fun imported(sessions: Int): String = if (sessions == 1) "Added 1 session" else "Added $sessions sessions"

    /** A game that left every catalog and left nothing readable behind. */
    const val removedGame = "Removed game"
}

/** How many games the page lists beside the chart. */
const val PLAY_TOP_GAMES = 4

/** Days on a week. */
const val PLAY_WEEK_DAYS = 7

/** The week holding [date], named: "This week" for today's, else its first and last days. */
fun playWeekLabel(start: LocalDate, today: LocalDate, style: DateStyle): String {
    val end = start.plusDays(PLAY_WEEK_DAYS - 1L)
    if (today in start..end) return PlayCopy.thisWeek
    val last = if (end.year == today.year) style.monthDay(end) else style.monthDayYear(end)
    return "${style.monthDay(start)} – $last"
}

/** The chart card's top line: the week, "November 2026", or "2026". */
fun playPeriodLabel(zoom: PlayZoom, day: LocalDate, today: LocalDate, style: DateStyle): String = when (zoom) {
    PlayZoom.Week -> playWeekLabel(playWeekStart(day, style.firstDayOfWeek), today, style)
    PlayZoom.Month -> style.monthYear(day)
    PlayZoom.Year -> day.year.toString()
}

/** A month's own name, as the empty month and the year's readout say it: "November". */
fun playMonthName(month: java.time.Month, style: DateStyle): String =
    month.getDisplayName(TextStyle.FULL_STANDALONE, style.locale).replaceFirstChar { it.titlecase(style.locale) }

/** What the chart says with nothing in its period: a week, a month, or a year with no play. */
fun playPeriodEmpty(zoom: PlayZoom, day: LocalDate, today: LocalDate, style: DateStyle): String = when (zoom) {
    PlayZoom.Week -> if (today in playWeekStart(day, style.firstDayOfWeek).let { it..it.plusDays(PLAY_WEEK_DAYS - 1L) }) PlayCopy.nothing else PlayCopy.nothingThen
    PlayZoom.Month -> PlayCopy.nothingIn(if (day.year == today.year) playMonthName(day.month, style) else style.monthYear(day))
    PlayZoom.Year -> PlayCopy.nothingIn(day.year.toString())
}

/** The cursor's one line on a day: "Tue, Nov 4 · 2h 10m · 3 sessions", or "Tue, Nov 4 · No play". */
fun playDayReadout(date: LocalDate, millis: Long, sessions: Int, style: DateStyle): String {
    val name = style.weekdayMonthDay(date)
    if (millis <= 0L) return "$name · ${PlayCopy.noPlay}"
    return if (sessions > 0) "$name · ${playDuration(millis)} · ${PlayCopy.sessionCount(sessions)}" else "$name · ${playDuration(millis)}"
}

/** The cursor's one line on a month bar: "March · 12h 40m · 9 days", or "March · No play". */
fun playMonthReadout(month: java.time.Month, millis: Long, days: Int, style: DateStyle): String {
    val name = playMonthName(month, style)
    if (millis <= 0L) return "$name · ${PlayCopy.noPlay}"
    return "$name · ${playDuration(millis)} · ${PlayCopy.dayCount(days)}"
}

/** When a game was last played, as its page says it: Today, Yesterday, or the date. */
fun playWhen(date: LocalDate?, today: LocalDate, style: DateStyle): String = when (date) {
    null -> PlayCopy.none
    today -> PlayCopy.today
    today.minusDays(1) -> PlayCopy.yesterday
    else -> if (date.year == today.year) style.monthDay(date) else style.monthDayYear(date)
}

/** A run of days in a row, as Best streak says it: "–" under two. */
fun playStreak(days: Int): String = if (days < 2) PlayCopy.none else PlayCopy.dayCount(days)

/**
 * Time in front today, in [zone], for the L1 island. A stretch that began
 * before midnight counts only its part after it.
 */
fun playToday(spans: List<PlaySpan>, nowMillis: Long, zone: ZoneId): Long {
    val start = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    return spans.sumOf { (it.endMillis - maxOf(it.startMillis, start)).coerceAtLeast(0L) }
}

/** A length of play as hours and minutes: 6h 40m, 45m, 2h, <1m, or 0m. */
fun playDuration(millis: Long): String {
    if (millis <= 0L) return "0m"
    val minutes = millis / 60_000L
    if (minutes < 1L) return "<1m"
    val hours = minutes / 60L
    val rest = minutes % 60L
    return when {
        hours > 0L && rest > 0L -> "${hours}h ${rest}m"
        hours > 0L -> "${hours}h"
        else -> "${rest}m"
    }
}

/** Empty days stay quiet beside the days with play. Totals elsewhere still say 0m. */
fun playDayTotal(millis: Long): String = if (millis <= 0L) "–" else playDuration(millis)

/** A visible play mark, kept inside the track even for a short session just before midnight. */
fun playMarkRange(block: ClosedFloatingPointRange<Float>, trackWidth: Float, minWidth: Float): ClosedFloatingPointRange<Float> {
    val track = trackWidth.coerceAtLeast(0f)
    val from = block.start.coerceIn(0f, 1f) * track
    val to = block.endInclusive.coerceIn(0f, 1f) * track
    val width = (to - from).coerceAtLeast(minWidth.coerceAtLeast(0f)).coerceAtMost(track)
    val start = from.coerceIn(0f, track - width)
    return start..(start + width)
}

/**
 * Where a D-pad press takes the cursor in [zoom], before the calendar's bounds. Week: up and
 * down are a day, left and right a week. Month: four ways over the calendar, a day across
 * and a week down. Year: left and right are a month, landing on its first day. Null when
 * the press does not move the cursor there.
 */
fun playStep(zoom: PlayZoom, day: LocalDate, meaning: Meaning): LocalDate? = when (zoom) {
    PlayZoom.Week -> when (meaning) {
        Meaning.MoveUp -> day.minusDays(1)
        Meaning.MoveDown -> day.plusDays(1)
        Meaning.MoveLeft -> day.minusWeeks(1)
        Meaning.MoveRight -> day.plusWeeks(1)
        else -> null
    }
    PlayZoom.Month -> when (meaning) {
        Meaning.MoveUp -> day.minusWeeks(1)
        Meaning.MoveDown -> day.plusWeeks(1)
        Meaning.MoveLeft -> day.minusDays(1)
        Meaning.MoveRight -> day.plusDays(1)
        else -> null
    }
    PlayZoom.Year -> when (meaning) {
        Meaning.MoveLeft -> day.withDayOfMonth(1).minusMonths(1)
        Meaning.MoveRight -> day.withDayOfMonth(1).plusMonths(1)
        else -> null
    }
}

/**
 * The cursor after [meaning], kept between the first day played and today. A press that would
 * leave the bounds goes as far as they allow; one that cannot move at all (in Year, one that
 * stays in its month) returns null, so the page stays exactly as it was.
 */
fun playMove(page: PlayActivityPage, meaning: Meaning): LocalDate? {
    val calendar = page.calendar ?: return null
    val from = page.cursor
    val target = calendar.clamp(playStep(page.zoom, from, meaning) ?: return null)
    val same = if (page.zoom == PlayZoom.Year) YearMonth.from(target) == YearMonth.from(from) else target == from
    return target.takeUnless { same }
}

/**
 * The page at [zoom], keeping its date. From Year into Month, the cursor lands on the focused
 * month's last played day, or its first day when it had none.
 */
fun playZoomed(page: PlayActivityPage, zoom: PlayZoom): PlayActivityPage {
    val calendar = page.calendar ?: return page.copy(zoom = zoom)
    val day = if (page.zoom == PlayZoom.Year && zoom != PlayZoom.Year) {
        val month = YearMonth.from(page.cursor)
        calendar.lastPlayedIn(month) ?: calendar.clamp(month.atDay(1))
    } else page.cursor
    return page.copy(zoom = zoom, day = day)
}

/**
 * The D-pad moves the cursor date over the chart, and L2 and R2 zoom in and out around it.
 * A lists the focused day's sessions, which the shell reads from the log
 * ([Effect.OpenPlayDay]); on a month bar it opens that month. On a game's page, Y lists the
 * games its time can come from. B closes an open list, then the page; L1, R1, and Select
 * close the page and put the grid focus back. On a day's list, up and down move, the bottom
 * button removes the session in focus (the shell has it held first), and B goes back.
 * Held keys repeat only the D-pad, so a held A or B does one thing.
 */
internal fun applyPlayActivity(model: PickerModel, page: PlayActivityPage, meaning: Meaning, repeatCount: Int = 0): Pair<PickerModel, Effect?> {
    val close = model.copy(playActivity = null, focus = page.grid) to null
    if (meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel || meaning == Meaning.View) return close
    val listed = page.sessions
    if (listed != null) {
        val at = page.session.coerceIn(0, (listed.size - 1).coerceAtLeast(0))
        return when (meaning) {
            Meaning.MoveUp -> model.copy(playActivity = page.copy(session = (at - 1).coerceAtLeast(0))) to null
            Meaning.MoveDown -> model.copy(playActivity = page.copy(session = (at + 1).coerceAtMost(listed.lastIndex.coerceAtLeast(0)))) to null
            Meaning.ActivateBottom -> {
                val id = listed.getOrNull(at) ?: return model to null
                val rest = listed - id
                // The last one gone, the day has nothing to list; the chart shows again.
                val next = if (rest.isEmpty()) page.copy(sessions = null, session = 0) else page.copy(sessions = rest, session = at.coerceAtMost(rest.lastIndex))
                model.copy(playActivity = next) to Effect.RemovePlaySession(id)
            }
            Meaning.Back -> if (repeatCount > 0) model to null else model.copy(playActivity = page.copy(sessions = null, session = 0)) to null
            else -> model to null
        }
    }
    if (page.moving && page.game != null) return applyPlayMoving(model, page, page.game, meaning, repeatCount)
    if (repeatCount > 0 && meaning in PLAY_ONCE) return model to null
    return when (meaning) {
        Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight ->
            playMove(page, meaning)?.let { model.copy(playActivity = page.copy(day = it)) to null } ?: (model to null)
        Meaning.LetterBackward -> page.zoom.finer()?.let { model.copy(playActivity = playZoomed(page, it)) to null } ?: (model to null)
        Meaning.LetterForward -> page.zoom.coarser()?.let { model.copy(playActivity = playZoomed(page, it)) to null } ?: (model to null)
        Meaning.Activate -> when {
            page.calendar == null -> model to null
            page.zoom == PlayZoom.Year -> model.copy(playActivity = playZoomed(page, PlayZoom.Month)) to null
            else -> model to Effect.OpenPlayDay(page.cursor, page.game)
        }
        Meaning.Options -> if (page.game != null && page.sources.isNotEmpty()) {
            model.copy(playActivity = page.copy(moving = true, source = page.source.coerceIn(0, page.sources.lastIndex))) to null
        } else model to null
        Meaning.Back -> close
        else -> model to null
    }
}

/** Keys a held press does once: a held D-pad still travels. */
private val PLAY_ONCE = setOf(Meaning.Activate, Meaning.Options, Meaning.Back, Meaning.LetterBackward, Meaning.LetterForward)

/**
 * Move time from, on a game's page. Up and down move over the other games with play. A held
 * A moves the focused game's play here (the shell holds it first), and A on one moved here
 * puts it back. Y or B goes back to the chart.
 */
private fun applyPlayMoving(model: PickerModel, page: PlayActivityPage, game: String, meaning: Meaning, repeatCount: Int): Pair<PickerModel, Effect?> {
    val at = page.source.coerceIn(0, (page.sources.size - 1).coerceAtLeast(0))
    return when (meaning) {
        Meaning.MoveUp -> model.copy(playActivity = page.copy(source = (at - 1).coerceAtLeast(0))) to null
        Meaning.MoveDown -> model.copy(playActivity = page.copy(source = (at + 1).coerceAtMost(page.sources.lastIndex.coerceAtLeast(0)))) to null
        Meaning.Activate -> {
            val from = page.sources.getOrNull(at) ?: return model to null
            if (from in page.moved) {
                // Undo is a tap; a held A that already moved does not put it straight back.
                if (repeatCount > 0) return model to null
                model.copy(playActivity = page.copy(source = at, moved = page.moved - from)) to Effect.UndoPlayMove(from, game)
            } else {
                model.copy(playActivity = page.copy(source = at, moved = page.moved + from)) to Effect.MovePlayTime(from, game)
            }
        }
        Meaning.Back, Meaning.Options -> if (repeatCount > 0) model to null else model.copy(playActivity = page.copy(moving = false)) to null
        else -> model to null
    }
}

/**
 * The hint row under the page: an open list's keys, or the chart's in its zoom. [hasSessions]
 * is whether the focused day has sessions A can list.
 */
fun playActivityHints(model: PickerModel, hasSessions: Boolean = true): List<HintKey> {
    val back = HintKey(promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB, Copy.back)
    val confirm = promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA
    val page = model.playActivity ?: return listOf(back)
    if (page.sessions != null) {
        return listOf(HintKey(actionFaces(model.faceMap).bottomGlyph, PlayCopy.remove), back)
    }
    if (page.moving && page.game != null) {
        val focused = page.sources.getOrNull(page.source)
        return listOfNotNull(focused?.let { HintKey(confirm, if (it in page.moved) PlayCopy.undo else PlayCopy.holdToMove) }, back)
    }
    return listOfNotNull(
        when {
            page.zoom == PlayZoom.Year -> HintKey(confirm, PlayCopy.open)
            hasSessions -> HintKey(confirm, PlayCopy.sessions)
            else -> null
        },
        // Week's sides step whole weeks while up and down step days, so they are named; Month and Year move as drawn.
        if (page.zoom == PlayZoom.Week) HintKey(PromptKey.DpadSides, PlayCopy.week) else null,
        if (page.game != null && page.sources.isNotEmpty()) HintKey(actionFaces(model.faceMap).optionsGlyph, PlayCopy.moveTime) else null,
        back,
    )
}

/** One other game a game's page can take play from. [gone] is a game no library has now. */
data class PlaySource(val id: String, val name: String, val gone: Boolean, val activeMillis: Long)

/**
 * The order a game's page lists [sources] for the game named [name]: first a game of the
 * same name, as a ROM renamed or replaced in another region or revision is, then one whose
 * name shares most of its words, and in each a game no library has now before one still
 * there. Then the rest, gone first, each tier by most time played, then A to Z.
 */
fun playSourceOrder(name: String, sources: List<PlaySource>): List<PlaySource> {
    val words = playWords(name)
    val key = words.joinToString(" ")
    return sources.sortedWith(
        compareByDescending<PlaySource> { source ->
            val theirs = playWords(source.name)
            val like = when {
                key.isNotEmpty() && theirs.joinToString(" ") == key -> 2
                alike(words, theirs) -> 1
                else -> 0
            }
            like * 2 + if (source.gone) 1 else 0
        }.thenByDescending { it.activeMillis }.thenBy { it.name.lowercase() }.thenBy { it.id },
    )
}

/**
 * The gone game of [name]'s own name with the most play, as a renamed or replaced ROM
 * leaves behind: what a game with no play of its own points to. Null when there is none.
 */
fun foundSource(name: String, sources: List<PlaySource>): PlaySource? {
    val key = playWords(name).joinToString(" ").takeIf { it.isNotEmpty() } ?: return null
    return sources.filter { it.gone && it.activeMillis > 0L && playWords(it.name).joinToString(" ") == key }
        .maxByOrNull { it.activeMillis }
}

/** Half or more of the words of the shorter name are in the other. */
private fun alike(ours: List<String>, theirs: List<String>): Boolean {
    if (ours.isEmpty() || theirs.isEmpty()) return false
    val shorter = if (ours.size <= theirs.size) ours else theirs
    val longer = (if (shorter === ours) theirs else ours).toSet()
    return shorter.count { it in longer } * 2 >= shorter.size
}

/**
 * A name's words as two names of one game share them: lower case, without the tags in
 * brackets a ROM set adds, "(USA)", "[!]", "(Rev 1)", or a file extension, and without
 * punctuation. "Pokémon - Emerald Version (USA, Europe).gba" is "pokémon emerald version".
 */
/** A ROM file's extension at the end of its name; "Dragon Quest I.II" keeps its ".II". */
private val ROM_EXTENSION = Regex(
    "\\.(gba|gbc|gb|nes|fds|sfc|smc|n64|z64|v64|nds|3ds|cci|cia|cxi|iso|cso|chd|cue|bin|img|pbp|m3u|rvz|gcm|wbfs|wua|wux|rpx|xci|nsp|gen|md|smd|sms|gg|32x|pce|ngp|ngc|ws|wsc|a26|lnx|zip|7z)$",
)

internal fun playWords(name: String): List<String> =
    name.lowercase()
        .replace(ROM_EXTENSION, "")
        .replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() && it != "the" }
