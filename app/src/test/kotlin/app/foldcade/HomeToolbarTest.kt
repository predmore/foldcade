package app.foldcade

import app.foldcade.language.Meaning
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeToolbarTest {
    @Test fun allHasNoToolbarSoUpAndMenuButtonsReachTheSharedReducer() {
        val home = HomeSession(null)
        home.showAll()
        for (meaning in listOf(Meaning.MoveUp, Meaning.LeftPanel, Meaning.RightPanel, Meaning.Options)) {
            assertFalse(home.handle(meaning, 0)?.handled == true)
        }
    }
}
