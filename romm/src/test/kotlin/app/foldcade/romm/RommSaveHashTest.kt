package app.foldcade.romm

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expected values are from RomM 5.4's `hash_save_content`, run in Python on the same files. */
class RommSaveHashTest {
    @Test
    fun aZipHashesByItsFilesWhicheverOrderAndFoldersItLists() {
        val expected = "06962f13ce5a4e15b9ae34f3b72bcc89"
        val listed = zipOf(
            "extdata/icon" to "icon",
            "data/" to null,
            "data/00000001/" to null,
            "data/00000001/00.SAV" to "abc",
            "data/00000001.metadata" to "meta",
        )
        val bare = zipOf(
            "data/00000001.metadata" to "meta",
            "data/00000001/00.SAV" to "abc",
            "extdata/icon" to "icon",
        )
        assertEquals(expected, rommSaveHash(listed))
        assertEquals(expected, rommSaveHash(bare))
    }

    @Test
    fun anythingElseIsTheMd5OfItsBytes() {
        assertEquals("8d90078ef0b3778140a7fa0ab541cfd6", rommSaveHash("plain save".toByteArray()))
        assertEquals(md5Hex(ByteArray(8192)), rommSaveHash(ByteArray(8192)))
    }

    @Test
    fun aBrokenZipHasNoHash() {
        val zip = zipOf("data/a" to "x")
        // Keep the end record, so it still looks like a zip, and break the entry before it.
        val broken = zip.copyOf().also { bytes -> for (i in 0 until 30) bytes[i] = 0 }
        assertNull(rommSaveHash(broken))
    }

    private fun zipOf(vararg entries: Pair<String, String?>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                if (text != null) zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
