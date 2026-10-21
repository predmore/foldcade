package app.foldcade.language

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

/** One stretch a game was in front, from Foldcade's play log. Wall-clock milliseconds. */
data class PlaySpan(val gameId: String, val startMillis: Long, val endMillis: Long)

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
)

object PlayCopy {
    const val activity = "Play activity"
    const val pastWeek = "Past 7 days"
    const val mostPlayed = "Most played"
    const val nothing = "Nothing played these days"
    const val week = "Week"

    /** A game that left every catalog and left nothing readable behind. */
    const val removedGame = "Removed game"
}

/** How many games the page lists under the schedule. */
const val PLAY_TOP_GAMES = 4

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
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val last = today.minusWeeks(weeksBack.coerceAtLeast(0).toLong())
    val dates = (6 downTo 0).map { last.minusDays(it.toLong()) }
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
 * Left goes a week back and right a week forward, up to this week. B, L1, and
 * R1 close the page and put the grid focus back.
 */
internal fun applyPlayActivity(model: PickerModel, page: PlayActivityPage, meaning: Meaning): Pair<PickerModel, Effect?> =
    when (meaning) {
        Meaning.MoveLeft -> model.copy(playActivity = page.copy(weeksBack = page.weeksBack + 1)) to null
        Meaning.MoveRight -> model.copy(playActivity = page.copy(weeksBack = (page.weeksBack - 1).coerceAtLeast(0))) to null
        // Select closes it again, as L1 then Select opened it.
        Meaning.Back, Meaning.LeftPanel, Meaning.RightPanel, Meaning.View -> model.copy(playActivity = null, focus = page.grid) to null
        else -> model to null
    }

/** The hint row under the page. */
fun playActivityHints(model: PickerModel): List<HintKey> = listOf(
    HintKey(PromptKey.DpadSides, PlayCopy.week),
    HintKey(promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB, Copy.back),
)
