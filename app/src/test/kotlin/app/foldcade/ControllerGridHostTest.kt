package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class ControllerGridHostTest {
    @Test fun recentSortChangesAllFacesAndTriggersWithoutChangingHomeSlots() {
        val store = SessionStore(MemoryPrefs())
        store.setLibrarySort(LibrarySort.RecentlyPlayed)
        val played = mutableMapOf("android:z" to 30L, "android:c" to 10L)
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()),
            lastPlayedMillis = { played[it] })
        shell.setInstalledApps(listOf(
            LaunchableApp("a", "Alpha", true), LaunchableApp("b", "Beta", true),
            LaunchableApp("c", "Charlie", true), LaunchableApp("z", "Zulu", true),
        ), emptySet())
        val before = decodeHome(store.homeBoardRaw()).slots
        shell.openHomeAll()
        assertEquals(listOf("Zulu", "Charlie", "Alpha", "Beta"), (0..3).map { shell.homeFace(it)?.title })
        assertTrue(shell.model.gridLabels.isEmpty())
        shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        assertEquals(12, shell.model.focus.cellIndex)
        assertNull(shell.model.letterJump)
        played["android:a"] = 40L
        shell.notePlayChanged()
        assertEquals("Alpha", shell.homeFace(0)?.title)
        assertEquals(before, decodeHome(store.homeBoardRaw()).slots)
        shell.goHome()
        shell.onMeaning(Meaning.Menu, HostScreen.Bottom)
        assertNotNull(shell.model.settings)
        shell.onMeaning(Meaning.Menu, HostScreen.Bottom)
        assertNull(shell.model.settings)
    }

    @Test fun shelfTriggersUseAlphabeticalLabelsAndCompatibleDestinations() {
        val shell = ShellController(SessionStore(MemoryPrefs()),
            PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(LaunchableApp("a", "Alpha", false),
            LaunchableApp("b", "Beta", false), LaunchableApp("g", "Game", true)), emptySet())
        shell.showHomeGrid(HomeGrid.Apps)
        assertEquals(listOf("Alpha", "Beta"), shell.model.gridLabels)
        assertTrue(shell.model.appActions!!.destinations.any { it.folderId == HOME_ANDROID_GAMES })
        assertTrue(shell.model.appActions!!.destinations.any { it.folderId == HOME_ANDROID_APPS })
        shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        assertEquals(1, shell.model.focus.cellIndex)
        assertEquals("B", shell.model.letterJump)
    }

    @Test fun returningHomeRestoresTheFolderOrAllOpener() {
        val home = HomeSession(null)
        home.remerge(emptyList(), listOf(HomeItem("app", "App", HomeKind.AndroidApp)),
            emptyList(), emptyList())
        val root = home.faces()
        val folder = root.indexOfFirst { it.folder }
        home.handle(Meaning.Activate, folder)
        assertNotNull(home.board.openFolderId)
        assertEquals(folder, home.showRoot())
        assertNull(home.board.openFolderId)
        val all = root.indexOfFirst { it.pinned }
        home.handle(Meaning.Activate, all)
        assertTrue(home.board.allOpen)
        assertEquals(all, home.showRoot())
        assertFalse(home.board.allOpen)
    }
}
