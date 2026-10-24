package app.foldcade

import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A game-file read asks only for the bytes still in the file. */
class GameFileReadTest {
    @Test
    fun aReadStopsAtTheEndAndRefusesANonsenseRequest() {
        assertEquals(4, gameReadCount(10, 6, 100))
        assertEquals(0, gameReadCount(10, 10, 4))
        assertEquals(0, gameReadCount(10, 12, 4))
        assertEquals(0, gameReadCount(10, 0, 0))
        assertNull(gameReadCount(10, -1, 4))
        assertNull(gameReadCount(-1, 0, 4))
        val file = byteArrayOf(1, 2, 3, 4, 5)
        assertTrue(byteArrayOf(4, 5).contentEquals(slice(file, 3, 100)))
        assertTrue(byteArrayOf(1, 2).contentEquals(slice(file, 0, 2)))
        assertEquals(0, slice(file, 5, Int.MAX_VALUE)!!.size)
    }

    @Test
    fun aReadThatRunsOutOfMemoryReturnsNull() {
        val file = byteArrayOf(1, 2, 3)
        assertNull(readSlice(file.size.toLong(), 0, file.size) { throw OutOfMemoryError("oversized") })
        assertNull(readSlice(file.size.toLong(), 0, file.size) { throw IOException("unreadable") })
    }

    private fun slice(file: ByteArray, offset: Long, length: Int): ByteArray? {
        var at = offset.toInt()
        return readSlice(file.size.toLong(), offset, length) { buffer: ByteBuffer ->
            if (at >= file.size) return@readSlice -1
            val count = minOf(buffer.remaining(), file.size - at)
            buffer.put(file, at, count)
            at += count
            count
        }
    }
}
