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
import kotlinx.coroutines.runBlocking
import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import org.junit.Assert.*
import org.junit.Test

class SetupBackupTest {
    private val tree = "content://documents/tree/primary%3AROMs"
    private val local = homeGameId("local-folder", "$tree/document/primary%3AROMs%2Fgame.zip")
    private val server = homeGameId("romm", "42")
    private val collection = HomeFolder("home.collection.1", "Mine", userMade = true, slots = listOf(local, server))
    private fun board() = HomeBoard(slots = listOf(HOME_ALL, local, collection.id),
        folders = mapOf(collection.id to collection), hidden = setOf(server), offHome = setOf("lib:romm:missing"))
    private fun backup(home: HomeBoard = board(), theme: String = "afterglow") = SetupBackup(
        BackupManifest(1, "0.15.0", 341, "Thor", BackupSummary(3, 1, 1), listOf(BackupFolder(tree, "ROMs"))),
        mapOf("theme" to theme, "romm_origin" to "https://romm.example"), encodeHome(home), "", "{}",
        ByteArrayOutputStream().also { PlayArchive.write(it, emptyList(), emptyMap(), emptyList()) }.toByteArray(),
    )

    @Test fun everySessionStoreKeyAndPrefixMustHaveAnExplicitClassification() {
        val source = listOf(File("src/main/kotlin/app/foldcade/SessionStore.kt"), File("app/src/main/kotlin/app/foldcade/SessionStore.kt")).first { it.isFile }.readText()
        val constants = Regex("const val ((?:KEY_[A-Z_]+)|(?:[A-Z_]+_PREFIX)) = \"([^\"]*)\"").findAll(source).map { it.groupValues[2] }
        val inlineKeys = Regex("(?:getString|getBoolean|getFloat|getInt|getLong|putString|putBoolean|putFloat|putInt|putLong|putStringSet|contains|remove|startsWith)\\(\"([^\"]+)\"")
            .findAll(source).map { it.groupValues[1].substringBefore('$') }
        val retired = Regex("\"(moonlight_(?:source|import|import_confirmed|import_settled|placements))\"").findAll(source).map { it.groupValues[1] }
        val keys = (constants + inlineKeys + retired).toSet()
        assertTrue(keys.size > 50)
        val unclassified = keys.filter { BackupSettings.classify(it) == null }
        assertEquals("Persisted keys need a backup decision", emptyList<String>(), unclassified)
        assertEquals(BackupDisposition.DeviceOnly, BackupSettings.classify("player_save_mark:game")?.disposition)
    }

    @Test fun interruptedApplyIsReplayedBeforeStoresOpenAndUndoCopyStaysIntact() {
        for (afterCommit in listOf(false, true)) {
            val root = Files.createTempDirectory("setup-restore").toFile()
            try {
                val prefs = MemoryPrefs()
                prefs.edit().putString("theme", "neon").putString("music_track", "lanternlight")
                    .putString("player_save_mark:save", "hash").putString("pending_save_return:save", "pending")
                    .putBoolean("save_awaits_load:player", true).putString("player_save_folder:player", "this-device").commit()
                val backups = SetupBackups(root, prefs)
                backups.stage(backup(), backup(HomeBoard(), "neon"))
                val before = backups.before.readBytes()
                val crash = { error("process stopped") }
                try {
                    backups.applyStaged(emptySet(), {}, afterFiles = if (afterCommit) ({}) else crash,
                        afterPreferences = if (afterCommit) crash else ({}))
                    fail("No simulated stop")
                } catch (expected: IllegalStateException) { assertEquals("process stopped", expected.message) }
                assertTrue(backups.pending())
                SetupBackups(root, prefs).applyStaged(emptySet(), {})
                assertFalse(backups.pending())
                assertArrayEquals(before, backups.before.readBytes())
                assertEquals("afterglow", prefs.getString("theme", null))
                assertFalse(prefs.contains("music_track"))
                assertEquals("hash", prefs.getString("player_save_mark:save", null))
                assertEquals("pending", prefs.getString("pending_save_return:save", null))
                assertTrue(prefs.getBoolean("save_awaits_load:player", false))
                assertEquals("this-device", prefs.getString("player_save_folder:player", null))
                assertEquals("", prefs.getString("folder_tree", null))
                assertEquals(listOf(BackupFolder(tree, "ROMs")), SessionStore(prefs).restoreFolders())
                assertTrue(SessionStore(prefs).restoreHolds().awaiting(local))
                assertTrue(backups.result.isFile)
            } finally { root.deleteRecursively() }
        }
    }

    @Test fun onlyExistingGrantsAreKeptAndUndoToggles() {
        val root = Files.createTempDirectory("setup-undo").toFile()
        try {
            val prefs = MemoryPrefs()
            val backups = SetupBackups(root, prefs)
            val original = backup(HomeBoard(), "neon")
            backups.stage(backup(), original)
            backups.applyStaged(setOf(tree), {})
            assertEquals(listOf(tree), SessionStore(prefs).folderTrees())
            val undo = backups.before.inputStream().use(SetupArchive::read)
            backups.stage(undo, backup())
            backups.applyStaged(setOf(tree), {})
            assertEquals("neon", prefs.getString("theme", null))
            assertEquals("afterglow", backups.before.inputStream().use(SetupArchive::read).settings["theme"])
            assertTrue(backups.before.isFile)
        } finally { root.deleteRecursively() }
    }

    @Test fun aRestoredAddressNeverReceivesTheOtherServersToken() = runBlocking {
        val root = Files.createTempDirectory("backup-origin").toFile()
        try {
            val prefs = MemoryPrefs()
            prefs.edit().putString("romm_origin", "https://old.example").commit()
            val secrets = MemoryCredentialStore()
            secrets.put(RommCredentials.PLUGIN_ID, RommCredentials.ACCESS_TOKEN, Credential.ApiToken("old-server-token"))
            val backups = SetupBackups(root, prefs)
            backups.stage(backup(), backup())
            backups.applyStaged(emptySet(), {})
            val session = SessionStore(prefs)
            assertTrue(session.restoreRommNeedsSignIn())
            assertNull(readRommToken(session.restoreCredentials(secrets), onMainThread = false))
            assertTrue(secrets.lookup(RommCredentials.PLUGIN_ID, RommCredentials.ACCESS_TOKEN) is CredentialLookup.Present)
            saveRommConnection(session, secrets, "https://romm.example", "new-server-token")
            assertFalse(session.restoreRommNeedsSignIn())
            assertEquals("new-server-token", readRommToken(session.restoreCredentials(secrets), onMainThread = false))
            // Restoring the same address retains this device's sign-in.
            backups.stage(backup(), backup())
            backups.applyStaged(emptySet(), {})
            assertFalse(SessionStore(prefs).restoreRommNeedsSignIn())
        } finally { root.deleteRecursively() }
    }

    @Test fun restoredStepsTakeThePlaceOfTheAutomaticSignInAsk() {
        val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()),
            restoreResultPending = { true })
        shell.askToSignInAgain()
        assertNull(shell.model.dialog)
        assertFalse(shell.model.reLoginPending)
        assertTrue(shell.present(backupDone(BackupCopy.signIn, HostScreen.Bottom, restored = true)))
    }

    @Test fun theGoldenBackupHasARealHomeAndMatchingPreviewCounts() {
        val fixture = listOf(File("../host/src/test/resources/backups/format-1.zip"), File("host/src/test/resources/backups/format-1.zip")).first { it.isFile }
        val read = fixture.inputStream().use(SetupArchive::read)
        val summary = backupHomeSummary(decodeHome(read.home), emptySet())
        assertEquals(read.manifest.summary.games, summary.games)
        assertEquals(read.manifest.summary.collections, summary.collections)
        assertEquals(read.manifest.summary.hidden, summary.hidden)
        assertTrue(decodeHome(read.home).folders.values.single().userMade)
    }

    @Test fun signedOutServerAndMissingFolderNeverReportEmptyAndHeldFacesAreGaps() {
        val prefs = MemoryPrefs()
        prefs.edit().putString("home_board", encodeHome(board())).putString("romm_origin", "https://romm.example")
            .putString("restore_holds", RestoreHolds.forBoard(board(), emptyMap(), listOf(tree)).encode()).commit()
        fun shell() = ShellController(SessionStore(prefs), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        val first = shell()
        // Startup drops unconfigured sources. That is not a catalog report.
        first.dropLibrary("romm")
        first.dropLibrary("local-folder")
        first.setSignedIn(emptyList())
        first.noteShelfChanged()
        assertEquals(local, decodeHome(prefs.getString("home_board", null)).slots[1])
        assertTrue(server in decodeHome(prefs.getString("home_board", null)).hidden)
        val again = shell()
        again.dropLibrary("romm")
        again.dropLibrary("local-folder")
        assertTrue(again.homeFace(1)!!.empty)
        assertNull(again.homeFace(1)!!.id)
        // A and Y on the held position do not offer a game or a launch.
        val home = HomeSession(prefs.getString("home_board", null)).apply { restoredGaps = setOf(local, server) }
        assertNull(home.handle(Meaning.Activate, 1)?.effect)
        assertFalse(home.actions(1)?.hide == true)
        assertNull(home.handle(Meaning.ActivateBottom, 1)?.effect)
        // One successfully connected source releases only that source.
        again.ingestLibrary("romm", emptyList(), emptyList())
        val left = decodeHome(prefs.getString("home_board", null))
        assertFalse(server in left.hidden)
        assertEquals(local, left.slots[1])
        again.reportRestoreFolders(setOf(tree))
        again.ingestLibrary("local-folder", emptyList(), emptyList())
        assertFalse(local in decodeHome(prefs.getString("home_board", null)).slots)
    }
}
