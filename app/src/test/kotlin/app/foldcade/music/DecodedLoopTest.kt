package app.foldcade.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** The decoded home loop's file is a WAV that any reader, ExoPlayer's included, takes as 16-bit PCM. */
class DecodedLoopTest {
    @Test
    fun theHeaderIsTheCanonicalOne() {
        // Python's `wave` module, writing 8 bytes of 48 kHz stereo 16-bit PCM.
        val expected = "524946462c00000057415645666d7420100000000100020080bb000000ee0200040010006461746108000000"
        assertEquals(expected, wavHeader(dataBytes = 8, rate = 48_000, channels = 2).joinToString("") { "%02x".format(it) })
    }

    @Test
    fun theSizesCountTheWholeLoop() {
        // Lanternlight decoded on the Thor: 80 s of 48 kHz stereo.
        val header = java.nio.ByteBuffer.wrap(wavHeader(dataBytes = 15_360_000, rate = 48_000, channels = 2))
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(15_360_036, header.getInt(4))
        assertEquals(15_360_000, header.getInt(40))
    }
}
