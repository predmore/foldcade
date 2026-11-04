package app.foldcade

import app.foldcade.language.FoldNotice
import org.junit.Assert.*
import org.junit.Test

class FirmwareNoticesTest {
    @Test fun cartridgeCleanupDoesNotHideDiscSetupButExplicitXDoes() {
        val session = FirmwareNoticeDismissals()
        val disc = FoldNotice("firmware-folder:retroarch", "BIOS folder", "Choose RetroArch", firmwarePlayerId = "retroarch.playstation")
        assertTrue(session.allows(disc))
        session.dismiss(disc, byUser = false) // Quiet cartridge launch automatically clears the shared notice.
        assertTrue(session.allows(disc.copy(postedMillis = 100))) // Disc launch still offers setup again.
        session.dismiss(disc, byUser = true) // X or Clear all.
        assertFalse(session.allows(disc.copy(postedMillis = 200)))
        assertTrue(session.allows(disc.copy(id = "firmware-folder:duckstation", firmwarePlayerId = "duckstation")))
        assertTrue(session.allows(disc.copy(firmwarePlayerId = null))) // Other notice policies stay unchanged.
    }
}
