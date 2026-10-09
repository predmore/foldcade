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
 * `POST /api/saves` without a session id. A dead network, HTTP 408, or
 * HTTP 429 leaves the entry in place. Any other 4xx, including a moved
 * slot, drops the entry. A metadata file that cannot be read is skipped.
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
        val metas = Files.list(root).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".json") }.collect(Collectors.toList())
        }
        return metas.mapNotNull { path ->
            try {
                readPending(path)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun readPending(path: Path): PendingSaveUpload {
        val obj = parseObject(Files.readAllBytes(path))
        val id = obj.reqString("id")
        return PendingSaveUpload(
            id = id,
            romId = obj.reqLong("rom_id"),
            fileName = obj.reqString("file_name"),
            slot = obj.optString("slot"),
            emulator = obj.optString("emulator"),
            deviceId = obj.reqString("device_id"),
            contentHash = obj.reqString("content_hash"),
            bytes = root.resolve("$id.bin"),
        )
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
                drop(item)
            } catch (e: RommHttpException) {
                if (permanentClientError(e.status)) drop(item) else kept++
            }
        }
        return FlushResult(sent, kept)
    }

    private fun drop(item: PendingSaveUpload) {
        Files.deleteIfExists(item.bytes)
        Files.deleteIfExists(root.resolve("${item.id}.json"))
    }

    /** 408 and 429 can succeed on a later try. The rest of 4xx will not. */
    private fun permanentClientError(status: Int): Boolean =
        status in 400..499 && status != 408 && status != 429
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
