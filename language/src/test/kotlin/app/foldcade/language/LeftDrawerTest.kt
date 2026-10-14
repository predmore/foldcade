package app.foldcade.language

import org.junit.Assert.assertEquals
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
                Row.PlayActivity, Row.Home, Row.AllLibrary, Row.AndroidGames, Row.Apps,
                Row.EditHome, Row.Settings,
            ),
            panelRows(opened.panel!!, opened),
        )
        val (home, effect) = reduce(at(opened, Row.Home), Meaning.Activate)
        assertEquals(Effect.GoHome, effect)
        assertNull(home.panel)
    }

    @Test
    fun editHomeHoldsTheFocusedTilesActionsThenTheBoardsSettings() {
        val opened = reduce(model(), Meaning.LeftPanel).first
        val edit = reduce(at(opened, Row.EditHome), Meaning.Activate).first
        assertEquals(PanelLevel.EditHome, edit.panel?.level)
        assertEquals(
            listOf(Row.MoveTile, Row.AddToCollection, Row.RenameFolder, Row.AddNewGames, Row.HiddenApps),
            panelRows(edit.panel!!, edit),
        )
        // Add to, then Back, returns to Edit home on Add to, then to L1 on Edit home.
        val moveTo = reduce(at(edit, Row.AddToCollection), Meaning.Activate).first
        assertEquals(PanelLevel.Collections, moveTo.panel?.level)
        val back = reduce(moveTo, Meaning.Back).first
        assertEquals(PanelLevel.EditHome, back.panel?.level)
        assertEquals(Row.AddToCollection, panelRows(back.panel!!, back)[back.panel!!.index])
        val root = reduce(back, Meaning.Back).first
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
        assertEquals(listOf(PromptKey.DpadSides, PromptKey.FaceB), playActivityHints(page).map { it.key })
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
        val week = playWeek(spans, now, zone, locale = Locale.US)
        assertEquals(PlayCopy.pastWeek, week.label)
        assertEquals(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"), week.days.map { it.label })
        assertEquals(3_600_000L, week.days[5].totalMillis)
        assertEquals(5_400_000L, week.days[6].totalMillis)
        assertEquals(listOf("a" to 7_200_000L, "b" to 1_800_000L), week.top)
        assertEquals(9_000_000L, week.totalMillis)
        val friday = week.days[5].blocks.single()
        assertEquals(23f / 24f, friday.start, 0.0001f)
        assertEquals(1f, friday.endInclusive, 0.0001f)
        val earlier = playWeek(spans, now, zone, weeksBack = 1, locale = Locale.US)
        assertEquals("Sep 27 – Oct 3", earlier.label)
        assertEquals(listOf("c" to 10_800_000L), earlier.top)
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
