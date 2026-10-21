package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionRowTest {
    private val model = PickerModel(count = 1, rowsPerPage = 1, homeRoleHeld = true, appVersion = AboutCopy.version("0.1.2", 230))

    @Test
    fun aboutShowsTheVersionAboveTheLicenses() {
        assertEquals(listOf(Row.Version, Row.Licenses), settingsRows(SettingsCategory.About, model))
        assertEquals(RowText("Version", "0.1.2 (230)"), rowText(Row.Version, model))
        assertEquals(RowControl.Value("0.1.2 (230)"), rowControl(Row.Version, model))
    }

    @Test
    fun aCopiesTheDebugLineAndTheRowSaysSoUntilTheFocusLeaves() {
        val about = model.onSetting(Row.Version)
        val (copied, effect) = reduce(about, Meaning.Activate)
        assertEquals(Effect.CopyDebugInfo, effect)
        assertEquals(about.settings, copied.settings)
        assertEquals(RowText("Version", "Copied"), rowText(Row.Version, copied))
        assertEquals(RowControl.Value("Copied"), rowControl(Row.Version, copied))

        val left = reduce(copied, Meaning.MoveDown).first
        assertEquals(Row.Licenses, settingsFocus(left))
        assertFalse(left.versionCopied)
        val back = reduce(left, Meaning.MoveUp).first
        assertEquals(Row.Version, settingsFocus(back))
        assertEquals("0.1.2 (230)", rowText(Row.Version, back).value)
    }

    @Test
    fun copiedStaysWhileTheFocusStaysOnVersion() {
        val copied = reduce(model.onSetting(Row.Version), Meaning.Activate).first
        // Right does nothing on a plain value, so the focus stays and so does the word.
        val still = reduce(copied, Meaning.MoveRight).first
        assertTrue(still.versionCopied)
        val closed = reduce(copied, Meaning.Menu).first
        assertFalse(closed.versionCopied)
    }

    @Test
    fun debugLineNamesFoldcadeAndroidAndTheDevice() {
        assertEquals("0.1.2 (230)", AboutCopy.version("0.1.2", 230))
        assertEquals("AYN Thor", AboutCopy.device("AYN", "Thor"))
        assertEquals("AYN Thor", AboutCopy.device("AYN", "AYN Thor"))
        assertEquals(
            "Foldcade 0.1.2 (230) · Android 13 · AYN Thor · TQ3A.230901",
            AboutCopy.debugLine("0.1.2 (230)", "13", "AYN Thor", "TQ3A.230901"),
        )
        // A part Android left blank leaves no empty separator.
        assertEquals("Foldcade 0.1.2 (230) · Android 13 · AYN Thor", AboutCopy.debugLine("0.1.2 (230)", "13", "AYN Thor", ""))
    }

    @Test
    fun quickTilesAreNamedUnderneath() {
        assertEquals(
            listOf("Wi-Fi", "Bluetooth", "Battery", "Settings"),
            quickSettings().map { tileLabel(Row.QuickTile(it)) },
        )
    }
}
