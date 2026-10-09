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
        val model = PickerModel(count = 8, rowsPerPage = 1, showLaunchTarget = false)
        val paged = reduce(model, Meaning.PageTowardEnd).first
        assertEquals(4, paged.focus.cellIndex)
        val shoulder = reduce(model, Meaning.LeftPanel).first
        assertEquals(0, shoulder.focus.cellIndex)
        assertEquals(Side.Left, shoulder.panel?.side)
    }

    @Test
    fun unusedKeysAreNotGivenAMeaning() {
        listOf(
            KeyEvent.KEYCODE_BUTTON_X,
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
        assertEquals(Color(0xFF000000), theme.surface)
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
        val model = PickerModel(count = 2, rowsPerPage = 2, showLaunchTarget = true, dialog = prompt)
        assertEquals(1, prompt.index)
        val (next, effect) = focusAndActivateDialog(model, 0)
        assertEquals(Effect.DialogChoice(DialogButton.UseAsHome, DialogKind.Home), effect)
        assertNull(next.dialog)
    }

    @Test
    fun hintsUseAAndBOnlyWhenThatActionDoesSomething() {
        assertEquals("A", hintFor(HintPlace.RootGrid))
        assertEquals("A  B", hintFor(HintPlace.InsidePlatform))
        assertEquals("B", hintFor(HintPlace.Connect))
        assertNull(hintLine(activateDoesSomething = false, backDoesSomething = false))
        assertFalse(hintFor(HintPlace.RootGrid)!!.contains("X"))
        assertFalse(hintFor(HintPlace.Dialog)!!.contains("L1"))
    }

    @Test
    fun focusDoesNotWrapAndLaunchTargetIsChrome() {
        val model = PickerModel(count = 6, rowsPerPage = 1, showLaunchTarget = true)
        val rightEdge = reduce(model.copy(focus = GridFocus(cellIndex = 3, lastColumn = 3)), Meaning.MoveRight).first
        assertEquals(3, rightEdge.focus.cellIndex)
        val up = reduce(model.copy(focus = GridFocus(cellIndex = 1, lastColumn = 1)), Meaning.MoveUp).first
        assertEquals(Chrome.LaunchTarget, up.focus.chrome)
        val (cycled, effect) = reduce(up, Meaning.Activate)
        assertEquals(Effect.CycleLaunchTarget, effect)
        assertNull(cycled.panel)
        assertTrue(cycled.launchOnBottom)
        val cluster = reduce(model.copy(focus = GridFocus(cellIndex = 3, lastColumn = 3)), Meaning.MoveUp).first
        assertEquals(Chrome.StatusCluster, cluster.focus.chrome)
    }

    @Test
    fun pagingKeepsTheSlotAndStopsAtTheEnds() {
        val model = PickerModel(count = 6, rowsPerPage = 1, showLaunchTarget = false)
        val next = reduce(model.copy(focus = GridFocus(1, 1)), Meaning.PageTowardEnd).first
        assertEquals(5, next.focus.cellIndex)
        val stuck = reduce(next, Meaning.PageTowardEnd).first
        assertEquals(5, stuck.focus.cellIndex)
        val back = reduce(model, Meaning.PageTowardStart).first
        assertEquals(0, back.focus.cellIndex)
    }

    @Test
    fun leftPanelCyclesValuesAndRightReplacesIt() {
        val root = PickerModel(count = 2, rowsPerPage = 3, showLaunchTarget = true, themes = listOf("Built-in", "Sample"))
        val (stayed, effect) = reduce(root, Meaning.Back)
        assertNull(effect)
        assertEquals(root.focus, stayed.focus)
        val opened = reduce(root, Meaning.LeftPanel, HostScreen.Bottom).first
        assertEquals(Side.Left, opened.panel?.side)
        assertEquals(HostScreen.Bottom, opened.panel?.screen)
        val primary = reduce(opened.copy(panel = opened.panel?.copy(index = 2)), Meaning.Activate).first
        assertFalse(primary.primaryIsTop)
        assertEquals(2, primary.panel?.index)
        val theme = reduce(opened.copy(panel = opened.panel?.copy(index = 1)), Meaning.Activate).first
        assertEquals(1, theme.themeIndex)
        val child = reduce(opened, Meaning.Activate).first
        assertEquals(PanelLevel.Library, child.panel?.level)
        val (folder, add) = reduce(child, Meaning.Activate)
        assertNull(add)
        assertEquals(DialogKind.Folder, folder.dialog?.kind)
        val closed = reduce(opened, Meaning.Back).first
        assertNull(closed.panel)
        val right = reduce(opened, Meaning.RightPanel, HostScreen.Top).first
        assertEquals(Side.Right, right.panel?.side)
        assertEquals(HostScreen.Top, right.panel?.screen)
        val again = reduce(right, Meaning.RightPanel, HostScreen.Top).first
        assertNull(again.panel)
    }

    @Test
    fun homePromptOpensOnTheSafeButtonAndShouldersDoNothing() {
        val prompt = homePrompt()
        assertEquals(DialogButton.NotNow, prompt.buttons[prompt.index])
        val model = PickerModel(count = 2, rowsPerPage = 2, showLaunchTarget = true, dialog = prompt)
        val (dismissed, choice) = reduce(model, Meaning.Back)
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.Home), choice)
        assertNull(dismissed.dialog)
        val left = reduce(model, Meaning.MoveLeft).first
        val (chosen, use) = reduce(left, Meaning.Activate)
        assertEquals(Effect.DialogChoice(DialogButton.UseAsHome, DialogKind.Home), use)
        assertNull(chosen.dialog)
        val shoulders = reduce(model, Meaning.RightPanel).first
        assertEquals(prompt.index, shoulders.dialog?.index)
        assertEquals(prompt, shoulders.dialog)
    }

    @Test
    fun downFromChromeReturnsToTheColumnThatHadACell() {
        val model = PickerModel(count = 6, rowsPerPage = 2, showLaunchTarget = true)
        val chrome = reduce(model.copy(focus = GridFocus(cellIndex = 1, lastColumn = 1)), Meaning.MoveUp).first
        assertEquals(Chrome.LaunchTarget, chrome.focus.chrome)
        val back = reduce(chrome, Meaning.MoveDown).first
        assertNull(back.focus.chrome)
        assertEquals(1, back.focus.cellIndex)
    }

    @Test
    fun repeatedMoveStepsOncePerEvent() {
        val model = PickerModel(count = 6, rowsPerPage = 2, showLaunchTarget = false)
        val once = reduce(model, Meaning.MoveRight).first
        val twice = reduce(once, Meaning.MoveRight).first
        assertEquals(2, twice.focus.cellIndex)
    }

    @Test
    fun arrangeMovesAHeldIconAndBackDropsIt() {
        val model = PickerModel(count = 4, rowsPerPage = 1, showLaunchTarget = false, arranging = true)
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
    fun motionTokensAreTheSharedScale() {
        assertEquals(100, Motion.durationShort)
        assertEquals(150, Motion.durationFocus)
        assertEquals(200, Motion.durationTravel)
        assertEquals(1f, Motion.scaleRest)
        assertEquals(1.12f, Motion.scaleFocus)
        assertEquals(100, Motion.duration(Motion.durationTravel, animatorScale = 0f))
        assertEquals(400, Motion.duration(Motion.durationTravel, animatorScale = 2f))
        assertEquals(150, Motion.duration(Motion.durationFocus, animatorScale = 1f))
        assertTrue(Motion.duration(Motion.durationShort, animatorScale = 0.01f) >= 1)
        assertTrue(Motion.reduced(0f))
        assertFalse(Motion.reduced(1f))
        assertEquals(0f, Motion.easingArrive.transform(0f), 0.001f)
        assertEquals(1f, Motion.easingArrive.transform(1f), 0.001f)
        assertEquals(0f, Motion.easingLeave.transform(0f), 0.001f)
        assertEquals(1f, Motion.easingLeave.transform(1f), 0.001f)
        assertTrue(Motion.easingArrive.transform(0.5f) != 0.5f)
    }
}
