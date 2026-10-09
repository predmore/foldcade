package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSettingsMapTest {
    @Test
    fun leftPanelKeepsAndroidSettingsAndTheWayOut() {
        val held = leftRows(homeRoleHeld = true)
        assertEquals(Row.AndroidSettings, held[held.lastIndex - 1])
        assertEquals(Row.DefaultHomeApp, held.last())
        assertEquals("Android settings", rowLabel(Row.AndroidSettings, sample()))
        assertEquals("Default home app", rowLabel(Row.DefaultHomeApp, sample()))
        val opened = reduce(sample().copy(homeRoleHeld = true), Meaning.LeftPanel).first
        val rows = panelRows(opened.panel!!, opened)
        val settings = opened.panel!!.copy(index = rows.indexOf(Row.AndroidSettings))
        val (stayed, effect) = reduce(opened.copy(panel = settings), Meaning.Activate)
        assertEquals(Effect.OpenAndroidSetting(AndroidSetting.Settings), effect)
        assertEquals(settings.index, stayed.panel?.index)
        val home = settings.copy(index = rows.indexOf(Row.DefaultHomeApp))
        val (homeStayed, homeEffect) = reduce(opened.copy(panel = home), Meaning.Activate)
        assertEquals(Effect.OpenAndroidSetting(AndroidSetting.Home), homeEffect)
        assertEquals(Row.DefaultHomeApp, panelRows(home, opened).last())
        assertEquals(home.index, homeStayed.panel?.index)
    }

    @Test
    fun rightClusterTilesMoveInTwoColumnsAndDoNotWrap() {
        val notices = listOf(FoldNotice("save", "Both saves were kept", "Archived"))
        val rows = rightRows(showLaunchTarget = true, notices = notices)
        assertEquals(
            listOf(Row.LaunchTarget, Row.Notice("save")) + quickSettings().map { Row.QuickTile(it) },
            rows,
        )
        val opened = reduce(sample().copy(notices = notices), Meaning.RightPanel).first
        assertEquals(0, opened.panel?.index)
        val wifi = move(opened, Meaning.MoveDown, Meaning.MoveDown)
        assertEquals(Row.QuickTile(QuickSetting.Wifi), rows[wifi.panel!!.index])
        val bluetooth = reduce(wifi, Meaning.MoveRight).first
        assertEquals(Row.QuickTile(QuickSetting.Bluetooth), rows[bluetooth.panel!!.index])
        val stuck = reduce(bluetooth, Meaning.MoveRight).first
        assertEquals(bluetooth.panel?.index, stuck.panel?.index)
        val sound = reduce(bluetooth, Meaning.MoveDown).first
        assertEquals(Row.QuickTile(QuickSetting.Sound), rows[sound.panel!!.index])
        val display = reduce(sound, Meaning.MoveLeft).first
        assertEquals(Row.QuickTile(QuickSetting.Display), rows[display.panel!!.index])
        val backToWifi = reduce(display, Meaning.MoveUp).first
        assertEquals(wifi.panel?.index, backToWifi.panel?.index)
        val edge = reduce(backToWifi, Meaning.MoveLeft).first
        assertEquals(backToWifi.panel?.index, edge.panel?.index)
        val appInfo = move(sound, Meaning.MoveDown)
        assertEquals(Row.QuickTile(QuickSetting.AppInfo), rows[appInfo.panel!!.index])
        val battery = reduce(appInfo, Meaning.MoveLeft).first
        assertEquals(Row.QuickTile(QuickSetting.Battery), rows[battery.panel!!.index])
        val stayed = reduce(battery, Meaning.MoveDown).first
        assertEquals(battery.panel?.index, stayed.panel?.index)
        val (activated, effect) = reduce(edge, Meaning.Activate)
        assertEquals(Effect.OpenAndroidSetting(AndroidSetting.Wifi), effect)
        assertEquals(edge.panel?.index, activated.panel?.index)
        assertEquals("Wi-Fi", rowLabel(Row.QuickTile(QuickSetting.Wifi), sample()))
        assertEquals("App info", rowLabel(Row.QuickTile(QuickSetting.AppInfo), sample()))
        assertTrue(quickSettings().none { it.androidSetting() == AndroidSetting.Settings })
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

    private fun sample() = PickerModel(count = 2, rowsPerPage = 2, showLaunchTarget = true)

    private fun move(model: PickerModel, vararg meanings: Meaning): PickerModel {
        var current = model
        meanings.forEach { current = reduce(current, it).first }
        return current
    }
}
