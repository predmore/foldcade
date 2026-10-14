package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class CollectionIdentityTest {
    @Test fun deletedCollectionIdsAreNeverReusedAfterRestart() {
        val first = newCollection(HomeBoard(), nextUserFolderId(HomeBoard()), "Old")
        val removed = removeFolder(first, "home.user.1")
        assertEquals("home.user.2", nextUserFolderId(decodeHome(encodeHome(removed))))
    }

    @Test fun deletingACollectionDropsHiddenMembershipReferences() {
        val id = "game"
        val old = newCollection(HomeBoard(), "home.user.1", "Old", member = id)
        val removed = removeFolder(hideGame(old, id), "home.user.1")
        assertTrue(removed.hiddenSlots[id].orEmpty().none { it.group == "home.user.1" })
        assertEquals(removed, newCollection(removed, "home.user.1", "Replacement"))
        val replacementId = nextUserFolderId(removed)
        val replacement = newCollection(removed, replacementId, "Replacement")
        val shown = showHidden(replacement, id, listOf(HomeItem(id, "Game", HomeKind.Loose)), emptyList())
        assertTrue(shown.folders.getValue(replacementId).slots.isEmpty())
    }
    @Test fun oldSavesInferTheCounterFromSurvivingHiddenReferences() {
        val old = HomeBoard(hidden = setOf("game"), hiddenSlots = mapOf("game" to listOf(HiddenSlot("home.user.8", 0))))
        assertEquals("home.user.9", nextUserFolderId(decodeHome(encodeHome(old))))
    }

}
