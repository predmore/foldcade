package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.host.backup.*
import app.foldcade.host.play.PlayArchive
import app.foldcade.language.*
import app.foldcade.localfolder.FolderEntry
import app.foldcade.localfolder.LocalFolderBackend
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SetupRebaseRegressionTest {
    private val mediaTree = "content://documents/tree/card%3AES-DE"
    private val currentTree = "content://documents/tree/primary%3AROMs"
    private fun host() = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
    private fun backup(settings: Map<String, Any> = emptyMap()) = SetupBackup(
        BackupManifest(1, "0.1.6", 1, "Thor", BackupSummary(), listOf(BackupFolder(mediaTree, "ES-DE"))),
        settings, encodeHome(HomeBoard()), "", "{}",
        ByteArrayOutputStream().also { PlayArchive.write(it, emptyList(), emptyMap(), emptyList()) }.toByteArray(),
    )
    private fun fixture(test: (File) -> Unit) {
        val root = Files.createTempDirectory("setup-rebase").toFile()
        try { test(root) } finally { root.deleteRecursively() }
    }

    @Test fun layoutPlayOnAndPhysicalRolesRoundTripAndRestoreOnTheThor() {
        for (layout in ScreenLayout.entries) for (playOn in Panel.entries) for (primaryTop in listOf(false, true)) fixture { root ->
            val choices = mapOf("screen_layout" to layout.id, "play_on" to if (playOn == Panel.Top) "top" else "bottom", "primary_top" to primaryTop)
            for (key in choices.keys) assertEquals(BackupDisposition.BackedUp, BackupSettings.classify(key)?.disposition)
            val encoded = ByteArrayOutputStream().also { SetupArchive.write(it, backup(choices)) }.toByteArray()
            val read = SetupArchive.read(encoded.inputStream())
            assertEquals(choices, read.settings)
            val prefs = MemoryPrefs()
            val backups = SetupBackups(root, prefs)
            backups.stage(read, backup())
            assertEquals(SetupRestoreResult.Restored, backups.resume(emptySet(), {}))
            val store = SessionStore(prefs)
            assertEquals(layout, store.session.layout)
            assertEquals(playOn, store.session.playOn)
            assertEquals(primaryTop, store.session.defaultDisplayIsTop)
            assertEquals(layout, ShellController(store, host()).model.layout)
        }
    }

    @Test fun olderBackupsResetNewChoicesAndMediaPicksKeepTheExistingGrantAndCacheRules() = fixture { root ->
        val prefs = MemoryPrefs().apply {
            edit().putString("screen_layout", ScreenLayout.TopOnly.id).putString("play_on", "bottom")
                .putBoolean("primary_top", true).putString("folder_tree", currentTree)
                .putString("player_save_mark:game", "unsynced").apply()
        }
        val cache = File(root, "romm/servers/server/catalog.json").apply { parentFile!!.mkdirs(); writeText("local cached facts") }
        val backups = SetupBackups(root, prefs)
        backups.stage(backup(), backup()) // Format 1 predates the two new keys.
        assertEquals(SetupRestoreResult.Restored, backups.resume(setOf(currentTree), {}))
        val store = SessionStore(prefs)
        assertEquals(ScreenLayout.Default, store.session.layout)
        assertEquals(Panel.Top, store.session.playOn)
        assertEquals(listOf(currentTree), store.folderTrees())
        assertEquals(listOf(BackupFolder(mediaTree, "ES-DE")), store.restoreFolders())
        assertEquals(listOf(currentTree, mediaTree), carriedBackupFolders(store.folderTrees(), store.restoreFolders()).map { it.uri })
        assertEquals("local cached facts", cache.readText())
        assertEquals("unsynced", prefs.getString("player_save_mark:game", null))
        assertEquals(BackupDisposition.DeviceOnly, BackupSettings.classify("folder_tree")?.disposition)
        assertFalse(BackupSettings.portable(mapOf("folder_tree" to mediaTree)).containsKey("folder_tree"))
    }

    @Test fun legacyPrimaryBottomBackupsStillRunTheScreenLayoutMigration() = fixture { root ->
        val prefs = MemoryPrefs()
        val backups = SetupBackups(root, prefs)
        backups.stage(backup(mapOf("primary_top" to false)), backup())
        assertEquals(SetupRestoreResult.Restored, backups.resume(emptySet(), {}))
        val session = SessionStore(prefs).session
        assertEquals(ScreenLayout.ShowcaseBottom, session.layout)
        assertEquals(Panel.Bottom, session.playOn)
        assertTrue(session.defaultDisplayIsTop)
    }

    @Test fun backupAndPlayHistoryFollowSettingsInEveryLayoutAndAvoidAGamesScreen() {
        for (layout in ScreenLayout.entries) for (occupied in listOf<Panel?>(null, Panel.Top, Panel.Bottom)) {
            val store = SessionStore(MemoryPrefs())
            store.update { it.withLayout(layout).let { next -> if (occupied == null) next else next.launch(ExternalApp("game"), occupied) } }
            if (Panel.entries.none { store.session.draws(it) }) continue
            val shell = ShellController(store, host())
            shell.onMeaning(Meaning.Menu, HostScreen.Bottom)
            val pageScreen = shell.model.settings!!.screen
            assertTrue(store.session.draws(pageScreen.panel()))
            val category = settingsCategories(shell.model).indexOf(SettingsCategory.Screens)
            shell.touchSettingsCategory(category, pageScreen)
            val rows = settingsRows(SettingsCategory.Screens, shell.model)
            assertEquals(Effect.OpenBackup, shell.touchSettingsRow(rows.indexOf(Row.Backup), pageScreen))
            val screen = shell.model.backupDialogScreen(HostScreen.Bottom)
            assertEquals(pageScreen, screen)
            assertTrue(shell.present(backupDialog(screen, undo = true)))
            assertEquals(pageScreen, shell.model.dialog!!.screen)
            assertNull(shell.touchDialog(shell.model.dialog!!.buttons.indexOf(DialogButton.PlayHistory), screen))
            assertEquals(playHistoryDialog(screen), shell.model.dialog)
            shell.dismissDialog()
            for (dialog in listOf(restoreDialog("Nov 1, 2026", "Home", screen), backupDone(BackupCopy.saved, screen), backupDone("", screen, restored = true))) {
                assertTrue(shell.present(dialog))
                assertEquals(screen, shell.model.dialog!!.screen)
                assertTrue(store.session.draws(shell.model.dialog!!.screen.panel()))
                shell.dismissDialog()
            }
        }
    }

    @Test fun mediaPurposeAndPendingPickLabelsSurviveTheirUnion() {
        val current = LibraryFolder(currentTree, "ROMs", artAndInfo = true)
        val folders = libraryFoldersWithOutstandingRestores(listOf(current), listOf(BackupFolder(mediaTree, "ES-DE")))
        assertEquals(current, folders[0])
        assertTrue(folders[0].artAndInfo)
        assertEquals("ES-DE", folders[1].label)
        assertTrue(folders[1].pickAgain)
        assertFalse(folders[1].artAndInfo) // Purpose is learned after a granted scan.
        val model = PickerModel(0, 1, folders = folders)
        assertEquals("ROMs · art and info", settingsInnerTitle(SettingsInner.Folder(currentTree), model))
    }

    @Test fun anUnchangedCompletedRefreshKeepsItsMediaSnapshotAndDoesNotReloadTheCatalog() = runBlocking {
        val files = mutableListOf<FolderEntry>()
        val backend = LocalFolderBackend(FolderEntry("content://nes", "nes", "vnd.android.document/directory")) { files.toList() }
        assertNull(readFolderRefresh(backend, { true }) { error("No initial scan") })
        backend.connect()
        val snapshot = backend.mediaScan()
        assertNull(readFolderRefresh(backend, { false }) { error("Incomplete scan") })
        val unchanged = readFolderRefresh(backend, { true }) { error("Must keep the cached scan") }!!
        assertFalse(unchanged.changed)
        assertNull(unchanged.library)
        assertSame(snapshot, backend.mediaScan())
        files += FolderEntry("content://nes/game", "Game.nes", "application/octet-stream")
        var loads = 0
        val changed = readFolderRefresh(backend, { true }) { loads++; LoadedLibrary.NoPlatforms }!!
        assertTrue(changed.changed)
        assertEquals(LoadedLibrary.NoPlatforms, changed.library)
        assertEquals(1, loads)
        assertNotSame(snapshot, backend.mediaScan())
    }
}
