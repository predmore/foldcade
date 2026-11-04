package app.foldcade

import app.foldcade.api.plugin.*
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

class MoveSettingsTest {
    private val store = SessionStore(MemoryPrefs())
    private val melon = MelonDsEntry()
    private val retro = RetroArchEntry()
    private val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
        register(LocalFolderEntry()); register(melon); register(retro)
    }
    private val pkg = PlayerChoiceTest.PKG
    private val ra = retro.players.filterIsInstance<RetroArchPlayer>().first { it.platformId == "nintendo-ds" }
    private val shell = ShellController(store, host).apply {
        setInstalledApps(emptyList(), host.playerPackageNames(), setOf(pkg, MelonDsPlayer.STABLE_PACKAGE))
        ingestLibrary("romm", listOf(entry("nintendo-ds")), host.platformDefinitions())
    }
    private fun entry(platform: String) = Game("romm", platform, platform, Availability.Cached, platform).let {
        GridEntry(it.remoteKey, it.label, "", platform, null, false, it)
    }
    private fun open() { shell.onMeaning(Meaning.Menu, HostScreen.Top); assertNotNull(shell.model.settings) }
    private fun ds() = shell.model.systemPlayers.single { it.platformId == "nintendo-ds" }.choice

    @Test fun systemChoicesReadCurrentCatalogInstallsPreferencesAndCoreFilesWhenOpened() {
        assertTrue(shell.model.systemPlayers.isEmpty())
        open()
        assertTrue(ds().options.any { it.playerId == melon.player.id })
        shell.closeSettings()
        store.setSystemPlayerChoice("nintendo-ds", PlayerChoice(ra.id, "melonds", "melonDS (RetroArch)"))
        ra.cores.update(mapOf(pkg to setOf("melonds_libretro_android.so")))
        open()
        assertEquals("melonds", ds().selected!!.core)
        assertTrue(ds().selectedUsable)
        assertEquals(listOf("melonds"), ds().options.filter { it.playerId == ra.id }.map { it.core })
        // A rescan while the page stays open must update its options too.
        ra.cores.update(mapOf(pkg to setOf("melondsds_libretro_android.so")))
        shell.setRetroArch(RetroArchSetting(1, pkg))
        assertTrue(ds().selectedUsable) // A missing selected core is fetched on launch.
        assertEquals(listOf("melondsds"), ds().options.filter { it.playerId == ra.id }.map { it.core })
        shell.setInstalledApps(emptyList(), host.playerPackageNames(), setOf(MelonDsPlayer.STABLE_PACKAGE))
        assertFalse(ds().options.any { it.playerId == ra.id })
        shell.ingestLibrary("romm", listOf(entry("nintendo-ds"), entry("game-boy-advance")), host.platformDefinitions())
        shell.setInstalledApps(emptyList(), host.playerPackageNames(), setOf(pkg, MelonDsPlayer.STABLE_PACKAGE))
        ra.cores.update(mapOf(pkg to setOf("mgba_libretro_android.so", "vbam_libretro_android.so", "melonds_libretro_android.so")))
        shell.setRetroArch(RetroArchSetting(3, pkg))
        assertTrue(shell.model.systemPlayers.any { it.platformId == "game-boy-advance" })
    }

    private fun openSystemNotice(platform: String) {
        val notice = FoldNotice(PLAYER_CHOICE_SYSTEM_NOTICE + platform, "Opened another emulator", "Choose an emulator", settingsCategory = SettingsCategory.Players)
        shell.setNotices(listOf(notice))
        shell.onMeaning(Meaning.RightPanel, HostScreen.Top)
        val panel = shell.model.panel!!
        walkPanelTo(shell, panelRows(panel, shell.model).indexOf(Row.Notice(notice.id)), panel.screen)
        shell.onMeaning(Meaning.Activate, HostScreen.Top)
        assertEquals(SettingsCategory.Players, settingsCategories(shell.model)[shell.model.settings!!.category])
        assertEquals(SettingsInner.Systems, shell.model.settings!!.inner)
        assertEquals(Row.SystemPlayer(platform), settingsFocus(shell.model))
    }

    @Test fun fallbackNoticeFindsItsSystemBeforeSettingsHasEverOpenedAndWithNoPlayersInstalled() {
        store.setSystemPlayerChoice("nintendo-ds", PlayerChoice(ra.id, "melonds", "melonDS (RetroArch)"))
        shell.setInstalledApps(emptyList(), host.playerPackageNames(), emptySet())
        assertTrue(shell.model.systemPlayers.isEmpty())
        openSystemNotice("nintendo-ds")
    }

    @Test fun fallbackNoticeFindsItsSystemAfterAnEarlierSortingSystemArrives() {
        open()
        shell.closeSettings()
        val gba = retro.players.first { it.platformId == "game-boy-advance" }
        store.setSystemPlayerChoice("game-boy-advance", PlayerChoice(gba.id, label = gba.displayName))
        shell.ingestLibrary("romm", listOf(entry("nintendo-ds"), entry("game-boy-advance")), host.platformDefinitions())
        assertEquals(listOf("nintendo-ds"), shell.model.systemPlayers.map { it.platformId })
        openSystemNotice("nintendo-ds")
    }

    @Test fun restoredSaveSetupFindSavesOpensItsSettingsRowBeforeSettingsHasEverOpened() {
        for (libraryGrid in listOf(false, true)) {
            val cold = ShellController(SessionStore(MemoryPrefs()), host)
            cold.setInstalledApps(emptyList(), host.playerPackageNames(), setOf(pkg, MelonDsPlayer.STABLE_PACKAGE))
            cold.ingestLibrary("romm", listOf(entry("nintendo-ds")), host.platformDefinitions())
            if (libraryGrid) {
                cold.showPlatforms("romm", emptyList())
                cold.showGames(listOf(entry("nintendo-ds")))
            }
            assertTrue(cold.model.systemPlayers.isEmpty())
            cold.restoreSaveSetup(FIND_SAVES_TILE, HostScreen.Top)
            assertEquals(FIND_SAVES_TILE, cold.model.saveSetup!!.focus)
            cold.setAllFilesGranted(true)
            // A newly completed step celebrates first. Revisit its now-done tile to open its Settings.
            cold.onMeaning(Meaning.MoveRight, HostScreen.Top)
            cold.onMeaning(Meaning.MoveLeft, HostScreen.Top)
            assertEquals(FIND_SAVES_TILE, cold.model.saveSetup!!.focus)
            cold.onMeaning(Meaning.Activate, HostScreen.Top)
            assertNull(cold.model.saveSetup)
            assertEquals(SettingsCategory.Players, settingsCategories(cold.model)[cold.model.settings!!.category])
            assertEquals(Row.FindSaves, settingsFocus(cold.model))
        }
    }

    @Test fun saveRowsReuseWorkButRefreshOnSetupSyncFolderAndInstallChanges() {
        val before = shell.model.playerSaves
        repeat(4) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        assertSame(before, shell.model.playerSaves)
        melon.player.bindSaveFolder("content://saves/tree/nds")
        store.setPlayerSaveFolder(melon.player.id, "content://saves/tree/nds")
        shell.refreshPlayerSaves()
        assertTrue(shell.model.playerSaves.single { it.playerId == melon.player.id }.chosen)
        shell.setSaveSync(mapOf(melon.player.id to SaveSyncState.NeedsSetup("Set up sync")), mapOf(melon.player.id to "Finish emulator setup"))
        val row = shell.model.playerSaves.single { it.playerId == melon.player.id }
        assertEquals("Set up sync", row.saveSyncSteps)
        assertEquals("Finish emulator setup", row.setupSteps)
        shell.setSaveSync(mapOf(melon.player.id to SaveSyncState.Syncing), emptyMap())
        assertTrue(shell.model.playerSaves.single { it.playerId == melon.player.id }.saveSyncing)
        assertNull(shell.model.playerSaves.single { it.playerId == melon.player.id }.setupSteps)
        shell.setInstalledApps(emptyList(), host.playerPackageNames(), emptySet())
        assertTrue(shell.model.playerSaves.none { it.installed })
        assertTrue(shell.model.playerSaves.all { it.packages.isEmpty() })
    }

    @Test fun pluginRegistrationInvalidatesRowsEvenWhenItDeclaresNoPlatforms() {
        val lateHost = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply { register(LocalFolderEntry()) }
        val lateShell = ShellController(SessionStore(MemoryPrefs()), lateHost)
        lateShell.setInstalledApps(emptyList(), emptySet(), setOf(MelonDsPlayer.STABLE_PACKAGE))
        assertTrue(lateShell.model.playerSaves.isEmpty())
        lateHost.register(MelonDsEntry())
        lateShell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertTrue(lateShell.model.playerSaves.single().installed)
        lateShell.ingestLibrary("romm", listOf(entry("nintendo-ds")), lateHost.platformDefinitions())
        lateHost.register(RetroArchEntry())
        lateShell.setInstalledApps(emptyList(), lateHost.playerPackageNames(), lateHost.playerPackageNames())
        lateShell.onMeaning(Meaning.Menu, HostScreen.Top)
        assertTrue(lateShell.model.systemPlayers.single().choice.options.any { it.playerId == "melonds" })
    }
}
