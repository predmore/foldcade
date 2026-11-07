package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A RomM game Foldcade waited on and couldn't find names the cause and its fix (review-D 217). */
class CatalogGameDialogTest {
    private val screen = HostScreen.Bottom

    @Test fun offlineItNamesWifiAndBackCloses() {
        val dialog = catalogGameUnavailableDialog(offline = true, screen = screen)
        assertEquals(Copy.noInternet, dialog.title)
        assertEquals(listOf(DialogButton.Wifi), dialog.buttons)
        assertEquals(Copy.wifi, dialogButtonLabel(DialogButton.Wifi))
        assertEquals(Copy.close, dialogHints(dialog, FaceMap.standard()).single().label)
        val (closed, effect) = reduce(PickerModel(count = 1, rowsPerPage = 1, dialog = dialog), Meaning.Back, screen)
        assertEquals(null, closed.dialog)
        assertEquals(DialogButton.Dismiss, (effect as Effect.DialogChoice).button)
        val (_, wifi) = reduce(PickerModel(count = 1, rowsPerPage = 1, dialog = dialog), Meaning.Activate, screen)
        assertEquals(DialogButton.Wifi, (wifi as Effect.DialogChoice).button)
    }

    @Test fun onlineRommAnsweredWithoutItAndOkCloses() {
        val dialog = catalogGameUnavailableDialog(offline = false, screen = screen)
        assertEquals(Copy.gameUnavailable, dialog.title)
        assertEquals(listOf(DialogButton.Ok), dialog.buttons)
        assertEquals(0, dialogBackButton(dialog))
        assertTrue(dialogHints(dialog, FaceMap.standard()).isEmpty())
    }
}
