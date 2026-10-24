package app.foldcade

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPickerTest {
    @Test
    fun aRecreatedPickerKeepsTheReasonItWasOpened() {
        val save = FolderPickerState(FolderPurpose.LaunchOffer, pendingLaunch = 4)
        val cores = FolderPickerState(FolderPurpose.RetroArchCores, pendingLaunch = 2)
        val eden = FolderPickerState(FolderPurpose.EdenFolder, pendingLaunch = 6)
        val settings = FolderPickerState(FolderPurpose.Settings, settingsPlayerId = "azahar")
        assertEquals(save, decodeFolderPickerState(save.encode()))
        assertEquals(cores, decodeFolderPickerState(cores.encode()))
        assertEquals(eden, decodeFolderPickerState(eden.encode()))
        assertEquals(settings, decodeFolderPickerState(settings.encode()))
        assertEquals(FolderPickerState(), decodeFolderPickerState(null))
        assertEquals(FolderPickerState(), decodeFolderPickerState("not-a-purpose\n\n"))
    }

    @Test
    fun aLostPurposeWouldScanTheChosenFolderAsALibrary() {
        val fresh = FolderPickerState()
        assertEquals(FolderPickResult.UseAsLibrary, folderPickResult(fresh, choseFolder = true))
        val save = FolderPickerState(FolderPurpose.LaunchOffer, pendingLaunch = 4)
        assertEquals(FolderPickResult.RememberSaveAndLaunch, folderPickResult(save, choseFolder = true))
        assertEquals(FolderPickResult.SkipSaveAndLaunch, folderPickResult(save, choseFolder = false))
        val cores = FolderPickerState(FolderPurpose.RetroArchCores)
        assertEquals(FolderPickResult.ImportCores, folderPickResult(cores, choseFolder = true))
        assertEquals(FolderPickResult.SettleCores, folderPickResult(cores, choseFolder = false))
        val eden = FolderPickerState(FolderPurpose.EdenFolder)
        assertEquals(FolderPickResult.UseEdenFolder, folderPickResult(eden, choseFolder = true))
        assertEquals(FolderPickResult.SettleEden, folderPickResult(eden, choseFolder = false))
        val settings = FolderPickerState(FolderPurpose.Settings, settingsPlayerId = "azahar")
        assertEquals(FolderPickResult.RememberSaveForPlayer, folderPickResult(settings, choseFolder = true))
        assertEquals(FolderPickResult.ClearSettingsPlayer, folderPickResult(settings, choseFolder = false))
        assertEquals(FolderPickResult.Ignore, folderPickResult(fresh, choseFolder = false))
    }
}
