package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Y's menu beside its tile. */
class TileMenuTest {
    private val openWith = PlayerChoiceRow(listOf(PlayerChoice("duckstation", label = "DuckStation"), PlayerChoice("retroarch", label = "RetroArch")))
    private val game = TileActions(move = true, collections = emptyList(), playTime = 60_000L, about = true, select = true, hide = true, game = "g")

    private fun model(tile: TileActions = game, layout: ScreenLayout = ScreenLayout.Default) =
        PickerModel(count = 8, rowsPerPage = 3, focus = GridFocus(cellIndex = 4, lastColumn = 1), tileActions = tile, layout = layout)

    @Test
    fun bClosesTheMenuBackOntoItsTile() {
        val open = reduce(model(), Meaning.Options).first
        val closed = reduce(open, Meaning.Back).first
        assertNull(closed.panel)
        assertEquals(GridFocus(cellIndex = 4, lastColumn = 1), closed.focus)
    }

    @Test
    fun yClosesTheMenuUnlessARowThereTakesY() {
        // A game in All has no Move, so Y has no row: it closes the menu it opened.
        val all = model(game.copy(move = false))
        val open = reduce(all, Meaning.Options).first
        assertEquals(Side.Tile, open.panel?.side)
        assertNull(reduce(open, Meaning.Options).first.panel)
        // From Add to's list as well.
        val addTo = reduce(open.copy(panel = open.panel!!.copy(index = panelRows(open.panel!!, open).indexOf(Row.AddToCollection))), Meaning.Activate).first
        assertEquals(PanelLevel.Collections, addTo.panel?.level)
        assertNull(reduce(addTo, Meaning.Options).first.panel)
        // On Home, Y then Y is still Move.
        val home = reduce(model(), Meaning.Options).first
        val rows = panelRows(home.panel!!, home)
        assertEquals(Row.MoveTile, rows[chordIndex(rows, Meaning.Options)!!])
    }

    @Test
    fun aHeldYNeverRepeatsIntoTheMenuItOpened() {
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_Y, repeatCount = 1))
        val open = reduce(model(), Meaning.Options).first
        assertEquals(open, reduce(open, Meaning.Options, repeatCount = 1).first)
    }

    @Test
    fun lOneAndROneSwapTheMenuForTheirIslands() {
        val open = reduce(model(), Meaning.Options).first
        val left = reduce(open, Meaning.LeftPanel).first
        assertEquals(Side.Left, left.panel?.side)
        assertEquals(PanelLevel.Root, left.panel?.level)
        assertEquals(ScreenLayout.Default.menuScreen, left.panel?.screen)
        assertEquals(open.panel!!.grid, left.focus)
        val right = reduce(open, Meaning.RightPanel).first
        assertEquals(Side.Right, right.panel?.side)
        assertEquals(ScreenLayout.Default.menuScreen, right.panel?.screen)
    }

    @Test
    fun aGameHoldingTheGridsScreenSendsTheMenuToTheOther() {
        val open = reduce(model(), Meaning.Options).first
        assertEquals(HostScreen.Bottom, open.panel?.screen)
        val moved = open.onShownScreens { it == HostScreen.Top }
        assertEquals(HostScreen.Top, moved.panel?.screen)
        assertEquals(Side.Tile, moved.panel?.side)
        // Top only: the grid is on the top screen, and the dark one never has it.
        val top = reduce(model(layout = ScreenLayout.TopOnly), Meaning.Options).first.offTheDarkScreen()
        assertEquals(HostScreen.Top, top.panel?.screen)
        // Art bottom: the grid is on top too.
        assertEquals(HostScreen.Top, reduce(model(layout = ScreenLayout.ShowcaseBottom), Meaning.Options).first.panel?.screen)
    }

    @Test
    fun aRunningGamesRestartAndCloseAreRoundTilesAfterMoveAndAddTo() {
        val paused = game.copy(restart = true, close = true, moveGame = HostScreen.Bottom, openWith = openWith)
        val rows = actionRows(paused)
        assertEquals(
            listOf(Row.MoveTile, Row.AddToCollection, Row.RestartGame, Row.CloseGame,
                Row.OpenWith, Row.PlayTime, Row.About, Row.SelectTile, Row.MoveGame, Row.Hide),
            rows,
        )
        assertEquals(0..3, tileBlock(rows))
        // X still closes a paused game from inside Y, on its tile's rim.
        assertEquals(Row.CloseGame, rows[chordIndex(rows, Meaning.ActivateBottom)!!])
        assertEquals(Copy.restart, tileLabel(Row.RestartGame))
        assertEquals(Copy.closeGame, tileLabel(Row.CloseGame))
        // Not running, nothing moves.
        assertEquals(listOf(Row.MoveTile, Row.AddToCollection, Row.PlayTime, Row.About, Row.SelectTile, Row.Hide), actionRows(game))
    }

    @Test
    fun newCollectionSaysSoUnderItsTile() {
        val all = actionRows(TileActions(move = true, newCollection = true))
        assertEquals(listOf(Row.MoveTile, Row.NewCollection), all)
        assertEquals(0..1, tileBlock(all))
        assertEquals("New collection", tileLabel(Row.NewCollection))
        assertEquals(2, tileLabelLines(Row.NewCollection))
    }

    @Test
    fun openWithOnAPausedGameSaysItWaitsForTheNextLaunch() {
        val paused = game.copy(move = false, restart = true, close = true, openWith = openWith)
        val open = reduce(model(paused), Meaning.Options).first
        val rows = panelRows(open.panel!!, open)
        val onOpenWith = open.copy(panel = open.panel!!.copy(index = rows.indexOf(Row.OpenWith)))
        assertEquals(Copy.nextLaunch, panelNote(onOpenWith))
        assertNull(panelNote(open))
        // A game not running starts with its new choice, so nothing needs saying.
        val idle = reduce(model(game.copy(openWith = openWith)), Meaning.Options).first
        assertNull(panelNote(idle.copy(panel = idle.panel!!.copy(index = panelRows(idle.panel!!, idle).indexOf(Row.OpenWith)))))
    }

    @Test
    fun rThreeOpensTheSummaryOnAGridAndNeverLaunches() {
        assertEquals(Meaning.Info, meaningOf(KeyEvent.KEYCODE_BUTTON_THUMBR))
        assertEquals(Meaning.Info, meaningOf(KeyEvent.KEYCODE_BUTTON_THUMBR, homeKeys = HomeKeys.AllLibrary))
        // Once per press.
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_THUMBR, repeatCount = 1))
        // Nowhere a summary can't show, and L3 means nothing.
        for (keys in listOf(HomeKeys.Moving, HomeKeys.Typing, HomeKeys.Finding, HomeKeys.FindResults, HomeKeys.Selecting, HomeKeys.PickingIcon)) {
            assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_THUMBR, homeKeys = keys))
        }
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_THUMBL))
        // Both stick clicks are swallowed, so Android never turns them into DPAD_CENTER, which launches.
        assertTrue(consumesUnmapped(KeyEvent.KEYCODE_BUTTON_THUMBL))
        assertTrue(consumesUnmapped(KeyEvent.KEYCODE_BUTTON_THUMBR))
        assertEquals(PromptKey.R3, promptKeyOf(KeyEvent.KEYCODE_BUTTON_THUMBR))
        assertEquals(KeyEvent.KEYCODE_BUTTON_THUMBR, keyOfPrompt(PromptKey.R3))
        // Info reaching the picker changes nothing there: the shell opens the hero's card.
        val grid = model()
        assertEquals(grid to null, reduce(grid, Meaning.Info))
    }

    @Test
    fun rThreeInfoJoinsTheHintRowOnlyForASummary() {
        val keys = listOf(HintKey(PromptKey.FaceA, Copy.hintTop), HintKey(PromptKey.FaceX, Copy.hintBottom), HintKey(PromptKey.FaceY, Copy.hintOptions))
        assertEquals(keys, withInfoHint(keys, summary = false))
        assertEquals(keys + HintKey(PromptKey.R3, Copy.hintInfo), withInfoHint(keys, summary = true))
        // With Find there too, the row still has room for it.
        val withFind = withFindHint(keys, atRoot = true)
        assertEquals(PromptKey.R3, withInfoHint(withFind, summary = true).last().key)
    }
}
