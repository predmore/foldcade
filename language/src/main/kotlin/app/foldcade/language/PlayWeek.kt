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
    /**
     * The game the page is about, opened from its Y menu's Play time: the schedule shows only
     * its play, and the side lists [sources]. Null for everything played.
     */
    val game: String? = null,
    /** On a game's page, the other games with play that can move onto it, likeliest first. */
    val sources: List<String> = emptyList(),
    /** The source in focus. */
    val source: Int = 0,
    /** Sources moved onto [game] while the page is open. A on one again puts it back. */
    val moved: Set<String> = emptySet(),
)

object PlayCopy {
    const val activity = "Play activity"
    const val pastWeek = "Past 7 days"
    const val mostPlayed = "Most played"
    const val nothing = "Nothing played these days"
    const val week = "Week"
    const val sessions = "Sessions"
    const val remove = "Remove"

    /** A game's Y menu row, and the top of its own Play activity. */
    const val playTime = "Play time"
    const val total = "Total"

    /** Over the other games on a game's page. */
    const val moveFrom = "Move time from"

    /** Held A on one of them: the hint says to hold, as a tap does nothing. */
    const val holdToMove = "Hold to move"
    const val moveHere = "Move here"
    const val moved = "Moved here"
    const val undo = Copy.undo

    /** A game with play that no library has any more: its file was renamed, replaced, or removed. */
    const val gone = "Gone"
    const val noSources = "No other games played yet"

    /** Settings › System. Its dialog offers both ways. */
    const val history = "Play history"
    const val historyBody = "Export your play time to a file, or import one. Importing only adds to what's here."
    const val export = "Export"
    const val import = "Import"
    const val exported = "Saved."
    fun exported(folder: String?, name: String?): String = when {
        !folder.isNullOrBlank() -> "Saved to $folder."
        !name.isNullOrBlank() -> "Saved as $name."
        else -> exported
    }
    const val notSaved = "The file couldn't be saved."
    const val updated = "Updated"

    /** A game's Play time row when it has none, but a gone game of its name has: its time, waiting. */
    fun found(millis: Long): String = "Found ${playDuration(millis)}"
    const val nothingNew = "Nothing new to add"
    const val notHistory = "That file isn't a Foldcade play history."
    const val unreadable = "That file couldn't be read."

    fun imported(sessions: Int): String = if (sessions == 1) "Added 1 session" else "Added $sessions sessions"

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
 * grid focus back. On the list, up and down move, the bottom button removes
 * the session in focus (the shell has it held first), and back goes to the week.
 */
internal fun applyPlayActivity(model: PickerModel, page: PlayActivityPage, meaning: Meaning): Pair<PickerModel, Effect?> {
    if (page.game != null) return applyPlayGame(model, page, page.game, meaning)
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

/**
 * A game's page. Up and down move over the other games with play; left and right step
 * the weeks of this game's schedule. A held A moves the focused game's play here (the
 * shell holds it first), and A on one moved here puts it back. B, L1, R1, and Select close.
 */
private fun applyPlayGame(model: PickerModel, page: PlayActivityPage, game: String, meaning: Meaning): Pair<PickerModel, Effect?> {
    val at = page.source.coerceIn(0, (page.sources.size - 1).coerceAtLeast(0))
    return when (meaning) {
        Meaning.MoveUp -> model.copy(playActivity = page.copy(source = (at - 1).coerceAtLeast(0))) to null
        Meaning.MoveDown -> model.copy(playActivity = page.copy(source = (at + 1).coerceAtMost(page.sources.lastIndex.coerceAtLeast(0)))) to null
        Meaning.MoveLeft -> model.copy(playActivity = page.copy(weeksBack = page.weeksBack + 1)) to null
        Meaning.MoveRight -> model.copy(playActivity = page.copy(weeksBack = (page.weeksBack - 1).coerceAtLeast(0))) to null
        Meaning.Activate -> {
            val from = page.sources.getOrNull(at) ?: return model to null
            if (from in page.moved) {
                model.copy(playActivity = page.copy(source = at, moved = page.moved - from)) to Effect.UndoPlayMove(from, game)
            } else {
                model.copy(playActivity = page.copy(source = at, moved = page.moved + from)) to Effect.MovePlayTime(from, game)
            }
        }
        Meaning.Back, Meaning.LeftPanel, Meaning.RightPanel, Meaning.View -> model.copy(playActivity = null, focus = page.grid) to null
        else -> model to null
    }
}

/** The hint row under the page: the week's keys, the open day's, or a game's. */
fun playActivityHints(model: PickerModel): List<HintKey> {
    val back = HintKey(promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB, Copy.back)
    val page = model.playActivity
    if (page?.game != null) {
        val confirm = promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA
        val focused = page.sources.getOrNull(page.source)
        return listOfNotNull(
            focused?.let { HintKey(confirm, if (it in page.moved) PlayCopy.undo else PlayCopy.holdToMove) },
            HintKey(PromptKey.DpadSides, PlayCopy.week),
            back,
        )
    }
    if (model.playActivity?.sessions != null) {
        return listOf(HintKey(actionFaces(model.faceMap).bottomGlyph, PlayCopy.remove), back)
    }
    return listOf(
        HintKey(promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, PlayCopy.sessions),
        HintKey(PromptKey.DpadSides, PlayCopy.week),
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
