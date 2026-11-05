package app.foldcade

import app.foldcade.language.PlayCopy
import org.junit.Assert.*
import org.junit.Test

class ExportDocumentTest {
    @Test fun resultsNameTheFolderThePlayerActuallyChose() {
        assertEquals("Downloads", exportFolder("com.android.externalstorage.documents", "primary:Download/history.zip"))
        assertEquals("Downloads/Backups", exportFolder("com.android.externalstorage.documents", "primary:Download/Backups/setup.zip"))
        assertEquals("Documents", exportFolder("com.android.externalstorage.documents", "primary:Documents/setup.zip"))
        assertEquals("Downloads", exportFolder("com.android.providers.downloads.documents", "msf:42"))
        assertEquals("Downloads/Backups", exportFolder("com.android.providers.downloads.documents", "raw:/storage/emulated/0/Download/Backups/setup.zip"))
        assertNull(exportFolder("cloud", "opaque"))
        assertEquals("Saved to Downloads.", PlayCopy.exported("Downloads", null))
        assertEquals("Saved as setup.zip.", PlayCopy.exported(null, "setup.zip"))
        assertEquals("Saved Foldcade backup 2026-11-04 (2).zip to Downloads.", PlayCopy.exported("Downloads", "Foldcade backup 2026-11-04 (2).zip"))
    }
}
