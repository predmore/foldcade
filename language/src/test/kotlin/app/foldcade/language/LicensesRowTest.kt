package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Test

class LicensesRowTest {
    @Test
    fun leftPanelOpensTheLicenseNoticeAndStaysPut() {
        val model = PickerModel(count = 1, rowsPerPage = 1, showLaunchTarget = false, homeRoleHeld = true)
        val rows = leftRows(homeRoleHeld = true)
        assertEquals(Row.AndroidSettings, rows[rows.indexOf(Row.Licenses) + 1])
        assertEquals(LicenseCopy.row, rowLabel(Row.Licenses, model))
        val opened = reduce(model, Meaning.LeftPanel).first
        val index = panelRows(opened.panel!!, opened).indexOf(Row.Licenses)
        val (stayed, effect) = reduce(opened.copy(panel = opened.panel!!.copy(index = index)), Meaning.Activate)
        assertEquals(Effect.OpenLicenses, effect)
        assertEquals(index, stayed.panel?.index)
    }
}
