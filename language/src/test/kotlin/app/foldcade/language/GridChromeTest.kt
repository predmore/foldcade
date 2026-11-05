package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 5 November UX review's grid chrome: Find's empty state, footers, menu verbs and the icon picker (review-B, C, D). */
class GridChromeTest {
    @Test
    fun noMatchesAlwaysNamesTheNextStep() {
        // Another filter would show some: say how many All has, since L2 and R2 reach it.
        assertEquals("3 in All", noMatchStep("zel", inAll = 3))
        // Nothing anywhere: change the query.
        assertEquals(FindCopy.fewerLetters, noMatchStep("zzzzqx", inAll = 0))
        assertEquals(FindCopy.otherLetter, noMatchStep("q", inAll = 0))
        // An empty query under an empty filter: try another filter.
        assertEquals(FindCopy.tryFilter, noMatchStep("", inAll = 0))
    }

    @Test
    fun keyboardFootersNameOnlyWhatNoKeyWears() {
        val faces = FaceMap.standard()
        // Erase, Space, Shift and Done wear X, Y, L2 and Start on their keys (review-C S1).
        assertEquals(listOf(PromptKey.L1, PromptKey.R1, PromptKey.FaceB), gridHints(null, HomeKeys.Typing, faces).map { it.key })
        assertEquals(listOf(PromptKey.L2, PromptKey.R2, PromptKey.FaceB), findKeyboardHints(faces, typed = true).map { it.key })
    }

    @Test
    fun aMenuRowsHintSaysWhatAdoes() {
        // L1's Edit home: switches turn on or off, and Folders and Hidden open their pages, with a ›.
        val edit = PickerModel(count = 4, rowsPerPage = 2, recentOnHome = true, addNewToHome = false)
        assertEquals(Copy.hintTurnOff, menuVerb(Row.RecentOnHome, edit))
        assertEquals(Copy.hintTurnOn, menuVerb(Row.AddNewGames, edit))
        assertEquals(RowControl.Opens, rowControl(Row.Folders, edit))
        assertEquals(RowControl.Opens, rowControl(Row.Hidden, edit))
        assertEquals(Copy.hintOpen, menuVerb(Row.Folders, edit))
        // L1's places open; a tile menu's verbs are chosen; Add to's checks add or take out.
        assertEquals(Copy.hintOpen, menuVerb(Row.AllLibrary, edit))
        assertEquals(Copy.hintChoose, menuVerb(Row.MoveTile, edit))
        val tile = TileActions(move = true, collections = listOf(CollectionChoice(HOME_ROOT, Copy.home, member = true), CollectionChoice("c", "Backlog", member = false)), game = "g")
        val withTile = edit.copy(tileActions = tile)
        assertEquals(Copy.hintTakeOut, menuVerb(Row.Collection(HOME_ROOT, Copy.home), withTile))
        assertEquals(Copy.hintAdd, menuVerb(Row.Collection("c", "Backlog"), withTile))
        // The hint row names that verb on A, never the old "Confirm".
        val menu = reduce(withTile, Meaning.Options).first
        val hints = panelHints(menu)!!
        assertEquals(HintKey(PromptKey.FaceA, Copy.hintChoose), hints.first())
        assertTrue(hints.none { it.label == Copy.confirm })
    }

    @Test
    fun theIconPickersShortLastRowSitsCentredAndUpAndDownKeepTheirColumn() {
        val columns = IconPick.COLUMNS
        val last = FOLDER_ICONS.lastIndex
        val lastStart = last / columns * columns
        val count = last - lastStart + 1
        // Centred exactly, half a column over when the counts differ by an odd number (review r1).
        assertEquals((columns - count) / 2f, IconPick.LAST_ROW_SHIFT, 0f)
        assertEquals((columns - 1) / 2f, (IconPick.glyphColumn(lastStart) + IconPick.glyphColumn(last)) / 2f, 0f)
        assertEquals(0f, IconPick.glyphColumn(0), 0f)
        val pick = IconPick.open("c", CollectionLook(FOLDER_ICONS[lastStart], CollectionTint.Gold))
        // Up from the centred row lands on the glyph drawn at or just left of it, and down comes back.
        val up = pick.step(Meaning.MoveUp)
        assertEquals(lastStart - columns + kotlin.math.floor(IconPick.LAST_ROW_SHIFT).toInt(), up.glyph)
        assertEquals(lastStart, up.step(Meaning.MoveDown).glyph)
        // Down from a column past the short row's end goes to its nearest glyph, and the last row goes nowhere.
        val left = IconPick.open("c", CollectionLook(FOLDER_ICONS[lastStart - columns], CollectionTint.Gold))
        assertEquals(lastStart, left.step(Meaning.MoveDown).glyph)
        val right = IconPick.open("c", CollectionLook(FOLDER_ICONS[lastStart - 1], CollectionTint.Gold))
        assertEquals(last, right.step(Meaning.MoveDown).glyph)
        assertEquals(pick, pick.step(Meaning.MoveDown))
    }

    @Test
    fun newCollectionKeepsOneLineBesideFewerTiles() {
        assertEquals(1, tileLabelLines(Row.NewCollection, lineSize = 2))
        assertEquals(2, tileLabelLines(Row.NewCollection, lineSize = quickTileColumns))
        assertEquals(1, tileLabelLines(Row.MoveTile, lineSize = quickTileColumns))
    }

    @Test
    fun hiddenWithNothingInItNamesItselfOnTheHero() {
        val copy = heroCopy(HeroSubject.Folder(HeroFolder(HIDDEN_HERO_KEY, Copy.hiddenApps, count = null, mark = MARK_HIDDEN, note = Copy.noHiddenApps)))
        assertEquals(Copy.hiddenApps, copy.title)
        assertEquals(Copy.noHiddenApps, copy.detail)
        assertTrue(copy.showsArt)
    }
}
