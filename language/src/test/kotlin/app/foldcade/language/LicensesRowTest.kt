package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LicensesRowTest {
    @Test
    fun heldBackCannotStepOutOfSettingsAfterLicensesFinishes() {
        for (faces in listOf(FaceMap.standard(), FaceMap.standard().copy(
            confirmKey = KeyEvent.KEYCODE_BUTTON_B, backKey = KeyEvent.KEYCODE_BUTTON_A,
        ))) {
            for (back in listOf(faces.backKey, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE)) {
                assertEquals(Meaning.Back, meaningOf(back, 0, faces))
                val settings = PickerModel(1, 1, faceMap = faces).onSetting(Row.Licenses)
                var returned = reduce(settings, Meaning.Activate).first
                // Licenses finishes on the first down. Its held repeats arrive at Home.
                for (repeat in 1..20) {
                    val meaning = meaningOf(back, repeat, faces)
                    assertNull(meaning)
                    if (meaning != null) returned = reduce(returned, meaning, repeatCount = repeat).first
                }
                assertEquals(settings.settings, returned.settings)
                // Releasing and pressing Back again still steps out of the rows.
                val fresh = reduce(returned, meaningOf(back, 0, faces)!!).first
                assertEquals(settings.settings!!.copy(onRows = false), fresh.settings)
            }
        }
    }

    @Test
    fun leftPanelOpensTheLicenseNoticeAndStaysPut() {
        val model = PickerModel(count = 1, rowsPerPage = 1, homeRoleHeld = true)
        assertEquals(listOf(Row.Version, Row.Licenses), settingsRows(SettingsCategory.About, model))
        assertEquals(LicenseCopy.row, rowLabel(Row.Licenses, model))
        val about = model.onSetting(Row.Licenses)
        val (stayed, effect) = reduce(about, Meaning.Activate)
        assertEquals(Effect.OpenLicenses, effect)
        assertEquals(about.settings, stayed.settings)
    }
}
