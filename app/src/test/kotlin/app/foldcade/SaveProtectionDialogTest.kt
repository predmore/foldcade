package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class SaveProtectionDialogTest {
    private val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
    private val incident = SaveIncident("WrongFolder:azahar", IncidentKind.WrongFolder, "azahar", "Azahar", emptyList(),
        FolderRef("primary:Azahar", "Azahar"), FolderRef("primary:AzaharQA", "AzaharQA"), Evidence(EvidenceKind.FreshLog, "log"), 1)

    @Test fun aReceiptUpdatesTheOpenStopWithoutChangingItsFocusOrLaunchHold() {
        val request = shell.showSaveProtection(incident, holdsLaunch = false)!!
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        val focused = shell.model.dialog!!
        assertFalse(focused.body.contains("safe"))
        shell.updateSaveProtection(request, incident, 10, true)
        assertEquals(focused.index, shell.model.dialog!!.index)
        assertEquals(focused.holdsLaunch, shell.model.dialog!!.holdsLaunch)
        assertEquals(SaveProtectionCopy.body(incident, 10, true), shell.model.dialog!!.body)
    }

    @Test fun aLateReceiptCannotReopenOrUpdateALaterStopForTheSameIncident() {
        val first = shell.showSaveProtection(incident)!!
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.updateSaveProtection(first, incident, 10, true)
        assertNull(shell.model.dialog)
        val later = shell.showSaveProtection(incident)!!
        shell.updateSaveProtection(first, incident, 10, true)
        assertSame(later, shell.model.dialog)
        assertFalse(shell.model.dialog!!.body.contains("safe"))
    }
}
