package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.plugins.melonds.MelonDsEntry
import app.foldcade.plugins.melonds.MelonDsPlayer
import app.foldcade.plugins.retroarch.RetroArchEntry
import app.foldcade.plugins.retroarch.RetroArchPlayer
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class ShellPlayerChoiceTest {
    private val prefs = MemoryPrefs()
    private val store = SessionStore(prefs)
    private val retro = RetroArchEntry()
    private val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
        register(LocalFolderEntry())
        register(MelonDsEntry())
        register(retro)
    }
    private val ra = retro.players.filterIsInstance<RetroArchPlayer>().first { it.platformId == "nintendo-ds" }
    private val selected = PlayerChoice(ra.id, "melonds", "melonDS (RetroArch)")
    private val game = Game("romm", "42", "nintendo-ds", Availability.Cached, "Tetris")
    private val other = game.copy(remoteKey = "99", label = "Mario")
    private val id = homeGameId(game.backendId, game.remoteKey)
    private val shell = ShellController(store, host).apply {
        setInstalledApps(emptyList(), host.playerPackageNames(), setOf(PlayerChoiceTest.PKG, MelonDsPlayer.STABLE_PACKAGE))
        ra.cores.update(mapOf(PlayerChoiceTest.PKG to setOf("melondsds_libretro_android.so", "melonds_libretro_android.so")))
        ingestLibrary("romm", listOf(cell(game), cell(other)), emptyList())
    }

    private fun cell(game: Game, fileName: String? = null) = GridEntry(game.remoteKey, game.label, "", game.platformId, null, false, game, fileName)
    private fun openWithRow() {
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val row = panelRows(shell.model.panel!!, shell.model).indexOf(Row.OpenWith)
        assertTrue(row >= 0)
        walkPanelTo(shell, row, shell.model.panel!!.screen)
    }

    @Test fun gameAndSystemChoicesDriveGridScreensAndSaveHealthPlayer() {
        store.setSystemPlayerChoice("nintendo-ds", selected)
        shell.showPlatforms("romm", emptyList())
        shell.showGames(listOf(cell(game), cell(other)))
        assertFalse(shell.focusedGame()!!.occupiesBothDisplays)
        assertEquals("melonds", (shell.savePlayer(GameRoute(id, game, game)) as RetroArchPlayer).pinnedCore)
        store.setGamePlayerChoice(id, PlayerChoice("melonds", label = "melonDS"))
        shell.showGames(listOf(cell(game), cell(other)))
        assertTrue(shell.focusedGame()!!.occupiesBothDisplays)
        assertEquals("melonds", shell.savePlayer(GameRoute(id, game, game))!!.id)
    }

    @Test fun touchAndControllerPersistOnTheMenusTargetAfterTheGridChanges() {
        shell.showPlatforms("romm", emptyList())
        shell.showGames(listOf(cell(game), cell(other)))
        openWithRow()
        // The focused slot now contains Mario. The open menu continues to name Tetris.
        shell.ingestLibrary("romm", listOf(cell(other), cell(game)), emptyList())
        assertEquals(id, shell.model.tileActions!!.game)
        val row = panelRows(shell.model.panel!!, shell.model).indexOf(Row.OpenWith)
        shell.touchPanel(row, shell.model.panel!!.screen)
        assertEquals("melonds", SessionStore(prefs).gamePlayerChoice(id)!!.playerId)
        assertNull(store.gamePlayerChoice(homeGameId(other.backendId, other.remoteKey)))
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertEquals("melondsds", SessionStore(prefs).gamePlayerChoice(id)!!.core)
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertEquals(selected, SessionStore(prefs).gamePlayerChoice(id))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom, repeatCount = 1)
        assertEquals(selected, store.gamePlayerChoice(id))
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertNull(store.gamePlayerChoice(id))
    }

    @Test fun foldedCopiesFindTheirSavedChoiceAndDefaultClearsTheCopyToo() {
        store.setGamePlayerChoice(id, selected)
        shell.ingestLibrary("romm", listOf(cell(game, "Tetris.nds")), emptyList())
        val local = game.copy(backendId = "local-folder", remoteKey = "local", availability = Availability.LocalOnly)
        shell.ingestLibrary("local-folder", listOf(cell(local, "Tetris.nds")), emptyList())
        val tile = shell.tileOf(id)
        assertTrue(tile.startsWith(SAME_PREFIX))
        assertEquals(selected, shell.gamePlayerChoice(tile))
        assertEquals(selected, shell.gamePlayerChoice(homeGameId(local.backendId, local.remoteKey)))
        shell.showPlatforms("romm", emptyList())
        shell.showGames(listOf(cell(game)))
        openWithRow()
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertNull(shell.gamePlayerChoice(tile))
        assertNull(SessionStore(prefs).gamePlayerChoice(id))
    }

    @Test fun runningGameKeepsItsSavePlayerAfterThePreferenceChanges() {
        val launched = ra.withCore("melonds")
        shell.runningPlayer = { tile -> launched.takeIf { tile == id } }
        store.setGamePlayerChoice(id, PlayerChoice("melonds", label = "melonDS"))
        assertSame(launched, shell.savePlayer(GameRoute(id, game, game)))
        shell.showPlatforms("romm", emptyList())
        shell.showGames(listOf(cell(game)))
        assertFalse(shell.focusedGame()!!.occupiesBothDisplays)
    }

    @Test fun systemsIncludeGamesListedBeforeTheHomeCatalogArrives() {
        val listing = ShellController(store, host)
        listing.setInstalledApps(emptyList(), host.playerPackageNames(), host.playerPackageNames())
        listing.showPlatforms("romm", emptyList())
        listing.showGames(listOf(cell(game)))
        assertEquals(listOf("nintendo-ds"), listing.model.systemPlayers.map { it.platformId })
    }

    @Test fun systemsOnlyListLibraryPlatformsWithChoicesAndSettingsWritesPersist() {
        assertEquals(listOf("nintendo-ds"), shell.model.systemPlayers.map { it.platformId })
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        repeat(leftRows().indexOf(Row.Settings)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        val category = settingsCategories(shell.model).indexOf(SettingsCategory.Players)
        shell.touchSettingsCategory(category, shell.model.settings!!.screen)
        val systems = settingsRows(SettingsCategory.Players, shell.model).indexOf(Row.Systems)
        shell.touchSettingsRow(systems, shell.model.settings!!.screen)
        assertEquals(SettingsInner.Systems, shell.model.settings!!.inner)
        shell.onMeaning(Meaning.MoveLeft, HostScreen.Bottom)
        assertEquals(selected, SessionStore(prefs).systemPlayerChoice("nintendo-ds"))
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertNull(SessionStore(prefs).systemPlayerChoice("nintendo-ds"))
        // A platform alias reads the same canonical system setting.
        store.setSystemPlayerChoice("nintendo-ds", selected)
        assertEquals(PlayerChoiceSource.System, shell.playerResolution(id, "nds").source)
    }
}
