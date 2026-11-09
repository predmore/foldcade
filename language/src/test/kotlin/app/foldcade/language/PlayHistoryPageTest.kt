package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Play activity's history: days, weeks, months and years over one cursor date, its bounds, and its copy. */
class PlayHistoryPageTest {
    private val zone = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 11, 9) // A Monday.
    private val gb = DateStyle(Locale.UK, monthDayPattern = "d MMM", monthDayYearPattern = "d MMM y", is24Hour = true,
        weekdayMonthDayPattern = "EEE d MMM", monthYearPattern = "LLLL y")

    // ---- The history ----

    @Test
    fun aStretchAcrossMidnightCountsOnEachDayForItsOwnPart() {
        val history = PlayHistory(listOf(
            // Friday 23:00 to Saturday 01:00: an hour on each day.
            span("a", 2026, 11, 6, 23, 0, 2026, 11, 7, 1, 0),
            span("b", 2026, 11, 7, 12, 0, 2026, 11, 7, 12, 30),
        ), zone)
        assertEquals(HOUR, history.dayTotal(LocalDate.of(2026, 11, 6)))
        assertEquals(HOUR + HOUR / 2, history.dayTotal(LocalDate.of(2026, 11, 7)))
        val friday = history.dayBlocks(LocalDate.of(2026, 11, 6)).single()
        assertEquals(23f / 24f, friday.start, 0.0001f)
        assertEquals(1f, friday.endInclusive, 0.0001f)
        assertEquals(0f, history.dayBlocks(LocalDate.of(2026, 11, 7)).first().start, 0.0001f)
        assertEquals(2, history.daysPlayed)
        // Both days of the weekend are in a row.
        assertEquals(2, history.bestStreak)
        assertEquals("a", history.dayTop(LocalDate.of(2026, 11, 6)))
        assertEquals("a", history.dayTop(LocalDate.of(2026, 11, 7)))
        assertNull(history.dayTop(LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun aStretchEndingAtMidnightBelongsToItsOwnDay() {
        val history = PlayHistory(listOf(span("a", 2026, 11, 6, 22, 0, 2026, 11, 7, 0, 0, "s")), zone)
        assertEquals(2 * HOUR, history.dayTotal(LocalDate.of(2026, 11, 6)))
        assertEquals(0L, history.dayTotal(LocalDate.of(2026, 11, 7)))
        assertEquals(listOf("s"), history.daySessions(LocalDate.of(2026, 11, 6)).map { it.sessionId })
        assertTrue(history.daySessions(LocalDate.of(2026, 11, 7)).isEmpty())
    }

    @Test
    fun aSessionAcrossMidnightIsListedWholeOnBothDaysInTheOrderSessionsStarted() {
        val history = PlayHistory(listOf(
            // Left running overnight: Friday 21:00 to Saturday 12:00, in two stretches.
            span("a", 2026, 10, 9, 21, 0, 2026, 10, 9, 23, 0, "night"),
            span("a", 2026, 10, 9, 23, 30, 2026, 10, 10, 12, 0, "night"),
            span("b", 2026, 10, 10, 13, 0, 2026, 10, 10, 13, 45, "lunch"),
            span("c", 2026, 10, 10, 6, 0, 2026, 10, 10, 6, 10, "early"),
            // Spans from before session ids were kept have nothing to remove by.
            span("d", 2026, 10, 10, 8, 0, 2026, 10, 10, 9, 0),
        ), zone)
        val saturday = history.daySessions(LocalDate.of(2026, 10, 10))
        assertEquals(listOf("night", "early", "lunch"), saturday.map { it.sessionId })
        val night = saturday.first()
        assertEquals("a", night.gameId)
        assertEquals(millis(2026, 10, 9, 21, 0), night.startMillis)
        assertEquals(millis(2026, 10, 10, 12, 0), night.endMillis)
        assertEquals(2 * HOUR + 12 * HOUR + 30 * MINUTE, night.activeMillis)
        assertEquals(listOf("night"), history.daySessions(LocalDate.of(2026, 10, 9)).map { it.sessionId })
        assertTrue(history.daySessions(LocalDate.of(2026, 10, 11)).isEmpty())
        // Three launches and one stretch Android alone saw; the overnight one is the longest.
        assertEquals(4, history.sessions)
        assertEquals(14 * HOUR + 30 * MINUTE, history.longestMillis)
    }

    @Test
    fun aWeekSumsItsDaysAndListsItsMostPlayed() {
        val history = PlayHistory(listOf(
            span("a", 2026, 11, 6, 23, 0, 2026, 11, 7, 1, 0),
            span("b", 2026, 11, 7, 12, 0, 2026, 11, 7, 12, 30),
            // The week before is outside this one.
            span("c", 2026, 10, 29, 12, 0, 2026, 10, 29, 15, 0),
        ), zone)
        val week = history.week(LocalDate.of(2026, 11, 1), DateStyle())
        assertEquals(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"), week.days.map { it.label })
        assertEquals(HOUR, week.days[5].totalMillis)
        assertEquals(listOf("a" to 2 * HOUR, "b" to HOUR / 2), week.top)
        assertEquals(2 * HOUR + HOUR / 2, week.totalMillis)
        assertEquals(listOf("c" to 3 * HOUR), history.week(LocalDate.of(2026, 10, 25), DateStyle()).top)
    }

    @Test
    fun monthsAndYearsAddUpTheirDays() {
        val history = PlayHistory(listOf(
            span("a", 2025, 12, 31, 23, 0, 2026, 1, 1, 1, 0),
            span("a", 2026, 1, 15, 10, 0, 2026, 1, 15, 12, 0),
            span("b", 2026, 3, 2, 10, 0, 2026, 3, 2, 10, 45),
        ), zone)
        assertEquals(HOUR, history.monthTotal(YearMonth.of(2025, 12)))
        assertEquals(3 * HOUR, history.monthTotal(YearMonth.of(2026, 1)))
        assertEquals(2, history.monthDaysPlayed(YearMonth.of(2026, 1)))
        assertEquals(3 * HOUR + 45 * MINUTE, history.yearTotal(2026))
        assertEquals(HOUR, history.yearTotal(2025))
        assertEquals(3 * HOUR, history.total(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
        assertEquals(3 * HOUR + 45 * MINUTE, history.total(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
        assertEquals(2 * HOUR, history.total(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 3, 1)))
        assertEquals(0L, history.total(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1)))
        assertEquals(listOf("a" to 4 * HOUR, "b" to 45 * MINUTE), history.top(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31)))
        assertEquals(LocalDate.of(2025, 12, 31), history.first)
        assertEquals(LocalDate.of(2026, 3, 2), history.last)
    }

    @Test
    fun theBestStreakIsTheLongestRunOfDaysInARow() {
        val days = listOf(1, 2, 3, 5, 6, 7, 8, 12)
        val history = PlayHistory(days.map { span("a", 2026, 10, it, 20, 0, 2026, 10, it, 21, 0) }, zone)
        assertEquals(4, history.bestStreak)
        assertEquals(8, history.daysPlayed)
        assertEquals(1, PlayHistory(listOf(span("a", 2026, 10, 1, 20, 0, 2026, 10, 1, 21, 0)), zone).bestStreak)
        val empty = PlayHistory(emptyList(), zone)
        assertEquals(0, empty.bestStreak)
        assertEquals(0, empty.sessions)
        assertEquals(0L, empty.longestMillis)
        assertNull(empty.first)
        assertEquals("–", playStreak(0))
        assertEquals("–", playStreak(1))
        assertEquals("2 days", playStreak(2))
    }

    @Test
    fun aDaysHeatIsItsPlaceAmongThePlayersOwnDaysInQuarters() {
        val heat = PlayHeat(listOf(10L, 20L, 30L, 40L, 0L))
        assertEquals(listOf(10L, 20L, 30L), heat.quartiles)
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(0L, 10L, 20L, 30L, 40L).map(heat::level))
        // Between two days, a day reads as the shorter one's level; past the longest, as full.
        assertEquals(1, heat.level(15L))
        assertEquals(4, heat.level(400L))
        assertEquals(1, heat.level(1L))
        // One day played, or every day alike, reads full.
        assertEquals(4, PlayHeat(listOf(5L)).level(5L))
        assertEquals(4, PlayHeat(listOf(5L, 5L, 5L)).level(5L))
        assertEquals(listOf(2, 4), listOf(10L, 40L).map(PlayHeat(listOf(10L, 40L))::level))
        assertEquals(0, PlayHeat(emptyList()).level(100L))
        assertTrue(PlayHeat(emptyList()).quartiles.isEmpty())
        // The same time is the same level in any month: levels come from every day in scope.
        val history = PlayHistory(listOf(
            span("a", 2026, 9, 1, 10, 0, 2026, 9, 1, 11, 0),
            span("a", 2026, 10, 1, 10, 0, 2026, 10, 1, 14, 0),
        ), zone)
        assertEquals(2, history.heat.level(history.dayTotal(LocalDate.of(2026, 9, 1))))
        assertEquals(4, history.heat.level(history.dayTotal(LocalDate.of(2026, 10, 1))))
    }

    @Test
    fun theMonthCalendarIsWholeWeeksFromTheDevicesFirstDay() {
        // November 2026 starts on a Sunday.
        val sunday = playMonthGrid(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(35, sunday.size)
        assertEquals(LocalDate.of(2026, 11, 1), sunday.first())
        assertEquals(LocalDate.of(2026, 12, 5), sunday.last())
        val monday = playMonthGrid(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(42, monday.size)
        assertEquals(LocalDate.of(2026, 10, 26), monday.first())
        // February 2026 fits four weeks exactly from Sunday.
        assertEquals(28, playMonthGrid(YearMonth.of(2026, 2), DayOfWeek.SUNDAY).size)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), playWeekdays(DayOfWeek.MONDAY).take(2))
        assertEquals(LocalDate.of(2026, 11, 8), playWeekStart(today, DayOfWeek.SUNDAY))
        assertEquals(today, playWeekStart(today, DayOfWeek.MONDAY))
        assertEquals(DayOfWeek.SUNDAY, DateStyle().firstDayOfWeek)
        assertEquals(DayOfWeek.MONDAY, DateStyle(Locale.UK).firstDayOfWeek)
    }

    // ---- The calendar's bounds ----

    @Test
    fun theCursorStaysBetweenTheFirstDayPlayedAndToday() {
        val calendar = PlayCalendar(today, listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 3), LocalDate.of(2026, 11, 1)))
        assertEquals(LocalDate.of(2026, 10, 3), calendar.first)
        assertEquals(LocalDate.of(2026, 11, 1), calendar.lastPlayed)
        assertEquals(LocalDate.of(2026, 10, 3), calendar.clamp(LocalDate.of(2020, 1, 1)))
        assertEquals(today, calendar.clamp(LocalDate.of(2027, 1, 1)))
        assertEquals(LocalDate.of(2026, 10, 20), calendar.lastPlayedIn(YearMonth.of(2026, 10)))
        assertNull(calendar.lastPlayedIn(YearMonth.of(2026, 9)))
        assertNull(calendar.lastPlayedIn(YearMonth.of(2026, 12)))
        // Nothing played: the cursor stays on today.
        val none = PlayCalendar(today)
        assertEquals(today, none.first)
        assertNull(none.lastPlayed)
        assertEquals(today, none.clamp(LocalDate.of(2026, 1, 1)))
        assertEquals(calendar, PlayCalendar(today, listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 20))))
    }

    @Test
    fun everythingOpensOnThisWeeksTodayAndAGameOnTheMonthItWasLastPlayed() {
        val calendar = PlayCalendar(today, listOf(LocalDate.of(2025, 3, 4), LocalDate.of(2026, 8, 30)))
        val everything = PlayActivityPage(HostScreen.Top).opened(calendar)
        assertEquals(PlayZoom.Week, everything.zoom)
        assertEquals(today, everything.day)
        val game = PlayActivityPage(HostScreen.Top, game = "g").opened(calendar)
        assertEquals(PlayZoom.Month, game.zoom)
        assertEquals(LocalDate.of(2026, 8, 30), game.day)
        val never = PlayActivityPage(HostScreen.Top, game = "g").opened(PlayCalendar(today))
        assertEquals(PlayZoom.Month, never.zoom)
        assertEquals(today, never.day)
    }

    @Test
    fun aHistoryChangeKeepsTheCursorInsideTheNewBounds() {
        val page = page(PlayZoom.Month, LocalDate.of(2026, 10, 5), first = LocalDate.of(2026, 10, 1)).playActivity!!
        // The first day's sessions were removed: the cursor moves up to the new first day.
        val later = PlayCalendar(today, listOf(LocalDate.of(2026, 10, 12)))
        assertEquals(LocalDate.of(2026, 10, 12), page.refreshed(later).day)
        assertSame(page, page.refreshed(page.calendar!!))
    }

    // ---- The D-pad ----

    @Test
    fun weekRowsAreDaysAndItsSidesAreWholeWeeks() {
        val start = page(PlayZoom.Week, LocalDate.of(2026, 11, 4))
        assertEquals(LocalDate.of(2026, 11, 3), cursor(reduce(start, Meaning.MoveUp).first))
        assertEquals(LocalDate.of(2026, 11, 5), cursor(reduce(start, Meaning.MoveDown).first))
        assertEquals(LocalDate.of(2026, 10, 28), cursor(reduce(start, Meaning.MoveLeft).first))
        // A week on would pass today: it goes as far as today.
        assertEquals(today, cursor(reduce(start, Meaning.MoveRight).first))
        // Up from the week's first row is the last day of the week before.
        assertEquals(LocalDate.of(2026, 10, 31), cursor(reduce(page(PlayZoom.Week, LocalDate.of(2026, 11, 1)), Meaning.MoveUp).first))
    }

    @Test
    fun theMonthsCursorMovesFourWaysAndFlowsIntoTheNextMonthAtItsEdge() {
        val third = page(PlayZoom.Month, LocalDate.of(2026, 11, 3))
        assertEquals(LocalDate.of(2026, 10, 27), cursor(reduce(third, Meaning.MoveUp).first))
        assertEquals(LocalDate.of(2026, 11, 2), cursor(reduce(third, Meaning.MoveLeft).first))
        val first = page(PlayZoom.Month, LocalDate.of(2026, 11, 1))
        assertEquals(LocalDate.of(2026, 10, 31), cursor(reduce(first, Meaning.MoveLeft).first))
        val october = page(PlayZoom.Month, LocalDate.of(2026, 10, 31))
        assertEquals(LocalDate.of(2026, 11, 1), cursor(reduce(october, Meaning.MoveRight).first))
        assertEquals(LocalDate.of(2026, 11, 7), cursor(reduce(october, Meaning.MoveDown).first))
        // A held D-pad keeps travelling.
        val held = (1..10).fold(first) { at, _ -> reduce(at, Meaning.MoveLeft, repeatCount = 1).first }
        assertEquals(LocalDate.of(2026, 10, 22), cursor(held))
    }

    @Test
    fun theCursorNeverReachesTheFutureNorPassesTheFirstDayPlayed() {
        val atToday = page(PlayZoom.Month, today, first = LocalDate.of(2026, 10, 1))
        // Nothing can move it on: the page is exactly as it was.
        assertEquals(atToday, reduce(atToday, Meaning.MoveRight).first)
        assertEquals(atToday, reduce(atToday, Meaning.MoveDown).first)
        // A week down from five days ago lands on today.
        assertEquals(today, cursor(reduce(page(PlayZoom.Month, today.minusDays(5)), Meaning.MoveDown).first))
        val atFirst = page(PlayZoom.Month, LocalDate.of(2026, 10, 1), first = LocalDate.of(2026, 10, 1))
        assertEquals(atFirst, reduce(atFirst, Meaning.MoveLeft).first)
        assertEquals(atFirst, reduce(atFirst, Meaning.MoveUp).first)
        // Nothing played: today, and nowhere else.
        val nothing = PickerModel(count = 1, rowsPerPage = 1).copy(playActivity = PlayActivityPage(HostScreen.Top).opened(PlayCalendar(today)))
        listOf(Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight).forEach { assertEquals(nothing, reduce(nothing, it).first) }
        // Before the shell has read the history, the D-pad waits.
        val waiting = PickerModel(count = 1, rowsPerPage = 1).copy(playActivity = PlayActivityPage(HostScreen.Top))
        assertEquals(waiting, reduce(waiting, Meaning.MoveLeft).first)
        assertNull(reduce(waiting, Meaning.Activate).second)
    }

    @Test
    fun theYearsBarsAreMonthsAndPastJanuaryIsLastYearsDecember() {
        val march = page(PlayZoom.Year, LocalDate.of(2026, 3, 14))
        assertEquals(LocalDate.of(2026, 2, 1), cursor(reduce(march, Meaning.MoveLeft).first))
        assertEquals(LocalDate.of(2026, 4, 1), cursor(reduce(march, Meaning.MoveRight).first))
        val january = page(PlayZoom.Year, LocalDate.of(2026, 1, 20))
        assertEquals(LocalDate.of(2025, 12, 1), cursor(reduce(january, Meaning.MoveLeft).first))
        // Up and down have nothing to do on a row of bars.
        assertEquals(march, reduce(march, Meaning.MoveUp).first)
        // This month is the last bar there is.
        val now = page(PlayZoom.Year, LocalDate.of(2026, 11, 2))
        assertEquals(now, reduce(now, Meaning.MoveRight).first)
        // The first month played is the first bar: its first day is before play began, so the cursor stops at it.
        val second = page(PlayZoom.Year, LocalDate.of(2025, 4, 3), first = LocalDate.of(2025, 3, 18))
        assertEquals(LocalDate.of(2025, 3, 18), cursor(reduce(second, Meaning.MoveLeft).first))
        val firstMonth = page(PlayZoom.Year, LocalDate.of(2025, 3, 18), first = LocalDate.of(2025, 3, 18))
        assertEquals(firstMonth, reduce(firstMonth, Meaning.MoveLeft).first)
    }

    // ---- Zooms ----

    @Test
    fun l2AndR2ZoomAroundTheCursorsDateAndStopAtTheEnds() {
        val day = LocalDate.of(2026, 10, 14)
        val week = page(PlayZoom.Week, day)
        val month = reduce(week, Meaning.LetterForward).first
        assertEquals(PlayZoom.Month, month.playActivity?.zoom)
        assertEquals(day, cursor(month))
        val year = reduce(month, Meaning.LetterForward).first
        assertEquals(PlayZoom.Year, year.playActivity?.zoom)
        assertEquals(day, cursor(year))
        assertEquals(year, reduce(year, Meaning.LetterForward).first)
        assertEquals(week, reduce(week, Meaning.LetterBackward).first)
        assertEquals(PlayZoom.Week, reduce(month, Meaning.LetterBackward).first.playActivity?.zoom)
        assertEquals(day, cursor(reduce(month, Meaning.LetterBackward).first))
        // A held trigger zooms once.
        assertEquals(week, reduce(week, Meaning.LetterForward, repeatCount = 1).first)
        assertEquals(PlayZoom.Month, PlayZoom.Year.finer())
        assertNull(PlayZoom.Year.coarser())
        assertNull(PlayZoom.Week.finer())
    }

    @Test
    fun aMonthBarOpensOnItsLastPlayedDayOrItsFirstDay() {
        val played = listOf(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 22), LocalDate.of(2026, 1, 8))
        val calendar = PlayCalendar(today, played)
        fun year(day: LocalDate) = PickerModel(count = 1, rowsPerPage = 1).copy(
            playActivity = PlayActivityPage(HostScreen.Top, zoom = PlayZoom.Year, day = day, calendar = calendar),
        )
        // A on March: its last played day.
        val (march, none) = reduce(year(LocalDate.of(2026, 3, 1)), Meaning.Activate)
        assertNull(none)
        assertEquals(PlayZoom.Month, march.playActivity?.zoom)
        assertEquals(LocalDate.of(2026, 3, 22), cursor(march))
        // L2 does the same.
        assertEquals(LocalDate.of(2026, 3, 22), cursor(reduce(year(LocalDate.of(2026, 3, 1)), Meaning.LetterBackward).first))
        // A month without play opens on its first day.
        assertEquals(LocalDate.of(2026, 5, 1), cursor(reduce(year(LocalDate.of(2026, 5, 1)), Meaning.Activate).first))
        // This month without play opens on its first day, not past today.
        assertEquals(LocalDate.of(2026, 11, 1), cursor(reduce(year(LocalDate.of(2026, 11, 1)), Meaning.Activate).first))
        // A held A opens the month and does not go on to list a day.
        assertEquals(year(LocalDate.of(2026, 3, 1)), reduce(year(LocalDate.of(2026, 3, 1)), Meaning.Activate, repeatCount = 1).first)
    }

    // ---- A, B, Y ----

    @Test
    fun aOnADayAsksTheShellForItsSessionsOfThePagesGame() {
        val day = LocalDate.of(2026, 11, 3)
        val (asked, effect) = reduce(page(PlayZoom.Month, day), Meaning.Activate)
        assertEquals(Effect.OpenPlayDay(day, null), effect)
        assertNull(asked.playActivity?.sessions)
        assertEquals(Effect.OpenPlayDay(day, "g"), reduce(page(PlayZoom.Week, day, game = "g"), Meaning.Activate).second)
        // X does nothing on the chart.
        val week = page(PlayZoom.Week, day)
        assertEquals(week to null, reduce(week, Meaning.ActivateBottom))
    }

    @Test
    fun bClosesTheListFirstThenThePageAndNeverZoomsOut() {
        val year = page(PlayZoom.Year, LocalDate.of(2026, 3, 3))
        val closed = reduce(year, Meaning.Back).first
        assertNull(closed.playActivity)
        assertEquals(year.playActivity!!.grid, closed.focus)
        val listed = page(PlayZoom.Month, LocalDate.of(2026, 11, 3)).let { it.copy(playActivity = it.playActivity!!.copy(sessions = listOf("s1"))) }
        val back = reduce(listed, Meaning.Back).first.playActivity!!
        assertNull(back.sessions)
        assertEquals(PlayZoom.Month, back.zoom)
        // An open list keeps the chart where it is.
        assertEquals(listed, reduce(listed, Meaning.LetterForward).first)
        assertEquals(listed, reduce(listed, Meaning.MoveLeft).first)
        // A held B closes the list and leaves the page open.
        assertEquals(listed, reduce(listed, Meaning.Back, repeatCount = 1).first)
        listOf(Meaning.LeftPanel, Meaning.RightPanel, Meaning.View).forEach { meaning ->
            assertNull(reduce(listed, meaning).first.playActivity)
            assertNull(reduce(year, meaning).first.playActivity)
        }
    }

    @Test
    fun yOnAGamesPageOpensMoveTimeFromAndYOrBGoesBack() {
        val chart = page(PlayZoom.Month, LocalDate.of(2026, 11, 3), game = "g", sources = listOf("old", "other"))
        val moving = reduce(chart, Meaning.Options).first
        assertTrue(moving.playActivity!!.moving)
        // The D-pad is on the list now; the chart's date stays.
        assertEquals(1, reduce(moving, Meaning.MoveDown).first.playActivity?.source)
        assertEquals(LocalDate.of(2026, 11, 3), cursor(reduce(moving, Meaning.MoveLeft).first))
        val (moved, move) = reduce(moving, Meaning.Activate)
        assertEquals(Effect.MovePlayTime("old", "g"), move)
        assertEquals(setOf("old"), moved.playActivity?.moved)
        val (back, undo) = reduce(moved, Meaning.Activate)
        assertEquals(Effect.UndoPlayMove("old", "g"), undo)
        assertEquals(emptySet<String>(), back.playActivity?.moved)
        // A held A that moved does not undo it at once.
        assertEquals(moved to null, reduce(moved, Meaning.Activate, repeatCount = 1))
        assertFalse(reduce(moving, Meaning.Options).first.playActivity!!.moving)
        assertFalse(reduce(moving, Meaning.Back).first.playActivity!!.moving)
        assertTrue(reduce(moving, Meaning.Back, repeatCount = 1).first.playActivity!!.moving)
        // Everything played, or a game with nothing to take, has no list.
        val everything = page(PlayZoom.Month, LocalDate.of(2026, 11, 3))
        assertEquals(everything, reduce(everything, Meaning.Options).first)
        val alone = page(PlayZoom.Month, LocalDate.of(2026, 11, 3), game = "g")
        assertEquals(alone, reduce(alone, Meaning.Options).first)
    }

    // ---- Hints ----

    @Test
    fun hintsFollowTheZoomAndWhatTheDayHas() {
        // The tabs and the readout say how the D-pad moves, so no zoom names it on the hint row.
        val week = page(PlayZoom.Week, LocalDate.of(2026, 11, 3))
        assertEquals(listOf(PlayCopy.sessions, Copy.back), playActivityHints(week).map { it.label })
        assertEquals(listOf(PromptKey.FaceA, PromptKey.FaceB), playActivityHints(week).map { it.key })
        assertEquals(listOf(Copy.back), playActivityHints(week, hasSessions = false).map { it.label })
        assertFalse(playHoldsKey(week))
        val month = page(PlayZoom.Month, LocalDate.of(2026, 11, 3))
        assertEquals(listOf(PlayCopy.sessions, Copy.back), playActivityHints(month).map { it.label })
        assertEquals(listOf(Copy.back), playActivityHints(month, hasSessions = false).map { it.label })
        val year = page(PlayZoom.Year, LocalDate.of(2026, 11, 3))
        assertEquals(listOf(PlayCopy.open, Copy.back), playActivityHints(year, hasSessions = false).map { it.label })
        val game = page(PlayZoom.Month, LocalDate.of(2026, 11, 3), game = "g", sources = listOf("old"))
        assertEquals(listOf(PlayCopy.sessions, PlayCopy.moveTime, Copy.back), playActivityHints(game).map { it.label })
        assertEquals(PromptKey.FaceY, playActivityHints(game)[1].key)
        // Move and Remove read as one pattern: Hold, then the key and its verb. Undo is a tap.
        val moving = reduce(game, Meaning.Options).first
        assertEquals(listOf(PlayCopy.move, Copy.back), playActivityHints(moving).map { it.label })
        assertTrue(playHoldsKey(moving))
        val moved = reduce(moving, Meaning.Activate).first
        assertEquals(listOf(PlayCopy.undo, Copy.back), playActivityHints(moved).map { it.label })
        assertFalse(playHoldsKey(moved))
        val listed = week.copy(playActivity = week.playActivity!!.copy(sessions = listOf("s1")))
        assertTrue(playHoldsKey(listed))
        assertEquals("Moved here · 2h", PlayCopy.movedAmount(2 * HOUR))
    }

    // ---- Copy ----

    @Test
    fun periodsAreNamedInTheDevicesOrder() {
        val us = DateStyle()
        assertEquals(PlayCopy.thisWeek, playPeriodLabel(PlayZoom.Week, today, today, us))
        assertEquals("Oct 25 – Oct 31", playPeriodLabel(PlayZoom.Week, LocalDate.of(2026, 10, 28), today, us))
        assertEquals("26 Oct – 1 Nov", playPeriodLabel(PlayZoom.Week, LocalDate.of(2026, 10, 28), today, gb))
        assertEquals("Dec 28 – Jan 3, 2026", playPeriodLabel(PlayZoom.Week, LocalDate.of(2025, 12, 30), LocalDate.of(2027, 2, 1), us))
        assertEquals("November 2026", playPeriodLabel(PlayZoom.Month, today, today, us))
        assertEquals("2026", playPeriodLabel(PlayZoom.Year, today, today, us))
        assertEquals("No play in November", playPeriodEmpty(PlayZoom.Month, today, today, us))
        assertEquals("No play in March 2025", playPeriodEmpty(PlayZoom.Month, LocalDate.of(2025, 3, 2), today, us))
        assertEquals("No play in 2025", playPeriodEmpty(PlayZoom.Year, LocalDate.of(2025, 3, 2), today, us))
        assertEquals(PlayCopy.nothing, playPeriodEmpty(PlayZoom.Week, today, today, us))
        assertEquals(PlayCopy.nothingThen, playPeriodEmpty(PlayZoom.Week, LocalDate.of(2026, 10, 1), today, us))
    }

    @Test
    fun theReadoutSaysWhatAHoverWould() {
        val us = DateStyle()
        val day = LocalDate.of(2026, 11, 3)
        assertEquals("Tue, Nov 3 · 2h 10m · 3 sessions", playDayReadout(day, 130 * MINUTE, 3, us))
        assertEquals("Tue, Nov 3 · 45m · 1 session", playDayReadout(day, 45 * MINUTE, 1, us))
        // Time Android alone saw has no sessions to count.
        assertEquals("Tue, Nov 3 · 45m", playDayReadout(day, 45 * MINUTE, 0, us))
        assertEquals("Tue, Nov 3 · No play", playDayReadout(day, 0L, 0, us))
        assertEquals("Tue 3 Nov · No play", playDayReadout(day, 0L, 0, gb))
        assertEquals("March · 12h 40m · 9 days", playMonthReadout(java.time.Month.MARCH, 760 * MINUTE, 9, us))
        assertEquals("March · 1h · 1 day", playMonthReadout(java.time.Month.MARCH, HOUR, 1, us))
        assertEquals("March · No play", playMonthReadout(java.time.Month.MARCH, 0L, 0, us))
    }

    @Test
    fun aGamesFactsSayWhenInShortWords() {
        val us = DateStyle()
        assertEquals("Today", playWhen(today, today, us))
        assertEquals("Yesterday", playWhen(today.minusDays(1), today, us))
        assertEquals("Oct 3", playWhen(LocalDate.of(2026, 10, 3), today, us))
        assertEquals("Mar 4, 2024", playWhen(LocalDate.of(2024, 3, 4), today, us))
        assertEquals("–", playWhen(null, today, us))
        assertEquals("Week", PlayCopy.zoom(PlayZoom.Week))
        assertEquals("Month", PlayCopy.zoom(PlayZoom.Month))
        assertEquals("Year", PlayCopy.zoom(PlayZoom.Year))
    }

    @Test
    fun aShortDayStillSplitsAtItsOwnMidnight() {
        // New York's clocks went back on Nov 1, 2026: that day had 25 hours.
        val york = ZoneId.of("America/New_York")
        val start = LocalDateTime.of(2026, 11, 1, 23, 0).atZone(york).toInstant().toEpochMilli()
        val history = PlayHistory(listOf(PlaySpan("a", start, start + 2 * HOUR)), york)
        assertEquals(HOUR, history.dayTotal(LocalDate.of(2026, 11, 1)))
        assertEquals(HOUR, history.dayTotal(LocalDate.of(2026, 11, 2)))
        assertEquals(24f / 25f, history.dayBlocks(LocalDate.of(2026, 11, 1)).single().start, 0.0001f)
    }

    @Test
    fun aMonthBarNeverOpensOnADayPastToday() {
        // Play logged ahead of a clock that was later set back: Nov 20 is in this month but past today.
        val calendar = PlayCalendar(today, listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 11, 20)))
        val year = PickerModel(count = 1, rowsPerPage = 1).copy(
            playActivity = PlayActivityPage(HostScreen.Top, zoom = PlayZoom.Year, day = LocalDate.of(2026, 11, 1), calendar = calendar),
        )
        assertEquals(today, cursor(reduce(year, Meaning.Activate).first))
        assertEquals(today, cursor(reduce(year, Meaning.LetterBackward).first))
        // Nor does the page open on it.
        assertEquals(LocalDate.of(2026, 10, 2), PlayActivityPage(HostScreen.Top, game = "g").opened(calendar).day)
    }

    @Test
    fun aNewDayOrZoneMovesTheBoundsAndTheCursorWithThem() {
        val page = page(PlayZoom.Month, today, first = LocalDate.of(2026, 10, 1)).playActivity!!
        // Midnight passed while the page was open: tomorrow is reachable, and the cursor stays where it was.
        val tomorrow = PlayCalendar(today.plusDays(1), listOf(LocalDate.of(2026, 10, 1)))
        val later = page.refreshed(tomorrow)
        assertEquals(today.plusDays(1), later.calendar?.today)
        assertEquals(today, later.day)
        // A zone west of here can make today yesterday: the cursor comes back inside the bounds.
        val yesterday = PlayCalendar(today.minusDays(1), listOf(LocalDate.of(2026, 10, 1)))
        assertEquals(today.minusDays(1), page.refreshed(yesterday).day)
    }

    @Test
    fun anOpenListKeepsOnlyTheSessionsStillOnItsDayAndClosesWhenNoneAre() {
        val open = page(PlayZoom.Month, LocalDate.of(2026, 10, 1), first = LocalDate.of(2026, 10, 1)).playActivity!!
            .copy(sessions = listOf("a", "b", "c"), session = 2)
        // The first day's sessions went: the cursor moves to the new first day, which has none of them.
        val moved = PlayCalendar(today, listOf(LocalDate.of(2026, 10, 5)))
        val closed = open.refreshed(moved) { day -> if (day == LocalDate.of(2026, 10, 5)) listOf("x") else emptyList() }
        assertEquals(LocalDate.of(2026, 10, 5), closed.day)
        assertNull(closed.sessions)
        assertEquals(0, closed.session)
        // The day stays, one of its sessions went elsewhere: the list keeps the rest and its focus stays on a row.
        val same = PlayCalendar(today, listOf(LocalDate.of(2026, 9, 1)))
        val kept = open.refreshed(same) { listOf("a", "b") }
        assertEquals(listOf("a", "b"), kept.sessions)
        assertEquals(1, kept.session)
    }

    @Test
    fun aGameFoldingIntoATileWhileItsPageIsOpenReadsTheTilesHistory() {
        val open = page(PlayZoom.Month, LocalDate.of(2026, 10, 3), first = LocalDate.of(2026, 10, 3), game = "copy", sources = listOf("other")).playActivity!!
            .copy(sessions = listOf("s1", "s2"), session = 1, moving = false)
        // The tile has older play, so its first day is earlier; only s2 is the tile's on that day.
        val tile = PlayCalendar(today, listOf(LocalDate.of(2025, 1, 1)))
        val folded = open.folded("tile", listOf("other"), tile) { listOf("s2") }
        assertEquals("tile", folded.game)
        assertEquals(LocalDate.of(2025, 1, 1), folded.calendar?.first)
        assertEquals(LocalDate.of(2026, 10, 3), folded.day)
        assertEquals(listOf("s2"), folded.sessions)
        assertEquals(0, folded.session)
        // With nothing left to move, Move time from closes.
        val moving = open.copy(sessions = null, moving = true)
        assertFalse(moving.folded("tile", emptyList(), tile) { emptyList() }.moving)
        assertTrue(moving.folded("tile", listOf("other"), tile) { emptyList() }.moving)
    }

    @Test
    fun aNewTimeZoneRefreshesThePageEvenWhenThePlayedDatesStayTheSame() {
        val la = ZoneId.of("America/Los_Angeles")
        val ny = ZoneId.of("America/New_York")
        val played = listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 3))
        val open = page(PlayZoom.Month, LocalDate.of(2026, 11, 3)).playActivity!!
            .copy(calendar = PlayCalendar(today, played, la), sessions = listOf("late"), session = 0)
        // Same dates, another zone: the late session now falls on the next day, so the list lets it go.
        val moved = open.refreshed(PlayCalendar(today, played, ny)) { listOf("early") }
        assertEquals(ny, moved.calendar?.zone)
        assertNull(moved.sessions)
        assertFalse(PlayCalendar(today, played, la) == PlayCalendar(today, played, ny))
    }

    @Test
    fun anOpenListLetsGoOfASessionThatLeftItsDayUnderTheSameCalendar() {
        val open = page(PlayZoom.Month, LocalDate.of(2026, 11, 3)).playActivity!!.copy(sessions = listOf("a", "b"), session = 1)
        val same = open.calendar!!
        assertEquals(listOf("a"), open.refreshed(same) { listOf("a") }.sessions)
        assertEquals(0, open.refreshed(same) { listOf("a") }.session)
        // Nothing changed: the same page.
        assertSame(open, open.refreshed(same) { listOf("a", "b") })
    }

    @Test
    fun aFoldKeepsTheRowsMovedThisVisitSoTheyCanStillGoBack() {
        val open = page(PlayZoom.Month, LocalDate.of(2026, 11, 3), game = "copy", sources = listOf("a", "b")).playActivity!!
            .copy(moved = setOf("a"), source = 1, moving = true)
        // "a" gave all its time here, so a fresh read no longer lists it; a new source "c" turned up.
        val folded = open.folded("tile", listOf("b", "c"), PlayCalendar(today, listOf(LocalDate.of(2026, 10, 1)))) { emptyList() }
        assertEquals(listOf("a", "b", "c"), folded.sources)
        assertEquals(setOf("a"), folded.moved)
        assertEquals(1, folded.source)
        assertTrue(folded.moving)
    }

    @Test
    fun theChevronsLightOnlyWhereThereIsAPeriodToReach() {
        val calendar = PlayCalendar(today, listOf(LocalDate.of(2025, 3, 4)))
        // November 2026: earlier months to reach, nothing after today.
        assertEquals(true to false, playPeriodReach(calendar, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        // March 2025 holds the first day played: nothing earlier.
        assertEquals(false to true, playPeriodReach(calendar, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31)))
        assertEquals(false to false, playPeriodReach(PlayCalendar(today), LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14)))
        assertEquals(false to false, playPeriodReach(null, today, today))
    }

    @Test
    fun yearsStripShowsTheYearsAroundTheShownOneThatFit() {
        val width = { _: Int -> 100f }
        // Every year fits.
        assertEquals(2024..2026, playYearWindow(2024, 2026, 2026, room = 400f, gap = 10f, width = width))
        // Room for three of seven: the shown year and one each side.
        assertEquals(2019..2021, playYearWindow(2016, 2022, 2020, room = 330f, gap = 10f, width = width))
        // At an end, the room goes to the other side.
        assertEquals(2020..2022, playYearWindow(2016, 2022, 2022, room = 330f, gap = 10f, width = width))
        assertEquals(2016..2018, playYearWindow(2016, 2022, 2016, room = 330f, gap = 10f, width = width))
        // Room for two: the later side first.
        assertEquals(2020..2021, playYearWindow(2016, 2022, 2020, room = 215f, gap = 10f, width = width))
        // Never less than the shown year.
        assertEquals(2020..2020, playYearWindow(2016, 2022, 2020, room = 50f, gap = 10f, width = width))
    }

    private fun page(
        zoom: PlayZoom,
        day: LocalDate,
        first: LocalDate = LocalDate.of(2024, 6, 1),
        game: String? = null,
        sources: List<String> = emptyList(),
    ): PickerModel {
        val grid = GridFocus(cellIndex = 3)
        return PickerModel(count = 8, rowsPerPage = 2, focus = grid).copy(
            playActivity = PlayActivityPage(HostScreen.Top, grid = grid, zoom = zoom, day = day,
                calendar = PlayCalendar(today, listOf(first)), game = game, sources = sources),
        )
    }

    private fun cursor(model: PickerModel): LocalDate = model.playActivity!!.cursor

    private fun span(game: String, y1: Int, m1: Int, d1: Int, h1: Int, n1: Int, y2: Int, m2: Int, d2: Int, h2: Int, n2: Int, session: String = ""): PlaySpan =
        PlaySpan(game, millis(y1, m1, d1, h1, n1), millis(y2, m2, d2, h2, n2), session)

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
    }
}
