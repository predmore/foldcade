package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/** A new collection opened before it holds a game keeps its name and leads to filling it. */
class EmptyCollectionHostTest {
    private val quest = androidHomeId("quest")
    private val group = HomeFolder("home.user.1", "The backlog of every game I promised myself I'd finish", userMade = true)
    private val initial = HomeBoard(slots = listOf(HOME_ALL, group.id), folders = mapOf(group.id to group))
    private val apps = listOf(LaunchableApp("quest", "Quest", true))
    private val store = SessionStore(MemoryPrefs()).also { it.saveHomeBoard(encodeHome(initial)) }
    private fun shell() = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())).also { it.setInstalledApps(apps, emptySet()) }
    private fun board() = decodeHome(store.homeBoardRaw())

    @Test fun anEmptyCollectionShowsItselfAndAddGamesFillsItThenBackComesBack() {
        val shell = shell()
        val root = board().slots.indexOf(group.id)
        open(shell, root)
        assertEquals(group.id, shell.emptyCollection()?.id)
        // The top screen names the collection where an empty slot would show nothing.
        assertNull(shell.focusedHero())
        val hero = shell.emptyCollectionHero() as HeroSubject.Folder
        assertEquals(group.name, heroCopy(hero).title)
        assertEquals("0 games", heroCopy(hero).detail)
        // Y has nothing to offer on a grid with no tiles, so it is not hinted.
        assertFalse(shell.hasOptions())

        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
        assertNull(shell.emptyCollection())
        assertEquals(quest, shell.homeFace(shell.model.focus.cellIndex)?.id)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, Row.Collection(group.id, group.name))
        assertEquals(listOf(quest), board().folders.getValue(group.id).slots)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.model.panel)

        // Back from All returns to the collection, now holding the game, and then to its tile.
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertEquals(HomeKeys.Idle, shell.homeKeys())
        assertEquals(listOf(quest), shell.homeSlots())
        assertNull(shell.emptyCollection())
        assertEquals(quest, shell.homeFace(shell.model.focus.cellIndex)?.id)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertTrue(shell.atRest())
        assertEquals(root, shell.model.focus.cellIndex)
    }

    @Test fun xAndOneTapAlsoAddGamesAndBackWithoutAddingStillReturns() {
        val shell = shell()
        open(shell, board().slots.indexOf(group.id))
        assertNull(shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom))
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertEquals(group.id, shell.emptyCollection()?.id)
        // The button is the only thing to press, so one tap presses it.
        assertNull(shell.touchAddGames(HostScreen.Bottom))
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
    }

    @Test fun downFromThePillsComesBackToAddGames() {
        val shell = shell()
        open(shell, board().slots.indexOf(group.id))
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertNotNull(shell.model.focus.chrome)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        assertNull(shell.model.focus.chrome)
        assertEquals(group.id, shell.emptyCollection()?.id)
    }

    @Test fun allOpenedAnotherWayStillGoesBackHome() {
        val shell = shell()
        val root = board().slots.indexOf(group.id)
        open(shell, root)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        // Home leaves All; the collection Add games left is forgotten.
        shell.goHome()
        focus(shell, board().slots.indexOf(HOME_ALL))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertTrue(shell.atRest())
        // On Home, and inside a collection with games, A is not Add games.
        assertNull(shell.touchAddGames(HostScreen.Bottom))
        assertTrue(shell.atRest())
    }

    @Test fun aCollectionEmptiedByAddToStillOffersUndo() {
        val filled = group.copy(slots = listOf(quest))
        store.saveHomeBoard(encodeHome(initial.copy(folders = mapOf(group.id to filled))))
        val shell = shell()
        open(shell, board().slots.indexOf(group.id))
        assertNull(shell.emptyCollection())
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, Row.Collection(group.id, group.name))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertEquals(group.id, shell.emptyCollection()?.id)
        assertEquals(group.name, (shell.emptyCollectionHero() as HeroSubject.Folder).folder.name)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Undo)
        assertEquals(listOf(quest), board().folders.getValue(group.id).slots)
        assertNull(shell.emptyCollection())
    }

    @Test fun besideAnAppUpStaysOnAddGamesForAToPress() {
        // The pills are on the top screen, so with an app there Up has nowhere to go.
        store.place(Panel.Top, ExternalApp("game"))
        val shell = shell()
        open(shell, board().slots.indexOf(group.id))
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertNull(shell.model.focus.chrome)
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertNull(shell.model.panel)
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
    }

    @Test fun aTapOnAddGamesAddsGamesWhileAPillIsLit() {
        val shell = shell()
        open(shell, board().slots.indexOf(group.id))
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        assertNotNull(shell.model.focus.chrome)
        assertNull(shell.touchAddGames(HostScreen.Bottom))
        assertNull(shell.model.panel)
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
    }

    private fun open(shell: ShellController, index: Int) {
        focus(shell, index)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertFalse(shell.atRest())
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
