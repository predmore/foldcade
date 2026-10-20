package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class ControllerGridTest {
    @Test fun unusedControllerButtonsCannotFallBackToActivation() {
        val unused = listOf(KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_BUTTON_C, KeyEvent.KEYCODE_BUTTON_Z,
            KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_BUTTON_16)
        unused.forEach {
            assertNull(meaningOf(it))
            assertTrue(consumesUnmapped(it))
        }
        assertTrue(consumesUnmapped(KeyEvent.KEYCODE_BUTTON_START))
        assertFalse(consumesUnmapped(KeyEvent.KEYCODE_ENTER))
        assertFalse(consumesUnmapped(KeyEvent.KEYCODE_HOME))
        assertEquals(Meaning.LetterBackward, meaningOf(KeyEvent.KEYCODE_BUTTON_L2))
        assertEquals(Meaning.LetterForward, meaningOf(KeyEvent.KEYCODE_BUTTON_R2))
        assertEquals(Meaning.MoveDown, meaningOf(KeyEvent.KEYCODE_DPAD_DOWN, 1, homeKeys = HomeKeys.AllLibrary))
    }

    @Test fun triggersJumpFourColumnsAndClampToTheReachKeepingTheRow() {
        val model = PickerModel(count = 20, rowsPerPage = 3, focus = GridFocus(1))
        val right = reduce(model, Meaning.LetterForward).first
        assertEquals(13, right.focus.cellIndex)
        val edge = reduce(reduce(right, Meaning.LetterForward).first, Meaning.LetterForward).first
        assertEquals(GridStrip.index(GridStrip.reachColumns(20) - 1, 1), edge.focus.cellIndex)
        assertEquals(edge.focus, reduce(edge, Meaning.LetterForward).first.focus)
        val left = reduce(model.copy(focus = GridFocus(4)), Meaning.LetterBackward).first
        assertEquals(1, left.focus.cellIndex)
        assertNull(left.letterJump)
    }

    @Test fun heldDirectionsStopAtTheRealTailButFreshPressesCanContinue() {
        val model = PickerModel(count = 4, rowsPerPage = 3, focus = GridFocus(3))
        assertEquals(3, reduce(model, Meaning.MoveRight, repeatCount = 1).first.focus.cellIndex)
        assertEquals(6, reduce(model, Meaning.MoveRight).first.focus.cellIndex)
        assertEquals(3, reduce(model, Meaning.MoveDown, repeatCount = 1).first.focus.cellIndex)
        assertEquals(4, reduce(model, Meaning.MoveDown).first.focus.cellIndex)
        val gaps = model.copy(count = 9, lastOccupiedIndex = 3)
        assertEquals(3, reduce(gaps, Meaning.MoveRight, repeatCount = 1).first.focus.cellIndex)
        val past = model.copy(focus = GridFocus(6))
        assertEquals(3, reduce(past, Meaning.MoveLeft, repeatCount = 1).first.focus.cellIndex)
        assertEquals(0, reduce(model.copy(focus = GridFocus()), Meaning.MoveLeft, repeatCount = 1).first.focus.cellIndex)
    }

    @Test fun allAndShelvesJumpLetterGroupsWithTheSamePrompt() {
        for (grid in HomeGrid.entries) {
            val model = PickerModel(count = 5, rowsPerPage = 3, homeGrid = grid,
                gridLabels = listOf("1 game", "! game", "Alpha", "Aster", "Beta"))
            val jumped = reduce(model, Meaning.LetterForward).first
            assertEquals(2, jumped.focus.cellIndex)
            assertEquals("A", jumped.letterJump)
            assertEquals(1, jumped.letterJumpEpoch)
            val back = reduce(jumped, Meaning.LetterBackward).first
            assertEquals(0, back.focus.cellIndex)
            assertEquals("#", back.letterJump)
            assertEquals(2, back.letterJumpEpoch)
        }
        assertEquals(listOf(PromptKey.L2, PromptKey.R2), triggerHints(true).map { it.key })
        assertEquals(Copy.letterJump, triggerHints(true).last().label)
        assertEquals(Copy.screenJump, triggerHints(false).last().label)
    }

    @Test fun allRecentOrdersPlayedGamesFirstAndUnplayedAlphabetically() {
        val items = listOf(
            HomeItem("b", "Beta", HomeKind.Rom),
            HomeItem("z", "Zulu", HomeKind.AndroidGame, lastPlayedMillis = 30),
            HomeItem("c", "Charlie", HomeKind.Rom, lastPlayedMillis = 10),
            HomeItem("a", "Alpha", HomeKind.Rom),
            HomeItem("app", "App", HomeKind.AndroidApp),
        )
        assertEquals(listOf("a", "b", "c", "z"), allItems(items).map { it.id })
        assertEquals(listOf("z", "c", "a", "b"), allItems(items, LibrarySort.RecentlyPlayed).map { it.id })
    }

    @Test fun installedAppsGoOnlyInTheAndroidFoldersAndCollections() {
        val folders = listOf(
            HomeFolder("roms", "ROMs", platformId = "ds"),
            HomeFolder("games", "Games", bucket = HomeKind.AndroidGame),
            HomeFolder("apps", "Apps", bucket = HomeKind.AndroidApp),
            HomeFolder("mine", "Mine", userMade = true),
        )
        val game = HomeItem("g", "Game", HomeKind.AndroidGame)
        val app = HomeItem("a", "App", HomeKind.AndroidApp)
        assertEquals(listOf("games", "apps", "mine"), folders.filter { folderTakes(it, game, emptyList()) }.map { it.id })
        assertEquals(listOf("games", "apps", "mine"), folders.filter { folderTakes(it, app, emptyList()) }.map { it.id })
    }

    @Test fun startUsesTheSameSettingsPageAsTheMenuAndClosesIt() {
        val model = PickerModel(count = 10, rowsPerPage = 3, focus = GridFocus(4))
        val opened = reduce(model, Meaning.Menu).first
        assertEquals(HostScreen.Top, opened.settings?.screen)
        assertEquals(model.focus, opened.settings?.grid)
        assertNull(reduce(opened, Meaning.Menu).first.settings)
        val menu = reduce(model, Meaning.LeftPanel).first
        assertEquals(opened.settings, reduce(menu, Meaning.Menu).first.settings)
        val dialog = model.copy(dialog = homePrompt())
        assertNull(reduce(dialog, Meaning.Menu).first.settings)
    }
    @Test fun theIconEditorKeepsItsShouldersWhenAnAppCoversTheIslands() {
        // An app on the top screen: L1, R1 and Start have no islands to open.
        for (meaning in listOf(Meaning.LeftPanel, Meaning.RightPanel, Meaning.Menu)) {
            assertTrue(topMenuKeyBlocked(meaning, HomeKeys.Idle, islandsOpen = false))
            assertFalse(topMenuKeyBlocked(meaning, HomeKeys.Idle, islandsOpen = true))
        }
        // The editor on the bottom screen still steps its colour with them, as its hint says.
        val l1 = meaningOf(KeyEvent.KEYCODE_BUTTON_L1, homeKeys = HomeKeys.PickingIcon)
        val r1 = meaningOf(KeyEvent.KEYCODE_BUTTON_R1, homeKeys = HomeKeys.PickingIcon)
        assertEquals(Meaning.LeftPanel, l1)
        assertFalse(topMenuKeyBlocked(l1!!, HomeKeys.PickingIcon, islandsOpen = false))
        assertFalse(topMenuKeyBlocked(r1!!, HomeKeys.PickingIcon, islandsOpen = false))
        assertTrue(topMenuKeyBlocked(Meaning.Menu, HomeKeys.PickingIcon, islandsOpen = false))
        // The keyboard's shoulders move its caret, so they never meet this guard.
        for (key in listOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1)) {
            val caret = meaningOf(key, homeKeys = HomeKeys.Typing)!!
            assertFalse(opensTopMenu(caret))
            assertFalse(topMenuKeyBlocked(caret, HomeKeys.Typing, islandsOpen = false))
        }
    }

    @Test fun startSharesMenuOwnershipAndPendingLaunchCancellation() {
        for (meaning in listOf(Meaning.LeftPanel, Meaning.RightPanel, Meaning.Menu)) {
            assertTrue(opensTopMenu(meaning))
            assertTrue(cancelsPendingLaunch(meaning, dialogOpen = false, conflictOpen = false))
            assertFalse(cancelsPendingLaunch(meaning, dialogOpen = true, conflictOpen = false))
            assertFalse(cancelsPendingLaunch(meaning, dialogOpen = false, conflictOpen = true))
        }
        assertFalse(opensTopMenu(Meaning.Activate))
        assertFalse(cancelsPendingLaunch(Meaning.Activate, dialogOpen = false, conflictOpen = false))
        assertTrue(cancelsPendingLaunch(Meaning.Back, dialogOpen = false, conflictOpen = false))
    }

    @Test fun homeTriggersStopAtOccupiedColumnsWhileSwipesKeepTheirReach() {
        val short = PickerModel(count = 9, rowsPerPage = 3, focus = GridFocus(1), screenJumpLastColumn = 2)
        val right = reduce(short, Meaning.LetterForward).first
        assertEquals(7, right.focus.cellIndex)
        assertEquals(right.focus, reduce(right, Meaning.LetterForward).first.focus)
        val left = reduce(right, Meaning.LetterBackward).first
        assertEquals(1, left.focus.cellIndex)
        assertEquals(left.focus, reduce(left, Meaning.LetterBackward).first.focus)
        val gaps = short.copy(count = 15, lastOccupiedIndex = 6, screenJumpLastColumn = 2)
        assertEquals(7, reduce(gaps, Meaning.LetterForward).first.focus.cellIndex)
        assertEquals(13, reduce(short, Meaning.PageTowardEnd).first.focus.cellIndex)
        val singleColumn = short.copy(count = 1, screenJumpLastColumn = 0)
        assertEquals(1, reduce(singleColumn, Meaning.LetterForward).first.focus.cellIndex)
    }

}
