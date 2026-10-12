package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.AndroidShelf
import app.foldcade.language.AppShelfState
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.HOME_ALL
import app.foldcade.language.HomeBoard
import app.foldcade.language.HomeFolder
import app.foldcade.language.HomeMusicSetting
import app.foldcade.language.HostScreen
import app.foldcade.language.LaunchableApp
import app.foldcade.language.LibrarySort
import app.foldcade.language.Meaning
import app.foldcade.language.MotionSpeed
import app.foldcade.language.MusicTrack
import app.foldcade.language.PromptKey
import app.foldcade.language.Row
import app.foldcade.language.decodeHome
import app.foldcade.language.defaultLanternlightTrack
import app.foldcade.language.encodeHome
import app.foldcade.language.leftRows
import app.foldcade.language.panelRows
import app.foldcade.language.settingsCategories
import app.foldcade.language.settingsRows
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPersistenceTest {
    @Test
    fun iconProviderSelectionPersistsIndependentlyOfTheBaseTheme() {
        val prefs = MemoryPrefs()
        val changed = shell(prefs)
        changed.openSetting(Row.IconTheme)
        changed.onMeaning(Meaning.Activate, HostScreen.Bottom)
        changed.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(1, changed.model.iconThemeIndex)
        assertEquals("Afterglow", SessionStore(prefs).iconTheme())
        val restored = shell(prefs)
        assertEquals(1, restored.model.iconThemeIndex)
        restored.openSetting(Row.Theme)
        restored.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(0, restored.model.themeIndex)
        assertEquals(1, restored.model.iconThemeIndex)
    }

    @Test
    fun stableThemeIdsRestoreOldNamesAndKeepUnavailableProvidersSaved() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        store.setTheme("Afterglow")
        assertEquals(1, store.themeIndex(listOf("builtin", "afterglow"), listOf("Built-in", "Afterglow")))
        store.setTheme("afterglow")
        assertEquals(0, store.themeIndex(listOf("afterglow", "builtin"), listOf("Afterglow", "Built-in")))
        store.setIconTheme("temporarily-unavailable")
        assertNull(shell(prefs).model.iconThemeIndex)
        assertEquals("temporarily-unavailable", SessionStore(prefs).iconTheme())
    }
    @Test
    fun settingsEditedInTheShellSurviveARecreatedController() {
        val prefs = MemoryPrefs()
        val changed = shell(prefs)
        assertEquals(1, changed.model.themeIndex)
        val rows = listOf(
            Row.Theme, Row.Background, Row.MotionSpeed, Row.Music, Row.MusicVolume,
            Row.Primary, Row.Order, Row.Artwork, Row.AddNewGames,
        )
        for (row in rows) {
            changed.openSetting(row)
            changed.onMeaning(Meaning.Activate, HostScreen.Bottom)
        }
        val restored = shell(prefs)
        assertEquals(0, restored.model.themeIndex)
        assertEquals(changed.model.backgroundMotion, restored.model.backgroundMotion)
        assertTrue(restored.model.backgroundPinned)
        assertEquals(changed.model.motionSpeed, restored.model.motionSpeed)
        assertEquals(changed.model.music, restored.model.music)
        assertEquals(changed.model.primaryIsTop, restored.model.primaryIsTop)
        assertEquals(LibrarySort.RecentlyPlayed, restored.model.sort)
        assertFalse(restored.model.artwork)
        assertFalse(restored.model.addNewToHome)
    }

    @Test
    fun addNewGamesIsSavedEvenWhenNoMergeChangesTheLayout() {
        val prefs = MemoryPrefs()
        val changed = shell(prefs)
        changed.applyHomeAddNew(false)
        assertFalse(shell(prefs).model.addNewToHome)
        changed.applyHomeAddNew(true)
        assertTrue(shell(prefs).model.addNewToHome)
    }

    @Test
    fun savedThemeFollowsItsNameAndAMissingThemeDoesNotEraseTheChoice() {
        val prefs = MemoryPrefs()
        SessionStore(prefs).setTheme("Afterglow")
        assertEquals(2, shell(prefs, listOf("Built-in", "Another", "Afterglow")).model.themeIndex)
        assertEquals(0, shell(prefs, listOf("Built-in")).model.themeIndex)
        assertEquals(1, shell(prefs).model.themeIndex)
    }

    @Test
    fun anUnavailableTrackFallsBackWithoutErasingTheSavedSelection() {
        val prefs = MemoryPrefs()
        SessionStore(prefs).setMusic(false, 0.25f, "dusk")
        assertEquals(HomeMusicSetting.DEFAULT_TRACK_ID, shell(prefs).model.music.trackId)
        assertEquals("dusk", SessionStore(prefs).musicTrackId())
        val restored = ShellController(
            SessionStore(prefs), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()),
            homeTracks = listOf(defaultLanternlightTrack(), MusicTrack("dusk", "Dusk", "Foldcade", "GPLv3", "music/dusk.ogg")),
        )
        assertEquals(HomeMusicSetting(false, 0.25f, "dusk"), restored.model.music)
    }

    @Test
    fun androidAndGameNativePlacementsAreKeptUntilTheirCatalogsHaveReported() {
        val prefs = MemoryPrefs()
        val android = "android:example.game"
        val pc = "gamenative.STEAM_730"
        val hidden = "android:example.hidden"
        val folder = HomeFolder(
            "home.user.1", "Favorites", userMade = true, namedByUser = true,
            mark = "nintendo-ds", slots = listOf(android, null, pc),
        )
        val saved = HomeBoard(slots = listOf(HOME_ALL, null, folder.id), folders = mapOf(folder.id to folder), hidden = setOf(hidden))
        SessionStore(prefs).saveHomeBoard(encodeHome(saved))

        val restarted = shell(prefs)
        fun board() = decodeHome(SessionStore(prefs).homeBoardRaw())
        assertEquals(folder, board().folders[folder.id])
        assertEquals(2, board().slots.indexOf(folder.id))
        assertTrue(hidden in board().hidden)

        // An unrelated refresh during plugin loading must also preserve these choices.
        restarted.noteShelfChanged()
        assertEquals(folder, board().folders[folder.id])
        restarted.setInstalledApps(
            listOf(LaunchableApp("example.game", "Game", true), LaunchableApp("example.hidden", "Hidden", true)),
            emptySet(),
        )
        assertEquals(folder, board().folders[folder.id])
        assertTrue(hidden in board().hidden)

        // Once a successful scan confirms removal, the saved slot is cleared.
        restarted.setInstalledApps(emptyList(), emptySet())
        assertEquals(listOf(null, null, pc), board().folders[folder.id]?.slots)
        restarted.settleGameNative()
        restarted.noteShelfChanged()
        assertEquals(listOf(null, null, null), board().folders[folder.id]?.slots)
    }

    @Test
    fun folderNamesIconsAndPlacementSurviveEditsThroughTheShell() {
        val prefs = MemoryPrefs()
        val changed = shell(prefs)
        changed.chooseHomeAction(Row.NewFolder)
        changed.editRename("My favorites")
        changed.onMeaning(Meaning.Activate, HostScreen.Bottom)
        val index = changed.model.focus.cellIndex

        changed.chooseHomeAction(Row.FolderIcon)
        changed.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        changed.onMeaning(Meaning.Activate, HostScreen.Bottom)

        val restored = shell(prefs)
        assertEquals("My favorites", restored.homeFace(index)?.title)
        assertEquals(changed.homeFace(index)?.mark, restored.homeFace(index)?.mark)
    }

    @Test
    fun savedSourcesCalibrationAndShelfChoicesSurviveReopeningTheStore() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        store.addFolderTree("content://tree/roms")
        store.addFolderTree("content://tree/other")
        store.removeFolderTree("content://tree/roms")
        store.setPlayerSaveFolder("player", "content://tree/saves")
        store.setRommOrigin("https://romm.example")
        store.saveButtonPrompt("controller", PromptKey.FaceA, dismissed = true)
        store.saveButtonPrompt("other", PromptKey.FaceB, dismissed = false)
        val shelf = AppShelfState().move("game", AndroidShelf.Apps).hide("hidden").pin("favorite", true).show("player-app", true)
        store.saveAppShelfState(shelf)
        store.setHomePromptSettled()
        store.setFolderGrantExplained()
        store.setUsagePromptOffered()
        store.setSaveFolderPromptSkipped("player")

        val restored = SessionStore(prefs)
        assertEquals(listOf("content://tree/other"), restored.folderTrees())
        assertEquals("content://tree/saves", restored.playerSaveFolder("player"))
        store.setRommInstance("romm#2", "https://other.example")
        assertEquals("https://romm.example", restored.rommOrigin())
        assertEquals(listOf("romm#2" to "https://other.example"), restored.rommInstances())
        assertEquals(PromptKey.FaceA, restored.buttonPromptConfirm("controller"))
        assertTrue(restored.buttonPromptDismissed("controller"))
        assertEquals(PromptKey.FaceB, restored.buttonPromptConfirm("other"))
        assertFalse(restored.buttonPromptDismissed("other"))
        assertEquals(shelf, restored.appShelfState())
        assertTrue(restored.homePromptSettled())
        assertFalse(restored.folderGrantPending())
        assertTrue(restored.usagePromptOffered())
        assertTrue(restored.saveFolderPromptSkipped("player"))

        restored.removeRommInstance("romm#2")
        assertTrue(SessionStore(prefs).rommInstances().isEmpty())
        restored.clearRommOrigin()
        restored.saveButtonPrompt("controller", null, dismissed = false)
        assertNull(SessionStore(prefs).rommOrigin())
        assertNull(SessionStore(prefs).buttonPromptConfirm("controller"))
    }

    @Test
    fun invalidStoredValuesFallBackWithoutBreakingStartup() {
        val prefs = MemoryPrefs()
        prefs.edit().putFloat("music_volume", Float.NaN).putString("background_motion", "unknown")
            .putString("motion_speed", "unknown").putString("library_sort", "unknown").apply()
        val restored = SessionStore(prefs)
        assertEquals(HomeMusicSetting.DEFAULT_VOLUME, restored.musicVolume())
        assertEquals(BackgroundMotion.Off, restored.backgroundMotion())
        assertEquals(MotionSpeed.Slow, restored.motionSpeed())
        assertEquals(LibrarySort.Listed, restored.librarySort())
        restored.setMusic(false, Float.POSITIVE_INFINITY, " dusk ")
        assertEquals(HomeMusicSetting.DEFAULT_VOLUME, SessionStore(prefs).musicVolume())
        assertEquals("dusk", SessionStore(prefs).musicTrackId())
    }

    @Test
    fun aSharedFolderGrantRemainsInUseUntilItsLastSavedReferenceChanges() {
        val prefs = MemoryPrefs()
        val uri = "content://tree/shared"
        val store = SessionStore(prefs)
        store.addFolderTree(uri)
        store.setPlayerSaveFolder("one", uri)
        store.setPlayerSaveFolder("two", uri)
        store.removeFolderTree(uri)
        assertTrue(SessionStore(prefs).folderTreeInUse(uri))
        store.setPlayerSaveFolder("one", "content://tree/other")
        assertTrue(SessionStore(prefs).folderTreeInUse(uri))
        store.setPlayerSaveFolder("two", "content://tree/other")
        assertFalse(SessionStore(prefs).folderTreeInUse(uri))
        assertTrue(SessionStore(prefs).folderTreeInUse("content://tree/other"))
    }

    private fun shell(prefs: MemoryPrefs, themes: List<String> = listOf("Built-in", "Afterglow")): ShellController =
        ShellController(
            SessionStore(prefs), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()),
            themeNames = themes, themeMotions = listOf(BackgroundMotion.Off, BackgroundMotion.Ribbons),
        )

    private fun ShellController.openSetting(row: Row) {
        closeSettings()
        onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        repeat(leftRows().indexOf(Row.Settings)) { onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        onMeaning(Meaning.Activate, HostScreen.Bottom)
        val categories = settingsCategories(model)
        val category = categories.indexOfFirst { row in settingsRows(it, model) }
        repeat(category) { onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        repeat(settingsRows(categories[category], model).indexOf(row)) { onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
    }

    private fun ShellController.chooseHomeAction(row: Row) {
        onMeaning(Meaning.Options, HostScreen.Bottom)
        repeat(panelRows(model.panel!!, model).indexOf(row)) { onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        onMeaning(Meaning.Activate, HostScreen.Bottom)
    }
}
