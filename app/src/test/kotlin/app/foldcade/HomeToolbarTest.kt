package app.foldcade

import app.foldcade.language.Meaning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeToolbarTest {
    @Test fun everyToolbarControlLetsMenuButtonsReachTheSharedReducer() {
        val home = HomeSession(null)
        home.showAll()
        for (control in 0..3) {
            home.focusChrome(control)
            for (meaning in listOf(Meaning.LeftPanel, Meaning.RightPanel, Meaning.Options)) {
                assertFalse(home.handle(meaning, 0)?.handled == true)
                assertEquals(control, home.board.allChrome)
            }
        }
    }
}
