package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class NewGameFolderTest {
    @Test fun genuinelyNewGamesReturnAnAutomaticallyEmptiedFolderToHome() {
        for (kind in listOf(HomeKind.Rom, HomeKind.AndroidApp)) {
            val old = HomeItem("old", "Old", kind, "gba")
            val fresh = HomeItem("fresh", "Fresh", kind, "gba")
            val platforms = listOf(HomePlatform("gba", "GBA"))
            val before = mergeHome(HomeBoard(), listOf(old), platforms)
            val folder = before.folders.values.single().id
            val hidden = mergeHome(hideGame(before, old.id), listOf(old), platforms)
            assertFalse(folder in hidden.slots)
            val loaded = mergeHome(hidden, listOf(old, fresh), platforms)
            assertTrue("$kind folder must return", folder in loaded.slots)
            assertTrue(onHome(loaded, fresh.id))
            assertTrue(old.id in loaded.hidden)
        }
    }
    @Test fun newGamesDoNotReturnAnExplicitlyRetiredFolder() {
        val old = HomeItem("old", "Old", HomeKind.Rom, "gba")
        val fresh = HomeItem("fresh", "Fresh", HomeKind.Rom, "gba")
        val platforms = listOf(HomePlatform("gba", "GBA"))
        val before = mergeHome(HomeBoard(), listOf(old), platforms)
        val folder = before.folders.values.single().id
        val removed = removeFolder(before, folder)
        val loaded = mergeHome(removed, listOf(old, fresh), platforms)
        assertTrue(folder in loaded.retired)
        assertFalse(folder in loaded.slots)
        assertTrue(fresh.id in loaded.folders.getValue(folder).slots)
    }

}
