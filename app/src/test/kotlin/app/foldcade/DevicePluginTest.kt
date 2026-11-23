package app.foldcade

import app.foldcade.host.backup.BackupSettings
import app.foldcade.language.FanTier
import app.foldcade.language.DEVICE_SCOPE
import app.foldcade.language.DEVICE_THOR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePluginTest {
    private fun device(packageName: String, device: String = DEVICE_THOR) = testApkPlugin(packageName = packageName).copy(
        platform = "", scopes = setOf(DEVICE_SCOPE), authority = "$packageName.device", device = device)

    @Test fun aDevicePluginIsSupportedOnlyForADeviceThisHostKnows() {
        assertTrue(device("app.foldcade.thor").supported)
        assertFalse(device("example.other", device = "other-handheld").supported)
        // A play plugin still needs a platform Foldcade plays.
        assertTrue(testApkPlugin().supported)
    }

    @Test fun theDeviceGoesToThePluginGrantedMostRecentlyAndConsentIsForgottenWithIt() {
        val grants = ApkPluginGrants(MemoryPrefs())
        val thor = device("app.foldcade.thor")
        val other = device("app.foldcade.thor.debug")
        val moonlight = testApkPlugin(packageName = "app.foldcade.moonlight")
        grants.setGranted(moonlight, "gamestream", true)
        grants.setGranted(thor, DEVICE_SCOPE, true)
        assertTrue(grants.granted(thor, DEVICE_SCOPE))
        // One device plugin at a time: granting the other turns the first one's switch off.
        grants.setGranted(other, DEVICE_SCOPE, true)
        assertFalse(grants.granted(thor, DEVICE_SCOPE))
        assertTrue(grants.granted(other, DEVICE_SCOPE))
        // Play plugins' consent is untouched.
        assertTrue(grants.granted(moonlight, "gamestream"))
        // A new signer is a new plugin, with no consent.
        assertFalse(grants.granted(other.copy(signers = setOf("b".repeat(64))), DEVICE_SCOPE))
        // A device plugin can't be granted a play scope it never asked for, and uninstalling forgets it.
        grants.setGranted(other, "gamestream", true)
        assertFalse(grants.granted(other, "gamestream"))
        grants.forget(other.packageName)
        assertFalse(grants.granted(other, DEVICE_SCOPE))
        assertTrue(grants.routes(moonlight))
    }

    @Test fun aGamesFanTierIsSavedUnderItsTileAndTravelsInTheSetupBackup() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        store.setGameFan("lib:romm:kart", FanTier.Performance)
        assertEquals(FanTier.Performance, SessionStore(prefs).gameFan("lib:romm:kart"))
        assertEquals(null, store.gameFan("lib:romm:zelda"))
        val saved = prefs.all.filterKeys { it.startsWith("game_fan:") }
        assertEquals(1, saved.size)
        assertEquals(saved, BackupSettings.portable(saved))
        // Default is no entry at all, and a value this build can't choose reads as Default.
        store.setGameFan("lib:romm:kart", null)
        assertTrue(prefs.all.keys.none { it.startsWith("game_fan:") })
        prefs.edit().putString(saved.keys.single(), "default").apply()
        assertEquals(null, store.gameFan("lib:romm:kart"))
    }
}
