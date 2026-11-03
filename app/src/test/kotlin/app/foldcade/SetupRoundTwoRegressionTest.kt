package app.foldcade

import android.content.SharedPreferences
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.host.backup.*
import app.foldcade.host.play.PlayArchive
import app.foldcade.language.*
import app.foldcade.localfolder.FolderEntry
import app.foldcade.localfolder.FolderRescan
import app.foldcade.localfolder.LocalFolderBackend
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SetupRoundTwoRegressionTest {
    private val tree = "content://documents/tree/primary%3AROMs"
    private val otherTree = "content://documents/tree/old-card%3AGames"
    private val local = homeGameId("local-folder", "$tree/document/game.zip")
    private val board = HomeBoard(slots = listOf(HOME_ALL, local, null))
    private fun prefs() = MemoryPrefs().apply {
        edit().putString("home_board", encodeHome(board))
            .putString("restore_holds", RestoreHolds.forBoard(board, emptyMap(), listOf(tree)).encode())
            .putString("restore_folders", encodeBackupFolders(listOf(BackupFolder(tree, "ROMs"), BackupFolder(otherTree, "Games"))))
            .putString("player_save_mark:game", "unsynced-hash").apply()
    }
    private fun host() = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
    private fun backup() = SetupBackup(
        BackupManifest(1, "0.1.6", 1, "Thor", BackupSummary(1)), mapOf("theme" to "afterglow"),
        encodeHome(board), "", "{}",
        ByteArrayOutputStream().also { PlayArchive.write(it, emptyList(), emptyMap(), emptyList()) }.toByteArray(),
    )
    private fun fixture(test: (File) -> Unit) {
        val root = Files.createTempDirectory("setup-round-two").toFile()
        try { test(root) } finally { root.deleteRecursively() }
    }

    @Test fun foldersAndAllNeverTargetRootGapsAndOrdinaryEmptyPlacesAreNotHeld() {
        val home = HomeSession(encodeHome(board)).apply { restoredGaps = setOf(local) }
        assertEquals(local, home.restoredGapAt(1))
        assertNull(home.restoredGapAt(2))
        home.showFolders()
        assertNull(home.restoredGapAt(1))
        home.showAll()
        assertNull(home.restoredGapAt(1))
        val ordinary = HomeSession(encodeHome(board))
        assertNull(ordinary.restoredGapAt(1))

        val prefs = prefs()
        val shell = ShellController(SessionStore(prefs), host())
        val quest = androidHomeId("quest")
        shell.setInstalledApps(listOf(LaunchableApp("quest", "Quest", true), LaunchableApp("notes", "Notes", false)), emptySet())
        shell.goHome()
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        for (row in listOf(Row.EditHome, Row.Folders)) {
            walkPanelTo(shell, panelRows(shell.model.panel!!, shell.model).indexOf(row), HostScreen.Bottom)
            shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        }
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        assertEquals("Android Games", shell.homeFace(1)?.title)
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom)
        assertEquals(listOf(quest), shell.homeSlots())
        assertTrue(SessionStore(prefs).restoreHolds().awaiting(local))
        assertEquals(local, decodeHome(SessionStore(prefs).homeBoardRaw()).slots[1])
    }

    @Test fun bothScanPathsNotifyPcCatalogsOnlyAfterTheLastPendingFolderHasReported() {
        for (quiet in listOf(false, true)) {
            val store = SessionStore(prefs())
            var refreshed = 0
            lateinit var shell: ShellController
            shell = ShellController(store, host(), onRestoreFoldersConnected = {
                assertTrue(store.restoreFolders().isEmpty())
                refreshed++
                shell.reportRestoreSources(setOf("gamenative", "droiddeck"))
            })
            store.updateRestoreHolds { RestoreHolds(it.sources + ("pc" to setOf("gamenative", "droiddeck"))) }
            assertFalse(shell.finishFolderReport(false, setOf(tree, otherTree), empty = true, emptyList()))
            assertEquals(0, refreshed)
            if (quiet) shell.reportRestoreFolders(setOf(tree))
            else shell.finishFolderReport(true, setOf(tree), empty = false, emptyList())
            assertEquals(0, refreshed)
            assertTrue(store.restoreHolds().awaiting("pc"))
            if (quiet) shell.reportRestoreFolders(setOf(otherTree))
            else shell.finishFolderReport(true, setOf(otherTree), empty = true, emptyList())
            assertEquals(1, refreshed)
            assertFalse(store.restoreHolds().awaiting("pc"))
            shell.reportRestoreFolders(setOf(tree, otherTree))
            assertEquals(1, refreshed)
        }
    }

    @Test fun anUnmountedTreeThatReturnsEmptyReleasesItsHoldsOnTheUnchangedScan() = runBlocking {
        var mounted = false
        var complete = false
        val backend = LocalFolderBackend(FolderEntry("forest", "", "vnd.android.document/directory")) {
            complete = mounted
            emptyList()
        }
        val prefs = prefs()
        val store = SessionStore(prefs)
        val shell = ShellController(store, host())
        backend.connect()
        assertFalse(shell.finishFolderReport(complete, emptySet(), empty = true, emptyList()))
        assertTrue(store.restoreHolds().awaiting(local))
        mounted = true
        assertEquals(FolderRescan.Unchanged, backend.rescan())
        assertTrue(backend.listPlatforms().isEmpty())
        assertTrue(shell.finishFolderReport(complete, setOf(tree), empty = true, emptyList()))
        assertFalse(store.restoreHolds().awaiting(local))
        assertFalse(local in backupHomeIds(decodeHome(store.homeBoardRaw())))
        assertEquals(listOf(otherTree), store.restoreFolders().map { it.uri })
    }

    @Test fun moonlightPublishesItsCatalogBeforeReleasingARestoredPlace() {
        val previous = Shelf.moonlightGames
        try {
            for (empty in listOf(false, true)) {
                Shelf.moonlightGames = emptyList()
                val computer = "12345678-abcd-1234-abcd-123456789abc"
                val id = "$computer|1"
                val prefs = MemoryPrefs().apply {
                    edit().putString("home_board", encodeHome(HomeBoard(slots = listOf(HOME_ALL, null, null, id))))
                        .putString("restore_holds", RestoreHolds(mapOf(id to setOf("moonlight:$computer"))).encode()).apply()
                }
                val shell = ShellController(SessionStore(prefs), host())
                shell.settleMoonlight()
                shell.noteShelfChanged() // A merge while the app list is still being read.
                assertEquals(id, decodeHome(SessionStore(prefs).homeBoardRaw()).slots[3])
                val games = if (empty) emptyList() else listOf(ShelfGame(id, "Quest", "PC", libraryId = "moonlight", remoteKey = id))
                shell.publishMoonlightCatalog(games, computer)
                val restored = decodeHome(SessionStore(prefs).homeBoardRaw())
                if (empty) assertFalse(id in backupHomeIds(restored)) else assertEquals(id, restored.slots[3])
                assertFalse(SessionStore(prefs).restoreHolds().awaiting(id))
            }
        } finally { Shelf.moonlightGames = previous }
    }

    @Test fun aFailedSafetyCopyClearsTheUnreadyStageAndCanBeRetriedImmediately() = fixture { root ->
        val backups = SetupBackups(root, prefs())
        backups.before.parentFile!!.mkdirs()
        val oldUndo = byteArrayOf(3, 1, 4)
        backups.before.writeBytes(oldUndo)
        File(root, "backup/before-next.zip").writeText("unfinished safety copy")
        try {
            backups.stage(backup(), backup().copy(playHistoryFile = File(root, "missing-play.zip")))
            fail("Expected missing safety-copy input")
        } catch (_: IOException) { }
        assertFalse(backups.pending())
        assertFalse(File(root, "backup/before-next.zip").exists())
        assertFalse(File(root, "backup/ready").exists())
        assertArrayEquals(oldUndo, backups.before.readBytes())
        backups.stage(backup(), backup())
        assertTrue(backups.pending())
        assertEquals(SetupRestoreResult.Restored, backups.resume(emptySet(), {}))
    }

    @Test fun aReadyStageKeepsItsSafetyCopyWhenTheRenameFailsAndStartupFinishesIt() = fixture { root ->
        val backups = SetupBackups(root, prefs())
        backups.before.mkdirs()
        val blocker = File(backups.before, "blocks-the-rename").apply { writeText("occupied") }
        val current = backup().copy(settings = mapOf("theme" to "neon"))
        try {
            backups.stage(backup(), current)
            fail("Expected failed before-copy rename")
        } catch (_: IOException) { }
        assertTrue(backups.pending())
        assertTrue(File(root, "backup/ready").isFile)
        assertTrue(File(root, "backup/before-next.zip").isFile)
        assertTrue(blocker.isFile)
        val stale = leaveStaleFiles(root)
        backups.before.deleteRecursively()
        assertEquals(SetupRestoreResult.Restored, backups.resume(emptySet(), {}))
        assertEquals("neon", backups.before.inputStream().use(SetupArchive::read).settings["theme"])
        stale.forEach { assertFalse(it.path, it.exists()) }
        assertFalse(File(root, "backup/before-next.zip").exists())
    }

    private fun leaveStaleFiles(root: File): List<File> {
        val directory = File(root, "backup").apply { mkdirs() }
        val work = listOf("import-abandoned", "apply-abandoned", "export-abandoned", "before-abandoned").map {
            File(directory, it).apply { mkdirs(); File(this, "section.zip").writeText("section") }
        }
        return work + listOf(".staged.zip-abandoned.tmp", "attempts.abandoned.tmp", ".before-restore.zip-abandoned.tmp").map {
            File(directory, it).apply { writeText("partial") }
        }
    }

    @Test fun startupCleansAbandonedFilesWithoutAStageAndKeepsOnlyTheNewestFailedArchive() = fixture { root ->
        val backups = SetupBackups(root, prefs())
        val stale = leaveStaleFiles(root) + File(root, "backup/before-next.zip").apply { writeText("unready") }
        backups.before.writeText("keep undo")
        val older = File(root, "backup/failed-restore-old.zip").apply { writeText("old"); setLastModified(1000) }
        val newer = File(root, "backup/failed-restore-new.zip").apply { writeText("new"); setLastModified(2000) }
        assertEquals(SetupRestoreResult.None, backups.resume(emptySet()) { fail("No staged apply") })
        stale.forEach { assertFalse(it.path, it.exists()) }
        assertFalse(older.exists())
        assertTrue(newer.isFile)
        assertEquals("keep undo", backups.before.readText())
    }

    @Test fun startupCleanupKeepsAReadyStageAndItsRecoveryInputsAfterAProcessStop() = fixture { root ->
        val prefs = prefs()
        val backups = SetupBackups(root, prefs)
        val incoming = backups.read(ByteArrayOutputStream().also { SetupArchive.write(it, backup()) }.toByteArray().inputStream())
        backups.stage(incoming, backup())
        val before = backups.before.readBytes()
        val stale = leaveStaleFiles(root)
        try {
            backups.resume(emptySet()) { throw AssertionError("process stopped") }
            fail("Expected process stop")
        } catch (expected: AssertionError) { assertEquals("process stopped", expected.message) }
        stale.forEach { assertFalse(it.path, it.exists()) }
        assertFalse(incoming.sourceArchive!!.parentFile!!.exists())
        for (name in listOf("staged.zip", "ready", "rollback-settings.json", "rollback-art.json", "attempts")) {
            assertTrue(name, File(root, "backup/$name").isFile)
        }
        assertArrayEquals(before, backups.before.readBytes())
        assertEquals(SetupRestoreResult.Restored, SetupBackups(root, prefs).resume(emptySet(), {}))
    }

    @Test fun outstandingFoldersHaveExistingTilesAndForgetClearsEvenAnOldVolumeId() {
        val store = SessionStore(prefs())
        store.addFolderTree(tree)
        val folders = libraryFoldersWithOutstandingRestores(listOf(LibraryFolder(tree, "ROMs")), store.restoreFolders())
        assertEquals(listOf(tree, otherTree), folders.map { it.uri })
        assertEquals(listOf("ROMs", "Games"), folders.map { it.label })
        assertTrue(folders.all { it.pickAgain })
        var model = PickerModel(0, 1, folders = folders)
        val tile = Row.SettingsTile(SettingsInner.Folder(otherTree))
        val rows = settingsRows(SettingsCategory.Library, model)
        model = model.copy(settings = SettingsPage(HostScreen.Top, category = settingsCategories(model).indexOf(SettingsCategory.Library), onRows = true, row = rows.indexOf(tile)))
        model = reduce(model, Meaning.Activate, HostScreen.Top).first
        assertEquals(SettingsInner.Folder(otherTree), model.settings!!.inner)
        assertEquals(listOf(Row.PickFolderAgain(otherTree), Row.ForgetFolder(otherTree, "Games")), settingsInnerRows(model.settings!!.inner!!, model))
        model = reduce(model, Meaning.MoveDown, HostScreen.Top).first
        val effect = reduce(model, Meaning.Activate, HostScreen.Top).second
        assertEquals(Effect.ForgetFolder(otherTree), effect)
        store.updateRestoreHolds { RestoreHolds(it.sources + ("old-card-game" to setOf("folder:$otherTree"))) }
        store.removeFolderTree((effect as Effect.ForgetFolder).uri)
        assertFalse(store.restoreHolds().awaiting("old-card-game"))
        assertFalse(otherTree in carriedBackupFolders(store.folderTrees(), store.restoreFolders()).map { it.uri })
        assertEquals(listOf(LibraryFolder(tree, "ROMs")), libraryFoldersWithOutstandingRestores(listOf(LibraryFolder(tree, "ROMs")), emptyList()))
    }

    @Test fun holdReportsAndXUseApplyWithoutCommittingOrTouchingSaveMarks() {
        val actual = prefs()
        var applies = 0
        val prefs = object : SharedPreferences by actual {
            override fun edit(): SharedPreferences.Editor {
                val editor = actual.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        editor.putString(key, value); return this
                    }
                    override fun commit(): Boolean = throw AssertionError("Synchronous hold commit")
                    override fun apply() { applies++; editor.apply() }
                }
            }
        }
        val store = SessionStore(prefs)
        store.updateRestoreHolds { RestoreHolds(it.sources + ("server" to setOf("romm"))) }
        val shell = ShellController(store, host())
        shell.reportRestoreFolders(setOf(otherTree))
        shell.reportRestoreSources(setOf("romm"))
        assertFalse(store.restoreHolds().awaiting("server"))
        shell.goHome()
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom)
        assertFalse(store.restoreHolds().awaiting(local))
        assertEquals(listOf(tree), store.restoreFolders().map { it.uri })
        assertTrue(applies >= 5)
        assertEquals("unsynced-hash", actual.getString("player_save_mark:game", null))
    }

    @Test fun previewDistinguishesConnectingRommFromSwitchingServers() {
        val backup = backup().copy(settings = backup().settings + ("romm_origin" to "https://romm.example"))
        val connecting = backupPreview(backup, emptySet()).second
        assertTrue(connecting.contains("After the restore, sign in to RomM at romm.example."))
        assertFalse(connecting.contains("switches"))
        assertTrue(backupPreview(backup, emptySet(), "https://other.example").second.contains("RomM changes to romm.example. Sign in after the restore."))
        assertFalse(backupPreview(backup, emptySet(), "https://romm.example").second.contains("RomM"))
    }
}
