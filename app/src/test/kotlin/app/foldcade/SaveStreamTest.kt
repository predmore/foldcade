package app.foldcade

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class SaveStreamTest {
    @Test fun tinySavesWithHugeCeilingsAllocateForTheBody() {
        val bytes = "small save".toByteArray()
        for (limit in listOf(256 * 1024 * 1024, Int.MAX_VALUE)) {
            assertArrayEquals(bytes, readSaveStream(ByteArrayInputStream(bytes), limit))
        }
    }

    @Test fun exactEmptyAndOversizedUnknownLengthBodies() {
        assertArrayEquals(byteArrayOf(), readSaveStream(ByteArrayInputStream(byteArrayOf()), 0))
        assertArrayEquals(byteArrayOf(1, 2), readSaveStream(ByteArrayInputStream(byteArrayOf(1, 2)), 2))
        var read = 0
        val endless = object : InputStream() { override fun read(): Int { read++; return 7 } }
        assertNull(readSaveStream(endless, 10))
        assertEquals(11, read)
    }
}
