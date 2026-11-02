package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Y › Play time on a game, its own Play activity page, and Settings › System › Backup › Play history. */
class PlayGamePageTest {
    private val game = "lib:local-folder:new.gba"

    @Test
    fun aGamesMenuOffersPlayTimeWithItsTimeAfterTheVerbs() {
        val tile = TileActions(move = true, collections = emptyList(), art = true, saves = true, playTime = 2 * 3_600_000L + 5 * 60_000L, game = game, remove = RemoveVerb.Remove)
        val rows = actionRows(tile)
        assertEquals(listOf(Row.MoveTile, Row.AddToCollection, Row.ChooseArt, Row.Saves, Row.PlayTime, Row.RemoveTile), rows)
        // The verbs stay the round tiles; Play time is a row under them.
        assertEquals(0 until 4, tileBlock(rows))
        val model = PickerModel(count = 1, rowsPerPage = 1, tileActions = tile)
        assertEquals(RowText(PlayCopy.playTime, "2h 5m"), rowText(Row.PlayTime, model))
        assertEquals(RowControl.Value("2h 5m"), rowControl(Row.PlayTime, model))
        // A tile that is not a game, or nothing played anywhere yet, has no row.
        assertFalse(Row.PlayTime in actionRows(tile.copy(playTime = null)))
        assertEquals(RowText(PlayCopy.playTime, "0m"), rowText(Row.PlayTime, model.copy(tileActions = tile.copy(playTime = 0L))))
        // A renamed ROM, with no play of its own, says the old file's time was found.
        assertEquals(RowText(PlayCopy.playTime, "Found 12h"), rowText(Row.PlayTime, model.copy(tileActions = tile.copy(playTime = 0L, playFound = 12 * 3_600_000L))))
        assertEquals(RowText(PlayCopy.playTime, "2h 5m"), rowText(Row.PlayTime, model.copy(tileActions = tile.copy(playFound = 60_000L))))
    }

    @Test
    fun playTimeOpensTheGamesPageAndAsksTheShellForItsSources() {
        val tile = TileActions(move = true, playTime = 0L, game = game)
        val menu = reduce(PickerModel(count = 1, rowsPerPage = 1, tileActions = tile), Meaning.Options).first
        val at = menu.copy(panel = menu.panel!!.copy(index = panelRows(menu.panel!!, menu).indexOf(Row.PlayTime)))
        val (opened, effect) = reduce(at, Meaning.Activate)
        assertNull(opened.panel)
        assertEquals(game, opened.playActivity?.game)
        // A full page, on the larger top screen, wherever Y's menu was.
        assertEquals(ScreenLayout.Default.pageScreen, opened.playActivity?.screen)
        assertEquals(Effect.OpenPlayGame(game), effect)
    }

    @Test
    fun onAGamesPageUpAndDownMoveOverSourcesAndAMovesThenPutsBack() {
        val page = gamePage(listOf("old", "other"))
        assertEquals(1, reduce(page, Meaning.MoveDown).first.playActivity?.source)
        assertEquals(1, reduce(reduce(page, Meaning.MoveDown).first, Meaning.MoveDown).first.playActivity?.source)
        assertEquals(0, reduce(page, Meaning.MoveUp).first.playActivity?.source)
        // Left and right still step the game's weeks.
        assertEquals(1, reduce(page, Meaning.MoveLeft).first.playActivity?.weeksBack)

        val (moved, move) = reduce(page, Meaning.Activate)
        assertEquals(Effect.MovePlayTime("old", game), move)
        assertEquals(setOf("old"), moved.playActivity?.moved)
        assertEquals(listOf(PlayCopy.undo, PlayCopy.week, Copy.back), playActivityHints(moved).map { it.label })

        val (back, undo) = reduce(moved, Meaning.Activate)
        assertEquals(Effect.UndoPlayMove("old", game), undo)
        assertEquals(emptySet<String>(), back.playActivity?.moved)
        assertEquals(listOf(PlayCopy.holdToMove, PlayCopy.week, Copy.back), playActivityHints(back).map { it.label })
        assertEquals(listOf(PromptKey.FaceA, PromptKey.DpadSides, PromptKey.FaceB), playActivityHints(back).map { it.key })

        // X and Select do not reach a day's sessions here: the schedule is only read.
        assertNull(reduce(page, Meaning.ActivateBottom).second)
        listOf(Meaning.Back, Meaning.LeftPanel, Meaning.RightPanel, Meaning.View).forEach { meaning ->
            val closed = reduce(moved, meaning).first
            assertNull(closed.playActivity)
            assertEquals(page.playActivity!!.grid, closed.focus)
        }
    }

    @Test
    fun aGamesPageWithNothingToTakeHasOnlyWeekAndBack() {
        val page = gamePage(emptyList())
        assertEquals(page.playActivity, reduce(page, Meaning.Activate).first.playActivity)
        assertNull(reduce(page, Meaning.Activate).second)
        assertEquals(listOf(PlayCopy.week, Copy.back), playActivityHints(page).map { it.label })
    }

    @Test
    fun sourcesPutTheSameGameFirstAndGoneGamesAheadOfOnesStillThere() {
        val ordered = playSourceOrder(
            "Pokémon - Emerald Version (USA, Europe)",
            listOf(
                PlaySource("long", "Tetris", gone = false, activeMillis = 90_000_000L),
                PlaySource("gone", "Advance Wars", gone = true, activeMillis = 1_000L),
                PlaySource("same-here", "Pokemon Emerald", gone = false, activeMillis = 5_000L),
                PlaySource("renamed", "Pokémon - Emerald Version (USA) (Rev 1).gba", gone = true, activeMillis = 5_000L),
                PlaySource("copy", "Pokémon Emerald Version", gone = false, activeMillis = 2_000L),
            ),
        ).map { it.id }
        // The renamed file is gone and named alike: the likeliest. A copy still here comes next.
        assertEquals(listOf("renamed", "copy", "same-here", "gone", "long"), ordered)
    }

    @Test
    fun aGameWithNoPlayFindsOnlyAGoneGameOfItsOwnName() {
        val sources = listOf(
            PlaySource("here", "Pokémon Emerald", gone = false, activeMillis = 9_000L),
            PlaySource("alike", "Pokémon Emerald Plus", gone = true, activeMillis = 8_000L),
            PlaySource("old", "Pokémon - Emerald (USA).gba", gone = true, activeMillis = 5_000L),
            PlaySource("older", "Pokemon Emerald", gone = true, activeMillis = 7_000L),
        )
        assertEquals("old", foundSource("Pokémon - Emerald (Europe).gba", sources)?.id)
        assertNull(foundSource("Tetris", sources))
        assertNull(foundSource("(USA)", sources))
    }

    @Test
    fun aRomsWordsLeaveOutItsTagsAndExtension() {
        assertEquals(listOf("pokémon", "emerald", "version"), playWords("Pokémon - Emerald Version (USA, Europe).gba"))
        assertEquals(listOf("legend", "of", "zelda", "a", "link", "to", "past"), playWords("The Legend of Zelda - A Link to the Past [!] (Rev 1).sfc"))
        assertEquals(emptyList<String>(), playWords("(USA)"))
        // A dot in a title is not an extension.
        assertEquals(listOf("dragon", "quest", "i", "ii"), playWords("Dragon Quest I.II"))
        assertEquals(playWords("Dragon Quest I.II"), playWords("Dragon Quest I.II (USA).gba"))
        assertEquals(listOf("r", "e", "p", "o"), playWords("R.E.P.O."))
    }

    @Test
    fun backupOffersPlayHistoryAndItsDialogOpensOnExport() {
        val model = PickerModel(count = 1, rowsPerPage = 1)
        assertTrue(Row.Backup in settingsRows(SettingsCategory.Screens, model))
        val (opened, effect) = reduce(model.onSetting(Row.Backup), Meaning.Activate)
        assertEquals(Effect.OpenBackup, effect)
        assertNull(opened.dialog) // The host supplies Undo from its current before-copy.
        val backup = backupDialog(HostScreen.Top)
        val (history, historyEffect) = focusAndActivateDialog(opened.copy(dialog = backup), backup.buttons.indexOf(DialogButton.PlayHistory))
        assertNull(historyEffect)
        val asking = history.dialog!!
        assertEquals(DialogKind.PlayHistory, asking.kind)
        assertEquals(listOf(DialogButton.ExportPlay, DialogButton.ImportPlay, DialogButton.NotNow), asking.buttons)
        assertEquals(DialogButton.ExportPlay, asking.buttons[asking.index])
        assertEquals(DialogButton.NotNow, asking.buttons[asking.safeIndex])
        assertEquals(listOf("Export", "Import"), asking.buttons.take(2).map { dialogButtonLabel(it) })
        assertEquals("Added 1 session", PlayCopy.imported(1))
        assertEquals("Added 34 sessions", PlayCopy.imported(34))
    }

    private fun gamePage(sources: List<String>): PickerModel {
        val grid = GridFocus(cellIndex = 3)
        return PickerModel(count = 8, rowsPerPage = 2, focus = grid).copy(
            playActivity = PlayActivityPage(HostScreen.Top, grid = grid, game = game, sources = sources),
        )
    }
}
