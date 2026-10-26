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

        val setup = installed.copy(signedIn = listOf(SignedInBackend("romm", "RomM")))
        val eden = Row.SettingsTile(SettingsInner.Emulator(EDEN_SHELF_KEY))
        assertEquals(listOf(eden), settingsRows(SettingsCategory.Players, setup))
        assertEquals(listOf(Row.EdenFolder), settingsInnerRows(SettingsInner.Emulator(EDEN_SHELF_KEY), setup))
        assertEquals(Copy.eden, rowText(eden, setup).label)
        assertEquals(TileStatus.Attention, settingsTileStatus(eden, setup))
        assertEquals(Copy.edenKeys, rowText(Row.EdenFolder, setup).label)
        assertEquals(Copy.setUp, rowText(Row.EdenFolder, setup).value)

        val granted = setup.copy(eden = EdenSetting(fromRomm = true))
        assertEquals(Copy.edenFromRomm, rowText(Row.EdenFolder, granted).value)
        assertEquals(TileStatus.Fine, settingsTileStatus(eden, granted))

        val both = granted.copy(retroArch = RetroArchSetting(cores = 3))
        assertEquals(
            listOf(eden, Row.SettingsTile(SettingsInner.Emulator(RETROARCH_SHELF_KEY))),
            settingsRows(SettingsCategory.Players, both),
        )
        val (closed, effect) = reduce(granted.onSetting(Row.EdenFolder), Meaning.Activate)
        assertEquals(Effect.ShowEdenFolder, effect)
        assertNull(closed.settings)
    }

    @Test
    fun theOfferOpensOnContinueAndNotNowIsSafe() {
        val dialog = checkNotNull(previewDialogState("eden-folder", 0, HostScreen.Bottom))
        assertEquals(DialogKind.EdenFolder, dialog.kind)
        assertEquals(Copy.edenFolderTitle, dialog.title)
        assertEquals(Copy.edenFolderBody, dialog.body)
        assertEquals(DialogButton.ContinueGrant, dialog.buttons.first())
        assertEquals(DialogButton.NotNow, dialog.buttons[dialog.safeIndex])
        val (after, effect) = reduce(
            PickerModel(count = 1, rowsPerPage = 1, dialog = dialog),
            Meaning.Back,
        )
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.EdenFolder), effect)
        assertNull(after.dialog)
    }

    @Test
    fun theRestartPromptOpensOnContinueAndNotNowIsSafe() {
        val dialog = checkNotNull(previewDialogState("eden-restart", 0, HostScreen.Bottom))
        assertEquals(DialogKind.EdenRestart, dialog.kind)
        assertEquals(Copy.edenRestartTitle, dialog.title)
        assertEquals(Copy.edenRestartBody, dialog.body)
        assertEquals("Restart Eden once", dialog.title)
        assertEquals(
            "Foldcade added Switch files to Eden. Eden reads them only when it starts. On the next screen, choose Force stop, then OK. Press B to come back and start the game again.",
            dialog.body,
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
        assertEquals(
            "Choose Use this folder where the picker opens, without opening a folder inside it.",
            dialog.body,
        )
        assertEquals(listOf(DialogButton.TryAgain, DialogButton.NotNow), dialog.buttons)
        assertEquals(DialogButton.TryAgain, dialog.buttons.first())
        assertEquals(0, dialog.index)
        assertEquals("Try again", dialogButtonLabel(dialog.buttons[dialog.index]))
        assertEquals(Copy.tryAgain, dialogButtonLabel(DialogButton.TryAgain))
        assertEquals(DialogButton.NotNow, dialog.buttons[dialog.safeIndex])
        assertEquals(1, dialog.safeIndex)
        val (after, effect) = reduce(
            PickerModel(count = 1, rowsPerPage = 1, dialog = dialog),
            Meaning.Back,
        )
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.EdenWrongFolder), effect)
        assertNull(after.dialog)
    }
}
