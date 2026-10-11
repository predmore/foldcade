package app.foldcade.artwork

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Image bytes on disk, so art is downloaded once. Files are named by a hash of
 * the address. When the folder passes [maxTotalBytes], the least recently used
 * images are deleted first.
 */
class ArtBytes(
    private val dir: File,
    private val fetch: suspend (String) -> ByteArray?,
    private val maxTotalBytes: Long = 256L * 1024 * 1024,
) {
    constructor(dir: File, http: ArtHttp, maxTotalBytes: Long = 256L * 1024 * 1024) : this(
        dir,
        { url -> http.get(url).takeIf { it.status == 200 }?.body },
        maxTotalBytes,
    )

    // Bounded locks coalesce requests from both displays without retaining every URL.
    private val loads = Array(32) { Mutex() }
    private val disk = Mutex()

    /** The image at [url], or null when the server has none. Throws when it cannot be reached. */
    suspend fun load(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(dir, hashOf(url))
        loads[(url.hashCode() and Int.MAX_VALUE) % loads.size].withLock {
            disk.withLock { read(file) }?.let { return@withLock it }
            val bytes = fetch(url)?.takeIf { it.isNotEmpty() } ?: return@withLock null
            coroutineContext.ensureActive()
            disk.withLock { save(file, bytes) }
            bytes
        }
    }

    private fun read(file: File): ByteArray? = try {
        if (file.isFile) {
            file.setLastModified(System.currentTimeMillis())
            file.readBytes().takeIf { it.isNotEmpty() }
        } else null
    } catch (_: IOException) {
        null
    }

    private fun save(file: File, bytes: ByteArray) {
        var temp: File? = null
        try {
            dir.mkdirs()
            // A different cache instance may still be finishing a request after an origin change.
            temp = File.createTempFile(file.name, ".tmp", dir)
            temp.writeBytes(bytes)
            if (temp.renameTo(file)) trim()
        } catch (_: IOException) {
            // Cache eviction or a full disk must not fail a successful image request.
        } finally {
            temp?.delete()
        }
    }

    private fun trim() {
        val files = dir.listFiles { file -> file.isFile && !file.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxTotalBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxTotalBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun hashOf(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
}
