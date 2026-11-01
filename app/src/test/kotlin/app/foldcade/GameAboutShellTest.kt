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
import app.foldcade.language.panelRows
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Y › About from the grid: only a library's games have it, and a held key stays its own. */
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

    private fun shell(): ShellController {
        val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(LaunchableApp("com.example.asphalt", "Asphalt", systemGame = true)), emptySet())
        shell.ingestLibrary("local-folder", listOf(entry("zelda", "Zelda", "gba"), entry("kart7", "Mario Kart 7", "3ds")), emptyList())
        shell.openHomeAll()
        return shell
    }

    private fun entry(key: String, title: String, platformId: String): GridEntry = GridEntry(
        id = key,
        title = title,
        shortText = platformId,
        platformId = platformId,
        availabilityLabel = null,
        occupiesBothDisplays = false,
        game = Game(backendId = "local-folder", remoteKey = key, platformId = platformId, availability = Availability.LocalOnly, label = title),
    )

    private fun rows(shell: ShellController): List<Row> = panelRows(shell.model.panel!!, shell.model)

    private fun titles(shell: ShellController): List<String> =
        List(shell.model.count) { index -> shell.homeFace(index)?.title.orEmpty() }

    private fun focus(shell: ShellController, index: Int) {
        assertTrue(index >= 0)
        val columns = GridStrip.column(index) - GridStrip.column(shell.model.focus.cellIndex)
        repeat(maxOf(columns, 0)) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        repeat(maxOf(-columns, 0)) { shell.onMeaning(Meaning.MoveLeft, HostScreen.Bottom) }
        val rows = GridStrip.row(index) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(maxOf(rows, 0)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        repeat(maxOf(-rows, 0)) { shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom) }
        assertEquals(index, shell.model.focus.cellIndex)
    }
}
