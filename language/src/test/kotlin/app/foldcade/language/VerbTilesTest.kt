package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

/** Y's menu leads with the tile's verbs as round tiles, and every other action is a row under them. */
class VerbTilesTest {
    private val game = TileActions(move = true, collections = emptyList(), art = true, saves = true, select = true,
        restart = true, hide = true, removeDownload = true, sort = FolderSortChoice(HOME_ROOT, FolderSort.Custom), game = "g")

    @Test fun aGameLeadsWithMoveAddToArtAndSaves() {
        val rows = actionRows(game)
        assertEquals(listOf(Row.MoveTile, Row.AddToCollection, Row.ChooseArt, Row.Saves), rows.slice(tileBlock(rows)))
        assertEquals(listOf(Row.SelectTile, Row.RestartGame, Row.Hide, Row.RemoveDownload, Row.SortFolder), rows.drop(4))
    }

    @Test fun eachKindOfTileLeadsWithItsOwnVerbs() {
        fun tiles(tile: TileActions) = actionRows(tile).let { it.slice(tileBlock(it)) }
        assertEquals(listOf(Row.MoveTile, Row.RenameFolder, Row.FolderIcon),
            tiles(TileActions(move = true, rename = true, icon = true, remove = RemoveVerb.Remove)))
        assertEquals(listOf(Row.MoveTile, Row.RenameFolder), tiles(TileActions(move = true, rename = true)))
        assertEquals(listOf(Row.MoveTile, Row.NewCollection), tiles(TileActions(move = true, newCollection = true, tidy = true)))
        assertEquals(listOf(Row.AddToCollection),
            tiles(TileActions(bulk = listOf("a", "b"), collections = emptyList(), newCollection = true, hide = true)))
        assertEquals(listOf(Row.NewCollection), tiles(TileActions(bulk = listOf("a"), newCollection = true)))
    }

    @Test fun undoStaysFirstAboveTheTiles() {
        val rows = actionRows(game.copy(undo = true))
        assertEquals(Row.Undo, rows.first())
        assertEquals(1..4, tileBlock(rows))
        val opened = reduce(PickerModel(count = 4, rowsPerPage = 2, tileActions = game.copy(undo = true)), Meaning.Options).first
        assertEquals(0, opened.panel?.index)
        assertEquals(1, reduce(opened, Meaning.MoveDown).first.panel?.index)
    }

    @Test fun aVerbPastTheFourthIsARow() {
        val rows = actionRows(game.copy(rename = true))
        assertEquals(0..3, tileBlock(rows))
        assertEquals(Row.RenameFolder, rows[4])
    }

    @Test fun aVerbFurtherDownAPageStaysARow() {
        assertTrue(tileBlock(collectionRows(TileActions(collections = listOf(CollectionChoice(HOME_ROOT, "Home", false))))).isEmpty())
        assertTrue(tileBlock(listOf(Row.ShowHidden, Row.ChooseArt)).isEmpty())
    }

    @Test fun yOpensOnTheFirstTileAndYThenYStillMoves() {
        val model = PickerModel(count = 4, rowsPerPage = 2, tileActions = game)
        val opened = reduce(model, Meaning.Options).first
        assertEquals(0, opened.panel?.index)
        assertEquals(Row.MoveTile, actionRows(game)[chordIndex(actionRows(game), Meaning.Options)!!])
        val (moving, effect) = reduce(move(opened, Meaning.MoveRight, Meaning.MoveDown), Meaning.Options)
        assertNull(moving.panel)
        assertEquals(Effect.MoveTile, effect)
    }

    @Test fun leftAndRightWalkTheTilesAndUpReturnsToTheOneFocusLeft() {
        val opened = reduce(PickerModel(count = 4, rowsPerPage = 2, tileActions = game), Meaning.Options).first
        val last = move(opened, Meaning.MoveRight, Meaning.MoveRight, Meaning.MoveRight, Meaning.MoveRight)
        assertEquals(3, last.panel?.index)
        val select = move(last, Meaning.MoveDown)
        assertEquals(4, select.panel?.index)
        assertEquals(3, move(select, Meaning.MoveUp).panel?.index)
        val art = move(opened, Meaning.MoveRight, Meaning.MoveRight, Meaning.MoveDown, Meaning.MoveDown, Meaning.MoveUp, Meaning.MoveUp)
        assertEquals(2, art.panel?.index)
        assertEquals(0, move(art, Meaning.MoveLeft, Meaning.MoveLeft, Meaning.MoveLeft).panel?.index)
        // Nothing sits above the tiles without Undo.
        assertEquals(0, move(opened, Meaning.MoveUp).panel?.index)
    }

    @Test fun upFromTheRowsFallsToTheNearestTileWhenTheOneLeftIsGone() {
        val rows = listOf(Row.MoveTile, Row.NewCollection, Row.Tidy, Row.SortFolder)
        assertEquals(1, panelFocusIndex(rows, 2, Meaning.MoveUp, column = 3))
        assertEquals(0, panelFocusIndex(rows, 2, Meaning.MoveUp))
        assertEquals(2, panelFocusIndex(rows, 1, Meaning.MoveDown))
    }

    @Test fun editHomeLeadsWithTheTileThenTheBoard() {
        val model = PickerModel(count = 4, rowsPerPage = 2,
            tileActions = TileActions(move = true, rename = true, remove = RemoveVerb.Remove, tidyBoard = true))
        val rows = editHomeRows(model)
        assertEquals(listOf(Row.MoveTile, Row.RenameFolder), rows.slice(tileBlock(rows)))
        assertEquals(listOf(Row.RemoveTile, Row.Tidy, Row.RecentOnHome, Row.AddNewGames, Row.Hidden), rows.drop(2))
    }

    @Test fun everyVerbHasOneWordUnderItsTile() {
        verbRows.forEach { verb ->
            val words = tileLabel(verb)
            assertNotNull("$verb", words)
            assertFalse("$verb: $words", words!!.contains(' ') && words != Copy.addTo)
        }
    }

    @Test fun placesAndQuickTilesKeepTheirBlocks() {
        assertEquals(1..4, tileBlock(leftRows()))
        assertEquals(0..3, tileBlock(rightRows(emptyList())))
    }

    private fun move(model: PickerModel, vararg meanings: Meaning): PickerModel {
        var current = model
        meanings.forEach { current = reduce(current, it).first }
        return current
    }

    @Test fun backFromEditHomeReturnsUpToThePlaceL1WasLeftFrom() {
        val apps = leftRows().indexOf(Row.Apps)
        val l1 = PickerModel(count = 4, rowsPerPage = 2, tileActions = game,
            panel = SidePanel(Side.Left, HostScreen.Top, PanelLevel.Root, apps, GridFocus()))
        val onEdit = move(l1, Meaning.MoveDown)
        assertEquals(Row.EditHome, leftRows()[onEdit.panel!!.index])
        val inside = reduce(onEdit, Meaning.Activate).first
        assertEquals(PanelLevel.EditHome, inside.panel?.level)
        val back = reduce(move(inside, Meaning.MoveRight, Meaning.MoveDown), Meaning.Back).first
        assertEquals(PanelLevel.Root, back.panel?.level)
        assertEquals(apps, move(back, Meaning.MoveUp).panel?.index)
    }
}
