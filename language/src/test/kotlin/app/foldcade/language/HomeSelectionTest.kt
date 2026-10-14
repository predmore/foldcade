package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class HomeSelectionTest {
    private val collection = HomeFolder("home.user.1", "Quests", userMade = true, slots = listOf("a"))
    private val folder = HomeFolder("platform", "Games", bucket = HomeKind.Rom, slots = listOf("a", "b", "c"))
    private val board = HomeBoard(slots = listOf(HOME_ALL, "a", "b", null, collection.id, folder.id),
        folders = mapOf(collection.id to collection, folder.id to folder))

    @Test fun checksAreIdsAndResolveInViewOrderRatherThanCheckOrder() {
        val selected = HomeSelection("root").toggle("b", 2).toggle("a", 1)
        assertEquals(listOf("a", "b"), selected.ordered(board.slots))
        assertEquals(listOf("b", "a"), selected.ordered(listOf("b", null, "new", "a")))
        assertEquals(setOf("a"), selected.toggle("b", 2).checked)
        assertNull(selected.inView("folder"))
        assertEquals(selected, selected.inView("root"))
        val refused = selected.toggle(null, 0)
        assertEquals(selected.checked, refused.checked)
        assertEquals(1, refused.refusals)
        assertEquals(0, refused.refusedAt)
    }

    @Test fun mixedAddsTheMissingGamesAndAllRemovesTheWholeBatch() {
        val ids = listOf("a", "b")
        val choice = bulkCollectionChoices(board, ids).first { it.folderId == collection.id }
        assertTrue(choice.some)
        assertFalse(choice.member)
        val added = bulkCollection(board, collection.id, ids)
        assertEquals(listOf("a", "b"), added.folders.getValue(collection.id).slots)
        assertTrue(bulkCollectionChoices(added, ids).first { it.folderId == collection.id }.member)
        val removed = bulkCollection(added, collection.id, ids)
        assertTrue(removed.folders.getValue(collection.id).slots.all { it == null })
        assertEquals(board.slots, removed.slots)
        assertEquals(folder, removed.folders.getValue(folder.id))
        assertFalse(bulkCollectionChoices(removed, ids).first { it.folderId == collection.id }.some)
        val favorites = bulkCollection(board, HOME_FAVORITES, ids)
        assertEquals(ids, favorites.folders.getValue(HOME_FAVORITES).slots)
        assertEquals(HOME_FAVORITES, bulkCollectionChoices(board, ids).first().folderId)
    }

    @Test fun newCollectionUsesTheFirstCheckedRootSlotWithoutLosingAnyPins() {
        val made = bulkNewCollection(board, "home.user.2", "Playing", listOf("a", "b"))
        assertEquals("home.user.2", made.slots[1])
        assertEquals("a", made.slots[3])
        assertEquals("b", made.slots[2])
        assertEquals(listOf("a", "b"), made.folders.getValue("home.user.2").slots)
        assertEquals(folder, made.folders.getValue(folder.id))
        assertEquals(collection, made.folders.getValue(collection.id))
    }

    @Test fun aNestedOrAllSelectionUsesTheFirstFreeRootSlot() {
        for (view in listOf(board.copy(openFolderId = folder.id), board.copy(allOpen = true))) {
            val made = bulkNewCollection(view, "home.user.2", "Playing", listOf("c", "a"))
            assertEquals("home.user.2", made.slots[3])
            assertEquals(listOf("c", "a"), made.folders.getValue("home.user.2").slots)
            assertEquals(view.openFolderId, made.openFolderId)
            assertEquals(view.allOpen, made.allOpen)
        }
    }

    @Test fun oneUndoReversesTheWholeHideOnTheCurrentBoard() {
        val after = bulkHide(board, listOf("a", "b"))
        assertTrue(after.hidden.containsAll(listOf("a", "b")))
        assertTrue(after.folders.values.all { "a" !in it.slots && "b" !in it.slots })
        val undo = HomeUndo(board, AppShelfState(), Copy.hidden, 0L, after)
        assertEquals(board, undo.restore(after))
        val arrived = after.copy(slots = after.slots + "new", folders = after.folders +
            (folder.id to after.folders.getValue(folder.id).copy(slots = after.folders.getValue(folder.id).slots + "new")))
        val restored = undo.restore(arrived)
        assertTrue("new" in restored.slots)
        assertEquals(listOf("a", "b", "c", "new"), restored.folders.getValue(folder.id).slots)
        assertNull(undo.afterEdit(after, bulkCollection(after, HOME_FAVORITES, listOf("c"))))
    }

    @Test fun selectionKeysHintsAndBulkHideChordFollowTheSharedMap() {
        assertEquals(Meaning.Options, meaningOf(KeyEvent.KEYCODE_BUTTON_Y, homeKeys = HomeKeys.Selecting))
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_A, homeKeys = HomeKeys.Selecting))
        assertEquals(Meaning.Back, meaningOf(KeyEvent.KEYCODE_BUTTON_B, homeKeys = HomeKeys.Selecting))
        assertTrue(HomeKeys.Selecting.guardsHeldKeys)
        val guard = HeldKeyGuard()
        assertFalse(guard.swallows(KeyEvent.KEYCODE_BUTTON_A, 0, capturing = true))
        assertTrue(guard.swallows(KeyEvent.KEYCODE_BUTTON_A, 1, capturing = false))
        assertEquals(listOf(SelectionCopy.check, SelectionCopy.actions, SelectionCopy.done), gridHints(null, HomeKeys.Selecting, FaceMap.standard()).map { it.label })
        val actions = TileActions(bulk = listOf("a", "b"), collections = bulkCollectionChoices(board, listOf("a", "b")), newCollection = true, remove = RemoveVerb.Hide)
        assertEquals(Row.RemoveTile, actionRows(actions, null)[chordIndex(actionRows(actions, null), Meaning.ActivateBottom)!!])
        assertTrue(actionRows(TileActions(), null).isEmpty())
    }
}
