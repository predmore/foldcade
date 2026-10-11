package app.foldcade.romm

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * What this device last agreed with RomM, per slot of one ROM. Kept in [file].
 *
 * [SlotRecord.syncedHash] is the hash of the local bytes after the last upload,
 * download, or matching no-op. A local save whose hash differs from it holds
 * progress RomM has not seen, whatever its timestamp says. Such a save is never
 * overwritten without first being kept on the server and in the local trash.
 * A slot with no record counts as unsynced, so a save whose history was lost is
 * also kept.
 *
 * [SlotRecord.archivedHash] is the last local hash already uploaded as a
 * kept-both archive, so a retried conflict does not archive the same bytes again.
 * [SlotRecord.emulator] is the player that last wrote the slot. A later sync
 * with no player at hand uses it.
 *
 * Each write is durable. An unreadable file reads as empty, so every slot counts
 * as unsynced and is kept rather than overwritten. A read that fails for a
 * moment does not let [update] drop the other slots: only a file that parses
 * as something other than a ledger is moved aside and started over.
 * The caller serializes access for one ROM.
 */
class SaveLedger(private val file: Path) {
    fun slots(): Map<String, SlotRecord> = try {
        read()
    } catch (_: IOException) {
        emptyMap()
    }

    /** Throws when [file] exists and cannot be read. */
    private fun read(): Map<String, SlotRecord> {
        val bytes = try {
            Files.readAllBytes(file)
        } catch (_: NoSuchFileException) {
            return emptyMap()
        }
        val root = try {
            parseObject(bytes)
        } catch (e: RommException) {
            throw UnreadableLedger(e)
        }
        val slots = root["slots"] as? JsonObject ?: throw UnreadableLedger(null)
        return slots.mapNotNull { (slot, value) ->
            val obj = value as? JsonObject ?: return@mapNotNull null
            slot to SlotRecord(
                syncedHash = obj.optString("synced_hash"),
                archivedHash = obj.optString("archived_hash"),
                emulator = obj.optString("emulator"),
            )
        }.toMap()
    }

    fun slot(slot: String): SlotRecord? = slots()[slot]

    /** True when [hash] is not what this device last agreed with RomM for [slot]. */
    fun unsynced(slot: String, hash: String): Boolean = slot(slot)?.syncedHash != hash

    fun update(slot: String, change: (SlotRecord) -> SlotRecord?) {
        val current = try {
            read()
        } catch (unreadable: UnreadableLedger) {
            Files.move(file, file.resolveSibling("${file.fileName}.unreadable-${System.currentTimeMillis()}"))
            emptyMap()
        }.toMutableMap()
        val next = change(current[slot] ?: SlotRecord())
        if (next == null) current.remove(slot) else current[slot] = next
        write(current)
    }

    fun markSynced(slot: String, hash: String, emulator: String?) =
        update(slot) { it.copy(syncedHash = hash, emulator = emulator ?: it.emulator) }

    private fun write(slots: Map<String, SlotRecord>) {
        val root = buildJsonObject {
            put(
                "slots",
                buildJsonObject {
                    slots.toSortedMap().forEach { (slot, record) ->
                        put(
                            slot,
                            buildJsonObject {
                                putNullable("synced_hash", record.syncedHash)
                                putNullable("archived_hash", record.archivedHash)
                                putNullable("emulator", record.emulator)
                            },
                        )
                    }
                },
            )
        }
        writeDurably(file, root.toString().toByteArray(Charsets.UTF_8))
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: String?) {
        if (value == null) put(name, JsonNull) else put(name, value)
    }

    companion object {
        const val FILE_NAME: String = "ledger.json"
    }
}

/** The ledger file is there and is not a ledger. A failed read is an [IOException] instead. */
private class UnreadableLedger(cause: Throwable?) : IOException("save ledger is unreadable", cause)

data class SlotRecord(
    val syncedHash: String? = null,
    val archivedHash: String? = null,
    val emulator: String? = null,
)
