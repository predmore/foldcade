package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.host.backup.*
import app.foldcade.host.play.PlayArchive
import app.foldcade.language.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class SetupRoundThreeRegressionTest {
    private val tree = "content://documents/tree/old-card%3AROMs"
    private val connectedTree = "content://documents/tree/primary%3ASteam%20Library"
    private fun host() = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
    private fun backup(theme: String = "builtin", name: String = theme) = SetupBackup(
        BackupManifest(1, "0.1.6", 1, "Thor", BackupSummary(theme = name)),
        mapOf("theme" to theme), encodeHome(HomeBoard()), "", "{}",
        ByteArrayOutputStream().also { PlayArchive.write(it, emptyList(), emptyMap(), emptyList()) }.toByteArray(),
    )

    @Test fun openingBackupThroughTheControllerShowsUndoAfterRestoreAndOnEveryReopen() {
        val root = Files.createTempDirectory("backup-dialog-controller").toFile()
        try {
            val prefs = MemoryPrefs()
            val backups = SetupBackups(root, prefs)
            // No before-copy yet: exactly the dialog the activity presents on OpenBackup.
            val fresh = ShellController(SessionStore(prefs), host())
            fresh.openSetting(Row.Backup)
            assertEquals(Effect.OpenBackup, fresh.onMeaning(Meaning.Activate, HostScreen.Bottom))
            assertTrue(fresh.present(backupDialog(HostScreen.Bottom, backups.before.isFile)))
            assertFalse(DialogButton.UndoRestore in fresh.model.dialog!!.buttons)

            backups.stage(backup(), backup("afterglow", "Afterglow"))
            assertEquals(SetupRestoreResult.Restored, backups.resume(emptySet(), {}))
            assertEquals("afterglow", backups.before.inputStream().use(SetupArchive::read).settings["theme"])
            val shell = ShellController(SessionStore(prefs), host())
            assertTrue(shell.present(backupDone("", HostScreen.Bottom, restored = true)))
            shell.touchDialog(0, HostScreen.Bottom) // Acknowledge Restored, as in the QA repro.
            shell.openSetting(Row.Backup)
            repeat(2) {
                assertEquals(Effect.OpenBackup, shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
                assertNull(shell.model.dialog)
                assertTrue(shell.present(backupDialog(HostScreen.Bottom, backups.before.isFile)))
                val dialog = shell.model.dialog!!
                assertEquals(listOf(DialogButton.BackUp, DialogButton.PickRestore, DialogButton.UndoRestore, DialogButton.PlayHistory, DialogButton.NotNow), dialog.buttons)
                assertEquals(DialogButton.BackUp, dialog.buttons[dialog.index])
                assertEquals(DialogButton.NotNow, dialog.buttons[dialog.safeIndex])
                assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom, repeatCount = 1))
                assertEquals(dialog, shell.model.dialog)
                shell.onMeaning(Meaning.Back, HostScreen.Bottom)
            }
            // Exercise the actual Undo effect, not just the list of buttons.
            assertEquals(Effect.OpenBackup, shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
            assertTrue(shell.present(backupDialog(HostScreen.Bottom, backups.before.isFile)))
            assertEquals(Effect.DialogChoice(DialogButton.UndoRestore, DialogKind.Backup),
                shell.touchDialog(shell.model.dialog!!.buttons.indexOf(DialogButton.UndoRestore), HostScreen.Bottom))
        } finally { root.deleteRecursively() }
    }

    @Test fun pendingFoldersArePublishedBeforeAnyScanEvenWithNoGrantedTreesAndAfterAColdStart() {
        for (hasConnectedTree in listOf(false, true)) {
            val prefs = MemoryPrefs().apply {
                edit().putString("restore_folders", encodeBackupFolders(listOf(BackupFolder(tree, "ROMs")))).apply()
            }
            if (hasConnectedTree) SessionStore(prefs).addFolderTree(connectedTree)
            repeat(2) {
                val store = SessionStore(prefs)
                val shell = ShellController(store, host()) // No plugin load or reloadFolder call.
                val pending = LibraryFolder(tree, "ROMs", pickAgain = true)
                assertEquals(if (hasConnectedTree) listOf(LibraryFolder(connectedTree, "Steam Library"), pending) else listOf(pending), shell.model.folders)
                assertEquals(store.libraryFolders(), shell.model.folders)
                shell.openSetting(Row.SettingsTile(SettingsInner.Folder(tree)))
                shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
                assertEquals(SettingsInner.Folder(tree), shell.model.settings!!.inner)
                assertEquals(listOf(Row.PickFolderAgain(tree), Row.ForgetFolder(tree, pending.label)), settingsInnerRows(shell.model.settings!!.inner!!, shell.model))
            }
        }
    }

    @Test fun forgettingTheLastOutstandingFolderUsesTheSamePcRefreshHookAsAScan() {
        val prefs = MemoryPrefs().apply {
            edit().putString("restore_folders", encodeBackupFolders(listOf(BackupFolder(tree, "ROMs"), BackupFolder(connectedTree, "Steam Library"))))
                .putString("restore_holds", RestoreHolds(mapOf("gamenative.1" to setOf("gamenative"), "droiddeck.2" to setOf("droiddeck"), "local" to setOf("folder:$tree"))).encode())
                .putString("player_save_mark:game", "unsynced-hash").apply()
        }
        val store = SessionStore(prefs)
        var refreshes = 0
        lateinit var shell: ShellController
        shell = ShellController(store, host(), onRestoreFoldersConnected = {
            assertTrue(store.restoreFolders().isEmpty())
            assertTrue(shell.model.folders.isEmpty())
            refreshes++
            shell.reportRestoreSources(setOf("gamenative", "droiddeck"))
        })
        shell.removeFolderTree(tree)
        assertEquals(0, refreshes)
        assertFalse(store.restoreHolds().awaiting("local"))
        assertTrue(store.restoreHolds().awaiting("gamenative.1"))
        shell.removeFolderTree(connectedTree)
        assertEquals(1, refreshes)
        assertFalse(store.restoreHolds().awaiting("gamenative.1"))
        assertFalse(store.restoreHolds().awaiting("droiddeck.2"))
        shell.removeFolderTree(connectedTree)
        shell.reportRestoreFolders(setOf(tree, connectedTree))
        assertEquals(1, refreshes)
        assertTrue(carriedBackupFolders(store.folderTrees(), store.restoreFolders()).isEmpty())
        assertEquals("unsynced-hash", prefs.getString("player_save_mark:game", null))
    }

    @Test fun previewAndSnapshotThemeNamesUseSettingsLabelsAndKeepUnavailablePackNames() {
        val themes = linkedMapOf("builtin" to Copy.builtIn, "afterglow" to "Afterglow", "aurora-id" to "Aurora")
        val model = PickerModel(0, 1, themes = themes.values.toList(), themeIds = themes.keys.toList())
        for ((id, label) in themes) {
            val selected = model.copy(themeIndex = model.themeIds.indexOf(id))
            assertEquals(rowText(Row.Theme, selected).value, backupThemeName(id, "old label", themes))
            assertTrue(backupPreview(backup(id, "old label"), emptySet(), themes = themes).second.endsWith("Theme: $label"))
        }
        assertEquals("Aurora", backupThemeName("Aurora", "old label", themes)) // Legacy saved name.
        assertTrue(backupPreview(backup("builtin"), emptySet()).second.endsWith("Theme: Built-in")) // Existing v1 export with raw id in summary.
        val elsewhere = backup("elsewhere-id", "Somewhere Else")
        assertTrue(backupPreview(elsewhere, emptySet(), themes = themes).second.endsWith("Theme: Somewhere Else"))
        val fallback = rowText(Row.Theme, model.copy(themeIndex = 1)).value!!
        assertEquals(fallback, backupThemeName(null, fallback, themes))
    }

    private fun ShellController.openSetting(row: Row) {
        closeSettings()
        onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        repeat(leftRows().indexOf(Row.Settings)) { onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        onMeaning(Meaning.Activate, HostScreen.Bottom)
        val categories = settingsCategories(model)
        val category = categories.indexOfFirst { row in settingsRows(it, model) }
        assertTrue("Missing $row", category >= 0)
        repeat(category) { onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        val target = settingsRows(categories[category], model).indexOf(row)
        if (row.settingsTile()) {
            repeat(target / quickTileColumns) { onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
            repeat(target % quickTileColumns) { onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        } else {
            repeat(target) { onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        }
    }
}
