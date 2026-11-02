package app.foldcade

import app.foldcade.language.HOME_ALL
import app.foldcade.language.HomeBoard
import app.foldcade.language.HomeFolder
import app.foldcade.language.Meaning
import app.foldcade.language.decodeHome
import app.foldcade.language.encodeHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEncodingTest {
    private val ids = List(1_015) { "lib:local:$it" }
    private val folder = HomeFolder("home.user.1", "Games", userMade = true, slots = ids)
    private fun home() = HomeSession(encodeHome(HomeBoard(
        slots = listOf(HOME_ALL, folder.id), folders = mapOf(folder.id to folder),
    )))

    @Test
    fun repeatedReadsAndNavigationReuseTheSavedEncoding() {
        val home = home()
        home.showAll()
        val saved = home.encoded()
        repeat(100) {
            home.handle(Meaning.MoveRight, it, repeatCount = 1)
            assertSame(saved, home.encoded())
        }
    }

    @Test
    fun durableEditsAndUndoEncodeTheCurrentSnapshot() {
        val home = home()
        val before = home.board
        val saved = home.encoded()
        home.hide(ids.first())
        val hidden = home.encoded()
        assertNotEquals(saved, hidden)
        assertEquals(encodeHome(home.board), hidden)
        assertTrue(ids.first() in decodeHome(hidden).hidden)
        assertSame(hidden, home.encoded())
        home.restore(before)
        assertEquals(saved, home.encoded())
        assertSame(home.encoded(), home.encoded())
    }

    @Test
    fun openingAndClosingViewsKeepThePersistedBoardIntact() {
        val home = home()
        val saved = home.encoded()
        home.showAll()
        assertEquals(saved, home.encoded())
        assertSame(home.encoded(), home.encoded())
        home.showRoot()
        assertEquals(saved, home.encoded())
    }
}
