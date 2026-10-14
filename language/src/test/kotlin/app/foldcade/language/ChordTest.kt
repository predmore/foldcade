package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTest {
    private val tile = TileActions(move = true, collections = emptyList(), rename = true, remove = RemoveVerb.Unpin)
    private val home = PickerModel(count = 8, rowsPerPage = 2, tileActions = tile, themes = listOf(Copy.builtIn, "Afterglow"))

    @Test
    fun hiddenShowUsesYAndUndoLeavesMoveAndRemoveChordsAlone() {
        val hidden = actionRows(TileActions(show = true, undo = true), null)
        assertEquals(listOf(Row.Undo, Row.ShowHidden), hidden)
        assertEquals(listOf(null, Meaning.Options), chords(hidden))
        for (verb in RemoveVerb.entries) {
            val rows = actionRows(TileActions(move = true, undo = true, remove = verb, deleteShortcut = true), null)
            assertEquals(rows.indexOf(Row.MoveTile), chordIndex(rows, Meaning.Options))
            assertEquals(rows.indexOf(Row.RemoveTile), chordIndex(rows, Meaning.ActivateBottom))
            assertNull(chords(rows)[rows.indexOf(Row.DeleteShortcut)])
        }
        assertNull(chordIndex(listOf(Row.MoveApp), Meaning.Options))
    }

    @Test
    fun startAndSelectAreMeaningsOncePerPress() {
        assertEquals(Meaning.Menu, meaningOf(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(Meaning.View, meaningOf(KeyEvent.KEYCODE_BUTTON_SELECT))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_START, repeatCount = 1))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_SELECT, homeKeys = HomeKeys.Typing))
    }

    @Test
    fun lOneThenStartOpensSettingsAndStartClosesIt() {
        val opened = reduce(reduce(home, Meaning.LeftPanel).first, Meaning.Menu, HostScreen.Top).first
        assertNull(opened.panel)
        assertEquals(HostScreen.Top, opened.settings?.screen)
        val closed = reduce(opened, Meaning.Menu).first
        assertNull(closed.settings)
        assertEquals(home.focus, closed.focus)
    }

    @Test
    fun lOneThenYIsEditHomeAndSelectIsPlayActivity() {
        val drawer = reduce(home, Meaning.LeftPanel).first
        assertEquals(PanelLevel.EditHome, reduce(drawer, Meaning.Options).first.panel?.level)
        val page = reduce(drawer, Meaning.View).first
        assertTrue(page.playActivity != null)
        assertNull(reduce(page, Meaning.View).first.playActivity)
    }

    @Test
    fun yThenYMovesAndYThenXRemoves() {
        val menu = reduce(home, Meaning.Options).first
        assertEquals(PanelLevel.Actions, menu.panel?.level)
        val (moving, move) = reduce(menu, Meaning.Options)
        assertEquals(Effect.MoveTile, move)
        assertNull(moving.panel)
        assertEquals(Effect.RemoveTile, reduce(menu, Meaning.ActivateBottom).second)
        // A chord runs its row wherever the focus is.
        val lower = menu.copy(panel = menu.panel!!.copy(index = 2))
        assertEquals(Effect.MoveTile, reduce(lower, Meaning.Options).second)
    }

    @Test
    fun rOneThenStartOpensAndroidSettingsAndLeavesNoticesTheirKeys() {
        val notice = FoldNotice(id = "n", packageName = "app", title = "Hi", body = "", postedMillis = 0L, openable = true, dismissable = true)
        val drawer = reduce(home.copy(notices = listOf(notice)), Meaning.RightPanel).first
        assertEquals(Effect.OpenAndroidSetting(AndroidSetting.Settings), reduce(drawer, Meaning.Menu).second)
        val onNotice = drawer.copy(panel = drawer.panel!!.copy(index = panelRows(drawer.panel!!, drawer).indexOf(Row.Notice("n"))))
        assertEquals(Effect.DismissNotice("n"), reduce(onNotice, Meaning.Options).second)
        assertEquals(Effect.OpenNotice("n", onBottom = true), reduce(onNotice, Meaning.ActivateBottom).second)
    }

    @Test
    fun aChordPressedWhileTheOtherIslandFoldsRunsInTheOneOpening() {
        val right = reduce(home, Meaning.RightPanel).first
        val switching = reduce(right, Meaning.LeftPanel).first
        assertTrue(switching.panel!!.retiring)
        val opened = reduce(switching, Meaning.Menu, HostScreen.Top).first
        assertTrue(opened.settings != null)
        assertNull(opened.panel)
    }

    @Test
    fun aChordTheOpeningIslandLacksDoesNothingToTheOneLeaving() {
        val left = reduce(home, Meaning.LeftPanel).first
        val switching = reduce(left, Meaning.RightPanel).first
        assertTrue(switching.panel!!.retiring)
        // R1 has no Select or Y row, so L1's Play activity and Edit home stay untouched.
        for (meaning in listOf(Meaning.View, Meaning.Options)) {
            val (after, effect) = reduce(switching, meaning)
            assertEquals(switching, after)
            assertNull(effect)
        }
    }

    @Test
    fun eachRowShowsOnlyItsOwnKeyAndAKeyBelongsToOneRow() {
        val drawer = reduce(home, Meaning.LeftPanel).first.panel!!
        val rows = panelRows(drawer, home)
        assertEquals(PromptKey.Start, rowKeys(rows)[rows.indexOf(Row.Settings)])
        assertNull(rowKeys(rows)[rows.indexOf(Row.Home)])
        val apps = AppActions(favorite = false, onGamesShelf = true, hiddenShelf = false, destinations = emptyList())
        val both = actionRows(tile, apps)
        val keys = chords(both)
        assertEquals(Meaning.Options, keys[both.indexOf(Row.MoveTile)])
        assertNull(keys[both.indexOf(Row.MoveApp)])
        assertEquals(PromptKey.FaceX, rowKeys(both)[both.indexOf(Row.RemoveTile)])
        // X stays Remove from Home beside Delete download, and is Delete download where it is alone.
        val downloaded = actionRows(tile.copy(removeDownload = true), null)
        assertNull(chords(downloaded)[downloaded.indexOf(Row.RemoveDownload)])
        assertEquals(listOf(Meaning.ActivateBottom), chords(actionRows(TileActions(removeDownload = true), null)))
    }

    @Test
    fun leftAndRightStepAChoiceRowBothWays() {
        val onTheme = home.onSetting(Row.Theme)
        assertEquals(1, reduce(onTheme, Meaning.MoveLeft).first.themeIndex)
        assertEquals(1, reduce(onTheme, Meaning.MoveRight).first.themeIndex)
        val onMotion = home.onSetting(Row.MotionSpeed)
        assertEquals(MotionSpeed.Off, reduce(onMotion, Meaning.MoveLeft).first.motionSpeed)
        assertEquals(MotionSpeed.Slower, reduce(onMotion, Meaning.MoveRight).first.motionSpeed)
        val onIcons = home.onSetting(Row.IconTheme)
        assertEquals(1, reduce(onIcons, Meaning.MoveLeft).first.iconThemeIndex)
        assertEquals(0, reduce(onIcons, Meaning.MoveRight).first.iconThemeIndex)
        // A background that is not among the choices steps to the last one on Left, the first on Right.
        val choices = home.backgroundChoices
        val unlisted = home.copy(backgroundMotion = BackgroundMotion.Embers, backgroundChoices = choices.filter { it.motion != BackgroundMotion.Embers })
            .onSetting(Row.Background)
        assertEquals(unlisted.backgroundChoices.last().motion, reduce(unlisted, Meaning.MoveLeft).first.backgroundMotion)
        assertEquals(unlisted.backgroundChoices.first().motion, reduce(unlisted, Meaning.MoveRight).first.backgroundMotion)
        // The rows stay focused while their value changes.
        assertTrue(reduce(onMotion, Meaning.MoveLeft).first.settings!!.onRows)
        // A switch has no direction, so Left still steps back to the categories.
        assertFalse(reduce(home.onSetting(Row.Music), Meaning.MoveLeft).first.settings!!.onRows)
    }

    @Test
    fun rowsSayHowTheyShowTheirValue() {
        assertEquals(RowControl.Toggle(true), rowControl(Row.Music, home))
        assertEquals(RowControl.Choice(Copy.builtIn), rowControl(Row.Theme, home))
        assertEquals(RowControl.Opens, rowControl(Row.Licenses, home))
        assertEquals(RowControl.None, rowControl(Row.MusicVolume, home))
        assertEquals(RowControl.None, rowControl(Row.Home, home))
    }
}
