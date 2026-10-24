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
        // A sheet in another app's tree is [sheetTreeOf]'s.
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

    @Test
    fun aSheetInAPickedFolderGrantsTheTreeItsFilesAreIn() {
        val tree = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
        assertEquals(tree, sheetTreeOf("$tree/document/primary%3AROMs%2Fps1%2FCastlevania%20(USA).cue"))
        assertEquals(tree, sheetTreeOf("$tree/document/primary%3AROMs%2Fps1%2FGame.M3U"))
        assertEquals(tree, sheetTreeOf("$tree/document/primary%3AGame.gdi"))
        // One file needs nothing beside it, and a document outside a tree names no tree.
        assertNull(sheetTreeOf("$tree/document/primary%3AROMs%2Fps1%2FGame.chd"))
        assertNull(sheetTreeOf("content://com.android.externalstorage.documents/document/primary%3AROMs%2FGame.cue"))
        assertNull(sheetTreeOf("$tree/document/"))
        assertNull(sheetTreeOf("file:///sdcard/ROMs/Game.cue"))
    }

    @Test
    fun aSiblingStaysInTheSameTreeFolder() {
        val romm = "content://app.foldcade.romm.folders/tree/romm%2Froms%2F37/document/romm%2Froms%2F37%2FGame.cue"
        assertEquals(
            "content://app.foldcade.romm.folders/tree/romm%2Froms%2F37/document/romm%2Froms%2F37%2FTrack.bin",
            siblingContentUri(romm, "Track.bin"),
        )
        val tree = "content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fps2"
        assertEquals(
            "$tree/document/primary%3AROMs%2Fps2%2FTrack%20%281%29.bin",
            siblingContentUri("$tree/document/primary%3AROMs%2Fps2%2FGame.cue", "Track (1).bin"),
        )
        val volume = "content://com.android.externalstorage.documents/tree/primary%3A"
        assertEquals(
            "$volume/document/primary%3ATrack.bin",
            siblingContentUri("$volume/document/primary%3AGame.cue", "Track.bin"),
        )
        assertNull(siblingContentUri(romm, "../Track.bin"))
        assertNull(siblingContentUri(romm, "dir/Track.bin"))
        assertNull(siblingContentUri(romm, "."))
        assertNull(siblingContentUri(romm, ".."))
        assertNull(siblingContentUri("$tree/document/primary%3AROMs%2Fps2%2FGame.cue".replace("/tree/", "/document/"), "Track.bin"))
        // The tree is the file itself, so a neighbour would leave it.
        assertNull(siblingContentUri("$tree/document/primary%3AROMs%2Fps2", "Track.bin"))
    }

    private companion object {
        const val AUTHORITY = "app.foldcade.romm.folders"
    }
}
