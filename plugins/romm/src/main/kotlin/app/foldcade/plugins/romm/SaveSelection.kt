package app.foldcade.plugins.romm

import app.foldcade.romm.RommSave
import app.foldcade.romm.md5File
import app.foldcade.romm.copySaveToTrash
import app.foldcade.romm.writeDurably
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A single ROM's unfinished selection. The ROM mutex owns this file and its staged bytes. */
internal class SaveSelection(private val root: Path) {
    private val intent = root.resolve("selection.json")
    val staged: Path = root.resolve("selected.sav")
    fun pending(): Boolean = Files.exists(intent)

    data class Intent(val slot: String, val emulator: String, val hash: String, val serverHash: String, val previousHash: String?)

    fun begin(slot: String, emulator: String, hash: String, serverHash: String, previousHash: String?) {
        writeDurably(intent, buildJsonObject {
            put("slot", slot); put("emulator", emulator); put("hash", hash); put("server_hash", serverHash)
            put("previous_hash", previousHash)
        }.toString().toByteArray())
    }

    private fun read(): Intent {
        val obj = Json.parseToJsonElement(String(Files.readAllBytes(intent), Charsets.UTF_8)) as? JsonObject ?: throw IOException("Unreadable save selection")
        fun field(key: String): String = obj[key]?.jsonPrimitive?.contentOrNull ?: throw IOException("Unreadable save selection")
        return Intent(field("slot"), field("emulator"), field("hash"), field("server_hash"),
            obj["previous_hash"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull)
    }

    /**
     * Inspect the head after an ambiguous upload; never repeat an overwrite. A later server
     * head or new local progress wins. Both the staged choice and the old server version stay.
     * A crash between slot replacement and ledger write simply repeats these durable writes.
     */
    fun recover(data: RommData, romId: Long, saves: List<RommSave>) {
        if (!pending()) return
        val held = read()
        if (!Files.isRegularFile(staged) || md5File(staged) != held.hash) throw IOException("Staged save is unreadable")
        val head = saves.filter { it.slot == held.slot }.maxWithOrNull(compareBy<RommSave> { it.updatedAt }.thenBy { it.id })
        val slot = data.slotFile(romId, held.slot)
        val current = if (Files.exists(slot)) md5File(slot) else null
        if (head?.contentHash?.lowercase() == held.serverHash &&
            (current == held.previousHash || current == held.hash || current == null)) {
            install(data, romId, held)
        }
        // Keep staging as an extra copy. A future explicit choice may replace it, after sync.
        Files.deleteIfExists(intent)
    }

    fun finish(data: RommData, romId: Long) {
        val held = read()
        install(data, romId, held)
        Files.deleteIfExists(intent)
    }

    private fun install(data: RommData, romId: Long, held: Intent) {
        if (md5File(staged) != held.hash) throw IOException("Staged save changed")
        val slot = data.slotFile(romId, held.slot)
        if (Files.exists(slot) && md5File(slot) != held.hash) copySaveToTrash(slot)
        writeDurably(slot, Files.readAllBytes(staged))
        // The ledger tracks raw file MD5, including zip container bytes, unlike RomM's ZIP hash.
        data.ledger(romId).markSynced(held.slot, held.hash, held.emulator)
    }
}
