package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

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
        assertEquals(listOf(Row.RecentOnHome, Row.AddNewGames, Row.Folders, Row.Hidden), panelRows(edit.panel!!, edit))
        val root = reduce(edit, Meaning.Back).first
        assertEquals(PanelLevel.Root, root.panel?.level)
        assertEquals(Row.EditHome, panelRows(root.panel!!, root)[root.panel!!.index])
    }

    @Test
    fun yOpensTheTilesMenuBesideItOnTheGridsScreenAndAddToComesBackToIt() {
        val actions = reduce(model(), Meaning.Options).first
        assertEquals(Side.Tile, actions.panel?.side)
        assertEquals(PanelLevel.Actions, actions.panel?.level)
        // The grid's screen in every layout, never the hero's where L1's island is.
        assertEquals(ScreenLayout.ShowcaseTop.gridScreen, actions.panel?.screen)
        ScreenLayout.entries.forEach { layout ->
            assertEquals(layout.gridScreen, reduce(model().copy(layout = layout), Meaning.Options).first.panel?.screen)
        }
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
