package app.foldcade.language

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

/** How far Play activity looks: a week of day rows, a month of day cells, or a year of month bars. */
enum class PlayZoom {
    Week,
    Month,
    Year,
    ;

    /** L2: one step closer, or null at Week. */
    fun finer(): PlayZoom? = entries.getOrNull(ordinal - 1)

    /** R2: one step further out, or null at Year. */
    fun coarser(): PlayZoom? = entries.getOrNull(ordinal + 1)
}

/**
 * Where the page's cursor may go: from the first day played in its scope to [today]. It never
 * goes into the future, and with nothing played it stays on today. [played] are the days with
 * any play, as epoch days, oldest first, so Year can land Month on a month's last played day.
 */
class PlayCalendar(
    val today: LocalDate,
    played: List<LocalDate> = emptyList(),
    /**
     * The time zone the days were counted in. Part of the calendar's identity: a new zone can
     * keep the same played dates yet put a session on another day.
     */
    val zone: ZoneId? = null,
) {
    private val days: LongArray = played.map { it.toEpochDay() }.distinct().sorted().toLongArray()

    /** The earliest day the cursor can reach. */
    val first: LocalDate = days.firstOrNull()?.let(LocalDate::ofEpochDay)?.takeIf { it < today } ?: today

    /** The last played day on or before [today], or null when nothing was. */
    val lastPlayed: LocalDate? = days.lastOrNull { it <= today.toEpochDay() }?.let(LocalDate::ofEpochDay)

    fun clamp(date: LocalDate): LocalDate = when {
        date < first -> first
        date > today -> today
        else -> date
    }

    /** The last day of [month] with play, or null when it had none. */
    fun lastPlayedIn(month: YearMonth): LocalDate? {
        val end = month.atEndOfMonth().toEpochDay()
        val start = month.atDay(1).toEpochDay()
        // The first index past the month's end, then one back.
        var lo = 0
        var hi = days.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (days[mid] <= end) lo = mid + 1 else hi = mid
        }
        val at = lo - 1
        return if (at >= 0 && days[at] >= start) LocalDate.ofEpochDay(days[at]) else null
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is PlayCalendar && other.today == today && other.zone == zone && other.days.contentEquals(days))

    override fun hashCode(): Int = 31 * (31 * today.hashCode() + (zone?.hashCode() ?: 0)) + days.contentHashCode()

    override fun toString(): String = "PlayCalendar(today=$today, zone=$zone, days=${days.size})"
}

/**
 * How deep a day's cell is shaded, 0 to 4, from the player's own days: 0 is none, and 4 is a
 * day at least as long as three in four of the days they played. The levels come from every
 * played day in scope, so the same time reads the same in every month and year.
 */
class PlayHeat(totals: Collection<Long>) {
    private val sorted: LongArray = totals.filter { it > 0L }.sorted().toLongArray()

    /** The day totals at a quarter, a half and three quarters of the way up, or empty with no play. */
    val quartiles: List<Long> = if (sorted.isEmpty()) emptyList() else listOf(0.25, 0.5, 0.75).map { share ->
        sorted[(ceil(share * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)]
    }

    /**
     * The share of played days this long or shorter, in quarters, rounded up. A tie goes to the
     * higher level, so one day played, or every day the same, reads as full.
     */
    fun level(millis: Long): Int {
        if (millis <= 0L || sorted.isEmpty()) return 0
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] <= millis) lo = mid + 1 else hi = mid
        }
        return ceil(PLAY_HEAT_LEVELS * lo.toDouble() / sorted.size).toInt().coerceIn(1, PLAY_HEAT_LEVELS)
    }

    override fun equals(other: Any?): Boolean = this === other || (other is PlayHeat && other.sorted.contentEquals(sorted))

    override fun hashCode(): Int = sorted.contentHashCode()
}

/** The heat levels above none. */
const val PLAY_HEAT_LEVELS = 4

/**
 * Everything Play activity draws, read once from the stretches in front: each day's time, its
 * marks across the hours, its games and its sessions, plus the lifetime facts a game's page
 * lists. A stretch that crosses midnight counts on each day for its own part; a session that
 * does is listed whole on each day it touched, so removing it removes all of it.
 *
 * Days are epoch days in [zone]. Build it once per change of the play log, never per move.
 */
class PlayHistory(spans: List<PlaySpan>, val zone: ZoneId) {
    private val totals = HashMap<Long, Long>()
    private val blocks = HashMap<Long, MutableList<ClosedFloatingPointRange<Float>>>()
    private val games = HashMap<Long, HashMap<String, Long>>()
    private val sessionsOn = HashMap<Long, MutableList<DaySession>>()

    /** Each month's time, and how many of its days had play. */
    private val months = HashMap<YearMonth, Long>()
    private val monthDays = HashMap<YearMonth, Int>()

    /** Sessions: one per Foldcade launch, and one per stretch Android alone saw. */
    val sessions: Int

    /** The longest session's time in front, or 0. */
    val longestMillis: Long

    val daysPlayed: Int

    /** The most days in a row with any play. */
    val bestStreak: Int

    val first: LocalDate?
    val last: LocalDate?
    val heat: PlayHeat

    init {
        for (span in spans) split(span)
        val grouped = spans.filter { it.sessionId.isNotEmpty() }.groupBy { it.sessionId }
        val whole = grouped.values.map { stretches ->
            DaySession(
                sessionId = stretches.first().sessionId,
                gameId = stretches.first().gameId,
                startMillis = stretches.minOf { it.startMillis },
                endMillis = stretches.maxOf { it.endMillis },
                activeMillis = stretches.sumOf { (it.endMillis - it.startMillis).coerceAtLeast(0L) },
            ) to stretches
        }
        for ((session, stretches) in whole) {
            if (session.activeMillis <= 0L) continue
            val touched = stretches.filter { it.endMillis > it.startMillis }.flatMapTo(HashSet()) { daysOf(it.startMillis, it.endMillis) }
            touched.forEach { sessionsOn.getOrPut(it) { mutableListOf() } += session }
        }
        sessionsOn.values.forEach { list -> list.sortWith(compareBy<DaySession> { it.startMillis }.thenBy { it.sessionId }) }
        val loose = spans.filter { it.sessionId.isEmpty() && it.endMillis > it.startMillis }
        sessions = whole.count { it.first.activeMillis > 0L } + loose.size
        longestMillis = maxOf(whole.maxOfOrNull { it.first.activeMillis } ?: 0L, loose.maxOfOrNull { it.endMillis - it.startMillis } ?: 0L)
        val played = totals.filterValues { it > 0L }.keys.sorted()
        daysPlayed = played.size
        bestStreak = longestRun(played)
        first = played.firstOrNull()?.let(LocalDate::ofEpochDay)
        last = played.lastOrNull()?.let(LocalDate::ofEpochDay)
        heat = PlayHeat(totals.values)
        for ((day, millis) in totals) {
            if (millis <= 0L) continue
            val month = YearMonth.from(LocalDate.ofEpochDay(day))
            months[month] = (months[month] ?: 0L) + millis
            monthDays[month] = (monthDays[month] ?: 0) + 1
        }
    }

    private fun split(span: PlaySpan) {
        if (span.endMillis <= span.startMillis) return
        var day = Instant.ofEpochMilli(span.startMillis).atZone(zone).toLocalDate()
        while (true) {
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            if (start >= span.endMillis) break
            val from = maxOf(span.startMillis, start)
            val to = minOf(span.endMillis, end)
            if (to > from) {
                val key = day.toEpochDay()
                totals[key] = (totals[key] ?: 0L) + (to - from)
                val length = (end - start).coerceAtLeast(1L).toFloat()
                blocks.getOrPut(key) { mutableListOf() } += ((from - start) / length)..((to - start) / length)
                val perGame = games.getOrPut(key) { HashMap() }
                perGame[span.gameId] = (perGame[span.gameId] ?: 0L) + (to - from)
            }
            day = day.plusDays(1)
        }
    }

    private fun daysOf(startMillis: Long, endMillis: Long): List<Long> {
        val from = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate().toEpochDay()
        // An end exactly at midnight belongs to the day before.
        val to = Instant.ofEpochMilli(endMillis - 1).atZone(zone).toLocalDate().toEpochDay()
        return (from..maxOf(from, to)).toList()
    }

    fun dayTotal(date: LocalDate): Long = totals[date.toEpochDay()] ?: 0L

    /** The day's stretches as fractions of it, 0 to 1. */
    fun dayBlocks(date: LocalDate): List<ClosedFloatingPointRange<Float>> = blocks[date.toEpochDay()].orEmpty()

    /** The sessions with time in front on [date], first started first. */
    fun daySessions(date: LocalDate): List<DaySession> = sessionsOn[date.toEpochDay()].orEmpty()

    /** The game played most on [date], or null on a day without play. */
    fun dayTop(date: LocalDate): String? = games[date.toEpochDay()]?.entries
        ?.maxWithOrNull(compareBy<Map.Entry<String, Long>> { it.value }.thenByDescending { it.key })?.key

    fun monthTotal(month: YearMonth): Long = months[month] ?: 0L

    fun monthDaysPlayed(month: YearMonth): Int = monthDays[month] ?: 0

    fun yearTotal(year: Int): Long = (1..12).sumOf { monthTotal(YearMonth.of(year, it)) }

    /** Time from [from] to [to], both included. */
    fun total(from: LocalDate, to: LocalDate): Long {
        if (to < from) return 0L
        // A whole year or month reads its sum; anything else adds its days.
        if (from.dayOfMonth == 1 && to == from.withDayOfMonth(from.lengthOfMonth())) return monthTotal(YearMonth.from(from))
        if (from.dayOfYear == 1 && to == from.withDayOfYear(from.lengthOfYear())) return yearTotal(from.year)
        var sum = 0L
        var day = from.toEpochDay()
        val end = to.toEpochDay()
        while (day <= end) { sum += totals[day] ?: 0L; day++ }
        return sum
    }

    /** The games played most from [from] to [to], both included, with their time, most first. */
    fun top(from: LocalDate, to: LocalDate, count: Int = PLAY_TOP_GAMES): List<Pair<String, Long>> {
        val sum = HashMap<String, Long>()
        var day = from.toEpochDay()
        val end = to.toEpochDay()
        while (day <= end) {
            games[day]?.forEach { (game, millis) -> sum[game] = (sum[game] ?: 0L) + millis }
            day++
        }
        return sum.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(count)
            .map { it.key to it.value }
    }

    /** Where the cursor may go while [today] is today. */
    fun calendar(today: LocalDate): PlayCalendar = PlayCalendar(today, totals.filterValues { it > 0L }.keys.map(LocalDate::ofEpochDay), zone)

    /** The seven days from [start], as the week zoom draws them, named in [style]. */
    fun week(start: LocalDate, style: DateStyle): PlayWeek {
        val dates = (0 until PLAY_WEEK_DAYS).map { start.plusDays(it.toLong()) }
        val days = dates.map { date ->
            PlayDay(date, date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, style.locale), dayBlocks(date), dayTotal(date))
        }
        return PlayWeek(days = days, totalMillis = days.sumOf { it.totalMillis }, top = top(dates.first(), dates.last()))
    }
}

private fun longestRun(sortedDays: List<Long>): Int {
    var best = 0
    var run = 0
    var previous: Long? = null
    for (day in sortedDays) {
        run = if (previous != null && day == previous + 1) run + 1 else 1
        best = maxOf(best, run)
        previous = day
    }
    return best
}

/** The first day of the week holding [date], weeks starting on [firstDay]. */
fun playWeekStart(date: LocalDate, firstDay: DayOfWeek): LocalDate = date.with(TemporalAdjusters.previousOrSame(firstDay))

/** The days of the month calendar for [month], whole weeks starting on [firstDay]: 28 to 42 of them. */
fun playMonthGrid(month: YearMonth, firstDay: DayOfWeek): List<LocalDate> {
    val start = playWeekStart(month.atDay(1), firstDay)
    val end = playWeekStart(month.atEndOfMonth(), firstDay).plusDays(PLAY_WEEK_DAYS - 1L)
    return generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
}

/** The weekdays in order from [firstDay]. */
fun playWeekdays(firstDay: DayOfWeek): List<DayOfWeek> = (0 until PLAY_WEEK_DAYS).map { firstDay.plus(it.toLong()) }
