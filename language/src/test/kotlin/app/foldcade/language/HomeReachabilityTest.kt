package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeReachabilityTest {
    @Test fun bulkReachabilityMatchesPinsFoldersCollectionsRecentAndHiddenGames() {
        val board = HomeBoard(
            slots = listOf(HOME_ALL, "pin", "hidden-pin", "system", "collection", HOME_RECENT),
            folders = mapOf(
                "system" to HomeFolder("system", "System", slots = listOf("filed", "hidden", null)),
                "collection" to HomeFolder("collection", "Collection", slots = listOf("filed", "collected")),
                HOME_RECENT to HomeFolder(HOME_RECENT, "Recent", slots = listOf("recent", "hidden")),
                "removed" to HomeFolder("removed", "Removed", slots = listOf("off-home")),
            ),
            hidden = setOf("hidden", "hidden-pin"),
        )
        val ids = setOf(HOME_ALL, "pin", "hidden-pin", "system", "collection", HOME_RECENT,
            "filed", "hidden", "collected", "recent", "removed", "off-home", "unknown")
        for (shown in listOf(board, board.copy(allOpen = true), board.copy(openFolderId = "removed"))) {
            assertEquals(ids.filter { onHome(shown, it) }.toSet(), onHomeIds(shown))
        }
    }
}
