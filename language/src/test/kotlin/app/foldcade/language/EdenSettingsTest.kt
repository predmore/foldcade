package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EdenSettingsTest {
    @Test
    fun edenGetsATileForItsKeysWithBothValues() {
        val bare = PickerModel(count = 1, rowsPerPage = 1, homeRoleHeld = true)
        assertFalse(Row.EdenFolder in settingsRows(SettingsCategory.Players, bare))
        assertFalse(SettingsCategory.Players in settingsCategories(bare))

        val installed = bare.copy(eden = EdenSetting(fromRomm = false))
        assertFalse(Row.EdenFolder in settingsRows(SettingsCategory.Players, installed))
        assertFalse(SettingsCategory.Players in settingsCategories(installed))
        val rommOnly = bare.copy(signedIn = listOf(SignedInBackend("romm", "RomM")))
        assertFalse(Row.EdenFolder in settingsRows(SettingsCategory.Players, rommOnly))
        val otherLibrary = installed.copy(signedIn = listOf(SignedInBackend("steam", "Steam")))
        assertFalse(Row.EdenFolder in settingsRows(SettingsCategory.Players, otherLibrary))

        val setup = installed.copy(signedIn = listOf(SignedInBackend("romm", "RomM")), boardEmulators = setOf(EDEN_SHELF_KEY))
        val eden = Row.SettingsTile(SettingsInner.Emulator(EDEN_SHELF_KEY))
        assertEquals(listOf(eden, Row.SaveSetup, Row.FindSaves), settingsRows(SettingsCategory.Players, setup))
        assertEquals(listOf(Row.EdenFolder), settingsInnerRows(SettingsInner.Emulator(EDEN_SHELF_KEY), setup))
        assertEquals(Copy.eden, rowText(eden, setup).label)
        assertEquals(TileStatus.Attention, settingsTileStatus(eden, setup))
        assertEquals(Copy.edenKeys, rowText(Row.EdenFolder, setup).label)
        assertEquals(Copy.notSyncing, rowText(Row.EdenFolder, setup).value)
        assertEquals(RowControl.Value(Copy.notSyncing, attention = true, opens = true), rowControl(Row.EdenFolder, setup))

        val granted = setup.copy(eden = EdenSetting(fromRomm = true))
        assertEquals(Copy.edenFromRomm, rowText(Row.EdenFolder, granted).value)
        assertEquals(TileStatus.Fine, settingsTileStatus(eden, granted))

        val both = granted.copy(retroArch = RetroArchSetting(cores = 3))
        assertEquals(
            listOf(eden, Row.SettingsTile(SettingsInner.Emulator(RETROARCH_SHELF_KEY)), Row.SaveSetup, Row.FindSaves),
            settingsRows(SettingsCategory.Players, both),
        )
        val page = granted.onSetting(Row.EdenFolder)
        val (opened, effect) = reduce(page, Meaning.Activate)
        assertEquals(Effect.ShowEdenFolder, effect)
        assertEquals(page.settings, opened.settings)
    }

    @Test
    fun anUnresolvedEdenSaveOffersSetupPlayWithoutSyncingOrCancel() {
        for (needsUser in listOf(false, true)) {
            val dialog = saveNotPlacedDialog("Fantasy Life", HostScreen.Bottom, playAnyway = true, needsEdenUser = needsUser)
            assertEquals(DialogKind.EdenSaveHold, dialog.kind)
            assertEquals("${Copy.saveNotPlaced} Fantasy Life", dialog.title)
            assertEquals(if (needsUser) Copy.edenSaveNeedsUser else Copy.saveUnresolved, dialog.body)
            assertEquals(listOf(DialogButton.SetUpSaves, DialogButton.PlayWithoutSyncing, DialogButton.NotNow), dialog.buttons)
            assertEquals(DialogButton.SetUpSaves, dialog.buttons[dialog.index])
            assertEquals(DialogButton.NotNow, dialog.buttons[dialog.safeIndex])
            val model = PickerModel(count = 1, rowsPerPage = 1, dialog = dialog)
            assertEquals(Effect.DialogChoice(DialogButton.SetUpSaves, DialogKind.EdenSaveHold), reduce(model, Meaning.Activate).second)
            val play = reduce(model, Meaning.MoveRight).first
            assertEquals(Effect.DialogChoice(DialogButton.PlayWithoutSyncing, DialogKind.EdenSaveHold), reduce(play, Meaning.Activate).second)
            val cancel = reduce(play, Meaning.MoveRight).first
            assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.EdenSaveHold), reduce(cancel, Meaning.Activate).second)
            val (after, effect) = reduce(model, Meaning.Back)
            assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.EdenSaveHold), effect)
            assertNull(after.dialog)
        }
    }

    @Test
    fun otherPlayersKeepTheExistingSaveNotPlacedPrompt() {
        val dialog = saveNotPlacedDialog("Tetris", HostScreen.Top)
        assertEquals(DialogKind.Ok, dialog.kind)
        assertEquals(Copy.saveNotPlacedBody, dialog.body)
        assertEquals(listOf(DialogButton.Ok), dialog.buttons)
        assertEquals(0, dialog.safeIndex)
    }

    @Test
    fun theRestartPromptOpensOnContinueAndNotNowIsSafe() {
        val dialog = checkNotNull(previewDialogState("eden-restart", 0, HostScreen.Bottom))
        assertEquals(DialogKind.EdenRestart, dialog.kind)
        assertEquals(Copy.edenRestartTitle, dialog.title)
        assertEquals(Copy.edenRestartBody, dialog.body)
        assertEquals("Restart Eden once", dialog.title)
        // A lead line, then the three steps a dialog shows as a list.
        assertEquals(
            DialogBody("Eden reads new Switch files only when it starts.", listOf("Tap Force stop, then OK.", "Press B to come back.", "Start the game again.")),
            dialogBodyParts(dialog.body),
        )
        assertEquals(listOf(DialogButton.ContinueGrant, DialogButton.NotNow), dialog.buttons)
        assertEquals(DialogButton.ContinueGrant, dialog.buttons.first())
        assertEquals(0, dialog.index)
        assertEquals(DialogButton.NotNow, dialog.buttons[dialog.safeIndex])
        assertEquals(1, dialog.safeIndex)
        val (after, effect) = reduce(
            PickerModel(count = 1, rowsPerPage = 1, dialog = dialog),
            Meaning.Back,
        )
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.EdenRestart), effect)
        assertNull(after.dialog)
    }

    @Test
    fun theWrongFolderOffersTryAgainAndNotNowIsSafe() {
        val dialog = checkNotNull(previewDialogState("eden-wrong-folder", 0, HostScreen.Bottom))
        assertEquals(DialogKind.EdenWrongFolder, dialog.kind)
        assertEquals(Copy.edenWrongFolderTitle, dialog.title)
        assertEquals("That isn’t Eden’s folder", dialog.title)
        assertEquals(Copy.edenWrongFolderBody, dialog.body)
        assertEquals("Tap Use this folder as soon as the picker opens.", dialog.body)
        // The capture holds a launch: its way on is Play without syncing, and Back cancels.
        assertEquals(listOf(DialogButton.TryAgain, DialogButton.PlayWithoutSyncing), dialog.buttons)
        assertEquals(DialogButton.TryAgain, dialog.buttons.first())
        assertEquals(0, dialog.index)
        assertEquals("Try again", dialogButtonLabel(dialog.buttons[dialog.index]))
        assertEquals(Copy.tryAgain, dialogButtonLabel(DialogButton.TryAgain))
        assertEquals(BACK_CLOSES, dialog.safeIndex)
        val (after, effect) = reduce(
            PickerModel(count = 1, rowsPerPage = 1, dialog = dialog),
            Meaning.Back,
        )
        assertEquals(Effect.DialogChoice(DialogButton.Dismiss, DialogKind.EdenWrongFolder), effect)
        assertNull(after.dialog)
        // From Settings, nothing waits: Not now only closes, and Back presses it.
        val settings = edenWrongFolderDialog(HostScreen.Bottom)
        assertEquals(listOf(DialogButton.TryAgain, DialogButton.NotNow), settings.buttons)
        assertEquals(DialogButton.NotNow, settings.buttons[settings.safeIndex])
        // A launch that cannot start without the folder has no Play without syncing.
        assertEquals(listOf(DialogButton.TryAgain, DialogButton.NotNow), edenWrongFolderDialog(HostScreen.Bottom, launchHeld = true, needed = true).buttons)
    }
}
