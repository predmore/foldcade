package app.foldcade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderUriTest {
    private val tree = "content://com.android.externalstorage.documents/tree/primary%3Asaves"
    private val folder = "$tree/document/primary%3Asaves%2FAzahar"

    @Test fun saveDocumentsRootAtTheDocumentInsideTheTree() {
        assertEquals("primary:saves/Azahar", folderDocumentId(folder))
        assertEquals("primary:saves", folderDocumentId(tree))
        assertEquals("primary:saves", folderTreeId(folder))
        assertEquals(folder, folderDocumentUri(tree, "primary:saves/Azahar"))
    }

    @Test fun encodedSpacesAndLiteralPlusesStayInTheDocumentId() {
        assertEquals("primary:saves/A + B", folderDocumentId("$tree/document/primary%3Asaves%2FA%20%2B%20B"))
        assertEquals("primary:saves/A+B", folderDocumentId("$tree/document/primary%3Asaves%2FA+B"))
    }

    @Test fun libraryRemovalKeepsATreeUsedByAContainedSaveFolder() {
        val store = SessionStore(MemoryPrefs())
        store.addFolderTree(tree)
        store.setPlayerSaveFolder("azahar", folder, found = true)
        store.removeFolderTree(tree)
        assertTrue(store.folderTreeInUse(tree))
        assertTrue(store.folderTreeInUse(folder))
        store.setPlayerSaveFolder("azahar", null)
        assertFalse(store.folderTreeInUse(tree))
    }

    @Test fun anotherFolderReferenceKeepsTheSameGrant() {
        val store = SessionStore(MemoryPrefs())
        store.addFolderTree(tree)
        assertTrue(store.folderTreeInUse(folder))
        assertFalse(store.folderTreeInUse("content://other/tree/primary%3Asaves"))
        assertEquals(tree, folderGrantUri(folder))
        assertEquals(tree, folderGrantUri("content://com.android.externalstorage.documents/tree/primary%3asaves"))
    }

    @Test fun rebuiltTreeUrisKeepTheCharactersAndroidLeavesUnencoded() {
        val picked = "content://com.android.externalstorage.documents/tree/primary%3AEmu%20(old)%2FJon's%20saves!~"
        assertEquals(picked, folderGrantUri(picked))
        assertEquals("primary:Emu (old)/Jon's saves!~", folderDocumentId(picked))
    }
}
