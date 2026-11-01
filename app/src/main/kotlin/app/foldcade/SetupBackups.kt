package app.foldcade

import android.content.SharedPreferences
import app.foldcade.host.backup.*
import app.foldcade.host.play.mergedBackupPlay
import app.foldcade.host.play.PlayNames
import app.foldcade.language.*
import app.foldcade.romm.writeDurably
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/** Disk work only. The stage is replayed before any live store can read or save a preference. */
internal class SetupBackups(private val root: File, private val prefs: SharedPreferences) {
    private val directory = File(root, "backup")
    val before = File(directory, "before-restore.zip")
    private val stage = File(directory, "staged.zip")
    val result = File(directory, "restored.txt")

    fun encode(backup: SetupBackup): ByteArray = ByteArrayOutputStream().also { SetupArchive.write(it, backup) }.toByteArray()

    /** Keep the undo copy before publishing the stage. No running state has changed yet. */
    fun stage(incoming: SetupBackup, current: SetupBackup) {
        val next = encode(incoming)
        val previous = encode(current)
        // Read our output too, so a snapshot that cannot round-trip never becomes the undo copy.
        SetupArchive.read(previous.inputStream())
        writeDurably(before.toPath(), previous)
        writeDurably(stage.toPath(), next)
    }

    fun pending(): Boolean = stage.isFile

    /**
     * A stop at any point leaves the stage. The next process repeats the replacement and
     * the idempotent play merge before constructing SessionStore or ShellController.
     * Errors propagate: a partly applied setup must never be opened by the shell.
     */
    fun applyStaged(
        granted: Set<String>, restoreThemes: (Map<String, ByteArray>) -> Unit,
        afterFiles: () -> Unit = {}, afterPreferences: () -> Unit = {},
    ) {
        if (!stage.isFile) return
        val backup = stage.inputStream().use(SetupArchive::read)
        val board = decodeHome(backup.home)
        val folders = backup.manifest.folders.distinctBy { it.uri }
        val kept = folders.filter { it.uri in granted }
        val missing = folders - kept.toSet()
        val holds = RestoreHolds.forBoard(board, decodeSameGames(backup.sameGames), folders.map { it.uri })
        val plan = BackupSettings.plan(prefs.all, backup.preferences())
        // An archive may choose another server. A token kept on this device must never be
        // sent to that address until the player signs in there. The secret itself stays put.
        val origin = plan.put["romm_origin"] as? String
        val signIn = origin != null && (prefs.getBoolean("restore_romm_sign_in", false) || origin != prefs.getString("romm_origin", null))

        writeDurably(File(root, "art/choices.json").toPath(), backup.artChoices.encodeToByteArray())
        restoreThemes(backup.themes)
        val play = backup.play()
        val playFile = File(root, "play-sessions/events.log")
        writeDurably(playFile.toPath(), mergedBackupPlay(playFile, play.events))
        PlayNames(File(playFile.parentFile, "names.log")).merge(play.names, durable = true)
        PlayArt(File(playFile.parentFile, "art")).adopt(play.art)
        afterFiles()

        val editor = prefs.edit()
        plan.remove.forEach(editor::remove)
        plan.put.forEach { (key, value) -> editor.putValue(key, value) }
        // Library trees are the one special case: transport the list, never transport grants.
        editor.putString("folder_tree", kept.joinToString("\n") { it.uri })
        editor.putString("restore_folders", encodeBackupFolders(missing))
        editor.putString("restore_holds", holds.encode())
        editor.putBoolean("restore_romm_sign_in", signIn)
        check(editor.commit()) { "Restored preferences were not committed" }
        afterPreferences()
        val todo = buildList {
            if (backup.settings["romm_origin"] != null || holds.sources.values.any { "romm" in it }) add("romm")
            if (missing.isNotEmpty()) add("folders:" + missing.joinToString(", ") { it.name })
            val saveFolders = backup.manifest.saveFolders.filter { name ->
                prefs.all.none { (key, value) -> key == "player_save_folder:$name" && value is String && value.isNotBlank() }
            }
            if (saveFolders.isNotEmpty()) add("saves:" + saveFolders.joinToString(", "))
            if (backup.manifest.computers) add("computers")
            fun chosen(prefix: String) = prefs.all.any { (key, value) -> key.startsWith(prefix) && value is String && value.isNotBlank() }
            if (backup.manifest.retroArchCores && !chosen("retroarch_cores_folder:")) add("cores")
            if (backup.manifest.edenFolder && !chosen("eden_folder:")) add("eden")
        }
        writeDurably(result.toPath(), todo.joinToString("\n").encodeToByteArray())
        check(stage.delete()) { "Restore stage was not cleared" }
        syncDirectory()
    }

    private fun syncDirectory() {
        runCatching { FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) } }
    }
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
