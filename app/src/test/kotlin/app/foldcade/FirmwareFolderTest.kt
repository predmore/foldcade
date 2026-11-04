package app.foldcade

import java.net.URLEncoder
import org.junit.Assert.*
import org.junit.Test

class FirmwareFolderTest {
    private fun uri(id: String) = "content://com.android.externalstorage.documents/tree/" + URLEncoder.encode(id, "UTF-8")
    @Test fun confirmedSystemFoldersAndRootsOnEitherVolumeSurviveRestartWithoutChangingSaves() {
        for (id in listOf("primary:RetroArch", "ABCD-1234:RetroArch", "ABCD-1234:RetroArch/system", "primary:My BIOS")) {
            val pick = uri(id)
            val system = pickedFirmwareFolder(pick, { true }) { root -> folderDocumentUri(root, "$id/system") }!!
            val prefs = MemoryPrefs()
            SessionStore(prefs).apply {
                setPlayerSaveFolder("retroarch", "original-saves")
                setPlayerFirmwareFolder("retroarch", system)
            }
            val restarted = SessionStore(prefs)
            assertEquals("original-saves", restarted.playerSaveFolder("retroarch"))
            assertEquals(system, restarted.playerFirmwareFolder("retroarch"))
            assertTrue(restarted.folderTreeInUse(pick))
            assertEquals(system, findFirmwareFolder(restarted.playerFirmwareFolder("retroarch"),
                listOf(SaveFolderGrant(pick, true, true)), { true }) { error("explicit system folder was narrowed again") })
            assertEquals(app.foldcade.host.backup.BackupDisposition.DeviceOnly,
                app.foldcade.host.backup.BackupSettings.classify("player_firmware_folder:retroarch")?.disposition)
        }
    }
    @Test fun secondaryDefaultGrantsAreReusedButARevokedCustomChoiceIsNeverReplaced() {
        val system = uri("ABCD-1234:RetroArch/system")
        val grants = listOf(SaveFolderGrant(system, true, true))
        assertEquals(system, findFirmwareFolder(null, grants, { it == system }) { error("system already exists") })
        assertNull(findFirmwareFolder(uri("primary:Custom BIOS"), grants, { true }) { error("must preserve custom choice") })
        assertNull(findFirmwareFolder(null, listOf(SaveFolderGrant(system, true, false)), { true }) { error("read only") })
    }
}
