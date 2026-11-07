package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class FirmwareTest {
    @Test fun missingRetroArchBiosOffersItsCustomFolderAndNotNowStillCancels() {
        val body = FirmwareCopy.missing("Sega CD", listOf("bios_CD_U.bin"), "RetroArch/system")
        val dialog = missingFirmwareDialog(body, HostScreen.Top, "retroarch.sega-cd")
        assertEquals(DialogKind.FirmwareFolder, dialog.kind)
        assertEquals("retroarch.sega-cd", dialog.subject)
        assertEquals(body, dialog.body)
        assertEquals(listOf(DialogButton.ChooseSaveFolder, DialogButton.NotNow), dialog.buttons)
        assertEquals(DialogButton.NotNow, dialog.buttons[dialog.safeIndex])
        assertEquals(DialogKind.Ok, missingFirmwareDialog(body, HostScreen.Top).kind)
    }
    @Test fun missingBiosNamesTheSystemAndOnlyOffersAnExistingRommConnection() {
        val local = FirmwareCopy.missing("Sega CD", listOf("bios_CD_U.bin"), "RetroArch/system")
        assertEquals("Missing the Sega CD BIOS (bios_CD_U.bin). Put it in RetroArch/\u2060system. Then start the game again.", local)
        assertFalse(local.contains("RomM"))
        val connected = FirmwareCopy.missing("Sega CD", listOf("bios_CD_U.bin"), "RetroArch/system", rommConnected = true)
        assertEquals("Missing the Sega CD BIOS (bios_CD_U.bin). Add it to RomM’s firmware for Sega CD, or put it in RetroArch/\u2060system. Then start the game again.", connected)
        assertTrue(connected.indexOf("Sega CD") < connected.indexOf("bios_CD_U.bin"))
        // RomM, the way that places the file by itself, comes before the folder.
        assertTrue(connected.indexOf("RomM") < connected.indexOf("RetroArch/\u2060system"))
        assertTrue(connected.length < 140)
        assertTrue(FirmwareCopy.missing("PlayStation", listOf("PlayStation BIOS (.bin)"), "DuckStation/bios").startsWith("Missing the PlayStation BIOS (.bin)."))
    }

    @Test fun missingBiosNamesTheExactFileAndOneDestination() {
        val message = FirmwareCopy.missing("Sega CD", listOf("bios_CD_U.bin"), "RetroArch/system")
        assertTrue(message.contains("bios_CD_U.bin"))
        assertTrue(message.contains("RetroArch/\u2060system"))
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
