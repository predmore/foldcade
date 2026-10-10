package app.foldcade.romm

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.stream.Collectors

/**
 * Copies of saves that could not be uploaded. [flush] retries
 * `POST /api/saves` without a session id. A dead network, HTTP 408, or
 * HTTP 429 leaves the entry in place. An auth failure (401, 403, or no token)
 * leaves that entry and every later one, and stops the flush. Any other 4xx,
 * including a moved slot, drops the entry. A metadata file that cannot be
 * read is skipped.
 *
 * Each file is written to a temp name, synced, then renamed. The `.json` is
 * renamed last, so an entry exists only once its `.bin` is complete. A `.json`
 * whose `.bin` is gone has nothing to send and is removed. Temp files and
 * `.bin` files without a `.json` are removed once they are [STALE] old.
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
        writeDurably(bin, bytes)
        val meta = buildJsonObject {
            put("id", id)
            put("rom_id", save.romId)
            put("file_name", save.fileName)
            if (slot == null) put("slot", JsonNull) else put("slot", slot)
            if (save.emulator == null) put("emulator", JsonNull) else put("emulator", save.emulator)
            put("device_id", deviceId)
            put("content_hash", contentHash)
        }
        writeDurably(root.resolve("$id.json"), meta.toString().toByteArray())
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
        sweep()
        var sent = 0
        var kept = 0
        val items = pending()
        for ((index, item) in items.withIndex()) {
            val bytes = try {
                Files.readAllBytes(item.bytes)
            } catch (_: NoSuchFileException) {
                drop(item)
                continue
            } catch (_: IOException) {
                kept++
                continue
            }
            try {
                client.uploadSave(
                    romId = item.romId,
                    fileName = item.fileName,
                    slot = item.slot,
                    emulator = item.emulator,
                    deviceId = item.deviceId,
                    sessionId = null,
                    contentHash = item.contentHash,
                    bytes = bytes,
                )
                drop(item)
                sent++
            } catch (e: CancellationException) {
                throw e
            } catch (_: RommUnauthenticated) {
                // Every later upload would fail the same way. Keep them all for the next token.
                return FlushResult(sent, kept + items.size - index)
            } catch (_: RommUnavailable) {
                kept++
            } catch (_: RommSlotMoved) {
                drop(item)
            } catch (e: RommHttpException) {
                when {
                    e.status == 401 || e.status == 403 -> return FlushResult(sent, kept + items.size - index)
                    permanentClientError(e.status) -> drop(item)
                    else -> kept++
                }
            } catch (_: RommException) {
                kept++
            }
        }
        return FlushResult(sent, kept)
    }

    private fun drop(item: PendingSaveUpload) {
        Files.deleteIfExists(item.bytes)
        Files.deleteIfExists(root.resolve("${item.id}.json"))
    }

    /** Removes what a crash between the writes in [enqueue] can leave behind. */
    private fun sweep() {
        if (!Files.isDirectory(root)) return
        val cutoff = Instant.now().minus(STALE)
        val files = Files.list(root).use { stream -> stream.collect(Collectors.toList()) }
        for (file in files) {
            val name = file.fileName.toString()
            val leftover = name.endsWith(".tmp") ||
                (name.endsWith(".bin") && !Files.exists(root.resolve(name.removeSuffix(".bin") + ".json")))
            if (!leftover) continue
            try {
                if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) Files.deleteIfExists(file)
            } catch (_: IOException) {
                Unit
            }
        }
    }

    private fun writeDurably(target: Path, bytes: ByteArray) {
        val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
        FileChannel.open(
            temp,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        ).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** 408 and 429 can succeed on a later try. 401 and 403 stop the flush before this. The rest of 4xx will not. */
    private fun permanentClientError(status: Int): Boolean =
        status in 400..499 && status != 408 && status != 429

    companion object {
        val STALE: Duration = Duration.ofHours(1)
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
