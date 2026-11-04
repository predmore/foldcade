package app.foldcade

import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** A provider must support a single atomic replacement; a pair of SAF renames does not qualify. */
internal interface PresetFile {
    /** Null means absent. An unreadable file must throw. */
    fun read(): ByteArray?
    fun backup(name: String, bytes: ByteArray)
    /** A surviving adjacent backup also consumes the attempt after reinstall or lost private state. */
    fun hasPriorBackup(): Boolean = false
    /** Recheck both the bytes and the emulator's stopped state immediately before the replacement. */
    fun replace(expected: ByteArray?, bytes: ByteArray?, stopped: () -> Boolean): Boolean
}

internal enum class ThorPresetStatus { Ready, Applied, Kept, Restored, CloseEmulator, FileAccess, ChooseFolder, Manual, Failed }

internal data class PresetEdit(val section: String, val key: String, val before: String?, val after: String)
internal data class PresetPlan(val bytes: ByteArray, val edits: List<PresetEdit>)

/**
 * Azahar 2126.2 defaults, pinned to 219d743a1e698101fe1ab8bef43e6563126c3c27:
 * https://github.com/azahar-emu/azahar/blob/219d743a1e698101fe1ab8bef43e6563126c3c27/src/android/app/src/main/jni/config.cpp
 * ReadValues overrides Android's accurate-mul (false), layout (2), and secondary layout (0).
 * resolution_factor defaults to 1 in src/common/settings.h. Empty INI values use these defaults.
 * The generated INI incorrectly puts secondary layout in Storage; native reads Layout.
 * GPU drivers are installed to PRIVATE files/gpu_driver by GpuDriverHelper.kt. A ZIP in the
 * user tree is not an installed driver, so no driver or graphics backend is ever selected here.
 */
internal fun azaharThorPreset(original: ByteArray): PresetPlan? {
    val text = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(original)).toString()
    } catch (_: Exception) { return null }
    if ('\u0000' in text) return null
    val ini = PresetIni(text)
    if (!ini.valid) return null
    val edits = listOf(
        arrayOf("Layout", "layout_option", "2", "1"),
        arrayOf("Layout", "secondary_display_layout", "0", "4"),
        arrayOf("Renderer", "resolution_factor", "1", "5"),
        arrayOf("Renderer", "shaders_accurate_mul", "false", "true"),
    ).mapNotNull { (section, key, default, desired) ->
        val before = ini.value(section, key)
        val value = before?.lowercase().orEmpty()
        val untouched = value.isEmpty() || value == default || (default == "false" && value == "0")
        if (untouched) PresetEdit(section, key, before, desired) else null
    }
    return PresetPlan(ini.patch(edits).toByteArray(Charsets.UTF_8), edits)
}

/**
 * Preserve comments, unrelated bytes, CRLF and the player's values. Ambiguous INIs stay untouched.
 * Case folding matches Azahar's bundled INIReader::MakeKey:
 * https://github.com/benhoyt/inih/blob/5cc5e2c24642513aaa5b19126aad42d0e4e0923e/cpp/INIReader.cpp
 */
private class PresetIni(private val text: String) {
    private val newline = if ("\r\n" in text) "\r\n" else "\n"
    private val values = linkedMapOf<Pair<String, String>, String>()
    private val indexes = mutableMapOf<Pair<String, String>, Int>()
    private val lines = text.splitToSequence('\n').toList()
    var valid = true
        private set

    init {
        var section = ""
        val sections = HashSet<String>()
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim().removePrefix("\uFEFF")
            when {
                trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith(';') -> Unit
                trimmed.startsWith('[') -> {
                    if (!trimmed.endsWith(']')) { valid = false; continue }
                    section = trimmed.substring(1, trimmed.length - 1).lowercase(java.util.Locale.ROOT)
                    if (section != section.trim()) valid = false
                    if (!sections.add(section)) valid = false
                }
                '=' in line -> {
                    val key = section to line.substringBefore('=').trim().lowercase(java.util.Locale.ROOT)
                    if (key in values || key.second.isEmpty()) valid = false
                    values[key] = line.substringAfter('=').trim()
                    indexes[key] = index
                }
                else -> valid = false
            }
        }
    }

    private fun name(section: String, key: String) = section.lowercase(java.util.Locale.ROOT) to key.lowercase(java.util.Locale.ROOT)
    fun value(section: String, key: String): String? = values[name(section, key)]

    fun patch(edits: List<PresetEdit>): String {
        if (edits.isEmpty()) return text
        val replacements = edits.mapNotNull { edit -> indexes[name(edit.section, edit.key)]?.let { it to edit } }.toMap()
        val out = lines.mapIndexed { index, line ->
            replacements[index]?.let { edit ->
                line.substringBefore('=') + "= " + edit.after + if (line.endsWith('\r')) "\r" else ""
            } ?: line
        }.joinToString("\n")
        val missing = edits.filter { name(it.section, it.key) !in indexes }.groupBy { it.section }
        if (missing.isEmpty()) return out
        val patched = out.split('\n').toMutableList()
        for ((section, additions) in missing) {
            val heading = patched.indexOfFirst { it.trim().removePrefix("\uFEFF") .equals("[$section]", ignoreCase = true) }
            val insert = if (heading < 0) patched.size else
                (heading + 1 until patched.size).firstOrNull { patched[it].trim().startsWith('[') } ?: patched.size
            val extra = additions.map { "${it.key} = ${it.after}" + if (newline == "\r\n") "\r" else "" }
            if (heading < 0) {
                if (patched.lastOrNull()?.isNotEmpty() == true) patched.add("")
                patched.add("[$section]" + if (newline == "\r\n") "\r" else "")
                patched.addAll(extra)
                patched.add("")
            } else {
                patched.addAll(insert, extra)
            }
        }
        return patched.joinToString("\n")
    }
}

/**
 * One attempt per emulator and user folder, independent of the installed version. The journal is
 * durable BEFORE touching the emulator: a crash after replacement can never cause re-application.
 * Even a value the player later resets to its upstream default stays theirs. Backups never expire.
 * All callers run off Main. The same monitor covers settings, launches, and undo.
 */
internal class ThorPresets(private val root: Path) {
    @Synchronized
    fun status(identity: String, file: PresetFile): ThorPresetStatus {
        val record = record(identity) ?: return ThorPresetStatus.Ready
        return when (record.getString("phase")) {
            "restored" -> ThorPresetStatus.Restored
            "kept" -> ThorPresetStatus.Kept
            "prepared" -> if (file.read()?.presetHash() == record.optString("appliedHash")) ThorPresetStatus.Applied else ThorPresetStatus.Kept
            "applied" -> ThorPresetStatus.Applied
            else -> ThorPresetStatus.Failed
        }
    }

    @Synchronized
    fun apply(identity: String, file: PresetFile, onApplied: () -> Unit = {}, stopped: () -> Boolean): ThorPresetStatus {
        val previous = record(identity)
        // Only a failure observed before any replacement may retry. A prepared crash is
        // ambiguous (the player might have changed the applied value back), so never retry it.
        val retry = previous?.optString("phase") == "failed"
        if (previous != null && !retry) return status(identity, file)
        if (!retry && file.hasPriorBackup()) {
            val dir = directory(identity)
            Files.createDirectories(dir)
            saveRecord(dir, JSONObject().put("phase", "kept").put("identity", identity))
            return ThorPresetStatus.Kept
        }
        val original = file.read()
        val plan = azaharThorPreset(original ?: byteArrayOf()) ?: return ThorPresetStatus.Failed
        val dir = directory(identity)
        Files.createDirectories(dir)
        if (plan.edits.isEmpty()) {
            saveRecord(dir, JSONObject().put("phase", "kept").put("identity", identity))
            return ThorPresetStatus.Kept
        }
        if (!stopped()) return ThorPresetStatus.CloseEmulator
        // Snapshot both versions before publishing the consumed-attempt journal.
        durablePresetWrite(dir.resolve("original.ini"), original ?: byteArrayOf())
        durablePresetWrite(dir.resolve("applied.ini"), plan.bytes)
        val backupName = "config.ini.foldcade-original-${UUID.randomUUID()}"
        file.backup(backupName, original ?: byteArrayOf())
        val journal = JSONObject().put("phase", "prepared").put("identity", identity)
            .put("originalExists", original != null).put("originalHash", original?.presetHash())
            .put("appliedHash", plan.bytes.presetHash()).put("backup", backupName)
            .put("edits", JSONArray(plan.edits.map { edit -> JSONObject()
                .put("section", edit.section).put("key", edit.key)
                .put("before", edit.before ?: JSONObject.NULL).put("after", edit.after) }))
        saveRecord(dir, journal)
        try {
            if (!file.replace(original, plan.bytes, stopped)) {
                noteFailedReplacement(dir, journal, file, original)
                return ThorPresetStatus.Failed
            }
        } catch (failure: Exception) {
            noteFailedReplacement(dir, journal, file, original)
            throw failure
        }
        saveRecord(dir, journal.put("phase", "applied"))
        onApplied()
        return ThorPresetStatus.Applied
    }

    @Synchronized
    fun canRetry(identity: String): Boolean = record(identity)?.optString("phase") == "failed"

    @Synchronized
    fun edits(identity: String): Set<String> = record(identity)?.optJSONArray("edits")?.let { edits ->
        (0 until edits.length()).map { edits.getJSONObject(it).getString("key") }.toSet()
    }.orEmpty()

    @Synchronized
    fun canRestore(identity: String): Boolean = record(identity)?.let {
        it.optString("phase") !in setOf("restored", "failed") && it.has("originalExists")
    } == true

    @Synchronized
    fun restore(identity: String, file: PresetFile, stopped: () -> Boolean): ThorPresetStatus {
        val journal = record(identity) ?: return ThorPresetStatus.Ready
        if (!canRestore(identity)) return status(identity, file)
        if (!stopped()) return ThorPresetStatus.CloseEmulator
        val dir = directory(identity)
        val original = if (journal.getBoolean("originalExists")) Files.readAllBytes(dir.resolve("original.ini")) else null
        check(original?.presetHash() == journal.optString("originalHash").takeIf { it.isNotEmpty() })
        val current = file.read()
        // An explicit full-file restore keeps today's settings as well, next to the config and in Foldcade.
        if (current != null) {
            val name = "config.ini.foldcade-before-restore-${UUID.randomUUID()}"
            durablePresetWrite(dir.resolve(name), current)
            file.backup(name, current)
        }
        saveRecord(dir, journal.put("phase", "restoring"))
        if (!file.replace(current, original, stopped)) return ThorPresetStatus.Failed
        saveRecord(dir, journal.put("phase", "restored"))
        return ThorPresetStatus.Restored
    }

    private fun noteFailedReplacement(dir: Path, journal: JSONObject, file: PresetFile, original: ByteArray?) {
        val current = runCatching { file.read() }.getOrElse { return }
        if ((current == null && original == null) || (current != null && original != null && current.contentEquals(original))) {
            saveRecord(dir, journal.put("phase", "failed"))
        }
    }

    private fun directory(identity: String) = root.resolve(identity.toByteArray().presetHash())
    private fun record(identity: String): JSONObject? {
        val path = directory(identity).resolve("record.json")
        return if (Files.exists(path)) JSONObject(Files.readAllBytes(path).toString(Charsets.UTF_8)) else null
    }
    private fun saveRecord(dir: Path, record: JSONObject) = durablePresetWrite(dir.resolve("record.json"), record.toString(2).toByteArray())
}

/** No truncate-in-place fallback: unsupported atomic moves leave the old file intact. */
internal fun durablePresetWrite(path: Path, bytes: ByteArray) {
    Files.createDirectories(path.parent)
    val temporary = Files.createTempFile(path.parent, ".foldcade-preset-", ".tmp")
    try {
        FileOutputStream(temporary.toFile()).use { stream -> stream.write(bytes); stream.fd.sync() }
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally { Files.deleteIfExists(temporary) }
}

internal fun ByteArray.presetHash(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
