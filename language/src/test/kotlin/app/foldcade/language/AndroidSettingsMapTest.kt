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
        assertEquals("Home app", rowLabel(Row.DefaultHomeApp, sample()))
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
    fun rightClusterIsOneRowOfAndroidScreensThenBrightnessThenNotices() {
        val notices = listOf(FoldNotice("save", "Both saves were kept", "Archived"))
        val rows = rightRows(notices = notices)
        assertEquals(
            quickSettings().map { Row.QuickTile(it) } + listOf(Row.Brightness, Row.Notice("save")),
            rows,
        )
        assertEquals(
            listOf(QuickSetting.Wifi, QuickSetting.Bluetooth, QuickSetting.Battery, QuickSetting.Settings),
            quickSettings(),
        )
        // R1 opens on brightness, under the tiles. Up reaches the first tile.
        val opened = reduce(sample().copy(notices = notices), Meaning.RightPanel).first
        assertEquals(Row.Brightness, rows[opened.panel!!.index])
        val wifi = move(opened, Meaning.MoveUp)
        assertEquals(Row.QuickTile(QuickSetting.Wifi), rows[wifi.panel!!.index])
        val edge = reduce(wifi, Meaning.MoveLeft).first
        assertEquals(wifi.panel?.index, edge.panel?.index)
        val bluetooth = reduce(wifi, Meaning.MoveRight).first
        assertEquals(Row.QuickTile(QuickSetting.Bluetooth), rows[bluetooth.panel!!.index])
        // One row at the top: up stays, down returns to brightness.
        assertEquals(bluetooth.panel?.index, reduce(bluetooth, Meaning.MoveUp).first.panel?.index)
        assertEquals(Row.Brightness, rows[reduce(bluetooth, Meaning.MoveDown).first.panel!!.index])
        assertEquals(Row.Notice("save"), rows[move(opened, Meaning.MoveDown).panel!!.index])
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
    fun downFromTheTilesReturnsToBrightness() {
        val opened = reduce(sample(), Meaning.RightPanel).first
        val tiles = reduce(opened, Meaning.MoveUp).first
        assertEquals(0, tiles.panel?.index)
        val back = reduce(tiles, Meaning.MoveDown).first
        assertEquals(opened.panel?.index, back.panel?.index)
        // Nothing under brightness without notices.
        assertEquals(opened.panel?.index, reduce(back, Meaning.MoveDown).first.panel?.index)
    }

    @Test
    fun tilesAtTheEndOfAListStillTakeTheLastRow() {
        val rows = listOf(Row.Brightness) + quickSettings().map { Row.QuickTile(it) }
        assertEquals(1, panelFocusIndex(rows, 0, Meaning.MoveDown))
        assertEquals(0, panelFocusIndex(rows, 3, Meaning.MoveUp))
        assertEquals(3, panelFocusIndex(rows, 3, Meaning.MoveDown))
    }

    private fun sample() = PickerModel(count = 2, rowsPerPage = 2)

    private fun move(model: PickerModel, vararg meanings: Meaning): PickerModel {
        var current = model
        meanings.forEach { current = reduce(current, it).first }
        return current
    }
}
