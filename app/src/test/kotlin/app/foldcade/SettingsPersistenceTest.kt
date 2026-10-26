package app.foldcade

import android.view.KeyEvent
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
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
import app.foldcade.language.SettingsCategory
import app.foldcade.language.ThorStyle
import app.foldcade.language.decodeHome
import app.foldcade.language.defaultLanternlightTrack
import app.foldcade.language.encodeHome
import app.foldcade.language.leftRows
import app.foldcade.language.panelRows
import app.foldcade.language.rowText
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
    fun savedCalibrationCanBeChangedBackAndStillBeChangedAfterRestart() {
        val prefs = MemoryPrefs()
        SessionStore(prefs).saveButtonPrompt("controller", PromptKey.FaceB, dismissed = true)
        val changed = shell(prefs)
        changed.applyFacePrompt(ThorStyle.Standard, "controller", firstSession = true)
        assertFalse(changed.model.offerButtonLabels)
        assertTrue(Row.ButtonLabels in settingsRows(SettingsCategory.Personalization, changed.model))
        changed.openSetting(Row.ButtonLabels)
        changed.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(changed.model.capturingConfirm)
        changed.calibrateConfirm(PromptKey.FaceA)
        assertFalse(changed.model.capturingConfirm)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, changed.model.faceMap.confirmKey)
        assertEquals(PromptKey.FaceA, SessionStore(prefs).buttonPromptConfirm("controller"))

        val restored = shell(prefs)
        restored.applyFacePrompt(ThorStyle.Standard, "controller", firstSession = false)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, restored.model.faceMap.confirmKey)
        assertEquals("Confirm: A", rowText(Row.ButtonLabels, restored.model).value)
        restored.openSetting(Row.ButtonLabels)
        restored.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(restored.model.capturingConfirm)
        restored.calibrateConfirm(PromptKey.FaceB)
        assertEquals(PromptKey.FaceB, SessionStore(prefs).buttonPromptConfirm("controller"))
    }

    @Test
    fun leavingCalibrationDisarmsItWithoutReplacingTheSavedButton() {
        val cancellations: List<ShellController.() -> Unit> = listOf(
            { onMeaning(Meaning.MoveUp, HostScreen.Bottom) },
            { onMeaning(Meaning.MoveDown, HostScreen.Bottom) },
            { onMeaning(Meaning.MoveLeft, HostScreen.Bottom) },
            { onMeaning(Meaning.Back, HostScreen.Bottom) },
            { onMeaning(Meaning.Menu, HostScreen.Bottom) },
            { onMeaning(Meaning.LeftPanel, HostScreen.Bottom) },
            { onMeaning(Meaning.RightPanel, HostScreen.Bottom) },
            { touchSettingsCategory(1, model.settings!!.screen) },
            { touchSettingsRow(0, model.settings!!.screen) },
            { closeSettings() },
        )
        for ((index, cancel) in cancellations.withIndex()) {
            val prefs = MemoryPrefs()
            SessionStore(prefs).saveButtonPrompt("controller", PromptKey.FaceB, dismissed = false)
            val changed = shell(prefs)
            changed.applyFacePrompt(null, "controller", firstSession = false)
            changed.openSetting(Row.ButtonLabels)
            changed.onMeaning(Meaning.Activate, HostScreen.Bottom)
            assertTrue("armed before cancellation $index", changed.model.capturingConfirm)
            changed.cancel()
            assertFalse("disarmed after cancellation $index", changed.model.capturingConfirm)
            changed.calibrateConfirm(PromptKey.FaceA)
            assertEquals("saved button after cancellation $index", PromptKey.FaceB, SessionStore(prefs).buttonPromptConfirm("controller"))
        }
    }

    @Test
    fun refreshingTheSameControllerKeepsCalibrationButChangingControllerCancelsIt() {
        val prefs = MemoryPrefs()
        SessionStore(prefs).saveButtonPrompt("controller", PromptKey.FaceB, dismissed = false)
        val changed = shell(prefs)
        changed.applyFacePrompt(ThorStyle.Standard, "controller", firstSession = false)
        changed.openSetting(Row.ButtonLabels)
        changed.onMeaning(Meaning.Activate, HostScreen.Bottom)
        changed.applyFacePrompt(ThorStyle.Standard, "controller", firstSession = false)
        assertTrue(changed.model.capturingConfirm)
        changed.applyFacePrompt(ThorStyle.Standard, "other-controller", firstSession = false)
        assertFalse(changed.model.capturingConfirm)
        changed.calibrateConfirm(PromptKey.FaceA)
        assertNull(SessionStore(prefs).buttonPromptConfirm("other-controller"))
        assertEquals(PromptKey.FaceB, SessionStore(prefs).buttonPromptConfirm("controller"))
    }

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
        restored.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
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
            Row.Primary, Row.Order, Row.Artwork,
        )
        for (row in rows) {
            changed.openSetting(row)
            // Theme's A opens the Themes page; right steps it, as left and right step every choice.
            changed.onMeaning(if (row == Row.Theme) Meaning.MoveRight else Meaning.Activate, HostScreen.Bottom)
        }
        // New games on Home lives in Edit home.
        changed.closeSettings()
        changed.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        // L1's places are one line of tiles, so Down passes them in one step.
        while (leftRows()[changed.model.panel!!.index] != Row.EditHome) changed.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        changed.onMeaning(Meaning.Activate, HostScreen.Bottom)
        repeat(panelRows(changed.model.panel!!, changed.model).indexOf(Row.AddNewGames)) { changed.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        changed.onMeaning(Meaning.Activate, HostScreen.Bottom)
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
    fun aMissingAfterglowFallsBackToSlotZero() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        store.setTheme("afterglow")
        assertEquals(0, store.themeIndex(listOf("builtin", "night")))
        assertEquals(0, fallbackThemeIndex(listOf("builtin")))
        assertEquals(1, fallbackThemeIndex(listOf("builtin", "afterglow")))
        assertEquals(2, fallbackThemeIndex(listOf("Built-in", "Night", "Afterglow")))
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
            homeTracks = listOf(defaultLanternlightTrack(), MusicTrack("dusk", "Dusk", "Foldcade", "MIT", "music/dusk.ogg")),
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
        changed.chooseHomeAction(Row.NewCollection)
        changed.typing()?.setText("My favorites")
        changed.onMeaning(Meaning.Done, HostScreen.Bottom)
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
        // What a release with Android shelves saved: read once, as the apps hidden there.
        prefs.edit().putStringSet("android_shelf_apps", setOf("game")).putStringSet("android_shelf_hidden", setOf("hidden"))
            .putStringSet("android_shelf_favorites", setOf("favorite")).putStringSet("android_shelf_shown", setOf("player-app")).apply()
        store.setHomePromptSettled()
        store.setFolderGrantExplained()
        store.setUsagePromptOffered()

        val restored = SessionStore(prefs)
        assertEquals(listOf("content://tree/other"), restored.folderTrees())
        assertEquals("content://tree/saves", restored.playerSaveFolder("player"))
        assertEquals("https://romm.example", restored.rommOrigin())
        assertEquals(PromptKey.FaceA, restored.buttonPromptConfirm("controller"))
        assertTrue(restored.buttonPromptDismissed("controller"))
        assertEquals(PromptKey.FaceB, restored.buttonPromptConfirm("other"))
        assertFalse(restored.buttonPromptDismissed("other"))
        assertEquals(setOf("hidden"), restored.takeShelfHiddenApps())
        assertEquals(emptySet<String>(), SessionStore(prefs).takeShelfHiddenApps())
        assertFalse(prefs.all.keys.any { it.startsWith("android_shelf_") })
        assertTrue(restored.homePromptSettled())
        assertFalse(restored.folderGrantPending())
        assertTrue(restored.usagePromptOffered())

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

    @Test
    fun retroArchsCoresFolderKeepsItsGrantInUse() {
        val prefs = MemoryPrefs()
        val uri = "content://com.retroarch.aarch64.documents/tree/%2Fdata%2Fuser%2F0%2Fcom.retroarch.aarch64"
        val store = SessionStore(prefs)
        store.addFolderTree(uri)
        store.setRetroArchCoresFolder("com.retroarch.aarch64", uri)
        store.removeFolderTree(uri)
        assertTrue(SessionStore(prefs).folderTreeInUse(uri))
        assertEquals(uri, SessionStore(prefs).retroArchCoresFolder("com.retroarch.aarch64"))
        store.setRetroArchCoresFolder("com.retroarch.aarch64", "content://other")
        assertFalse(SessionStore(prefs).folderTreeInUse(uri))
    }

    @Test
    fun edensFolderKeepsItsGrantInUse() {
        val prefs = MemoryPrefs()
        val uri = "content://dev.eden.eden_emulator.user/tree/root"
        val store = SessionStore(prefs)
        store.setEdenFolder("dev.eden.eden_emulator", uri)
        assertTrue(SessionStore(prefs).folderTreeInUse(uri))
        assertEquals(uri, SessionStore(prefs).edenFolder("dev.eden.eden_emulator"))
        assertFalse(store.edenFolderOffered())
        store.setEdenFolderOffered()
        assertTrue(SessionStore(prefs).edenFolderOffered())
        store.setEdenFolder("dev.eden.eden_emulator", "content://other")
        assertFalse(SessionStore(prefs).folderTreeInUse(uri))
        assertTrue(SessionStore(prefs).folderTreeInUse("content://other"))
    }

    @Test
    fun anEdenRestartRoundTripsUntilCleared() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        assertNull(store.edenRestartPending())
        store.setEdenRestartPending("", 3)
        assertNull(SessionStore(prefs).edenRestartPending())
        store.setEdenRestartPending("dev.eden.eden_emulator", 7)
        assertEquals(
            EdenRestartPending("dev.eden.eden_emulator", 7),
            SessionStore(prefs).edenRestartPending(),
        )
        store.setEdenRestartPending("dev.eden.eden_emulator", -1)
        assertEquals(
            EdenRestartPending("dev.eden.eden_emulator", -1),
            SessionStore(prefs).edenRestartPending(),
        )
        store.clearEdenRestartPending()
        assertNull(SessionStore(prefs).edenRestartPending())
        assertNull(store.edenRestartPending())
    }

    @Test
    fun refusedEdenFirmwareDropsTheOldestPastTwenty() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        assertTrue(store.refusedEdenFirmware().isEmpty())
        store.rememberRefusedEdenFirmware("")
        assertTrue(store.refusedEdenFirmware().isEmpty())
        store.rememberRefusedEdenFirmware("m")
        repeat(19) { store.rememberRefusedEdenFirmware("a$it") }
        assertEquals(20, store.refusedEdenFirmware().size)
        store.rememberRefusedEdenFirmware("a0")
        assertEquals(20, store.refusedEdenFirmware().size)
        assertTrue(store.refusedEdenFirmware().contains("m"))
        store.rememberRefusedEdenFirmware("a19")
        val kept = store.refusedEdenFirmware()
        assertEquals(20, kept.size)
        assertFalse(kept.contains("m"))
        assertTrue(kept.contains("a0"))
        assertTrue(kept.contains("a19"))
        store.rememberRefusedEdenFirmware("a19")
        assertEquals(kept, store.refusedEdenFirmware())
        assertEquals(kept, SessionStore(prefs).refusedEdenFirmware())
    }

    @Test
    fun aWrittenSaveWaitsAndTheLastStartIsKept() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        assertFalse(store.saveAwaitsLoad("retroarch"))
        store.setSaveAwaitsLoad("retroarch", true)
        assertTrue(SessionStore(prefs).saveAwaitsLoad("retroarch"))
        assertFalse(SessionStore(prefs).saveAwaitsLoad("melonds"))
        store.setSaveAwaitsLoad("retroarch", false)
        assertFalse(SessionStore(prefs).saveAwaitsLoad("retroarch"))
        store.setLastPlayerStart("com.retroarch.aarch64", "key")
        assertEquals("key", SessionStore(prefs).lastPlayerStart("com.retroarch.aarch64"))
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
