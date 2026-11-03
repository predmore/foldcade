package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class CloseGameTest {
    @Test fun pausedGamesNameResumeAndCloseWithCalibratedFaces() {
        for (faces in listOf(FaceMap.standard(), FaceMap.standard().copy(confirmKey = android.view.KeyEvent.KEYCODE_BUTTON_X))) {
            val hints = gridHints(null, HomeKeys.Idle, faces, screens = true, play = PlayKey.Resume)
            assertEquals(listOf(Copy.hintResume, Copy.hintClose), hints.map { it.label })
            assertEquals(actionFaces(faces).bottomGlyph, hints[1].key)
            val found = findResultHints(faces, screens = true, options = true, play = PlayKey.Resume)
            assertEquals(hints, found.take(2))
        }
        assertEquals(listOf(Copy.hintResume), gridHints(null, HomeKeys.Idle, FaceMap.standard(), play = PlayKey.Resume, close = false).map { it.label })
    }

    @Test fun closeNamesCompanionsAndOpensOnTheSafeButton() {
        val dialog = closeGameDialog(HostScreen.Top, listOf("Notes", "Browser", "Music", "Files"))
        assertEquals("Close the game?", dialog.title)
        assertEquals("Anything not saved is lost. Notes, Browser and 2 more close too.", dialog.body)
        assertEquals(listOf(DialogButton.CloseIt, DialogButton.NotNow), dialog.buttons)
        assertEquals(DialogButton.NotNow, dialog.buttons[dialog.index])
        assertEquals(dialog.index, dialog.safeIndex)
        val model = PickerModel(count = 1, rowsPerPage = 1, dialog = dialog)
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.CloseGame), reduce(model, Meaning.Back).second)
        assertEquals("", Copy.companionCloseLine(emptyList()))
        assertEquals("Notes closes too.", Copy.companionCloseLine(listOf("Notes")))
        assertEquals("Notes and Music close too.", Copy.companionCloseLine(listOf("Notes", "Music")))
    }

    @Test fun closeIgnoresXAndRepeatingConfirmation() {
        val model = PickerModel(count = 1, rowsPerPage = 1, dialog = closeGameDialog(HostScreen.Top).copy(index = 0))
        assertEquals(model, reduce(model, Meaning.ActivateBottom, repeatCount = 0).first)
        assertNull(reduce(model, Meaning.ActivateBottom, repeatCount = 8).second)
        assertNull(reduce(model, Meaning.Activate, repeatCount = 1).second)
        assertEquals(Effect.DialogChoice(DialogButton.CloseIt, DialogKind.CloseGame), reduce(model, Meaning.Activate).second)
    }

    @Test fun restartAndCloseStayTogetherAndXBelongsToClose() {
        val tile = TileActions(move = true, collections = emptyList(), art = true, saves = true, select = true,
            remove = RemoveVerb.Remove, restart = true, close = true, moveGame = HostScreen.Bottom, hide = true, game = "g")
        val rows = actionRows(tile)
        assertEquals(listOf(Row.RestartGame, Row.CloseGame), rows.slice(rows.indexOf(Row.RestartGame)..rows.indexOf(Row.CloseGame)))
        // With Move, Add to, Art and Saves on the tiles, they lead the rows under them.
        assertEquals(tileBlock(rows).last + 1, rows.indexOf(Row.RestartGame))
        assertTrue(rows.indexOf(Row.MoveGame) > rows.indexOf(Row.CloseGame))
        assertEquals(Row.CloseGame, rows[chordIndex(rows, Meaning.ActivateBottom)!!])
        assertNull(chords(rows)[rows.indexOf(Row.MoveGame)])
        assertNull(chords(rows)[rows.indexOf(Row.RemoveTile)])
        val model = PickerModel(count = 1, rowsPerPage = 1, tileActions = tile)
        val menu = reduce(model, Meaning.Options).first
        assertEquals(Effect.CloseGame("g"), reduce(menu, Meaning.ActivateBottom).second)
        assertEquals(Copy.playOnBottom, rowText(Row.MoveGame, model).label)
        assertEquals(Copy.playOnTop, rowText(Row.MoveGame, model.copy(tileActions = tile.copy(moveGame = HostScreen.Top))).label)
    }
}
