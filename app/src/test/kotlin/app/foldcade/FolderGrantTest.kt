package app.foldcade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderGrantTest {
    @Test
    fun aFileInADownloadFolderGrantsThatFolderOnly() {
        val folder = "romm-media%2Fservers%2Fabc%2Froms%2F35%2Ff35"
        assertEquals(
            "content://$AUTHORITY/tree/$folder",
            folderTreeOf("content://$AUTHORITY/tree/$folder/document/$folder%2FGame.nsp", AUTHORITY),
        )
    }

    @Test
    fun anyOtherUriGrantsNoFolder() {
        // The plain download provider, another app's tree, and a folder rather than a file.
        assertNull(folderTreeOf("content://app.foldcade.romm.cache/romm-media/roms/35/Game.nsp", AUTHORITY))
        assertNull(
            folderTreeOf(
                "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fa.nsp",
                AUTHORITY,
            ),
        )
        assertNull(folderTreeOf("content://$AUTHORITY/tree/romm%2Froms", AUTHORITY))
        assertNull(folderTreeOf("content://$AUTHORITY/tree//document/x", AUTHORITY))
        // The debug build's provider is not the release build's.
        assertNull(folderTreeOf("content://app.foldcade.debug.romm.folders/tree/a/document/a%2Fb", AUTHORITY))
    }

    private companion object {
        const val AUTHORITY = "app.foldcade.romm.folders"
    }
}
