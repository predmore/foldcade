package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.host.PluginHost
import app.foldcade.host.backup.*
import app.foldcade.host.play.*
import app.foldcade.language.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class SetupBackupUxTest {
    private val tree = "content://documents/tree/old-card%3AROMs"
    private val other = "content://documents/tree/new-card%3AROMs"
    private val localKey = "$tree/document/game.nes"
    private val local = homeGameId("local-folder", localKey)
    private val pc = "12345678-abcd-1234-abcd-123456789abc"
    private fun host() = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
    private fun backup(theme: String = "base", computers: Boolean = false) = SetupBackup(
        BackupManifest(1, "0.1.6", 1, "Thor", BackupSummary(), computers = computers), mapOf("theme" to theme),
        encodeHome(HomeBoard()), "", "{}", ByteArrayOutputStream().also {
            PlayArchive.write(it, listOf(PlayEvent(theme, "game", PlayKind.Started, 1, null, 0), PlayEvent(theme, "game", PlayKind.Ended, 2, null, 1)), emptyMap(), emptyList())
        }.toByteArray(),
    )
    private fun fixture(test: (File) -> Unit) {
        val root = Files.createTempDirectory("backup-ux").toFile()
        try { test(root) } finally { root.deleteRecursively() }
    }

    @Test fun undoAndRedoAreBoundToTheBeforeCopyAcrossRestartsAndKeepBothHistories() = fixture { root ->
        val prefs = MemoryPrefs()
        val log = File(root, "play-sessions/events.log").apply { parentFile!!.mkdirs() }
        log.writeBytes(mergedBackupPlay(log, backup("base").play().events))
        var backups = SetupBackups(root, prefs)
        assertNull(backups.undoAction())
        backups.stage(backup("restored"), backup("base"))
        backups.resume(emptySet(), {})
        backups = SetupBackups(root, prefs)
        assertEquals(RestoreAction.Undo, backups.undoAction())
        assertEquals("restored", prefs.getString("theme", null))
        val undo = backups.before.inputStream().use(backups::read)
        backups.stage(undo, backup("restored"), RestoreAction.Undo)
        try {
            backups.applyStaged(emptySet(), {}, afterPreferences = { throw IOException("stopped after commit") })
            fail("Expected simulated stop")
        } catch (expected: IOException) { assertEquals("stopped after commit", expected.message) }
        backups = SetupBackups(root, prefs)
        assertEquals(SetupRestoreResult.Restored, backups.resume(emptySet(), {}))
        assertEquals("base", prefs.getString("theme", null))
        assertEquals(RestoreAction.Redo, backups.undoAction())
        val redo = backups.before.inputStream().use(backups::read)
        assertTrue(redo.manifest.redoRestore)
        backups.stage(redo, backup("base"), RestoreAction.Redo)
        backups.resume(emptySet(), {})
        assertEquals("restored", prefs.getString("theme", null))
        assertEquals(RestoreAction.Undo, SetupBackups(root, prefs).undoAction())
        assertEquals(setOf("base", "restored"), FilePlayLog(log).read().map { it.sessionId }.toSet())
        assertEquals(4, FilePlayLog(log).read().size)
        // An ordinary restore starts a fresh Undo cycle even after a prior undo/redo.
        val picked = backup("another").let { it.copy(manifest = it.manifest.copy(redoRestore = true)) }
        backups.stage(picked, backup("restored")) // Picking an archive is still an ordinary Restore.
        backups.resume(emptySet(), {})
        assertEquals(RestoreAction.Undo, backups.undoAction())
    }

    @Test fun aReadyUndoKeepsItsRedoFlagThroughTheRecoverableBeforeCopyRename() = fixture { root ->
        val prefs = MemoryPrefs()
        val backups = SetupBackups(root, prefs)
        backups.before.mkdirs()
        File(backups.before, "blocks rename").writeText("occupied")
        try { backups.stage(backup("base"), backup("restored"), RestoreAction.Undo); fail("Expected blocked rename") }
        catch (_: IOException) { }
        assertTrue(backups.pending())
        backups.before.deleteRecursively()
        assertEquals(SetupRestoreResult.Restored, SetupBackups(root, prefs).resume(emptySet(), {}))
        assertEquals(RestoreAction.Redo, SetupBackups(root, prefs).undoAction())
    }

    @Test fun aPendingFolderPageFocusesPickAgainAndPickingAnotherTreeCannotForgetIt() {
        for (same in listOf(false, true)) {
            val prefs = MemoryPrefs().apply {
                edit().putString("restore_folders", encodeBackupFolders(listOf(BackupFolder(tree, "A long ROM folder name"))))
                    .putString("restore_holds", RestoreHolds(mapOf(local to setOf("folder:$tree"))).encode())
                    .putString("home_board", encodeHome(HomeBoard(slots = listOf(HOME_ALL, local))))
                    .putString("player_save_mark:game", "unsynced").apply()
            }
            val store = SessionStore(prefs)
            val shell = ShellController(store, host())
            shell.onMeaning(Meaning.Menu, HostScreen.Top)
            val screen = shell.model.settings!!.screen
            shell.touchSettingsCategory(settingsCategories(shell.model).indexOf(SettingsCategory.Library), screen)
            val tile = Row.SettingsTile(SettingsInner.Folder(tree))
            assertEquals(TileStatus.Attention, settingsTileStatus(tile, shell.model))
            assertEquals("A long ROM folder name", settingsTileLabel(SettingsInner.Folder(tree), shell.model))
            shell.touchSettingsRow(settingsRows(SettingsCategory.Library, shell.model).indexOf(tile), screen)
            assertEquals("A long ROM folder name · Pick again", settingsInnerTitle(SettingsInner.Folder(tree), shell.model))
            assertEquals(0, shell.model.settings!!.row)
            assertEquals(listOf(Row.PickFolderAgain(tree), Row.ForgetFolder(tree, "A long ROM folder name")), settingsInnerRows(SettingsInner.Folder(tree), shell.model))
            assertEquals(Effect.PickFolderAgain(tree), shell.onMeaning(Meaning.Activate, screen))
            assertNull(shell.onMeaning(Meaning.Activate, screen, repeatCount = 1))
            assertTrue(store.restoreHolds().awaiting(local))
            assertEquals(FolderPickResult.UseAsLibrary, folderPickResult(FolderPickerState(FolderPurpose.Library), choseFolder = true))
            val picked = if (same) tree else other
            store.addFolderTree(picked)
            shell.reportRestoreFolders(setOf(picked))
            shell.setFolders(store.libraryFolders())
            val game = Game("local-folder", localKey, "nes", Availability.LocalOnly, "Game")
            shell.ingestLibrary("local-folder", if (same) listOf(GridEntry(localKey, "Game", "NES", "nes", null, false, game)) else emptyList(), emptyList())
            assertEquals(!same, store.restoreHolds().awaiting(local))
            assertEquals(!same, store.restoreFolders().any { it.uri == tree })
            assertEquals(!same, shell.model.folders.single { it.uri == tree }.pickAgain)
            assertEquals(local, decodeHome(store.homeBoardRaw()).slots[1])
            assertEquals("unsynced", prefs.getString("player_save_mark:game", null))
        }
    }

    @Test fun restoredLibraryShortcutIsFocusedButOkIsSafeAndHeldACannotEnterThePage() {
        for (library in listOf(false, true)) {
            val shell = ShellController(SessionStore(MemoryPrefs()), host())
            val dialog = backupDone(if (library) BackupCopy.folders("roms") else "", HostScreen.Top, restored = true, library = library).copy(subject = "restored")
            shell.present(dialog)
            assertEquals(if (library) DialogButton.Library else DialogButton.Ok, dialog.buttons[dialog.index])
            assertEquals(DialogButton.Ok, dialog.buttons[dialog.safeIndex])
            assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Top, repeatCount = 1))
            shell.onMeaning(Meaning.Activate, HostScreen.Top)
            if (library) {
                assertEquals(SettingsCategory.Library, settingsCategories(shell.model)[shell.model.settings!!.category])
                val page = shell.model.settings
                shell.onMeaning(Meaning.Activate, HostScreen.Top, repeatCount = 1)
                assertEquals(page, shell.model.settings)
            } else assertNull(shell.model.settings)
            shell.closeSettings()
            shell.present(dialog)
            shell.onMeaning(Meaning.Back, HostScreen.Top)
            assertNull(shell.model.settings)
        }
    }

    @Test fun aHeldGapNamesItsFolderRommOrKnownComputerAndOrdinaryGapsDoNot() {
        for ((source, title) in listOf("folder:$tree" to "Waiting for ROMs", "romm" to "Waiting for RomM", "moonlight:$pc" to "Waiting for game-win", "unknown" to null)) {
            val prefs = MemoryPrefs().apply {
                edit().putString("restore_holds", RestoreHolds(mapOf(local to setOf(source))).encode())
                    .putString("restore_folders", encodeBackupFolders(listOf(BackupFolder(tree, "ROMs"))))
                    .putString("home_board", encodeHome(HomeBoard(slots = listOf(HOME_ALL, local)))).apply()
            }
            val store = SessionStore(prefs)
            store.setMoonlightHostNames(mapOf(pc to "game-win"))
            val shell = ShellController(store, host())
            assertNull(shell.restoredGapTitle())
            shell.goHome()
            shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            assertEquals(title, shell.restoredGapTitle())
        }
    }

    @Test fun aLocallyDiscoveredUnpairedComputerDoesNotInventABackupPairingStep() {
        for (computers in listOf(false, true)) fixture { root ->
            val prefs = MemoryPrefs()
            val backups = SetupBackups(root, prefs)
            backups.stage(backup(computers = computers), backup())
            backups.resume(emptySet(), {})
            val store = SessionStore(prefs)
            val shell = ShellController(store, host())
            shell.setMoonlightHosts(mapOf(pc to MoonlightHost("game-win", online = true)))
            assertEquals(listOf(StreamHostSetting(pc, "game-win", paired = false)), shell.model.streamHosts)
            assertEquals(computers, "computers" in backups.result.readLines())
            assertEquals(computers, "computers" in backups.result.readLines() && storedComputersNeedPairing(store, null))
        }
    }
}
