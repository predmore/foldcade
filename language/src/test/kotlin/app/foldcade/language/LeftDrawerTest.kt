package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class LeftDrawerTest {
    private val tile = TileActions(move = true, collections = listOf(CollectionChoice(HOME_FAVORITES, "Favorites", false)), rename = true)

    @Test
    fun lOneIsPlayThenWhereToGoThenEditHomeAndSettings() {
        val opened = reduce(model(), Meaning.LeftPanel).first
        assertEquals(
            listOf(
                Row.PlayActivity, Row.Home, Row.AllLibrary, Row.Apps,
                Row.EditHome, Row.Settings,
            ),
            panelRows(opened.panel!!, opened),
        )
        val (home, effect) = reduce(at(opened, Row.Home), Meaning.Activate)
        assertEquals(Effect.GoHome, effect)
        assertNull(home.panel)
    }

    @Test
    fun editHomeHoldsTheBoardsSettingsAndBackReturnsToL1() {
        val opened = reduce(model(), Meaning.LeftPanel).first
        val edit = reduce(at(opened, Row.EditHome), Meaning.Activate).first
        assertEquals(PanelLevel.EditHome, edit.panel?.level)
        // The focused tile's actions are Y's, so Edit home names no tile.
        assertFalse(PanelLevel.EditHome.actsOnTile)
        assertEquals(listOf(Row.RecentOnHome, Row.AddNewGames, Row.Folders, Row.Hidden), panelRows(edit.panel!!, edit))
        val root = reduce(edit, Meaning.Back).first
        assertEquals(PanelLevel.Root, root.panel?.level)
        assertEquals(Row.EditHome, panelRows(root.panel!!, root)[root.panel!!.index])
    }

    @Test
    fun yOpensTheTilesMenuOnTheLeftAndAddToComesBackToIt() {
        val actions = reduce(model(), Meaning.Options).first
        assertEquals(Side.Left, actions.panel?.side)
        assertEquals(PanelLevel.Actions, actions.panel?.level)
        val moveTo = reduce(at(actions, Row.AddToCollection), Meaning.Activate).first
        val back = reduce(moveTo, Meaning.Back).first
        assertEquals(PanelLevel.Actions, back.panel?.level)
        assertEquals(Row.AddToCollection, panelRows(back.panel!!, back)[back.panel!!.index])
    }

    @Test
    fun upFromTheTopRowReachesThePillOnThatSide() {
        val left = reduce(model(), Meaning.MoveUp).first
        assertEquals(Chrome.LeftIsland, left.focus.chrome)
        val right = reduce(model().copy(focus = GridFocus(cellIndex = GridStrip.index(Metrics.columns / 2, 0))), Meaning.MoveUp).first
        assertEquals(Chrome.StatusCluster, right.focus.chrome)
        assertEquals(Chrome.StatusCluster, reduce(left, Meaning.MoveRight).first.focus.chrome)
        assertEquals(Chrome.LeftIsland, reduce(right, Meaning.MoveLeft).first.focus.chrome)
        val opened = reduce(left, Meaning.Activate).first
        assertEquals(Side.Left, opened.panel?.side)
        // Down from a pill comes back to the column it left.
        assertEquals(GridFocus(cellIndex = 0, lastColumn = 0), reduce(left, Meaning.MoveDown).first.focus)
    }

    @Test
    fun thePlayPageStepsByWeeksAndClosesBackToTheGrid() {
        val opened = reduce(model(), Meaning.LeftPanel).first
        val page = reduce(at(opened, Row.PlayActivity), Meaning.Activate).first
        assertNull(page.panel)
        assertEquals(PlayActivityPage(HostScreen.Top, 0, opened.panel!!.grid), page.playActivity)
        val older = reduce(reduce(page, Meaning.MoveLeft).first, Meaning.MoveLeft).first
        assertEquals(2, older.playActivity?.weeksBack)
        val newer = reduce(page, Meaning.MoveRight).first
        assertEquals(0, newer.playActivity?.weeksBack)
        listOf(Meaning.Back, Meaning.LeftPanel, Meaning.RightPanel).forEach { meaning ->
            val closed = reduce(older, meaning).first
            assertNull(closed.playActivity)
            assertEquals(opened.panel!!.grid, closed.focus)
        }
        assertEquals(listOf(PromptKey.FaceA, PromptKey.DpadSides, PromptKey.FaceB), playActivityHints(page).map { it.key })
        assertEquals(listOf(PlayCopy.sessions, PlayCopy.week, Copy.back), playActivityHints(page).map { it.label })
    }

    @Test
    fun upAndDownMoveOverTheDaysAndAListsOne() {
        val page = reduce(at(reduce(model(), Meaning.LeftPanel).first, Row.PlayActivity), Meaning.Activate).first
        // Today, the last row, to start; down stays there.
        assertEquals(PLAY_WEEK_DAYS - 1, page.playActivity?.day)
        assertEquals(PLAY_WEEK_DAYS - 1, reduce(page, Meaning.MoveDown).first.playActivity?.day)
        val saturday = reduce(page, Meaning.MoveUp).first
        assertEquals(PLAY_WEEK_DAYS - 2, saturday.playActivity?.day)
        val top = (1..10).fold(page) { at, _ -> reduce(at, Meaning.MoveUp).first }
        assertEquals(0, top.playActivity?.day)
        // A week back keeps the day.
        val older = reduce(saturday, Meaning.MoveLeft).first.playActivity!!
        assertEquals(1, older.weeksBack)
        assertEquals(PLAY_WEEK_DAYS - 2, older.day)
        // A asks the shell for that day's sessions; the page waits for them.
        val (asked, effect) = reduce(saturday.copy(playActivity = older), Meaning.Activate)
        assertEquals(Effect.OpenPlayDay(1, PLAY_WEEK_DAYS - 2), effect)
        assertNull(asked.playActivity?.sessions)
        // X does nothing on the week.
        val (unchanged, none) = reduce(saturday, Meaning.ActivateBottom)
        assertEquals(saturday.playActivity, unchanged.playActivity)
        assertNull(none)
    }

    @Test
    fun onADaysListXRemovesTheFocusedSessionAndBGoesBackToTheWeek() {
        val page = reduce(at(reduce(model(), Meaning.LeftPanel).first, Row.PlayActivity), Meaning.Activate).first
        val listed = page.copy(playActivity = page.playActivity!!.copy(day = 3, sessions = listOf("s1", "s2", "s3")))
        assertEquals(listOf(PromptKey.FaceX, PromptKey.FaceB), playActivityHints(listed).map { it.key })
        val calibrated = listed.copy(faceMap = faceMapWithConfirm(FaceMap.standard(), PromptKey.FaceX))
        assertEquals(actionFaces(calibrated.faceMap).bottomGlyph, playActivityHints(calibrated).first().key)
        assertEquals(PromptKey.FaceA, playActivityHints(calibrated).first().key)
        assertEquals(listOf(PlayCopy.remove, Copy.back), playActivityHints(listed).map { it.label })
        val second = reduce(listed, Meaning.MoveDown).first
        assertEquals(1, second.playActivity?.session)
        assertEquals(2, reduce(reduce(second, Meaning.MoveDown).first, Meaning.MoveDown).first.playActivity?.session)
        assertEquals(0, reduce(listed, Meaning.MoveUp).first.playActivity?.session)
        // Left and right do not change the week under an open list.
        assertEquals(second.playActivity, reduce(second, Meaning.MoveLeft).first.playActivity)
        val (removed, effect) = reduce(second, Meaning.ActivateBottom)
        assertEquals(Effect.RemovePlaySession("s2"), effect)
        assertEquals(listOf("s1", "s3"), removed.playActivity?.sessions)
        assertEquals(1, removed.playActivity?.session)
        // The last row gone, the focus stays on the new last row.
        val last = reduce(reduce(removed, Meaning.MoveDown).first, Meaning.ActivateBottom).first
        assertEquals(listOf("s1"), last.playActivity?.sessions)
        assertEquals(0, last.playActivity?.session)
        // The day emptied, the week shows again on that day.
        val emptied = reduce(last, Meaning.ActivateBottom).first.playActivity!!
        assertNull(emptied.sessions)
        assertEquals(3, emptied.day)
        val back = reduce(second, Meaning.Back).first
        assertNull(back.playActivity?.sessions)
        assertEquals(3, back.playActivity?.day)
        listOf(Meaning.LeftPanel, Meaning.RightPanel, Meaning.View).forEach { meaning ->
            val closed = reduce(second, meaning).first
            assertNull(closed.playActivity)
            assertEquals(page.playActivity!!.grid, closed.focus)
        }
    }

    @Test
    fun aDaysSessionsAreWholeAndInTheOrderTheyStarted() {
        val zone = ZoneOffset.UTC
        val now = millis(2026, 10, 10, 20, 0, zone)
        val spans = listOf(
            // Left running overnight: Friday 21:00 to Saturday 12:00, in two stretches.
            PlaySpan("a", millis(2026, 10, 9, 21, 0, zone), millis(2026, 10, 9, 23, 0, zone), "night"),
            PlaySpan("a", millis(2026, 10, 9, 23, 30, zone), millis(2026, 10, 10, 12, 0, zone), "night"),
            PlaySpan("b", millis(2026, 10, 10, 13, 0, zone), millis(2026, 10, 10, 13, 45, zone), "lunch"),
            PlaySpan("c", millis(2026, 10, 10, 6, 0, zone), millis(2026, 10, 10, 6, 10, zone), "early"),
            // Spans from before session ids were kept have nothing to remove by.
            PlaySpan("d", millis(2026, 10, 10, 8, 0, zone), millis(2026, 10, 10, 9, 0, zone)),
        )
        val saturday = playDaySessions(spans, now, zone, 0, PLAY_WEEK_DAYS - 1)
        assertEquals(listOf("night", "early", "lunch"), saturday.map { it.sessionId })
        val night = saturday.first()
        assertEquals("a", night.gameId)
        assertEquals(millis(2026, 10, 9, 21, 0, zone), night.startMillis)
        assertEquals(millis(2026, 10, 10, 12, 0, zone), night.endMillis)
        assertEquals(2 * 3_600_000L + 12 * 3_600_000L + 30 * 60_000L, night.activeMillis)
        assertEquals(listOf("night"), playDaySessions(spans, now, zone, 0, PLAY_WEEK_DAYS - 2).map { it.sessionId })
        assertEquals(emptyList<DaySession>(), playDaySessions(spans, now, zone, 1, PLAY_WEEK_DAYS - 1))
        assertEquals(emptyList<DaySession>(), playDaySessions(spans, now, zone, 0, PLAY_WEEK_DAYS))
    }

    @Test
    fun theWeekSplitsAtMidnightAndListsTheMostPlayed() {
        val zone = ZoneOffset.UTC
        val now = millis(2026, 10, 10, 20, 0, zone)
        val spans = listOf(
            // Friday 23:00 to Saturday 01:00: an hour on each day.
            PlaySpan("a", millis(2026, 10, 9, 23, 0, zone), millis(2026, 10, 10, 1, 0, zone)),
            PlaySpan("b", millis(2026, 10, 10, 12, 0, zone), millis(2026, 10, 10, 12, 30, zone)),
            // Eight days ago is outside this week.
            PlaySpan("c", millis(2026, 10, 2, 12, 0, zone), millis(2026, 10, 2, 15, 0, zone)),
        )
        val week = playWeek(spans, now, zone, DateStyle())
        assertEquals(PlayCopy.pastWeek, week.label)
        assertEquals(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"), week.days.map { it.label })
        assertEquals(3_600_000L, week.days[5].totalMillis)
        assertEquals(5_400_000L, week.days[6].totalMillis)
        assertEquals(listOf("a" to 7_200_000L, "b" to 1_800_000L), week.top)
        assertEquals(9_000_000L, week.totalMillis)
        val friday = week.days[5].blocks.single()
        assertEquals(23f / 24f, friday.start, 0.0001f)
        assertEquals(1f, friday.endInclusive, 0.0001f)
        val earlier = playWeek(spans, now, zone, DateStyle(), weeksBack = 1)
        assertEquals("Sep 27 – Oct 3", earlier.label)
        assertEquals(listOf("c" to 10_800_000L), earlier.top)
    }

    @Test
    fun anEarlierWeekReadsInTheDevicesDateOrder() {
        val zone = ZoneOffset.UTC
        val now = millis(2026, 11, 20, 20, 0, zone)
        // What Android's best patterns give for en-GB.
        val gb = DateStyle(Locale.UK, monthDayPattern = "d MMM", monthDayYearPattern = "d MMM y", is24Hour = true)
        assertEquals("7 Nov – 13 Nov", playWeek(emptyList(), now, zone, gb, weeksBack = 1).label)
        assertEquals("Nov 7 – Nov 13", playWeek(emptyList(), now, zone, DateStyle(), weeksBack = 1).label)
    }

    @Test
    fun todayCountsOnlyTimeSinceMidnight() {
        val zone = ZoneOffset.UTC
        val now = millis(2026, 10, 10, 20, 0, zone)
        val spans = listOf(
            // Friday 23:00 to Saturday 01:00: only the hour after midnight.
            PlaySpan("a", millis(2026, 10, 9, 23, 0, zone), millis(2026, 10, 10, 1, 0, zone)),
            PlaySpan("b", millis(2026, 10, 10, 12, 0, zone), millis(2026, 10, 10, 12, 30, zone)),
            PlaySpan("c", millis(2026, 10, 8, 12, 0, zone), millis(2026, 10, 8, 15, 0, zone)),
        )
        assertEquals(5_400_000L, playToday(spans, now, zone))
        assertEquals(0L, playToday(emptyList(), now, zone))
    }

    @Test
    fun durationsAreHoursAndMinutes() {
        assertEquals("0m", playDuration(0L))
        assertEquals("<1m", playDuration(30_000L))
        assertEquals("45m", playDuration(45 * 60_000L))
        assertEquals("2h", playDuration(2 * 3_600_000L))
        assertEquals("6h 40m", playDuration(400 * 60_000L))
    }

    private fun model() = PickerModel(count = 8, rowsPerPage = 2, tileActions = tile)

    private fun at(model: PickerModel, row: Row): PickerModel {
        val panel = model.panel!!
        return model.copy(panel = panel.copy(index = panelRows(panel, model).indexOf(row)))
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
}
