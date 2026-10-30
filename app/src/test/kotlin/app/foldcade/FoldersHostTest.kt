package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/**
 * A folder comes off Home with Y › Remove, and Edit home's Folders lists it with a switch. From
 * Folders, A opens any folder to browse, on Home or off it, and Back comes back to Folders.
 */
class FoldersHostTest {
    private val quest = androidHomeId("quest")
    private val notes = androidHomeId("notes")
    private val apps = listOf(LaunchableApp("quest", "Quest", true), LaunchableApp("notes", "Notes", false))
    private val store = SessionStore(MemoryPrefs())
    private fun shell() = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())).also { it.setInstalledApps(apps, emptySet()) }
    private fun board() = decodeHome(store.homeBoardRaw())

    @Test fun removeTakesAFolderOffHomeAndFoldersPutsItBack() {
        val shell = shell()
        val games = board().slots.indexOf(HOME_ANDROID_GAMES)
        assertTrue(games > 0)
        focus(shell, games)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.RemoveTile)
        assertFalse(HOME_ANDROID_GAMES in board().slots)
        assertEquals(listOf(quest), board().folders.getValue(HOME_ANDROID_GAMES).slots)
        // Undo says where it went: off Home, not gone.
        assertEquals(Copy.offHome, shell.model.undoVerb)

        openFolders(shell)
        assertTrue(shell.homeFoldersOpen())
        assertEquals(listOf("Android Apps", "Android Games"), (0 until shell.model.count).map { shell.homeFace(it)?.title })
        assertEquals(listOf(true, false), (0 until shell.model.count).map { shell.homeFace(it)?.onGrid })
        focus(shell, 1)
        val hero = shell.focusedHero() as HeroSubject.Item
        assertEquals("Android Games", hero.item.title)
        assertEquals(FindCopy.notOnHome, hero.item.availability)

        // Y › On Home, which flips in place and leaves the menu open on it.
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Row.FolderOnHome, panelRows(shell.model.panel!!, shell.model).last())
        choose(shell, Row.FolderOnHome)
        assertEquals(games, board().slots.indexOf(HOME_ANDROID_GAMES))
        assertNotNull(shell.model.panel)
        assertEquals(RowControl.Toggle(true), rowControl(Row.FolderOnHome, shell.model))
        assertEquals(true, shell.homeFace(1)?.onGrid)
        choose(shell, Row.FolderOnHome)
        assertFalse(HOME_ANDROID_GAMES in board().slots)
        assertEquals(RowControl.Toggle(false), rowControl(Row.FolderOnHome, shell.model))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.model.panel)
        assertTrue(shell.homeFoldersOpen())
    }

    @Test fun aFolderOffHomeOpensFromFoldersAndBackComesBackToIt() {
        val shell = shell()
        val root = board().slots.indexOf(HOME_ANDROID_APPS)
        focus(shell, root)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.RemoveTile)
        val rest = shell.model.focus.cellIndex
        openFolders(shell)
        assertEquals(0, shell.model.focus.cellIndex)
        assertEquals("Android Apps", shell.homeFace(0)?.title)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertFalse(shell.homeFoldersOpen())
        assertEquals(listOf(notes), shell.homeSlots())
        // Still off Home: browsing it does not put it back.
        assertFalse(HOME_ANDROID_APPS in board().slots)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertTrue(shell.homeFoldersOpen())
        assertEquals(0, shell.model.focus.cellIndex)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertFalse(shell.homeFoldersOpen())
        assertTrue(shell.atRest())
        // Back lands where the board was left.
        assertEquals(rest, shell.model.focus.cellIndex)
    }

    @Test fun foldersIsNotSavedAndARestartKeepsTheSwitches() {
        val shell = shell()
        openFolders(shell)
        focus(shell, 1)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.FolderOnHome)
        assertFalse(HOME_ANDROID_GAMES in board().slots)
        assertFalse(board().foldersOpen)
        val again = shell()
        assertTrue(again.atRest())
        openFolders(again)
        assertEquals(listOf(true, false), (0 until again.model.count).map { again.homeFace(it)?.onGrid })
    }

    @Test fun xOpensAFolderInFoldersAsAdoes() {
        val shell = shell()
        openFolders(shell)
        focus(shell, 1)
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom)
        assertEquals(listOf(quest), shell.homeSlots())
        assertTrue(HOME_ANDROID_GAMES in board().slots)
    }

    @Test fun findListsAFolderOffHomeAndOpensItWhereItIs() {
        val home = HomeSession(null)
        val items = listOf(HomeItem(quest, "Quest", HomeKind.AndroidGame), HomeItem(notes, "Notes", HomeKind.AndroidApp))
        home.remerge(emptyList(), items, emptyList(), emptyList())
        home.setFolderOnHome(HOME_ANDROID_GAMES, on = false, index = 0)
        val entry = home.findEntries().single { it.id == HOME_ANDROID_GAMES }
        assertEquals(FindKind.Group, entry.kind)
        assertEquals(listOf("1 game", FindCopy.notOnHome), findFacts(home.board, entry))
        // Its menu is Folders' switch, and one on Home also goes to its tile.
        assertEquals(TileActions(folderOnHome = FolderSwitch(HOME_ANDROID_GAMES, on = false)), home.findActions(HOME_ANDROID_GAMES))
        assertEquals(TileActions(folderOnHome = FolderSwitch(HOME_ANDROID_APPS, on = true), showOnHome = HOME_ANDROID_APPS), home.findActions(HOME_ANDROID_APPS))
        assertNull(home.openOffHome(HOME_ANDROID_APPS))
        assertEquals(0, home.openOffHome(HOME_ANDROID_GAMES))
        assertEquals(listOf(quest), visibleSlots(home.board))
        assertFalse(groupOnHome(home.board, HOME_ANDROID_GAMES))
    }

    @Test fun anOpenMenuKeepsItsFolderWhenAReportMovesFoldersUnderIt() {
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())).also { it.setInstalledApps(apps.take(1), emptySet()) }
        openFolders(shell)
        assertEquals(listOf("Android Games"), (0 until shell.model.count).map { shell.homeFace(it)?.title })
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        // Android Apps arrives and sorts first, onto the slot the menu was opened on.
        shell.setInstalledApps(apps, emptySet())
        assertEquals("Android Apps", shell.homeFace(0)?.title)
        assertEquals(HOME_ANDROID_GAMES, shell.model.tileActions?.folderOnHome?.folderId)
        choose(shell, Row.FolderOnHome)
        assertFalse(HOME_ANDROID_GAMES in board().slots)
        assertTrue(HOME_ANDROID_APPS in board().slots)
    }

    @Test fun showOnHomeLeavesFolders() {
        val home = HomeSession(null)
        home.remerge(emptyList(), listOf(HomeItem(quest, "Quest", HomeKind.AndroidGame), HomeItem(notes, "Notes", HomeKind.AndroidApp)), emptyList(), emptyList())
        home.showFolders()
        val at = home.reveal(HOME_ANDROID_GAMES)
        assertFalse(home.board.foldersOpen)
        assertEquals(HOME_ANDROID_GAMES, home.face(at!!)?.id)
    }

    private fun openFolders(shell: ShellController) {
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.EditHome)
        choose(shell, Row.Folders)
        assertNull(shell.model.panel)
    }

    private fun focus(shell: ShellController, target: Int) {
        val columns = GridStrip.column(target) - GridStrip.column(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(columns)) { shell.onMeaning(if (columns > 0) Meaning.MoveRight else Meaning.MoveLeft, HostScreen.Bottom) }
        val rows = GridStrip.row(target) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(rows)) { shell.onMeaning(if (rows > 0) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom) }
        assertEquals(target, shell.model.focus.cellIndex)
    }

    private fun choose(shell: ShellController, row: Row) {
        val target = panelRows(shell.model.panel!!, shell.model).indexOf(row)
        assertTrue("Missing $row", target >= 0)
        walkPanelTo(shell, target, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }
}
