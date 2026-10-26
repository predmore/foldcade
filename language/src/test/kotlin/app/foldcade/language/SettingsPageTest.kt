package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPageTest {
    private val home = PickerModel(
        count = 4,
        rowsPerPage = 1,
        themes = listOf(Copy.builtIn, "Afterglow"),
    )

    private fun openFromMenu(): PickerModel {
        val menu = reduce(home, Meaning.LeftPanel, HostScreen.Top).first
        val rows = panelRows(menu.panel!!, menu)
        assertEquals(Row.Settings, rows.last())
        val onSettings = menu.copy(panel = menu.panel!!.copy(index = rows.lastIndex))
        val (opened, effect) = reduce(onSettings, Meaning.Activate, HostScreen.Top)
        assertNull(effect)
        return opened
    }

    @Test
    fun theMenuRowOpensThePageOnItsScreen() {
        val opened = openFromMenu()
        assertNull(opened.panel)
        assertEquals(HostScreen.Top, opened.settings?.screen)
        assertEquals(0, opened.settings?.category)
        assertFalse(opened.settings!!.onRows)
    }

    @Test
    fun categoriesHideWhenTheyHaveNoRows() {
        assertFalse(SettingsCategory.Players in settingsCategories(home))
        val notInstalled = home.copy(playerSaves = listOf(PlayerSaveSetting("melonds", "melonDS", chosen = false)))
        assertFalse(SettingsCategory.Players in settingsCategories(notInstalled))
        val withSaves = home.copy(playerSaves = listOf(PlayerSaveSetting("melonds", "melonDS", chosen = false, installed = true)))
        assertTrue(SettingsCategory.Players in settingsCategories(withSaves))
    }

    @Test
    fun rightEntersTheRowsAndLeftReturnsToTheCategories() {
        var model = openFromMenu()
        model = reduce(model, Meaning.MoveDown).first
        assertEquals(1, model.settings?.category)
        model = reduce(model, Meaning.MoveRight).first
        assertTrue(model.settings!!.onRows)
        model = reduce(model, Meaning.MoveDown).first
        assertEquals(1, model.settings?.row)
        model = reduce(model, Meaning.MoveLeft).first
        // The track row steps its value instead of leaving the rows.
        assertTrue(model.settings!!.onRows)
        model = reduce(model, Meaning.MoveUp).first
        model = reduce(model, Meaning.MoveLeft).first
        assertFalse(model.settings!!.onRows)
        assertEquals(1, model.settings?.category)
    }

    @Test
    fun aRowActsInPlaceAndThePageStaysOpen() {
        var model = openFromMenu()
        model = reduce(model, Meaning.Activate).first
        val (stepped, effect) = reduce(model, Meaning.MoveRight)
        assertNull(effect)
        assertEquals(1, stepped.themeIndex)
        assertTrue(stepped.settings!!.onRows)
        assertNull(stepped.panel)
    }

    @Test
    fun theSteamGridDbRowAsksForAKey() {
        val (form, open) = reduce(home.onSetting(Row.ArtKey), Meaning.Activate)
        assertEquals(Effect.OpenArtKey, open)
        assertNull(form.settings)
        assertTrue(form.connectOpen)
        assertEquals(ConnectKind.ArtKey, form.connectKind)
        // The key form has no server field: the key, then Save.
        assertEquals(listOf(ConnectField.Token, ConnectField.Save), connectFields(form.connectKind))
        val onSave = form.copy(connectIndex = 1)
        assertEquals(Effect.SaveArtKey, reduce(onSave, Meaning.Activate).second)

    }

    @Test
    fun aSavedKeyIsOnlyForgottenByTheDialogsForgetKey() {
        val saved = home.copy(artKeySaved = true).onSetting(Row.ArtKey)
        val (asked, effect) = reduce(saved, Meaning.Activate)
        // A only asks. The page stays open under the dialog, which opens on Keep key.
        assertNull(effect)
        val dialog = asked.dialog!!
        assertEquals(DialogKind.ForgetArtKey, dialog.kind)
        assertEquals(listOf(DialogButton.ForgetKey, DialogButton.KeepKey), dialog.buttons)
        assertEquals(DialogButton.KeepKey, dialog.buttons[dialog.index])
        assertEquals(DialogButton.KeepKey, dialog.buttons[dialog.safeIndex])
        assertEquals(HostScreen.Top, dialog.screen)
        assertEquals(saved.settings, asked.settings)
        assertFalse(asked.connectOpen)

        val keep = Effect.DialogChoice(DialogButton.KeepKey, DialogKind.ForgetArtKey)
        val forget = Effect.DialogChoice(DialogButton.ForgetKey, DialogKind.ForgetArtKey)
        // A second press, as from a bounce or a held A, lands on Keep key.
        val (again, second) = reduce(asked, Meaning.Activate, repeatCount = 1)
        assertEquals(keep, second)
        assertNull(again.dialog)
        assertEquals(saved.settings, again.settings)
        // Back keeps the key and leaves the page where it was.
        val (backed, back) = reduce(asked, Meaning.Back)
        assertEquals(keep, back)
        assertNull(backed.dialog)
        assertEquals(saved.settings, backed.settings)
        assertTrue(backed.artKeySaved)
        // The drawers and Start do not leave the dialog, and choose nothing.
        for (away in listOf(Meaning.LeftPanel, Meaning.RightPanel, Meaning.Menu, Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveRight)) {
            val (stays, none) = reduce(asked, away)
            assertNull(none)
            assertEquals(DialogButton.KeepKey, stays.dialog!!.buttons[stays.dialog!!.index])
        }
        // Forget key takes a move onto it and a press, or a tap on it.
        val onForget = reduce(asked, Meaning.MoveLeft).first
        assertNull(reduce(asked, Meaning.MoveLeft).second)
        assertEquals(forget, reduce(onForget, Meaning.Activate).second)
        assertEquals(keep, reduce(onForget, Meaning.Back).second)
        assertEquals(forget, focusAndActivateDialog(asked, 0).second)
        assertEquals(keep, focusAndActivateDialog(asked, 1).second)
    }

    @Test
    fun theRommFormStaysRommAfterTheKeyForm() {
        val leftOver = home.copy(connectKind = ConnectKind.ArtKey)
        val (connect, _) = reduce(leftOver.onSetting(Row.AddRomm), Meaning.Activate)
        assertEquals(ConnectKind.Romm, connect.connectKind)
        val onSave = connect.copy(connectIndex = 2)
        assertEquals(Effect.SaveRommToken, reduce(onSave, Meaning.Activate).second)
    }

    @Test
    fun aOnATextRowRaisesTheKeyboardThere() {
        val (romm, _) = reduce(home.onSetting(Row.AddRomm), Meaning.Activate)
        for (field in listOf(ConnectField.Origin, ConnectField.Token)) {
            val on = romm.copy(connectIndex = connectFields(romm.connectKind).indexOf(field))
            val (typing, effect) = reduce(on, Meaning.Activate)
            assertEquals(Effect.EditField, effect)
            assertEquals(on.connectIndex, typing.connectIndex)
            assertTrue(typing.connectOpen)
        }
        val (key, _) = reduce(home.onSetting(Row.ArtKey), Meaning.Activate)
        assertEquals(Effect.EditField, reduce(key.copy(connectIndex = 0), Meaning.Activate).second)
    }

    @Test
    fun aRowThatLeavesTheMenuClosesThePage() {
        var model = openFromMenu()
        val library = settingsCategories(model).indexOf(SettingsCategory.Library)
        repeat(library) { model = reduce(model, Meaning.MoveDown).first }
        model = reduce(model, Meaning.MoveRight).first
        // With no library yet, the first tile is Add, and its page offers a RomM server.
        assertEquals(Row.SettingsTile(SettingsInner.Add), settingsFocus(model))
        model = reduce(model, Meaning.Activate).first
        assertEquals(SettingsInner.Add, model.settings!!.inner)
        model = reduce(model, Meaning.MoveDown).first
        assertEquals(Row.AddRomm, settingsFocus(model))
        val (connect, effect) = reduce(model, Meaning.Activate)
        assertEquals(Effect.OpenAddRomm, effect)
        assertNull(connect.settings)
        assertTrue(connect.connectOpen)
    }

    @Test
    fun syncGamesWaitsForALibraryThenStaysOpenWhileItRuns() {
        assertFalse(Row.SyncLibrary in settingsRows(SettingsCategory.Library, home))
        val withFolder = home.copy(folders = listOf(LibraryFolder("content://roms", "roms")))
        assertEquals(Row.SyncLibrary, settingsRows(SettingsCategory.Library, withFolder)[librarySources(withFolder).size])
        val (syncing, effect) = reduce(withFolder.onSetting(Row.SyncLibrary), Meaning.Activate)
        assertEquals(Effect.SyncLibrary, effect)
        assertTrue(syncing.settings!!.onRows)
        assertEquals(RowControl.None, rowControl(Row.SyncLibrary, syncing))
        assertEquals(RowControl.Value(Copy.syncing), rowControl(Row.SyncLibrary, syncing.copy(librarySyncing = true)))
    }

    @Test
    fun backStepsOutThenCloses() {
        var model = openFromMenu()
        model = reduce(model, Meaning.MoveRight).first
        model = reduce(model, Meaning.Back).first
        assertFalse(model.settings!!.onRows)
        model = reduce(model, Meaning.Back).first
        assertNull(model.settings)
        assertNull(model.panel)
    }

    @Test
    fun shouldersCloseThePage() {
        val opened = openFromMenu()
        assertNull(reduce(opened, Meaning.LeftPanel).first.settings)
        assertNull(reduce(opened, Meaning.RightPanel).first.settings)
    }
}
