package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FindSavesSettingTest {
    private val model = PickerModel(count = 1, rowsPerPage = 1, playerSaves = listOf(
        PlayerSaveSetting("azahar", "Azahar", chosen = false, installed = true),
    ))

    @Test fun findSavesSitsUnderTheEmulatorTilesAndMirrorsAndroid() {
        val rows = settingsRows(SettingsCategory.Players, model)
        assertTrue(rows.dropLast(1).all { it is Row.SettingsTile })
        assertEquals(Row.FindSaves, rows.last())
        for (granted in listOf(false, true)) {
            val current = model.copy(allFilesGranted = granted)
            val text = rowText(Row.FindSaves, current)
            assertEquals("Find saves", text.label)
            assertEquals(if (granted) "Allowed" else "Ask", text.value)
            assertEquals(RowControl.Value(text.value!!), rowControl(Row.FindSaves, current))
        }
    }

    @Test fun confirmOpensAndroidWithoutPretendingToGrantOrRevokeAccess() {
        for (granted in listOf(false, true)) {
            val current = model.copy(allFilesGranted = granted).onSetting(Row.FindSaves)
            val (after, effect) = reduce(current, Meaning.Activate)
            assertEquals(Effect.OpenAllFilesAccess, effect)
            assertEquals(granted, after.allFilesGranted)
            assertEquals(current.settings, after.settings)
            assertNull(reduce(current, Meaning.Activate, repeatCount = 1).second)
            assertNull(reduce(current, Meaning.MoveLeft).second)
        }
    }
}
