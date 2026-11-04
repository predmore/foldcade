package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class FirmwareTest {
    @Test fun missingBiosNamesTheExactFileAndOneDestination() {
        val message = FirmwareCopy.missing(listOf("bios_CD_U.bin"), "RetroArch/system")
        assertTrue(message.contains("bios_CD_U.bin"))
        assertTrue(message.contains("RetroArch/system"))
        assertFalse(message.contains("RomM"))
        assertFalse(message.contains("download"))
        assertEquals("PlayStation BIOS ready", FirmwareCopy.ready("PlayStation"))
    }
    @Test fun grantingBiosAccessHasOnePickAndCancelDoesNotLaunchWithoutIt() {
        val dialog = firmwareFolderDialog("retroarch.sega-cd", FirmwareCopy.folder("RetroArch", "RetroArch/system", listOf("bios_CD_U.bin")), HostScreen.Top)
        assertEquals(DialogKind.FirmwareFolder, dialog.kind)
        assertEquals(listOf(DialogButton.ChooseSaveFolder, DialogButton.NotNow), dialog.buttons)
        assertEquals(DialogButton.NotNow, dialog.buttons[dialog.safeIndex])
        assertEquals("retroarch.sega-cd", dialog.subject)
        assertTrue(dialog.body.contains("bios_CD_U.bin"))
    }
}
