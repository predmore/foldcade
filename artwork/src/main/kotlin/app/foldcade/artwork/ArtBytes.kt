package app.foldcade.artwork

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Image bytes on disk, so art is downloaded once. Files are named by a hash of
 * the address. When the folder passes [maxTotalBytes], the least recently used
 * images are deleted first.
 */
class ArtBytes(
    private val dir: File,
    private val http: ArtHttp,
    private val maxTotalBytes: Long = 256L * 1024 * 1024,
) {
    /** The image at [url], or null when the server has none. Throws when it cannot be reached. */
    suspend fun load(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(dir, hashOf(url))
        if (file.isFile) {
            file.setLastModified(System.currentTimeMillis())
            return@withContext file.readBytes()
        }
        val response = http.get(url)
        if (response.status != 200 || response.body.isEmpty()) return@withContext null
        dir.mkdirs()
        val temp = File(dir, "${file.name}.tmp")
        temp.writeBytes(response.body)
        if (!temp.renameTo(file)) temp.delete()
        trim()
        response.body
    }

    private fun trim() {
        val files = dir.listFiles { file -> file.isFile && !file.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxTotalBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxTotalBytes) break
            total -= file.length()
            file.delete()
        }
    }

    private fun hashOf(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
}
