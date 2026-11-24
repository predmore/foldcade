package app.foldcade

import android.util.Log
import app.foldcade.language.*
import app.foldcade.romm.writeDurably
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Device history must survive an APK replacement, but must never follow a setup backup. */
internal class UpdateStore(private val file: File, scope: CoroutineScope) {
    private val notesFile = File(file.parentFile, "notes.json")
    private var ledger = UpdateLedger()
    private val notes = mutableMapOf<String, List<ReleaseNotes>>()
    private val fetched = mutableSetOf<String>()
    private val hiddenReady = mutableMapOf<String, Long>()
    private val writes = Channel<Unit>(Channel.CONFLATED)
    private val writing = Mutex()
    private var ledgerDirty = false
    private var notesDirty = false
    var lastAutomaticCheck: Long = 0
        private set

    init {
        try {
            val root = JSONObject(file.readText())
            val versions = root.getJSONObject("versions")
            ledger = UpdateLedger(versions.keys().asSequence().associateWith { key ->
                val v = versions.getJSONObject(key)
                ComponentVersion(v.getLong("code"), v.getString("name"), v.optionalLong("previousCode"), v.optionalString("previousName"))
            }, root.getJSONArray("unread").objects().map { v ->
                ComponentUpdate(v.getString("package"), v.getString("label"), v.getLong("fromCode"), v.optionalString("fromName"),
                    v.getLong("toCode"), v.getString("toName"), v.getLong("atMillis"))
            })
            // Migrate the original combined file without losing offline notes or read state.
            if (!notesFile.isFile && root.has("notes")) { loadNotes(root); ledgerDirty = true; notesDirty = true }
            root.optJSONObject("hiddenReady")?.let { saved -> saved.keys().forEach { hiddenReady[it] = saved.getLong(it) } }
            lastAutomaticCheck = root.optLong("lastAutomaticCheck")
        } catch (_: Exception) { /* A missing or damaged ledger establishes a fresh baseline. */ }
        try { loadNotes(JSONObject(notesFile.readText())) } catch (_: Exception) { /* Cache misses can be fetched again. */ }
        scope.launch(Dispatchers.IO) { for (ignored in writes) flush() }
        if (ledgerDirty || notesDirty) writes.trySend(Unit)
    }

    @Synchronized fun observe(packageName: String, label: String, code: Long, name: String, now: Long) {
        val next = ledger.observed(packageName, label, code, name, now)
        if (next != ledger) { ledger = next; changed() }
    }
    @Synchronized fun unread(): List<ComponentUpdate> = ledger.unread
    @Synchronized fun dismiss(read: Set<String>, ready: Map<String, Long> = emptyMap()) {
        ledger = ledger.copy(unread = ledger.unread.filterNot { it.packageName in read })
        hiddenReady.putAll(ready)
        changed()
    }
    fun read(packageName: String) = dismiss(setOf(packageName))
    fun hideReady(packageName: String, code: Long) = dismiss(emptySet(), mapOf(packageName to code))
    @Synchronized fun readyHidden(packageName: String, code: Long): Boolean = hiddenReady[packageName] == code
    @Synchronized fun checked(now: Long) { lastAutomaticCheck = now; changed() }

    @Synchronized fun update(packageName: String, label: String): ComponentUpdate? {
        ledger.unread.firstOrNull { it.packageName == packageName }?.let { return it }
        val v = ledger.versions[packageName] ?: return null
        return ComponentUpdate(packageName, label, v.previousCode ?: (v.code - 1), v.previousName, v.code, v.name, 0)
    }

    @Synchronized fun keepNotes(packageName: String, incoming: List<ReleaseNotes>, range: String? = null) {
        notes[packageName] = (incoming + notes[packageName].orEmpty()).distinctBy { it.code to it.tag }.take(60)
        range?.let { fetched += it }
        // Only the most recent page ranges are useful; notes themselves remain available for unread updates.
        if (fetched.size > 60) fetched.remove(fetched.first())
        notesDirty = true
        writes.trySend(Unit)
    }
    @Synchronized fun cached(update: ComponentUpdate): List<ReleaseNotes>? =
        if (rangeKey(update) in fetched) notesInRange(notes[update.packageName].orEmpty(), update.fromCode, update.toCode) else null
    @Synchronized fun known(update: ComponentUpdate): List<ReleaseNotes> = notesInRange(notes[update.packageName].orEmpty(), update.fromCode, update.toCode)

    private fun loadNotes(root: JSONObject) {
        root.optJSONObject("notes")?.let { saved -> saved.keys().forEach { key ->
            notes[key] = saved.getJSONArray(key).objects().map { v ->
                ReleaseNotes(v.optionalLong("code"), v.getString("name"), v.getString("body"), v.optBoolean("prerelease"), v.optString("tag"))
            }
        } }
        root.optJSONArray("fetched")?.let { saved -> for (i in 0 until saved.length()) fetched += saved.getString(i) }
    }

    private fun changed() { ledgerDirty = true; writes.trySend(Unit) }

    /** No caller holds the memory lock during IO; a slow/full disk cannot block notice reads. */
    suspend fun flush(): Unit = withContext(Dispatchers.IO) {
        writing.withLock {
            val pending = synchronized(this@UpdateStore) {
                val ledger = if (ledgerDirty) ledgerJson().toString().also { ledgerDirty = false } else null
                val notes = if (notesDirty) notesJson().toString().also { notesDirty = false } else null
                ledger to notes
            }
            pending.first?.let { save(file, it) }
            pending.second?.let { save(notesFile, it) }
            Unit
        }
    }

    private fun save(destination: File, json: String) {
        try { writeDurably(destination.toPath(), json.toByteArray()) }
        catch (failure: IOException) { Log.w("FoldcadeUpdates", "Update metadata not saved: ${destination.name}", failure) }
    }

    private fun ledgerJson(): JSONObject {
        val versions = JSONObject()
        ledger.versions.forEach { (key, v) -> versions.put(key, JSONObject().put("code", v.code).put("name", v.name)
            .put("previousCode", v.previousCode).put("previousName", v.previousName)) }
        val unread = JSONArray()
        ledger.unread.forEach { v -> unread.put(JSONObject().put("package", v.packageName).put("label", v.label)
            .put("fromCode", v.fromCode).put("fromName", v.fromName).put("toCode", v.toCode).put("toName", v.toName).put("atMillis", v.atMillis)) }
        return JSONObject().put("versions", versions).put("unread", unread)
            .put("hiddenReady", JSONObject(hiddenReady.toMap())).put("lastAutomaticCheck", lastAutomaticCheck)
    }

    private fun notesJson(): JSONObject {
        val savedNotes = JSONObject()
        notes.forEach { (key, releases) -> savedNotes.put(key, JSONArray().apply { releases.forEach { v ->
            put(JSONObject().put("code", v.code).put("name", v.name).put("body", v.body).put("prerelease", v.prerelease).put("tag", v.tag))
        } }) }
        return JSONObject().put("notes", savedNotes).put("fetched", JSONArray(fetched.toList()))
    }
}

internal fun rangeKey(update: ComponentUpdate): String = "${update.packageName}:${update.fromCode}:${update.toCode}"
private fun JSONObject.optionalLong(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
private fun JSONObject.optionalString(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull(::optJSONObject)
