package app.foldcade

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderListTest {
    @Test
    fun aFolderSavedBeforeTheListReadsBackAsOne() {
        val prefs = MemoryPrefs()
        prefs.edit().putString("folder_tree", "content://tree/roms").apply()
        val store = SessionStore(prefs)
        assertEquals(listOf("content://tree/roms"), store.folderTrees())

        store.addFolderTree("content://tree/Steam")
        store.addFolderTree("content://tree/roms")
        assertEquals(listOf("content://tree/roms", "content://tree/Steam"), store.folderTrees())

        store.removeFolderTree("content://tree/roms")
        assertEquals(listOf("content://tree/Steam"), store.folderTrees())
        store.removeFolderTree("content://tree/Steam")
        assertEquals(emptyList<String>(), store.folderTrees())
    }
}
