package app.foldcade

import app.foldcade.language.*
import app.foldcade.romm.writeDurably
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/** Device-owned evidence and incidents. Never transferred with setup or Android backup. */
internal class SaveProtectionStore(private val directory: Path) {
    private val locationsFile = directory.resolve("locations.json")
    private val incidentsFile = directory.resolve("incidents.json")
    private var locations = read(locationsFile)
    private var incidentRows = read(incidentsFile)

    @Synchronized fun location(key: String, root: FolderRef, packageName: String, installFirstMillis: Long?): LocationFact {
        val row = locations.optJSONObject(key) ?: return LocationFact.Bound(root)
        if (row.optString("identity") != root.identity || row.optString("packageName") != packageName ||
            row.nullableLong("installFirstMillis") != installFirstMillis) return LocationFact.Bound(root)
        if (row.optBoolean("bound")) return LocationFact.Bound(root, row.getLong("atMillis"))
        return LocationFact.Verified(root, Evidence(EvidenceKind.valueOf(row.getString("evidenceKind")), row.getString("detail")), row.getLong("atMillis"))
    }

    @Synchronized fun hadMarkers(key: String, root: FolderRef): Boolean =
        locations.optJSONObject(key)?.let { it.optString("identity") == root.identity && it.optBoolean("markersPresent") } == true

    @Synchronized fun verified(key: String, root: FolderRef, evidence: Evidence, at: Long, packageName: String, installFirstMillis: Long?, markers: Boolean?) {
        val next = JSONObject(locations.toString())
        next.put(key, JSONObject().put("identity", root.identity).put("label", root.label)
            .put("evidenceKind", evidence.kind.name).put("detail", evidence.detail).put("atMillis", at)
            .put("packageName", packageName).put("installFirstMillis", installFirstMillis ?: JSONObject.NULL)
            .put("markersPresent", markers == true || hadMarkers(key, root)))
        writeDurably(locationsFile, next.toString().toByteArray())
        locations = next
    }

    @Synchronized fun bound(key: String, root: FolderRef, packageName: String, installFirstMillis: Long?, at: Long) {
        val next = JSONObject(locations.toString()).put(key, JSONObject().put("identity", root.identity).put("label", root.label)
            .put("packageName", packageName).put("installFirstMillis", installFirstMillis ?: JSONObject.NULL)
            .put("evidenceKind", EvidenceKind.Config.name).put("detail", "Folder chosen").put("atMillis", at).put("bound", true))
        writeDurably(locationsFile, next.toString().toByteArray())
        locations = next
    }

    @Synchronized fun incidents(): List<SaveIncident> = incidentRows.keys().asSequence().map { incidentFromJson(incidentRows.getJSONObject(it)) }
        .sortedByDescending { it.openedAtMillis }.toList()

    @Synchronized fun put(incident: SaveIncident) {
        val next = JSONObject(incidentRows.toString()).put(incident.id, incident.toJson())
        writeDurably(incidentsFile, next.toString().toByteArray())
        incidentRows = next
    }

    @Synchronized fun close(id: String) {
        val next = JSONObject(incidentRows.toString()).apply { remove(id) }
        writeDurably(incidentsFile, next.toString().toByteArray())
        incidentRows = next
    }

    private fun read(file: Path): JSONObject {
        val text = try { Files.readAllBytes(file).toString(Charsets.UTF_8) } catch (_: NoSuchFileException) { return JSONObject() }
        return try {
            JSONObject(text).also { json ->
                json.keys().forEach { key ->
                    val row = json.getJSONObject(key)
                    if (file == incidentsFile) incidentFromJson(row) else {
                        row.getString("identity"); row.getString("label"); row.getString("packageName")
                        EvidenceKind.valueOf(row.getString("evidenceKind")); row.getString("detail"); row.getLong("atMillis")
                    }
                }
            }
        } catch (_: Exception) {
            Files.move(file, file.resolveSibling("${file.fileName}.unreadable-${System.currentTimeMillis()}"))
            JSONObject()
        }
    }
}

internal fun FolderRef.toJson(): JSONObject = JSONObject().put("identity", identity).put("label", label)
internal fun folderFromJson(json: JSONObject): FolderRef = FolderRef(json.getString("identity"), json.getString("label"))
internal fun Evidence.toJson(): JSONObject = JSONObject().put("kind", kind.name).put("detail", detail)
internal fun evidenceFromJson(json: JSONObject): Evidence = Evidence(EvidenceKind.valueOf(json.getString("kind")), json.getString("detail"))
internal fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)
private fun SaveIncident.toJson(): JSONObject = JSONObject().put("id", id).put("kind", kind.name).put("playerKey", playerKey)
    .put("playerName", playerName).put("gameIds", JSONArray(gameIds)).put("bound", bound?.toJson() ?: JSONObject.NULL)
    .put("live", live?.toJson() ?: JSONObject.NULL).put("evidence", evidence.toJson()).put("openedAtMillis", openedAtMillis)
    .put("kept", JSONArray(kept)).put("staged", JSONArray(staged)).put("repair", when (repair) {
        RepairState.NotStarted -> "NotStarted"
        RepairState.Binding -> "Binding"
        RepairState.Done -> "Done"
    })
private fun incidentFromJson(json: JSONObject): SaveIncident = SaveIncident(
    json.getString("id"), IncidentKind.valueOf(json.getString("kind")), json.getString("playerKey"), json.getString("playerName"),
    json.getJSONArray("gameIds").strings(), json.optJSONObject("bound")?.let(::folderFromJson), json.optJSONObject("live")?.let(::folderFromJson),
    evidenceFromJson(json.getJSONObject("evidence")), json.getLong("openedAtMillis"),
    json.optJSONArray("kept")?.strings().orEmpty(), json.optJSONArray("staged")?.strings().orEmpty(),
    when (json.optString("repair")) { "Binding" -> RepairState.Binding; "Done" -> RepairState.Done; else -> RepairState.NotStarted },
)

internal fun ProbeSnapshot.toJson(): JSONObject = JSONObject().put("atMillis", atMillis).put("installFirstMillis", installFirstMillis ?: JSONObject.NULL)
    .put("packageName", packageName).put("roots", JSONArray(roots.map { root -> JSONObject()
        .put("root", root.root.toJson()).put("bound", root.bound).put("spotHash", root.spotHash ?: JSONObject.NULL)
        .put("spotReadable", root.spotReadable).put("markersPresent", root.markersPresent ?: JSONObject.NULL)
        .put("emulatorHolds", root.emulatorHolds ?: JSONObject.NULL).put("hadMarkers", root.hadMarkers)
        .put("hasKnownSave", root.hasKnownSave).put("reachable", root.reachable)
        .put("signals", JSONArray(root.signals.map { signal -> JSONObject().put("source", signal.source)
            .put("names", signal.names ?: JSONObject.NULL).put("modifiedAtMillis", signal.modifiedAtMillis ?: JSONObject.NULL)
            .put("proves", signal.proves).put("perStart", signal.perStart) }))
    }))
internal fun snapshotFromJson(json: JSONObject): ProbeSnapshot = ProbeSnapshot(json.getLong("atMillis"), json.nullableLong("installFirstMillis"),
    json.getString("packageName"), json.getJSONArray("roots").let { roots -> (0 until roots.length()).map { index ->
        val root = roots.getJSONObject(index)
        RootReading(folderFromJson(root.getJSONObject("root")), root.getBoolean("bound"), root.getJSONArray("signals").let { signals ->
            (0 until signals.length()).map { i -> signals.getJSONObject(i).let { signal -> SignalReading(signal.getString("source"),
                signal.optString("names").takeUnless { signal.isNull("names") }, signal.nullableLong("modifiedAtMillis"), signal.getBoolean("proves"), signal.getBoolean("perStart")) } }
        }, root.optString("spotHash").takeUnless { root.isNull("spotHash") }, root.getBoolean("spotReadable"),
            if (root.isNull("markersPresent")) null else root.getBoolean("markersPresent"),
            if (root.isNull("emulatorHolds")) null else root.getBoolean("emulatorHolds"),
            root.optBoolean("hadMarkers"), root.optBoolean("hasKnownSave"), root.optBoolean("reachable", true))
    } })
