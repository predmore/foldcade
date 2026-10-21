package app.foldcade.language

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

/**
 * One stretch a game was in front, from Foldcade's play log. Wall-clock milliseconds.
 * [sessionId] is the launch it belongs to; one session can be several stretches.
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
    val label: String,
    val days: List<PlayDay>,
    val totalMillis: Long,
    /** Game ids and their time in this week, most first. */
    val top: List<Pair<String, Long>>,
)

/** The Play activity page. [weeksBack] is 0 for the seven days ending today. */
data class PlayActivityPage(
    val screen: HostScreen,
    val weeksBack: Int = 0,
    /** Grid focus to restore when the page closes. */
    val grid: GridFocus = GridFocus(),
    /** The day row in focus, 0 the oldest of the seven. Today to start. */
    val day: Int = PLAY_WEEK_DAYS - 1,
    /** The open day's session ids, in the order listed. Null while the week shows. */
    val sessions: List<String>? = null,
    /** The session in focus on the open day's list. */
    val session: Int = 0,
)

object PlayCopy {
    const val activity = "Play activity"
    const val pastWeek = "Past 7 days"
    const val mostPlayed = "Most played"
    const val nothing = "Nothing played these days"
    const val week = "Week"
    const val sessions = "Sessions"
    const val remove = "Remove"

    /** A game that left every catalog and left nothing readable behind. */
    const val removedGame = "Removed game"
}

/** How many games the page lists under the schedule. */
const val PLAY_TOP_GAMES = 4

/** Days on the page. */
const val PLAY_WEEK_DAYS = 7

/** The seven dates that end [weeksBack] weeks before today, in [zone], oldest first. */
fun playWeekDates(nowMillis: Long, zone: ZoneId, weeksBack: Int = 0): List<LocalDate> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val last = today.minusWeeks(weeksBack.coerceAtLeast(0).toLong())
    return (PLAY_WEEK_DAYS - 1 downTo 0).map { last.minusDays(it.toLong()) }
}

/**
 * The seven days that end [weeksBack] weeks before today, in [zone], named and
 * dated in the device's [style]. A stretch that crosses midnight counts on both
 * days, each for its own part.
 */
fun playWeek(
    spans: List<PlaySpan>,
    nowMillis: Long,
    zone: ZoneId,
    style: DateStyle,
    weeksBack: Int = 0,
): PlayWeek {
    val dates = playWeekDates(nowMillis, zone, weeksBack)
    val perGame = HashMap<String, Long>()
    val days = dates.map { date ->
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val length = (end - start).coerceAtLeast(1L).toFloat()
        var total = 0L
        val blocks = spans.mapNotNull { span ->
            val from = maxOf(span.startMillis, start)
            val to = minOf(span.endMillis, end)
            if (to <= from) return@mapNotNull null
            total += to - from
            perGame[span.gameId] = (perGame[span.gameId] ?: 0L) + (to - from)
            ((from - start) / length)..((to - start) / length)
        }
        PlayDay(date, date.dayOfWeek.getDisplayName(TextStyle.SHORT, style.locale), blocks, total)
    }
    val label = if (weeksBack <= 0) {
        PlayCopy.pastWeek
    } else {
        "${style.monthDay(dates.first())} – ${style.monthDay(dates.last())}"
    }
    return PlayWeek(
        label = label,
        days = days,
        totalMillis = days.sumOf { it.totalMillis },
        top = perGame.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(PLAY_TOP_GAMES)
            .map { it.key to it.value },
    )
}

/**
 * The sessions with time in front on day [day] (0 the oldest) of the week [weeksBack]
 * weeks back, first started first. Each is whole, so one that crossed midnight shows
 * on both days with all of its time: removing it removes all of it.
 */
fun playDaySessions(spans: List<PlaySpan>, nowMillis: Long, zone: ZoneId, weeksBack: Int, day: Int): List<DaySession> {
    val date = playWeekDates(nowMillis, zone, weeksBack).getOrNull(day) ?: return emptyList()
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val bySession = spans.filter { it.sessionId.isNotEmpty() }.groupBy { it.sessionId }
    return bySession.values
        .filter { stretches -> stretches.any { it.endMillis > start && it.startMillis < end } }
        .map { stretches ->
            DaySession(
                sessionId = stretches.first().sessionId,
                gameId = stretches.first().gameId,
                startMillis = stretches.minOf { it.startMillis },
                endMillis = stretches.maxOf { it.endMillis },
                activeMillis = stretches.sumOf { it.endMillis - it.startMillis },
            )
        }
        .sortedWith(compareBy<DaySession> { it.startMillis }.thenBy { it.sessionId })
}

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

/**
 * Up and down move between the days; left goes a week back and right a week
 * forward, up to this week. A lists the day's sessions, which the shell reads
 * from the log ([Effect.OpenPlayDay]). B, L1, and R1 close the page and put the
 * grid focus back. On the list, up and down move, X removes the session in
 * focus (the shell has it held first), and B goes back to the week.
 */
internal fun applyPlayActivity(model: PickerModel, page: PlayActivityPage, meaning: Meaning): Pair<PickerModel, Effect?> {
    val listed = page.sessions
    if (listed != null) {
        val at = page.session.coerceIn(0, (listed.size - 1).coerceAtLeast(0))
        return when (meaning) {
            Meaning.MoveUp -> model.copy(playActivity = page.copy(session = (at - 1).coerceAtLeast(0))) to null
            Meaning.MoveDown -> model.copy(playActivity = page.copy(session = (at + 1).coerceAtMost(listed.lastIndex.coerceAtLeast(0)))) to null
            Meaning.ActivateBottom -> {
                val id = listed.getOrNull(at) ?: return model to null
                val rest = listed - id
                // The last one gone, the day has nothing to list; the week shows again.
                val next = if (rest.isEmpty()) page.copy(sessions = null, session = 0) else page.copy(sessions = rest, session = at.coerceAtMost(rest.lastIndex))
                model.copy(playActivity = next) to Effect.RemovePlaySession(id)
            }
            Meaning.Back -> model.copy(playActivity = page.copy(sessions = null, session = 0)) to null
            Meaning.LeftPanel, Meaning.RightPanel, Meaning.View -> model.copy(playActivity = null, focus = page.grid) to null
            else -> model to null
        }
    }
    return when (meaning) {
        Meaning.MoveUp -> model.copy(playActivity = page.copy(day = (page.day - 1).coerceAtLeast(0))) to null
        Meaning.MoveDown -> model.copy(playActivity = page.copy(day = (page.day + 1).coerceAtMost(PLAY_WEEK_DAYS - 1))) to null
        Meaning.MoveLeft -> model.copy(playActivity = page.copy(weeksBack = page.weeksBack + 1)) to null
        Meaning.MoveRight -> model.copy(playActivity = page.copy(weeksBack = (page.weeksBack - 1).coerceAtLeast(0))) to null
        Meaning.Activate -> model to Effect.OpenPlayDay(page.weeksBack, page.day.coerceIn(0, PLAY_WEEK_DAYS - 1))
        // Select closes it again, as L1 then Select opened it.
        Meaning.Back, Meaning.LeftPanel, Meaning.RightPanel, Meaning.View -> model.copy(playActivity = null, focus = page.grid) to null
        else -> model to null
    }
}

/** The hint row under the page: the week's keys, or the open day's. */
fun playActivityHints(model: PickerModel): List<HintKey> {
    val back = HintKey(promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB, Copy.back)
    if (model.playActivity?.sessions != null) {
        return listOf(HintKey(PromptKey.FaceX, PlayCopy.remove), back)
    }
    return listOf(
        HintKey(promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, PlayCopy.sessions),
        HintKey(PromptKey.DpadSides, PlayCopy.week),
        back,
    )
}
