package app.foldcade.plugins.romm

import app.foldcade.api.plugin.SaveConflict
import app.foldcade.api.plugin.SaveConflictChoice
import app.foldcade.api.plugin.SaveCopy
import app.foldcade.romm.writeDurably
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Which bytes the slot file should hold. [Leave] keeps the file that is already there. */
internal enum class SlotHold {
    Local,
    Remote,
    Leave,
}

/** What one choice does to the prompt and the slot. Neither copy is deleted. */
internal data class ConflictDecision(
    val askAgain: Boolean,
    val slot: SlotHold,
)

internal fun conflictDecision(choice: SaveConflictChoice): ConflictDecision = when (choice) {
    SaveConflictChoice.KeepLocal -> ConflictDecision(askAgain = false, slot = SlotHold.Local)
    SaveConflictChoice.KeepRemote -> ConflictDecision(askAgain = false, slot = SlotHold.Remote)
    SaveConflictChoice.KeepBoth -> ConflictDecision(askAgain = false, slot = SlotHold.Leave)
    SaveConflictChoice.DecideLater -> ConflictDecision(askAgain = true, slot = SlotHold.Leave)
}

internal enum class ConflictStatus {
    Open,
    Deferred,
    Closed,
}

/**
 * One conflict this device is holding.
 * [localCopy] and [remoteCopy] are the preserved bytes. Resolving never deletes them.
 */
internal data class ConflictEntry(
    val id: String,
    val romId: Long,
    val slot: String,
    val gameLabel: String,
    val localSource: String,
    val localUpdatedAtMillis: Long,
    val localSizeBytes: Long,
    val localHash: String,
    val remoteSource: String,
    val remoteUpdatedAtMillis: Long,
    val remoteSizeBytes: Long,
    val remoteHash: String,
    val localCopy: String,
    val remoteCopy: String,
    val slotFile: String,
    val status: ConflictStatus,
    val choice: String?,
)

internal fun ConflictEntry.toConflict(): SaveConflict = SaveConflict(
    id = id,
    libraryId = ROMM_LIBRARY_ID,
    gameLabel = gameLabel,
    local = SaveCopy(localSource, localUpdatedAtMillis, localSizeBytes),
    remote = SaveCopy(remoteSource, remoteUpdatedAtMillis, remoteSizeBytes),
)

internal fun saveSource(device: String?, emulator: String?, fallback: String): String {
    val deviceName = device?.takeIf { it.isNotBlank() }
    val program = emulator?.takeIf { it.isNotBlank() }
    return when {
        deviceName != null && program != null -> "$deviceName · $program"
        deviceName != null -> deviceName
        program != null -> program
        else -> fallback
    }
}

/**
 * Conflicts waiting on disk for one RomM server.
 * An unreadable file reads as empty, so a bad write cannot delete the preserved copies.
 * The caller may be on any thread. One file is written by one lock.
 */
internal class SaveConflictLog(private val file: Path) {
    fun pending(): List<ConflictEntry> = locked { readUnlocked().filter { it.status == ConflictStatus.Open } }

    fun find(id: String): ConflictEntry? = locked { readUnlocked().find { it.id == id } }

    /**
     * Remembers [incoming]. The same two hashes keep a choice the player already made,
     * and a Keep-local choice is written back onto the slot if a sync replaced it.
     * Different bytes open the prompt again. Returns true when the pending set may have changed.
     */
    fun record(incoming: ConflictEntry): Boolean = locked {
        val current = readUnlocked().toMutableList()
        val index = current.indexOfFirst { it.id == incoming.id }
        if (index < 0) {
            current += incoming.copy(status = ConflictStatus.Open, choice = null)
            writeUnlocked(current)
            return@locked true
        }
        val old = current[index]
        val same = old.localHash == incoming.localHash && old.remoteHash == incoming.remoteHash
        val stored = if (!same || old.status == ConflictStatus.Open) {
            incoming.copy(status = ConflictStatus.Open, choice = if (same) old.choice else null)
        } else {
            old.copy(
                gameLabel = incoming.gameLabel,
                localSource = incoming.localSource,
                localUpdatedAtMillis = incoming.localUpdatedAtMillis,
                localSizeBytes = incoming.localSizeBytes,
                remoteSource = incoming.remoteSource,
                remoteUpdatedAtMillis = incoming.remoteUpdatedAtMillis,
                remoteSizeBytes = incoming.remoteSizeBytes,
                localCopy = incoming.localCopy,
                remoteCopy = incoming.remoteCopy,
                slotFile = incoming.slotFile,
            )
        }
        current[index] = stored
        writeUnlocked(current)
        if (stored.status == ConflictStatus.Closed && stored.choice == SaveConflictChoice.KeepLocal.name) {
            applyFiles(stored, SaveConflictChoice.KeepLocal)
        }
        stored.status == ConflictStatus.Open
    }

    /**
     * Applies [choice]. False when [id] is unknown or a preserved copy is missing:
     * the prompt stays, and nothing is deleted.
     */
    fun resolve(id: String, choice: SaveConflictChoice): Boolean = locked {
        val current = readUnlocked()
        val entry = current.find { it.id == id } ?: return@locked false
        applyFiles(entry, choice)
        val decision = conflictDecision(choice)
        val status = if (decision.askAgain) ConflictStatus.Deferred else ConflictStatus.Closed
        writeUnlocked(current.map { if (it.id == id) it.copy(status = status, choice = choice.name) else it })
        true
    }

    /** True when a deferred conflict is pending again. */
    fun reopenDeferred(): Boolean = locked {
        val current = readUnlocked()
        if (current.none { it.status == ConflictStatus.Deferred }) return@locked false
        writeUnlocked(
            current.map { entry ->
                if (entry.status == ConflictStatus.Deferred) entry.copy(status = ConflictStatus.Open) else entry
            },
        )
        true
    }

    private fun readUnlocked(): List<ConflictEntry> {
        val bytes = try {
            Files.readAllBytes(file)
        } catch (_: NoSuchFileException) {
            return emptyList()
        } catch (_: IOException) {
            return emptyList()
        }
        val root = try {
            Json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val items = root["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            readEntry(obj)
        }
    }

    private fun writeUnlocked(entries: List<ConflictEntry>) {
        val root = buildJsonObject {
            put(
                "items",
                buildJsonArray {
                    entries.sortedBy { it.id }.forEach { entry -> add(writeEntry(entry)) }
                },
            )
        }
        writeDurably(file, root.toString().toByteArray(Charsets.UTF_8))
    }

    private inline fun <T> locked(block: () -> T): T = synchronized(gate(file), block)

    companion object {
        private val gates = ConcurrentHashMap<String, Any>()

        private fun gate(file: Path): Any =
            gates.computeIfAbsent(file.toAbsolutePath().normalize().toString()) { Any() }
    }
}

/**
 * Puts the chosen bytes in the slot when the choice names one.
 * Both preserved copies are read back afterwards and must still match.
 */
internal fun applyFiles(entry: ConflictEntry, choice: SaveConflictChoice) {
    val local = Paths.get(entry.localCopy)
    val remote = Paths.get(entry.remoteCopy)
    if (!Files.isRegularFile(local) || !Files.isRegularFile(remote)) {
        throw IOException("a save copy is missing")
    }
    val localBytes = Files.readAllBytes(local)
    val remoteBytes = Files.readAllBytes(remote)
    when (conflictDecision(choice).slot) {
        SlotHold.Local -> writeDurably(Paths.get(entry.slotFile), localBytes)
        SlotHold.Remote -> writeDurably(Paths.get(entry.slotFile), remoteBytes)
        SlotHold.Leave -> Unit
    }
    if (!Files.readAllBytes(local).contentEquals(localBytes)) {
        throw IOException("the local save was lost")
    }
    if (!Files.readAllBytes(remote).contentEquals(remoteBytes)) {
        throw IOException("the other save was lost")
    }
}

private fun readEntry(obj: JsonObject): ConflictEntry? {
    val id = obj.string("id") ?: return null
    val slot = obj.string("slot") ?: return null
    val status = when (obj.string("status")) {
        "deferred" -> ConflictStatus.Deferred
        "closed" -> ConflictStatus.Closed
        "open" -> ConflictStatus.Open
        else -> return null
    }
    return ConflictEntry(
        id = id,
        romId = obj["rom_id"]?.jsonPrimitive?.longOrNull ?: return null,
        slot = slot,
        gameLabel = obj.string("game_label") ?: return null,
        localSource = obj.string("local_source") ?: return null,
        localUpdatedAtMillis = obj["local_updated_at"]?.jsonPrimitive?.longOrNull ?: return null,
        localSizeBytes = obj["local_size"]?.jsonPrimitive?.longOrNull ?: return null,
        localHash = obj.string("local_hash") ?: return null,
        remoteSource = obj.string("remote_source") ?: return null,
        remoteUpdatedAtMillis = obj["remote_updated_at"]?.jsonPrimitive?.longOrNull ?: return null,
        remoteSizeBytes = obj["remote_size"]?.jsonPrimitive?.longOrNull ?: return null,
        remoteHash = obj.string("remote_hash") ?: return null,
        localCopy = obj.string("local_copy") ?: return null,
        remoteCopy = obj.string("remote_copy") ?: return null,
        slotFile = obj.string("slot_file") ?: return null,
        status = status,
        choice = obj["choice"]?.jsonPrimitive?.contentOrNull,
    )
}

private fun writeEntry(entry: ConflictEntry): JsonObject = buildJsonObject {
    put("id", entry.id)
    put("rom_id", entry.romId)
    put("slot", entry.slot)
    put("game_label", entry.gameLabel)
    put("local_source", entry.localSource)
    put("local_updated_at", entry.localUpdatedAtMillis)
    put("local_size", entry.localSizeBytes)
    put("local_hash", entry.localHash)
    put("remote_source", entry.remoteSource)
    put("remote_updated_at", entry.remoteUpdatedAtMillis)
    put("remote_size", entry.remoteSizeBytes)
    put("remote_hash", entry.remoteHash)
    put("local_copy", entry.localCopy)
    put("remote_copy", entry.remoteCopy)
    put("slot_file", entry.slotFile)
    put(
        "status",
        when (entry.status) {
            ConflictStatus.Open -> "open"
            ConflictStatus.Deferred -> "deferred"
            ConflictStatus.Closed -> "closed"
        },
    )
    if (entry.choice == null) put("choice", JsonNull) else put("choice", entry.choice)
}

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
