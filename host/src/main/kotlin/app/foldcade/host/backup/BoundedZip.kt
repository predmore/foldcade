package app.foldcade.host.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipInputStream

/** Count skipped entries too: reaching the next ZIP entry inflates the preceding one. */
class InflateBudget(private var left: Long, private val tooLarge: () -> IOException = { IOException("archive is too large") }) {
    fun spend(bytes: Int) {
        left -= bytes
        if (left < 0) throw tooLarge()
    }
}

fun ZipInputStream.readBounded(limit: Long, budget: InflateBudget): ByteArray =
    readBoundedOrNull(limit, budget) ?: throw IOException("archive entry is too large")

fun ZipInputStream.readBoundedOrNull(limit: Long, budget: InflateBudget): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var size = 0L
    while (true) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), limit - size + 1).toInt().coerceAtLeast(1))
        if (read < 0) return out.toByteArray()
        budget.spend(read)
        size += read
        if (size > limit) return null
        out.write(buffer, 0, read)
    }
}

fun ZipInputStream.drainBounded(budget: InflateBudget) {
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) return
        budget.spend(read)
    }
}

/** Stream a large section to private staging storage without retaining an inflated copy. */
fun ZipInputStream.copyBounded(output: OutputStream, limit: Long, budget: InflateBudget) {
    val buffer = ByteArray(64 * 1024)
    var size = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) return
        budget.spend(read)
        size += read
        if (size > limit) throw IOException("archive entry is too large")
        output.write(buffer, 0, read)
    }
}
