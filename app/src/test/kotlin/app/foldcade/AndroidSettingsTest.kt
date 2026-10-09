package app.foldcade

import android.provider.Settings
import app.foldcade.language.AndroidSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSettingsTest {
    @Test
    fun wifiAndSoundPreferSettingsPanels() {
        val wifi = androidSettingTargets(AndroidSetting.Wifi)
        assertEquals(Settings.Panel.ACTION_WIFI, wifi.first().action)
        assertTrue(wifi.any { it.action == Settings.ACTION_WIFI_SETTINGS })
        assertEquals(Settings.ACTION_SETTINGS, wifi.last().action)

        val sound = androidSettingTargets(AndroidSetting.Sound)
        assertEquals(Settings.Panel.ACTION_VOLUME, sound.first().action)
        assertTrue(sound.any { it.action == Settings.ACTION_SOUND_SETTINGS })
        assertEquals(Settings.ACTION_SETTINGS, sound.last().action)
    }

    @Test
    fun screensWithoutAPanelStartOnTheirSettingsPage() {
        assertEquals(
            Settings.ACTION_BLUETOOTH_SETTINGS,
            androidSettingTargets(AndroidSetting.Bluetooth).first().action,
        )
        assertEquals(
            Settings.ACTION_DISPLAY_SETTINGS,
            androidSettingTargets(AndroidSetting.Display).first().action,
        )
        assertEquals(
            "android.intent.action.POWER_USAGE_SUMMARY",
            androidSettingTargets(AndroidSetting.Battery).first().action,
        )
        assertTrue(
            androidSettingTargets(AndroidSetting.Battery).any { it.action == Settings.ACTION_BATTERY_SAVER_SETTINGS },
        )
        listOf(AndroidSetting.Bluetooth, AndroidSetting.Display, AndroidSetting.Battery).forEach { setting ->
            assertFalse(
                androidSettingTargets(setting).any {
                    it.action.startsWith("android.settings.panel.")
                },
            )
            assertEquals(Settings.ACTION_SETTINGS, androidSettingTargets(setting).last().action)
        }
    }

    @Test
    fun appInfoOpensThisAppThenTheAppsList() {
        val targets = androidSettingTargets(AndroidSetting.AppInfo)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, targets.first().action)
        assertTrue(targets.first().packageData)
        assertEquals(Settings.ACTION_APPLICATION_SETTINGS, targets[1].action)
        assertEquals(Settings.ACTION_SETTINGS, targets.last().action)
    }

    @Test
    fun homeSettingsIsHowAUserLeavesFoldcade() {
        val targets = androidSettingTargets(AndroidSetting.Home)
        assertEquals(Settings.ACTION_HOME_SETTINGS, targets.first().action)
        assertTrue(targets.any { it.action == Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS })
        assertEquals(Settings.ACTION_SETTINGS, targets.last().action)
    }

    @Test
    fun androidSettingsOpensTheSettingsRoot() {
        val targets = androidSettingTargets(AndroidSetting.Settings)
        assertEquals(listOf(Settings.ACTION_SETTINGS), targets.map { it.action })
        assertFalse(targets.single().packageData)
    }
}
