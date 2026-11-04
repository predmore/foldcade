package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class RommPhoneTest {
    private val colors = KeyDropPage.Colors("#000000", "#111111", "#ffffff", "#888888", "#ffaa00")

    @Test fun phoneAsksOnlyForAnAddressAndContinuesToApprovalWithoutAnotherScan() {
        val form = RommPhonePage.form("/token", colors)
        assertTrue(form.contains("type=\"url\""))
        assertTrue(form.contains("name=\"key\""))
        assertFalse(form.contains("type=\"password\""))
        assertFalse(form.contains("<script"))
        val done = RommPhonePage.done("https://romm.example/pair/device?code=ABCD1234&x=\"", false, colors)
        assertTrue(done.contains("code=ABCD1234&amp;x=&quot;"))
        assertTrue(done.contains("rel=\"noreferrer\""))
        assertFalse(RommPhonePage.done(null, true, colors).contains("href="))
    }

    @Test fun discoveredChoiceIsOnePressAndTypingRemainsAvailable() {
        val model = PickerModel(count = 0, rowsPerPage = 2, connectOpen = true, connectFoundOrigin = "http://192.168.1.2", connectIndex = 0)
        assertEquals(Effect.UseFoundRomm, reduce(model, Meaning.Activate).second)
        assertNull(reduce(model, Meaning.Activate, repeatCount = 1).second)
        val typed = reduce(model, Meaning.MoveDown).first
        assertEquals(Effect.EditField, reduce(typed, Meaning.Activate).second)
    }
}
