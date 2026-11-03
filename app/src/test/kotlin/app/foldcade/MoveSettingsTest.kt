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
        assertFalse(ds().selectedUsable)
        assertEquals(listOf("melondsds"), ds().options.filter { it.playerId == ra.id }.map { it.core })
        shell.setInstalledApps(emptyList(), host.playerPackageNames(), setOf(MelonDsPlayer.STABLE_PACKAGE))
        assertFalse(ds().options.any { it.playerId == ra.id })
        shell.ingestLibrary("romm", listOf(entry("nintendo-ds"), entry("game-boy-advance")), host.platformDefinitions())
        shell.setInstalledApps(emptyList(), host.playerPackageNames(), setOf(pkg, MelonDsPlayer.STABLE_PACKAGE))
        ra.cores.update(mapOf(pkg to setOf("mgba_libretro_android.so", "vbam_libretro_android.so", "melonds_libretro_android.so")))
        shell.setRetroArch(RetroArchSetting(3, pkg))
        assertTrue(shell.model.systemPlayers.any { it.platformId == "game-boy-advance" })
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
