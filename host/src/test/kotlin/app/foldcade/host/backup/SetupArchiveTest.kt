package app.foldcade.host.backup

import app.foldcade.host.play.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class SetupArchiveTest {
    private fun sample(): SetupBackup {
        val play = ByteArrayOutputStream().also { PlayArchive.write(it, listOf(
            PlayEvent("session", "lib:romm:1", PlayKind.Started, 1000, null, 0),
            PlayEvent("session", "lib:romm:1", PlayKind.Ended, 61000, null, 60000),
        ), mapOf("lib:romm:1" to "A game"), emptyList()) }.toByteArray()
        val settings = BackupSettings.keys.filter { it.disposition == BackupDisposition.BackedUp }.associate { rule ->
            rule.name + (if (rule.name.endsWith(':')) "pad" else "") to when (rule.type) {
                PreferenceType.BooleanValue -> true
                PreferenceType.FloatValue -> .4f
                else -> if (rule.name == "romm_origin") "https://romm.example" else "chosen"
            }
        } - setOf("home_board", "same_games")
        return SetupBackup(BackupManifest(1793491200000, "0.15.0", 341, "Thor", BackupSummary(2, 1, 1, "Neon"),
            listOf(BackupFolder("content://documents/tree/ROMs", "ROMs")), listOf("melonds"), true),
            settings, "v2\nslots\taG9tZS5hbGw\n", "lib:romm:1\u001fsame:genesis:game.zip", "{\"game\":{\"Tile\":\"https://art.example/cover\"}}", play,
            mapOf("neon.zip" to byteArrayOf(1, 2, 3)))
    }
    private fun bytes(backup: SetupBackup): ByteArray = ByteArrayOutputStream().also { SetupArchive.write(it, backup) }.toByteArray()
    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
        }
        return entries
    }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()
    private fun refusal(bytes: ByteArray, expected: BackupRefusal) {
        try { SetupArchive.read(bytes.inputStream()); fail("Accepted refused archive") }
        catch (refused: SetupArchiveException) { assertEquals(expected, refused.reason) }
    }

    @Test fun everySectionRoundTripsAndOnlyPortableSettingsTravel() {
        val original = sample()
        val read = SetupArchive.read(bytes(original.copy(settings = original.settings + mapOf("player_save_mark:save" to "synced", "secret" to "token"))).inputStream())
        assertEquals(original.manifest, read.manifest)
        assertEquals(original.settings, read.settings)
        assertEquals(original.home, read.home)
        assertEquals(original.sameGames, read.sameGames)
        assertEquals(original.artChoices, read.artChoices)
        assertArrayEquals(original.playHistory, read.playHistory)
        assertArrayEquals(original.themes.getValue("neon.zip"), read.themes.getValue("neon.zip"))
        assertEquals(original.play().events, read.play().events)
    }

    @Test fun preferenceWireTypesPreserveEveryAndroidValueType() {
        val values = mapOf("string" to "A\nB", "bool" to false, "int" to 8, "long" to Long.MAX_VALUE,
            "float" to .25f, "set" to setOf("same", "games", "🎮"))
        val encoded = SetupArchive.settingsJson(values)
        assertEquals(values, encoded.mapValues { SetupArchive.readPreference(it.value) })
    }

    @Test fun formatOneFixtureRemainsReadable() {
        val read = javaClass.getResourceAsStream("/backups/format-1.zip")!!.use(SetupArchive::read)
        assertEquals(1793491200000, read.manifest.createdMillis)
        assertEquals(BackupSummary(2, 1, 1, "Afterglow"), read.manifest.summary)
        assertEquals("ROMs", read.manifest.folders.single().name)
        assertEquals("afterglow", read.settings["theme"])
        assertEquals(.4f, read.settings["music_volume"])
        assertEquals(2, read.play().events.size)
        assertTrue(read.home.startsWith("v2\n"))
    }

    @Test fun newerForeignTraversalAndWrongTypesAreRefused() {
        val base = entries(bytes(sample()))
        refusal(zip(base - "foldcade-backup.json"), BackupRefusal.Foreign)
        refusal(zip(mapOf("stranger.txt" to byteArrayOf(1))), BackupRefusal.Foreign)
        val marker = base.getValue("foldcade-backup.json").decodeToString().replace("\"format\":1", "\"format\":2")
        refusal(zip(base + ("foldcade-backup.json" to marker.encodeToByteArray())), BackupRefusal.Newer)
        for (name in listOf("../settings.json", "themes/../../x.zip", "/home.txt", "themes\\x.zip")) {
            refusal(zip(base + (name to byteArrayOf(1))), BackupRefusal.Unreadable)
        }
        refusal(zip(base + ("settings.json" to "{\"music_enabled\":{\"type\":\"string\",\"value\":\"false\"}}".encodeToByteArray())), BackupRefusal.Unreadable)
        val unknown = "{\"future_key\":{\"type\":\"alien\",\"value\":{}}}"
        assertEquals(emptyMap<String, Any>(), SetupArchive.read(zip(base + ("settings.json" to unknown.encodeToByteArray())).inputStream()).settings)
    }

    @Test fun entriesAndSkippedInflationAreBounded() {
        val base = entries(bytes(sample()))
        refusal(zip(base + ("settings.json" to ByteArray(4 * 1024 * 1024 + 1))), BackupRefusal.Unreadable)
        // Tiny compressed transport, 289 MiB inflated, and an unrecognized entry still spends the budget.
        val bomb = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("ignored.bin"))
            val chunk = ByteArray(1024 * 1024)
            repeat(289) { zip.write(chunk) }
            zip.closeEntry()
        } }.toByteArray()
        refusal(bomb, BackupRefusal.Unreadable)
    }

    @Test fun applyingAPlanReplacesPortableKeysAndNeverTouchesSaveState() {
        val current = mapOf("theme" to "Neon", "music_track" to "lanternlight", "button_prompt_confirm:old" to "FaceA",
            "player_save_mark:save" to "hash", "pending_save_return:save" to "pending", "save_awaits_load:player" to true,
            "folder_tree" to "this-device", "moonlight_paired_hosts" to "private", "future" to 8)
        val incoming = mapOf("theme" to "Afterglow", "primary_top" to false, "player_save_mark:save" to "bad", "folder_tree" to "foreign")
        val result = BackupSettings.plan(current, incoming).appliedTo(current)
        assertEquals("Afterglow", result["theme"])
        assertFalse(result.containsKey("music_track")); assertFalse(result.containsKey("button_prompt_confirm:old"))
        for ((key, value) in current.filterKeys { BackupSettings.classify(it)?.disposition != BackupDisposition.BackedUp }) assertEquals(key, value, result[key])
        assertEquals(false, result["primary_top"])
        for (unsafe in listOf("https://user:password@romm.example", "https://romm.example?token=secret", "https://user%40name@romm.example")) {
            assertFalse(BackupSettings.portable(mapOf("romm_origin" to unsafe)).containsKey("romm_origin"))
        }
    }

    @Test fun importingBackupPlayTwiceAddsNothingAndCommitIsWhole() {
        val root = Files.createTempDirectory("backup-play").toFile()
        try {
            val file = File(root, "events.log")
            val play = SetupArchive.read(bytes(sample()).inputStream()).play()
            val first = mergedBackupPlay(file, play.events)
            assertFalse(file.exists()) // Until the atomic commit, no partial session is visible.
            file.writeBytes(first)
            val twice = mergedBackupPlay(file, play.events)
            assertArrayEquals(first, twice)
            val log = PlayLog(PlayClock { 0 }, FilePlayLog(file))
            assertEquals(60000L, log.totals("lib:romm:1").activeMillis)
        } finally { root.deleteRecursively() }
    }

    @Test fun packsStreamFromFilesAndOversizeOrOverBudgetPacksAreReportedAndSkipped() {
        val root = Files.createTempDirectory("backup-large-packs").toFile()
        try {
            fun sparse(name: String, size: Long): File = File(root, name).also { file ->
                java.io.RandomAccessFile(file, "rw").use { it.setLength(size) }
            }
            val first = sparse("a.zip", SetupArchive.MAX_THEMES_BYTES - 8192)
            val second = sparse("b.zip", 16384)
            val small = sparse("c.zip", 4096)
            val tooBig = sparse("z.zip", SetupArchive.MAX_THEMES_BYTES + 1)
            val archive = File(root, "backup.zip")
            val result = archive.outputStream().use { output -> SetupArchive.write(output, sample().copy(
                themes = emptyMap(), themeFiles = listOf(first, second, small, tooBig).associateBy { it.name },
            )) }
            assertEquals(2, result.skippedThemes)
            val read = archive.inputStream().use { SetupArchive.read(it, File(root, "sections")) }
            assertTrue(read.themes.isEmpty())
            assertEquals(setOf("a.zip", "c.zip"), read.themeFiles.keys)
            assertEquals(first.length(), read.themeFiles.getValue("a.zip").length())
            assertTrue(read.playHistory.isEmpty())
            assertNotNull(read.playHistoryFile)
            assertEquals(sample().play().events, read.play().events)
            // The memory-only convenience reader refuses a large pack rather than allocating it.
            refusal(archive.readBytes(), BackupRefusal.Unreadable)
        } finally { root.deleteRecursively() }
    }

    @Test fun retainedPlayRecordsHaveAMemoryBudgetAsWellAsAnInflateBudget() {
        val events = (0 until 100).map { PlayEvent("s$it", "g", PlayKind.Started, 1, null, 0) }
        val archive = ByteArrayOutputStream().also { PlayArchive.write(it, events, emptyMap(), emptyList()) }.toByteArray()
        assertThrows(PlayArchiveException::class.java) { PlayArchive.read(archive.inputStream(), retainedLimit = 16 * 1024) }
    }
}
