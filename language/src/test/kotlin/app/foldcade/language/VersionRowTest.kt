package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionRowTest {
    private val model = PickerModel(count = 1, rowsPerPage = 1, homeRoleHeld = true, appVersion = AboutCopy.version("0.1.2", 230))

    @Test
    fun aboutShowsTheVersionAboveTheLicenses() {
        assertEquals(listOf(Row.Version, Row.Licenses), settingsRows(SettingsCategory.About, model))
        // The version is in About's head; the row says what A does with it.
        assertEquals(RowText("Copy version info", null), rowText(Row.Version, model))
        assertEquals(RowControl.None, rowControl(Row.Version, model))
        assertEquals(listOf(PageFact("Android", "13"), PageFact("Device", "AYN Thor")), aboutFacts("13", "AYN Thor"))
    }

    @Test
    fun aCopiesTheDebugLineAndTheRowSaysSoUntilTheFocusLeaves() {
        val about = model.onSetting(Row.Version)
        val (copied, effect) = reduce(about, Meaning.Activate)
        assertEquals(Effect.CopyDebugInfo, effect)
        assertEquals(about.settings, copied.settings)
        assertEquals(RowText("Copy version info", "Copied"), rowText(Row.Version, copied))
        assertEquals(RowControl.Value("Copied", healthy = true), rowControl(Row.Version, copied))

        val left = reduce(copied, Meaning.MoveDown).first
        assertEquals(Row.Licenses, settingsFocus(left))
        assertFalse(left.versionCopied)
        val back = reduce(left, Meaning.MoveUp).first
        assertEquals(Row.Version, settingsFocus(back))
        assertNull(rowText(Row.Version, back).value)
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
