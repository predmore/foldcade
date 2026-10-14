package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class HomeUndoMergeTest {
    @Test fun undoKeepsAnArrivalInTheRemovedPinsSlot() {
        val folder = HomeFolder("folder", "Games", bucket = HomeKind.Rom, slots = listOf("old"))
        val before = HomeBoard(slots = listOf(HOME_ALL, "old", null, folder.id), folders = mapOf(folder.id to folder))
        val after = removeFromHome(before, 1)
        val arrived = after.copy(slots = listOf(HOME_ALL, "new", null, folder.id), folders = after.folders + (folder.id to folder.copy(slots = listOf("old", "new"))))
        val restored = HomeUndo(before, AppShelfState(), Copy.unpinned, 0L, after).restore(arrived)
        assertEquals(listOf(HOME_ALL, "new", "old", folder.id), restored.slots)
        assertEquals(listOf("old", "new"), restored.folders.getValue(folder.id).slots)
    }

    @Test fun undoHideKeepsLaterCatalogLabelsAndFolderMembers() {
        val folder = HomeFolder("folder", "Old label", bucket = HomeKind.AndroidApp, slots = listOf("old", "kept"))
        val before = HomeBoard(slots = listOf(HOME_ALL, folder.id), folders = mapOf(folder.id to folder))
        val after = hideGame(before, "old")
        val current = after.copy(folders = after.folders + (folder.id to after.folders.getValue(folder.id).copy(name = "New label", slots = listOf("new", "kept"))))
        val restored = HomeUndo(before, AppShelfState(), Copy.hidden, 0L, after).restore(current)
        assertEquals("New label", restored.folders.getValue(folder.id).name)
        assertEquals(listOf("new", "kept", "old"), restored.folders.getValue(folder.id).slots)
        assertFalse("old" in restored.hidden)
        assertFalse("old" in restored.hiddenSlots)
    }
}
