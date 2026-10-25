package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.plugins.melonds.MelonDsEntry
import app.foldcade.plugins.retroarch.RetroArchEntry
import app.foldcade.plugins.armsx2.ArmsX2Entry
import app.foldcade.plugins.aps3e.Aps3eEntry
import app.foldcade.plugins.azahar.AzaharEntry
import app.foldcade.plugins.duckstation.DuckStationEntry
import app.foldcade.plugins.melonds.MelonDsPlayer
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class ShellSaveHealthTest {
    @Test fun healthAndNoticesStayQuietUntilAnEmulatorIsKnownToBeInstalled() {
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        plugins.register(LocalFolderEntry())
        val retro = RetroArchEntry()
        val melon = MelonDsEntry()
        plugins.register(retro)
        plugins.register(melon)
        val notices = SaveSetupNotices()
        lateinit var shell: ShellController
        shell = ShellController(SessionStore(MemoryPrefs()), plugins, onSaveHealthChanged = { players ->
            notices.update(players)
            shell.setNotices(notices.notices(100))
        })
        val platforms = listOf("game-boy-advance", "snes", "nintendo-ds")
        val games = platforms.mapIndexed { index, platform -> Game("romm", index.toString(), platform, Availability.Cached, platform) }
        shell.ingestLibrary("romm", games.map { GridEntry(it.remoteKey, it.label, "", it.platformId, null, false, it) }, emptyList())
        fun assertQuiet(game: Game) {
            assertNull(shell.savePlayer(game.platformId))
            assertEquals(SaveHealth.Fine, shell.saveHealthOf(homeGameId(game.backendId, game.remoteKey)))
        }
        // The catalog may arrive before the first installed-app scan.
        games.forEach(::assertQuiet)
        assertTrue(shell.model.notices.isEmpty())
        assertTrue(shell.model.playerSaves.none { it.installed })
        shell.model.playerSaves.forEach { setting ->
            assertEquals(RowControl.Value(Copy.playerSavesUnset), rowControl(Row.PlayerSave(setting.playerId), shell.model))
        }
        val packages = plugins.playerPackageNames()
        shell.setInstalledApps(emptyList(), packages, installedPlayers = emptySet())
        games.forEach(::assertQuiet)
        assertTrue(shell.model.notices.isEmpty())

        val melonPackage = melon.players.single().packageNames.first()
        shell.setInstalledApps(emptyList(), packages, installedPlayers = setOf(melonPackage))
        games.take(2).forEach(::assertQuiet)
        assertEquals("melonds", shell.model.notices.single().savePlayerId)
        assertEquals(SaveHealth.NotSyncing(SaveIssue.NoFolder), shell.saveHealthOf(homeGameId("romm", "2")))
        val retroSetting = shell.model.playerSaves.first { it.label == "RetroArch" }
        assertFalse(retroSetting.installed)
        assertEquals(RowControl.Value(Copy.playerSavesUnset), rowControl(Row.PlayerSave(retroSetting.playerId), shell.model))

        val retroPackage = retro.players.first().packageNames.first()
        shell.setInstalledApps(emptyList(), packages, installedPlayers = setOf(melonPackage, retroPackage))
        games.forEach {
            assertEquals(SaveHealth.NotSyncing(SaveIssue.NoFolder), shell.saveHealthOf(homeGameId(it.backendId, it.remoteKey)))
        }
        assertEquals(Copy.savesNotSyncingEmulatorsBody(2), shell.model.notices.single().body)
        assertTrue(shell.model.playerSaves.first { it.label == "RetroArch" }.installed)

        // An uninstall removes it from the merged notice and clears its cached tile health.
        shell.setInstalledApps(emptyList(), packages, installedPlayers = setOf(melonPackage))
        games.take(2).forEach(::assertQuiet)
        assertEquals("melonds", shell.model.notices.single().savePlayerId)
        shell.setInstalledApps(emptyList(), packages, installedPlayers = emptySet())
        games.forEach(::assertQuiet)
        assertTrue(shell.model.notices.isEmpty())
    }

    @Test fun hostedSetupIsTheOnlyStandingIssueWithoutADuckStationDataFolder() {
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        plugins.register(LocalFolderEntry())
        val duck = DuckStationEntry()
        plugins.register(duck)
        var setupPlayers = emptyMap<String, String>()
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins, onSaveHealthChanged = { setupPlayers = it })
        shell.setInstalledApps(emptyList(), plugins.playerPackageNames(), plugins.playerPackageNames())
        val game = Game("romm", "42", "playstation", Availability.Cached, "Game")
        shell.ingestLibrary("romm", listOf(GridEntry(game.remoteKey, game.label, "", game.platformId, null, false, game)), emptyList())
        val id = homeGameId(game.backendId, game.remoteKey)
        assertEquals(SaveHealth.NotSyncing(SaveIssue.HostedFolder), shell.saveHealthOf(id))
        assertEquals(mapOf(duck.player.id to duck.player.displayName), setupPlayers)
        duck.player.bindHostedFolder("content://hosted", true)
        shell.refreshPlayerSaves()
        assertEquals(SaveHealth.Fine, shell.saveHealthOf(id))
        assertTrue(setupPlayers.isEmpty())
        assertFalse(shell.model.playerSaves.single().chosen)
    }

    @Test fun aLargeCatalogChecksPlayerSetupOncePerPlatformAndSkipsUnchangedResumeRefreshes() {
        val player = CountingSavePlayer()
        val plugins = countingPlugins(player)
        var scans = 0
        var setupPlayers = emptyMap<String, String>()
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins, onSaveHealthChanged = {
            scans += 1
            setupPlayers = it
        })
        shell.setInstalledApps(emptyList(), plugins.playerPackageNames(), plugins.playerPackageNames())
        shell.refreshPlayerSaves()
        player.folderChecks = 0
        val games = List(2_000) { Game("romm", it.toString(), player.platformId, Availability.Cached, "Game $it") }
        shell.ingestLibrary("romm", games.map {
            GridEntry(it.remoteKey, it.label, "", it.platformId, null, true, it)
        }, emptyList())
        assertEquals(mapOf(player.id to player.displayName), setupPlayers)
        assertTrue("Setup checks must not grow with the catalog: ${player.folderChecks}", player.folderChecks <= 4)
        assertEquals(0, player.declarationReads) // Standing notices never ask for per-game slots.
        val scanned = scans
        repeat(4) { shell.refreshPlayerSaves() }
        assertEquals(scanned, scans)
        shell.setUnsyncedSaves("romm", setOf("0"))
        shell.noteSaveReachable(games.first(), player.id, false)
        assertEquals(scanned, scans) // Waiting and NoSlot only change game state.
    }

    @Test fun tileAndHeroReuseGameDeclarationsUntilSaveConfigurationChanges() {
        val player = CountingSavePlayer()
        player.bindSaveFolder("content://first")
        val store = SessionStore(MemoryPrefs()).apply { setPlayerSaveFolder(player.id, "content://first") }
        val plugins = countingPlugins(player)
        val shell = ShellController(store, plugins)
        shell.setInstalledApps(emptyList(), plugins.playerPackageNames(), plugins.playerPackageNames())
        shell.refreshPlayerSaves()
        val games = List(2) { Game("romm", it.toString(), player.platformId, Availability.Cached, "Game $it") }
        shell.ingestLibrary("romm", games.map {
            GridEntry(it.remoteKey, it.label, "", it.platformId, null, true, it)
        }, emptyList())
        val game = games.first()
        val id = homeGameId(game.backendId, game.remoteKey)
        val shown = ShelfGame(id, game.label, "", platformId = game.platformId)
        assertEquals(SaveHealth.Fine, shell.saveHealthAt(0, shown))
        val reads = player.declarationReads
        assertTrue(reads > 0)
        repeat(100) {
            assertEquals(SaveHealth.Fine, shell.saveHealthAt(0, shown))
            assertEquals(SaveHealth.Fine, shell.saveHealthOf(id))
        }
        assertEquals(reads, player.declarationReads)
        shell.saveHealthOf(homeGameId("romm", "1"))
        assertTrue(player.declarationReads > reads)
        shell.noteSaveReachable(game, player.id, false)
        assertEquals(SaveHealth.NotSyncing(SaveIssue.NoSlot), shell.saveHealthOf(id))
        player.bindSaveFolder("content://second")
        store.setPlayerSaveFolder(player.id, "content://second")
        shell.refreshPlayerSaves() // Folder changed even though the row still reads Folder set.
        assertEquals(SaveHealth.Fine, shell.saveHealthOf(id))
        shell.setSaveSync(emptyMap(), mapOf(player.id to "Finish setup"))
        assertEquals(SaveHealth.NotSyncing(SaveIssue.NeedsSetup), shell.saveHealthOf(id))
        shell.setSaveSync(emptyMap(), emptyMap())
        assertEquals(SaveHealth.Fine, shell.saveHealthOf(id))
    }

    private class CountingSavePlayer(private val delegate: MelonDsPlayer = MelonDsPlayer()) :
        Player by delegate, SaveFolderHolder by delegate {
        var declarationReads = 0
        var folderChecks = 0
        override fun saveDeclarations(game: Game): List<SaveDeclaration> {
            declarationReads += 1
            return delegate.saveDeclarations(game)
        }
        override fun hasSaveFolder(): Boolean {
            folderChecks += 1
            return delegate.hasSaveFolder()
        }
    }

    private fun countingPlugins(player: Player) = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
        register(LocalFolderEntry())
        register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val apiMinor = PLUGIN_API_MINOR
            override val players = listOf(player)
        })
    }

    @Test fun unavailableGameSlotsKeepTheirBadgeWithoutAnEmulatorSetupNotice() {
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        plugins.register(LocalFolderEntry())
        plugins.register(ArmsX2Entry())
        plugins.register(Aps3eEntry())
        plugins.register(AzaharEntry())
        var setupPlayers = emptyMap<String, String>()
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins, onSaveHealthChanged = { setupPlayers = it })
        shell.setInstalledApps(emptyList(), plugins.playerPackageNames(), plugins.playerPackageNames())
        val games = plugins.playerIds().mapIndexed { index, id ->
            val player = plugins.player(id)!!
            (player as SaveFolderHolder).bindSaveFolder("content://$id")
            Game("romm", index.toString(), player.platformId, Availability.Cached, "Game $index")
        }
        shell.ingestLibrary("romm", games.map { game ->
            GridEntry(game.remoteKey, game.label, "", game.platformId, null, false, game)
        }, emptyList())
        for (game in games) {
            val player = shell.savePlayer(game.platformId)!!
            shell.noteSaveReachable(game, player.id, false)
            val health = shell.saveHealthOf(homeGameId(game.backendId, game.remoteKey))
            assertEquals(SaveHealth.NotSyncing(SaveIssue.NoSlot), health)
            assertTrue(health.needsAttention)
            assertEquals(Copy.savesNotSyncing, saveHealthLine(health))
            assertTrue(setupPlayers.isEmpty())
        }
        val arms = shell.savePlayer("playstation-2")!!
        shell.setSaveSync(mapOf(arms.id to SaveSyncState.NeedsSetup("Choose a folder card")))
        assertEquals(mapOf(arms.id to arms.displayName), setupPlayers)
    }

    @Test fun addingAnUnconfiguredServerGameToHomeKeepsItsFreshSetupNotice() {
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        plugins.register(LocalFolderEntry())
        plugins.register(MelonDsEntry())
        val notices = SaveSetupNotices()
        lateinit var shell: ShellController
        shell = ShellController(SessionStore(MemoryPrefs()), plugins, onSaveHealthChanged = { players ->
            notices.update(players)
            shell.setNotices(notices.notices(100))
        })
        shell.setInstalledApps(emptyList(), plugins.playerPackageNames(), plugins.playerPackageNames())
        shell.applyHomeAddNew(false)
        val game = Game("romm", "42", "nintendo-ds", Availability.Cached, "New game")
        shell.ingestLibrary("romm", listOf(GridEntry(game.remoteKey, game.label, "", game.platformId, null, true, game)), emptyList())
        assertTrue(shell.model.notices.isEmpty())
        shell.openHomeAll()
        val index = (0 until shell.model.count).single { shell.homeFace(it)?.title == game.label }
        shell.touchCell(index, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        fun activate(row: Row) {
            val panel = shell.model.panel!!
            val rowIndex = panelRows(panel, shell.model).indexOf(row)
            assertTrue(rowIndex >= 0)
            if (panel.index != rowIndex) shell.touchPanel(rowIndex, HostScreen.Bottom)
            shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        }
        activate(Row.AddToCollection)
        val home = Row.Collection(HOME_ROOT, Copy.home)
        activate(home)
        assertEquals("melonds", shell.model.notices.single().savePlayerId)
    }

    @Test fun retroArchSystemsShareOneStandingNoticeAndItsSettingsFix() {
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        plugins.register(LocalFolderEntry())
        val retro = RetroArchEntry()
        plugins.register(retro)
        var setupPlayers = emptyMap<String, String>()
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins, onSaveHealthChanged = { setupPlayers = it })
        shell.setInstalledApps(emptyList(), plugins.playerPackageNames(), plugins.playerPackageNames())
        val players = retro.players.take(2)
        val entries = players.mapIndexed { index, player ->
            val game = Game("romm", index.toString(), player.platformId, Availability.RemoteOnly, "Game $index")
            GridEntry(game.remoteKey, game.label, "", game.platformId, null, false, game, "game$index.rom")
        }
        shell.ingestLibrary("romm", entries, emptyList())
        val setting = shell.model.playerSaves.single()
        assertEquals(mapOf(setting.playerId to setting.label), setupPlayers)
        for (player in players) assertEquals(setting.playerId, shell.saveNoticePlayerId(player.id))
    }

    @Test fun healthFollowsFoldedCopiesAndSurvivesTransientMarksThenClearsOnUpload() {
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        plugins.register(LocalFolderEntry())
        val melon = MelonDsEntry()
        plugins.register(melon)
        var setupPlayers = emptyMap<String, String>()
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins, onSaveHealthChanged = { setupPlayers = it })
        shell.setInstalledApps(emptyList(), plugins.playerPackageNames(), plugins.playerPackageNames())
        val player = plugins.playersFor("nintendo-ds").first()
        val server = Game("romm", "42", "nintendo-ds", Availability.Cached, "Tetris")
        val other = server.copy(remoteKey = "43", label = "Puzzle")
        val local = server.copy(backendId = "local-folder", remoteKey = "tree", availability = Availability.LocalOnly)
        fun entry(game: Game, file: String) = GridEntry(game.remoteKey, game.label, "", game.platformId, null, true, game, file)
        shell.ingestLibrary("romm", listOf(entry(server, "Tetris.nds"), entry(other, "Puzzle.nds")), emptyList())
        shell.ingestLibrary("local-folder", listOf(entry(local, "Tetris.nds")), emptyList())
        val id = homeGameId("local-folder", "tree")
        assertEquals(SaveHealth.NotSyncing(SaveIssue.NoFolder), shell.saveHealthOf(id))
        assertEquals(mapOf(player.id to player.displayName), setupPlayers) // Two server games, one player notice.
        shell.noteSaveReachable(server, player.id, false)
        (player as SaveFolderHolder).bindSaveFolder("content://saves")
        shell.refreshPlayerSaves()
        assertEquals(SaveHealth.Fine, shell.saveHealthOf(id))
        assertTrue(setupPlayers.isEmpty())
        shell.setUnsyncedSaves("romm", setOf("42"))
        assertEquals(SaveHealth.Waiting, shell.saveHealthOf(id))
        val until = shell.noteActivity("romm", "42", GameActivity.Synced(SyncResult(SyncOutcome.Queued)), 100)!!
        assertEquals(SaveHealth.Waiting, shell.saveHealthOf(id))
        shell.expireActivity("romm", "42", until)
        assertEquals(SaveHealth.Waiting, shell.saveHealthOf(id))
        shell.setUnsyncedSaves("romm", emptySet())
        assertEquals(SaveHealth.Fine, shell.saveHealthOf(id))
        shell.noteSaveReachable(server, player.id, false)
        assertEquals(SaveHealth.NotSyncing(SaveIssue.NoSlot), shell.saveHealthOf(id))
        shell.noteSaveReachable(server, player.id, true)
        assertEquals(SaveHealth.Fine, shell.saveHealthOf(id))
    }

    @Test fun aSaveWaitingForTheServerStaysVisibleAfterItsEmulatorIsUninstalled() {
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        plugins.register(LocalFolderEntry())
        plugins.register(MelonDsEntry())
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins)
        shell.setInstalledApps(emptyList(), plugins.playerPackageNames(), plugins.playerPackageNames())
        val game = Game("romm", "42", "nintendo-ds", Availability.Cached, "Tetris")
        shell.ingestLibrary("romm", listOf(GridEntry(game.remoteKey, game.label, "", game.platformId, null, false, game)), emptyList())
        val id = homeGameId("romm", "42")
        shell.setUnsyncedSaves("romm", setOf("42"))
        shell.setInstalledApps(emptyList(), plugins.playerPackageNames(), installedPlayers = emptySet())
        assertNull(shell.savePlayer(game.platformId))
        assertEquals(SaveHealth.Waiting, shell.saveHealthOf(id))
        shell.setUnsyncedSaves("romm", emptySet())
        assertEquals(SaveHealth.Fine, shell.saveHealthOf(id))
    }
}
