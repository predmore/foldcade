package app.foldcade.language

import android.view.KeyEvent
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractTest {
    @Test
    fun activateAndBackShareOneMap() {
        listOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        ).forEach { assertEquals(Meaning.Activate, meaningOf(it)) }
        assertEquals(Meaning.Back, meaningOf(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(Meaning.Back, meaningOf(KeyEvent.KEYCODE_BACK))
        assertEquals(Meaning.LeftPanel, meaningOf(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(Meaning.RightPanel, meaningOf(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(Meaning.MoveUp, meaningOf(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(Meaning.MoveDown, meaningOf(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(Meaning.MoveLeft, meaningOf(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(Meaning.MoveRight, meaningOf(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun shouldersIgnoreRepeatAndDoNotPage() {
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_L1, repeatCount = 1))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_R1, repeatCount = 4))
        assertEquals(Meaning.LeftPanel, meaningOf(KeyEvent.KEYCODE_BUTTON_L1, repeatCount = 0))
        val model = PickerModel(count = 24, rowsPerPage = 1)
        val paged = reduce(model, Meaning.PageTowardEnd).first
        assertEquals(GridStrip.index(Metrics.columns, 0), paged.focus.cellIndex)
        val shoulder = reduce(model, Meaning.LeftPanel).first
        assertEquals(0, shoulder.focus.cellIndex)
        assertEquals(Side.Left, shoulder.panel?.side)
    }

    @Test
    fun unusedKeysAreNotGivenAMeaning() {
        listOf(
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_HOME,
        ).forEach { assertNull(meaningOf(it)) }
    }

    @Test
    fun builtInThemeIsTrueBlack() {
        val theme = builtInTheme()
        assertEquals(Color(0xFF000000), theme.background)
        // Cards (islands, dialogs) are raised off the true-black backdrop.
        assertEquals(Color(0xFF1C1C20), theme.surface)
        assertEquals(Color(0xFFF2F2F2), theme.onBackground)
        assertEquals(Color(0xFF8A8A8A), theme.muted)
        assertEquals(Color(0xFFFFFFFF), theme.focus)
        assertEquals(0.12f, theme.iconRadius)
        assertEquals(0.92f, theme.artScale)
        assertEquals(4, Metrics.columns)
    }

    @Test
    fun faceButtonsDoNotFlipLabels() {
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(Meaning.Back, meaningOf(KeyEvent.KEYCODE_BUTTON_B))
    }

    @Test
    fun dialogTapFocusesAndActivatesThatButton() {
        val prompt = homePrompt()
        val model = PickerModel(count = 2, rowsPerPage = 2, dialog = prompt)
        assertEquals(DialogButton.UseAsHome, prompt.buttons[prompt.index])
        val (next, effect) = focusAndActivateDialog(model, 0)
        assertEquals(Effect.DialogChoice(DialogButton.UseAsHome, DialogKind.Home), effect)
        assertNull(next.dialog)
    }

    @Test
    fun hintsUseAAndBOnlyWhenThatActionDoesSomething() {
        assertNull(hintFor(HintPlace.RootGrid))
        assertEquals(HintActions(confirm = false, back = true), hintFor(HintPlace.InsidePlatform))
        assertEquals(HintActions(confirm = true, back = true), hintFor(HintPlace.Connect))
        assertNull(hintLine(activateDoesSomething = false, backDoesSomething = false))
        assertEquals(HintActions(confirm = true, back = true), hintFor(HintPlace.Dialog))
    }

    @Test
    fun gridHintsNameOnlyKeysThatAreNotObvious() {
        val map = FaceMap.standard()
        assertEquals(emptyList<HintKey>(), gridHints(hintFor(HintPlace.RootGrid), HomeKeys.Idle, map))
        assertEquals(
            listOf(HintKey(PromptKey.FaceB, Copy.back)),
            gridHints(hintFor(HintPlace.InsidePlatform), HomeKeys.Idle, map),
        )
        assertEquals(
            listOf(HintKey(PromptKey.FaceA, Copy.hintDrop), HintKey(PromptKey.FaceB, Copy.hintCancel)),
            gridHints(null, HomeKeys.Moving, map),
        )
        assertEquals(
            listOf(PromptKey.FaceX, PromptKey.FaceY, PromptKey.L1, PromptKey.R1, PromptKey.L2, PromptKey.Start, PromptKey.FaceB),
            gridHints(null, HomeKeys.Typing, map).map { it.key },
        )
        assertEquals(
            listOf(PromptKey.FaceB),
            gridHints(hintFor(HintPlace.InsidePlatform), HomeKeys.AllLibrary, map).map { it.key },
        )
        assertEquals(
            listOf(PromptKey.FaceA, PromptKey.FaceX, PromptKey.FaceY, PromptKey.FaceB),
            gridHints(hintFor(HintPlace.InsidePlatform), HomeKeys.AllLibrary, map, screens = true, options = true).map { it.key },
        )
        // Y is named only when the focused tile has a menu.
        assertEquals(
            listOf(HintKey(PromptKey.FaceY, Copy.hintOptions)),
            gridHints(hintFor(HintPlace.RootGrid), HomeKeys.Idle, map, options = true),
        )
    }

    @Test
    fun focusWalksIntoEmptySlotsPastTheEndAndUpReachesThePillOnThatSide() {
        // Five columns of three: 0 1 2 down the first, 12 alone at the top of the last.
        val model = PickerModel(count = 13, rowsPerPage = 1)
        // Right from the last tile walks the empty columns past it, then stops without wrapping.
        var walked = model.copy(focus = GridFocus(cellIndex = 12, lastColumn = 4))
        val columns = mutableListOf<Int>()
        repeat(GridStrip.trailing + 1) {
            walked = reduce(walked, Meaning.MoveRight).first
            columns += GridStrip.column(walked.focus.cellIndex)
        }
        assertEquals(listOf(5, 6, 7, 7), columns)
        assertEquals(GridStrip.index(7, 0), walked.focus.cellIndex)
        // An empty slot opens nothing.
        assertNull(reduce(walked, Meaning.Activate).second)
        val bottom = reduce(model.copy(focus = GridFocus(cellIndex = 2)), Meaning.MoveDown).first
        assertEquals(2, bottom.focus.cellIndex)
        // Right from the bottom row into the short last column lands on the empty slot under its one tile.
        val short = reduce(model.copy(focus = GridFocus(cellIndex = 11, lastColumn = 3)), Meaning.MoveRight).first
        assertEquals(14, short.focus.cellIndex)
        assertEquals(13, reduce(short, Meaning.MoveUp).first.focus.cellIndex)
        val up = reduce(model.copy(focus = GridFocus(cellIndex = 3, lastColumn = 1)), Meaning.MoveUp).first
        assertEquals(Chrome.LeftIsland, up.focus.chrome)
        val cluster = reduce(model.copy(focus = GridFocus(cellIndex = 9, lastColumn = 3)), Meaning.MoveUp).first
        assertEquals(Chrome.StatusCluster, cluster.focus.chrome)
    }

    @Test
    fun theStripScrollsOnlyAsFarAsTheCursorGoes() {
        val count = 7 * Metrics.rows
        assertEquals(0, GridStrip.follow(first = 0, column = Metrics.columns - 1, count = count, shown = Metrics.columns))
        assertEquals(1, GridStrip.follow(first = 0, column = Metrics.columns, count = count, shown = Metrics.columns))
        // Back left, the screen holds until the cursor reaches its first column.
        assertEquals(3, GridStrip.follow(first = 3, column = 4, count = count, shown = Metrics.columns))
        assertEquals(2, GridStrip.follow(first = 3, column = 2, count = count, shown = Metrics.columns))
        // Never past the screen with the last tiles at its left edge.
        assertEquals(6, GridStrip.follow(first = 9, column = 9, count = count, shown = Metrics.columns))
        // A strip that fits scrolls only once the cursor walks off its screen into the empty columns.
        assertEquals(0, GridStrip.follow(first = 0, column = 3, count = 6, shown = Metrics.columns))
        assertEquals(1, GridStrip.follow(first = 0, column = 4, count = 6, shown = Metrics.columns))
        // Up reads the pill from where the column sits on screen, not along the strip.
        val scrolled = PickerModel(count = count, rowsPerPage = 1, firstColumn = 3, focus = GridFocus(GridStrip.index(4, 0), 4))
        assertEquals(Chrome.LeftIsland, reduce(scrolled, Meaning.MoveUp).first.focus.chrome)
        val far = scrolled.copy(focus = GridFocus(GridStrip.index(6, 0), 6))
        assertEquals(Chrome.StatusCluster, reduce(far, Meaning.MoveUp).first.focus.chrome)
    }

    @Test
    fun aWidePanelShowsMoreColumnsOfTheSameRows() {
        val count = 12 * Metrics.rows
        val wide = PickerModel(count = count, rowsPerPage = 1, columnsShown = 8)
        // Eight columns on screen: the strip holds still until the cursor walks past the eighth.
        assertEquals(0, GridStrip.follow(first = 0, column = 7, count = count, shown = 8))
        assertEquals(1, GridStrip.follow(first = 0, column = 8, count = count, shown = 8))
        // Never past the screen whose last column is the last the cursor can reach.
        assertEquals(GridStrip.reachColumns(count) - 8, GridStrip.follow(first = 20, column = 14, count = count, shown = 8))
        val moved = wide.copy(focus = GridFocus(GridStrip.index(8, 0), 8))
        assertEquals(1, shownFirstColumn(moved))
        // A tile keeps its row and column: the same index sits in the same place as on a narrow panel.
        assertEquals(GridStrip.index(8, 0), moved.focus.cellIndex)
        // A swipe moves a screen of the panel's own columns.
        assertEquals(GridStrip.index(8, 0), reduce(wide, Meaning.PageTowardEnd).first.focus.cellIndex)
        // Up reads the pill from the half of this panel the column is in.
        val leftHalf = wide.copy(focus = GridFocus(GridStrip.index(3, 0), 3))
        assertEquals(Chrome.LeftIsland, reduce(leftHalf, Meaning.MoveUp).first.focus.chrome)
        val rightHalf = wide.copy(focus = GridFocus(GridStrip.index(4, 0), 4))
        assertEquals(Chrome.StatusCluster, reduce(rightHalf, Meaning.MoveUp).first.focus.chrome)
        // Moving to a narrower panel scrolls only as far as keeps the cursor on screen.
        val far = wide.copy(focus = GridFocus(GridStrip.index(7, 0), 7))
        assertEquals(0, shownFirstColumn(far))
        assertEquals(7 - Metrics.columns + 1, shownFirstColumn(far.copy(columnsShown = Metrics.columns)))
        assertEquals(7 - Metrics.columns + 1, shownFirstColumn(far, Metrics.columns))
    }

    @Test
    fun aSwipeRestsTheStripWhereItWasLeftAndBringsTheCursorAlong() {
        val count = 7 * Metrics.rows
        val row = Metrics.rows - 1
        val model = PickerModel(count = count, rowsPerPage = Metrics.rows, focus = GridFocus(GridStrip.index(1, row), 1))
        // Its tile still on screen, the cursor stays on it.
        val near = scrolledTo(model, 1)
        assertEquals(1, shownFirstColumn(near))
        assertEquals(GridStrip.index(1, row), near.focus.cellIndex)
        // Dragged off the left edge, it comes in along its row to the first column on screen.
        val far = scrolledTo(model, 4)
        assertEquals(4, shownFirstColumn(far))
        assertEquals(GridStrip.index(4, row), far.focus.cellIndex)
        assertEquals(4, far.focus.lastColumn)
        // Dragged back, off the right edge, to the last column on screen.
        val back = scrolledTo(far, 0)
        assertEquals(0, shownFirstColumn(back))
        assertEquals(GridStrip.index(Metrics.columns - 1, row), back.focus.cellIndex)
        // Never past either end of what the cursor can reach.
        val last = GridStrip.reachColumns(count) - Metrics.columns
        assertEquals(last, shownFirstColumn(scrolledTo(model, 99)))
        assertEquals(0, shownFirstColumn(scrolledTo(far, -3)))
        // A tile in the hand keeps the strip where its moves put it.
        val holding = model.copy(hold = Hold(index = 1, origin = 1, orderBefore = (0 until count).toList()))
        assertEquals(holding, scrolledTo(holding, 4))
        // A wide panel follows the same way across its own columns, not the bottom screen's four.
        val wide = model.copy(columnsShown = 8)
        val wideNear = scrolledTo(wide, 1)
        assertEquals(1, shownFirstColumn(wideNear))
        assertEquals(GridStrip.index(1, row), wideNear.focus.cellIndex)
        // Off the left edge of the eight, it comes in to the first column on that screen.
        val wideFar = scrolledTo(wide, 2)
        assertEquals(2, shownFirstColumn(wideFar))
        assertEquals(GridStrip.index(2, row), wideFar.focus.cellIndex)
        assertEquals(2, wideFar.focus.lastColumn)
        // Off the right edge, to the last of the eight.
        val wideBack = scrolledTo(wide.copy(focus = GridFocus(GridStrip.index(8, row), 8)), 0)
        assertEquals(0, shownFirstColumn(wideBack))
        assertEquals(GridStrip.index(7, row), wideBack.focus.cellIndex)
        assertEquals(7, wideBack.focus.lastColumn)
        // The wide screen reaches its end sooner, and still stops at the start.
        assertEquals(GridStrip.reachColumns(count) - 8, shownFirstColumn(scrolledTo(wide, 99)))
        assertEquals(0, shownFirstColumn(scrolledTo(wideFar, -3)))
        val holdingWide = wide.copy(hold = Hold(index = 1, origin = 1, orderBefore = (0 until count).toList()))
        assertEquals(holdingWide, scrolledTo(holdingWide, 2))
    }

    @Test
    fun aLaunchesOnTopAndXOnTheBottom() {
        val model = PickerModel(count = 6, rowsPerPage = 1, focus = GridFocus(cellIndex = 2, lastColumn = 2))
        assertEquals(Effect.Launch(2, onBottom = false), reduce(model, Meaning.Activate).second)
        assertEquals(Effect.Launch(2, onBottom = true), reduce(model, Meaning.ActivateBottom).second)
        assertEquals(Meaning.ActivateBottom, meaningOf(android.view.KeyEvent.KEYCODE_BUTTON_X))
        val hints = gridHints(null, HomeKeys.Idle, FaceMap.standard(), screens = true)
        assertEquals(listOf(Copy.hintTop, Copy.hintBottom), hints.map { it.label })
        assertTrue(gridHints(null, HomeKeys.Idle, FaceMap.standard()).isEmpty())
    }

    @Test
    fun everyGameNamesAAndAGameStillOpenResumes() {
        val map = FaceMap.standard()
        // A game that takes both screens has no choice of screens, but A still plays it.
        assertEquals(listOf(Copy.hintPlay), gridHints(null, HomeKeys.Idle, map, play = PlayKey.Play).map { it.label })
        // One still open in its player comes back where it was, whichever screen it takes.
        assertEquals(listOf(Copy.hintResume), gridHints(null, HomeKeys.Idle, map, screens = false, play = PlayKey.Resume).map { it.label })
        assertEquals(
            listOf(PromptKey.FaceA, PromptKey.FaceY),
            gridHints(hintFor(HintPlace.RootGrid), HomeKeys.Idle, map, options = true, play = PlayKey.Resume).map { it.key },
        )
        assertEquals(Copy.paused, activityLine(TileActivity.Paused))
    }

    @Test
    fun aGameStillOpenOffersRestartWhichAsksFirst() {
        val rows = actionRows(TileActions(move = true, select = true, restart = true, hide = true, removeDownload = true, game = "g"))
        assertEquals(listOf(Row.MoveTile, Row.SelectTile, Row.RestartGame, Row.Hide, Row.RemoveDownload), rows)
        assertNull(chords(rows)[rows.indexOf(Row.RestartGame)])
        val model = PickerModel(count = 6, rowsPerPage = 1, tileActions = TileActions(restart = true, game = "g"))
        val menu = reduce(model, Meaning.Options).first
        val index = panelRows(menu.panel!!, menu).indexOf(Row.RestartGame)
        val (after, effect) = reduce(menu.copy(panel = menu.panel!!.copy(index = index)), Meaning.Activate)
        assertEquals(Effect.RestartGame("g"), effect)
        assertNull(after.panel)
        val dialog = restartGameDialog(HostScreen.Top)
        assertEquals(DialogButton.NotNow, dialog.buttons[dialog.index])
        assertEquals(dialog.index, dialog.safeIndex)
    }

    @Test
    fun yOpensTheTileMenuAndAddToTogglesCollectionsAndStepsBackToIt() {
        val focus = GridFocus(cellIndex = 2, lastColumn = 2)
        val tile = TileActions(
            move = true,
            collections = listOf(CollectionChoice(HOME_ROOT, Copy.home, false), CollectionChoice("home.user.1", "Mine", true)),
            remove = RemoveVerb.Remove,
            hide = true,
        )
        val model = PickerModel(count = 6, rowsPerPage = 1, focus = focus, tileActions = tile)
        val open = reduce(model, Meaning.Options).first
        val panel = open.panel!!
        assertEquals(Side.Left, panel.side)
        assertEquals(PanelLevel.Actions, panel.level)
        assertEquals(
            listOf(Row.MoveTile, Row.AddToCollection, Row.RemoveTile, Row.Hide),
            panelRows(panel, open),
        )
        assertEquals(Copy.remove, rowText(Row.RemoveTile, open).label)
        assertEquals(Copy.hideApp, rowText(Row.Hide, open).label)
        assertEquals(RowControl.Opens, rowControl(Row.AddToCollection, open))
        val addTo = reduce(open.copy(panel = panel.copy(index = 1)), Meaning.Activate).first
        assertEquals(PanelLevel.Collections, addTo.panel?.level)
        val rows = panelRows(addTo.panel!!, addTo)
        assertEquals(
            listOf(Row.Collection(HOME_ROOT, Copy.home), Row.Collection("home.user.1", "Mine"), Row.NewCollection),
            rows,
        )
        assertEquals(RowControl.Check(false), rowControl(rows[0], addTo))
        assertEquals(RowControl.Check(true), rowControl(rows[1], addTo))
        // A toggles the check and the menu stays open on that row.
        val (toggled, effect) = reduce(addTo, Meaning.Activate)
        assertEquals(Effect.ToggleCollection(HOME_ROOT), effect)
        assertEquals(PanelLevel.Collections, toggled.panel?.level)
        assertEquals(0, toggled.panel?.index)
        assertEquals(RowControl.Check(true), rowControl(rows[0], toggled))
        // New collection closes the menu for the name field.
        val made = reduce(addTo.copy(panel = addTo.panel!!.copy(index = 2)), Meaning.Activate)
        assertEquals(Effect.NewCollection(), made.second)
        // Each effect names the game the rows act on, so a menu that kept its game still acts on it.
        val held = addTo.copy(tileActions = addTo.tileActions!!.copy(game = "lib:romm:7", removeDownload = true))
        assertEquals(Effect.ToggleCollection(HOME_ROOT, "lib:romm:7"), reduce(held, Meaning.Activate).second)
        assertEquals(Effect.NewCollection("lib:romm:7"), reduce(held.copy(panel = held.panel!!.copy(index = 2)), Meaning.Activate).second)
        val actions = held.copy(panel = held.panel!!.copy(level = PanelLevel.Actions, index = 0))
        val heldRows = panelRows(actions.panel!!, actions)
        fun pick(row: Row) = reduce(actions.copy(panel = actions.panel!!.copy(index = heldRows.indexOf(row))), Meaning.Activate).second
        assertEquals(Effect.Hide("lib:romm:7"), pick(Row.Hide))
        assertEquals(Effect.RemoveTile, pick(Row.RemoveTile))
        assertEquals(Effect.RemoveDownload("lib:romm:7"), pick(Row.RemoveDownload))
        val withArt = actions.copy(tileActions = actions.tileActions!!.copy(art = true))
        val artRows = panelRows(withArt.panel!!, withArt)
        assertEquals(Effect.ChooseArt("lib:romm:7"), reduce(withArt.copy(panel = withArt.panel!!.copy(index = artRows.indexOf(Row.ChooseArt))), Meaning.Activate).second)
        assertNull(made.first.panel)
        assertEquals(focus, made.first.focus)
        // B from Add to lands back on its row, and B again closes the menu.
        val back = reduce(addTo, Meaning.Back).first
        assertEquals(PanelLevel.Actions, back.panel?.level)
        assertEquals(1, back.panel?.index)
        assertNull(reduce(back, Meaning.Back).first.panel)
        // A tile with nothing to offer keeps Y shut, and so does the status island.
        assertNull(reduce(model.copy(tileActions = null), Meaning.Options).first.panel)
        assertNull(reduce(model.copy(focus = focus.copy(chrome = Chrome.StatusCluster)), Meaning.Options).first.panel)
    }

    @Test
    fun eachTileOffersItsRowsInOneOrder() {
        val choices = emptyList<CollectionChoice>()
        // A pin on the root.
        assertEquals(
            listOf(Row.MoveTile, Row.AddToCollection, Row.RemoveTile, Row.Hide, Row.RemoveDownload),
            actionRows(TileActions(move = true, collections = choices, remove = RemoveVerb.Unpin, hide = true, removeDownload = true)),
        )
        // In Recently played, or a folder: nothing to remove, so Hide alone.
        assertEquals(
            listOf(Row.AddToCollection, Row.Hide),
            actionRows(TileActions(collections = choices, hide = true)),
        )
        // An installed app in an Android folder can move to the other one, after Add to.
        assertEquals(
            listOf(Row.MoveTile, Row.AddToCollection, Row.MoveApp, Row.Hide),
            actionRows(TileActions(move = true, collections = choices, hide = true, moveTo = HOME_ANDROID_GAMES)),
        )
        // A collection, then an empty slot.
        assertEquals(
            listOf(Row.MoveTile, Row.RenameFolder, Row.FolderIcon, Row.RemoveTile),
            actionRows(TileActions(move = true, rename = true, icon = true, remove = RemoveVerb.Remove)),
        )
        assertEquals(listOf(Row.MoveTile, Row.NewCollection), actionRows(TileActions(move = true, newCollection = true)))
        // On a game, New collection lives at the end of Add to, not in Y's menu.
        assertEquals(
            listOf(Row.AddToCollection, Row.SelectTile),
            actionRows(TileActions(collections = choices, select = true, newCollection = true)),
        )
        assertEquals(listOf(Row.AddToCollection), actionRows(TileActions(bulk = listOf("a"), collections = choices, newCollection = true)))
        val model = PickerModel(count = 1, rowsPerPage = 1)
        assertEquals(Copy.unpin, rowText(Row.RemoveTile, model.copy(tileActions = TileActions(remove = RemoveVerb.Unpin))).label)
        assertEquals(Copy.remove, rowText(Row.RemoveTile, model.copy(tileActions = TileActions(remove = RemoveVerb.Remove))).label)
        assertEquals(Copy.newCollection, rowText(Row.NewCollection, model).label)
        assertEquals(Copy.moveToApps, rowText(Row.MoveApp, model.copy(tileActions = TileActions(moveTo = HOME_ANDROID_APPS))).label)
        assertEquals(Copy.moveToGames, rowText(Row.MoveApp, model.copy(tileActions = TileActions(moveTo = HOME_ANDROID_GAMES))).label)
        assertEquals(Copy.icon, rowText(Row.FolderIcon, model).label)
    }

    @Test
    fun moveAppNamesItsGameAndFolderAndR1NeverOffersIt() {
        val focus = GridFocus(cellIndex = 2, lastColumn = 0)
        val tile = TileActions(collections = emptyList(), hide = true, moveTo = HOME_ANDROID_APPS, game = "android:notes")
        val model = PickerModel(count = 3, rowsPerPage = 1, focus = focus, tileActions = tile)
        val open = reduce(model, Meaning.Options).first
        val rows = panelRows(open.panel!!, open)
        assertEquals(listOf(Row.AddToCollection, Row.MoveApp, Row.Hide), rows)
        val moved = reduce(open.copy(panel = open.panel!!.copy(index = rows.indexOf(Row.MoveApp))), Meaning.Activate)
        assertEquals(Effect.MoveApp("android:notes", HOME_ANDROID_APPS), moved.second)
        assertNull(moved.first.panel)
        assertEquals(focus, moved.first.focus)
        val r1 = reduce(model, Meaning.RightPanel).first
        assertTrue(panelRows(r1.panel!!, r1).none { it == Row.MoveApp || it == Row.Hide })
    }

    @Test
    fun hiddenGoesHomeOnBackEvenWhenEmptyOrOpenedFromAFolder() {
        for (count in listOf(0, 3)) {
            for (atRoot in listOf(false, true)) {
                val model = PickerModel(count = count, rowsPerPage = 1, homeGrid = HomeGrid.Hidden, atLibraryRoot = atRoot)
                assertEquals(Effect.GoHome, reduce(model, Meaning.Back).second)
                val menu = reduce(model, Meaning.LeftPanel).first
                val closed = reduce(menu, Meaning.Back)
                assertNull(closed.first.panel)
                assertNull(closed.second)
                assertEquals(HomeGrid.Hidden, closed.first.homeGrid)
            }
        }
    }

    @Test
    fun swipingMovesAScreenOfColumnsOnTheSameRowAndStopsAtTheEnds() {
        // Seven columns of three, the last holding 18 and 19, then three empty columns.
        val model = PickerModel(count = 20, rowsPerPage = 1)
        val next = reduce(model.copy(focus = GridFocus(1, 0)), Meaning.PageTowardEnd).first
        assertEquals(GridStrip.index(Metrics.columns, 1), next.focus.cellIndex)
        val empty = reduce(next, Meaning.PageTowardEnd).first
        assertEquals(GridStrip.index(8, 1), empty.focus.cellIndex)
        val last = reduce(empty, Meaning.PageTowardEnd).first
        assertEquals(GridStrip.index(9, 1), last.focus.cellIndex)
        val stuck = reduce(last, Meaning.PageTowardEnd).first
        assertEquals(GridStrip.index(9, 1), stuck.focus.cellIndex)
        val back = reduce(next, Meaning.PageTowardStart).first
        assertEquals(1, back.focus.cellIndex)
        assertEquals(0, reduce(model, Meaning.PageTowardStart).first.focus.cellIndex)
    }

    @Test
    fun openingOneIslandCollapsesTheOtherBeforeItExpands() {
        val root = PickerModel(count = 2, rowsPerPage = 2)
        val left = reduce(root, Meaning.LeftPanel, HostScreen.Bottom).first
        assertEquals(Side.Left, left.panel?.side)
        assertFalse(left.panel!!.retiring)
        assertNull(left.dialog)
        val closing = reduce(left, Meaning.RightPanel, HostScreen.Top).first
        assertEquals(Side.Left, closing.panel?.side)
        assertTrue(closing.panel!!.retiring)
        assertEquals(Side.Right, closing.panel?.pendingSide)
        assertNull(closing.dialog)
        val right = finishIslandRetire(closing)
        assertEquals(Side.Right, right.panel?.side)
        assertFalse(right.panel!!.retiring)
        assertNull(right.panel?.pendingSide)
        val back = reduce(right, Meaning.LeftPanel, HostScreen.Bottom).first
        assertEquals(Side.Right, back.panel?.side)
        assertTrue(back.panel!!.retiring)
        assertEquals(Side.Left, back.panel?.pendingSide)
        val reopened = finishIslandRetire(back)
        assertEquals(Side.Left, reopened.panel?.side)
        assertNull(reopened.panel?.pendingSide)
        val cancelled = reduce(closing, Meaning.LeftPanel, HostScreen.Top).first
        assertTrue(cancelled.panel!!.retiring)
        assertNull(cancelled.panel?.pendingSide)
        assertNull(finishIslandRetire(cancelled).panel)
    }

    @Test
    fun debugHoldShowsThatShoulderImmediately() {
        val root = PickerModel(count = 2, rowsPerPage = 2)
        val left = replaceIsland(root, Side.Left)
        assertEquals(Side.Left, left.panel?.side)
        assertEquals(HostScreen.Top, left.panel?.screen)
        assertFalse(left.panel!!.retiring)
        val right = replaceIsland(left, Side.Right)
        assertEquals(Side.Right, right.panel?.side)
        assertFalse(right.panel!!.retiring)
        assertNull(right.panel?.pendingSide)
        val again = replaceIsland(right, Side.Left)
        assertEquals(Side.Left, again.panel?.side)
        assertFalse(again.panel!!.retiring)
        val live = reduce(again, Meaning.RightPanel, HostScreen.Top).first
        assertEquals(Side.Left, live.panel?.side)
        assertTrue(live.panel!!.retiring)
        assertEquals(Side.Right, live.panel?.pendingSide)
    }

    @Test
    fun leftPanelCyclesValuesAndRightReplacesIt() {
        val root = PickerModel(count = 2, rowsPerPage = 3, themes = listOf("Built-in", "Sample"))
        val (stayed, effect) = reduce(root, Meaning.Back)
        assertNull(effect)
        assertEquals(root.focus, stayed.focus)
        val opened = reduce(root, Meaning.LeftPanel, HostScreen.Bottom).first
        assertEquals(Side.Left, opened.panel?.side)
        assertEquals(HostScreen.Top, opened.panel?.screen)
        val onPrimary = root.onSetting(Row.Primary)
        val primary = reduce(onPrimary, Meaning.Activate).first
        assertFalse(primary.primaryIsTop)
        assertEquals(onPrimary.settings, primary.settings)
        val theme = reduce(root.onSetting(Row.Theme), Meaning.Activate).first
        assertEquals(1, theme.themeIndex)
        // The sources live on the Settings page's Library rows, not in L1.
        assertFalse(leftRows().any { it is Row.AddFolder || it is Row.RommConnection })
        val (folder, add) = reduce(root.onSetting(Row.AddFolder), Meaning.Activate)
        assertNull(add)
        assertEquals(DialogKind.Folder, folder.dialog?.kind)
        val signedIn = root.copy(signedIn = listOf(SignedInBackend("romm", "RomM")))
        val connection = libraryRows(signedIn.signedIn).first()
        assertEquals("RomM", rowLabel(connection, signedIn))
        val (actions, openConnection) = reduce(signedIn.onSetting(connection), Meaning.Activate)
        assertNull(openConnection)
        assertEquals(PanelLevel.RommConnection, actions.panel?.level)
        assertEquals(listOf(Row.EditRomm("romm"), Row.SignOut("romm", "RomM")), panelRows(actions.panel!!, actions))
        val closed = reduce(opened, Meaning.Back).first
        assertNull(closed.panel)
        val right = reduce(opened, Meaning.RightPanel, HostScreen.Top).first
        assertEquals(Side.Left, right.panel?.side)
        assertTrue(right.panel!!.retiring)
        assertEquals(Side.Right, right.panel?.pendingSide)
        val openedRight = finishIslandRetire(right)
        assertEquals(Side.Right, openedRight.panel?.side)
        assertEquals(HostScreen.Top, openedRight.panel?.screen)
        assertFalse(openedRight.panel!!.retiring)
        val again = reduce(openedRight, Meaning.RightPanel, HostScreen.Top).first
        assertNull(again.panel)
    }

    @Test
    fun homePromptOpensOnThePrimaryActionAndBackStaysSafe() {
        val prompt = homePrompt()
        assertEquals(DialogButton.UseAsHome, prompt.buttons[prompt.index])
        assertEquals(DialogButton.NotNow, prompt.buttons[prompt.safeIndex])
        val model = PickerModel(count = 2, rowsPerPage = 2, dialog = prompt)
        val (dismissed, choice) = reduce(model, Meaning.Back)
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.Home), choice)
        assertNull(dismissed.dialog)
        val (chosen, use) = reduce(model, Meaning.Activate)
        assertEquals(Effect.DialogChoice(DialogButton.UseAsHome, DialogKind.Home), use)
        assertNull(chosen.dialog)
        val right = reduce(model, Meaning.MoveRight).first
        val (passed, notNow) = reduce(right, Meaning.Activate)
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.Home), notNow)
        assertNull(passed.dialog)
        val shoulders = reduce(model, Meaning.RightPanel).first
        assertEquals(prompt.index, shoulders.dialog?.index)
        assertEquals(prompt, shoulders.dialog)
        val folder = folderExplainer(HostScreen.Bottom)
        assertEquals(DialogButton.ContinueGrant, folder.buttons[folder.index])
        assertEquals(DialogButton.NotNow, folder.buttons[folder.safeIndex])
        val again = signInAgainPrompt()
        assertEquals(DialogButton.Ok, again.buttons[again.index])
        val shown = previewDialogState("home", 1, HostScreen.Top)
        assertEquals(HostScreen.Top, shown?.screen)
        assertEquals(DialogButton.NotNow, shown?.let { it.buttons[it.index] })
        val missing = previewDialogState("missing-player", 0, HostScreen.Bottom)
        assertEquals(DialogButton.Ok, missing?.let { it.buttons[it.index] })
        val saves = previewDialogState("save-folder", 0, HostScreen.Top)
        assertEquals(DialogButton.ChooseSaveFolder, saves?.let { it.buttons[it.index] })
        assertNull(previewDialogState("missing", 0, HostScreen.Bottom))
    }

    @Test
    fun downFromChromeReturnsToTheColumnThatHadACell() {
        val model = PickerModel(count = 6, rowsPerPage = 2)
        val chrome = reduce(model.copy(focus = GridFocus(cellIndex = 3, lastColumn = 1)), Meaning.MoveUp).first
        assertEquals(Chrome.LeftIsland, chrome.focus.chrome)
        val back = reduce(chrome, Meaning.MoveDown).first
        assertNull(back.focus.chrome)
        assertEquals(3, back.focus.cellIndex)
    }

    @Test
    fun repeatedMoveStepsOncePerEvent() {
        val model = PickerModel(count = 6, rowsPerPage = 2)
        val once = reduce(model, Meaning.MoveDown).first
        val twice = reduce(once, Meaning.MoveDown).first
        assertEquals(2, twice.focus.cellIndex)
    }

    @Test
    fun arrangeMovesAHeldIconAndBackDropsIt() {
        val model = PickerModel(count = 4, rowsPerPage = 1, arranging = true)
        val held = reduce(model, Meaning.Activate).first
        assertEquals(0, held.hold?.index)
        val moved = reduce(held, Meaning.MoveDown).first
        assertEquals(listOf(1, 0, 2, 3), moved.order)
        // Arranging reorders the tiles there are, so a held icon stops at the last one.
        val last = reduce(moved.copy(hold = moved.hold?.copy(index = 3), focus = GridFocus(3, 1)), Meaning.MoveDown).first
        assertEquals(3, last.hold?.index)
        // A on an empty slot past the end lifts nothing.
        assertNull(reduce(model.copy(focus = GridFocus(4, 1)), Meaning.Activate).first.hold)
        val dropped = reduce(moved, Meaning.Back).first
        assertNull(dropped.hold)
        assertEquals(listOf(0, 1, 2, 3), displayOrder(dropped))
        val left = reduce(dropped, Meaning.Back).first
        assertFalse(left.arranging)
    }

    @Test
    fun backgroundAndSpeedStaySeparateFromTheTheme() {
        val motions = listOf(BackgroundMotion.Off, BackgroundMotion.Ribbons)
        val root = PickerModel(
            count = 2,
            rowsPerPage = 3,
            themes = listOf("Built-in", "Afterglow"),
            themeMotions = motions,
            themeIndex = 1,
            backgroundMotion = BackgroundMotion.Ribbons,
            motionSpeed = MotionSpeed.Slow,
        )
        val rows = settingsRows(SettingsCategory.Personalization, root)
        assertEquals(listOf(Row.Theme, Row.IconTheme, Row.Background, Row.Themes, Row.MotionSpeed, Row.ButtonLabels), rows)
        val themed = reduce(root.onSetting(Row.Theme), Meaning.Activate).first
        assertEquals(0, themed.themeIndex)
        assertEquals(BackgroundMotion.Off, themed.backgroundMotion)
        assertFalse(themed.backgroundPinned)
        val pinned = reduce(themed.onSetting(Row.Background), Meaning.Activate).first
        assertEquals(BackgroundMotion.Ribbons, pinned.backgroundMotion)
        assertTrue(pinned.backgroundPinned)
        val kept = reduce(pinned.onSetting(Row.Theme), Meaning.Activate).first
        assertEquals(1, kept.themeIndex)
        assertEquals(BackgroundMotion.Ribbons, kept.backgroundMotion)
        val slowed = reduce(pinned.onSetting(Row.MotionSpeed), Meaning.Activate).first
        assertEquals(MotionSpeed.Slower, slowed.motionSpeed)
        assertEquals(BackgroundMotion.Ribbons, slowed.backgroundMotion)
        assertEquals("Background  Ribbons", rowLabel(Row.Background, pinned))
        assertEquals("Motion  Slower", rowLabel(Row.MotionSpeed, slowed))
        assertEquals(RowText("Background", "Ribbons"), rowText(Row.Background, pinned))
        assertEquals(RowText("Motion", "Slower"), rowText(Row.MotionSpeed, slowed))
        assertEquals(RowText(Copy.theme, "Afterglow"), rowText(Row.Theme, kept))
    }

    @Test
    fun azaharDialogsKeepTheRunningGameOnBack() {
        val missing = missingPlayerDialog("Azahar", HostScreen.Bottom)
        assertEquals("Azahar is not installed", missing.title)
        assertEquals(Copy.missingPlayerBody, missing.body)
        assertEquals(DialogKind.MissingPlayer, missing.kind)

        val close = closeBothPanelDialog(HostScreen.Bottom)
        assertEquals(DialogButton.NotNow, close.buttons[close.safeIndex])
        val model = PickerModel(count = 1, rowsPerPage = 1, dialog = close)
        val (kept, choice) = reduce(model, Meaning.Back)
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.ClosePlayer), choice)
        assertNull(kept.dialog)

        val saves = saveFolderDialog(HostScreen.Top)
        assertEquals(DialogKind.SaveFolder, saves.kind)
        assertEquals(HostScreen.Top, saves.screen)
        assertEquals(DialogButton.ChooseSaveFolder, saves.buttons[saves.index])
        assertEquals(DialogButton.PlayWithoutSyncing, saves.buttons[saves.safeIndex])
        assertFalse(saves.body.contains("sdmc"))
        val (started, saveChoice) = reduce(
            PickerModel(count = 1, rowsPerPage = 1, dialog = saves),
            Meaning.Activate,
        )
        assertEquals(Effect.DialogChoice(DialogButton.ChooseSaveFolder, DialogKind.SaveFolder), saveChoice)
        assertNull(started.dialog)

        // A player that cannot start without its folder: Continue is the default, and Not now promises no game.
        val needed = saveFolderDialog(HostScreen.Top, playerName = "aPS3e", opensThere = true, needed = true)
        assertEquals(DialogButton.ChooseSaveFolder, needed.buttons[needed.index])
        assertEquals(DialogButton.NotNow, needed.buttons[needed.safeIndex])
        assertTrue(needed.body.startsWith(Copy.saveFolderNeededBody))
        assertFalse(needed.body.contains("starts the game"))
    }

    @Test
    fun leftPanelOffersThePlayerSaveFolder() {
        val setting = PlayerSaveSetting(playerId = "azahar", label = "Azahar", chosen = false)
        val opened = PickerModel(
            count = 1,
            rowsPerPage = 1,
            homeRoleHeld = true,
            playerSaves = listOf(setting),
        )
        assertEquals(listOf(Row.PlayerSave("azahar")), settingsRows(SettingsCategory.Players, opened))
        val (closed, effect) = reduce(opened.onSetting(Row.PlayerSave("azahar")), Meaning.Activate)
        assertEquals(Effect.ChoosePlayerSave("azahar"), effect)
        assertNull(closed.panel)
        assertNull(closed.settings)
        assertEquals("Azahar  Not syncing", rowLabel(Row.PlayerSave("azahar"), opened))
        assertEquals(RowControl.Value(Copy.notSyncing, attention = true), rowControl(Row.PlayerSave("azahar"), opened))
    }

    @Test
    fun theMemoryCardRowReadsSyncingOrSetUp() {
        val syncing = PlayerSaveSetting("armsx2", "ARMSX2", chosen = true, saveSyncing = true)
        val model = PickerModel(count = 1, rowsPerPage = 1, homeRoleHeld = true, playerSaves = listOf(syncing))
        assertEquals(
            listOf(Row.PlayerSave("armsx2"), Row.SaveSync("armsx2")),
            settingsRows(SettingsCategory.Players, model),
        )
        assertEquals("ARMSX2 memory card  Syncing", rowLabel(Row.SaveSync("armsx2"), model))
        val (leftSyncing, open) = reduce(model.onSetting(Row.SaveSync("armsx2")), Meaning.Activate)
        assertEquals(Effect.OpenPlayer("armsx2"), open)
        assertNull(leftSyncing.settings)
        val steps = "Slot 1 needs a folder card to sync."
        val asking = model.copy(playerSaves = listOf(syncing.copy(saveSyncing = false, saveSyncSteps = steps)))
        assertEquals("ARMSX2 memory card  Not syncing", rowLabel(Row.SaveSync("armsx2"), asking))
        assertEquals(RowControl.Value(Copy.notSyncing, attention = true), rowControl(Row.SaveSync("armsx2"), asking))
        val (closed, effect) = reduce(asking.onSetting(Row.SaveSync("armsx2")), Meaning.Activate)
        assertEquals(Effect.ShowSaveSync("armsx2"), effect)
        assertNull(closed.settings)
        val dialog = saveSyncDialog("ARMSX2", steps, HostScreen.Top)
        assertEquals("Sync ARMSX2 saves?", dialog.title)
        assertEquals(steps, dialog.body)
        assertEquals("Open ARMSX2", openPlayerLabel(dialog.subject))
    }

    @Test
    fun aChosenFolderRowDoesNotClaimToFixSeparateSaveSetup() {
        val settings = listOf(
            PlayerSaveSetting("duckstation", "DuckStation", true, hostedTitle = "DuckStation memory cards"),
            PlayerSaveSetting("armsx2", "ARMSX2", true, saveSyncSteps = "Choose a folder card"),
            PlayerSaveSetting("aps3e", "aPS3e", true, setupSteps = "Install firmware"),
        )
        for (setting in settings) {
            val model = PickerModel(count = 1, rowsPerPage = 1, playerSaves = listOf(setting))
            val row = Row.PlayerSave(setting.playerId)
            assertEquals(Copy.playerSavesSet, rowText(row, model).value)
            assertEquals(RowControl.Value(Copy.playerSavesSet), rowControl(row, model))
            assertEquals(Effect.ChoosePlayerSave(setting.playerId), reduce(model.onSetting(row), Meaning.Activate).second)
            val fix = when (setting.playerId) {
                "duckstation" -> Row.HostedSaves(setting.playerId)
                "armsx2" -> Row.SaveSync(setting.playerId)
                else -> Row.PlayerSetup(setting.playerId)
            }
            assertEquals(Copy.notSyncing, rowText(fix, model).value)
            assertEquals(RowControl.Value(Copy.notSyncing, attention = true), rowControl(fix, model))
        }
    }

    @Test
    fun hostedCardsInUseMakeTheUnchosenDataFolderOptional() {
        val setting = PlayerSaveSetting("duckstation", "DuckStation", false, hostedTitle = "Cards", hostedInUse = true)
        val model = PickerModel(count = 1, rowsPerPage = 1, playerSaves = listOf(setting))
        assertEquals(Copy.playerSavesUnset, rowText(Row.PlayerSave(setting.playerId), model).value)
        assertEquals(RowControl.Value(Copy.playerSavesUnset), rowControl(Row.PlayerSave(setting.playerId), model))
        assertEquals(Copy.hostedSavesSet, rowText(Row.HostedSaves(setting.playerId), model).value)
    }

    @Test
    fun theSetupRowShowsThePlayersSteps() {
        val steps = "Open aPS3e and finish its setup."
        val setting = PlayerSaveSetting("aps3e", "aPS3e", chosen = true, setupSteps = steps)
        val model = PickerModel(count = 1, rowsPerPage = 1, homeRoleHeld = true, playerSaves = listOf(setting))
        assertEquals(
            listOf(Row.PlayerSave("aps3e"), Row.PlayerSetup("aps3e")),
            settingsRows(SettingsCategory.Players, model),
        )
        assertEquals("aPS3e setup  Not syncing", rowLabel(Row.PlayerSetup("aps3e"), model))
        assertEquals(RowControl.Value(Copy.notSyncing, attention = true), rowControl(Row.PlayerSetup("aps3e"), model))
        val (closed, effect) = reduce(model.onSetting(Row.PlayerSetup("aps3e")), Meaning.Activate)
        assertEquals(Effect.ShowPlayerSetup("aps3e"), effect)
        assertNull(closed.settings)
        val dialog = playerSetupDialog("aPS3e", steps, HostScreen.Top)
        assertEquals(DialogKind.HostedSaves, dialog.kind)
        assertEquals("Set up aPS3e", dialog.title)
        assertEquals(steps, dialog.body)
        assertEquals(listOf(DialogButton.OpenPlayer, DialogButton.NotNow), dialog.buttons)
        assertEquals(0, dialog.index)
        assertEquals(1, dialog.safeIndex)
        assertEquals("aPS3e", dialog.subject)
        assertEquals("Open aPS3e", openPlayerLabel(dialog.subject))
        val ready = model.copy(playerSaves = listOf(setting.copy(setupSteps = null)))
        assertEquals(listOf(Row.PlayerSave("aps3e")), settingsRows(SettingsCategory.Players, ready))
    }

    @Test
    fun motionTokensAreTheSharedScale() {
        assertEquals(100, Motion.durationShort)
        assertEquals(180, Motion.durationFocus)
        assertEquals(200, Motion.durationTravel)
        assertEquals(320, Motion.durationIsland)
        assertTrue(Motion.durationIsland in 280..350)
        assertEquals(1f, Motion.scaleRest)
        assertEquals(1.05f, Motion.scaleFocus)
        assertEquals(100, Motion.duration(Motion.durationTravel, animatorScale = 0f))
        assertEquals(400, Motion.duration(Motion.durationTravel, animatorScale = 2f))
        assertEquals(180, Motion.duration(Motion.durationFocus, animatorScale = 1f))
        assertTrue(Motion.duration(Motion.durationShort, animatorScale = 0.01f) >= 1)
        assertTrue(Motion.reduced(0f))
        assertFalse(Motion.reduced(1f))
        assertEquals(0f, Motion.easingArrive.transform(0f), 0.001f)
        assertEquals(1f, Motion.easingArrive.transform(1f), 0.001f)
        assertEquals(0f, Motion.easingLeave.transform(0f), 0.001f)
        assertEquals(1f, Motion.easingLeave.transform(1f), 0.001f)
        assertEquals(0f, Motion.easingDissolve.transform(0f), 0.001f)
        assertEquals(1f, Motion.easingDissolve.transform(1f), 0.001f)
        // A dissolve opens gentler than an arrival, so art does not cut over art.
        assertTrue(Motion.easingDissolve.transform(0.05f) < Motion.easingArrive.transform(0.05f))
        assertTrue(Motion.easingArrive.transform(0.5f) != 0.5f)
    }

    @Test
    fun noLibraryMovesBetweenAddFolderAndConnect() {
        val model = PickerModel(
            count = 2,
            rowsPerPage = 1,
            gridKind = GridKind.NoLibrary,
            libraryGrid = true,
            folderGrantPending = false,
        )
        val (added, add) = reduce(model, Meaning.Activate)
        assertEquals(Effect.AddFolder, add)
        assertNull(added.dialog)
        val right = reduce(model, Meaning.MoveRight).first
        assertEquals(1, right.focus.cellIndex)
        val (opened, connect) = reduce(right, Meaning.Activate)
        assertEquals(Effect.OpenAddRomm, connect)
        assertTrue(opened.connectOpen)
        val pending = model.copy(folderGrantPending = true)
        assertEquals(DialogKind.Folder, reduce(pending, Meaning.Activate).first.dialog?.kind)
    }

    @Test
    fun aPlatformOpensAndBackLeavesIt() {
        val platforms = PickerModel(
            count = 2,
            rowsPerPage = 1,
            gridKind = GridKind.Platforms,
            libraryGrid = true,
            atLibraryRoot = true,
        )
        assertEquals(Effect.OpenPlatform(0), reduce(platforms, Meaning.Activate).second)
        assertNull(reduce(platforms, Meaning.Back).second)
        val games = platforms.copy(gridKind = GridKind.Games, atLibraryRoot = false, count = 3)
        assertEquals(Effect.Launch(0), reduce(games, Meaning.Activate).second)
        assertEquals(Effect.LeavePlatform, reduce(games, Meaning.Back).second)
        val none = games.copy(count = 0, emptyGrid = EmptyGrid.NoGames)
        assertEquals(Effect.LeavePlatform, reduce(none, Meaning.Back).second)
        assertNull(reduce(none, Meaning.Activate).second)
    }

    @Test
    fun anUnreachableLibraryTriesAgain() {
        val model = PickerModel(
            count = 1,
            rowsPerPage = 1,
            gridKind = GridKind.Unreachable,
            libraryGrid = true,
        )
        assertEquals(Effect.TryAgain, reduce(model, Meaning.Activate).second)
    }

    @Test
    fun libraryRowsAddSourcesAndNeverSwitchTheGrid() {
        assertEquals(listOf(Row.AddFolder, Row.AddRomm), libraryRows())

        val signedIn = libraryRows(listOf(SignedInBackend("romm", "RomM")))
        assertEquals(
            listOf(Row.RommConnection("romm", "RomM"), Row.AddFolder, Row.AddRomm),
            signedIn,
        )
        val other = libraryRows(listOf(SignedInBackend("other", "Other")))
        assertEquals(listOf(Row.SignOut("other", "Other"), Row.AddFolder, Row.AddRomm), other)
    }

    @Test
    fun addRommOpensTheSameConnectForm() {
        val home = PickerModel(
            count = 1,
            rowsPerPage = 1,
            signedIn = listOf(SignedInBackend("romm", "RomM")),
        )
        val (opened, effect) = reduce(home.onSetting(Row.AddRomm), Meaning.Activate)
        assertEquals(Effect.OpenAddRomm, effect)
        assertTrue(opened.connectAnother)
        assertEquals("", opened.connectOrigin)
        assertEquals(ConnectKind.Romm, opened.connectKind)
        assertEquals(listOf(ConnectField.Origin, ConnectField.Token, ConnectField.Save), connectFields(opened.connectKind))
        assertEquals("Add a RomM server", rowLabel(Row.AddRomm, opened))
        assertNull(opened.settings)

        // Each folder can be removed on its own. Adding another keeps the first.
        val folders = libraryRows(folders = listOf(LibraryFolder("content://roms", "roms"), LibraryFolder("content://steam", "Steam")))
        assertEquals(
            listOf(Row.AddFolder, Row.AddRomm, Row.ForgetFolder("content://roms", "roms"), Row.ForgetFolder("content://steam", "Steam")),
            folders,
        )
    }
}
