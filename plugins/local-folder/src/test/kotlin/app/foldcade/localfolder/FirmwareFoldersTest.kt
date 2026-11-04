package app.foldcade.localfolder

import org.junit.Assert.*
import org.junit.Test

class FirmwareFoldersTest {
    @Test fun findsBiosInsideAndBesideASystemOrLibraryIncludingFrontendRoots() {
        assertTrue(localBiosFolderPaths("ROMs/ps1").containsAll(listOf("ROMs/ps1/bios", "ROMs/bios", "bios")))
        assertTrue(localBiosFolderPaths("ES-DE/ROMs/ps1").containsAll(listOf("ES-DE/bios", "ES-DE/BIOS")))
        assertTrue(localBiosFolderPaths("share/roms/psx").contains("share/bios"))
        assertTrue(localBiosFolderPaths("bios").contains("bios"))
        assertEquals(listOf("bios", "BIOS"), localBiosFolderPaths(""))
        assertFalse(localBiosFolderPaths("ROMs").any { it.startsWith('/') || "//" in it })
    }
}
