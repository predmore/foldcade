package app.foldcade

import android.content.SharedPreferences
import app.foldcade.host.backup.*
import app.foldcade.host.play.mergedBackupPlay
import app.foldcade.host.play.PlayNames
import app.foldcade.language.*
import app.foldcade.romm.writeDurably
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlinx.serialization.json.*

internal enum class SetupRestoreResult { None, Restored, Failed }

/** File staging and its recovery run before SessionStore can read or save anything. */
internal class SetupBackups(private val root: File, private val prefs: SharedPreferences) {
    private val directory = File(root, "backup")
    val before = File(directory, "before-restore.zip")
    private val stage = File(directory, "staged.zip")
    private val nextBefore = File(directory, "before-next.zip")
    private val ready = File(directory, "ready")
    private val attempts = File(directory, "attempts")
    private val baseline = File(directory, "rollback-settings.json")
    private val oldArt = File(directory, "rollback-art.json")
    val result = File(directory, "restored.txt")
    val failed = File(directory, "failed")
    var failurePending = false
        private set

    fun workspace(kind: String): File = File(directory, "$kind-${UUID.randomUUID()}").apply { mkdirs() }

    /** Keep the transport exactly as picked; large sections are read into separate private files. */
    fun read(input: InputStream): SetupBackup {
        val work = workspace("import")
        return try {
            val source = File(work, "source.zip")
            writeBackupDurably(source) { output -> copySetupAtMost(input, output, 300L * 1024 * 1024) }
            source.inputStream().use { SetupArchive.read(it, File(work, "sections")) }.copy(sourceArchive = source)
        } catch (failure: Exception) { work.deleteRecursively(); throw failure }
    }

    fun discard(backup: SetupBackup?) {
        backup?.sourceArchive?.parentFile?.takeIf { it.parentFile == directory && it.name.startsWith("import-") }?.deleteRecursively()
    }

    /** Stage first. A ready marker makes completing the before-copy rename recoverable too. */
    fun stage(incoming: SetupBackup, current: SetupBackup, afterStage: () -> Unit = {}) {
        check(!stage.exists()) { "A restore is already staged" }
        failed.delete()
        ready.delete()
        result.delete()
        try {
            val source = incoming.sourceArchive
            writeBackupDurably(stage) { output ->
                if (source != null) source.inputStream().use { copySetupAtMost(it, output, 300L * 1024 * 1024) }
                else SetupArchive.write(output, incoming)
            }
            afterStage()
            val localKeys = setOf("folder_tree", "restore_holds", "restore_folders", "restore_romm_sign_in", "restore_committed")
            val original = prefs.all.mapNotNull { (key, value) ->
                if (value != null && (BackupSettings.classify(key)?.disposition == BackupDisposition.BackedUp || key in localKeys)) key to value else null
            }.toMap()
            writeDurably(baseline.toPath(), SetupArchive.settingsJson(original).toString().encodeToByteArray())
            writeDurably(oldArt.toPath(), current.artChoices.encodeToByteArray())
            // Packs stay installed across restore/undo. Their bytes must never block the safety copy.
            writeBackupDurably(nextBefore) { SetupArchive.write(it, current.copy(themes = emptyMap(), themeFiles = emptyMap())) }
            writeDurably(attempts.toPath(), "0".encodeToByteArray())
            writeDurably(ready.toPath(), UUID.randomUUID().toString().encodeToByteArray())
            moveBackup(nextBefore, before)
        } catch (failure: Exception) {
            // Before readiness nothing can apply. A failed safety copy must not block
            // another try; after readiness startup owns the recoverable rename.
            if (!ready.isFile) {
                stage.delete()
                nextBefore.delete()
            }
            throw failure
        }
    }

    fun pending(): Boolean = stage.isFile
    fun hasNotice(): Boolean = result.isFile || failed.isFile || failurePending
    fun acknowledgeFailure() { failed.delete(); failurePending = false }

    /** A killed process spends an attempt too. Two failures preserve and quarantine the transport. */
    fun resume(granted: Set<String>, restoreThemes: (Map<String, File>) -> Unit): SetupRestoreResult {
        try { return resumeAttempt(granted, restoreThemes) }
        finally { cleanupStaleFiles() }
    }

    private fun resumeAttempt(granted: Set<String>, restoreThemes: (Map<String, File>) -> Unit): SetupRestoreResult {
        if (failed.isFile) { failurePending = true; return SetupRestoreResult.Failed }
        if (!pending()) return SetupRestoreResult.None
        if (!ready.isFile) return abandon(rollback = false)
        val id = try { ready.takeIf { it.length() <= 128 }?.readText() }
            catch (_: Exception) { return abandon(rollback = true) }
        if (id.isNullOrBlank()) return abandon(rollback = true)
        if (prefs.getString("restore_committed", null) == id) {
            finishCommit()
            return SetupRestoreResult.Restored
        }
        val used = try { attempts.takeIf { it.length() <= 16 }?.readText()?.toIntOrNull()?.takeIf { it in 0..2 } ?: 2 }
            catch (_: Exception) { return abandon(rollback = true) }
        if (used >= 2) return abandon(rollback = true)
        try { writeDurably(attempts.toPath(), (used + 1).toString().encodeToByteArray()) }
        catch (_: Exception) { return abandon(rollback = true) }
        try {
            applyStaged(granted, restoreThemes)
            return SetupRestoreResult.Restored
        } catch (failure: Exception) {
            // A normal I/O failure gets its second bounded attempt in this process. Only an
            // actual process stop needs the next start to spend the remaining attempt.
            if (used + 1 < 2) return resumeAttempt(granted, restoreThemes)
            return abandon(rollback = true)
        }
    }

    /** Startup has no active imports or exports. Keep only recovery inputs and one failed archive. */
    private fun cleanupStaleFiles() {
        val files = directory.listFiles().orEmpty()
        val recovering = ready.isFile && stage.isFile
        val newestFailure = files.filter { it.isFile && it.name.startsWith("failed-restore-") && it.extension == "zip" }
            .maxWithOrNull(compareBy<File> { it.lastModified() }.thenBy { it.name })
        for (file in files) {
            val name = file.name
            if (file == before || (recovering && (file == stage || file == nextBefore))) continue
            val stale = name.startsWith("import-") || name.startsWith("apply-") || name.startsWith("export-") ||
                name.startsWith("before-") || name.endsWith(".tmp") ||
                (name.startsWith("failed-restore-") && file.extension == "zip" && file != newestFailure)
            if (stale) file.deleteRecursively()
        }
    }

    private fun abandon(rollback: Boolean): SetupRestoreResult {
        failurePending = true
        // Renames need no second copy of the archive or art, including on a full filesystem.
        if (stage.isFile) try { moveBackup(stage, File(directory, "failed-restore-${UUID.randomUUID()}.zip")) } catch (_: Exception) { }
        if (rollback) {
            try { restoreBaselinePreferences() } catch (_: Exception) { }
            if (oldArt.isFile) try { moveBackup(oldArt, File(root, "art/choices.json")) } catch (_: Exception) { }
        }
        result.delete()
        val noticeSource = attempts.takeIf { it.isFile } ?: ready.takeIf { it.isFile }
        if (noticeSource != null) try { moveBackup(noticeSource, failed) } catch (_: Exception) { }
        ready.delete()
        return SetupRestoreResult.Failed
    }

    private fun restoreBaselinePreferences() {
        if (!baseline.isFile || baseline.length() > 32L * 1024 * 1024) return
        val original = Json.parseToJsonElement(baseline.readText()).jsonObject.mapValues { SetupArchive.readPreference(it.value) }
        val editor = prefs.edit()
        prefs.all.keys.filter { BackupSettings.classify(it)?.disposition == BackupDisposition.BackedUp ||
            it in setOf("folder_tree", "restore_holds", "restore_folders", "restore_romm_sign_in", "restore_committed") }.forEach(editor::remove)
        original.forEach { (key, value) -> editor.putValue(key, value) }
        // commit(false) still resets Android's cached values. Incoming disk preferences were
        // never committed without the marker that resume() checks before counting failures.
        editor.commit()
    }

    /** Files/result first; the committed preference marker makes the final cleanup idempotent. */
    fun applyStaged(
        granted: Set<String>, restoreThemes: (Map<String, File>) -> Unit,
        afterFiles: () -> Unit = {}, afterPreferences: () -> Unit = {},
    ) {
        if (!stage.isFile) return
        check(ready.length() in 1..128)
        val id = ready.readText()
        if (prefs.getString("restore_committed", null) == id) { finishCommit(); return }
        if (nextBefore.isFile) moveBackup(nextBefore, before)
        val work = workspace("apply")
        try {
            val backup = stage.inputStream().use { SetupArchive.read(it, work) }
            val folders = backup.manifest.folders.distinctBy { it.uri }
            val currentTrees = prefs.getString("folder_tree", null).orEmpty().lines().filter { it.isNotBlank() }
            val kept = folders.filter { it.uri in granted }
            val trees = (currentTrees + kept.map { it.uri }).distinct()
            val missing = folders.filter { it.uri !in granted }
            val holds = RestoreHolds.forBoard(decodeHome(backup.home), decodeSameGames(backup.sameGames), folders.map { it.uri })
            val plan = BackupSettings.plan(prefs.all, backup.preferences())
            val origin = plan.put["romm_origin"] as? String
            val signIn = prefs.getBoolean("restore_romm_sign_in", false) || (origin != null && origin != prefs.getString("romm_origin", null))
            writeDurably(File(root, "art/choices.json").toPath(), backup.artChoices.encodeToByteArray())
            restoreThemes(backup.themeFiles)
            val play = backup.play()
            val playFile = File(root, "play-sessions/events.log")
            writeDurably(playFile.toPath(), mergedBackupPlay(playFile, play.events))
            PlayNames(File(playFile.parentFile, "names.log")).merge(play.names, durable = true)
            PlayArt(File(playFile.parentFile, "art")).adopt(play.art)
            afterFiles()
            val todo = buildList {
                if (backup.settings["romm_origin"] != null || holds.sources.values.any { "romm" in it }) add("romm")
                if (missing.isNotEmpty()) add("folders:" + missing.joinToString(", ") { it.name })
                val saves = backup.manifest.saveFolders.filter { name -> prefs.getString("player_save_folder:$name", null).isNullOrBlank() }
                if (saves.isNotEmpty()) add("saves:" + saves.joinToString(", "))
                if (backup.manifest.computers) add("computers")
                fun chosen(prefix: String) = prefs.all.any { (key, value) -> key.startsWith(prefix) && value is String && value.isNotBlank() }
                if (backup.manifest.retroArchCores && !chosen("retroarch_cores_folder:")) add("cores")
                if (backup.manifest.edenFolder && !chosen("eden_folder:")) add("eden")
            }
            writeDurably(result.toPath(), todo.joinToString("\n").encodeToByteArray())
            val editor = prefs.edit()
            plan.remove.forEach(editor::remove)
            plan.put.forEach { (key, value) -> editor.putValue(key, value) }
            editor.putString("folder_tree", trees.joinToString("\n"))
            editor.putString("restore_folders", encodeBackupFolders(missing))
            editor.putString("restore_holds", holds.encode())
            editor.putBoolean("restore_romm_sign_in", signIn)
            editor.putString("restore_committed", id)
            if (!editor.commit()) {
                restoreBaselinePreferences()
                error("Restored preferences were not committed")
            }
            afterPreferences()
            finishCommit()
        } finally { work.deleteRecursively() }
    }

    private fun finishCommit() {
        // Preferences already contain the commit marker. A cleanup failure must not turn
        // a finished restore into a crash loop or another application of its files.
        if (stage.isFile && !stage.delete()) return
        ready.delete()
        attempts.delete()
        baseline.delete()
        oldArt.delete()
        syncBackupDirectory(directory)
    }
}

internal fun copySetupAtMost(input: InputStream, output: OutputStream, limit: Long) {
    var bytes = 0L
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return
        bytes += count
        check(bytes <= limit) { "Backup is too large" }
        output.write(buffer, 0, count)
    }
}

internal fun writeBackupDurably(target: File, body: (OutputStream) -> Unit) {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, ".${target.name}-${UUID.randomUUID()}.tmp")
    try {
        FileOutputStream(temporary).use { output ->
            val stream = object : java.io.FilterOutputStream(output) {
                override fun close() { flush() }
            }
            body(stream)
            output.fd.sync()
        }
        moveBackup(temporary, target)
    } finally { temporary.delete() }
}

private fun moveBackup(from: File, to: File) {
    to.parentFile?.mkdirs()
    try { Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
    catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    syncBackupDirectory(to.parentFile!!)
}

private fun syncBackupDirectory(directory: File) {
    try { FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) } } catch (_: Exception) { }
}

internal fun SharedPreferences.Editor.putValue(key: String, value: Any): SharedPreferences.Editor = apply {
    when (value) {
        is String -> putString(key, value)
        is Boolean -> putBoolean(key, value)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        else -> error("Unsupported preference")
    }
}

internal fun encodeBackupFolders(folders: List<BackupFolder>): String = kotlinx.serialization.json.JsonArray(folders.map {
    kotlinx.serialization.json.buildJsonObject {
        put("uri", kotlinx.serialization.json.JsonPrimitive(it.uri))
        put("name", kotlinx.serialization.json.JsonPrimitive(it.name))
    }
}).toString()

internal fun decodeBackupFolders(raw: String?): List<BackupFolder> = try {
    val array = kotlinx.serialization.json.Json.parseToJsonElement(raw ?: "[]") as kotlinx.serialization.json.JsonArray
    array.map { value ->
        val row = value as kotlinx.serialization.json.JsonObject
        BackupFolder((row.getValue("uri") as kotlinx.serialization.json.JsonPrimitive).content,
            (row.getValue("name") as kotlinx.serialization.json.JsonPrimitive).content)
    }
} catch (_: Exception) { emptyList() }
