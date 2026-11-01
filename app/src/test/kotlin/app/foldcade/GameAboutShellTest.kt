package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.GridStrip
import app.foldcade.language.HomeKeys
import app.foldcade.language.HostScreen
import app.foldcade.language.LaunchableApp
import app.foldcade.language.Meaning
import app.foldcade.language.Row
import app.foldcade.language.ScreenLayout
import app.foldcade.language.panelRows
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Y › About from the grid: only a library's games have it, it opens on the top screen or beside a game in every layout, a held key stays its own, and it closes once its game or screen is gone. */
class GameAboutShellTest {
    @Test
    fun aLibraryGameHasAboutAndAnAndroidGameDoesNot() {
        val shell = shell()
        focus(shell, titles(shell).indexOf("Zelda"))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertTrue(Row.About in rows(shell))
        shell.onMeaning(Meaning.Back, HostScreen.Top)

        focus(shell, titles(shell).indexOf("Asphalt"))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertFalse(Row.About in rows(shell))
    }

    @Test
    fun aboutOpensThePageAndAHeldKeyNeitherRepeatsNorReachesTheGrid() {
        val shell = shell()
        val zelda = titles(shell).indexOf("Zelda")
        focus(shell, zelda)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        walkPanelTo(shell, rows(shell).indexOf(Row.About), HostScreen.Top)
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        val page = shell.model.gameAbout!!
        assertNull(shell.model.panel)
        assertEquals("Zelda", shell.aboutGame(page.game)?.title)
        assertEquals(HomeKeys.Idle, shell.homeKeys())

        // A's repeats from the press that opened it do nothing, here or on the grid under it.
        shell.onMeaning(Meaning.Activate, HostScreen.Top, repeatCount = 1)
        assertEquals(page, shell.model.gameAbout)

        // B closes it on the game, and its repeats do not take the grid back out of All games.
        shell.onMeaning(Meaning.Back, HostScreen.Top)
        assertNull(shell.model.gameAbout)
        assertEquals(zelda, shell.model.focus.cellIndex)
        shell.onMeaning(Meaning.Back, HostScreen.Top, repeatCount = 1)
        shell.onMeaning(Meaning.Back, HostScreen.Top, repeatCount = 2)
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
        assertEquals(zelda, shell.model.focus.cellIndex)
    }

    @Test
    fun aTapBesideThePageClosesIt() {
        val shell = shell()
        focus(shell, titles(shell).indexOf("Zelda"))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        walkPanelTo(shell, rows(shell).indexOf(Row.About), HostScreen.Top)
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        assertNotNull(shell.model.gameAbout)
        shell.closeGameAbout()
        assertNull(shell.model.gameAbout)
    }

    @Test
    fun besideAGameThePageHoldsTheGridOpenUntilItCloses() {
        val store = SessionStore(MemoryPrefs())
        val shell = shell(store)
        // A game plays on the bottom screen, and the grid was opened on the top one beside it.
        store.place(Panel.Bottom, ExternalApp("lib:local-folder:kart7", false))
        store.update { it.browse(Panel.Top) }
        assertEquals(Surface.Picker, store.session.surfaceOn(Panel.Top))
        assertFalse(shell.holdsCommandCenter())
        openAbout(shell, "Zelda")
        assertEquals(HostScreen.Top, shell.model.gameAbout?.screen)
        // The command center's idle timer waits while the page is read, so B still reaches the page.
        assertTrue(shell.holdsCommandCenter())
        shell.onMeaning(Meaning.Back, HostScreen.Top)
        assertNull(shell.model.gameAbout)
        assertFalse(shell.holdsCommandCenter())
    }

    @Test
    fun thePageOpensOnTheTopScreenInEveryLayoutAndEitherPlayOn() {
        for (layout in ScreenLayout.entries) for (playOn in Panel.entries) {
            val case = "$layout, Play on $playOn"
            val store = SessionStore(MemoryPrefs())
            store.update { it.withLayout(layout).withPlayOn(playOn) }
            val shell = shell(store)
            // Keys come from the grid's screen; the dark screen's are the grid's too.
            openAbout(shell, "Zelda", keys = layout.gridScreen)
            assertEquals(case, HostScreen.Top, shell.model.gameAbout?.screen)
            // Top only leaves nothing beside the page: the bottom stays dark.
            assertEquals(case, layout != ScreenLayout.TopOnly, store.session.draws(Panel.Bottom))
            shell.onMeaning(Meaning.Back, HostScreen.Top)
            assertNull(case, shell.model.gameAbout)
        }
    }

    @Test
    fun besideAGameThePageOpensOnTheScreenFoldcadeStillShowsInEveryLayout() {
        // Each layout with a game on either screen, but Top only's game on top, which leaves no grid to open.
        val cases = ScreenLayout.entries.flatMap { layout -> Panel.entries.map { layout to it } } - (ScreenLayout.TopOnly to Panel.Top)
        for ((layout, playOn) in cases) {
            val case = "$layout, Play on $playOn"
            val store = SessionStore(MemoryPrefs())
            store.update { it.withLayout(layout).withPlayOn(playOn) }
            val shell = shell(store)
            store.afterSessionChanged = shell::noteSessionChanged
            store.update { it.launch(ExternalApp("lib:local-folder:kart7", false)) }
            val beside = playOn.flip()
            store.update { it.browse(beside) }
            assertEquals(case, Surface.Picker, store.session.surfaceOn(beside))
            openAbout(shell, "Zelda", keys = beside.screen())
            // On top, unless the game is there: then the screen beside it, which still takes the keys.
            assertEquals(case, beside.screen(), shell.model.gameAbout?.screen)
            assertTrue(case, shell.holdsCommandCenter())
            shell.noteSessionChanged()
            assertNotNull(case, shell.model.gameAbout)
            shell.onMeaning(Meaning.Back, beside.screen())
            assertNull(case, shell.model.gameAbout)
            assertFalse(case, shell.holdsCommandCenter())
        }
    }

    @Test
    fun aRescanThatTakesTheGameClosesThePageAndOneThatKeepsItDoesNot() {
        val shell = shell()
        openAbout(shell, "Zelda")
        val page = shell.model.gameAbout!!

        // Listed again with the game still there, the page stays.
        shell.ingestLibrary("local-folder", listOf(zeldaEntry(), entry("kart7", "Mario Kart 7", "3ds")), emptyList())
        assertEquals(page, shell.model.gameAbout)

        // Another library's copy folds the game under a new tile. The page reads that tile and stays.
        shell.ingestLibrary("romm", listOf(entry("31", "Zelda", "gba", library = "romm", fileName = "zelda.gba")), emptyList())
        assertEquals(page, shell.model.gameAbout)
        assertEquals("Zelda", shell.aboutGame(page.game)?.title)
        assertTrue(shell.tileOf(page.game) != page.game)

        // Both copies gone: no page about nothing, and the grid has its keys back.
        shell.dropLibrary("romm")
        shell.ingestLibrary("local-folder", listOf(entry("kart7", "Mario Kart 7", "3ds")), emptyList())
        assertNull(shell.model.gameAbout)
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
    }

    @Test
    fun aLaunchThatCommitsWhileThePageIsOpenClosesIt() {
        val shell = shell()
        val zelda = titles(shell).indexOf("Zelda")
        openAbout(shell, "Zelda")
        // A slow start, a download or a save, lands while the page was opened meanwhile.
        shell.noteGameLaunched("lib:local-folder:kart7")
        assertNull(shell.model.gameAbout)
        assertEquals(zelda, shell.model.focus.cellIndex)
    }

    @Test
    fun aGameTakingThePagesScreenOrTheOneBesideItClosesThePage() {
        for (layout in ScreenLayout.entries) for (onPageScreen in listOf(true, false)) {
            val store = SessionStore(MemoryPrefs())
            store.update { it.withLayout(layout) }
            val shell = shell(store)
            store.afterSessionChanged = shell::noteSessionChanged
            openAbout(shell, "Zelda", keys = layout.gridScreen)
            val screen = shell.model.gameAbout!!.screen.panel()
            // A game on the page's own screen covers it; one on the other, even Top only's dark one,
            // leaves this screen a command center.
            store.place(if (onPageScreen) screen else screen.flip(), ExternalApp("lib:local-folder:kart7", false))
            assertNull("$layout, onPageScreen=$onPageScreen", shell.model.gameAbout)
        }
        // Browsing beside a game keeps it: the grid's screen still shows the page.
        val store = SessionStore(MemoryPrefs())
        val shell = shell(store)
        store.afterSessionChanged = shell::noteSessionChanged
        store.place(Panel.Bottom, ExternalApp("lib:local-folder:kart7", false))
        store.update { it.browse(Panel.Top) }
        openAbout(shell, "Zelda")
        shell.noteSessionChanged()
        assertNotNull(shell.model.gameAbout)
        store.update { it.stopBrowsing() }
        assertNull(shell.model.gameAbout)
    }

    /** Y › About on [title], with every key from [keys]: the screen the grid takes them on. */
    private fun openAbout(shell: ShellController, title: String, keys: HostScreen = HostScreen.Bottom) {
        focus(shell, titles(shell).indexOf(title), keys)
        shell.onMeaning(Meaning.Options, keys)
        walkPanelTo(shell, rows(shell).indexOf(Row.About), keys)
        shell.onMeaning(Meaning.Activate, keys)
        assertNotNull(shell.model.gameAbout)
    }

    private fun shell(store: SessionStore = SessionStore(MemoryPrefs())): ShellController {
        store.setRommOrigin("https://romm.example")
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(LaunchableApp("com.example.asphalt", "Asphalt", systemGame = true)), emptySet())
        shell.ingestLibrary("local-folder", listOf(zeldaEntry(), entry("kart7", "Mario Kart 7", "3ds")), emptyList())
        shell.openHomeAll()
        return shell
    }

    private fun zeldaEntry(): GridEntry = entry("zelda", "Zelda", "gba", fileName = "zelda.gba")

    private fun entry(key: String, title: String, platformId: String, library: String = "local-folder", fileName: String? = null): GridEntry = GridEntry(
        id = key,
        title = title,
        shortText = platformId,
        platformId = platformId,
        availabilityLabel = null,
        occupiesBothDisplays = false,
        game = Game(backendId = library, remoteKey = key, platformId = platformId, availability = Availability.LocalOnly, label = title),
        fileName = fileName,
    )

    private fun rows(shell: ShellController): List<Row> = panelRows(shell.model.panel!!, shell.model)

    private fun titles(shell: ShellController): List<String> =
        List(shell.model.count) { index -> shell.homeFace(index)?.title.orEmpty() }

    private fun focus(shell: ShellController, index: Int, keys: HostScreen = HostScreen.Bottom) {
        assertTrue(index >= 0)
        val columns = GridStrip.column(index) - GridStrip.column(shell.model.focus.cellIndex)
        repeat(maxOf(columns, 0)) { shell.onMeaning(Meaning.MoveRight, keys) }
        repeat(maxOf(-columns, 0)) { shell.onMeaning(Meaning.MoveLeft, keys) }
        val rows = GridStrip.row(index) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(maxOf(rows, 0)) { shell.onMeaning(Meaning.MoveDown, keys) }
        repeat(maxOf(-rows, 0)) { shell.onMeaning(Meaning.MoveUp, keys) }
        assertEquals(index, shell.model.focus.cellIndex)
    }
}
