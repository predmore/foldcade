package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.plugins.duckstation.DuckStationEntry
import app.foldcade.plugins.melonds.MelonDsEntry
import app.foldcade.plugins.retroarch.RetroArchEntry
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class ShellSaveSetupTest {
    private class Rig {
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val melon = MelonDsEntry()
        val duck = DuckStationEntry()
        val notices = SaveSetupNotices()
        var closedPages = 0
        lateinit var shell: ShellController

        init {
            plugins.register(LocalFolderEntry())
            plugins.register(RetroArchEntry())
            plugins.register(melon)
            plugins.register(duck)
            shell = ShellController(
                SessionStore(MemoryPrefs()), plugins,
                onSaveHealthChanged = { players ->
                    notices.update(players)
                    shell.setNotices(notices.notices(100))
                },
                onSavePickerClosed = { closedPages++ },
                saveFolderPick = { player -> if (player.id == "melonds") FolderPick.OpensThere else FolderPick.Browse },
            )
            val games = listOf("nintendo-ds", "playstation").mapIndexed { index, platform ->
                Game("romm", index.toString(), platform, Availability.Cached, platform)
            }
            shell.ingestLibrary("romm", games.map { GridEntry(it.remoteKey, it.label, "", it.platformId, null, false, it) }, emptyList())
            val installed = (melon.players + duck.players).map { it.packageNames.first() }.toSet()
            shell.setInstalledApps(emptyList(), plugins.playerPackageNames(), installedPlayers = installed)
        }
    }

    @Test fun eachEmulatorsSettingCarriesHowItsPickerOpensAndWhetherAllFilesCouldFindIt() {
        val rig = Rig()
        val saves = rig.shell.model.playerSaves.associateBy { it.playerId }
        assertEquals(FolderPick.OpensThere, saves.getValue("melonds").pick)
        assertTrue(saves.getValue("melonds").findable)
        // DuckStation's folder is in Android/data, where all-files access never reaches.
        assertFalse(saves.getValue("duckstation").findable)
        assertEquals(rig.duck.players.filterIsInstance<HostedSaveFolder>().first().hostedFolderSteps, saves.getValue("duckstation").hostedSteps)
        assertNull(saves.getValue("melonds").hostedSteps)
    }

    @Test fun theMergedNoticeOpensThePageWhichKeepsItsKeysAndHoldsBackAConflict() {
        val rig = Rig()
        val shell = rig.shell
        val notice = shell.model.notices.single()
        assertEquals(SettingsCategory.Players, notice.settingsCategory)
        shell.onMeaning(Meaning.RightPanel, HostScreen.Top)
        val panel = shell.model.panel!!
        repeat(panelRows(panel, shell.model).indexOf(Row.Notice(notice.id)) - panel.index) { shell.onMeaning(Meaning.MoveDown, HostScreen.Top) }
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Top))
        val page = shell.model.saveSetup!!
        assertEquals(FIND_SAVES_TILE, page.focus)
        assertEquals(HomeKeys.Idle, shell.homeKeys())
        assertTrue(shell.holdsCommandCenter())
        // X skips Find saves; the next that needs you is DuckStation, A to Z.
        assertNull(shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom))
        assertEquals("duckstation", shell.model.saveSetup!!.focus)
        assertEquals(Effect.OpenPlayer("duckstation"), shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        // A conflict waits for the page, as it does for Saves.
        shell.presentSaveConflict(previewSaveConflict().copy(preview = false))
        assertNull(shell.model.saveConflict)
        assertNull(shell.onMeaning(Meaning.Back, HostScreen.Bottom))
        assertNull(shell.model.saveSetup)
        assertEquals(1, rig.closedPages)
        // Skipping quieted nothing.
        assertEquals(notice, shell.model.notices.single())
    }

    @Test fun aFolderPickedForTheFocusedTileChecksAndTheFocusGlidesOn() {
        val rig = Rig()
        val shell = rig.shell
        shell.restoreSaveSetup("melonds", HostScreen.Top)
        assertEquals("melonds", shell.model.saveSetup!!.focus)
        assertTrue(shell.noteSaveSetup("melonds", SaveSetupCopy.wrongFolder("melonDS", emptyList())))
        assertFalse(shell.noteSaveSetup("duckstation", "no"))
        val melon = rig.plugins.player("melonds") as SaveFolderHolder
        melon.bindSaveFolder("content://com.android.externalstorage.documents/tree/primary%3Asaves%2Fnds")
        shell.refreshPlayerSaves()
        val page = shell.model.saveSetup!!
        assertEquals("melonds", page.celebrating)
        assertNull(page.note)
        shell.advanceSaveSetup("melonds")
        assertEquals("duckstation", shell.model.saveSetup!!.focus)
    }

    @Test fun aPageIsBroughtBackOnlyForAProcessThatNeverHadOne() {
        val rig = Rig()
        val shell = rig.shell
        shell.restoreSaveSetup(null, HostScreen.Top)
        assertNotNull(shell.model.saveSetup)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        // A Home that outlived the page brings back a stale one: it stays closed.
        shell.restoreSaveSetup("melonds", HostScreen.Top)
        assertNull(shell.model.saveSetup)
    }

    @Test fun aRommSignInOffersThePageOnlyWhileAnEmulatorNeedsAStepAndNothingElseIsUp() {
        val rig = Rig()
        rig.shell.openAddRomm()
        rig.shell.offerSaveSetup(HostScreen.Top)
        assertNull(rig.shell.model.saveSetup)
        rig.shell.closeConnect()
        rig.shell.offerSaveSetup(HostScreen.Top)
        assertNotNull(rig.shell.model.saveSetup)

        val ready = Rig()
        (ready.plugins.player("melonds") as SaveFolderHolder).bindSaveFolder("content://com.android.externalstorage.documents/tree/primary%3Asaves%2Fnds")
        ready.duck.players.filterIsInstance<HostedSaveFolder>().forEach { it.bindHostedFolder("content://app.foldcade.saves/tree/foldcade%3Aduckstation", true) }
        ready.shell.refreshPlayerSaves()
        ready.shell.offerSaveSetup(HostScreen.Top)
        assertNull(ready.shell.model.saveSetup)
    }

    @Test fun thePickerMeetsAPlayersOwnFilesWithOnlyAllowLeft() {
        assertEquals(FolderPick.OwnProvider, saveFolderPickOf("content://aenu.aps3e.provider/root/root", opensThere = true))
        assertEquals(FolderPick.OpensThere, saveFolderPickOf(primaryFolderDocument("Android/data/com.armsx2/files"), opensThere = true))
        assertEquals(FolderPick.Browse, saveFolderPickOf(primaryFolderDocument("RetroArch/saves"), opensThere = false))
        assertEquals(FolderPick.Browse, saveFolderPickOf(null, opensThere = false))
    }
}
