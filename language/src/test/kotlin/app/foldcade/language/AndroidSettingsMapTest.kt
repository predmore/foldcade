package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSettingsMapTest {
    @Test
    fun leftPanelKeepsAndroidSettingsAndTheWayOut() {
        val held = sample().copy(homeRoleHeld = true)
        assertEquals(
            listOf(Row.Primary, Row.DefaultHomeApp, Row.AndroidSettings),
            settingsRows(SettingsCategory.Screens, held),
        )
        assertEquals("Android settings", rowLabel(Row.AndroidSettings, sample()))
        assertEquals("Default home app", rowLabel(Row.DefaultHomeApp, sample()))
        val settings = held.onSetting(Row.AndroidSettings)
        val (stayed, effect) = reduce(settings, Meaning.Activate)
        assertEquals(Effect.OpenAndroidSetting(AndroidSetting.Settings), effect)
        assertEquals(settings.settings, stayed.settings)
        val home = held.onSetting(Row.DefaultHomeApp)
        val (homeStayed, homeEffect) = reduce(home, Meaning.Activate)
        assertEquals(Effect.OpenAndroidSetting(AndroidSetting.Home), homeEffect)
        assertEquals(home.settings, homeStayed.settings)
    }

    @Test
    fun rightClusterIsBrightnessNoticesThenOneRowOfAndroidScreens() {
        val notices = listOf(FoldNotice("save", "Both saves were kept", "Archived"))
        val rows = rightRows(notices = notices)
        assertEquals(
            listOf(Row.Brightness, Row.Notice("save")) + quickSettings().map { Row.QuickTile(it) },
            rows,
        )
        assertEquals(
            listOf(QuickSetting.Wifi, QuickSetting.Bluetooth, QuickSetting.Battery, QuickSetting.Settings),
            quickSettings(),
        )
        val opened = reduce(sample().copy(notices = notices), Meaning.RightPanel).first
        assertEquals(0, opened.panel?.index)
        val wifi = move(opened, Meaning.MoveDown, Meaning.MoveDown)
        assertEquals(Row.QuickTile(QuickSetting.Wifi), rows[wifi.panel!!.index])
        val edge = reduce(wifi, Meaning.MoveLeft).first
        assertEquals(wifi.panel?.index, edge.panel?.index)
        val bluetooth = reduce(wifi, Meaning.MoveRight).first
        assertEquals(Row.QuickTile(QuickSetting.Bluetooth), rows[bluetooth.panel!!.index])
        // One row: down stays, up returns to the row above.
        assertEquals(bluetooth.panel?.index, reduce(bluetooth, Meaning.MoveDown).first.panel?.index)
        assertEquals(Row.Notice("save"), rows[reduce(bluetooth, Meaning.MoveUp).first.panel!!.index])
        val last = move(wifi, *Array(quickSettings().size + 2) { Meaning.MoveRight })
        assertEquals(Row.QuickTile(QuickSetting.Settings), rows[last.panel!!.index])
        val (activated, effect) = reduce(wifi, Meaning.Activate)
        assertEquals(Effect.OpenAndroidSetting(AndroidSetting.Wifi), effect)
        assertEquals(wifi.panel?.index, activated.panel?.index)
        // The last tile is Android's Settings app. Foldcade's own settings are on L1.
        val (stayed, android) = reduce(last, Meaning.Activate)
        assertEquals(Effect.OpenAndroidSetting(AndroidSetting.Settings), android)
        assertEquals(last.panel, stayed.panel)
        assertEquals(null, stayed.settings)
        assertEquals("Wi-Fi", rowLabel(Row.QuickTile(QuickSetting.Wifi), sample()))
        assertEquals("Android settings", rowLabel(Row.QuickTile(QuickSetting.Settings), sample()))
        assertTrue(quickSettings().none { it.androidSetting() == AndroidSetting.Home })
    }

    @Test
    fun upFromTheFirstTileReturnsToTheRowAbove() {
        val opened = reduce(sample(), Meaning.RightPanel).first
        val tiles = reduce(opened, Meaning.MoveDown).first
        val launch = reduce(tiles, Meaning.MoveUp).first
        assertEquals(0, launch.panel?.index)
        assertEquals(0, reduce(launch, Meaning.MoveUp).first.panel?.index)
    }

    private fun sample() = PickerModel(count = 2, rowsPerPage = 2)

    private fun move(model: PickerModel, vararg meanings: Meaning): PickerModel {
        var current = model
        meanings.forEach { current = reduce(current, it).first }
        return current
    }
}
