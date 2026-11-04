package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class ThorSetupTest {
    @Test fun `restore asks explicitly and defaults to leaving settings alone`() {
        val dialog = thorSetupDialog("azahar", ThorSetupSetting(ThorSetupStatus.Applied,
            setOf(ThorSetupOption.OtherScreen), canRestore = true), HostScreen.Top)
        assertEquals(DialogButton.NotNow, dialog.buttons[dialog.index])
        assertTrue(dialog.body.contains(ThorSetupCopy.option(ThorSetupOption.OtherScreen)))
        assertFalse(dialog.body.contains(ThorSetupCopy.option(ThorSetupOption.Resolution)))
        val confirm = restoreThorSetupDialog("azahar", HostScreen.Top)
        assertEquals(DialogButton.NotNow, confirm.buttons[confirm.index])
        assertTrue(confirm.body.contains("whole original config"))
        assertEquals("azahar", confirm.subject)
    }

    @Test fun `setup notice opens its emulator row and held A does not activate it`() {
        val notice = FoldNotice(ThorSetupCopy.noticePrefix + "azahar", ThorSetupCopy.needsNotice,
            ThorSetupCopy.closeBody, savePlayerId = "azahar")
        val base = PickerModel(count = 2, rowsPerPage = 2).copy(notices = listOf(notice), playerSaves = listOf(
            PlayerSaveSetting("azahar", "Azahar", true, installed = true, thorSetup = ThorSetupSetting(ThorSetupStatus.CloseEmulator))))
        val right = reduce(base, Meaning.RightPanel).first
        val focused = right.copy(panel = right.panel!!.copy(index = panelRows(right.panel!!, right).indexOf(Row.Notice(notice.id))))
        assertTrue(panelHints(focused)!!.any { it.label == NoticeCopy.open })
        assertFalse(panelHints(focused)!!.any { it.label == Copy.setUp })
        val (opened, effect) = reduce(focused, Meaning.Activate)
        assertNull(effect)
        assertNull(opened.saveSetup)
        assertEquals(SettingsInner.Emulator("azahar"), opened.settings!!.inner)
        assertEquals(Row.ThorSetup("azahar"), settingsInnerRows(opened.settings!!.inner!!, opened)[opened.settings!!.row])
        assertNull(reduce(opened, Meaning.Activate, repeatCount = 1).second)
        assertEquals(Effect.ShowThorSetup("azahar"), reduce(opened, Meaning.Activate).second)
    }

    @Test fun `settings status does not swallow the first launch notice and restore has its own instructions`() {
        val cached = ThorSetupSetting(ThorSetupStatus.CloseEmulator)
        val notices = ThorSetupNotices()
        assertNotNull(notices.update("azahar", cached.status, 1L))
        assertNull(notices.update("azahar", cached.status, 2L))
        assertNotNull(notices.update("azahar", ThorSetupStatus.Applied, 3L))
        val restore = thorSetupDialog("azahar", cached.copy(canRestore = true), HostScreen.Top)
        assertEquals(ThorSetupCopy.closeRestoreBody, restore.body)
        assertFalse(restore.body.contains("next game"))
    }

    @Test fun `file access and stopped emulator have clear next steps`() {
        assertEquals(DialogButton.ThorFileAccess, thorSetupDialog("azahar", ThorSetupSetting(ThorSetupStatus.FileAccess), HostScreen.Top).buttons.first())
        assertEquals(DialogButton.ThorAppInfo, thorSetupDialog("azahar", ThorSetupSetting(ThorSetupStatus.CloseEmulator), HostScreen.Top).buttons.first())
    }
}
