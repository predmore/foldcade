package app.foldcade

import android.content.SharedPreferences
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.host.backup.*
import app.foldcade.host.play.PlayArchive
import app.foldcade.language.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class SetupReviewRegressionTest {
    private val tree = "content://documents/tree/primary%3AROMs"
    private val otherTree = "content://documents/tree/card%3AROMs"
    private val local = homeGameId("local-folder", "$tree/document/game.zip")
    private val server = homeGameId("romm", "42")
    private val collection = HomeFolder("home.collection.1", "Mine", userMade = true, slots = listOf(local, server))
    private fun board() = HomeBoard(slots = listOf(HOME_ALL, local, server, collection.id), folders = mapOf(collection.id to collection))
    private fun backup(theme: String = "afterglow") = SetupBackup(
        BackupManifest(1, "0.1.6", 1, "Thor", BackupSummary(2, 1), listOf(BackupFolder(tree, "ROMs"))),
        mapOf("theme" to theme, "romm_origin" to "https://romm.example"), encodeHome(board()), "", "{}",
        ByteArrayOutputStream().also { PlayArchive.write(it, emptyList(), emptyMap(), emptyList()) }.toByteArray(),
    )
    private fun prefs(held: Boolean = true) = MemoryPrefs().apply {
        edit().putString("home_board", encodeHome(board())).putString("romm_origin", "https://romm.example")
            .putString("restore_holds", if (held) RestoreHolds.forBoard(board(), emptyMap(), listOf(tree)).encode() else "{}").commit()
    }
    private fun shell(prefs: SharedPreferences) = ShellController(SessionStore(prefs), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
    private fun fixture(test: (File) -> Unit) {
        val root = Files.createTempDirectory("setup-review").toFile()
        try { test(root) } finally { root.deleteRecursively() }
    }
    private fun encoded(backup: SetupBackup) = ByteArrayOutputStream().also { SetupArchive.write(it, backup) }.toByteArray()

    @Test fun anIncompleteEmptyForestKeepsNormalAndRestoredPlaces() {
        for (held in listOf(false, true)) {
            val prefs = prefs(held)
            val shell = shell(prefs)
            assertFalse(shell.finishFolderReport(false, emptySet(), empty = true, emptyList()))
            shell.noteShelfChanged()
            assertTrue(local in decodeHome(prefs.getString("home_board", null)).slots)
            if (held) assertTrue(SessionStore(prefs).restoreHolds().awaiting(local))
            assertTrue(shell.finishFolderReport(true, setOf(tree), empty = true, emptyList()))
            assertFalse(local in decodeHome(prefs.getString("home_board", null)).slots)
            assertFalse(SessionStore(prefs).restoreHolds().awaiting(local))
        }
    }

    @Test fun gameNativeWaitsForFoldersAndForEvidenceOfAnActualSetup() = fixture { root ->
        val catalog = File(root, "catalog.txt")
        val fresh = GameNativeCatalogRead(shortcutsRead = true, hasGames = false, storedCatalog = false)
        assertFalse(fresh.releasesRestore(foldersRead = false, hasFolders = false))
        keepGameNativeCatalog(catalog, emptyList(), storedCatalog = false)
        assertFalse(catalog.exists())
        val established = GameNativeCatalogRead(shortcutsRead = true, hasGames = false, storedCatalog = true)
        assertFalse(established.releasesRestore(foldersRead = false, hasFolders = true))
        assertTrue(established.releasesRestore(foldersRead = true, hasFolders = true))
        assertTrue(established.releasesRestore(foldersRead = false, hasFolders = false))
        assertFalse(established.copy(shortcutsRead = false).releasesRestore(true, true))
        assertTrue(fresh.copy(hasGames = true).releasesRestore(true, true))
    }

    @Test fun aCachedRommIngestDoesNotReleaseMissingRestoredGames() {
        val prefs = prefs()
        val shell = shell(prefs)
        shell.ingestLibrary("romm", emptyList(), emptyList(), restoreReported = false)
        val cached = decodeHome(prefs.getString("home_board", null))
        assertTrue(server in cached.slots)
        assertTrue(server in cached.folders.getValue(collection.id).slots)
        assertTrue(SessionStore(prefs).restoreHolds().awaiting(server))
        shell.ingestLibrary("romm", emptyList(), emptyList(), restoreReported = true)
        assertFalse(server in decodeHome(prefs.getString("home_board", null)).slots)
        assertFalse(SessionStore(prefs).restoreHolds().awaiting(server))
    }

    @Test fun xAndTheRemoveMenuClearAHeldGapFromEveryPlaceAndItsHold() {
        for (menu in listOf(false, true)) {
            val prefs = prefs()
            val shell = shell(prefs)
            shell.goHome()
            shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
            assertEquals(1, shell.model.focus.cellIndex)
            assertTrue(shell.homeFace(1)!!.empty)
            if (menu) {
                shell.onMeaning(Meaning.Options, HostScreen.Bottom)
                assertTrue(Row.RemoveTile in panelRows(shell.model.panel!!, shell.model))
                assertEquals(Copy.remove, rowText(Row.RemoveTile, shell.model).label)
            }
            shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom)
            val gone = decodeHome(prefs.getString("home_board", null))
            assertFalse(local in backupHomeIds(gone))
            assertFalse(SessionStore(prefs).restoreHolds().awaiting(local))
            assertTrue(server in gone.slots)
            // A held X cannot delete the next place under it.
            shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom, repeatCount = 1)
            assertTrue(server in decodeHome(prefs.getString("home_board", null)).slots)
        }
        val hidden = board().copy(hidden = setOf(local), hiddenSlots = mapOf(local to listOf(HiddenSlot(null, 1))))
        assertFalse(local in backupHomeIds(forgetRestoredGame(hidden, local)))
    }

    @Test fun explicitlyRemovingSourcesReleasesOnlyTheirOwnHoldsAndKeepsPendingFoldersForBackup() {
        val prefs = prefs()
        val pc = "12345678-abcd-1234-abcd-123456789abc|1"
        val store = SessionStore(prefs)
        store.saveRestoreHolds(RestoreHolds(store.restoreHolds().sources + (pc to setOf("moonlight:${pc.substringBefore('|')}"))))
        prefs.edit().putString("restore_folders", encodeBackupFolders(listOf(BackupFolder(tree, "ROMs"), BackupFolder(otherTree, "Card")))).commit()
        assertEquals(listOf(tree, otherTree), carriedBackupFolders(emptyList(), store.restoreFolders()).map { it.uri })
        store.clearRommOrigin()
        assertFalse(store.restoreHolds().awaiting(server))
        assertTrue(store.restoreHolds().awaiting(local))
        store.removeFolderTree(tree)
        assertFalse(store.restoreHolds().awaiting(local))
        assertEquals(listOf(otherTree), store.restoreFolders().map { it.uri })
        val shell = shell(prefs)
        shell.reportRestoreSources(setOf("moonlight:${pc.substringBefore('|')}"))
        assertFalse(store.restoreHolds().awaiting(pc))
    }

    @Test fun gapRemovalUndoKeepsWaitingOnlyWhenTheSourceHasNotReportedSinceRemoval() {
        for (reported in listOf(false, true)) {
            val prefs = prefs()
            val shell = shell(prefs)
            shell.goHome()
            shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
            shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom)
            assertEquals(Copy.removed, shell.model.undoVerb)
            if (reported) {
                shell.reportRestoreFolders(setOf(tree))
                shell.ingestLibrary("local-folder", emptyList(), emptyList())
            }
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            val rows = panelRows(shell.model.panel!!, shell.model)
            val target = rows.indexOf(Row.Undo)
            assertTrue(target >= 0)
            walkPanelTo(shell, target, HostScreen.Bottom)
            shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
            assertEquals(!reported, SessionStore(prefs).restoreHolds().awaiting(local))
            assertEquals(!reported, local in backupHomeIds(decodeHome(prefs.getString("home_board", null))))
        }
    }

    @Test fun incomingWithoutRommKeepsTheDeviceConnectionAndFoldersAreAUnion() = fixture { root ->
        val prefs = prefs()
        prefs.edit().putString("folder_tree", otherTree).putString("player_save_mark:game", "hash").commit()
        val backups = SetupBackups(root, prefs)
        backups.stage(backup().copy(settings = mapOf("theme" to "afterglow")), backup("neon"))
        backups.applyStaged(setOf(tree, otherTree), {})
        assertEquals("https://romm.example", SessionStore(prefs).rommOrigin())
        assertFalse(SessionStore(prefs).restoreRommNeedsSignIn())
        assertEquals(listOf(otherTree, tree), SessionStore(prefs).folderTrees())
        assertEquals("hash", prefs.getString("player_save_mark:game", null))
        assertTrue(backupPreview(backup(), emptySet(), "https://other.example").second.contains("RomM changes to romm.example. Sign in after the restore."))
        assertFalse(backupPreview(backup(), emptySet(), "https://romm.example").second.contains("changes to"))
    }

    @Test fun rawIncomingBytesAreStagedBeforeTheUndoCopyChangesAndThemesCannotBlockUndo() = fixture { root ->
        val prefs = prefs()
        val backups = SetupBackups(root, prefs)
        backups.stage(backup(), backup("neon"))
        backups.applyStaged(emptySet(), {})
        val originalUndo = backups.before.readBytes()
        val undo = backups.before.inputStream().use(backups::read)
        val huge = File(root, "huge.zip")
        java.io.RandomAccessFile(huge, "rw").use { it.setLength(SetupArchive.MAX_THEMES_BYTES + 1) }
        try {
            backups.stage(undo, backup().copy(themeFiles = mapOf("huge.zip" to huge)), afterStage = { throw AssertionError("stopped after staging") })
            fail("Expected stop")
        } catch (expected: AssertionError) { assertEquals("stopped after staging", expected.message) }
        assertArrayEquals(originalUndo, backups.before.readBytes())
        assertArrayEquals(originalUndo, File(root, "backup/staged.zip").readBytes())
        assertEquals(SetupRestoreResult.Failed, backups.resume(emptySet(), {}))
        backups.acknowledgeFailure()
        val raw = encoded(backup())
        val incoming = backups.read(raw.inputStream())
        backups.stage(incoming, backup().copy(themeFiles = mapOf("huge.zip" to huge)))
        assertTrue(backups.before.inputStream().use(SetupArchive::read).themes.isEmpty())
        assertArrayEquals(raw, File(root, "backup/staged.zip").readBytes())
    }

    @Test fun readAndStageKeepUnknownTransportSectionsWithoutReencoding() = fixture { root ->
        val bytes = encoded(backup())
        val preserved = ByteArrayOutputStream()
        ZipOutputStream(preserved).use { zip ->
            ZipInputStream(bytes.inputStream()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    zip.putNextEntry(ZipEntry(entry.name)); input.copyTo(zip); zip.closeEntry()
                }
            }
            zip.putNextEntry(ZipEntry("future-section.bin")); zip.write(byteArrayOf(3, 1, 4)); zip.closeEntry()
        }
        val backups = SetupBackups(root, prefs())
        val incoming = backups.read(preserved.toByteArray().inputStream())
        assertTrue(incoming.playHistory.isEmpty())
        assertTrue(incoming.playHistoryFile!!.isFile)
        backups.stage(incoming, backup("neon"))
        assertArrayEquals(preserved.toByteArray(), File(root, "backup/staged.zip").readBytes())
    }

    @Test fun twoFailedAttemptsQuarantineTheStageAndRestoreOriginalChoicesWithoutTouchingSaves() = fixture { root ->
        val prefs = prefs()
        prefs.edit().putString("theme", "neon").putString("player_save_mark:game", "hash").commit()
        val originalArt = "{\"original\":{}}"
        val backups = SetupBackups(root, prefs)
        backups.stage(backup(), backup("neon").copy(artChoices = originalArt))
        val before = backups.before.readBytes()
        var calls = 0
        val broken: (Map<String, File>) -> Unit = { calls++; throw IOException("ENOSPC") }
        assertEquals(SetupRestoreResult.Failed, backups.resume(emptySet(), broken))
        val restarted = SetupBackups(root, prefs)
        assertEquals(SetupRestoreResult.Failed, restarted.resume(emptySet(), broken))
        assertEquals(2, calls)
        assertFalse(restarted.pending())
        assertTrue(File(root, "backup").listFiles()!!.any { it.name.startsWith("failed-restore-") })
        assertArrayEquals(before, restarted.before.readBytes())
        assertEquals("neon", prefs.getString("theme", null))
        assertEquals(originalArt, File(root, "art/choices.json").readText())
        assertEquals("hash", prefs.getString("player_save_mark:game", null))
        assertTrue(restarted.failed.isFile)
        assertEquals(SetupRestoreResult.Failed, SetupBackups(root, prefs).resume(emptySet(), broken))
        assertEquals(2, calls)
        assertEquals("The restore didn't finish. Your setup is as it was.", BackupCopy.restoreUnfinished)
    }

    @Test fun corruptStagesAndCommitFailuresAlsoStopAfterTwoAttempts() {
        for (corrupt in listOf(false, true)) fixture { root ->
            val actual = prefs().apply { edit().putString("theme", "neon").putString("player_save_mark:game", "hash").commit() }
            val prefs: SharedPreferences = if (corrupt) actual else object : SharedPreferences by actual {
                override fun edit(): SharedPreferences.Editor {
                    val editor = actual.edit()
                    return object : SharedPreferences.Editor by editor { override fun commit(): Boolean { editor.commit(); return false } }
                }
            }
            val backups = SetupBackups(root, prefs)
            backups.stage(backup(), backup("neon"))
            if (corrupt) File(root, "backup/staged.zip").writeText("broken zip")
            assertEquals(SetupRestoreResult.Failed, backups.resume(emptySet(), {}))
            assertEquals(SetupRestoreResult.Failed, SetupBackups(root, prefs).resume(emptySet(), {}))
            assertEquals("neon", prefs.getString("theme", null))
            assertEquals("hash", prefs.getString("player_save_mark:game", null))
        }
    }

    @Test fun processDeathsCountAndACommittedRestoreOnlyFinishesCleanup() = fixture { root ->
        val prefs = prefs()
        val backups = SetupBackups(root, prefs)
        backups.stage(backup(), backup("neon"))
        repeat(2) {
            try { SetupBackups(root, prefs).resume(emptySet()) { throw AssertionError("process died") }; fail("Expected stop") }
            catch (_: AssertionError) { }
        }
        assertEquals(SetupRestoreResult.Failed, SetupBackups(root, prefs).resume(emptySet()) { fail("No third apply") })
        backups.acknowledgeFailure()
        backups.stage(backup(), backup("neon"))
        try { backups.applyStaged(emptySet(), {}, afterPreferences = { error("died after commit") }); fail("Expected stop") }
        catch (expected: IllegalStateException) { assertEquals("died after commit", expected.message) }
        assertEquals(SetupRestoreResult.Restored, SetupBackups(root, prefs).resume(emptySet()) { fail("Already committed") })
        assertEquals("afterglow", prefs.getString("theme", null))
    }

    @Test fun inabilityToRecordAnAttemptCannotMakeAStartupLoop() = fixture { root ->
        val prefs = prefs()
        val backups = SetupBackups(root, prefs)
        backups.stage(backup(), backup("neon"))
        val counter = File(root, "backup/attempts")
        counter.delete(); counter.mkdir(); File(counter, "full").writeText("blocked")
        assertEquals(SetupRestoreResult.Failed, backups.resume(emptySet()) { fail("Must not start an untracked apply") })
        assertFalse(backups.pending())
    }

    @Test fun aTransientFailureCanFinishItsSecondAttemptWithoutCrashingTheApplication() = fixture { root ->
        val prefs = prefs()
        val backups = SetupBackups(root, prefs)
        backups.stage(backup(), backup("neon"))
        var calls = 0
        assertEquals(SetupRestoreResult.Restored, backups.resume(emptySet()) {
            if (++calls == 1) throw IOException("temporary read failure")
        })
        assertEquals(2, calls)
        assertEquals("afterglow", prefs.getString("theme", null))
        assertFalse(backups.pending())
        assertFalse(backups.failed.isFile)
    }
}
