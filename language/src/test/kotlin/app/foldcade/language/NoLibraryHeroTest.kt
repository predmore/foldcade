package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A library with no folder or server shows its focused button large on the top screen (review-A 324). */
class NoLibraryHeroTest {
    private val empty = PickerModel(count = 2, rowsPerPage = 1, libraryGrid = true, gridKind = GridKind.NoLibrary)

    private fun hero(model: PickerModel) = (noLibraryHero(model) as HeroSubject.Folder).folder

    @Test fun theHeroFollowsTheFocusedButton() {
        assertEquals(Copy.addFolder, hero(empty).name)
        assertEquals(MARK_FOLDER, hero(empty).mark)
        val romm = empty.copy(focus = GridFocus(cellIndex = 1))
        assertEquals(Copy.addRommServer, hero(romm).name)
        assertEquals(MARK_CLOUD, hero(romm).mark)
        assertEquals("", heroCopy(noLibraryHero(romm)!!).detail)
    }

    @Test fun noHeroOnAnotherGridOrOnThePills() {
        assertNull(noLibraryHero(empty.copy(gridKind = GridKind.Games)))
        assertNull(noLibraryHero(empty.copy(libraryGrid = false)))
        assertNull(noLibraryHero(empty.copy(focus = GridFocus(chrome = Chrome.LeftIsland))))
    }
}
