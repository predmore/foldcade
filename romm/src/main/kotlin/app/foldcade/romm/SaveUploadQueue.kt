package app.foldcade.romm

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.stream.Collectors

/**
 * Copies of saves that could not be uploaded. [flush] retries
 * `POST /api/saves` without a session id. A 409 or a dead network leaves
 * the entry in place.
 */
class SaveUploadQueue(private val root: Path) {
    fun enqueue(
        save: LocalSave,
        deviceId: String,
        contentHash: String,
        bytes: ByteArray,
        slot: String? = save.slot,
    ) {
        Files.createDirectories(root)
        val id = UUID.randomUUID().toString()
        val bin = root.resolve("$id.bin")
        Files.write(bin, bytes)
        val meta = buildJsonObject {
            put("id", id)
            put("rom_id", save.romId)
            put("file_name", save.fileName)
            if (slot == null) put("slot", JsonNull) else put("slot", slot)
            if (save.emulator == null) put("emulator", JsonNull) else put("emulator", save.emulator)
            put("device_id", deviceId)
            put("content_hash", contentHash)
        }
        Files.write(root.resolve("$id.json"), meta.toString().toByteArray())
    }

    fun pending(): List<PendingSaveUpload> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".json") }.map { path ->
                val obj = parseObject(Files.readAllBytes(path))
                val id = obj.reqString("id")
                PendingSaveUpload(
                    id = id,
                    romId = obj.reqLong("rom_id"),
                    fileName = obj.reqString("file_name"),
                    slot = obj.optString("slot"),
                    emulator = obj.optString("emulator"),
                    deviceId = obj.reqString("device_id"),
                    contentHash = obj.reqString("content_hash"),
                    bytes = root.resolve("$id.bin"),
                )
            }.collect(Collectors.toList())
        }
    }

    suspend fun flush(client: RommClient): FlushResult {
        var sent = 0
        var kept = 0
        for (item in pending()) {
            try {
                client.uploadSave(
                    romId = item.romId,
                    fileName = item.fileName,
                    slot = item.slot,
                    emulator = item.emulator,
                    deviceId = item.deviceId,
                    sessionId = null,
                    contentHash = item.contentHash,
                    bytes = Files.readAllBytes(item.bytes),
                )
                Files.deleteIfExists(item.bytes)
                Files.deleteIfExists(root.resolve("${item.id}.json"))
                sent++
            } catch (e: CancellationException) {
                throw e
            } catch (_: RommUnavailable) {
                kept++
            } catch (_: RommSlotMoved) {
                kept++
            } catch (_: RommHttpException) {
                kept++
            }
        }
        return FlushResult(sent, kept)
    }
}

data class PendingSaveUpload(
    val id: String,
    val romId: Long,
    val fileName: String,
    val slot: String?,
    val emulator: String?,
    val deviceId: String,
    val contentHash: String,
    val bytes: Path,
)
