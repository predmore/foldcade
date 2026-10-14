package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class HiddenCollectionRecoveryTest {
    @Test fun newCollectionUsesTheAdmittedBoardAndKeepsRecoveredMemberships() {
        val id = "game"
        val original = newCollection(HomeBoard(), "home.user.1", "Original", member = id)
        val hidden = hideGame(original, id)
        val restored = newCollection(hidden, "home.user.2", "New", member = id)
        assertEquals(listOf(id), restored.folders.getValue("home.user.1").slots)
        assertEquals(listOf(id), restored.folders.getValue("home.user.2").slots)
        assertFalse(id in restored.hidden)
        assertFalse(id in restored.hiddenSlots)
    }
    @Test fun anExplicitCollectionSlotCannotOverwriteARecoveredPin() {
        val id = "game"
        val original = newCollection(HomeBoard(slots = listOf(HOME_ALL, id)), "home.user.1", "Original", member = id)
        val restored = newCollection(hideGame(original, id), "home.user.2", "New", at = 1, member = id)
        assertEquals(id, restored.slots[1])
        assertTrue("home.user.2" in restored.slots)
    }

}
