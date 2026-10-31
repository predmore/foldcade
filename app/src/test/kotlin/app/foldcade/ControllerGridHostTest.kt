package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class ControllerGridHostTest {
    @Test fun allGamesTriggersStepItsTabsAndPlayReordersRecentWithoutChangingHomeSlots() {
        val store = SessionStore(MemoryPrefs())
        val played = mutableMapOf("android:z" to 30L, "android:c" to 10L)
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()),
            lastPlayedMillis = { played[it] })
        shell.setInstalledApps(listOf(
            LaunchableApp("a", "Alpha", true), LaunchableApp("b", "Beta", true),
            LaunchableApp("c", "Charlie", true), LaunchableApp("z", "Zulu", true),
        ), emptySet())
        val before = decodeHome(store.homeBoardRaw()).slots
        shell.openHomeAll()
        assertEquals(listOf("Alpha", "Beta", "Charlie", "Zulu"), (0..3).map { shell.homeFace(it)?.title })
        // L2 and R2 are the tabs' here, so nothing jumps by letter.
        assertTrue(shell.model.gridLabels.isEmpty())
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        assertEquals(LibraryTabKind.Recent, shell.libraryBar()!!.tab.kind)
        assertEquals(0, shell.model.focus.cellIndex)
        assertNull(shell.model.letterJump)
        assertEquals(listOf("Zulu", "Charlie"), (0 until shell.model.count).map { shell.homeFace(it)?.title })
        played["android:a"] = 40L
        shell.notePlayChanged()
        assertEquals(listOf("Alpha", "Zulu", "Charlie"), (0 until shell.model.count).map { shell.homeFace(it)?.title })
        shell.onMeaning(Meaning.LetterBackward, HostScreen.Bottom)
        assertEquals(LibraryTabKind.All, shell.libraryBar()!!.tab.kind)
        assertEquals(before, decodeHome(store.homeBoardRaw()).slots)
        shell.goHome()
        assertNull(shell.libraryBar())
        shell.onMeaning(Meaning.Menu, HostScreen.Bottom)
        assertNotNull(shell.model.settings)
        shell.onMeaning(Meaning.Menu, HostScreen.Bottom)
        assertNull(shell.model.settings)
    }

    @Test fun hiddenTriggersUseAlphabeticalLabelsAndAppsOfferTheOtherAndroidFolder() {
        val store = SessionStore(MemoryPrefs())
        store.saveHomeBoard(encodeHome(HomeBoard(hidden = setOf(androidHomeId("a"), androidHomeId("b")))))
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(LaunchableApp("a", "Alpha", false),
            LaunchableApp("b", "Beta", false), LaunchableApp("g", "Game", true)), emptySet())
        shell.showHomeGrid(HomeGrid.Hidden)
        assertEquals(listOf("Alpha", "Beta"), shell.model.gridLabels)
        shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        assertEquals(1, shell.model.focus.cellIndex)
        assertEquals("B", shell.model.letterJump)

        // An app in Android Games moves to Android Apps, the one other folder it can be in.
        shell.openHomeFolder(HOME_ANDROID_GAMES)
        assertEquals(HOME_ANDROID_APPS, shell.model.tileActions?.moveTo)
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
        val all = root.indexOfFirst { it.allTile }
        home.handle(Meaning.Activate, all)
        assertTrue(home.board.allOpen)
        assertEquals(all, home.showRoot())
        assertFalse(home.board.allOpen)
    }
    @Test fun androidFoldersHiddenAndMenuAllRestoreTheirRootOpenerAndRootRecallKeepsFocus() {
        val shell = ShellController(SessionStore(MemoryPrefs()),
            PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(LaunchableApp("app", "App", false)), emptySet())
        shell.touchCell(5, HostScreen.Bottom)
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        val apps = panelRows(shell.model.panel!!, shell.model).indexOf(Row.Apps)
        shell.touchPanel(apps, HostScreen.Top)
        shell.touchPanel(apps, HostScreen.Top)
        assertFalse(shell.model.atLibraryRoot)
        assertEquals("App", shell.focusedGame()?.title)
        // L1 Apps opens the folder as A on its tile would, so Home comes back to that tile.
        shell.goHome()
        assertEquals(HOME_ANDROID_APPS, shell.homeFace(shell.model.focus.cellIndex)?.id)

        shell.touchCell(5, HostScreen.Bottom)
        shell.showHomeGrid(HomeGrid.Hidden)
        shell.goHome()
        assertEquals(5, shell.model.focus.cellIndex)

        shell.touchCell(6, HostScreen.Bottom)
        shell.goHome()
        assertEquals(6, shell.model.focus.cellIndex)
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        val all = panelRows(shell.model.panel!!, shell.model).indexOf(Row.AllLibrary)
        shell.touchPanel(all, HostScreen.Top)
        shell.touchPanel(all, HostScreen.Top)
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
        shell.goHome()
        assertEquals(6, shell.model.focus.cellIndex)

        val folder = (0 until shell.model.count).first { shell.homeFace(it)?.folder == true }
        shell.touchCell(folder, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.showHomeGrid(HomeGrid.Hidden)
        shell.goHome()
        assertEquals(folder, shell.model.focus.cellIndex)
    }

    @Test fun homeAndFolderTriggersUseTheLastOccupiedColumn() {
        val store = SessionStore(MemoryPrefs())
        val folder = HomeFolder("mine", "Mine", userMade = true, slots = listOf(androidHomeId("app")))
        store.saveHomeBoard(encodeHome(HomeBoard(
            slots = listOf(HOME_ALL, null, null, null, null, null, folder.id, null, null, null),
            folders = mapOf(folder.id to folder), addNewToHome = false,
        )))
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(LaunchableApp("app", "App", false)), emptySet())
        shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        assertEquals(2, GridStrip.column(shell.model.focus.cellIndex))
        shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        assertEquals(2, GridStrip.column(shell.model.focus.cellIndex))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(1, shell.model.count)
        assertEquals(0, shell.model.screenJumpLastColumn)
        shell.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        assertEquals(0, shell.model.focus.cellIndex)
    }

}
