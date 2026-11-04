package app.foldcade.net

import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PublicDownloadTest {
    @get:Rule val temp = TemporaryFolder()
    private fun body(bytes: String, declared: Long) = object : ResponseBody() {
        private val buffer = Buffer().writeUtf8(bytes)
        override fun contentType() = null
        override fun contentLength() = declared
        override fun source() = buffer
    }
    @Test fun completeBodiesAreStreamedAndReportProgressForKnownAndUnknownSizes() {
        for (declared in listOf(5L, -1L)) {
            val file = temp.newFile()
            val progress = mutableListOf<Pair<Long, Long?>>()
            body("hello", declared).use { it.downloadTo(file, 5) { bytes, total -> progress += bytes to total } }
            assertEquals("hello", file.readText())
            assertEquals(0L, progress.first().first)
            assertEquals(5L to 5L, progress.last())
        }
    }
    @Test fun emptyTruncatedAndOversizedBodiesFail() {
        for ((bytes, declared, limit) in listOf(Triple("", 0L, 5L), Triple("hi", 3L, 5L), Triple("hello", 5L, 3L), Triple("hello", -1L, 3L))) {
            try {
                body(bytes, declared).use { it.downloadTo(temp.newFile(), limit) { _, _ -> } }
                fail("Accepted size=$declared, limit=$limit, bytes=${bytes.length}")
            } catch (_: PublicHttpsException) { }
        }
    }
}
