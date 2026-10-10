package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Test

class LicensesRowTest {
    @Test
    fun leftPanelOpensTheLicenseNoticeAndStaysPut() {
        val model = PickerModel(count = 1, rowsPerPage = 1, showLaunchTarget = false, homeRoleHeld = true)
        assertEquals(listOf(Row.Licenses), settingsRows(SettingsCategory.About, model))
        assertEquals(LicenseCopy.row, rowLabel(Row.Licenses, model))
        val about = model.onSetting(Row.Licenses)
        val (stayed, effect) = reduce(about, Meaning.Activate)
        assertEquals(Effect.OpenLicenses, effect)
        assertEquals(about.settings, stayed.settings)
    }
}
