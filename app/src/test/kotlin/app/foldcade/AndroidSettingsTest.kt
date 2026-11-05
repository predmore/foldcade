package app.foldcade

import android.provider.Settings
import app.foldcade.language.AndroidSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class AndroidSettingsTest {
    @Test fun androidPanelReturnsOnResumeEvenWithoutAStop() {
        val store = SessionStore(MemoryPrefs())
        store.handoffAndroidSetting(Panel.Top) { true }
        assertEquals(OTHER_APP_ID, store.session.externalOn(Panel.Top)?.id)
        assertEquals(Surface.CommandCenter, store.session.surfaceOn(Panel.Bottom))
        // Home's onResume does this after a translucent panel; onStop is not needed.
        store.update { it.foldcadeResumed(Panel.Top) }
        assertTrue(store.session.bothScreensFree())
        assertEquals(Surface.Picker, store.session.surfaceOn(Panel.Bottom))
    }

    @Test fun failedAndroidHandoffsRestoreHomeForFalseAndForExceptions() {
        for (panel in Panel.entries) {
            val store = SessionStore(MemoryPrefs())
            val other = ExternalApp("other")
            store.place(panel.flip(), other)
            assertFalse(store.handoffAndroidSetting(panel) { false })
            assertNull(store.session.externalOn(panel))
            assertEquals(other, store.session.externalOn(panel.flip()))
            for (failure in listOf(SecurityException("denied"), IllegalStateException("failed"))) {
                try {
                    store.handoffAndroidSetting(panel) { throw failure }
                    fail("launch should throw")
                } catch (actual: RuntimeException) {
                    assertSame(failure, actual)
                }
                assertNull(store.session.externalOn(panel))
                assertEquals(other, store.session.externalOn(panel.flip()))
            }
        }
    }

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
