package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A shelf of any length stays two lines and scrolls sideways as the grid does, never the page. */
class SettingsShelfTest {
    private val names = listOf(
        "aPS3e", "ARMSX2", "Azahar", "Dolphin", "DuckStation", "Eden", "Flycast", "melonDS",
        "M64Plus", "Pizza Boy", "PPSSPP", "RetroArch", "Saturn", "Snes9x EX", "GBA.emu", "MD.emu",
    )

    /** The shelf's order with every emulator syncing: A to Z. */
    private val order = names.sortedBy { it.lowercase() }

    /** [count] emulators, A to Z, the ones in [unsynced] not syncing. */
    private fun shelf(count: Int, unsynced: Set<String> = emptySet()): PickerModel = PickerModel(
        count = 4,
        rowsPerPage = 1,
        homeRoleHeld = true,
        playerSaves = order.take(count).map { name ->
            PlayerSaveSetting(name.lowercase(), name, chosen = name !in unsynced, installed = true, packages = listOf("pkg.${name.lowercase()}"))
        },
    )

    private fun tile(name: String) = Row.SettingsTile(SettingsInner.Emulator(name.lowercase()))

    private fun PickerModel.onShelf(row: Int = 0): PickerModel {
        val category = settingsCategories(this).indexOf(SettingsCategory.Players)
        return copy(settings = SettingsPage(HostScreen.Top, category = category, row = row, onRows = true))
    }

    private fun PickerModel.press(vararg meanings: Meaning): PickerModel =
        meanings.fold(this) { model, meaning -> reduce(model, meaning, HostScreen.Top).first }

    @Test
    fun aFewTilesSitOnOneLineAndMoreOnTwo() {
        assertEquals(1, SettingsShelf.lines(1))
        assertEquals(1, SettingsShelf.lines(4))
        assertEquals(2, SettingsShelf.lines(5))
        assertEquals(2, SettingsShelf.lines(16))
        assertEquals(4, SettingsShelf.columns(8))
        assertFalse(SettingsShelf.scrolls(8))
        assertTrue(SettingsShelf.scrolls(9))
        assertEquals(8, SettingsShelf.columns(16))
        assertEquals(4, SettingsShelf.lastFirst(16))
        assertEquals(0, SettingsShelf.lastFirst(3))
    }

    @Test
    fun theStripFollowsTheFocusAColumnAtATimeAndKeepsItsPlaceOnTheRows() {
        val model = shelf(16).onShelf()
        assertEquals(0, model.settings!!.shelfFirst)
        // Three columns right stay on the first screen; the fourth scrolls one column.
        val third = model.press(Meaning.MoveRight, Meaning.MoveRight, Meaning.MoveRight)
        assertEquals(tile(order[6]), settingsFocus(third))
        assertEquals(0, third.settings!!.shelfFirst)
        val fourth = third.press(Meaning.MoveRight)
        assertEquals(tile(order[8]), settingsFocus(fourth))
        assertEquals(1, fourth.settings!!.shelfFirst)
        // The far end, then Right stays.
        val end = fourth.press(Meaning.MoveRight, Meaning.MoveRight, Meaning.MoveRight, Meaning.MoveRight, Meaning.MoveRight)
        assertEquals(tile(order[14]), settingsFocus(end))
        assertEquals(4, end.settings!!.shelfFirst)
        // Down to the bottom line, then to the rows: the strip stays where it was.
        val rows = end.press(Meaning.MoveDown, Meaning.MoveDown)
        assertEquals(Row.SaveSetup, settingsFocus(rows))
        assertEquals(4, rows.settings!!.shelfFirst)
        // Up lands on the bottom tile of the first column on screen, so the strip does not jump.
        val up = rows.press(Meaning.MoveUp)
        assertEquals(tile(order[9]), settingsFocus(up))
        assertEquals(4, up.settings!!.shelfFirst)
        // Left walks back, scrolling once it passes the left edge, and leaves from the first column.
        val left = up.press(Meaning.MoveLeft)
        assertEquals(tile(order[7]), settingsFocus(left))
        assertEquals(3, left.settings!!.shelfFirst)
        val start = left.press(Meaning.MoveLeft, Meaning.MoveLeft, Meaning.MoveLeft)
        assertEquals(tile(order[1]), settingsFocus(start))
        assertEquals(0, start.settings!!.shelfFirst)
        assertFalse(start.press(Meaning.MoveLeft).settings!!.onRows)
    }

    @Test
    fun aTilesPageKeepsTheStripForTheWayBack() {
        val far = shelf(16).onShelf().press(*Array(6) { Meaning.MoveRight })
        val tile = settingsFocus(far)
        val first = far.settings!!.shelfFirst
        val page = far.press(Meaning.Activate)
        assertTrue(page.settings!!.inner != null)
        val back = page.press(Meaning.Back)
        assertEquals(tile, settingsFocus(back))
        assertEquals(first, back.settings!!.shelfFirst)
    }

    @Test
    fun anotherCategoryStartsAtItsFirstColumn() {
        val far = shelf(16).onShelf().press(*Array(7) { Meaning.MoveRight })
        assertEquals(4, far.settings!!.shelfFirst)
        val categories = far.press(Meaning.Back)
        val other = categories.press(Meaning.MoveUp)
        assertEquals(0, other.settings!!.shelfFirst)
    }

    @Test
    fun emulatorsThatAreNotSyncingLeadTheShelf() {
        val model = shelf(16, unsynced = setOf("RetroArch", "Dolphin"))
        val tiles = settingsRows(SettingsCategory.Players, model).takeWhile { it.settingsTile() }
        assertEquals(listOf(tile("Dolphin"), tile("RetroArch")), tiles.take(2))
        assertEquals(TileStatus.Attention, settingsTileStatus(tiles[0], model))
        // The others stay A to Z after them.
        assertEquals(tile("aPS3e"), tiles[2])
        assertEquals(16, tiles.size)
    }

    @Test
    fun theFocusStaysOnItsTileWhenTheShelfReordersUnderIt() {
        val before = shelf(9, unsynced = setOf("Flycast")).onShelf(row = 0)
        assertEquals(tile("Flycast"), settingsFocus(before))
        // Flycast starts syncing: its tile leaves the front for its place A to Z, and the focus goes with it.
        val after = before.copy(playerSaves = before.playerSaves.map { it.copy(chosen = true) })
        val kept = retainSettingsShelf(before, after)
        assertEquals(tile("Flycast"), settingsFocus(kept))
        assertEquals(order.indexOf("Flycast"), kept.settings!!.row)
        assertEquals(0, kept.settings!!.shelfFirst)
    }

    @Test
    fun aSwipeScrollsTheStripAndBringsTheFocusAlong() {
        val model = shelf(16).onShelf(row = 1)
        val swiped = model.settings!!.scrolledShelf(3, model)
        assertEquals(3, swiped.shelfFirst)
        // The bottom tile of the first column, now off screen, hands the focus to the bottom tile of column 3.
        assertEquals(7, swiped.row)
        // Past the end it stops at the last screen.
        assertEquals(4, model.settings!!.scrolledShelf(9, model).shelfFirst)
        // Focus on a row stays there.
        val onRow = model.copy(settings = model.settings!!.copy(row = 16))
        val rowSwiped = onRow.settings!!.scrolledShelf(2, onRow)
        assertEquals(16, rowSwiped.row)
        assertEquals(2, rowSwiped.shelfFirst)
        assertEquals(2, shownShelfFirst(rowSwiped, onRow))
    }

    @Test
    fun aSingleTileNeverScrolls() {
        val model = shelf(1).onShelf()
        assertEquals(0, model.press(Meaning.MoveRight).settings!!.row)
        assertEquals(0, model.press(Meaning.MoveRight).settings!!.shelfFirst)
        assertNull(settingsFocusIndex(settingsRows(SettingsCategory.Players, model), 0, Meaning.MoveLeft))
    }

    @Test
    fun aLibraryFolderWaitingForItsGrantLeadsTheLibraryShelfAndAddStaysLast() {
        val waiting = LibraryFolder("content://b", "b", pickAgain = true)
        val model = PickerModel(
            count = 4,
            rowsPerPage = 1,
            homeRoleHeld = true,
            signedIn = listOf(SignedInBackend("romm", "RomM")),
            folders = listOf(LibraryFolder("content://a", "a"), waiting),
        )
        val tiles = settingsRows(SettingsCategory.Library, model).takeWhile { it.settingsTile() }
        assertEquals(
            listOf(
                Row.SettingsTile(SettingsInner.Folder(waiting.uri)),
                Row.RommConnection("romm", "RomM"),
                Row.SettingsTile(SettingsInner.Folder("content://a")),
                Row.SettingsTile(SettingsInner.Add),
            ),
            tiles,
        )
    }
}
