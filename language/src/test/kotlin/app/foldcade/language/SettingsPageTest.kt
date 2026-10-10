package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPageTest {
    private val home = PickerModel(
        count = 4,
        rowsPerPage = 1,
        showLaunchTarget = false,
        themes = listOf(Copy.builtIn, "Afterglow"),
    )

    private fun openFromMenu(): PickerModel {
        val menu = reduce(home, Meaning.LeftPanel, HostScreen.Top).first
        val rows = panelRows(menu.panel!!, menu)
        assertEquals(Row.Settings, rows.last())
        val onSettings = menu.copy(panel = menu.panel!!.copy(index = rows.lastIndex))
        val (opened, effect) = reduce(onSettings, Meaning.Activate, HostScreen.Top)
        assertNull(effect)
        return opened
    }

    @Test
    fun theMenuRowOpensThePageOnItsScreen() {
        val opened = openFromMenu()
        assertNull(opened.panel)
        assertEquals(HostScreen.Top, opened.settings?.screen)
        assertEquals(0, opened.settings?.category)
        assertFalse(opened.settings!!.onRows)
    }

    @Test
    fun categoriesHideWhenTheyHaveNoRows() {
        assertFalse(SettingsCategory.Players in settingsCategories(home))
        val withSaves = home.copy(playerSaves = listOf(PlayerSaveSetting("melonds", "melonDS", chosen = false)))
        assertTrue(SettingsCategory.Players in settingsCategories(withSaves))
    }

    @Test
    fun rightEntersTheRowsAndLeftReturnsToTheCategories() {
        var model = openFromMenu()
        model = reduce(model, Meaning.MoveDown).first
        assertEquals(1, model.settings?.category)
        model = reduce(model, Meaning.MoveRight).first
        assertTrue(model.settings!!.onRows)
        model = reduce(model, Meaning.MoveDown).first
        assertEquals(1, model.settings?.row)
        model = reduce(model, Meaning.MoveLeft).first
        // The track row steps its value instead of leaving the rows.
        assertTrue(model.settings!!.onRows)
        model = reduce(model, Meaning.MoveUp).first
        model = reduce(model, Meaning.MoveLeft).first
        assertFalse(model.settings!!.onRows)
        assertEquals(1, model.settings?.category)
    }

    @Test
    fun aRowActsInPlaceAndThePageStaysOpen() {
        var model = openFromMenu()
        model = reduce(model, Meaning.Activate).first
        val (stepped, effect) = reduce(model, Meaning.Activate)
        assertNull(effect)
        assertEquals(1, stepped.themeIndex)
        assertTrue(stepped.settings!!.onRows)
        assertNull(stepped.panel)
    }

    @Test
    fun aRowThatLeavesTheMenuClosesThePage() {
        var model = openFromMenu()
        val library = settingsCategories(model).indexOf(SettingsCategory.Library)
        repeat(library) { model = reduce(model, Meaning.MoveDown).first }
        model = reduce(model, Meaning.MoveRight).first
        val rows = settingsRows(SettingsCategory.Library, model)
        repeat(rows.indexOf(Row.Connect)) { model = reduce(model, Meaning.MoveDown).first }
        val (connect, effect) = reduce(model, Meaning.Activate)
        assertEquals(Effect.OpenConnect, effect)
        assertNull(connect.settings)
        assertTrue(connect.connectOpen)
    }

    @Test
    fun backStepsOutThenCloses() {
        var model = openFromMenu()
        model = reduce(model, Meaning.MoveRight).first
        model = reduce(model, Meaning.Back).first
        assertFalse(model.settings!!.onRows)
        model = reduce(model, Meaning.Back).first
        assertNull(model.settings)
        assertNull(model.panel)
    }

    @Test
    fun shouldersCloseThePage() {
        val opened = openFromMenu()
        assertNull(reduce(opened, Meaning.LeftPanel).first.settings)
        assertNull(reduce(opened, Meaning.RightPanel).first.settings)
    }
}
