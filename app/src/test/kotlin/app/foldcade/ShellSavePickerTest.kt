package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.artwork.*
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.plugins.melonds.MelonDsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShellSavePickerTest {
    @get:Rule val temp = TemporaryFolder()
    private val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
        register(LocalFolderEntry()); register(MelonDsEntry())
    }
    private val shell = ShellController(SessionStore(MemoryPrefs()), plugins).apply {
        setInstalledApps(emptyList(), plugins.playerPackageNames(), plugins.playerPackageNames())
    }
    private val game = Game("romm", "3", "nintendo-ds", Availability.Cached, "Original")
    private fun entry(game: Game) = GridEntry(game.remoteKey, game.label, "", game.platformId, null, true, game)
    private fun picker(session: Long = 1) = SavePickerState(SavePickerTarget(homeGameId("romm", "3"), "romm", "3", "melonds", session), "Original", "melonDS", SaveHealth.NotSyncing(SaveIssue.NoFolder), loading = false)

    @Test fun coverFallbackUsesTheOriginalGamesHeroArtAndItsChosenCoverAfterFocusMoves() = runBlocking {
        shell.ingestLibrary("romm", listOf(entry(game), entry(game.copy(remoteKey = "4", label = "Other"))), emptyList())
        shell.openHomeAll()
        val original = shell.homeTileOf(picker().target.gameId)
        val hero = shell.runningApp(original)
        val other = (0 until shell.model.count).single { shell.homeFace(it)?.title == "Other" }
        shell.touchCell(other, HostScreen.Bottom)
        shell.presentSavePicker(picker())
        assertEquals("Other", shell.focusedGame()?.title)
        val art = shell.savePickerArt(picker().target)!!
        assertEquals(hero.record, art.record)
        assertEquals(hero.query, art.query)
        val source = object : ArtSource {
            override val id = "covers"
            override fun handles(query: ArtQuery) = true
            override suspend fun find(query: ArtQuery) = ArtSet(id, "https://covers/${query.title}")
        }
        var choices = ArtChoices()
        val covers = CoverArt(plugins, ArtFinder({ listOf(source) }, ArtIndex(temp.newFile()))) { choices }
        assertEquals("https://covers/Original", covers.fetch(art.record, art.query)?.cover)
        assertEquals(covers.cached(hero.record, hero.query)?.cover, covers.cached(art.record, art.query)?.cover)
        choices = choices.set(hero.query!!.key, ArtKind.Tile, "https://chosen/Original")
        assertEquals("https://chosen/Original", covers.cached(art.record, art.query)?.cover)
    }

    @Test fun libraryOnlyPickerArtUsesItsNamedGameBeforeItEntersTheHomeCatalog() {
        shell.showPlatforms("romm", emptyList())
        shell.showGames(listOf(entry(game), entry(game.copy(remoteKey = "99", label = "Other"))))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Effect.OpenSaves(homeGameId("romm", "3")), shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        shell.presentSavePicker(picker())
        val art = shell.savePickerArt(picker().target)!!
        assertEquals(game, art.record)
        assertEquals(ArtQuery(homeGameId("romm", "3"), "Original", platformId = game.platformId), art.query)
    }

    @Test fun closingThePickerRefreshesAConflictThatItHadCovered() {
        var refreshed = 0
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins, onSavePickerClosed = { refreshed++ })
        shell.ingestLibrary("romm", listOf(entry(game)), emptyList())
        shell.presentSavePicker(picker())
        val conflict = previewSaveConflict().copy(preview = false)
        shell.presentSaveConflict(conflict)
        assertNull(shell.model.saveConflict)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertEquals(1, refreshed)
        shell.presentSaveConflict(conflict)
        assertEquals(conflict, shell.model.saveConflict)
        shell.presentSavePicker(picker())
        shell.presentSavePicker(null)
        assertEquals(2, refreshed)
    }

    @Test fun theConfirmationKnowsAnOpenGameWillRestart() {
        shell.ingestLibrary("romm", listOf(entry(game)), emptyList())
        shell.running = setOf(homeGameId("romm", "3"))
        shell.presentSavePicker(picker())
        assertTrue(shell.model.savePicker!!.restartsOpenGame)
    }

    @Test fun homeAndAllOfferSavesForRemoteAndDownloadedGamesButNotLocalOnes() {
        shell.ingestLibrary("romm", listOf(entry(game), entry(game.copy(remoteKey = "4", label = "Remote", availability = Availability.RemoteOnly))), emptyList())
        shell.ingestLibrary("local-folder", listOf(entry(game.copy(backendId = "local-folder", remoteKey = "file", label = "Local", availability = Availability.LocalOnly))), emptyList())
        shell.openHomeAll()
        for ((title, offers) in listOf("Original" to true, "Remote" to true, "Local" to false)) {
            val index = (0 until shell.model.count).single { shell.homeFace(it)?.title == title }
            shell.touchCell(index, HostScreen.Bottom)
            assertEquals(title, offers, shell.model.tileActions?.saves == true)
        }
    }

    @Test fun aLibraryMenuKeepsItsNamedGameWhileTheCoveredGridRejectsTouches() {
        shell.showPlatforms("romm", emptyList())
        shell.showGames(listOf(entry(game), entry(game.copy(remoteKey = "99", label = "Other"))))
        assertTrue(shell.model.tileActions!!.saves)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertNull(shell.touchCell(1, HostScreen.Bottom))
        val effect = shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(Effect.OpenSaves(homeGameId("romm", "3")), effect)
        assertEquals(game, shell.savesMenuRoute(homeGameId("romm", "3"))?.play)
    }

    @Test fun theScreenOwnsGridKeysAndTouchesAndIgnoresOldAsyncResults() {
        shell.ingestLibrary("romm", listOf(entry(game)), emptyList())
        shell.openHomeAll()
        shell.presentSavePicker(picker())
        val focused = shell.model.focus
        assertNull(shell.touchCell(2, HostScreen.Bottom))
        assertFalse(shell.scrollsByTouch())
        assertTrue(shell.holdsCommandCenter())
        assertFalse(shell.atRest())
        for (meaning in listOf(Meaning.Menu, Meaning.View, Meaning.LeftPanel, Meaning.RightPanel, Meaning.ActivateBottom)) {
            assertNull(shell.onMeaning(meaning, HostScreen.Bottom))
            assertNull(shell.model.panel)
            assertEquals(focused, shell.model.focus)
        }
        val old = shell.model.savePicker!!.target
        shell.presentSavePicker(picker(session = 2))
        shell.updateSavePicker(old) { it.copy(error = true) }
        assertFalse(shell.model.savePicker!!.error)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.model.savePicker)
    }

    @Test fun staleTapsKeepTheirNamedSaveAfterReorderingAndCannotConfirmAnotherChoice() {
        shell.ingestLibrary("romm", listOf(entry(game)), emptyList())
        (plugins.player("melonds") as SaveFolderHolder).bindSaveFolder("content://saves")
        val first = SavePickerSave("first", "sav", "melonds", null, 1, 10, false, null)
        val second = first.copy(id = "second")
        shell.presentSavePicker(picker().copy(saves = listOf(first, second)))
        val target = shell.model.savePicker!!.target
        shell.updateSavePicker(target) { it.copy(saves = listOf(second, first)) }
        shell.touchSavePickerSave(target, "first")
        assertEquals(SavePickerChoice.Play(first), shell.model.savePicker!!.confirmation)
        assertNull(shell.touchSavePickerConfirm(target, SavePickerChoice.Play(second), 0))
        assertFalse(shell.model.savePicker!!.busy)
        shell.presentSavePicker(picker(session = 2).copy(saves = listOf(second)))
        assertNull(shell.touchSavePickerSave(target, "first"))
        assertNull(shell.model.savePicker!!.confirmation)
    }

    @Test fun uninstallingThePlayerKeepsWaitingVisibleAndLeavesSetupQuiet() {
        shell.ingestLibrary("romm", listOf(entry(game)), emptyList())
        shell.setUnsyncedSaves("romm", setOf("3"))
        shell.presentSavePicker(picker())
        shell.setInstalledApps(emptyList(), plugins.playerPackageNames(), installedPlayers = emptySet())
        val target = shell.model.savePicker!!.target
        assertEquals(SaveHealth.Waiting, shell.model.savePicker!!.health)
        assertFalse(shell.model.savePicker!!.canPlay)
        assertTrue(shell.model.savePicker!!.ownSlots.isEmpty())
        assertNull(shell.savePickerFix(target))
        assertEquals(Effect.SyncSavePicker(target), shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        shell.setUnsyncedSaves("romm", emptySet())
        assertEquals(SaveHealth.Fine, shell.model.savePicker!!.health)
        shell.updateSavePicker(target) { it.copy(busy = false, saves = listOf(SavePickerSave("first", "sav", "melonds", null, 1, 10, false, null))) }
        assertNull(shell.touchSavePickerSave(target, "first"))
        assertNull(shell.model.savePicker!!.confirmation)
        assertEquals(SavePickerCopy.installFirst, shell.model.savePicker!!.message)
    }

    @Test fun theHeaderUsesTheExistingFolderFixAndUpdatesWhenConfigured() {
        shell.ingestLibrary("romm", listOf(entry(game)), emptyList())
        shell.presentSavePicker(picker())
        val target = shell.model.savePicker!!.target
        assertEquals(Effect.ChoosePlayerSave("melonds"), shell.savePickerFix(target))
        assertEquals(SaveHealth.NotSyncing(SaveIssue.NoFolder), shell.model.savePicker!!.health)
        (plugins.player("melonds") as SaveFolderHolder).bindSaveFolder("content://saves")
        shell.refreshPlayerSaves()
        assertEquals(SaveHealth.Fine, shell.model.savePicker!!.health)
        shell.setUnsyncedSaves("romm", setOf("3"))
        assertEquals(SaveHealth.Waiting, shell.model.savePicker!!.health)
    }
}
