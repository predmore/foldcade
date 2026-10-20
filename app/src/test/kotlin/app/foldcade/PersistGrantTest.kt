package app.foldcade

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistGrantTest {
    @Test
    fun writeThenReadAndAFinalRefusalDoesNotThrow() {
        val taken = mutableListOf<Int>()
        assertTrue(persistTreeGrant(write = true) { flags -> taken += flags })
        assertEquals(listOf(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION), taken)

        val readOnly = mutableListOf<Int>()
        assertTrue(
            persistTreeGrant(write = true) { flags ->
                readOnly += flags
                if (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) throw SecurityException("no write")
            },
        )
        assertEquals(
            listOf(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            ),
            readOnly,
        )

        assertFalse(
            persistTreeGrant(write = true) { throw SecurityException("not persistable") },
        )
        assertFalse(
            persistTreeGrant(write = false) { throw SecurityException("not persistable") },
        )
        val library = mutableListOf<Int>()
        assertTrue(persistTreeGrant(write = false) { flags -> library += flags })
        assertEquals(listOf(Intent.FLAG_GRANT_READ_URI_PERMISSION), library)
    }
}
