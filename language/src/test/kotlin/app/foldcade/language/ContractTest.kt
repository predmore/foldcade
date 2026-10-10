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
        val model = PickerModel(count = 8, rowsPerPage = 1)
        val paged = reduce(model, Meaning.PageTowardEnd).first
        assertEquals(4, paged.focus.cellIndex)
        val shoulder = reduce(model, Meaning.LeftPanel).first
        assertEquals(0, shoulder.focus.cellIndex)
        assertEquals(Side.Left, shoulder.panel?.side)
    }

    @Test
    fun unusedKeysAreNotGivenAMeaning() {
        listOf(
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT,
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
        assertEquals(HintActions(confirm = false, back = true), hintFor(HintPlace.Connect))
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
            listOf(PromptKey.FaceA, PromptKey.FaceY, PromptKey.FaceX, PromptKey.Start),
            gridHints(null, HomeKeys.Editing, map).map { it.key },
        )
        assertEquals(
            listOf(PromptKey.FaceY, PromptKey.FaceB),
            gridHints(hintFor(HintPlace.InsidePlatform), HomeKeys.AllLibrary, map).map { it.key },
        )
        assertEquals(
            listOf(PromptKey.FaceA, PromptKey.FaceX, PromptKey.FaceY, PromptKey.FaceB),
            gridHints(hintFor(HintPlace.InsidePlatform), HomeKeys.AllLibrary, map, screens = true).map { it.key },
        )
    }

    @Test
    fun focusDoesNotWrapAndUpReachesTheStatusIsland() {
        val model = PickerModel(count = 6, rowsPerPage = 1)
        val rightEdge = reduce(model.copy(focus = GridFocus(cellIndex = 3, lastColumn = 3)), Meaning.MoveRight).first
        assertEquals(3, rightEdge.focus.cellIndex)
        val up = reduce(model.copy(focus = GridFocus(cellIndex = 1, lastColumn = 1)), Meaning.MoveUp).first
        assertEquals(Chrome.StatusCluster, up.focus.chrome)
        val cluster = reduce(model.copy(focus = GridFocus(cellIndex = 3, lastColumn = 3)), Meaning.MoveUp).first
        assertEquals(Chrome.StatusCluster, cluster.focus.chrome)
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
    fun pagingKeepsTheSlotAndStopsAtTheEnds() {
        val model = PickerModel(count = 6, rowsPerPage = 1)
        val next = reduce(model.copy(focus = GridFocus(1, 1)), Meaning.PageTowardEnd).first
        assertEquals(5, next.focus.cellIndex)
        val stuck = reduce(next, Meaning.PageTowardEnd).first
        assertEquals(5, stuck.focus.cellIndex)
        val back = reduce(model, Meaning.PageTowardStart).first
        assertEquals(0, back.focus.cellIndex)
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
        val child = reduce(opened, Meaning.Activate).first
        assertEquals(PanelLevel.Library, child.panel?.level)
        val (folder, add) = reduce(child, Meaning.Activate)
        assertNull(add)
        assertEquals(DialogKind.Folder, folder.dialog?.kind)
        val signedIn = child.copy(
            signedIn = listOf(SignedInBackend("romm", "RomM")),
            panel = child.panel?.copy(index = 0),
        )
        val signOut = libraryRows(signedIn.signedIn).first()
        assertEquals("Sign out / forget credentials · RomM", rowLabel(signOut, signedIn))
        val (left, forget) = reduce(signedIn, Meaning.Activate)
        assertEquals(Effect.ForgetCredentials("romm"), forget)
        assertNull(left.panel)
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
        assertEquals(DialogButton.ContinueGrant, saves?.let { it.buttons[it.index] })
        assertNull(previewDialogState("missing", 0, HostScreen.Bottom))
    }

    @Test
    fun downFromChromeReturnsToTheColumnThatHadACell() {
        val model = PickerModel(count = 6, rowsPerPage = 2)
        val chrome = reduce(model.copy(focus = GridFocus(cellIndex = 1, lastColumn = 1)), Meaning.MoveUp).first
        assertEquals(Chrome.StatusCluster, chrome.focus.chrome)
        val back = reduce(chrome, Meaning.MoveDown).first
        assertNull(back.focus.chrome)
        assertEquals(1, back.focus.cellIndex)
    }

    @Test
    fun repeatedMoveStepsOncePerEvent() {
        val model = PickerModel(count = 6, rowsPerPage = 2)
        val once = reduce(model, Meaning.MoveRight).first
        val twice = reduce(once, Meaning.MoveRight).first
        assertEquals(2, twice.focus.cellIndex)
    }

    @Test
    fun arrangeMovesAHeldIconAndBackDropsIt() {
        val model = PickerModel(count = 4, rowsPerPage = 1, arranging = true)
        val held = reduce(model, Meaning.Activate).first
        assertEquals(0, held.hold?.index)
        val moved = reduce(held, Meaning.MoveRight).first
        assertEquals(listOf(1, 0, 2, 3), moved.order)
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
        assertEquals(listOf(Row.Theme, Row.Background, Row.MotionSpeed), rows)
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
        assertEquals(DialogButton.NotNow, saves.buttons[saves.index])
        assertEquals(DialogButton.NotNow, saves.buttons[saves.safeIndex])
        assertFalse(saves.body.contains("sdmc"))
        val (started, saveChoice) = reduce(
            PickerModel(count = 1, rowsPerPage = 1, dialog = saves),
            Meaning.Activate,
        )
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.SaveFolder), saveChoice)
        assertNull(started.dialog)
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
        assertEquals("Azahar saves  Not set", rowLabel(Row.PlayerSave("azahar"), opened))
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
        assertEquals(Effect.OpenConnect, connect)
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
        assertEquals(listOf(Row.AddFolder, Row.Connect), libraryRows())

        val signedIn = libraryRows(listOf(SignedInBackend("romm", "RomM")))
        assertEquals(listOf(Row.SignOut("romm", "RomM"), Row.AddFolder, Row.Connect), signedIn)

        // Each folder can be removed on its own. Adding another keeps the first.
        val folders = libraryRows(folders = listOf(LibraryFolder("content://roms", "roms"), LibraryFolder("content://steam", "Steam")))
        assertEquals(
            listOf(Row.AddFolder, Row.Connect, Row.ForgetFolder("content://roms", "roms"), Row.ForgetFolder("content://steam", "Steam")),
            folders,
        )
    }
}
